package com.otectus.immersivetanning.minigame;

import net.minecraft.util.Mth;

import java.util.Arrays;

/**
 * Scoring of one stitching run, shared by client (prediction) and server (authority). A needle marker reaches each
 * stitch at a scheduled time; the leatherworker presses the awl as it arrives. Presses are matched to the next
 * unscored stitch: within the window they score by closeness, a little too early they spoil that stitch, far too
 * early they are ignored. Stitches whose window passes unpressed are missed. Misses leave an uneven stitch but never
 * abort the craft.
 */
public final class StitchRun {
    public enum Outcome { IGNORED, MISS, HIT }

    public record Press(Outcome outcome, int index, float accuracy) {}

    private final StitchPattern pattern;
    private final int[] times;
    private final float[] accuracy;
    private int next;

    public StitchRun(StitchPattern pattern) {
        this.pattern = pattern;
        this.times = pattern.stitchTimes();
        this.accuracy = new float[times.length];
        Arrays.fill(accuracy, Float.NaN);
    }

    /** Marks every stitch whose window closed before {@code timeMs} as missed. */
    public void advanceTo(int timeMs) {
        while (next < times.length && timeMs > times[next] + pattern.windowMs()) {
            accuracy[next++] = 0F;
        }
    }

    public Press press(int timeMs) {
        advanceTo(timeMs);
        if (next >= times.length) return new Press(Outcome.IGNORED, -1, 0F);
        int dt = timeMs - times[next];
        int window = pattern.windowMs();
        if (dt < -2 * window) return new Press(Outcome.IGNORED, -1, 0F);
        int index = next++;
        if (dt < -window) {
            accuracy[index] = 0F;
            return new Press(Outcome.MISS, index, 0F);
        }
        int off = Math.abs(dt);
        float value = off <= pattern.perfectMs() ? 1F
                : 1F - 0.7F * (off - pattern.perfectMs()) / (float) Math.max(1, window - pattern.perfectMs());
        accuracy[index] = Mth.clamp(value, 0.3F, 1F);
        return new Press(Outcome.HIT, index, accuracy[index]);
    }

    /**
     * Applies the server's view of one press (client correction): the stitch's accuracy is overwritten and the run
     * moves forward to the server's next stitch. It never moves backwards, so presses still in flight stay valid.
     */
    public void correct(int index, float value, int nextIndex) {
        if (index >= 0 && index < accuracy.length) accuracy[index] = value;
        int target = Mth.clamp(nextIndex, 0, times.length);
        while (next < target) {
            if (Float.isNaN(accuracy[next])) accuracy[next] = 0F;
            next++;
        }
    }

    public boolean isComplete() {
        return next >= times.length;
    }

    /** The time after which no further press can count. */
    public int endMs() {
        return times[times.length - 1] + pattern.windowMs();
    }

    public int next() { return next; }
    public int count() { return times.length; }
    public int time(int index) { return times[index]; }
    public float accuracy(int index) { return accuracy[index]; }
    public StitchPattern pattern() { return pattern; }

    /** Workmanship 0-100: mean accuracy over every stitch, unscored stitches counting as misses. */
    public int score() {
        float sum = 0F;
        for (float a : accuracy) if (!Float.isNaN(a)) sum += a;
        return Mth.clamp(Math.round(100F * sum / times.length), 0, 100);
    }

    /** Mean accuracy of the stitches scored so far, or -1 before the first. */
    public float runningAccuracy() {
        float sum = 0F;
        int n = 0;
        for (float a : accuracy) {
            if (!Float.isNaN(a)) {
                sum += a;
                n++;
            }
        }
        return n == 0 ? -1F : sum / n;
    }
}
