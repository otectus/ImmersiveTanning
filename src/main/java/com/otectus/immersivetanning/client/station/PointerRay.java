package com.otectus.immersivetanning.client.station;

import com.otectus.immersivetanning.station.StationFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jetbrains.annotations.Nullable;

/**
 * The pointer as a ray in the world, from the exact matrices the last frame was drawn with (captured at the start of
 * level rendering), so the ray matches the picture in every camera mode, at every field of view and window shape.
 * The same matrices project work points back to the screen for keyboard targeting and the HUD.
 */
public final class PointerRay {
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f INVERSE = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;
    private static boolean valid;

    /** A ray in station-local pixels: origin and unit direction (t is in pixels). */
    public record Local(Vec3 origin, Vec3 dir) {
        public Vec3 at(double t) {
            return origin.add(dir.scale(t));
        }
    }

    public static void capture(Matrix4f projection, Matrix4f view, Vec3 cameraPos) {
        PROJECTION.set(projection);
        VIEW.set(view);
        new Matrix4f(PROJECTION).mul(VIEW).invert(INVERSE);
        camera = cameraPos;
        valid = true;
    }

    public static boolean valid() {
        return valid;
    }

    public static void invalidate() {
        valid = false;
    }

    /** The world ray through a GUI-space point: {origin, unit direction}, or null before the first frame. */
    @Nullable
    public static Vec3[] world(double guiX, double guiY) {
        if (!valid) return null;
        Minecraft mc = Minecraft.getInstance();
        double w = mc.getWindow().getGuiScaledWidth();
        double h = mc.getWindow().getGuiScaledHeight();
        float nx = (float) (guiX / w * 2.0 - 1.0);
        float ny = (float) (1.0 - guiY / h * 2.0);
        Vector4f near = new Vector4f(nx, ny, -1F, 1F).mul(INVERSE);
        Vector4f far = new Vector4f(nx, ny, 1F, 1F).mul(INVERSE);
        near.div(near.w);
        far.div(far.w);
        Vec3 n = new Vec3(near.x, near.y, near.z);
        Vec3 dir = new Vec3(far.x - near.x, far.y - near.y, far.z - near.z).normalize();
        return new Vec3[]{camera.add(n), dir};
    }

    /** The pointer ray in a station's local pixels. */
    @Nullable
    public static Local local(BlockPos pos, Direction facing, double guiX, double guiY) {
        Vec3[] ray = world(guiX, guiY);
        if (ray == null) return null;
        Vec3 origin = StationFrame.toLocalPx(pos, facing, ray[0]);
        Vec3 dir = StationFrame.dirToLocal(facing, ray[1]).normalize();
        return new Local(origin, dir);
    }

    /** A world point on the GUI: {x, y}, or null when it is behind the camera. */
    @Nullable
    public static float[] project(Vec3 world) {
        if (!valid) return null;
        Vec3 rel = world.subtract(camera);
        Vector4f clip = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1F).mul(VIEW).mul(PROJECTION);
        if (clip.w <= 1.0E-4F) return null;
        float nx = clip.x / clip.w;
        float ny = clip.y / clip.w;
        Minecraft mc = Minecraft.getInstance();
        return new float[]{(nx + 1F) / 2F * mc.getWindow().getGuiScaledWidth(), (1F - ny) / 2F * mc.getWindow().getGuiScaledHeight()};
    }

    /** A station-local pixel point on the GUI. */
    @Nullable
    public static float[] projectLocal(BlockPos pos, Direction facing, Vec3 localPx) {
        return project(StationFrame.pxToWorld(pos, facing, localPx.x, localPx.y, localPx.z));
    }

    private PointerRay() {}
}
