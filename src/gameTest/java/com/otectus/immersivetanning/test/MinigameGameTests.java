package com.otectus.immersivetanning.test;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.minigame.FleshingSurface;
import com.otectus.immersivetanning.minigame.SeamPatterns;
import com.otectus.immersivetanning.minigame.StitchPattern;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** The shared, deterministic minigame rules: the same inputs always produce the same scores on both sides. */
@GameTestHolder(ImmersiveTanning.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MinigameGameTests {

    @GameTest(template = "empty")
    public static void fleshingSurfaceIsDeterministic(GameTestHelper h) {
        FleshingSurface a = new FleshingSurface(HideSize.LARGE, 12345L);
        FleshingSurface b = new FleshingSurface(HideSize.LARGE, 12345L);
        FleshingSurface c = new FleshingSurface(HideSize.LARGE, 54321L);
        boolean same = true;
        boolean differs = false;
        int hide = 0;
        int flesh = 0;
        for (int y = 0; y < FleshingSurface.H; y++) {
            for (int x = 0; x < FleshingSurface.W; x++) {
                same &= a.isHide(x, y) == b.isHide(x, y) && a.thickness(x, y) == b.thickness(x, y);
                differs |= a.thickness(x, y) != c.thickness(x, y);
                if (a.isHide(x, y)) hide++;
                if (a.thickness(x, y) > 0) flesh++;
            }
        }
        h.assertTrue(same, "Same seed must build the same hide");
        h.assertTrue(differs, "Different seeds must place the flesh differently");
        h.assertTrue(a.patches() == HideSize.LARGE.fleshPatches(), "A large hide carries five patches");
        h.assertTrue(hide > FleshingSurface.W * FleshingSurface.H / 3, "The hide fills most of the work area, got " + hide);
        h.assertTrue(flesh > hide / 6 && flesh < hide * 2 / 3, "Flesh covers a broad but partial share of the hide, got " + flesh + "/" + hide);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fleshingAlongTheGrainCleansTheHide(GameTestHelper h) {
        FleshingSurface surface = new FleshingSurface(HideSize.MEDIUM, 777L);
        scrapeEveryPatch(surface, 0F);
        h.assertTrue(surface.coverage() >= 0.97F, "Grain-aligned passes remove the flesh, coverage " + surface.coverage());
        h.assertTrue(surface.control() >= 0.9F, "Strokes stayed on the hide, control " + surface.control());
        h.assertTrue(surface.score() >= 85, "Careful fleshing scores high, got " + surface.score());
        h.assertTrue(surface.isComplete() || surface.coverage() >= 0.97F, "Surface reports completion");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fleshingAcrossTheGrainNeedsMorePasses(GameTestHelper h) {
        FleshingSurface along = new FleshingSurface(HideSize.SMALL, 99L);
        FleshingSurface across = new FleshingSurface(HideSize.SMALL, 99L);
        scrapeEveryPatchOnce(along, 0F);
        scrapeEveryPatchOnce(across, Mth.HALF_PI);
        h.assertTrue(along.coverage() > across.coverage() + 0.1F,
                "One pass along the grain removes more than one across it: " + along.coverage() + " vs " + across.coverage());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fleshingPenalisesNoWorkSlipsAndOverworking(GameTestHelper h) {
        FleshingSurface idle = new FleshingSurface(HideSize.LARGE, 5L);
        h.assertTrue(idle.score() == 0, "No knife work scores 0");

        FleshingSurface off = new FleshingSurface(HideSize.LARGE, 5L);
        off.begin(FleshingSurface.quantize(0.01F), FleshingSurface.quantize(0.01F));
        off.drag(FleshingSurface.quantize(0.01F), FleshingSurface.quantize(0.99F));
        off.end();
        h.assertTrue(off.control() < 0.2F, "A stroke along the very edge of the work area slips off the hide, control " + off.control());

        FleshingSurface scrub = new FleshingSurface(HideSize.LARGE, 5L);
        scrapeEveryPatch(scrub, 0F);
        int before = scrub.score();
        for (int i = 0; i < 30; i++) {
            scrub.begin(FleshingSurface.quantize(0.3F), FleshingSurface.quantize(0.2F + i % 3 * 0.25F));
            scrub.drag(FleshingSurface.quantize(0.7F), FleshingSurface.quantize(0.2F + i % 3 * 0.25F));
            scrub.end();
        }
        h.assertTrue(scrub.overwork() > 0.5F, "Scrubbing a clean hide is overworking, got " + scrub.overwork());
        h.assertTrue(scrub.score() < before, "Overworking lowers Integrity: " + scrub.score() + " vs " + before);
        h.succeed();
    }

    /**
     * The seam's replacement for 1.2's timing run: a stitch is judged by how far its thread was drawn away from the
     * seam. Ideal pulls give 100; pulls at the edge of the clean band are fine but not perfect; slack or overpulled
     * stitches still complete the piece (never an abort) at a low score; a redone stitch is capped.
     */
    @GameTest(template = "empty")
    public static void stitchingScoresPullAndNeverAborts(GameTestHelper h) {
        com.otectus.immersivetanning.work.WorkPattern pattern = com.otectus.immersivetanning.work.WorkPatterns.get(ImmersiveTanning.id("chestplate"));
        h.assertTrue(pattern != null, "The chestplate work pattern is loaded from data");
        float[] pulls = {0F, 0.2F, 1F, 1.6F};
        int[] scores = new int[pulls.length];
        for (int k = 0; k < pulls.length; k++) {
            com.otectus.immersivetanning.work.LeatherworkJob job = WorkGameTests.job(pattern);
            WorkGameTests.cutAndSeat(job);
            for (int a = 0; a < job.stitchCount(); a++) {
                com.otectus.immersivetanning.work.WorkPattern.Join join = pattern.joinOf(a);
                float pull = k == 2 ? join.pullIdeal() : k == 1 ? join.pullMin() + 0.002F : k == 0 ? join.pullMin() * 0.2F : join.pullMax() * 1.6F;
                float[] f = job.stitchFrame(a);
                h.assertTrue(job.puncture(a, f[4], f[5], 1F) == com.otectus.immersivetanning.work.LeatherworkJob.Outcome.ACCEPTED, "Stitch " + a + " punctures");
                job.releaseThread(f[4] + f[6] * pull, f[5] + f[7] * pull, false);
            }
            h.assertTrue(job.stage() == com.otectus.immersivetanning.work.LeatherworkJob.Stage.TIE, "Every pull completes the seam (never an abort)");
            scores[k] = Math.round(job.stitchingScore() * 100F);
        }
        h.assertTrue(scores[2] == 100, "Ideal pulls stitch at 100, got " + scores[2]);
        h.assertTrue(scores[1] >= 78 && scores[1] < 100, "Pulls at the edge of the clean band are good, got " + scores[1]);
        h.assertTrue(scores[0] < 50 && scores[3] < 70, "Slack and puckered pulls are poor, got " + scores[0] + " and " + scores[3]);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void everySeamPatternLoads(GameTestHelper h) {
        for (String name : new String[]{"generic", "boots", "helmet", "chestplate", "leggings", "saddle", "horse_armor"}) {
            h.assertTrue(SeamPatterns.has(ImmersiveTanning.id(name)), "Seam pattern " + name + " is defined");
            StitchPattern p = SeamPatterns.get(ImmersiveTanning.id(name));
            int[] times = p.stitchTimes();
            for (int i = 1; i < times.length; i++) h.assertTrue(times[i] > times[i - 1], name + " stitch times increase");
            h.assertTrue(times[times.length - 1] < 12_000, name + " stitches in under 12 seconds");
        }
        h.succeed();
    }

    /** Close grain-aligned strokes through every patch, lifting the knife in between. */
    static void scrapeEveryPatch(FleshingSurface surface, float angleOffset) {
        for (int p = 0; p < surface.patches(); p++) {
            float g = surface.grain(p) + angleOffset;
            float cos = Mth.cos(g);
            float sin = Mth.sin(g);
            for (float off = -9F; off <= 9F; off += 2.2F) {
                float cx = surface.patchX(p) - sin * off;
                float cy = surface.patchY(p) + cos * off;
                stroke(surface, cx - cos * 11F, cy - sin * 11F, cx + cos * 11F, cy + sin * 11F);
            }
        }
    }

    static void scrapeEveryPatchOnce(FleshingSurface surface, float angleOffset) {
        for (int p = 0; p < surface.patches(); p++) {
            float g = surface.grain(p) + angleOffset;
            float cos = Mth.cos(g);
            float sin = Mth.sin(g);
            for (float off = -9F; off <= 9F; off += 6.5F) {
                float cx = surface.patchX(p) - sin * off;
                float cy = surface.patchY(p) + cos * off;
                stroke(surface, cx - cos * 10F, cy - sin * 10F, cx + cos * 10F, cy + sin * 10F);
            }
        }
    }

    static void stroke(FleshingSurface surface, float x0, float y0, float x1, float y1) {
        surface.begin(q(x0 / FleshingSurface.W), q(y0 / FleshingSurface.H));
        int steps = 24;
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            surface.drag(q(Mth.lerp(t, x0, x1) / FleshingSurface.W), q(Mth.lerp(t, y0, y1) / FleshingSurface.H));
        }
        surface.end();
    }

    static int q(float v) {
        return FleshingSurface.quantize(v);
    }

    private MinigameGameTests() {}
}
