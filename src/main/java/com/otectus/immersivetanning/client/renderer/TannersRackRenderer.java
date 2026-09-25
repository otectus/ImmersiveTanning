package com.otectus.immersivetanning.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.block.TannersRackBlock;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.leather.Finish;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shows the hide's state physically. A raw hide drapes over the fleshing beam, flesh side out. A wet hide is laced
 * upright into the frame: Supple hangs loose and sags, Harness is tensioned, Hardened is stretched taut to every
 * corner. The wet hide lightens toward its finished colour as it dries and settles once it is done.
 */
public class TannersRackRenderer implements BlockEntityRenderer<TannersRackBlockEntity> {
    public static final ResourceLocation RAW = ImmersiveTanning.id("textures/entity/hide_raw.png");
    public static final ResourceLocation LEATHER = ImmersiveTanning.id("textures/entity/hide_leather.png");
    public static final int WET_COLOR = 0x4A2E1C;
    private static final int CORD_COLOR = 0xFF3A2A1C;
    private static final float PX = 1F / 16F;

    public TannersRackRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public int getViewDistance() {
        return 64;
    }

    @Override
    public void render(TannersRackBlockEntity rack, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        HideData hide = rack.hide();
        if (hide == null || rack.mode() == TannersRackBlockEntity.Mode.EMPTY) return;
        BlockState state = rack.getBlockState();
        Direction facing = state.getBlock() instanceof TannersRackBlock ? state.getValue(TannersRackBlock.FACING) : Direction.NORTH;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-((facing.get2DDataValue() + 2) % 4) * 90F));
        pose.translate(-0.5, 0, -0.5);
        float width = switch (hide.size()) {
            case SMALL -> 7F;
            case MEDIUM -> 9F;
            case LARGE -> 10.6F;
        };
        if (rack.mode() == TannersRackBlockEntity.Mode.RAW) {
            renderDraped(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(RAW)), width, hide.size(), light);
        } else {
            long time = rack.getLevel() != null ? rack.getLevel().getGameTime() : 0L;
            float progress = rack.dryProgress(time);
            int color = MeshUtil.mix(WET_COLOR, rack.finish().leatherColor(), progress * progress * (3F - 2F * progress));
            VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(LEATHER));
            renderStretched(pose, buffer, rack.finish(), width, hide.size(), progress, rack.isSettled(), color, light);
        }
        pose.popPose();
    }

    /** The hide over the beam: up the back, over the top, and hanging down the front. */
    private void renderDraped(PoseStack pose, VertexConsumer buffer, float width, HideSize size, int light) {
        int rows = 9;
        int cols = 5;
        float[][][] pos = new float[rows][cols][];
        float hang = size == HideSize.SMALL ? 1.6F : size == HideSize.MEDIUM ? 0.8F : 0F;
        // Path in (z, y) pixels: behind the low fleshing beam, over its top, down the front toward the floor.
        float[][] path = {{5.6F, 3.2F}, {4.4F, 5.2F}, {3.3F, 6.4F}, {2.0F, 6.6F}, {0.9F, 5.9F}, {0.5F, 4.8F},
                {0.4F, 3.7F}, {0.45F, 2.5F + hang}, {0.6F, 1.1F + hang}};
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float u = c / (float) (cols - 1);
                float x = 8F + (u - 0.5F) * width * (r >= 6 ? 0.9F + 0.1F * Mth.cos((u - 0.5F) * Mth.PI) : 1F);
                float droop = r >= 6 ? 0.6F * Mth.sin(u * Mth.PI) : 0F;
                pos[r][c] = new float[]{x * PX, (path[r][1] - droop) * PX, (path[r][0] - 0.05F) * PX};
            }
        }
        MeshUtil.sheet(pose, buffer, pos, 0xFFFFFFFF, light);
    }

    /** The hide laced into the frame, posed by finish. */
    private void renderStretched(PoseStack pose, VertexConsumer buffer, Finish finish, float width, HideSize size, float progress,
                                 boolean settled, int color, int light) {
        float scale = width / 10.6F;
        float halfW;
        float topY;
        float bottomY;
        float bulge;
        float sag;
        switch (finish) {
            case SUPPLE -> {
                halfW = 4.1F * scale;
                topY = 13.3F;
                bottomY = 13.3F - 7.8F * scale;
                bulge = 0.9F;
                sag = 1.4F;
            }
            case HARNESS -> {
                halfW = 4.7F * scale;
                topY = 13.4F;
                bottomY = 13.4F - 8.7F * scale;
                bulge = 0.4F;
                sag = 0.4F;
            }
            default -> {
                halfW = 5.3F * scale;
                topY = 13.6F;
                bottomY = 13.6F - 9.4F * scale;
                bulge = 0.05F;
                sag = 0F;
            }
        }
        if (settled) {
            sag += finish == Finish.HARDENED ? 0F : 0.3F;
            bulge *= 0.8F;
        }
        // A wet hide hangs a little heavier than a dry one.
        sag += (1F - progress) * 0.3F;
        int rows = 6;
        int cols = 6;
        float[][][] pos = new float[rows][cols][];
        for (int r = 0; r < rows; r++) {
            float v = r / (float) (rows - 1);
            for (int c = 0; c < cols; c++) {
                float u = c / (float) (cols - 1);
                float across = Mth.sin(u * Mth.PI);
                float taper = finish == Finish.SUPPLE ? 1F - 0.18F * v : 1F;
                float x = 8F + (u - 0.5F) * 2F * halfW * taper;
                float y = Mth.lerp(v, topY, bottomY) - sag * v * v * across;
                float z = 8F - bulge * Mth.sin(v * Mth.PI) * across;
                pos[r][c] = new float[]{x * PX, y * PX, z * PX};
            }
        }
        MeshUtil.sheet(pose, buffer, pos, color, light);

        // Lacing: from the hide's edge to the frame. More cords, and tighter, the harder the finish.
        float z = 8F * PX;
        float cu = 0.5F;
        float cv = 0.5F;
        float innerLeft = 8F - halfW;
        float innerRight = 8F + halfW;
        int cords = finish == Finish.HARDENED ? 4 : finish == Finish.HARNESS ? 3 : 2;
        for (int i = 0; i < cords; i++) {
            float t = cords == 1 ? 0.5F : i / (float) (cords - 1);
            float x = Mth.lerp(t, innerLeft + 0.4F, innerRight - 0.4F);
            MeshUtil.cord(pose, buffer, x * PX, topY * PX, z - 0.001F, x * PX, 14F * PX, z - 0.001F, 0.3F * PX, cu, cv, CORD_COLOR, light);
        }
        if (finish != Finish.SUPPLE) {
            for (int i = 0; i < cords - 1; i++) {
                float t = (i + 0.5F) / (cords - 1);
                float y = Mth.lerp(t, topY - 0.5F, bottomY + 0.5F);
                MeshUtil.cord(pose, buffer, 3F * PX, y * PX, z - 0.001F, innerLeft * PX, y * PX, z - 0.001F, 0.3F * PX, cu, cv, CORD_COLOR, light);
                MeshUtil.cord(pose, buffer, innerRight * PX, y * PX, z - 0.001F, 13F * PX, y * PX, z - 0.001F, 0.3F * PX, cu, cv, CORD_COLOR, light);
            }
        }
        if (finish == Finish.HARDENED) {
            for (int i = 0; i < cords; i++) {
                float t = i / (float) (cords - 1);
                float x = Mth.lerp(t, innerLeft + 0.4F, innerRight - 0.4F);
                MeshUtil.cord(pose, buffer, x * PX, bottomY * PX, z - 0.001F, x * PX, 4F * PX, z - 0.001F, 0.3F * PX, cu, cv, CORD_COLOR, light);
            }
        }
    }
}
