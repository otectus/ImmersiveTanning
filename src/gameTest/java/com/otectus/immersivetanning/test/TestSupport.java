package com.otectus.immersivetanning.test;

import com.mojang.authlib.GameProfile;
import com.otectus.immersivetanning.blockentity.LeatherworkersBenchBlockEntity;
import com.otectus.immersivetanning.blockentity.StationBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.minigame.FleshingSurface;
import com.otectus.immersivetanning.minigame.StitchPattern;
import com.otectus.immersivetanning.network.packet.StationCommandPacket;
import com.otectus.immersivetanning.work.LeatherworkJob;
import com.otectus.immersivetanning.work.StationLease;
import com.otectus.immersivetanning.work.WorkAction;
import com.otectus.immersivetanning.work.WorkCommandHandler;
import com.otectus.immersivetanning.work.WorkPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class TestSupport {
    static final BlockPos STATION = new BlockPos(2, 1, 2);

    static FakePlayer player(GameTestHelper h) {
        FakePlayer player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "TannerTest")) {
            @Override
            public boolean hasDisconnected() {
                return false;
            }
        };
        BlockPos pos = h.absolutePos(STATION);
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5);
        return player;
    }

    /** Right-clicks the station with the item in the main hand. */
    static InteractionResult use(GameTestHelper h, FakePlayer player) {
        BlockPos pos = h.absolutePos(STATION);
        Vec3 hit = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.99);
        return h.getLevel().getBlockState(pos).use(h.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, Direction.SOUTH, pos, false));
    }

    /**
     * A scripted operator at the station: a lease as a real use would grant (rate and path budgets lifted, because a
     * test drives a whole job within one tick), and commands sent through the one server entry point, exactly as the
     * client's packets arrive. Everything else - stage, tools, materials, ownership, coordinates - is checked as usual.
     */
    static Operator operate(GameTestHelper h, FakePlayer player) {
        return operate(h, player, STATION);
    }

    static Operator operate(GameTestHelper h, FakePlayer player, BlockPos relative) {
        StationBlockEntity station = (StationBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(relative));
        StationLease lease = station.grant(player, false, h.getLevel().getGameTime());
        lease.unthrottleForTests();
        return new Operator(player, station, lease);
    }

    static final class Operator {
        final FakePlayer player;
        final StationBlockEntity station;
        final StationLease lease;
        int sequence;

        Operator(FakePlayer player, StationBlockEntity station, StationLease lease) {
            this.player = player;
            this.station = station;
            this.lease = lease;
        }

        void send(WorkAction action, int flags, int[] ints, short[] xs, short[] ys, @Nullable ResourceLocation id, @Nullable ResourceLocation id2) {
            int job = station instanceof LeatherworkersBenchBlockEntity bench && bench.job() != null ? bench.job().id() : 0;
            WorkCommandHandler.handle(player, new StationCommandPacket(station.getBlockPos(), station.instanceId(), lease.id, job, ++sequence,
                    action, flags, ints, xs, ys, id, id2));
        }

        void act(WorkAction action, int... ints) {
            send(action, 0, ints, new short[0], new short[0], null, null);
        }

        /** One press-drag-release: points in 0..1 (unit) or work-plane coordinates (plane), in batches like the client's. */
        void stroke(WorkAction action, boolean plane, List<float[]> points, int... ints) {
            int start = 0;
            while (start < points.size()) {
                int end = Math.min(points.size(), start + 40);
                short[] xs = new short[end - start];
                short[] ys = new short[end - start];
                for (int i = start; i < end; i++) {
                    float[] p = points.get(i);
                    xs[i - start] = plane ? StationCommandPacket.quantizePlane(p[0]) : StationCommandPacket.quantize(p[0]);
                    ys[i - start] = plane ? StationCommandPacket.quantizePlane(p[1]) : StationCommandPacket.quantize(p[1]);
                }
                int flags = (start == 0 ? WorkAction.BEGIN : 0) | (end == points.size() ? WorkAction.END : 0);
                send(action, flags, ints, xs, ys, null, null);
                start = end;
            }
        }
    }

    /**
     * Careful fleshing through the rack's commands: passes along each patch's grain on the server's own copy of the
     * hide, then "finish roughly" if a few cells remain (the old screen's "Done").
     */
    static void fleshCarefully(Operator op, TannersRackBlockEntity rack) {
        FleshingSurface mirror = rack.work().surface().copy();
        for (int p = 0; p < mirror.patches() && rack.mode() == TannersRackBlockEntity.Mode.RAW; p++) {
            float cos = Mth.cos(mirror.grain(p));
            float sin = Mth.sin(mirror.grain(p));
            for (float off = -9F; off <= 9F && rack.mode() == TannersRackBlockEntity.Mode.RAW; off += 2.2F) {
                float cx = mirror.patchX(p) - sin * off;
                float cy = mirror.patchY(p) + cos * off;
                List<float[]> stroke = new ArrayList<>();
                for (int i = 0; i <= 24; i++) {
                    float t = i / 24F;
                    float x = Mth.lerp(t, cx - cos * 11F, cx + cos * 11F);
                    float y = Mth.lerp(t, cy - sin * 11F, cy + sin * 11F);
                    stroke.add(new float[]{Mth.clamp(x / FleshingSurface.W, 0F, 1F), Mth.clamp(y / FleshingSurface.H, 0F, 1F)});
                }
                op.stroke(WorkAction.FLESH, false, stroke);
            }
        }
        if (rack.mode() == TannersRackBlockEntity.Mode.RAW) op.act(WorkAction.FLESH_ROUGH);
    }

    /** The finish's short treatment on a framed hide: broad strokes across the frame (or the cords drawn down). */
    static void treat(Operator op) {
        for (int pass = 0; pass < 4; pass++) {
            List<float[]> stroke = new ArrayList<>();
            for (int i = 0; i <= 10; i++) stroke.add(new float[]{0.1F + 0.8F * i / 10F, 0.15F + 0.1F * i / 10F + 0.2F * pass});
            if (pass % 2 == 1) java.util.Collections.reverse(stroke);
            op.stroke(WorkAction.TREAT, true, stroke);
        }
    }

    /** Points along a polyline every {@code step} units. */
    static List<float[]> along(float[] path, float step) {
        List<float[]> out = new ArrayList<>();
        float length = StitchPattern.length(path);
        int n = Math.max(2, (int) Math.ceil(length / step) + 1);
        for (int i = 0; i < n; i++) out.add(StitchPattern.pointAt(path, i / (n - 1F)));
        return out;
    }

    /** Traces every cut on its line, start to end. */
    static void cutCleanly(Operator op, LeatherworkersBenchBlockEntity bench) {
        LeatherworkJob job = bench.job();
        for (int c = 0; c < job.pattern().cuts().size(); c++) op.stroke(WorkAction.BENCH_CUT, true, along(job.pattern().cutPath(c), 0.02F));
    }

    /** Seats every panel exactly on its target. */
    static void seatCleanly(Operator op, LeatherworkersBenchBlockEntity bench) {
        LeatherworkJob job = bench.job();
        for (int p = 0; p < job.pattern().panels().size(); p++) {
            WorkPattern.Placement t = job.pattern().placement(p);
            op.send(WorkAction.BENCH_PLACE, 0, new int[]{p, Math.round(t.rot() * 10F) + 3600},
                    new short[]{StationCommandPacket.quantizePlane(t.x())}, new short[]{StationCommandPacket.quantizePlane(t.y())}, null, null);
        }
    }

    /** One stitch: puncture at the hole pair, draw the thread straight across the seam by {@code pull}, release. */
    static void stitch(Operator op, LeatherworkersBenchBlockEntity bench, int action, float pull) {
        float[] f = bench.job().stitchFrame(action);
        List<float[]> points = new ArrayList<>();
        for (int i = 0; i <= 4; i++) points.add(new float[]{f[4] + f[6] * pull * i / 4F, f[5] + f[7] * pull * i / 4F});
        op.stroke(WorkAction.BENCH_STITCH, true, points, action);
    }

    /** Every stitch at the join's ideal pull. */
    static void stitchCleanly(Operator op, LeatherworkersBenchBlockEntity bench) {
        LeatherworkJob job = bench.job();
        for (int a = 0; a < job.stitchCount(); a++) stitch(op, bench, a, job.pattern().joinOf(a).pullIdeal());
    }

    /** A whole clean piece from prepared materials: cuts, seats, stitches and the knot. */
    static void craftCleanly(Operator op, LeatherworkersBenchBlockEntity bench) {
        cutCleanly(op, bench);
        seatCleanly(op, bench);
        stitchCleanly(op, bench);
        op.act(WorkAction.BENCH_TIE);
    }

    /** A rushed piece: wobbly cuts within reach of the line, panels at the edge of their tolerance, slack stitches. */
    static void craftPoorly(Operator op, LeatherworkersBenchBlockEntity bench) {
        LeatherworkJob job = bench.job();
        for (int c = 0; c < job.pattern().cuts().size(); c++) {
            float tol = job.pattern().cuts().get(c).tolerance();
            List<float[]> points = along(job.pattern().cutPath(c), 0.02F);
            for (int i = 0; i < points.size(); i++) points.get(i)[1] += (i % 2 == 0 ? 0.85F : -0.85F) * tol;
            op.stroke(WorkAction.BENCH_CUT, true, points);
            op.stroke(WorkAction.BENCH_CUT, true, along(job.pattern().cutPath(c), 0.02F));
        }
        for (int p = 0; p < job.pattern().panels().size(); p++) {
            WorkPattern.Placement t = job.pattern().placement(p);
            float x = t.x() + t.posTol() * 0.9F;
            op.send(WorkAction.BENCH_PLACE, 0, new int[]{p, Math.round(t.rot() * 10F) + 3600},
                    new short[]{StationCommandPacket.quantizePlane(x)}, new short[]{StationCommandPacket.quantizePlane(t.y())}, null, null);
        }
        for (int a = 0; a < job.stitchCount(); a++) stitch(op, bench, a, job.pattern().joinOf(a).pullMin() * 0.4F);
        op.act(WorkAction.BENCH_TIE);
    }

    private TestSupport() {}
}
