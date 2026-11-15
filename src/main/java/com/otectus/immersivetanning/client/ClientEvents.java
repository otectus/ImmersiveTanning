package com.otectus.immersivetanning.client;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.effect.LeatherworkPolicy;
import com.otectus.immersivetanning.hide.HideItems;
import com.otectus.immersivetanning.leather.FieldRepair;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.leather.LeatherworkData;
import com.otectus.immersivetanning.util.Scores;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The leatherwork tooltip. The important information is the word identity: the name reads "Fine Supple Leather
 * Boots", the next line says what Supple means. Scores and effect details follow; Shift shows the numbers, taken from
 * the piece's {@link LeatherworkPolicy} (ordinary or scale-backed), so they match what the server applies. A piece
 * that is neither worn nor wears out (a saddle, horse armor, Genetic Animals tack) says that its finish and quality
 * are a record of its making, and claims no durability, repair or trait effect.
 */
@Mod.EventBusSubscriber(modid = ImmersiveTanning.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Optional<LeatherworkData> found = LeatherworkData.get(stack);
        List<Component> tooltip = event.getToolTip();
        if (found.isEmpty()) {
            Level level = Minecraft.getInstance().level;
            if (level != null) {
                HideItems.recipe(level, stack).ifPresent(recipe -> tooltip.add(Math.min(1, tooltip.size()),
                        Component.translatable("tooltip.immersive_tanning.mountable", recipe.size().displayName()).withStyle(ChatFormatting.DARK_GRAY)));
            }
            if (FieldRepair.isRepairable(stack) && Screen.hasShiftDown()) {
                tooltip.add(Math.min(1, tooltip.size()), Component.translatable("tooltip.immersive_tanning.field_repairable").withStyle(ChatFormatting.DARK_GRAY));
            }
            return;
        }
        LeatherworkData data = found.get();
        LeatherworkPolicy policy = LeatherworkPolicy.of(stack);
        Finish finish = data.finish();
        if (!tooltip.isEmpty() && !stack.hasCustomHoverName()) {
            tooltip.set(0, Component.translatable("tooltip.immersive_tanning.named", data.quality().displayName(), finish.displayName(),
                    stack.getHoverName()).withStyle(stack.getRarity().getStyleModifier()));
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("tooltip.immersive_tanning.finish_line", finish.displayName(), finish.tagline())
                .withStyle(s -> s.withColor(finish.textColor())));
        lines.add(Component.translatable("tooltip.immersive_tanning.integrity", Scores.label(data.integrity())).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.immersive_tanning.workmanship", Scores.label(data.workmanship())).withStyle(ChatFormatting.GRAY));
        if (data.trait() != null) {
            lines.add(Component.translatable("tooltip.immersive_tanning.trait", data.trait().displayName()).withStyle(ChatFormatting.GRAY));
        }
        if (policy.isScaleBacked()) lines.add(Component.translatable("tooltip.immersive_tanning.scale_backed").withStyle(ChatFormatting.GRAY));
        boolean damageable = stack.isDamageableItem();
        boolean recordOnly = !damageable && LivingEntity.getEquipmentSlotForItem(stack).getType() != EquipmentSlot.Type.ARMOR;
        if (recordOnly) lines.add(Component.translatable("tooltip.immersive_tanning.record_only").withStyle(ChatFormatting.DARK_GRAY));
        if (Screen.hasShiftDown()) {
            // Every number here comes from the piece's own policy, the one the server applies.
            double efficacy = policy.efficacy(data);
            if (damageable) {
                lines.add(Component.translatable("tooltip.immersive_tanning.durability", percent(policy.durability(data))).withStyle(ChatFormatting.DARK_GRAY));
            }
            boolean wearable = stack.getItem() instanceof ArmorItem;
            boolean patchable = FieldRepair.isRepairable(stack);
            switch (finish) {
                case SUPPLE -> {
                    if (wearable) {
                        if (policy.suppleSpeed() > 0) {
                            lines.add(Component.translatable("tooltip.immersive_tanning.effect.supple_speed",
                                    oneDecimal(policy.suppleSpeed() * efficacy * 100)).withStyle(ChatFormatting.DARK_GRAY));
                        }
                        if (policy.suppleVisibility() > 0) {
                            lines.add(Component.translatable("tooltip.immersive_tanning.effect.supple_quiet",
                                    Math.round(policy.suppleVisibility() * efficacy * 100)).withStyle(ChatFormatting.DARK_GRAY));
                        }
                        if (stack.getItem() instanceof ArmorItem armor && armor.getType() == ArmorItem.Type.BOOTS && policy.suppleMuffle()) {
                            lines.add(Component.translatable("tooltip.immersive_tanning.effect.supple_steps").withStyle(ChatFormatting.DARK_GRAY));
                        }
                    }
                }
                case HARNESS -> {
                    if (patchable || (!policy.isScaleBacked() && damageable)) {
                        lines.add(Component.translatable("tooltip.immersive_tanning.effect.harness_repair",
                                oneDecimal(policy.harnessRepair())).withStyle(ChatFormatting.DARK_GRAY));
                    }
                }
                case HARDENED -> {
                    if (wearable && policy.isScaleBacked()) {
                        if (policy.hardenedArmor() > 0 || policy.hardenedToughness() > 0) {
                            lines.add(Component.translatable("tooltip.immersive_tanning.effect.hardened_flat",
                                    ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(policy.hardenedArmor() * efficacy),
                                    ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(policy.hardenedToughness() * efficacy)).withStyle(ChatFormatting.DARK_GRAY));
                        }
                    } else if (wearable && (policy.hardenedArmorShare() > 0 || policy.hardenedToughness() > 0)) {
                        lines.add(Component.translatable("tooltip.immersive_tanning.effect.hardened",
                                Math.round(policy.hardenedArmorShare() * efficacy * 100),
                                oneDecimal(policy.hardenedToughness() * efficacy)).withStyle(ChatFormatting.DARK_GRAY));
                    }
                }
            }
            if (data.trait() != null && !recordOnly) lines.add(data.trait().effectDescription());
            if (wearable) lines.add(Component.translatable("tooltip.immersive_tanning.per_piece").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            if (patchable) lines.add(Component.translatable("tooltip.immersive_tanning.field_repairable").withStyle(ChatFormatting.DARK_GRAY));
            else if (policy.isScaleBacked()) lines.add(Component.translatable("tooltip.immersive_tanning.scale_backed_repair").withStyle(ChatFormatting.DARK_GRAY));
            if (data.crafter() != null) {
                lines.add(Component.translatable("tooltip.immersive_tanning.crafted_by", data.crafter()).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
        } else {
            lines.add(Component.translatable("tooltip.immersive_tanning.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.addAll(Math.min(1, tooltip.size()), lines);
    }

    private static int percent(double multiplier) {
        return (int) Math.round(multiplier * 100.0);
    }

    private static String oneDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private ClientEvents() {}
}
