package com.otectus.immersivetanning.recipe;

import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.leather.LeatherData;
import com.otectus.immersivetanning.trait.TraitRef;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The finished leather a player carries, grouped by what can be cut together: one finish and one trait (or none).
 * Integrity may differ within a group; the bench always cuts the best leather first.
 */
public final class LeatherStock {

    /** One cuttable group and its units' Integrity values, best first. */
    public record Group(Finish finish, @Nullable TraitRef trait, List<Integer> integrities) {
        public int count() {
            return integrities.size();
        }

        /** Integrity of a piece cut from the best {@code units} of this group. */
        public int integrityFor(int units) {
            int n = Math.min(units, integrities.size());
            if (n <= 0) return 0;
            int sum = 0;
            for (int i = 0; i < n; i++) sum += integrities.get(i);
            return Math.round(sum / (float) n);
        }

        public boolean matches(LeatherData data) {
            return data.finish() == finish && TraitRef.same(data.trait(), trait);
        }
    }

    public static List<Group> groups(Player player) {
        Map<String, Group> groups = new LinkedHashMap<>();
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            if (!AuxInventory.usable(slot, -1, inv)) continue;
            ItemStack stack = inv.getItem(slot);
            LeatherData data = LeatherData.get(stack).orElse(null);
            if (data == null) continue;
            String key = data.finish().getSerializedName() + "|" + (data.trait() == null ? "" : data.trait().id());
            Group group = groups.computeIfAbsent(key, k -> new Group(data.finish(), data.trait(), new ArrayList<>()));
            for (int i = 0; i < stack.getCount(); i++) group.integrities().add(data.integrity());
        }
        List<Group> out = new ArrayList<>();
        for (Group g : groups.values()) {
            List<Integer> sorted = new ArrayList<>(g.integrities());
            sorted.sort(Comparator.reverseOrder());
            out.add(new Group(g.finish(), g.trait(), List.copyOf(sorted)));
        }
        out.sort(Comparator.comparing((Group g) -> g.finish().ordinal()).thenComparing(g -> g.trait() == null ? "" : g.trait().id().toString()));
        return out;
    }

    @Nullable
    public static Group find(Player player, Finish finish, @Nullable TraitRef trait) {
        for (Group g : groups(player)) {
            if (g.finish() == finish && TraitRef.same(g.trait(), trait)) return g;
        }
        return null;
    }

    /**
     * Removes {@code units} of leather of the group, best Integrity first, and returns the removed stacks; returns
     * null and removes nothing when the player does not carry enough.
     */
    @Nullable
    public static List<ItemStack> take(Player player, Finish finish, @Nullable TraitRef trait, int units) {
        Group group = find(player, finish, trait);
        if (group == null || group.count() < units) return null;
        Inventory inv = player.getInventory();
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            if (!AuxInventory.usable(slot, -1, inv)) continue;
            LeatherData data = LeatherData.get(inv.getItem(slot)).orElse(null);
            if (data != null && group.matches(data)) slots.add(slot);
        }
        slots.sort(Comparator.comparingInt((Integer s) -> LeatherData.get(inv.getItem(s)).map(LeatherData::integrity).orElse(0)).reversed());
        List<ItemStack> taken = new ArrayList<>();
        int need = units;
        for (int slot : slots) {
            if (need <= 0) break;
            ItemStack removed = inv.removeItem(slot, Math.min(need, inv.getItem(slot).getCount()));
            need -= removed.getCount();
            if (!removed.isEmpty()) taken.add(removed);
        }
        inv.setChanged();
        return taken;
    }

    private LeatherStock() {}
}
