package com.otectus.immersivetanning.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.otectus.immersivetanning.block.LeatherworkersBenchBlock;
import com.otectus.immersivetanning.blockentity.LeatherworkersBenchBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** While a piece is being stitched, its cut leather and the piece itself lie on the bench's mat. */
public class LeatherworkersBenchRenderer implements BlockEntityRenderer<LeatherworkersBenchBlockEntity> {
    private static final float PX = 1F / 16F;
    private final ItemRenderer itemRenderer;

    public LeatherworkersBenchRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(LeatherworkersBenchBlockEntity bench, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack working = bench.working();
        if (working.isEmpty()) return;
        BlockState state = bench.getBlockState();
        Direction facing = state.getBlock() instanceof LeatherworkersBenchBlock ? state.getValue(LeatherworkersBenchBlock.FACING) : Direction.NORTH;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-((facing.get2DDataValue() + 2) % 4) * 90F));
        pose.translate(-0.5, 0, -0.5);
        // The cut leather panel.
        float[][][] panel = new float[2][2][];
        panel[0][0] = new float[]{4F * PX, 15.55F * PX, 4.5F * PX};
        panel[0][1] = new float[]{12F * PX, 15.55F * PX, 4.5F * PX};
        panel[1][0] = new float[]{4F * PX, 15.55F * PX, 11.5F * PX};
        panel[1][1] = new float[]{12F * PX, 15.55F * PX, 11.5F * PX};
        MeshUtil.sheet(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TannersRackRenderer.LEATHER)), panel,
                0xFF000000 | bench.leatherColor(), light);
        // The piece taking shape.
        pose.translate(0.5, 15.7F * PX, 0.5);
        pose.mulPose(Axis.XP.rotationDegrees(90F));
        pose.scale(0.45F, 0.45F, 0.45F);
        itemRenderer.renderStatic(working, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, bench.getLevel(),
                (int) bench.getBlockPos().asLong());
        pose.popPose();
    }
}
