package com.otectus.immersivetanning.client.station;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * What watchers see of a player working a station: the arm holding the knife or awl reaches forward and down to the
 * work instead of hanging at the side. A simplified pose; the tool itself is drawn at the work by the station.
 */
public final class WorkArmPose {
    public static final HumanoidModel.ArmPose WORKING = HumanoidModel.ArmPose.create("IMMERSIVE_TANNING_WORKING", false,
            (model, entity, arm) -> {
                var part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
                part.xRot = -0.95F + model.head.xRot * 0.4F;
                part.yRot = (arm == HumanoidArm.RIGHT ? -0.25F : 0.25F) + model.head.yRot * 0.5F;
                part.zRot = 0F;
            });

    public static final IClientItemExtensions TOOL = new IClientItemExtensions() {
        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            return ClientWork.working(entity.getId()) ? WORKING : null;
        }
    };

    private WorkArmPose() {}
}
