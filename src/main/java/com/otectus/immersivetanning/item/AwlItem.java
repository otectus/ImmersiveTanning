package com.otectus.immersivetanning.item;

import com.otectus.immersivetanning.leather.FieldRepair;
import com.otectus.immersivetanning.util.Feedback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Stitches at the Leatherworker's Bench, and patches worn leather in the field: keep using it to spend one Leather
 * Scrap on the most worn piece you are wearing.
 */
public class AwlItem extends Item {
    private static final int PATCH_TICKS = 24;

    public AwlItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack awl = player.getItemInHand(hand);
        if (FieldRepair.mostWornEquipped(player) == null) {
            if (!level.isClientSide) Feedback.info(player, "nothing_to_patch");
            return InteractionResultHolder.pass(awl);
        }
        if (FieldRepair.findScraps(player) == null) {
            if (!level.isClientSide) Feedback.fail(player, "need_scraps");
            return InteractionResultHolder.fail(awl);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(awl);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack awl, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof Player player) {
            ItemStack target = FieldRepair.mostWornEquipped(player);
            ItemStack scraps = FieldRepair.findScraps(player);
            if (target != null && scraps != null && FieldRepair.repair(player, target, scraps, awl)) {
                Feedback.info(player, "patched", target.getHoverName());
            }
        }
        return awl;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return PATCH_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(net.minecraftforge.common.Tags.Items.NUGGETS_IRON) || super.isValidRepairItem(stack, repair);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.immersive_tanning.awl.hint").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.immersive_tanning.awl.hint_repair").withStyle(ChatFormatting.DARK_GRAY));
    }
}
