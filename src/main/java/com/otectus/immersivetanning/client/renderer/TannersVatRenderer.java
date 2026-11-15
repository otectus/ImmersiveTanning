package com.otectus.immersivetanning.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.otectus.immersivetanning.blockentity.TannersVatBlockEntity;
import com.otectus.immersivetanning.client.render.WorkDraw;
import com.otectus.immersivetanning.client.render.WorkHands;
import com.otectus.immersivetanning.client.station.ClientWork;
import com.otectus.immersivetanning.client.station.StationFocus;
import com.otectus.immersivetanning.client.station.VatBehavior;
import com.otectus.immersivetanning.config.ClientConfig;
import com.otectus.immersivetanning.network.packet.OperatorPosePacket;
import com.otectus.immersivetanning.station.StationFrame;
import com.otectus.immersivetanning.station.VatGeometry;
import com.otectus.immersivetanning.util.StationEffects;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * The vat's state is its liquor and the hides in it. Water is clear and deepens towards tannin brown as bark is
 * stirred in; bark floats a moment and disperses. Each hide (up to four drawn; a larger batch shows a count) sinks
 * partway, folded, a large one pushing the surface up more, and shows a handle at the rim; a tanned hide rises dark
 * and wet. A pour tips a bucket over the rim, a sweep leaves rings, a lift drips. Animations are local presentation
 * of transfers the server already made.
 */
public class TannersVatRenderer implements BlockEntityRenderer<TannersVatBlockEntity> {
    private static final ResourceLocation WATER = new ResourceLocation("minecraft", "block/water_still");
    private static final int WATER_COLOR = 0x3F76E4;
    private static final int TANNIN_COLOR = 0x9A6230;
    private static final int SCRAPED_COLOR = 0xD9C2A0;
    private static final float PX = 1F / 16F;
    private final Font font;

    public TannersVatRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(TannersVatBlockEntity vat, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        long time = vat.getLevel() != null ? vat.getLevel().getGameTime() : 0L;
        Direction facing = StationFrame.facing(vat.getBlockState());
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(StationFrame.modelYRot(facing)));
        pose.translate(-0.5, 0, -0.5);
        boolean water = vat.hasWater();
        List<ClientWork.Animation> anims = ClientWork.animations(vat.getBlockPos(), 3F);
        float pourRise = 1F;
        float barkCloud = 0F;
        for (ClientWork.Animation a : anims) {
            if (a.type() == StationEffects.POUR) pourRise = Math.min(pourRise, Mth.clamp(a.age() / 1.1F, 0F, 1F));
            if (a.type() == StationEffects.BARK) barkCloud = Math.max(barkCloud, 1F - Mth.clamp(a.age() / 2.5F, 0F, 1F));
        }
        float surface = water ? Mth.lerp(pourRise, VatGeometry.SURFACE_EMPTY, VatGeometry.SURFACE_FULL) : VatGeometry.SURFACE_EMPTY;
        boolean still = ClientConfig.get(ClientConfig.CAMERA_MODE) == ClientConfig.CameraMode.REDUCED_MOTION;
        float clock = still ? 0F : (time % 12000 + partialTick) / 20F;
        hides(vat, pose, buffers, light, time, water, surface, clock);
        VertexConsumer props = WorkDraw.props(buffers);
        paddle(vat, pose, buffers, props, light, facing, surface, water);
        // The hook on the front rim, where tanned hides are lifted out.
        Vec3 hook = VatGeometry.HOOK;
        WorkDraw.box(pose, props, hook.x - 0.5, hook.y - 0.3, hook.z - 0.4, hook.x + 0.5, hook.y + 0.6, hook.z + 0.3, 0xFF5A5650, light, WorkDraw.IRON);
        if (vat.slots().size() > VatGeometry.SHOWN) {
            label(pose, buffers, Component.literal("×" + vat.slots().size()), new Vec3(10.5, VatGeometry.RIM + 1.2, 1.4), light);
        }
        // Draw translucent liquor after the immersed hides and paddle, then its surface reflections and wakes.
        if (water || pourRise < 1F) {
            liquor(vat, pose, buffers, light, surface, barkCloud);
            surfaceDetails(vat, pose, WorkDraw.translucent(buffers), light, surface, clock, still);
        }
        effects(vat, anims, pose, WorkDraw.translucent(buffers), buffers, light, surface);
        pose.popPose();
    }

    private void hides(TannersVatBlockEntity vat, PoseStack pose, MultiBufferSource buffers, int light, long time, boolean water, float surface, float clock) {
        List<TannersVatBlockEntity.Slot> slots = vat.slots();
        if (slots.isEmpty()) return;
        VertexConsumer buffer = WorkDraw.lazy(buffers, RenderType.entityCutoutNoCull(TannersRackRenderer.LEATHER));
        VertexConsumer props = WorkDraw.props(buffers);
        int shown = Math.min(slots.size(), VatGeometry.SHOWN);
        for (int i = 0; i < shown; i++) {
            TannersVatBlockEntity.Slot slot = slots.get(i);
            float progress = slot.progress(time);
            boolean done = slot.finished(time);
            int color = MeshUtil.mix(SCRAPED_COLOR, TannersRackRenderer.WET_COLOR, progress);
            float half = switch (slot.hide().size()) {
                case SMALL -> 2.6F;
                case MEDIUM -> 3.3F;
                case LARGE -> 4.0F;
            };
            Vec3 handle = VatGeometry.handle(i);
            float sink = water ? (done ? 0.5F : 1.6F + half * 0.18F) : 0F;
            float bob = ClientConfig.get(ClientConfig.CAMERA_MODE) == ClientConfig.CameraMode.REDUCED_MOTION ? 0F : 0.04F * Mth.sin(clock * 0.8F + i);
            float y = water ? surface - sink + bob : VatGeometry.SURFACE_EMPTY + 0.25F + i * 0.2F;
            pose.pushPose();
            pose.translate((8 + (handle.x - 8) * 0.45) * PX, y * PX, (8 + (handle.z - 8) * 0.45) * PX);
            pose.mulPose(Axis.YP.rotationDegrees(i * 67F + 20F));
            pose.mulPose(Axis.XP.rotationDegrees(8F + (i % 3) * 5F));
            float[][][] pos = new float[4][4][];
            for (int r = 0; r < 4; r++) {
                for (int c = 0; c < 4; c++) {
                    float u = c / 3F - 0.5F;
                    float v = r / 3F - 0.5F;
                    float fold = 0.7F * Mth.cos(u * Mth.PI) * (1F - Math.abs(v)) - (done ? 0F : 0.3F * v * v);
                    pos[r][c] = new float[]{u * 2 * half * PX, fold * PX, v * 2 * half * PX};
                }
            }
            MeshUtil.sheet(pose, buffer, pos, 0xFF000000 | color, light, true);
            pose.popPose();
            // The edge at the rim: a fold of the hide over the side, its colour telling its stage.
            int edge = done ? 0xFF3A2416 : slot.paused() ? 0xFF8A8A80 : slot.started() ? 0xFFB08A60 : 0xFFE0C8A0;
            WorkDraw.box(pose, props, handle.x - 0.9, handle.y - 1.4, handle.z - 0.5, handle.x + 0.9, handle.y + 0.3, handle.z + 0.5, edge, light, WorkDraw.LEATHER);
            if (done) WorkDraw.box(pose, props, handle.x - 0.15, handle.y - 2.2, handle.z - 0.15, handle.x + 0.15, handle.y - 1.4, handle.z + 0.15, 0xC0406080, light, WorkDraw.WHITE);
        }
    }

    private void liquor(TannersVatBlockEntity vat, PoseStack pose, MultiBufferSource buffers, int light, float surface, float barkCloud) {
        float charge = Mth.clamp(vat.tannin() / 6F + (vat.slots().isEmpty() ? 0F : 0.35F), 0F, 1F);
        int color = MeshUtil.mix(WATER_COLOR, TANNIN_COLOR, Math.min(1F, charge + barkCloud * 0.15F));
        int alpha = Math.round(Mth.lerp(charge, 195F, 230F));
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(WATER);
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        float min = VatGeometry.INNER_MIN * PX;
        float max = VatGeometry.INNER_MAX * PX;
        horizontalQuad(pose, buffer, sprite, min, min, max, max, surface * PX, (alpha << 24) | (color & 0xFFFFFF), light);
    }

    /** A narrow meniscus and broken, quiet reflections make the surface read as liquid against the dark staves. */
    private void surfaceDetails(TannersVatBlockEntity vat, PoseStack pose, VertexConsumer buffer, int light, float surface, float clock, boolean still) {
        double lo = VatGeometry.INNER_MIN + 0.04, hi = VatGeometry.INNER_MAX - 0.04;
        Vec3 up = new Vec3(0, 1, 0);
        double y = surface + 0.025;
        int rim = vat.tannin() > 0 ? 0x807EAAAF : 0x909CC9E0;
        Vec3[] corners = {new Vec3(lo, y, lo), new Vec3(lo, y, hi), new Vec3(hi, y, hi), new Vec3(hi, y, lo)};
        for (int i = 0; i < 4; i++) WorkDraw.ribbon(pose, buffer, corners[i], corners[(i + 1) % 4], up, 0.12, rim, light, WorkDraw.WHITE);
        for (int i = 0; i < 3; i++) {
            double z = 4.2 + i * 3.0 + Math.sin(clock * 0.65 + i * 2.1) * 0.22;
            double x = 4.6 + (i % 2) * 3.1;
            WorkDraw.ribbon(pose, buffer, new Vec3(x, y, z), new Vec3(x + 1.6, y, z + 0.12), up,
                    0.10, 0x388FC3D3, light, WorkDraw.WHITE);
        }
        VatBehavior local = StationFocus.behaviorAt(vat.getBlockPos()) instanceof VatBehavior v ? v : null;
        ClientWork.RemotePose remote = ClientWork.remote(vat.getBlockPos());
        Vec3 at = local != null && local.sweeping() ? VatGeometry.surfacePoint(local.su(), local.sv(), true)
                : remote != null && remote.tool == OperatorPosePacket.TOOL_PADDLE && remote.pressed ? new Vec3(remote.x, surface, remote.z) : null;
        if (at == null) return;
        double room = Math.min(Math.min(at.x - lo, hi - at.x), Math.min(at.z - lo, hi - at.z));
        if (room <= 0.25) return;
        WorkDraw.ring(pose, buffer, at.x, y + 0.015, at.z, Math.min(0.45, room - 0.12), 0.10, 0x80BDD5CE, light);
        if (!still && ClientConfig.particleAmount() != ClientConfig.ParticleAmount.MINIMAL) {
            for (int i = 0; i < 2; i++) {
                float phase = Mth.positiveModulo(clock * 0.85F + i * 0.5F, 1F);
                double radius = 0.5 + phase * 2.4;
                if (radius + 0.10 < room) WorkDraw.ring(pose, buffer, at.x, y + 0.02, at.z, radius, 0.10,
                        WorkDraw.alpha(0xBBD2C7, Math.round(75 * (1F - phase))), light);
            }
        }
    }

    private void effects(TannersVatBlockEntity vat, List<ClientWork.Animation> anims, PoseStack pose, VertexConsumer props,
                         MultiBufferSource buffers, int light, float surface) {
        for (ClientWork.Animation a : anims) {
            float age = a.age();
            switch (a.type()) {
                case StationEffects.POUR -> {
                    if (age > 1.2F) break;
                    // The bucket tips over the front rim; a short stream falls to the liquor.
                    float tip = Mth.clamp(age / 0.35F, 0F, 1F) * (age > 0.9F ? Mth.clamp(1F - (age - 0.9F) / 0.3F, 0F, 1F) : 1F);
                    pose.pushPose();
                    pose.translate(8 * PX, (VatGeometry.RIM + 3.0) * PX, 2.6 * PX);
                    pose.mulPose(Axis.XP.rotationDegrees(-tip * 105F));
                    pose.scale(0.42F, 0.42F, 0.42F);
                    Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(Items.WATER_BUCKET), ItemDisplayContext.FIXED, light,
                            OverlayTexture.NO_OVERLAY, pose, buffers, vat.getLevel(), 0);
                    pose.popPose();
                    if (tip > 0.6F && age < 0.95F) {
                        WorkDraw.box(pose, props, 7.4, surface, 3.6, 8.6, VatGeometry.RIM + 1.5, 4.4, 0xA03F76E4, light, WorkDraw.WHITE);
                    }
                    ring(pose, props, 8, surface, 5, age, light);
                }
                case StationEffects.SPLASH, StationEffects.SWEEP, StationEffects.LIFT -> ring(pose, props, 8, surface, 8, age, light);
                case StationEffects.BARK -> {
                    if (age > 2.5F) break;
                    float spread = Mth.clamp(age / 2.5F, 0F, 1F);
                    for (int k = 0; k < 5; k++) {
                        double ang = k * 1.256 + age * 0.4;
                        double r = 1.0 + spread * 3.5;
                        double x = 8 + Math.cos(ang) * r;
                        double z = 8 + Math.sin(ang) * r;
                        int c = WorkDraw.alpha(0x5A4029, Math.round(255 * (1F - spread)));
                        WorkDraw.box(pose, props, x - 0.5, surface + 0.02, z - 0.35, x + 0.5, surface + 0.22, z + 0.35, c, light, WorkDraw.WOOD);
                    }
                }
                default -> {
                }
            }
        }
    }

    private static void ring(PoseStack pose, VertexConsumer props, double x, double y, double z, float age, int light) {
        if (age > 1.6F || ClientConfig.get(ClientConfig.CAMERA_MODE) == ClientConfig.CameraMode.REDUCED_MOTION) return;
        float k = age / 1.6F;
        double radius = 0.6 + k * 4.2;
        double room = Math.min(Math.min(x - VatGeometry.INNER_MIN, VatGeometry.INNER_MAX - x),
                Math.min(z - VatGeometry.INNER_MIN, VatGeometry.INNER_MAX - z));
        if (radius + 0.10 < room) WorkDraw.ring(pose, props, x, y + 0.04, z, radius, 0.10,
                WorkDraw.alpha(0xBDD5CE, Math.round(100 * (1F - k))), light);
    }

    private void paddle(TannersVatBlockEntity vat, PoseStack pose, MultiBufferSource buffers, VertexConsumer props, int light, Direction facing,
                        float surface, boolean water) {
        VatBehavior local = StationFocus.behaviorAt(vat.getBlockPos()) instanceof VatBehavior v ? v : null;
        Vec3 blade;
        Vec3 handle;
        if (local != null && local.paddleInHand()) {
            Vec3 at = VatGeometry.surfacePoint(local.su(), local.sv(), water);
            blade = at.add(0, local.sweeping() ? -0.6 : 0.8, 0);
            handle = blade.add(WorkHands.rightHanded() ? -1.2 : 1.2, 7.5, -5.0);
            WorkHands.arm(pose, vat.getBlockPos(), facing, WorkHands.rightHanded(), handle.add(0, -1.2, 0.6), light);
        } else {
            ClientWork.RemotePose remote = ClientWork.remote(vat.getBlockPos());
            if (remote != null && remote.tool == OperatorPosePacket.TOOL_PADDLE) {
                blade = new Vec3(remote.x, remote.y + (remote.pressed ? -0.6 : 0.8), remote.z);
                handle = blade.add(-1.2, 7.5, -5.0);
            } else {
                Vec3 rest = VatGeometry.PADDLE_REST;
                blade = rest.add(4.2, 0, 0);
                handle = rest.add(-4.5, 0.2, 0);
            }
        }
        WorkDraw.beam(pose, props, handle, blade, 0.28, 0xFF8A6A48, light, WorkDraw.WOOD);
        Vec3 dir = blade.subtract(handle).normalize();
        WorkDraw.beam(pose, props, blade, blade.add(dir.scale(2.2)), 0.85, 0xFF7A5A3A, light, WorkDraw.WOOD);
    }

    private void label(PoseStack pose, MultiBufferSource buffers, Component text, Vec3 at, int light) {
        pose.pushPose();
        pose.translate(at.x * PX, at.y * PX, at.z * PX);
        pose.mulPose(Axis.YP.rotationDegrees(180F));
        float s = 0.01F;
        pose.scale(s, -s, s);
        Matrix4f m = pose.last().pose();
        font.drawInBatch(text, -font.width(text) / 2F, 0, 0xFFFFF0D0, false, m, buffers, Font.DisplayMode.NORMAL, 0x60000000, light);
        pose.popPose();
    }

    private static void horizontalQuad(PoseStack pose, VertexConsumer buffer, TextureAtlasSprite sprite,
                                       float x0, float z0, float x1, float z1, float y, int argb, int light) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        float u0 = sprite.getU(x0 * 16);
        float u1 = sprite.getU(x1 * 16);
        float v0 = sprite.getV(z0 * 16);
        float v1 = sprite.getV(z1 * 16);
        buffer.vertex(m, x0, y, z0).color(r, g, b, a).uv(u0, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
        buffer.vertex(m, x0, y, z1).color(r, g, b, a).uv(u0, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
        buffer.vertex(m, x1, y, z1).color(r, g, b, a).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
        buffer.vertex(m, x1, y, z0).color(r, g, b, a).uv(u1, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
    }
}
