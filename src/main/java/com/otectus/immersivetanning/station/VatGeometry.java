package com.otectus.immersivetanning.station;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Tanner's Vat's parts in local pixels. The vat is round-shouldered but square in plan; it has no facing, so its
 * local space is the world's (the frame of a north-facing station). The liquor surface is the work surface of the
 * paddle; the opening takes what is poured or lowered in; up to {@link #SHOWN} hides show a handle at the rim (a larger
 * batch shows a count); the front hook is where finished hides are lifted out.
 */
public final class VatGeometry {
    public static final float INNER_MIN = 2.5F;
    public static final float INNER_MAX = 13.5F;
    public static final float SURFACE_FULL = 12.0F;
    public static final float SURFACE_EMPTY = 2.3F;
    public static final float RIM = 13.0F;
    /** Hides drawn individually; the rest of a large batch is a count. */
    public static final int SHOWN = 4;
    private static final float[][] HANDLES = {{5.0F, 4.6F}, {11.0F, 4.6F}, {5.0F, 11.4F}, {11.0F, 11.4F}};
    /** The paddle rests across the back rim. */
    public static final Vec3 PADDLE_REST = new Vec3(8.0, 13.6, 14.3);
    /** Where finished hides are lifted out: the hook on the front rim. */
    public static final Vec3 HOOK = new Vec3(8.0, 13.4, 1.6);

    public static float surface(boolean water) {
        return water ? SURFACE_FULL : SURFACE_EMPTY;
    }

    /** The handle of the i-th shown hide (rim edge, local pixels). */
    public static Vec3 handle(int index) {
        float[] h = HANDLES[Math.floorMod(index, HANDLES.length)];
        return new Vec3(h[0], RIM - 0.4, h[1]);
    }

    public static AABB handleBox(int index) {
        return new AABB(handle(index), handle(index)).inflate(1.6, 1.0, 1.6);
    }

    public static AABB openingBox(boolean water) {
        float y = surface(water);
        return new AABB(INNER_MIN, y - 0.2, INNER_MIN, INNER_MAX, Math.max(y + 0.6, RIM - 0.4), INNER_MAX);
    }

    public static AABB paddleBox() {
        return new AABB(3.0, 13.0, 13.4, 13.0, 14.4, 15.2);
    }

    public static AABB hookBox() {
        return new AABB(HOOK, HOOK).inflate(1.6, 1.0, 1.2);
    }

    /** Where a ray meets the liquor (or the dry floor): normalized {su to +x, sv to +z, t}, or null. */
    @Nullable
    public static float[] intersectSurface(Vec3 origin, Vec3 dir, boolean water) {
        if (Math.abs(dir.y) < 1.0E-7) return null;
        float y = surface(water);
        double t = (y - origin.y) / dir.y;
        if (t <= 0) return null;
        double x = origin.x + dir.x * t;
        double z = origin.z + dir.z * t;
        float su = (float) ((x - INNER_MIN) / (INNER_MAX - INNER_MIN));
        float sv = (float) ((z - INNER_MIN) / (INNER_MAX - INNER_MIN));
        return new float[]{su, sv, (float) t};
    }

    /** A surface point from normalized coordinates, local pixels. */
    public static Vec3 surfacePoint(float su, float sv, boolean water) {
        return new Vec3(Mth.lerp(su, INNER_MIN, INNER_MAX), surface(water), Mth.lerp(sv, INNER_MIN, INNER_MAX));
    }

    private VatGeometry() {}
}
