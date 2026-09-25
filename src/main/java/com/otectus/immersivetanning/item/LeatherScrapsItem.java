package com.otectus.immersivetanning.item;

import com.otectus.immersivetanning.leather.FieldRepair;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Offcuts from the bench. Right-click a stack of scraps onto a damaged leather piece in any inventory screen to patch
 * it (an Awl must be somewhere in your inventory). Also a tagged component for grips and bindings.
 */
public class LeatherScrapsItem extends Item {

    public LeatherScrapsItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack scraps, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) return false;
        ItemStack target = slot.getItem();
        if (!FieldRepair.isRepairable(target) || !target.isDamaged()) return false;
        ItemStack awl = FieldRepair.findAwl(player);
        if (awl == null) return false;
        if (FieldRepair.repair(player, target, scraps, awl)) slot.setChanged();
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.immersive_tanning.leather_scraps.hint").withStyle(ChatFormatting.GRAY));
    }
}
