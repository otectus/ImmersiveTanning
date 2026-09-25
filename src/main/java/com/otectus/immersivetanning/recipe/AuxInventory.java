package com.otectus.immersivetanning.recipe;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Checks and removes auxiliary ingredients from a player's main inventory and offhand. */
public final class AuxInventory {
    private static final int OFFHAND_SLOT = 40;

    public static boolean hasAll(Player player, List<AuxiliaryIngredient> auxiliary, int protectedSlot) {
        return plan(player, auxiliary, protectedSlot) != null;
    }

    /** Removes the ingredients and returns them, or returns null (removing nothing) if any is missing. */
    @Nullable
    public static List<ItemStack> take(Player player, List<AuxiliaryIngredient> auxiliary, int protectedSlot) {
        int[] take = plan(player, auxiliary, protectedSlot);
        if (take == null) return null;
        Inventory inv = player.getInventory();
        List<ItemStack> taken = new ArrayList<>();
        for (int slot = 0; slot < take.length; slot++) {
            if (take[slot] > 0) {
                ItemStack removed = inv.removeItem(slot, take[slot]);
                if (!removed.isEmpty()) taken.add(removed);
            }
        }
        inv.setChanged();
        return taken;
    }

    @Nullable
    private static int[] plan(Player player, List<AuxiliaryIngredient> auxiliary, int protectedSlot) {
        Inventory inv = player.getInventory();
        int size = inv.getContainerSize();
        int[] remaining = new int[size];
        int[] take = new int[size];
        for (int slot = 0; slot < size; slot++) {
            if (!usable(slot, protectedSlot, inv)) continue;
            remaining[slot] = inv.getItem(slot).getCount();
        }
        for (AuxiliaryIngredient aux : auxiliary) {
            int need = aux.count();
            for (int slot = 0; slot < size && need > 0; slot++) {
                if (remaining[slot] <= 0) continue;
                ItemStack stack = inv.getItem(slot);
                if (!aux.ingredient().test(stack)) continue;
                int t = Math.min(remaining[slot], need);
                remaining[slot] -= t;
                take[slot] += t;
                need -= t;
            }
            if (need > 0) return null;
        }
        return take;
    }

    static boolean usable(int slot, int protectedSlot, Inventory inv) {
        if (slot == protectedSlot) return false;
        return slot < inv.items.size() || slot == OFFHAND_SLOT;
    }

    /** The inventory slot of the hand's item, which recipes must never consume. */
    public static int protectedSlot(Player player, net.minecraft.world.InteractionHand hand) {
        return hand == net.minecraft.world.InteractionHand.MAIN_HAND ? player.getInventory().selected : OFFHAND_SLOT;
    }

    private AuxInventory() {}
}
