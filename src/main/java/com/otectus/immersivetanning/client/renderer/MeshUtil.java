package com.otectus.immersivetanning.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Small helpers for drawing textured, deformable sheets (hides) and thin cords in block entity renderers. */
public final class MeshUtil {

    /**
     * Draws a grid of quads. {@code pos[row][col]} holds block-space xyz; UVs span 0..1 across the grid. Sheets use a
     * no-cull render type, so both faces show.
     */
    public static void sheet(PoseStack pose, VertexConsumer buffer, float[][][] pos, int argb, int light) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int rows = pos.length;
        int cols = pos[0].length;
        for (int r = 0; r + 1 < rows; r++) {
            for (int c = 0; c + 1 < cols; c++) {
                float u0 = c / (float) (cols - 1);
                float u1 = (c + 1) / (float) (cols - 1);
                float v0 = r / (float) (rows - 1);
                float v1 = (r + 1) / (float) (rows - 1);
                float[] a = pos[r][c];
                float[] b = pos[r][c + 1];
                float[] cc = pos[r + 1][c + 1];
                float[] d = pos[r + 1][c];
                Vector3f normal = normal(a, b, d);
                vertex(buffer, m, n, a, u0, v0, argb, light, normal);
                vertex(buffer, m, n, d, u0, v1, argb, light, normal);
                vertex(buffer, m, n, cc, u1, v1, argb, light, normal);
                vertex(buffer, m, n, b, u1, v0, argb, light, normal);
            }
        }
    }

    /** A thin flat cord between two points, {@code width} wide, facing the viewer along z. */
    public static void cord(PoseStack pose, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1,
                            float width, float u, float v, int argb, int light) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-5F) return;
        float ox = -dy / len * width / 2F;
        float oy = dx / len * width / 2F;
        Vector3f normal = new Vector3f(0, 0, -1);
        vertex(buffer, m, n, new float[]{x0 + ox, y0 + oy, z0}, u, v, argb, light, normal);
        vertex(buffer, m, n, new float[]{x0 - ox, y0 - oy, z0}, u, v, argb, light, normal);
        vertex(buffer, m, n, new float[]{x1 - ox, y1 - oy, z1}, u, v, argb, light, normal);
        vertex(buffer, m, n, new float[]{x1 + ox, y1 + oy, z1}, u, v, argb, light, normal);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f m, Matrix3f n, float[] p, float u, float v, int argb, int light, Vector3f normal) {
        buffer.vertex(m, p[0], p[1], p[2])
                .color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(n, normal.x(), normal.y(), normal.z())
                .endVertex();
    }

    private static Vector3f normal(float[] a, float[] b, float[] d) {
        Vector3f ab = new Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2]);
        Vector3f ad = new Vector3f(d[0] - a[0], d[1] - a[1], d[2] - a[2]);
        Vector3f cross = ab.cross(ad);
        if (cross.lengthSquared() < 1.0E-10F) return new Vector3f(0, 1, 0);
        return cross.normalize();
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0F, Math.min(1F, t));
        int out = 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int from = (a >> shift) & 255;
            out |= Math.round(from + (((b >> shift) & 255) - from) * t) << shift;
        }
        return out;
    }

    private MeshUtil() {}
}
