package com.otectus.immersivetanning.station;

import com.otectus.immersivetanning.leather.Finish;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Tanner's Rack's work surfaces in local pixels (north-facing model, see {@link StationFrame}).
 *
 * <p><b>The fleshing drape</b> is the one canonical surface of fleshing: the raw hide lies over the low beam, a little
 * way down its back, over the crest and down the front. It is an extrusion along x of a path in (z, y), so a pointer
 * ray meets it exactly and the same (u, v) is the blade's contact, the scoring cell and the texel drawn there. u runs
 * to the viewer's right (local -x), v from behind the crest down the front. The deformations the renderer adds (sag,
 * compression under the blade) are a fraction of a pixel and never move the scoring target.</p>
 *
 * <p><b>The frame</b> holds a wet hide upright at z = 8 for finishing: the tension lever on the viewer's right post
 * picks one of three detents, the cord toggle and the slicker sit on the low bar, and the lever's hub seats the
 * mechanism.</p>
 */
public final class RackGeometry {
    /** Fleshing grid across the beam: local x of u = 0 (viewer's left) and u = 1. */
    public static final float FLESH_X_LEFT = 14.0F;
    public static final float FLESH_X_RIGHT = 2.0F;
    /** The drape's path in (z, y) pixels, behind the crest to the bottom of the front face. */
    private static final float[][] PATH = {
            {3.6F, 5.6F}, {2.9F, 6.35F}, {2.0F, 6.6F}, {1.1F, 6.35F}, {0.7F, 5.6F}, {0.55F, 4.0F}, {0.5F, 2.6F}, {0.55F, 1.2F}};
    private static final float[] ARC = new float[PATH.length];
    public static final float PATH_LENGTH;

    static {
        float total = 0F;
        ARC[0] = 0F;
        for (int i = 1; i < PATH.length; i++) {
            total += Mth.sqrt(sq(PATH[i][0] - PATH[i - 1][0]) + sq(PATH[i][1] - PATH[i - 1][1]));
            ARC[i] = total;
        }
        PATH_LENGTH = total;
    }

    /** The frame's work area (the wet hide's plane) for finishing gestures, local pixels. */
    public static final float FRAME_Z = 8.0F;
    public static final float FRAME_X_LEFT = 12.6F;
    public static final float FRAME_X_RIGHT = 3.4F;
    public static final float FRAME_TOP = 13.4F;
    public static final float FRAME_BOTTOM = 4.6F;

    /** The tension lever: pivot on the viewer's right post, a 4-pixel handle swinging in the y-z plane. */
    public static final Vec3 LEVER_PIVOT = new Vec3(0.4, 9.0, 7.6);
    public static final float LEVER_LENGTH = 4.2F;
    /** Lever angle of each detent in degrees (positive raises the handle): Supple slack, Harness even, Hardened taut. */
    public static final float[] DETENT_DEGREES = {38F, 0F, -38F};
    public static final float LEVER_MIN = -48F;
    public static final float LEVER_MAX = 48F;

    /** The cord toggle Harness lacing is drawn with, hanging under the frame's low bar. */
    public static final Vec3 CORD_TOGGLE = new Vec3(8.0, 2.3, 7.0);
    /** The slicker / burnishing block resting on the low bar, viewer's left. */
    public static final Vec3 SLICKER = new Vec3(12.2, 4.6, 6.6);

    // ------------------------------------------------------------------ fleshing drape

    /** The drape point at (u, v) in local pixels: {x, y, z}. */
    public static float[] fleshPoint(float u, float v) {
        float[] zy = pathPoint(Mth.clamp(v, 0F, 1F) * PATH_LENGTH);
        return new float[]{Mth.lerp(Mth.clamp(u, 0F, 1F), FLESH_X_LEFT, FLESH_X_RIGHT), zy[1], zy[0]};
    }

    /** The outward normal (away from the beam, towards the viewer) at v, local: {x, y, z}. */
    public static float[] fleshNormal(float v) {
        int i = segmentAt(Mth.clamp(v, 0F, 1F) * PATH_LENGTH);
        float dz = PATH[i + 1][0] - PATH[i][0];
        float dy = PATH[i + 1][1] - PATH[i][1];
        float len = Mth.sqrt(dz * dz + dy * dy);
        return new float[]{0F, -dz / len, dy / len};
    }

    /** The tangent along v (down the drape), local: {x, y, z}. */
    public static float[] fleshTangent(float v) {
        int i = segmentAt(Mth.clamp(v, 0F, 1F) * PATH_LENGTH);
        float dz = PATH[i + 1][0] - PATH[i][0];
        float dy = PATH[i + 1][1] - PATH[i][1];
        float len = Mth.sqrt(dz * dz + dy * dy);
        return new float[]{0F, dy / len, dz / len};
    }

    private static float[] pathPoint(float arc) {
        int i = segmentAt(arc);
        float seg = ARC[i + 1] - ARC[i];
        float t = seg <= 0F ? 0F : Mth.clamp((arc - ARC[i]) / seg, 0F, 1F);
        return new float[]{Mth.lerp(t, PATH[i][0], PATH[i + 1][0]), Mth.lerp(t, PATH[i][1], PATH[i + 1][1])};
    }

    private static int segmentAt(float arc) {
        for (int i = 0; i + 2 < PATH.length; i++) {
            if (arc <= ARC[i + 1]) return i;
        }
        return PATH.length - 2;
    }

    /**
     * Where a ray (local pixels) meets the drape: {u, v, t}, or null when it misses. With {@code clamp} a ray that
     * misses (the blade slid past an edge) lands on the nearest edge instead, so a stroke that leaves the hide still
     * has a position - the surface counts that path as off the hide.
     */
    @Nullable
    public static float[] intersectFlesh(Vec3 origin, Vec3 dir, boolean clamp) {
        float best = Float.MAX_VALUE;
        float[] hit = null;
        int last = PATH.length - 2;
        for (int i = 0; i <= last; i++) {
            float az = PATH[i][0];
            float ay = PATH[i][1];
            float ez = PATH[i + 1][0] - az;
            float ey = PATH[i + 1][1] - ay;
            double det = -dir.z * ey + ez * dir.y;
            if (Math.abs(det) < 1.0E-7) continue;
            double t = (-(az - origin.z) * ey + ez * (ay - origin.y)) / det;
            double s = (dir.z * (ay - origin.y) - (az - origin.z) * dir.y) / det;
            boolean extendLow = clamp && i == 0;
            boolean extendHigh = clamp && i == last;
            if (t <= 0 || (!extendLow && s < 0) || (!extendHigh && s > 1)) continue;
            double x = origin.x + t * dir.x;
            float u = (float) ((FLESH_X_LEFT - x) / (FLESH_X_LEFT - FLESH_X_RIGHT));
            if (!clamp && (u < 0F || u > 1F)) continue;
            if (t < best) {
                float arc = ARC[i] + (float) s * (ARC[i + 1] - ARC[i]);
                best = (float) t;
                hit = new float[]{Mth.clamp(u, 0F, 1F), Mth.clamp(arc / PATH_LENGTH, 0F, 1F), (float) t};
            }
        }
        return hit;
    }

    /** The bounds of the drape, for render culling and hover boxes (local pixels). */
    public static AABB fleshBounds() {
        return new AABB(FLESH_X_RIGHT, 0.8, 0.2, FLESH_X_LEFT, 7.0, 4.0);
    }

    // ------------------------------------------------------------------ frame

    /** A frame point from normalized (fu to the viewer's right, fv down) in local pixels. */
    public static float[] framePoint(float fu, float fv) {
        return new float[]{Mth.lerp(fu, FRAME_X_LEFT, FRAME_X_RIGHT), Mth.lerp(fv, FRAME_TOP, FRAME_BOTTOM), FRAME_Z};
    }

    /** Where a ray meets the frame plane: {fu, fv, t}, normalized and clamped, or null for a ray parallel or behind. */
    @Nullable
    public static float[] intersectFrame(Vec3 origin, Vec3 dir) {
        if (Math.abs(dir.z) < 1.0E-7) return null;
        double t = (FRAME_Z - origin.z) / dir.z;
        if (t <= 0) return null;
        double x = origin.x + dir.x * t;
        double y = origin.y + dir.y * t;
        float fu = (float) ((FRAME_X_LEFT - x) / (FRAME_X_LEFT - FRAME_X_RIGHT));
        float fv = (float) ((FRAME_TOP - y) / (FRAME_TOP - FRAME_BOTTOM));
        return new float[]{fu, fv, (float) t};
    }

    /** The lever handle's end at an angle (degrees), local pixels. */
    public static Vec3 leverEnd(float degrees) {
        float r = degrees * Mth.DEG_TO_RAD;
        return LEVER_PIVOT.add(0, Mth.sin(r) * LEVER_LENGTH, -Mth.cos(r) * LEVER_LENGTH);
    }

    /** The lever angle (degrees) a ray points the handle to: the ray meets the lever's plane x = pivot.x. */
    public static float leverAngle(Vec3 origin, Vec3 dir, float fallback) {
        if (Math.abs(dir.x) < 1.0E-6) return fallback;
        double t = (LEVER_PIVOT.x - origin.x) / dir.x;
        if (t <= 0) return fallback;
        double y = origin.y + dir.y * t - LEVER_PIVOT.y;
        double z = origin.z + dir.z * t - LEVER_PIVOT.z;
        float angle = (float) Math.toDegrees(Math.atan2(y, -z));
        return Mth.clamp(angle, LEVER_MIN, LEVER_MAX);
    }

    /** The nearest detent to a lever angle. */
    public static Finish nearestDetent(float degrees) {
        int best = 0;
        for (int i = 1; i < DETENT_DEGREES.length; i++) {
            if (Math.abs(degrees - DETENT_DEGREES[i]) < Math.abs(degrees - DETENT_DEGREES[best])) best = i;
        }
        return Finish.byOrdinal(best);
    }

    public static float detentDegrees(Finish finish) {
        return DETENT_DEGREES[finish.ordinal()];
    }

    /** Hover box of the lever handle at an angle, local pixels. */
    public static AABB leverBox(float degrees) {
        Vec3 end = leverEnd(degrees);
        return new AABB(LEVER_PIVOT, end).inflate(0.9);
    }

    public static AABB hubBox() {
        return new AABB(LEVER_PIVOT, LEVER_PIVOT).inflate(1.1);
    }

    public static AABB cordBox() {
        return new AABB(CORD_TOGGLE, CORD_TOGGLE).inflate(1.2, 1.4, 1.0);
    }

    public static AABB slickerBox() {
        return new AABB(SLICKER, SLICKER).inflate(1.4, 0.8, 1.0);
    }

    public static AABB frameBox() {
        return new AABB(FRAME_X_RIGHT, FRAME_BOTTOM, FRAME_Z - 1.2, FRAME_X_LEFT, FRAME_TOP, FRAME_Z + 1.2);
    }

    private static float sq(float v) {
        return v * v;
    }

    private RackGeometry() {}
}
