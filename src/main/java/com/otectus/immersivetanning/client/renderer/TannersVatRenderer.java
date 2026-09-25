package com.otectus.immersivetanning.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.otectus.immersivetanning.blockentity.TannersVatBlockEntity;
import com.otectus.immersivetanning.item.ProcessedHideItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * The vat's state is its liquor: clear water, darkening toward tannin brown as bark is added, with the hides visible
 * as folded shapes just under the surface. Tanned hides float up and turn the colour of wet leather.
 */
public class TannersVatRenderer implements BlockEntityRenderer<TannersVatBlockEntity> {
    private static final ResourceLocation WATER = new ResourceLocation("minecraft", "block/water_still");
    private static final int WATER_COLOR = 0x3F76E4;
    private static final int TANNIN_COLOR = 0x9A6230;
    private static final int SCRAPED_COLOR = 0xD9C2A0;
    private static final float PX = 1F / 16F;

    public TannersVatRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(TannersVatBlockEntity vat, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        long time = vat.getLevel() != null ? vat.getLevel().getGameTime() : 0L;
        boolean water = vat.hasWater();
        float surface = water ? 12.0F : 2.3F;
        List<TannersVatBlockEntity.Slot> slots = vat.slots();
        if (!slots.isEmpty()) {
            VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(TannersRackRenderer.LEATHER));
            for (int i = 0; i < slots.size(); i++) {
                TannersVatBlockEntity.Slot slot = slots.get(i);
                float progress = slot.progress(time);
                int color = MeshUtil.mix(SCRAPED_COLOR, TannersRackRenderer.WET_COLOR, progress);
                float done = progress >= 1F ? 1F : 0F;
                float y = water ? surface - 1.4F + done * 1.0F - (i % 2) * 0.35F : 2.3F + i * 0.25F;
                pose.pushPose();
                pose.translate(0.5, y * PX, 0.5);
                pose.mulPose(Axis.YP.rotationDegrees(i * 67F + 20F));
                pose.mulPose(Axis.XP.rotationDegrees(8F + (i % 3) * 5F));
                float half = switch (slot.hide().size()) {
                    case SMALL -> 2.8F;
                    case MEDIUM -> 3.6F;
                    case LARGE -> 4.3F;
                } * PX;
                float[][][] pos = new float[3][3][];
                for (int r = 0; r < 3; r++) {
                    for (int c = 0; c < 3; c++) {
                        float fold = (r == 1 && c == 1) ? 0.6F * PX : 0F;
                        pos[r][c] = new float[]{(c - 1) * half, fold, (r - 1) * half};
                    }
                }
                MeshUtil.sheet(pose, buffer, pos, 0xFF000000 | color, light);
                pose.popPose();
            }
        }
        if (water) {
            float charge = Mth.clamp(vat.tannin() / 6F + (slots.isEmpty() ? 0F : 0.35F), 0F, 1F);
            int color = MeshUtil.mix(WATER_COLOR, TANNIN_COLOR, charge);
            int alpha = Math.round(Mth.lerp(charge, 180F, 215F));
            TextureAtlasSprite sprite = net.minecraft.client.Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(WATER);
            VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
            horizontalQuad(pose, buffer, sprite, 2.5F * PX, 2.5F * PX, 13.5F * PX, 13.5F * PX, surface * PX, (alpha << 24) | (color & 0xFFFFFF), light);
        }
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
