package com.otectus.immersivetanning.work;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The exact stacks a bench job took from a player, each with its role. While a job holds them they exist nowhere else:
 * not in the player's inventory, not in a display copy. They leave the ledger exactly once - returned whole when an
 * unstarted job is cancelled or a job is recovered, carried inside an unfinished workpiece, or consumed when the
 * piece is committed (crafting remainders, such as an emptied bucket, come back then).
 */
public final class MaterialLedger {
    public enum Role { LEATHER, AUXILIARY, WORKPIECE, SCRAP }

    public record Entry(Role role, ItemStack stack) {}

    private final List<Entry> entries = new ArrayList<>();

    public void add(Role role, ItemStack stack) {
        if (!stack.isEmpty()) entries.add(new Entry(role, stack.copy()));
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    /** Copies of every stack of a role. */
    public List<ItemStack> stacks(Role role) {
        List<ItemStack> out = new ArrayList<>();
        for (Entry e : entries) if (e.role() == role) out.add(e.stack().copy());
        return out;
    }

    public int count(Role role) {
        int n = 0;
        for (Entry e : entries) if (e.role() == role) n += e.stack().getCount();
        return n;
    }

    /** Removes everything and returns it (the one exit of the ledger). */
    public List<ItemStack> drain() {
        List<ItemStack> out = new ArrayList<>();
        for (Entry e : entries) out.add(e.stack().copy());
        entries.clear();
        return out;
    }

    /** Consumes the materials and returns their crafting remainders (an emptied bucket), which go back to the player. */
    public List<ItemStack> consume() {
        List<ItemStack> remainders = new ArrayList<>();
        for (Entry e : entries) {
            ItemStack stack = e.stack();
            if (e.role() == Role.AUXILIARY && stack.hasCraftingRemainingItem()) {
                for (int i = 0; i < stack.getCount(); i++) {
                    ItemStack remainder = stack.getCraftingRemainingItem();
                    if (!remainder.isEmpty()) remainders.add(remainder.copy());
                }
            }
        }
        entries.clear();
        return remainders;
    }

    public ListTag save() {
        ListTag list = new ListTag();
        for (Entry e : entries) {
            CompoundTag t = e.stack().save(new CompoundTag());
            t.putString("LedgerRole", e.role().name());
            list.add(t);
        }
        return list;
    }

    public static MaterialLedger load(ListTag list) {
        MaterialLedger ledger = new MaterialLedger();
        for (Tag t : list) {
            if (!(t instanceof CompoundTag c)) continue;
            Role role;
            try {
                role = Role.valueOf(c.getString("LedgerRole"));
            } catch (IllegalArgumentException e) {
                role = Role.AUXILIARY;
            }
            ItemStack stack = ItemStack.of(c);
            if (!stack.isEmpty()) ledger.entries.add(new Entry(role, stack));
        }
        return ledger;
    }

    public MaterialLedger copy() {
        return load(save());
    }
}
