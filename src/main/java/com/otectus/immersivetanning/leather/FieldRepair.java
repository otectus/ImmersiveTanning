package com.otectus.immersivetanning.leather;

import com.otectus.immersivetanning.advancement.ModAdvancements;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.item.AwlItem;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModSounds;
import com.otectus.immersivetanning.registry.ModTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Leather repairs in the field with Leather Scraps and an Awl, keeping enchantments, finish and quality. Harness
 * leather repairs best. There is no repair-kit item: carry scraps onto a damaged piece in the inventory (with an awl
 * anywhere in the inventory), or hold the awl and keep using it to patch the most worn piece you are wearing.
 */
public final class FieldRepair {

    public static boolean isRepairable(ItemStack stack) {
        return stack.isDamageableItem() && (LeatherworkData.has(stack) || stack.is(ModTags.FIELD_REPAIRABLE));
    }

    /** Durability one scrap restores on this stack. */
    public static int repairAmount(ItemStack stack) {
        double fraction = ServerConfig.get(ServerConfig.REPAIR_FRACTION_PER_SCRAP);
        Optional<LeatherworkData> data = LeatherworkData.get(stack);
        if (data.isPresent() && data.get().finish() == Finish.HARNESS) fraction *= ServerConfig.get(ServerConfig.HARNESS_REPAIR_MULTIPLIER);
        return Math.max(1, (int) Math.round(stack.getMaxDamage() * fraction));
    }

    @Nullable
    public static ItemStack findAwl(Player player) {
        Inventory inv = player.getInventory();
        if (player.getMainHandItem().getItem() instanceof AwlItem) return player.getMainHandItem();
        if (player.getOffhandItem().getItem() instanceof AwlItem) return player.getOffhandItem();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() instanceof AwlItem) return stack;
        }
        return null;
    }

    @Nullable
    public static ItemStack findScraps(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(ModItems.LEATHER_SCRAPS.get())) return stack;
        }
        return null;
    }

    /** The most worn repairable piece the player wears or holds in the offhand, if any. */
    @Nullable
    public static ItemStack mostWornEquipped(Player player) {
        ItemStack best = null;
        float worst = 0F;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!isRepairable(stack) || !stack.isDamaged()) continue;
            float wear = stack.getDamageValue() / (float) stack.getMaxDamage();
            if (wear > worst) {
                worst = wear;
                best = stack;
            }
        }
        return best;
    }

    /**
     * Spends one scrap and one point of awl durability to repair {@code target}. Runs on both logical sides during
     * inventory clicks, so it only changes stacks and plays sounds; advancements are awarded on the server.
     */
    public static boolean repair(Player player, ItemStack target, ItemStack scraps, ItemStack awl) {
        if (!isRepairable(target) || !target.isDamaged() || scraps.isEmpty() || awl.isEmpty()) return false;
        target.setDamageValue(Math.max(0, target.getDamageValue() - repairAmount(target)));
        if (!player.getAbilities().instabuild) {
            scraps.shrink(1);
            awl.hurtAndBreak(1, player, p -> {});
        }
        player.playSound(ModSounds.AWL_REPAIR.get(), 0.8F, 0.9F + player.getRandom().nextFloat() * 0.2F);
        if (player instanceof ServerPlayer sp) ModAdvancements.award(sp, ModAdvancements.PATCHED_UP);
        return true;
    }

    private FieldRepair() {}
}
