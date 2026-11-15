package com.otectus.immersivetanning.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.otectus.immersivetanning.ImmersiveTanning;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Immediate drawing of station props in block-entity renderers: tinted quads, boxes, flat ribbons (cut lines,
 * thread, cords), filled outlines (pattern paper, leather panels) and rings (ripples). Coordinates are local pixels
 * unless noted; everything is drawn on {@link #PROPS}, a small atlas of plain surfaces tinted by vertex colour.
 */
public final class WorkDraw {
    public static final ResourceLocation PROPS = ImmersiveTanning.id("textures/entity/work_props.png");
    private static final float PX = 1F / 16F;

    /** UV squares of the props atlas: {u0, v0, u1, v1}. */
    public static final float[] WHITE = {0.02F, 0.02F, 0.23F, 0.23F};
    public static final float[] PAPER = {0.27F, 0.02F, 0.48F, 0.23F};
    public static final float[] THREAD = {0.52F, 0.02F, 0.73F, 0.23F};
    public static final float[] WOOD = {0.77F, 0.02F, 0.98F, 0.23F};
    public static final float[] IRON = {0.02F, 0.27F, 0.23F, 0.48F};
    public static final float[] LEATHER = {0.27F, 0.27F, 0.48F, 0.48F};
    public static final float[] RING = {0.52F, 0.27F, 0.73F, 0.48F};
    public static final float[] DARK = {0.77F, 0.27F, 0.98F, 0.48F};

    /** The props atlas to draw on; safe to keep while items, arms or text are drawn in between (see {@link #lazy}). */
    public static VertexConsumer props(MultiBufferSource buffers) {
        return lazy(buffers, RenderType.entityCutoutNoCull(PROPS));
    }

    /** Blended surfaces for fading ripples and wet highlights; cutout props cannot fade. */
    public static VertexConsumer translucent(MultiBufferSource buffers) {
        return lazy(buffers, RenderType.entityTranslucent(PROPS));
    }

    /**
     * A buffer that asks the source again at each vertex. Non-fixed render types share one builder in the level's
     * buffer source: drawing an item, an arm or text ends the batch before it, so a consumer kept from earlier would
     * write into a finished builder (or another type's). Asking again is free while the type is unchanged.
     */
    public static VertexConsumer lazy(MultiBufferSource buffers, RenderType type) {
        return new Lazy(buffers, type);
    }

    private static final class Lazy implements VertexConsumer {
        private final MultiBufferSource source;
        private final RenderType type;
        private VertexConsumer current;

        private Lazy(MultiBufferSource source, RenderType type) {
            this.source = source;
            this.type = type;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            current = source.getBuffer(type);
            current.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            current.color(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            current.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            current.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            current.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            current.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            current.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            source.getBuffer(type).defaultColor(r, g, b, a);
        }

        @Override
        public void unsetDefaultColor() {
            source.getBuffer(type).unsetDefaultColor();
        }
    }

    /** A quad from four local-pixel corners (counter-clockwise seen from the front). */
    public static void quad(PoseStack pose, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb, int light, float[] uv) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        Vector3f normal = new Vector3f((float) (b.x - a.x), (float) (b.y - a.y), (float) (b.z - a.z))
                .cross((float) (d.x - a.x), (float) (d.y - a.y), (float) (d.z - a.z));
        if (normal.lengthSquared() < 1.0E-12F) normal.set(0, 1, 0);
        normal.normalize();
        vertex(buffer, m, n, a, uv[0], uv[1], argb, light, normal);
        vertex(buffer, m, n, b, uv[0], uv[3], argb, light, normal);
        vertex(buffer, m, n, c, uv[2], uv[3], argb, light, normal);
        vertex(buffer, m, n, d, uv[2], uv[1], argb, light, normal);
    }

    /** An axis-aligned box in local pixels. */
    public static void box(PoseStack pose, VertexConsumer buffer, double x0, double y0, double z0, double x1, double y1, double z1,
                           int argb, int light, float[] uv) {
        Vec3 a = new Vec3(x0, y0, z0), b = new Vec3(x1, y0, z0), c = new Vec3(x1, y1, z0), d = new Vec3(x0, y1, z0);
        Vec3 e = new Vec3(x0, y0, z1), f = new Vec3(x1, y0, z1), g = new Vec3(x1, y1, z1), h = new Vec3(x0, y1, z1);
        quad(pose, buffer, d, c, b, a, argb, light, uv);
        quad(pose, buffer, e, f, g, h, argb, light, uv);
        quad(pose, buffer, a, b, f, e, shade(argb, 0.7F), light, uv);
        quad(pose, buffer, h, g, c, d, argb, light, uv);
        quad(pose, buffer, a, e, h, d, shade(argb, 0.85F), light, uv);
        quad(pose, buffer, b, c, g, f, shade(argb, 0.85F), light, uv);
    }

    /** A box between two points with a square cross-section (a lever, a paddle shaft). */
    public static void beam(PoseStack pose, VertexConsumer buffer, Vec3 from, Vec3 to, double half, int argb, int light, float[] uv) {
        Vec3 axis = to.subtract(from);
        if (axis.lengthSqr() < 1.0E-8) return;
        Vec3 dir = axis.normalize();
        Vec3 side = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = dir.cross(side).normalize().scale(half);
        Vec3 v = dir.cross(u).normalize().scale(half);
        Vec3[] s = {from.add(u).add(v), from.add(u).subtract(v), from.subtract(u).subtract(v), from.subtract(u).add(v)};
        Vec3[] e = {to.add(u).add(v), to.add(u).subtract(v), to.subtract(u).subtract(v), to.subtract(u).add(v)};
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            quad(pose, buffer, s[i], e[i], e[j], s[j], shade(argb, i % 2 == 0 ? 1F : 0.82F), light, uv);
        }
        quad(pose, buffer, e[0], e[1], e[2], e[3], argb, light, uv);
        quad(pose, buffer, s[3], s[2], s[1], s[0], shade(argb, 0.75F), light, uv);
    }

    /** A flat ribbon from a to b lying in the plane with normal {@code up}, {@code width} pixels wide. */
    public static void ribbon(PoseStack pose, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 up, double width, int argb, int light, float[] uv) {
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1.0E-10) return;
        Vec3 side = dir.cross(up);
        if (side.lengthSqr() < 1.0E-10) side = dir.cross(new Vec3(0, 0, 1));
        side = side.normalize().scale(width / 2);
        quad(pose, buffer, a.add(side), a.subtract(side), b.subtract(side), b.add(side), argb, light, uv);
    }

    /** A polyline as ribbons. Dashed when {@code dash} > 0 (pixels on, pixels off). */
    public static void polyline(PoseStack pose, VertexConsumer buffer, List<Vec3> points, Vec3 up, double width, double dash,
                                int argb, int light, float[] uv) {
        for (int i = 1; i < points.size(); i++) {
            Vec3 a = points.get(i - 1);
            Vec3 b = points.get(i);
            if (dash <= 0) {
                ribbon(pose, buffer, a, b, up, width, argb, light, uv);
                continue;
            }
            double len = a.distanceTo(b);
            for (double t = 0; t < len; t += dash * 2) {
                Vec3 s = a.lerp(b, t / len);
                Vec3 e = a.lerp(b, Math.min(len, t + dash) / len);
                ribbon(pose, buffer, s, e, up, width, argb, light, uv);
            }
        }
    }

    /** A short quadratic thread: its slack is shape, not a flashing colour cue. */
    public static void thread(PoseStack pose, VertexConsumer buffer, Vec3 from, Vec3 control, Vec3 to, Vec3 normal,
                              double width, int color, int light) {
        Vec3 previous = from;
        for (int i = 1; i <= 10; i++) {
            double t = i / 10.0;
            Vec3 next = from.scale((1 - t) * (1 - t)).add(control.scale(2 * (1 - t) * t)).add(to.scale(t * t));
            ribbon(pose, buffer, previous, next, normal, width, color, light, THREAD);
            previous = next;
        }
    }

    /** A thin cut panel with a visible edge; lifted pieces retain their thickness. */
    public static void leather(PoseStack pose, VertexConsumer buffer, float[] xz, double y, double thickness, int color, int light) {
        for (int i = 0; i < xz.length / 2; i++) {
            int j = (i + 1) % (xz.length / 2);
            Vec3 a = new Vec3(xz[i * 2], y, xz[i * 2 + 1]);
            Vec3 b = new Vec3(xz[j * 2], y, xz[j * 2 + 1]);
            quad(pose, buffer, a, b, b.add(0, -thickness, 0), a.add(0, -thickness, 0), shade(color, 0.65F), light, LEATHER);
        }
        fill(pose, buffer, xz, y, color, light, LEATHER);
    }

    /** A filled outline (any simple polygon) on a horizontal plane at {@code y}, from points {x0, z0, x1, z1, ...}. */
    public static void fill(PoseStack pose, VertexConsumer buffer, float[] xz, double y, int argb, int light, float[] uv) {
        List<int[]> triangles = triangulate(xz);
        if (triangles.isEmpty()) return;
        float minX = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i + 1 < xz.length; i += 2) {
            minX = Math.min(minX, xz[i]);
            maxX = Math.max(maxX, xz[i]);
            minZ = Math.min(minZ, xz[i + 1]);
            maxZ = Math.max(maxZ, xz[i + 1]);
        }
        float width = Math.max(0.001F, maxX - minX);
        float depth = Math.max(0.001F, maxZ - minZ);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        Vector3f up = new Vector3f(0, 1, 0);
        for (int[] t : triangles) {
            // One planar mapping for the whole panel. Resetting UVs on each triangle pinches the grain into fans.
            // Cutout props are two-sided, so emit one upward-lit triangle rather than two coincident faces.
            for (int corner : new int[]{t[0], t[1], t[2], t[2]}) {
                float x = xz[corner * 2], z = xz[corner * 2 + 1];
                float u = uv[0] + (uv[2] - uv[0]) * (x - minX) / width;
                float v = uv[1] + (uv[3] - uv[1]) * (z - minZ) / depth;
                vertex(buffer, m, n, new Vec3(x, y, z), u, v, argb, light, up);
            }
        }
    }

    /** Ear-clipping triangulation of a simple polygon; returns index triples. */
    public static List<int[]> triangulate(float[] xy) {
        int n = xy.length / 2;
        List<int[]> out = new ArrayList<>();
        if (n < 3) return out;
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < n; i++) idx.add(i);
        double area = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += xy[i * 2] * xy[j * 2 + 1] - xy[j * 2] * xy[i * 2 + 1];
        }
        boolean ccw = area > 0;
        int guard = 0;
        while (idx.size() > 3 && guard++ < 512) {
            boolean clipped = false;
            for (int k = 0; k < idx.size(); k++) {
                int a = idx.get((k + idx.size() - 1) % idx.size());
                int b = idx.get(k);
                int c = idx.get((k + 1) % idx.size());
                double cross = (xy[b * 2] - xy[a * 2]) * (xy[c * 2 + 1] - xy[a * 2 + 1]) - (xy[b * 2 + 1] - xy[a * 2 + 1]) * (xy[c * 2] - xy[a * 2]);
                if ((cross > 0) != ccw) continue;
                boolean ear = true;
                for (int p : idx) {
                    if (p == a || p == b || p == c) continue;
                    if (inTriangle(xy[p * 2], xy[p * 2 + 1], xy, a, b, c)) {
                        ear = false;
                        break;
                    }
                }
                if (!ear) continue;
                out.add(new int[]{a, b, c});
                idx.remove(k);
                clipped = true;
                break;
            }
            if (!clipped) break;
        }
        if (idx.size() == 3) out.add(new int[]{idx.get(0), idx.get(1), idx.get(2)});
        else for (int k = 1; k + 1 < idx.size(); k++) out.add(new int[]{idx.get(0), idx.get(k), idx.get(k + 1)});
        return out;
    }

    private static boolean inTriangle(float px, float py, float[] xy, int a, int b, int c) {
        float d1 = sign(px, py, xy[a * 2], xy[a * 2 + 1], xy[b * 2], xy[b * 2 + 1]);
        float d2 = sign(px, py, xy[b * 2], xy[b * 2 + 1], xy[c * 2], xy[c * 2 + 1]);
        float d3 = sign(px, py, xy[c * 2], xy[c * 2 + 1], xy[a * 2], xy[a * 2 + 1]);
        boolean neg = d1 < 0 || d2 < 0 || d3 < 0;
        boolean pos = d1 > 0 || d2 > 0 || d3 > 0;
        return !(neg && pos);
    }

    private static float sign(float px, float py, float ax, float ay, float bx, float by) {
        return (px - bx) * (ay - by) - (ax - bx) * (py - by);
    }

    /** A flat ring on a horizontal plane (a ripple), {@code segments} quads round. */
    public static void ring(PoseStack pose, VertexConsumer buffer, double cx, double y, double cz, double radius, double width,
                            int argb, int light) {
        int segments = 20;
        for (int i = 0; i < segments; i++) {
            double a0 = i * Math.PI * 2 / segments;
            double a1 = (i + 1) * Math.PI * 2 / segments;
            Vec3 i0 = new Vec3(cx + Math.cos(a0) * radius, y, cz + Math.sin(a0) * radius);
            Vec3 i1 = new Vec3(cx + Math.cos(a1) * radius, y, cz + Math.sin(a1) * radius);
            Vec3 o0 = new Vec3(cx + Math.cos(a0) * (radius + width), y, cz + Math.sin(a0) * (radius + width));
            Vec3 o1 = new Vec3(cx + Math.cos(a1) * (radius + width), y, cz + Math.sin(a1) * (radius + width));
            // Upward winding. A duplicate back face would blend twice and make the fade too bright.
            quad(pose, buffer, i1, o1, o0, i0, argb, light, WHITE);
        }
    }

    private static void vertex(VertexConsumer buffer, Matrix4f m, Matrix3f n, Vec3 p, float u, float v, int argb, int light, Vector3f normal) {
        buffer.vertex(m, (float) p.x * PX, (float) p.y * PX, (float) p.z * PX)
                .color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(n, normal.x(), normal.y(), normal.z())
                .endVertex();
    }

    public static int shade(int argb, float f) {
        int a = (argb >>> 24) & 255;
        int r = Math.min(255, Math.round(((argb >> 16) & 255) * f));
        int g = Math.min(255, Math.round(((argb >> 8) & 255) * f));
        int b = Math.min(255, Math.round((argb & 255) * f));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int alpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    private WorkDraw() {}
}
