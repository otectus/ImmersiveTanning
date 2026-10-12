package com.otectus.immersivetanning.test;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.minigame.PressGate;
import com.otectus.immersivetanning.minigame.SeamPatterns;
import com.otectus.immersivetanning.minigame.SessionTiming;
import com.otectus.immersivetanning.minigame.StitchPattern;
import com.otectus.immersivetanning.minigame.StitchRun;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.lwjgl.glfw.GLFW;

/**
 * One press, one stitch: where an early press is ignored, spoils or scores; key repeats and a click landing with
 * Space count once; and the client's prediction converges on the server's verdict when latency shifts a press.
 */
@GameTestHolder(ImmersiveTanning.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StitchInputGameTests {

    @GameTest(template = "empty")
    public static void earlyPressIsIgnoredWithoutUsingUpTheStitch(GameTestHelper h) {
        for (net.minecraft.resources.ResourceLocation id : SeamPatterns.ids()) {
            StitchPattern pattern = SeamPatterns.get(id);
            int window = pattern.windowMs();
            StitchRun run = new StitchRun(pattern);
            int first = run.time(0);
            StitchRun.Press ignored = run.press(first - 2 * window - 1);
            h.assertTrue(ignored.outcome() == StitchRun.Outcome.IGNORED && run.next() == 0 && Float.isNaN(run.accuracy(0)),
                    pattern.id() + ": a press more than two windows early does nothing");
            StitchRun.Press hit = run.press(first);
            h.assertTrue(hit.outcome() == StitchRun.Outcome.HIT && hit.index() == 0 && run.accuracy(0) == 1F,
                    pattern.id() + ": the next press on the beat still scores the same stitch");

            StitchRun spoil = new StitchRun(pattern);
            StitchRun.Press miss = spoil.press(first - window - 1);
            h.assertTrue(miss.outcome() == StitchRun.Outcome.MISS && spoil.next() == 1, pattern.id() + ": just before the window spoils it");
            StitchRun edge = new StitchRun(pattern);
            h.assertTrue(edge.press(first - window).outcome() == StitchRun.Outcome.HIT, pattern.id() + ": the window's edge scores");

            // A press right after a stitch is never early enough to spoil the next one: it is ignored, not a miss.
            for (int i = 0; i + 1 < run.count(); i++) {
                int gap = run.time(i + 1) - run.time(i);
                h.assertTrue(gap > 2 * window, pattern.id() + ": stitches " + i + "/" + (i + 1) + " are " + gap
                        + " ms apart, more than two windows (" + 2 * window + ")");
            }
        }
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
        h.assertTrue(gate.click(3100 + 375), "A click on the next stitch, 375 ms later, is its own press");
        h.assertTrue(gate.click(3100 + 375 + PressGate.DUPLICATE_MS), "Deliberate presses one duplicate window apart both count");
        gate.keyUp(GLFW.GLFW_KEY_SPACE);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void clientPredictionConvergesOnTheServersVerdict(GameTestHelper h) {
        StitchPattern pattern = SeamPatterns.get(ImmersiveTanning.id("generic"));
        int first = pattern.stitchTimes()[0];
        int window = pattern.windowMs();

        // The client sees a press as ignored; network jitter beyond the tolerance makes the server see it later,
        // inside the spoil band. The server's reply moves the client forward and marks the stitch missed.
        StitchRun client = new StitchRun(pattern);
        StitchRun server = new StitchRun(pattern);
        int clientMs = first - 2 * window - 20;
        int jitterMs = 150;
        long serverElapsed = clientMs + jitterMs + SessionTiming.graceMs(0);
        int validated = SessionTiming.validate(clientMs, serverElapsed, 0, 0);
        StitchRun.Press predicted = client.press(clientMs);
        StitchRun.Press verdict = server.press(validated);
        h.assertTrue(predicted.outcome() == StitchRun.Outcome.IGNORED && verdict.outcome() == StitchRun.Outcome.MISS,
                "The jittered press is ignored locally but spoils the stitch on the server (validated " + validated + ")");
        client.correct(verdict.index(), verdict.accuracy(), server.next());
        h.assertTrue(client.next() == server.next() && client.accuracy(0) == 0F, "The correction brings the client level with the server");

        // In-flight presses stay valid: a correction for an older press never moves the client backwards.
        StitchRun.Press ahead = client.press(pattern.stitchTimes()[1]);
        client.correct(verdict.index(), verdict.accuracy(), 1);
        h.assertTrue(ahead.outcome() == StitchRun.Outcome.HIT && client.next() == 2, "A stale correction does not undo a newer press");

        // With honest timing (no jitter) client and server judge every press identically.
        StitchRun a = new StitchRun(pattern);
        StitchRun b = new StitchRun(pattern);
        for (int i = 0; i < a.count(); i++) {
            int t = a.time(i) + (i % 3 - 1) * 60;
            StitchRun.Press pa = a.press(t);
            StitchRun.Press pb = b.press(SessionTiming.validate(t, t, 0, 0));
            h.assertTrue(pa.outcome() == pb.outcome() && pa.index() == pb.index() && pa.accuracy() == pb.accuracy(),
                    "Press " + i + " is judged the same on both sides");
        }
        h.assertTrue(a.score() == b.score(), "Same Workmanship on both sides");
        h.succeed();
    }
}
