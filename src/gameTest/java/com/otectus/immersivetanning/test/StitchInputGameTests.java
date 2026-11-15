package com.otectus.immersivetanning.test;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.blockentity.LeatherworkersBenchBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.minigame.FleshingSurface;
import com.otectus.immersivetanning.minigame.PressGate;
import com.otectus.immersivetanning.registry.ModBlocks;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.work.LeatherworkJob;
import com.otectus.immersivetanning.work.WorkAction;
import com.otectus.immersivetanning.work.WorkPattern;
import com.otectus.immersivetanning.work.WorkPatterns;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.otectus.immersivetanning.test.TestSupport.STATION;

/**
 * One physical action, one accepted action: a press away from the next hole pair (or at the wrong one) is refused
 * without using the stitch up; key repeats and a click landing with Space count once; a thread let go of without a
 * release decides nothing; and the client's prediction of the hide converges exactly on the server's.
 */
@GameTestHolder(ImmersiveTanning.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StitchInputGameTests {

    @GameTest(template = "empty")
    public static void pressAwayFromTheHolesIsRefusedWithoutUsingUpTheStitch(GameTestHelper h) {
        for (var id : WorkPatterns.ids()) {
            WorkPattern pattern = WorkPatterns.get(id);
            LeatherworkJob job = WorkGameTests.job(pattern);
            WorkGameTests.cutAndSeat(job);
            float[] f = job.stitchFrame(0);
            h.assertTrue(job.puncture(0, f[4] + 0.2F, f[5], 1F) == LeatherworkJob.Outcome.OUT_OF_PLACE && job.nextStitch() == 0 && job.pending() < 0,
                    id + ": a press away from the holes does nothing");
            if (job.stitchCount() > 1) {
                float[] g = job.stitchFrame(1);
                h.assertTrue(job.puncture(1, g[4], g[5], 1F) == LeatherworkJob.Outcome.OUT_OF_ORDER && job.nextStitch() == 0,
                        id + ": the second hole pair cannot be stitched first");
            }
            h.assertTrue(job.puncture(0, f[4], f[5], 1F) == LeatherworkJob.Outcome.ACCEPTED && job.pending() == 0, id + ": the lit holes take the awl");
            h.assertTrue(job.puncture(0, f[4], f[5], 1F) == LeatherworkJob.Outcome.IGNORED, id + ": a second press while pulling is not another stitch");
            job.dropThread();
            h.assertTrue(job.pending() < 0 && job.nextStitch() == 0 && Float.isNaN(job.stitchQuality(0)),
                    id + ": letting go of the thread decides nothing");
        }
        h.succeed();
    }

    /** A stitch can be loosened and redone a few times, before the next is begun; the redone stitch is capped. */
    @GameTest(template = "empty")
    public static void loosenedStitchesAreRedoneAtACap(GameTestHelper h) {
        LeatherworkJob job = WorkGameTests.job(WorkPatterns.generic());
        WorkGameTests.cutAndSeat(job);
        float[] f = job.stitchFrame(0);
        float ideal = job.pattern().joinOf(0).pullIdeal();
        job.puncture(0, f[4], f[5], 1F);
        job.releaseThread(f[4] + f[6] * 0.01F, f[5] + f[7] * 0.01F, false);
        h.assertTrue(job.tension(0) == LeatherworkJob.Tension.SLACK, "A barely drawn thread is slack");
        for (int i = 0; i < LeatherworkJob.MAX_REDO; i++) {
            h.assertTrue(job.loosen(0) == LeatherworkJob.Outcome.ACCEPTED, "The last stitch may be loosened (" + i + ")");
            job.puncture(0, f[4], f[5], 1F);
            job.releaseThread(f[4] + f[6] * ideal, f[5] + f[7] * ideal, false);
        }
        h.assertTrue(job.loosen(0) == LeatherworkJob.Outcome.OUT_OF_ORDER, "Not more often than allowed");
        h.assertTrue(job.stitchQuality(0) == LeatherworkJob.REDONE_CAP, "A redone stitch is capped, got " + job.stitchQuality(0));
        float[] g = job.stitchFrame(1);
        job.puncture(1, g[4], g[5], 1F);
        job.releaseThread(g[4] + g[6] * ideal, g[5] + g[7] * ideal, false);
        h.assertTrue(job.loosen(0) == LeatherworkJob.Outcome.OUT_OF_ORDER, "Once the next stitch is made, the earlier one stays");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void onePhysicalActionIsOnePress(GameTestHelper h) {
        PressGate gate = new PressGate();
        h.assertTrue(gate.keyDown(GLFW.GLFW_KEY_SPACE, 1000), "Space goes down: a press");
        for (long t = 1500; t < 3000; t += 33) {
            h.assertTrue(!gate.keyDown(GLFW.GLFW_KEY_SPACE, t), "Auto-repeat while Space is held is not a press");
        }
        gate.keyUp(GLFW.GLFW_KEY_SPACE);
        h.assertTrue(gate.keyDown(GLFW.GLFW_KEY_SPACE, 3100), "Released and pressed again: a new press");
        h.assertTrue(!gate.click(3100 + PressGate.DUPLICATE_MS - 1), "A click landing with Space is the same press");
        h.assertTrue(gate.click(3100 + 375), "A click 375 ms later is its own press");
        h.assertTrue(gate.click(3100 + 375 + PressGate.DUPLICATE_MS), "Deliberate presses one duplicate window apart both count");
        gate.keyUp(GLFW.GLFW_KEY_SPACE);
        h.succeed();
    }

    /**
     * The client predicts the hide from exactly the points it sends; the server replays the same points. Whatever the
     * batching, the two hides are identical, and a command the server refuses (a watcher's) changes neither.
     */
    @GameTest(template = "empty")
    public static void clientPredictionConvergesOnTheServersVerdict(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.TANNERS_RACK.get());
        TannersRackBlockEntity rack = (TannersRackBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        rack.mountRaw(HideData.raw(HideSize.LARGE, null));
        FakePlayer player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TANNERS_KNIFE.get()));
        TestSupport.Operator op = TestSupport.operate(h, player);
        FleshingSurface predicted = rack.work().surface().copy();
        for (int s = 0; s < 6 && rack.mode() == TannersRackBlockEntity.Mode.RAW; s++) {
            List<float[]> stroke = new ArrayList<>();
            for (int i = 0; i <= 90; i++) stroke.add(new float[]{0.1F + 0.8F * i / 90F, 0.25F + 0.1F * s + 0.03F * (float) Math.sin(i * 0.3F)});
            for (int start = 0; start < stroke.size() && rack.mode() == TannersRackBlockEntity.Mode.RAW; start += 40) {
                int end = Math.min(stroke.size(), start + 40);
                short[] xs = new short[end - start], ys = new short[end - start];
                for (int i = start; i < end; i++) {
                    xs[i - start] = (short) FleshingSurface.quantize(stroke.get(i)[0]);
                    ys[i - start] = (short) FleshingSurface.quantize(stroke.get(i)[1]);
                    if (i == 0) predicted.begin(xs[i - start], ys[i - start]);
                    else predicted.drag(xs[i - start], ys[i - start]);
                }
                int flags = (start == 0 ? WorkAction.BEGIN : 0) | (end == stroke.size() ? WorkAction.END : 0);
                if (end == stroke.size() || predicted.isComplete()) predicted.end();
                op.send(WorkAction.FLESH, flags, new int[0], xs, ys, null, null);
                // Some seeds finish before the sixth stroke. Completion deliberately clears work() and retains
                // the rendered surface in scrapedLook(); compare each accepted batch, including that transition.
                FleshingSurface server = rack.mode() == TannersRackBlockEntity.Mode.SCRAPED ? rack.scrapedLook()
                        : rack.work() == null ? null : rack.work().surface();
                h.assertTrue(server != null, "The accepted batch retains a raw or completed hide surface");
                h.assertTrue(predicted.save().equals(server.save()) && predicted.score() == server.score(),
                        "Prediction matches batch " + start + " of stroke " + s + " (seed " + predicted.seed() + ")");
            }
        }
        h.succeed();
    }

    /**
     * A release whose batch brings no new point (the tool did not move since the last batch went out) still lets the
     * thread go where it was drawn and decides the stitch. The client smoke test found such releases refused, leaving
     * the thread in hand and every later stitch out of order.
     */
    @GameTest(template = "empty")
    public static void aReleaseWithoutNewPointsStillDecidesTheStitch(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.LEATHERWORKERS_BENCH.get());
        LeatherworkersBenchBlockEntity bench = (LeatherworkersBenchBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        FakePlayer player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.AWL.get()));
        player.getInventory().add(new ItemStack(ModItems.TANNERS_KNIFE.get()));
        player.getInventory().add(ModItems.leather(Finish.SUPPLE).create(50, null, 4));
        TestSupport.Operator op = TestSupport.operate(h, player);
        op.send(WorkAction.BENCH_PREPARE, 0, new int[]{Finish.SUPPLE.ordinal()}, new short[0], new short[0],
                ImmersiveTanning.id("leatherworking/leather_boots"), null);
        TestSupport.cutCleanly(op, bench);
        TestSupport.seatCleanly(op, bench);
        float[] f = bench.job().stitchFrame(0);
        float pull = bench.job().pattern().joinOf(0).pullIdeal();
        op.send(WorkAction.BENCH_STITCH, WorkAction.BEGIN, new int[]{0}, new short[]{plane(f[4])}, new short[]{plane(f[5])}, null, null);
        op.send(WorkAction.BENCH_STITCH, 0, new int[]{0}, new short[]{plane(f[4] + f[6] * pull)}, new short[]{plane(f[5] + f[7] * pull)}, null, null);
        h.assertTrue(bench.job().pending() == 0, "The thread is drawn");
        op.send(WorkAction.BENCH_STITCH, WorkAction.END, new int[]{0}, new short[0], new short[0], null, null);
        h.assertTrue(bench.job().pending() < 0 && bench.job().nextStitch() == 1 && bench.job().tension(0) == LeatherworkJob.Tension.CLEAN,
                "A release without new points lets go where the thread was: an even stitch, got " + bench.job().tension(0));
        op.send(WorkAction.BENCH_STITCH, WorkAction.BEGIN | WorkAction.END, new int[]{1}, new short[0], new short[0], null, null);
        h.assertTrue(bench.job().pending() < 0 && bench.job().nextStitch() == 1, "A press always needs its point");
        h.succeed();
    }

    private static short plane(float v) {
        return com.otectus.immersivetanning.network.packet.StationCommandPacket.quantizePlane(v);
    }

    /** At the bench, a thread let go of because the player left is not a stitch; leaving keeps every accepted step. */
    @GameTest(template = "empty")
    public static void leavingMidStitchKeepsTheWorkAndDecidesNothing(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.LEATHERWORKERS_BENCH.get());
        LeatherworkersBenchBlockEntity bench = (LeatherworkersBenchBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        FakePlayer player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.AWL.get()));
        player.getInventory().add(new ItemStack(ModItems.TANNERS_KNIFE.get()));
        player.getInventory().add(ModItems.leather(Finish.SUPPLE).create(50, null, 4));
        TestSupport.Operator op = TestSupport.operate(h, player);
        op.send(WorkAction.BENCH_PREPARE, 0, new int[]{Finish.SUPPLE.ordinal()}, new short[0], new short[0],
                ImmersiveTanning.id("leatherworking/leather_boots"), null);
        TestSupport.cutCleanly(op, bench);
        TestSupport.seatCleanly(op, bench);
        float[] f = bench.job().stitchFrame(0);
        op.send(WorkAction.BENCH_STITCH, WorkAction.BEGIN, new int[]{0},
                new short[]{com.otectus.immersivetanning.network.packet.StationCommandPacket.quantizePlane(f[4])},
                new short[]{com.otectus.immersivetanning.network.packet.StationCommandPacket.quantizePlane(f[5])}, null, null);
        h.assertTrue(bench.job().pending() == 0, "The awl is through the first holes");
        op.act(WorkAction.RELEASE);
        h.assertTrue(bench.job() != null && bench.job().pending() < 0 && bench.job().nextStitch() == 0
                && bench.job().stage() == LeatherworkJob.Stage.STITCHING, "Leaving lets go of the thread; cuts and seats stay");
        h.succeed();
    }
}
