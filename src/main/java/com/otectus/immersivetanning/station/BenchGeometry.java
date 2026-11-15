package com.otectus.immersivetanning.station;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Leatherworker's Bench's parts in local pixels (north-facing model, see {@link StationFrame}).
 *
 * <p>The <b>work plane</b> lies just over the stitched cutting mat. Its normalized coordinates (wx to the viewer's
 * right, wy towards the viewer, 0..1 each) are the authoritative coordinates of every bench task: cut paths, panel
 * placements, holes and thread pulls. The plane is square, so distances in it are the same along both axes.</p>
 *
 * <p>Around the mat: the pattern book at the near-left corner, the output pad on the viewer's right, the scrap pile
 * behind it, the jig (a low form the panels are seated on) in the middle once cutting is done.</p>
 */
public final class BenchGeometry {
    /** Work plane height and extent (local pixels): wx 0 at x = LEFT, wy 0 at z = FAR. */
    public static final float PLANE_Y = 15.56F;
    public static final float LEFT = 12.75F;
    public static final float FAR = 12.25F;
    public static final float SIZE = 9.5F;

    public static final AABB BOOK = new AABB(13.2, 15.0, 1.2, 15.8, 16.4, 4.4);
    public static final AABB OUTPUT_PAD = new AABB(0.3, 15.0, 4.0, 2.9, 16.6, 8.0);
    public static final AABB SCRAP_PILE = new AABB(0.3, 15.0, 8.4, 2.9, 16.2, 11.6);

    /** A work-plane point (normalized) in local pixels. */
    public static Vec3 point(float wx, float wy) {
        return new Vec3(LEFT - wx * SIZE, PLANE_Y, FAR - wy * SIZE);
    }

    /** A work-plane point lifted {@code lift} pixels off the plane. */
    public static Vec3 point(float wx, float wy, float lift) {
        return new Vec3(LEFT - wx * SIZE, PLANE_Y + lift, FAR - wy * SIZE);
    }

    /** Where a ray (local pixels) meets the work plane: {wx, wy, t}; unclamped, or null for a ray that never does. */
    @Nullable
    public static float[] intersectPlane(Vec3 origin, Vec3 dir) {
        if (Math.abs(dir.y) < 1.0E-7) return null;
        double t = (PLANE_Y - origin.y) / dir.y;
        if (t <= 0) return null;
        double x = origin.x + dir.x * t;
        double z = origin.z + dir.z * t;
        return new float[]{(float) ((LEFT - x) / SIZE), (float) ((FAR - z) / SIZE), (float) t};
    }

    /** Pixels per work-plane unit. */
    public static float pixels(float units) {
        return units * SIZE;
    }

    public static boolean inPlane(float wx, float wy, float margin) {
        return wx >= -margin && wy >= -margin && wx <= 1 + margin && wy <= 1 + margin;
    }

    public static float clamp01(float v) {
        return Mth.clamp(v, 0F, 1F);
    }

    private BenchGeometry() {}
}
