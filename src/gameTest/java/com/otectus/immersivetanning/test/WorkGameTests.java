package com.otectus.immersivetanning.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.blockentity.LeatherworkersBenchBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersVatBlockEntity;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.item.UnfinishedLeatherworkItem;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.leather.LeatherworkData;
import com.otectus.immersivetanning.minigame.FleshingSurface;
import com.otectus.immersivetanning.minigame.SeamPatterns;
import com.otectus.immersivetanning.network.packet.StationCommandPacket;
import com.otectus.immersivetanning.registry.ModBlocks;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.station.RackGeometry;
import com.otectus.immersivetanning.station.StationFrame;
import com.otectus.immersivetanning.work.FleshingWork;
import com.otectus.immersivetanning.work.LeatherworkJob;
import com.otectus.immersivetanning.work.MaterialLedger;
import com.otectus.immersivetanning.work.WorkAction;
import com.otectus.immersivetanning.work.WorkPattern;
import com.otectus.immersivetanning.work.WorkPatterns;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.otectus.immersivetanning.test.TestSupport.STATION;

/**
 * The 1.3 work layer: station geometry in every facing, the hide's canonical surface, deterministic and persistent
 * fleshing, the bench's cut/seat/stitch rules, every work pattern, malformed data, leases and sequences, material
 * ownership (refund, workpiece, recovery, exact NBT, remainders), bench repair, vat tending and save migrations.
 */
@GameTestHolder(ImmersiveTanning.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorkGameTests {

    // ------------------------------------------------------------------ geometry

    @GameTest(template = "empty")
    public static void stationFrameRoundTripsEveryFacing(GameTestHelper h) {
        BlockPos pos = new BlockPos(10, 64, -7);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (double[] p : new double[][]{{0.1, 0.2, 0.3}, {0.9, 0.5, -0.4}, {0.5, 1.7, 0.5}}) {
                Vec3 world = StationFrame.toWorld(pos, facing, p[0], p[1], p[2]);
                Vec3 back = StationFrame.toLocal(pos, facing, world);
                h.assertTrue(back.distanceTo(new Vec3(p[0], p[1], p[2])) < 1.0E-9, facing + ": local -> world -> local is exact");
                Vec3 dir = StationFrame.dirToLocal(facing, StationFrame.dirToWorld(facing, new Vec3(p[0], p[1], p[2])));
                h.assertTrue(dir.distanceTo(new Vec3(p[0], p[1], p[2])) < 1.0E-9, facing + ": directions round-trip");
            }
            // The model's front (-z) faces the way the block faces.
            Vec3 front = StationFrame.dirToWorld(facing, new Vec3(0, 0, -1));
            h.assertTrue(front.distanceTo(Vec3.atLowerCornerOf(facing.getNormal())) < 1.0E-9, facing + ": the front is the block's facing");
        }
        h.succeed();
    }

    /** The same hide cell is hit by a pointer ray in every facing: the scoring target is where the hide is drawn. */
    @GameTest(template = "empty")
    public static void pointerRaysMeetTheSameCellInEveryFacing(GameTestHelper h) {
        BlockPos pos = h.absolutePos(STATION);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (float[] uv : new float[][]{{0.2F, 0.3F}, {0.5F, 0.15F}, {0.8F, 0.75F}, {0.5F, 0.95F}}) {
                float[] p = RackGeometry.fleshPoint(uv[0], uv[1]);
                float[] n = RackGeometry.fleshNormal(uv[1]);
                Vec3 eyeLocal = new Vec3(p[0] + n[0] * 9, p[1] + n[1] * 9 + 2, p[2] + n[2] * 9);
                Vec3 eyeWorld = StationFrame.pxToWorld(pos, facing, eyeLocal.x, eyeLocal.y, eyeLocal.z);
                Vec3 targetWorld = StationFrame.pxToWorld(pos, facing, p[0], p[1], p[2]);
                Vec3 dirWorld = targetWorld.subtract(eyeWorld).normalize();
                Vec3 origin = StationFrame.toLocalPx(pos, facing, eyeWorld);
                Vec3 dir = StationFrame.dirToLocal(facing, dirWorld).normalize();
                float[] hit = RackGeometry.intersectFlesh(origin, dir, false);
                h.assertTrue(hit != null && Math.abs(hit[0] - uv[0]) < 1.0E-3 && Math.abs(hit[1] - uv[1]) < 1.0E-3,
                        facing + ": the ray at " + uv[0] + "," + uv[1] + " meets the same cell, got " + (hit == null ? "none" : hit[0] + "," + hit[1]));
            }
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ fleshing

    /** Paths sampled more or less finely (frame rates) and batched differently (packets) give the same hide. */
    @GameTest(template = "empty")
    public static void fleshingIsTheSameForAnyBatchingAndSurvivesSaving(GameTestHelper h) {
        FleshingSurface a = new FleshingSurface(HideSize.LARGE, 77L);
        FleshingSurface b = new FleshingSurface(HideSize.LARGE, 77L);
        List<int[]> path = new ArrayList<>();
        for (int i = 0; i <= 300; i++) {
            float t = i / 300F;
            path.add(new int[]{FleshingSurface.quantize(0.15F + 0.7F * t), FleshingSurface.quantize(0.3F + 0.4F * (float) Math.sin(t * 9))});
        }
        // a: two strokes, lifted at point 150. b: the same two strokes, saved and reloaded between them (a restart).
        a.begin(path.get(0)[0], path.get(0)[1]);
        for (int i = 1; i <= 150; i++) a.drag(path.get(i)[0], path.get(i)[1]);
        a.end();
        a.begin(path.get(150)[0], path.get(150)[1]);
        for (int i = 151; i < path.size(); i++) a.drag(path.get(i)[0], path.get(i)[1]);
        a.end();
        b.begin(path.get(0)[0], path.get(0)[1]);
        for (int i = 1; i <= 150; i++) b.drag(path.get(i)[0], path.get(i)[1]);
        b.end();
        FleshingSurface reloaded = FleshingSurface.load(b.save(), HideSize.MEDIUM);
        reloaded.begin(path.get(150)[0], path.get(150)[1]);
        for (int i = 151; i < path.size(); i++) reloaded.drag(path.get(i)[0], path.get(i)[1]);
        reloaded.end();
        h.assertTrue(reloaded.size() == HideSize.LARGE && reloaded.seed() == 77L, "A saved hide keeps its size and seed");
        h.assertTrue(Math.abs(a.coverage() - reloaded.coverage()) < 1.0E-6 && a.score() == reloaded.score(),
                "Saving mid-way changes nothing: " + a.coverage() + " vs " + reloaded.coverage());
        CompoundTag again = FleshingSurface.load(reloaded.save(), HideSize.SMALL).save();
        h.assertTrue(again.equals(reloaded.save()), "Saving a loaded hide reproduces it exactly");
        h.succeed();
    }

    /** Work done on a hide travels with it off the rack and back; an untouched hide comes off as a plain, stackable one. */
    @GameTest(template = "empty")
    public static void scrapedWorkTravelsWithTheHide(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.TANNERS_RACK.get());
        TannersRackBlockEntity rack = (TannersRackBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        rack.mountRaw(HideData.raw(HideSize.MEDIUM, null));
        ItemStack untouched = rack.takeRaw();
        h.assertTrue(untouched.getTag() == null && ItemStack.isSameItemSameTags(untouched, new ItemStack(ModItems.rawHide(HideSize.MEDIUM))),
                "An untouched hide comes off plain and stacks");
        rack.mountRaw(HideData.raw(HideSize.MEDIUM, null));
        FakePlayer player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TANNERS_KNIFE.get()));
        TestSupport.Operator op = TestSupport.operate(h, player);
        List<float[]> stroke = new ArrayList<>();
        for (int i = 0; i <= 30; i++) stroke.add(new float[]{0.2F + 0.6F * i / 30F, 0.5F});
        op.stroke(WorkAction.FLESH, false, stroke);
        long seed = rack.work().surface().seed();
        float coverage = rack.work().surface().coverage();
        h.assertTrue(rack.work().started() && rack.work().owner().equals(player.getUUID()), "The first stroke makes it the player's work");
        op.act(WorkAction.RACK_TAKE);
        ItemStack carried = player.getInventory().items.stream()
                .filter(s -> s.is(ModItems.rawHide(HideSize.MEDIUM))).findFirst().orElse(ItemStack.EMPTY);
        h.assertTrue(!carried.isEmpty() && carried.getTagElement(HideData.ROOT).contains(TannersRackBlockEntity.FLESHING), "The hide carries its work");
        player.setItemInHand(InteractionHand.MAIN_HAND, carried.copy());
        carried.setCount(0);
        op.act(WorkAction.RACK_MOUNT);
        h.assertTrue(rack.work() != null && rack.work().surface().seed() == seed && Math.abs(rack.work().surface().coverage() - coverage) < 1.0E-6,
                "Remounted, it is the same hide with the same work - nothing rerolled");
        h.succeed();
    }

    // ------------------------------------------------------------------ the bench, at the job

    static LeatherworkJob job(WorkPattern pattern) {
        MaterialLedger ledger = new MaterialLedger();
        ledger.add(MaterialLedger.Role.LEATHER, ModItems.leather(Finish.HARNESS).create(60, null, 4));
        return LeatherworkJob.craft(UUID.randomUUID(), "Tester", ImmersiveTanning.id("leatherworking/leather_boots"), new ItemStack(Items.LEATHER_BOOTS),
                Finish.HARNESS, null, 60, 4, ledger, pattern);
    }

    static float[] xs(List<float[]> points) {
        float[] out = new float[points.size()];
        for (int i = 0; i < out.length; i++) out[i] = points.get(i)[0];
        return out;
    }

    static float[] ys(List<float[]> points) {
        float[] out = new float[points.size()];
        for (int i = 0; i < out.length; i++) out[i] = points.get(i)[1];
        return out;
    }

    static void cutAndSeat(LeatherworkJob job) {
        for (int c = 0; c < job.pattern().cuts().size(); c++) {
            List<float[]> points = TestSupport.along(job.pattern().cutPath(c), 0.02F);
            job.cut(true, true, xs(points), ys(points), 1F);
        }
        for (int p = 0; p < job.pattern().panels().size(); p++) {
            WorkPattern.Placement t = job.pattern().placement(p);
            job.place(p, t.x(), t.y(), t.rot(), 1F);
        }
    }

    static void finishCleanly(LeatherworkJob job) {
        cutAndSeat(job);
        for (int a = 0; a < job.stitchCount(); a++) {
            float[] f = job.stitchFrame(a);
            float pull = job.pattern().joinOf(a).pullIdeal();
            job.puncture(a, f[4], f[5], 1F);
            job.releaseThread(f[4] + f[6] * pull, f[5] + f[7] * pull, false);
        }
        job.tie();
    }

    @GameTest(template = "empty")
    public static void cutsAdvanceOnlyAlongTheirLine(GameTestHelper h) {
        LeatherworkJob job = job(WorkPatterns.generic());
        float[] path = job.pattern().cutPath(0);
        float length = com.otectus.immersivetanning.minigame.StitchPattern.length(path);
        float[] start = com.otectus.immersivetanning.minigame.StitchPattern.pointAt(path, 0F);
        job.cut(true, true, new float[]{start[0]}, new float[]{start[1]}, 1F);
        h.assertTrue(job.cutFrontier(0) == 0F && !job.started(), "Touching the line without moving cuts nothing");
        float[] end = com.otectus.immersivetanning.minigame.StitchPattern.pointAt(path, 1F);
        job.cut(true, true, new float[]{start[0], end[0]}, new float[]{start[1], end[1]}, 1F);
        h.assertTrue(job.cutFrontier(0) < length * 0.5F, "Jumping to the far end does not cut the line, got " + job.cutFrontier(0));
        List<float[]> half = TestSupport.along(new float[]{start[0], start[1], (start[0] + end[0]) / 2, (start[1] + end[1]) / 2}, 0.02F);
        job.cut(true, true, xs(half), ys(half), 1F);
        float frontier = job.cutFrontier(0);
        h.assertTrue(frontier > length * 0.45F && frontier < length * 0.55F, "Half the line traced cuts half of it, got " + frontier / length);
        job.cut(true, true, xs(half), ys(half), 1F);
        h.assertTrue(job.cutFrontier(0) == frontier, "Cutting the same length again adds nothing");
        List<float[]> wide = TestSupport.along(new float[]{start[0], start[1] + 0.12F, end[0], end[1] + 0.12F}, 0.02F);
        job.cut(true, true, xs(wide), ys(wide), 1F);
        h.assertTrue(job.cutFrontier(0) == frontier && !job.strayMarks().isEmpty(), "Blade work beside the line is a rough edge, not progress");
        List<float[]> rest = TestSupport.along(new float[]{(start[0] + end[0]) / 2, (start[1] + end[1]) / 2, end[0], end[1]}, 0.02F);
        job.cut(true, true, xs(rest), ys(rest), 1F);
        h.assertTrue(job.cutDone(0), "Resuming from where it stopped finishes the cut");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void everyWorkPatternLoadsAndCompletes(GameTestHelper h) {
        for (String name : new String[]{"boots", "helmet", "chestplate", "leggings", "saddle", "horse_armor", "strap", "generic_wrap", "patch"}) {
            WorkPattern pattern = WorkPatterns.get(ImmersiveTanning.id(name));
            h.assertTrue(pattern != null, "Work pattern " + name + " is defined");
            h.assertTrue(pattern.cuts().size() <= 6 && pattern.totalActions() >= 8 && pattern.totalActions() <= 16,
                    name + ": bounded contour work and 8-16 stitches, got " + pattern.cuts().size() + "/" + pattern.totalActions());
            for (int a = 0; a < pattern.totalActions(); a++) {
                LeatherworkJob probe = job(pattern);
                cutAndSeat(probe);
                float[] f = probe.stitchFrame(a);
                float gap = (float) Math.hypot(f[2] - f[0], f[3] - f[1]);
                h.assertTrue(gap > 0.015F && gap < 0.12F, name + " stitch " + a + ": the holes straddle a seam, " + gap + " apart");
                float pull = pattern.joinOf(a).pullIdeal();
                boolean reachable = onMat(f[4] + f[6] * pull, f[5] + f[7] * pull)
                        || onMat(f[4] - f[6] * pull, f[5] - f[7] * pull);
                h.assertTrue(reachable, name + " stitch " + a + ": an even pull fits on the work mat");
            }
            LeatherworkJob job = job(pattern);
            finishCleanly(job);
            h.assertTrue(job.stage() == LeatherworkJob.Stage.DONE && job.workmanship() >= 95, name + ": clean work completes at "
                    + job.workmanship());
            LeatherworkJob copy = LeatherworkJob.load(job.save());
            h.assertTrue(copy != null && copy.workmanship() == job.workmanship() && copy.stage() == job.stage(), name + ": a saved job reads back the same");
        }
        // Every 1.2 seam pattern still becomes usable work.
        for (ResourceLocation id : SeamPatterns.ids()) {
            WorkPattern legacy = WorkPattern.fromLegacy(SeamPatterns.get(id));
            h.assertTrue(legacy.totalActions() >= 2 && legacy.totalActions() <= 8, id + ": its seams become 2-8 stitches, got " + legacy.totalActions());
            LeatherworkJob job = job(legacy);
            finishCleanly(job);
            h.assertTrue(job.stage() == LeatherworkJob.Stage.DONE, id + ": the adapted pattern completes");
        }
        h.succeed();
    }

    private static boolean onMat(float x, float y) {
        return x >= 0.02F && x <= 0.98F && y >= 0.02F && y <= 0.98F;
    }

    @GameTest(template = "empty")
    public static void malformedPatternsAreRefusedWithReasons(GameTestHelper h) {
        String valid = """
                {"schema_version":1,"panels":[{"id":"a","outline":[[0.1,0.1],[0.9,0.1],[0.9,0.9]]}],
                 "cuts":[{"id":"c","panel":"a","path":[[0.1,0.1],[0.9,0.1]]}],
                 "joins":[{"id":"j","a":{"panel":"a","path":[[0.2,0.2],[0.2,0.8]]},"b":{"panel":"a","path":[[0.3,0.2],[0.3,0.8]]},"actions":3}]}""";
        WorkPattern.fromJson(ImmersiveTanning.id("probe"), JsonParser.parseString(valid).getAsJsonObject());
        String[][] broken = {
                {"\"schema_version\":1", "\"schema_version\":2", "schema"},
                {"\"panel\":\"a\",\"path\":[[0.1,0.1],[0.9,0.1]]", "\"panel\":\"zz\",\"path\":[[0.1,0.1],[0.9,0.1]]", "unknown panel"},
                {"[[0.1,0.1],[0.9,0.1]]}]", "[[0.1,0.1],[0.11,0.1]]}]", "degenerate"},
                {"\"actions\":3", "\"actions\":40", "actions"},
                {"\"actions\":3", "\"actions\":3,\"pull\":{\"min\":0.3,\"ideal\":0.2,\"max\":0.4}", "pull"},
                {"[0.9,0.9]]", "[1.9,0.9]]", "must be"},
        };
        for (String[] b : broken) {
            String json = valid.replace(b[0], b[1]);
            try {
                WorkPattern.fromJson(ImmersiveTanning.id("probe"), JsonParser.parseString(json).getAsJsonObject());
                h.fail("A pattern with " + b[2] + " problems is refused");
            } catch (JsonParseException e) {
                h.assertTrue(e.getMessage() != null && !e.getMessage().isEmpty(), "The refusal says why (" + b[2] + ")");
            }
        }
        JsonObject nan = JsonParser.parseString(valid).getAsJsonObject();
        nan.getAsJsonArray("panels").get(0).getAsJsonObject().getAsJsonArray("outline").get(0).getAsJsonArray().set(0,
                new com.google.gson.JsonPrimitive(Float.NaN));
        try {
            WorkPattern.fromJson(ImmersiveTanning.id("probe"), nan);
            h.fail("A NaN coordinate is refused");
        } catch (JsonParseException expected) {
            // refused
        }
        h.assertTrue(WorkPattern.load(new CompoundTag()) == null, "A damaged saved pattern reads as missing, not as a crash");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oversizedOrOutOfRangeCommandsAreDropped(GameTestHelper h) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new StationCommandPacket(BlockPos.ZERO, 1L, 1, 0, 1, WorkAction.FLESH, 0, new int[0], new short[]{4095}, new short[]{0}, null, null).encode(buf);
        h.assertTrue(StationCommandPacket.decode(buf) != null, "A well-formed command decodes");
        FriendlyByteBuf bad = new FriendlyByteBuf(Unpooled.buffer());
        new StationCommandPacket(BlockPos.ZERO, 1L, 1, 0, 1, WorkAction.FLESH, 0, new int[0], new short[]{5000}, new short[]{0}, null, null).encode(bad);
        h.assertTrue(StationCommandPacket.decode(bad) == null && bad.readableBytes() == 0, "A coordinate out of range drops the whole command");
        FriendlyByteBuf many = new FriendlyByteBuf(Unpooled.buffer());
        many.writeBlockPos(BlockPos.ZERO);
        many.writeLong(1L);
        many.writeVarInt(1);
        many.writeVarInt(0);
        many.writeVarInt(1);
        many.writeVarInt(WorkAction.FLESH.ordinal());
        many.writeByte(0);
        many.writeVarInt(0);
        many.writeVarInt(5000);
        h.assertTrue(StationCommandPacket.decode(many) == null && many.readableBytes() == 0, "Too many points drop the command");
        h.succeed();
    }

    // ------------------------------------------------------------------ leases and authority

    @GameTest(template = "empty")
    public static void leasesSequencesAndInstancesGateEveryCommand(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.TANNERS_RACK.get());
        TannersRackBlockEntity rack = (TannersRackBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        rack.mountRaw(HideData.raw(HideSize.SMALL, null));
        FakePlayer owner = TestSupport.player(h);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TANNERS_KNIFE.get()));
        FakePlayer other = TestSupport.player(h);
        other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TANNERS_KNIFE.get()));
        TestSupport.Operator op = TestSupport.operate(h, owner);
        TestSupport.Operator watcher = TestSupport.operate(h, other);
        h.assertTrue(op.lease.operating() && !watcher.lease.operating(), "A second player at a worked rack watches");
        List<float[]> stroke = new ArrayList<>();
        for (int i = 0; i <= 20; i++) stroke.add(new float[]{0.2F + 0.6F * i / 20F, 0.45F});
        watcher.stroke(WorkAction.FLESH, false, stroke);
        h.assertTrue(!rack.work().started(), "A watcher's strokes change nothing");
        op.stroke(WorkAction.FLESH, false, stroke);
        float after = rack.work().surface().coverage();
        h.assertTrue(after > 0F, "The operator's strokes scrape");
        op.sequence -= 3; // a replayed, older command
        op.stroke(WorkAction.FLESH, false, stroke);
        h.assertTrue(rack.work().surface().coverage() == after, "Replayed commands are ignored");
        op.sequence += 10;
        long instance = rack.instanceId();
        h.setBlock(STATION, ModBlocks.TANNERS_RACK.get().defaultBlockState());
        h.assertTrue(rack.instanceId() == instance, "Setting the same block keeps the same station");
        // The station is replaced by a new one at the same place: the old lease and instance are stale.
        h.setBlock(STATION, net.minecraft.world.level.block.Blocks.AIR);
        h.setBlock(STATION, ModBlocks.TANNERS_RACK.get());
        TannersRackBlockEntity replaced = (TannersRackBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        replaced.mountRaw(HideData.raw(HideSize.SMALL, null));
        op.stroke(WorkAction.FLESH, false, stroke);
        h.assertTrue(replaced.instanceId() != instance && !replaced.work().started(), "Commands for the broken station do nothing to its replacement");
        h.succeed();
    }

    // ------------------------------------------------------------------ material ownership

    @GameTest(template = "empty")
    public static void startedPiecesTravelAsWorkpiecesAndRecoverExactly(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.LEATHERWORKERS_BENCH.get());
        LeatherworkersBenchBlockEntity bench = (LeatherworkersBenchBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        FakePlayer player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.AWL.get()));
        player.getInventory().add(new ItemStack(ModItems.TANNERS_KNIFE.get()));
        ItemStack special = ModItems.leather(Finish.HARDENED).create(70, null, 4);
        special.getOrCreateTag().putString("OtherMod", "kept");
        player.getInventory().add(special.copy());
        TestSupport.Operator op = TestSupport.operate(h, player);
        op.send(WorkAction.BENCH_PREPARE, 0, new int[]{Finish.HARDENED.ordinal()}, new short[0], new short[0],
                ImmersiveTanning.id("leatherworking/leather_boots"), null);
        h.assertTrue(bench.job() != null, "Boots are prepared");
        TestSupport.cutCleanly(op, bench);
        op.act(WorkAction.BENCH_CANCEL);
        h.assertTrue(bench.job() != null && StationGameTests.countLeather(player.getInventory()) == 0, "Once cut, a piece cannot be cancelled for its leather");
        op.act(WorkAction.BENCH_TAKE_WORKPIECE);
        ItemStack workpiece = StationGameTests.findItem(player.getInventory(), ModItems.UNFINISHED_LEATHERWORK.get());
        h.assertTrue(bench.job() == null && !workpiece.isEmpty() && UnfinishedLeatherworkItem.job(workpiece) != null,
                "Taken off, the started piece is one unfinished workpiece carrying the job");
        player.setItemInHand(InteractionHand.MAIN_HAND, workpiece.copy());
        workpiece.setCount(0);
        op.act(WorkAction.BENCH_MOUNT_WORKPIECE);
        h.assertTrue(bench.job() != null && bench.job().stage() == LeatherworkJob.Stage.ASSEMBLY, "Laid back on the bench, it resumes where it stopped");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.AWL.get()));

        // A pattern that no longer exists: the job's materials come back exactly once, with nothing else.
        CompoundTag saved = bench.job().save();
        saved.putString("Recipe", "immersive_tanning:leatherworking/removed_piece");
        bench.setJob(LeatherworkJob.load(saved));
        op.act(WorkAction.BENCH_TIE);
        op.act(WorkAction.BENCH_RECOVER);
        ItemStack back = StationGameTests.findItem(player.getInventory(), ModItems.leather(Finish.HARDENED));
        h.assertTrue(bench.job() == null && back.getCount() == 4 && ItemStack.isSameItemSameTags(back, special),
                "Recovery returns the exact leather, other mods' data included");
        h.assertTrue(StationGameTests.findItem(player.getInventory(), Items.LEATHER_BOOTS).isEmpty()
                && StationGameTests.findItem(player.getInventory(), ModItems.LEATHER_SCRAPS.get()).isEmpty(), "and no piece or scraps");
        op.act(WorkAction.BENCH_RECOVER);
        h.assertTrue(StationGameTests.findItem(player.getInventory(), ModItems.leather(Finish.HARDENED)).getCount() == 4, "Recovery happens once");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void breakingABenchDropsOneRoute(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.LEATHERWORKERS_BENCH.get());
        LeatherworkersBenchBlockEntity bench = (LeatherworkersBenchBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        MaterialLedger ledger = new MaterialLedger();
        ledger.add(MaterialLedger.Role.LEATHER, ModItems.leather(Finish.SUPPLE).create(50, null, 4));
        LeatherworkJob started = LeatherworkJob.craft(UUID.randomUUID(), "A", ImmersiveTanning.id("leatherworking/leather_boots"),
                new ItemStack(Items.LEATHER_BOOTS), Finish.SUPPLE, null, 50, 4, ledger, WorkPatterns.generic());
        List<float[]> cut = TestSupport.along(started.pattern().cutPath(0), 0.02F);
        started.cut(true, true, xs(cut), ys(cut), 1F);
        bench.setJob(started);
        h.destroyBlock(STATION);
        h.runAfterDelay(2, () -> {
            List<ItemEntity> drops = h.getEntities(EntityType.ITEM, STATION, 2);
            long workpieces = drops.stream().filter(e -> e.getItem().getItem() instanceof UnfinishedLeatherworkItem).count();
            long leather = drops.stream().filter(e -> e.getItem().is(ModItems.leather(Finish.SUPPLE))).count();
            h.assertTrue(workpieces == 1 && leather == 0, "A started piece drops as one workpiece, its leather inside, not beside it");
            drops.forEach(ItemEntity::discard);
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void ledgerKeepsExactStacksAndReturnsRemainders(GameTestHelper h) {
        MaterialLedger ledger = new MaterialLedger();
        ItemStack named = new ItemStack(Items.STRING, 3);
        named.setHoverName(Component.literal("Waxed thread"));
        ledger.add(MaterialLedger.Role.AUXILIARY, named);
        ledger.add(MaterialLedger.Role.AUXILIARY, new ItemStack(Items.WATER_BUCKET));
        MaterialLedger read = MaterialLedger.load(ledger.save());
        h.assertTrue(ItemStack.isSameItemSameTags(read.stacks(MaterialLedger.Role.AUXILIARY).get(0), named), "Saved materials keep their data");
        List<ItemStack> remainders = read.copy().consume();
        h.assertTrue(remainders.size() == 1 && remainders.get(0).is(Items.BUCKET), "Consuming a water bucket gives the bucket back");
        List<ItemStack> refund = read.drain();
        h.assertTrue(refund.size() == 2 && read.isEmpty() && read.drain().isEmpty(), "A ledger drains once");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void benchRepairMendsWithoutTouchingTheRest(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.LEATHERWORKERS_BENCH.get());
        LeatherworkersBenchBlockEntity bench = (LeatherworkersBenchBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        FakePlayer player = TestSupport.player(h);
        ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
        new LeatherworkData(Finish.HARNESS, 70, 88, null, "Maker").apply(boots);
        LeatherworkData.stampCrafter(boots, UUID.randomUUID());
        ((DyeableLeatherItem) Items.LEATHER_BOOTS).setColor(boots, 0x123456);
        boots.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 2);
        boots.setHoverName(Component.literal("Old Faithful"));
        boots.getOrCreateTag().putString("OtherMod", "kept");
        boots.setDamageValue(40);
        ItemStack before = boots.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, boots);
        player.getInventory().add(new ItemStack(ModItems.LEATHER_SCRAPS.get(), 10));
        player.getInventory().add(new ItemStack(ModItems.AWL.get()));
        TestSupport.Operator op = TestSupport.operate(h, player);
        op.act(WorkAction.BENCH_REPAIR, player.getInventory().selected);
        h.assertTrue(bench.job() != null && bench.job().kind() == LeatherworkJob.Kind.REPAIR && player.getMainHandItem().isEmpty(),
                "The worn boots are laid on the mat with their scraps");
        TestSupport.seatCleanly(op, bench);
        TestSupport.stitchCleanly(op, bench);
        op.act(WorkAction.BENCH_TIE);
        op.act(WorkAction.BENCH_COLLECT);
        ItemStack mended = StationGameTests.findItem(player.getInventory(), Items.LEATHER_BOOTS);
        h.assertTrue(!mended.isEmpty() && mended.getDamageValue() < 40, "The boots are mended, damage " + mended.getDamageValue());
        ItemStack compare = mended.copy();
        compare.setDamageValue(40);
        h.assertTrue(ItemStack.isSameItemSameTags(compare, before), "Finish, quality, maker, dye, enchantments, name and other data are untouched");
        h.assertTrue(LeatherworkData.get(mended).map(LeatherworkData::workmanship).orElse(-1) == 88, "Repair never rerolls the workmanship");
        h.succeed();
    }

    // ------------------------------------------------------------------ the vat

    @GameTest(template = "empty")
    public static void tendingCreditsEachHideOnceEver(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.TANNERS_VAT.get());
        TannersVatBlockEntity vat = (TannersVatBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        vat.fillWater();
        vat.addTannin(4);
        vat.insertHide(new HideData(HideSize.MEDIUM, 50, null));
        long before = vat.slots().get(0).remainingTicks(h.getLevel().getGameTime());
        h.assertTrue(vat.tend() == 1, "A sweep credits the steeping hide");
        long after = vat.slots().get(0).remainingTicks(h.getLevel().getGameTime());
        long credit = Math.round(ServerConfig.get(ServerConfig.TANNING_TICKS) * ServerConfig.get(ServerConfig.VAT_TENDING_CREDIT));
        h.assertTrue(before - after == credit, "It moves the hide by the configured share, got " + (before - after));
        h.assertTrue(vat.tend() == 0, "A second sweep credits nothing");
        vat.drainWater();
        vat.fillWater();
        h.assertTrue(vat.tend() == 0, "Draining and refilling never re-credits");
        CompoundTag saved = vat.saveWithoutMetadata();
        vat.load(saved);
        h.assertTrue(vat.tend() == 0 && vat.slots().get(0).tended(), "Nor does reloading");
        vat.addTannin(2);
        vat.insertHide(new HideData(HideSize.SMALL, 50, null));
        h.assertTrue(vat.tend() == 1, "A hide added later has its own chance");
        h.succeed();
    }

    // ------------------------------------------------------------------ migration

    @GameTest(template = "empty")
    public static void olderSavesMigrateOnceAndStably(GameTestHelper h) {
        // A 1.2 rack with a raw hide (no fleshing work, no version).
        h.setBlock(STATION, ModBlocks.TANNERS_RACK.get());
        TannersRackBlockEntity rack = (TannersRackBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        CompoundTag oldRack = new CompoundTag();
        oldRack.putString("Mode", "RAW");
        oldRack.put("Hide", HideData.raw(HideSize.LARGE, null).save());
        oldRack.putString("Finish", "harness");
        rack.load(oldRack);
        h.assertTrue(rack.mode() == TannersRackBlockEntity.Mode.RAW && rack.work() != null && !rack.work().started()
                && rack.hide().size() == HideSize.LARGE, "A 1.2 raw hide gains untouched work");
        long seed = rack.work().surface().seed();
        CompoundTag once = rack.saveWithoutMetadata();
        rack.load(once);
        CompoundTag twice = rack.saveWithoutMetadata();
        rack.load(twice);
        h.assertTrue(rack.work().surface().seed() == seed && once.getInt("RackVersion") == TannersRackBlockEntity.SAVE_VERSION,
                "Saved and loaded twice, it keeps the same hide (the migration does not run again)");
        // A 1.2 drying rack keeps its finish, clock and state.
        CompoundTag drying = new CompoundTag();
        drying.putString("Mode", "DRYING");
        drying.put("Hide", new HideData(HideSize.MEDIUM, 70, null).save());
        drying.putString("Finish", "supple");
        drying.putLong("DryStart", h.getLevel().getGameTime() - 100);
        drying.putInt("DryTicks", 2400);
        rack.load(drying);
        h.assertTrue(rack.mode() == TannersRackBlockEntity.Mode.DRYING && rack.finish() == Finish.SUPPLE
                && Math.abs(rack.dryProgress(h.getLevel().getGameTime()) - 100F / 2400F) < 1.0E-4, "A drying hide carries on as it was");

        // A 1.2 bench escrow (no record of the work): its exact stacks go onto the output pad, once.
        h.setBlock(STATION.east(2), ModBlocks.LEATHERWORKERS_BENCH.get());
        LeatherworkersBenchBlockEntity bench = (LeatherworkersBenchBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION.east(2)));
        CompoundTag oldBench = new CompoundTag();
        ListTag escrow = new ListTag();
        escrow.add(ModItems.leather(Finish.HARNESS).create(60, null, 4).save(new CompoundTag()));
        escrow.add(new ItemStack(Items.STRING, 2).save(new CompoundTag()));
        oldBench.put("Escrow", escrow);
        oldBench.put("Working", new ItemStack(Items.LEATHER_BOOTS).save(new CompoundTag()));
        bench.load(oldBench);
        h.assertTrue(bench.job() == null && bench.output().size() == 2 && bench.outputOwner() == null,
                "Old escrow becomes collectable materials, not an invented half-made piece");
        CompoundTag benchOnce = bench.saveWithoutMetadata();
        bench.load(benchOnce);
        bench.load(bench.saveWithoutMetadata());
        h.assertTrue(bench.output().size() == 2, "Reloading twice does not recover the escrow again");

        // A format 2 vat: its hides keep their clocks and gain their one tending chance.
        h.setBlock(STATION.west(2), ModBlocks.TANNERS_VAT.get());
        TannersVatBlockEntity vat = (TannersVatBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION.west(2)));
        CompoundTag oldVat = new CompoundTag();
        oldVat.putInt("Version", 2);
        oldVat.putInt("Water", 1000);
        oldVat.putInt("Tannin", 0);
        ListTag hides = new ListTag();
        CompoundTag hide = new HideData(HideSize.SMALL, 40, null).save();
        hide.putBoolean("Started", true);
        hide.putLong("Start", h.getLevel().getGameTime() - 50);
        hide.putInt("Duration", 3600);
        hides.add(hide);
        oldVat.put("Hides", hides);
        vat.load(oldVat);
        h.assertTrue(vat.slots().size() == 1 && vat.slots().get(0).started() && !vat.slots().get(0).tended(), "A format 2 hide keeps its start");
        CompoundTag vatOnce = vat.saveWithoutMetadata();
        vat.load(vatOnce);
        h.assertTrue(vatOnce.getInt("Version") == 3 && vat.slots().get(0).remainingTicks(h.getLevel().getGameTime()) == 3600 - 50,
                "Saved as format 3 with the same clock");
        h.succeed();
    }

    /** Unfinished drying is paused off the rack and resumes when remounted - never restarted, never instant. */
    @GameTest(template = "empty")
    public static void dryingPausesOffTheRackAndResumes(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.TANNERS_RACK.get());
        TannersRackBlockEntity rack = (TannersRackBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        rack.mountWet(new HideData(HideSize.MEDIUM, 80, null), Finish.HARDENED);
        rack.advance(1000);
        ItemStack wet = rack.takeWet();
        h.assertTrue(rack.isEmpty() && wet.getTagElement(HideData.ROOT).getString(TannersRackBlockEntity.DRY_FINISH).equals("hardened"),
                "Taken down unfinished, the hide keeps its finish");
        FakePlayer player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, wet);
        TestSupport.Operator op = TestSupport.operate(h, player);
        op.act(WorkAction.RACK_MOUNT);
        float progress = rack.dryProgress(h.getLevel().getGameTime());
        h.assertTrue(rack.mode() == TannersRackBlockEntity.Mode.DRYING && rack.finish() == Finish.HARDENED
                && Math.abs(progress - 1000F / ServerConfig.get(ServerConfig.DRYING_TICKS)) < 0.01F, "Remounted, it resumes at " + progress);
        h.succeed();
    }

    private WorkGameTests() {}
}
