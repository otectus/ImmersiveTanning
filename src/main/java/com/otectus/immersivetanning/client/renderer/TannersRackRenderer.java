package com.otectus.immersivetanning.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.client.render.WorkDraw;
import com.otectus.immersivetanning.client.render.WorkHands;
import com.otectus.immersivetanning.client.station.ClientWork;
import com.otectus.immersivetanning.client.station.RackBehavior;
import com.otectus.immersivetanning.client.station.StationBehavior;
import com.otectus.immersivetanning.client.station.StationFocus;
import com.otectus.immersivetanning.config.ClientConfig;
import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.minigame.FleshingSurface;
import com.otectus.immersivetanning.network.packet.OperatorPosePacket;
import com.otectus.immersivetanning.network.packet.StationCommandPacket;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.station.RackGeometry;
import com.otectus.immersivetanning.station.StationFrame;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The rack's material, drawn from its synchronised state (and, for the player scraping it, the local prediction).
 *
 * <p>Over the beam the raw hide is the fleshing surface itself on the canonical drape: patches stand up in relief and
 * clear where the blade went, with streaks along the strokes; the blade presses the hide a little where it touches.
 * The scraped hide stays there, clean, until picked up.</p>
 *
 * <p>In the frame the wet hide hangs by its cords. While a finish is chosen the tension lever moves continuously
 * between Supple (slack, sagging), Harness (even) and Hardened (taut, flat) and the hide follows it; the treatment
 * shows as smoothing, drawn cords or burnish. Drying darkens to the finish's colour, the sag eases as it stiffens,
 * and a dry hide settles.</p>
 */
public class TannersRackRenderer implements BlockEntityRenderer<TannersRackBlockEntity> {
    public static final ResourceLocation RAW = ImmersiveTanning.id("textures/entity/hide_raw.png");
    public static final ResourceLocation LEATHER = ImmersiveTanning.id("textures/entity/hide_leather.png");
    public static final int WET_COLOR = 0x79503A;
    private static final int CORD_COLOR = 0xFFD0B184;
    private static final float PX = 1F / 16F;
    private static final int GRID_U = 32;
    private static final int GRID_V = 24;
    private final Font font;

    public TannersRackRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    @Override
    public void render(TannersRackBlockEntity rack, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        HideData hide = rack.hide();
        Direction facing = StationFrame.facing(rack.getBlockState());
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(StationFrame.modelYRot(facing)));
        pose.translate(-0.5, 0, -0.5);
        StationBehavior behavior = StationFocus.behaviorAt(rack.getBlockPos());
        RackBehavior local = behavior instanceof RackBehavior r ? r : null;
        TannersRackBlockEntity.Mode mode = rack.mode();
        if (hide != null && (mode == TannersRackBlockEntity.Mode.RAW || mode == TannersRackBlockEntity.Mode.SCRAPED)) {
            FleshingSurface surface = local != null ? local.surface(rack)
                    : mode == TannersRackBlockEntity.Mode.SCRAPED ? rack.scrapedLook() : rack.work() == null ? null : rack.work().surface();
            if (surface != null) {
                ResourceLocation texture = ClientWork.texture(rack.getBlockPos()).of(surface);
                float pressU = Float.NaN;
                float pressV = Float.NaN;
                if (local != null && local.bladeDown()) {
                    pressU = local.u();
                    pressV = local.v();
                }
                drape(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), surface, light, pressU, pressV);
            } else {
                legacyDrape(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(RAW)), hide.size(), light);
            }
            if (mode == TannersRackBlockEntity.Mode.RAW) knife(rack, local, pose, buffers, light, facing, partialTick);
        } else if (hide != null && (mode == TannersRackBlockEntity.Mode.FRAMING || mode == TannersRackBlockEntity.Mode.DRYING)) {
            frame(rack, local, pose, buffers, light, facing, partialTick);
        }
        if (mode == TannersRackBlockEntity.Mode.FRAMING || local != null) lever(rack, local, pose, buffers, light);
        pose.popPose();
    }

    // ------------------------------------------------------------------ over the beam

    /** The drape grid over the canonical surface, with flesh in relief and a slight press under the blade. */
    private void drape(PoseStack pose, VertexConsumer buffer, FleshingSurface surface, int light, float pressU, float pressV) {
        float[][][] pos = new float[GRID_V + 1][GRID_U + 1][];
        for (int r = 0; r <= GRID_V; r++) {
            float v = r / (float) GRID_V;
            float[] n = RackGeometry.fleshNormal(v);
            for (int c = 0; c <= GRID_U; c++) {
                float u = c / (float) GRID_U;
                float[] p = RackGeometry.fleshPoint(u, v);
                int cx = Math.min(FleshingSurface.W - 1, (int) (u * FleshingSurface.W));
                int cy = Math.min(FleshingSurface.H - 1, (int) (v * FleshingSurface.H));
                float relief = 0.16F * surface.thickness(cx, cy) + 0.03F;
                if (!Float.isNaN(pressU)) {
                    float du = (u - pressU) * 12F;
                    float dv = (v - pressV) * 8F;
                    float d2 = du * du + dv * dv;
                    if (d2 < 1.5F) relief -= 0.12F * (1F - d2 / 1.5F);
                }
                pos[r][c] = new float[]{(p[0] + n[0] * relief) * PX, (p[1] + n[1] * relief) * PX, (p[2] + n[2] * relief) * PX};
            }
        }
        MeshUtil.sheet(pose, buffer, pos, 0xFFFFFFFF, light, true);
    }

    /** Before the client has the hide's surface: the old static drape (never longer than one sync). */
    private void legacyDrape(PoseStack pose, VertexConsumer buffer, HideSize size, int light) {
        float[][][] pos = new float[GRID_V / 3 + 1][9][];
        for (int r = 0; r < pos.length; r++) {
            float v = r / (float) (pos.length - 1);
            for (int c = 0; c < 9; c++) {
                float[] p = RackGeometry.fleshPoint(0.12F + 0.76F * c / 8F, v);
                pos[r][c] = new float[]{p[0] * PX, p[1] * PX, (p[2] - 0.05F) * PX};
            }
        }
        MeshUtil.sheet(pose, buffer, pos, 0xFFFFFFFF, light, true);
    }

    /** The knife: the operator's real knife at the blade's contact, or the watched operator's at the relayed pose. */
    private void knife(TannersRackBlockEntity rack, RackBehavior local, PoseStack pose, MultiBufferSource buffers, int light,
                       Direction facing, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 point;
        Vec3 normal;
        boolean down;
        ItemStack stack;
        if (local != null && local.knifeOut()) {
            float[] p = RackGeometry.fleshPoint(local.u(), local.v());
            float[] n = RackGeometry.fleshNormal(local.v());
            down = local.bladeDown();
            float lift = down ? 0.08F : 1.1F;
            point = new Vec3(p[0] + n[0] * lift, p[1] + n[1] * lift, p[2] + n[2] * lift);
            normal = new Vec3(n[0], n[1], n[2]);
            stack = heldKnife();
        } else {
            ClientWork.RemotePose remote = ClientWork.remote(rack.getBlockPos());
            float[] trail = ClientWork.trail(rack.getBlockPos());
            if (remote != null && remote.tool == OperatorPosePacket.TOOL_KNIFE) {
                float t = Mth.clamp((Util.getMillis() - remote.updatedMs) / 100F, 0F, 1F);
                point = new Vec3(Mth.lerp(t, remote.px, remote.x), Mth.lerp(t, remote.py, remote.y), Mth.lerp(t, remote.pz, remote.z));
                down = remote.pressed;
            } else if (trail != null && trail[0] == com.otectus.immersivetanning.network.packet.StationStrokePacket.FLESH) {
                float[] p = RackGeometry.fleshPoint(StationCommandPacket.unit((short) trail[1]), StationCommandPacket.unit((short) trail[2]));
                point = new Vec3(p[0], p[1], p[2]);
                down = trail[3] > 0;
            } else {
                return;
            }
            float v = estimateV(point);
            float[] n = RackGeometry.fleshNormal(v);
            normal = new Vec3(n[0], n[1], n[2]);
            stack = new ItemStack(ModItems.TANNERS_KNIFE.get());
        }
        // The blade rides the hide at a low scraping angle. Keep its grip beside the contact instead of raising
        // it into the close camera; the mirrored lateral rake follows the operator's working hand.
        double side = local == null || WorkHands.rightHanded() ? 1 : -1;
        Vec3 shaft = normal.scale(-0.35).add(side * 0.75, -0.15, 0.5).normalize();
        Vec3 face = new Vec3(0, 0.3, -1);
        Vec3 grip = WorkHands.tool(pose, buffers, stack, WorkHands.Tool.KNIFE, point, shaft, face, light, rack.getLevel(),
                (int) rack.getBlockPos().asLong());
        if (local != null && local.knifeOut()) {
            WorkHands.arm(pose, rack.getBlockPos(), facing, WorkHands.rightHanded(), grip, light);
            if (com.otectus.immersivetanning.config.ClientConfig.get(com.otectus.immersivetanning.config.ClientConfig.SUPPORTING_HAND)) {
                float hu = WorkHands.rightHanded() ? 0.18F : 0.82F;
                float[] hold = RackGeometry.fleshPoint(hu, 0.48F);
                float[] n = RackGeometry.fleshNormal(0.48F);
                WorkHands.arm(pose, rack.getBlockPos(), facing, !WorkHands.rightHanded(),
                        new Vec3(hold[0] + n[0] * 1.6, hold[1] + n[1] * 1.6, hold[2] + n[2] * 1.6), light);
            }
        }
    }

    private static float estimateV(Vec3 point) {
        float best = 0.5F;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i <= 20; i++) {
            float v = i / 20F;
            float[] p = RackGeometry.fleshPoint(0.5F, v);
            double d = (p[1] - point.y) * (p[1] - point.y) + (p[2] - point.z) * (p[2] - point.z);
            if (d < bestD) {
                bestD = d;
                best = v;
            }
        }
        return best;
    }

    private static ItemStack heldKnife() {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            if (player.getMainHandItem().is(ModItems.TANNERS_KNIFE.get())) return player.getMainHandItem();
            for (ItemStack s : player.getInventory().items) if (s.is(ModItems.TANNERS_KNIFE.get())) return s;
        }
        return new ItemStack(ModItems.TANNERS_KNIFE.get());
    }

    // ------------------------------------------------------------------ in the frame

    /** Tension 0 (Supple) .. 1 (Harness) .. 2 (Hardened), continuous while the lever moves. */
    private static float tension(TannersRackBlockEntity rack, RackBehavior local) {
        float angle = local != null ? local.leverAngle(rack) : RackGeometry.detentDegrees(rack.finish());
        return Mth.clamp(1F - angle / RackGeometry.DETENT_DEGREES[0], 0F, 2F);
    }

    private void frame(TannersRackBlockEntity rack, RackBehavior local, PoseStack pose, MultiBufferSource buffers, int light,
                       Direction facing, float partialTick) {
        HideData hide = rack.hide();
        long time = rack.getLevel() != null ? rack.getLevel().getGameTime() : 0L;
        boolean framing = rack.mode() == TannersRackBlockEntity.Mode.FRAMING;
        float progress = framing ? 0F : rack.dryProgress(time);
        float tension = framing ? tension(rack, local) : rack.finish().ordinal();
        int color = MeshUtil.mix(WET_COLOR, rack.finish().leatherColor(), progress * progress * (3F - 2F * progress));
        float width = switch (hide.size()) {
            case SMALL -> 7F;
            case MEDIUM -> 9F;
            case LARGE -> 10.6F;
        };
        VertexConsumer buffer = WorkDraw.lazy(buffers, RenderType.entityCutoutNoCull(LEATHER));
        float clock = (time % 12000 + partialTick) / 20F;
        float[][][] shape = stretched(pose, buffer, tension, width, progress, rack.isSettled(), color, light, framing ? rack.treatment() : 1F, rack.finish(), clock);
        cords(pose, WorkDraw.props(buffers), tension, shape, light, framing && rack.finish() == Finish.HARNESS ? rack.treatment() : 1F);
        if (framing) {
            props(rack, local, pose, buffers, light, facing);
        }
    }

    /** Draws the hide posed by tension; the same mesh locates the lacing on its actual silhouette. */
    private float[][][] stretched(PoseStack pose, VertexConsumer buffer, float tension, float width, float progress, boolean settled,
                              int color, int light, float treatment, Finish finish, float clock) {
        float scale = width / 10.6F;
        float halfW = Mth.lerp(tension / 2F, 4.1F, 5.3F) * scale;
        float topY = Mth.lerp(tension / 2F, 13.3F, 13.6F);
        float drop = Mth.lerp(tension / 2F, 7.8F, 9.4F) * scale;
        float bottomY = topY - drop;
        float bulge = tension < 1F ? Mth.lerp(tension, 0.9F, 0.4F) : Mth.lerp(tension - 1F, 0.4F, 0.05F);
        float sag = tension < 1F ? Mth.lerp(tension, 1.4F, 0.4F) : Mth.lerp(tension - 1F, 0.4F, 0F);
        if (settled) {
            sag += tension > 1.5F ? 0F : 0.3F;
            bulge *= 0.8F;
        }
        sag += (1F - progress) * 0.3F;
        int rows = 8;
        int cols = 8;
        float[][][] pos = new float[rows][cols][];
        boolean still = ClientConfig.get(ClientConfig.CAMERA_MODE) == ClientConfig.CameraMode.REDUCED_MOTION;
        for (int r = 0; r < rows; r++) {
            float v = r / (float) (rows - 1);
            for (int c = 0; c < cols; c++) {
                float u = c / (float) (cols - 1);
                float across = Mth.sin(u * Mth.PI);
                float taper = tension < 0.5F ? 1F - 0.18F * v * (1F - tension * 2F) : 1F;
                float x = 8F + (u - 0.5F) * 2F * halfW * taper;
                float y = Mth.lerp(v, topY, bottomY) - sag * v * v * across;
                // A wet supple hide stirs a little; a taut one does not.
                float stir = !still && progress < 1F ? (2F - tension) * 0.03F * Mth.sin(clock * 1.3F + u * 3F) * v : 0F;
                float z = 8F - bulge * Mth.sin(v * Mth.PI) * across + stir;
                pos[r][c] = new float[]{x * PX, y * PX, z * PX};
            }
        }
        int tint = color;
        if (treatment > 0F && treatment < 1F) tint = MeshUtil.mix(color, finish == Finish.HARDENED ? 0x2A1A10 : 0xC09068, 0.15F * treatment);
        MeshUtil.sheet(pose, buffer, pos, 0xFF000000 | (tint & 0xFFFFFF), light);
        return pos;
    }

    private void cords(PoseStack pose, VertexConsumer buffer, float tension, float[][][] shape, int light, float drawn) {
        // The pelt's upper legs and neck, not the transparent corners of its rectangular texture.
        for (float u : new float[]{0.17F, 0.83F}) {
            Vec3 at = onSheet(shape, u, 0.18F);
            lace(pose, buffer, at, new Vec3(at.x, 14.0, 7.9), tension < 0.5F ? 0.3 : 0.08, light);
        }
        if (tension > 0.5F) {
            for (float u : new float[]{0.19F, 0.81F}) {
                Vec3 at = onSheet(shape, u, 0.5F);
                lace(pose, buffer, at, new Vec3(u < 0.5F ? 3 : 13, at.y + 0.3, 7.9), (1F - drawn) * 0.9 + 0.08, light);
            }
        }
        if (tension > 1.5F) {
            Vec3 neck = onSheet(shape, 0.5F, 0.09F);
            lace(pose, buffer, neck, new Vec3(neck.x, 14.0, 7.9), 0, light);
            for (float u : new float[]{0.17F, 0.83F}) {
                Vec3 at = onSheet(shape, u, 0.84F);
                lace(pose, buffer, at, new Vec3(at.x, 4.0, 7.9), 0, light);
            }
        }
    }

    /** Bilinear attachment on the draped mesh, in local pixels, just ahead of the leather. */
    private static Vec3 onSheet(float[][][] mesh, float u, float v) {
        float x = u * (mesh[0].length - 1), y = v * (mesh.length - 1);
        int c = Math.min(mesh[0].length - 2, (int) x), r = Math.min(mesh.length - 2, (int) y);
        double[] out = new double[3];
        for (int k = 0; k < 3; k++) {
            out[k] = Mth.lerp(y - r, Mth.lerp(x - c, mesh[r][c][k], mesh[r][c + 1][k]),
                    Mth.lerp(x - c, mesh[r + 1][c][k], mesh[r + 1][c + 1][k])) * 16;
        }
        return new Vec3(out[0], out[1], out[2] - 0.06);
    }

    private void lace(PoseStack pose, VertexConsumer buffer, Vec3 hide, Vec3 anchor, double slack, int light) {
        WorkDraw.thread(pose, buffer, hide, hide.add(anchor).scale(0.5).add(0, -slack, -0.05), anchor,
                new Vec3(0, 0, -1), 0.13, CORD_COLOR, light);
        // A small tied end seats the cord visibly into the hide.
        WorkDraw.box(pose, buffer, hide.x - 0.14, hide.y - 0.12, hide.z - 0.06,
                hide.x + 0.14, hide.y + 0.12, hide.z + 0.03, CORD_COLOR, light, WorkDraw.THREAD);
    }

    /** The slicker on the low bar (or in hand), the cord toggle, and the treatment tool following the pointer. */
    private void props(TannersRackBlockEntity rack, RackBehavior local, PoseStack pose, MultiBufferSource buffers, int light, Direction facing) {
        VertexConsumer props = WorkDraw.props(buffers);
        int frameTool = local != null ? local.frameTool() : 0;
        Vec3 slicker = RackGeometry.SLICKER;
        ClientWork.RemotePose remote = ClientWork.remote(rack.getBlockPos());
        boolean remoteSlicker = remote != null && remote.tool == OperatorPosePacket.TOOL_SLICKER;
        if (frameTool == 1 || remoteSlicker) {
            Vec3 at;
            boolean down;
            if (frameTool == 1) {
                float[] p = RackGeometry.framePoint(local.treatX(), Math.min(1F, local.treatY()));
                down = local.treating();
                at = new Vec3(p[0], p[1], p[2] - (down ? 0.55 : 1.4));
            } else {
                at = new Vec3(remote.x, remote.y, remote.z);
                down = remote.pressed;
            }
            WorkDraw.box(pose, props, at.x - 1.3, at.y - 0.8, at.z - 0.5, at.x + 1.3, at.y + 0.8, at.z + 0.2, 0xFF8A6A48, light, WorkDraw.WOOD);
            if (frameTool == 1) WorkHands.arm(pose, rack.getBlockPos(), facing, WorkHands.rightHanded(), at.add(0, 0, -0.6), light);
        } else {
            WorkDraw.box(pose, props, slicker.x - 1.3, slicker.y, slicker.z - 0.5, slicker.x + 1.3, slicker.y + 0.7, slicker.z + 0.5, 0xFF8A6A48, light, WorkDraw.WOOD);
        }
        // The cord toggle under the frame: drawn down as the Harness cords are pulled.
        float pulled = rack.finish() == Finish.HARNESS ? rack.treatment() : 0F;
        Vec3 toggle = RackGeometry.CORD_TOGGLE.add(0, -pulled * 1.2, 0);
        if (frameTool == 2) {
            float[] p = RackGeometry.framePoint(local.treatX(), local.treatY());
            toggle = new Vec3(p[0], Math.min(p[1], RackGeometry.CORD_TOGGLE.y), RackGeometry.CORD_TOGGLE.z);
            WorkHands.arm(pose, rack.getBlockPos(), facing, WorkHands.rightHanded(), toggle.add(0, 0, -0.4), light);
        }
        WorkDraw.ribbon(pose, props, new Vec3(8, 4.0, 7.2), toggle, new Vec3(0, 0, -1), 0.3, CORD_COLOR, light, WorkDraw.THREAD);
        WorkDraw.box(pose, props, toggle.x - 0.5, toggle.y - 0.9, toggle.z - 0.35, toggle.x + 0.5, toggle.y, toggle.z + 0.35, 0xFFB08850, light, WorkDraw.WOOD);
    }

    /** The tension lever on the viewer's right post, with its three labelled detents while a finish is chosen. */
    private void lever(TannersRackBlockEntity rack, RackBehavior local, PoseStack pose, MultiBufferSource buffers, int light) {
        VertexConsumer props = WorkDraw.props(buffers);
        float angle = local != null ? local.leverAngle(rack) : RackGeometry.detentDegrees(rack.finish());
        Vec3 pivot = RackGeometry.LEVER_PIVOT;
        WorkDraw.box(pose, props, pivot.x - 0.6, pivot.y - 0.7, pivot.z - 0.7, pivot.x + 0.6, pivot.y + 0.7, pivot.z + 0.7, 0xFF5A5650, light, WorkDraw.IRON);
        Vec3 end = RackGeometry.leverEnd(angle);
        WorkDraw.beam(pose, props, pivot, end, 0.32, 0xFF7A5A3A, light, WorkDraw.WOOD);
        WorkDraw.box(pose, props, end.x - 0.45, end.y - 0.45, end.z - 0.45, end.x + 0.45, end.y + 0.45, end.z + 0.45, 0xFF6A4A2A, light, WorkDraw.WOOD);
        if (rack.mode() != TannersRackBlockEntity.Mode.FRAMING) return;
        Finish chosen = local != null ? RackGeometry.nearestDetent(angle) : rack.finish();
        for (Finish f : Finish.values()) {
            Vec3 at = RackGeometry.leverEnd(RackGeometry.detentDegrees(f)).add(0, 0, -0.2);
            WorkDraw.box(pose, props, at.x - 0.15, at.y - 0.15, at.z - 0.15, at.x + 0.15, at.y + 0.15, at.z + 0.15,
                    f == chosen ? 0xFFE8D090 : 0xFF6A6050, light, WorkDraw.IRON);
            // Outside the post: the labels never sit across the hide or show through the operator's hand.
            label(pose, buffers, f.displayName(), at.add(-0.7, 0.35, -0.3), f == chosen ? 0xFFFFE9B0 : 0xFFC8C0B0, light);
        }
    }

    private void label(PoseStack pose, MultiBufferSource buffers, Component text, Vec3 at, int color, int light) {
        pose.pushPose();
        pose.translate(at.x * PX, at.y * PX, at.z * PX);
        pose.mulPose(Axis.YP.rotationDegrees(180F));
        float s = 0.0045F;
        pose.scale(s, -s, s);
        Matrix4f m = pose.last().pose();
        // The font's built-in background sits at +z, in front of world-facing glyphs. A plain shadow avoids that
        // depth conflict while keeping the labels occluded normally by solid objects.
        font.drawInBatch(text, 0, 0, color, true, m, buffers, Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }
}
