package com.otectus.immersivetanning.minigame;

import com.otectus.immersivetanning.hide.HideSize;
import net.minecraft.util.Mth;

import java.util.Random;

/**
 * The hide being fleshed, shared by client and server. Both sides build it from the same seed and replay the same
 * quantised knife path, so the client's picture and the server's score always agree; only the server's score counts.
 *
 * <p>The hide carries a few broad patches of flesh. Dragging the knife removes flesh under a broad blade; each patch
 * has a grain, and a pass along the grain removes the patch in one stroke while a pass across it needs two. Three
 * things are measured and folded into Integrity:</p>
 * <ul>
 *     <li><b>Coverage</b> - how much of the flesh was removed;</li>
 *     <li><b>Control</b> - how much of the knife path stayed on the hide;</li>
 *     <li><b>Overworking</b> - how much of the path on the hide scraped without removing anything.</li>
 * </ul>
 * The cell grid is only the simulation resolution: the blade covers dozens of cells, so there are no small targets.
 */
public final class FleshingSurface {
    public static final int W = 64;
    public static final int H = 48;
    /** Knife positions travel as 12-bit fixed point per axis. */
    public static final int COORD_MAX = 4095;
    public static final float BLADE_RADIUS = 3.4F;
    public static final float COMPLETE_COVERAGE = 0.985F;
    private static final float EPS = 1.0E-4F;
    private static final float WORK_THRESHOLD = 0.02F;
    private static final float OVERWORK_ALLOWANCE = 0.35F;
    private static final float OVERWORK_RANGE = 0.5F;

    private final HideSize size;
    private final long seed;
    private final boolean[] hide = new boolean[W * H];
    private final byte[] region = new byte[W * H];
    private final float[] thickness = new float[W * H];
    private final int[] lastSegment = new int[W * H];
    private final float[] grain;
    private final float[] patchX;
    private final float[] patchY;
    private final float total;

    private boolean stroking;
    private float lastX;
    private float lastY;
    private int segment;
    private int strokeStart;
    private float removed;
    private float workLength;
    private float idleLength;
    private float offLength;
    private int version;

    public FleshingSurface(HideSize size, long seed) {
        this.size = size;
        this.seed = seed;
        Random random = new Random(seed);
        java.util.Arrays.fill(region, (byte) -1);
        java.util.Arrays.fill(lastSegment, Integer.MIN_VALUE);

        // Hide outline: an irregular oval with four leg lobes, a neck and a tail.
        float cx = W / 2F;
        float cy = H / 2F;
        float scale = switch (size) {
            case SMALL -> 0.84F;
            case MEDIUM -> 0.92F;
            case LARGE -> 1.0F;
        };
        float rx = W * 0.44F * scale;
        float ry = H * 0.41F * scale;
        float p1 = random.nextFloat() * Mth.TWO_PI;
        float p2 = random.nextFloat() * Mth.TWO_PI;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float u = (x + 0.5F - cx) / rx;
                float v = (y + 0.5F - cy) / ry;
                float angle = (float) StrictMath.atan2(v, u);
                float r = Mth.sqrt(u * u + v * v);
                hide[x + y * W] = r < outline(angle, p1, p2);
            }
        }

        // Flesh patches: broad, well separated blobs inside the hide.
        int count = size.fleshPatches();
        grain = new float[count];
        patchX = new float[count];
        patchY = new float[count];
        float minDistance = 11F * scale;
        for (int i = 0; i < count; i++) {
            float px = cx;
            float py = cy;
            for (int attempt = 0; attempt < 60; attempt++) {
                px = cx + (random.nextFloat() * 2F - 1F) * rx * 0.62F;
                py = cy + (random.nextFloat() * 2F - 1F) * ry * 0.58F;
                boolean clear = true;
                for (int j = 0; j < i; j++) {
                    float dx = px - patchX[j];
                    float dy = py - patchY[j];
                    if (dx * dx + dy * dy < minDistance * minDistance) {
                        clear = false;
                        break;
                    }
                }
                if (clear) break;
            }
            patchX[i] = px;
            patchY[i] = py;
            grain[i] = random.nextFloat() * Mth.PI;
            float a = (5.5F + random.nextFloat() * 3.0F) * scale;
            float b = (4.0F + random.nextFloat() * 2.2F) * scale;
            float rot = random.nextFloat() * Mth.PI;
            float q1 = random.nextFloat() * Mth.TWO_PI;
            float q2 = random.nextFloat() * Mth.TWO_PI;
            float cos = Mth.cos(rot);
            float sin = Mth.sin(rot);
            int x0 = Math.max(0, (int) (px - a - 2));
            int x1 = Math.min(W - 1, (int) (px + a + 2));
            int y0 = Math.max(0, (int) (py - a - 2));
            int y1 = Math.min(H - 1, (int) (py + a + 2));
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    int c = x + y * W;
                    if (!hide[c] || region[c] >= 0) continue;
                    float dx = x + 0.5F - px;
                    float dy = y + 0.5F - py;
                    float u = (dx * cos + dy * sin) / a;
                    float v = (-dx * sin + dy * cos) / b;
                    float angle = (float) StrictMath.atan2(v, u);
                    float edge = 1F + 0.12F * Mth.sin(3 * angle + q1) + 0.07F * Mth.sin(5 * angle + q2);
                    if (u * u + v * v < edge * edge) {
                        region[c] = (byte) i;
                        thickness[c] = 1F;
                    }
                }
            }
        }
        float sum = 0F;
        for (float t : thickness) sum += t;
        total = Math.max(1F, sum);
    }

    private static float outline(float angle, float p1, float p2) {
        float r = 1F + 0.05F * Mth.sin(3 * angle + p1) + 0.035F * Mth.sin(5 * angle + p2);
        for (float leg : new float[]{Mth.PI / 4F, 3F * Mth.PI / 4F, -Mth.PI / 4F, -3F * Mth.PI / 4F}) {
            float d = Mth.wrapDegrees((angle - leg) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
            r += 0.2F * (float) StrictMath.exp(-(d / 0.17F) * (d / 0.17F));
        }
        float neck = Mth.wrapDegrees(angle * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        r += 0.1F * (float) StrictMath.exp(-(neck / 0.3F) * (neck / 0.3F));
        float tail = Mth.wrapDegrees((angle - Mth.PI) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        r += 0.07F * (float) StrictMath.exp(-(tail / 0.12F) * (tail / 0.12F));
        return r;
    }

    // ------------------------------------------------------------------ knife path

    /** Quantises a normalised coordinate exactly as it is sent over the network. */
    public static int quantize(float normalized) {
        return Mth.clamp(Math.round(normalized * COORD_MAX), 0, COORD_MAX);
    }

    /** Places the knife on the hide (mouse pressed). Coordinates are quantised, see {@link #quantize}. */
    public void begin(int qx, int qy) {
        stroking = true;
        lastX = qx / (float) COORD_MAX * W;
        lastY = qy / (float) COORD_MAX * H;
        strokeStart = segment + 1;
    }

    /** Drags the knife to a new point of the current stroke. Ignored when no stroke is in progress. */
    public void drag(int qx, int qy) {
        if (!stroking) return;
        float x = qx / (float) COORD_MAX * W;
        float y = qy / (float) COORD_MAX * H;
        float dx = x - lastX;
        float dy = y - lastY;
        float length = Mth.sqrt(dx * dx + dy * dy);
        if (length < EPS) return;
        segment++;

        int steps = Math.max(1, Mth.ceil(length / 0.5F));
        float off = 0F;
        for (int i = 0; i < steps; i++) {
            float t = (i + 0.5F) / steps;
            if (!onHide(lastX + dx * t, lastY + dy * t)) off += length / steps;
        }

        float direction = (float) StrictMath.atan2(dy, dx);
        float before = removed;
        int x0 = Math.max(0, Mth.floor(Math.min(lastX, x) - BLADE_RADIUS));
        int x1 = Math.min(W - 1, Mth.floor(Math.max(lastX, x) + BLADE_RADIUS));
        int y0 = Math.max(0, Mth.floor(Math.min(lastY, y) - BLADE_RADIUS));
        int y1 = Math.min(H - 1, Mth.floor(Math.max(lastY, y) + BLADE_RADIUS));
        float r2 = BLADE_RADIUS * BLADE_RADIUS;
        for (int cy = y0; cy <= y1; cy++) {
            for (int cx = x0; cx <= x1; cx++) {
                if (distanceSq(cx + 0.5F, cy + 0.5F, lastX, lastY, dx, dy, length) > r2) continue;
                int c = cx + cy * W;
                boolean samePass = lastSegment[c] == segment - 1 && segment - 1 >= strokeStart;
                lastSegment[c] = segment;
                if (samePass || thickness[c] <= 0F) continue;
                float along = Math.abs(Mth.cos(direction - grain[region[c]]));
                float amount = Math.min(thickness[c], 0.5F + 0.5F * along);
                thickness[c] -= amount;
                if (thickness[c] < EPS) thickness[c] = 0F;
                removed += amount;
            }
        }
        offLength += off;
        if (removed - before > WORK_THRESHOLD) workLength += length - off;
        else idleLength += length - off;
        lastX = x;
        lastY = y;
        version++;
    }

    /** Lifts the knife (mouse released). */
    public void end() {
        stroking = false;
    }

    public boolean isStroking() {
        return stroking;
    }

    private boolean onHide(float x, float y) {
        int cx = Mth.floor(x);
        int cy = Mth.floor(y);
        return cx >= 0 && cy >= 0 && cx < W && cy < H && hide[cx + cy * W];
    }

    private static float distanceSq(float px, float py, float ax, float ay, float dx, float dy, float length) {
        float t = Mth.clamp(((px - ax) * dx + (py - ay) * dy) / (length * length), 0F, 1F);
        float qx = ax + dx * t - px;
        float qy = ay + dy * t - py;
        return qx * qx + qy * qy;
    }

    // ------------------------------------------------------------------ scoring

    public float coverage() {
        return Mth.clamp(removed / total, 0F, 1F);
    }

    public float control() {
        float path = workLength + idleLength + offLength;
        return path <= EPS ? 0F : (workLength + idleLength) / path;
    }

    /** Share of the on-hide knife path that removed nothing. */
    public float overwork() {
        float onHide = workLength + idleLength;
        return onHide <= EPS ? 0F : idleLength / onHide;
    }

    public float overworkPenalty() {
        return Mth.clamp((overwork() - OVERWORK_ALLOWANCE) / OVERWORK_RANGE, 0F, 1F);
    }

    /** Integrity 0-100. No knife work at all scores 0. */
    public int score() {
        if (workLength + idleLength + offLength <= EPS) return 0;
        float raw = 0.7F * coverage() + 0.15F * control() + 0.15F * (1F - overworkPenalty());
        return Mth.clamp(Math.round(raw * 100F), 0, 100);
    }

    public boolean isComplete() {
        return coverage() >= COMPLETE_COVERAGE;
    }

    // ------------------------------------------------------------------ presentation

    public HideSize size() { return size; }
    public long seed() { return seed; }
    public boolean isHide(int x, int y) { return hide[x + y * W]; }
    public float thickness(int x, int y) { return thickness[x + y * W]; }
    public int region(int x, int y) { return region[x + y * W]; }
    public int patches() { return grain.length; }
    public float grain(int patch) { return grain[patch]; }
    public float patchX(int patch) { return patchX[patch]; }
    public float patchY(int patch) { return patchY[patch]; }
    /** Increases whenever the surface changes, so a renderer knows when to refresh its texture. */
    public int version() { return version; }
}
