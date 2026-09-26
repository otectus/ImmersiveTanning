package com.otectus.immersivetanning.leather;

import com.otectus.immersivetanning.trait.TraitRef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * The long-term identity of a piece made at the Leatherworker's Bench: Integrity (how well the hide was prepared),
 * Workmanship (how well it was stitched), Finish (what the leather was made to do) and an optional innate trait.
 * Stored in the namespaced {@code immersive_tanning} compound so other mods' NBT is left alone; stacks without it
 * behave exactly like ordinary items.
 */
public record LeatherworkData(Finish finish, int integrity, int workmanship, @Nullable TraitRef trait, @Nullable String crafter) {
    public static final String ROOT = "immersive_tanning";
    public static final int VERSION = 1;
    public static final String CARRY = "DurabilityCarry";

    public LeatherworkData {
        integrity = Mth.clamp(integrity, 0, 100);
        workmanship = Mth.clamp(workmanship, 0, 100);
    }

    public TanningQuality quality() {
        return TanningQuality.fromScore(overallScore());
    }

    public int overallScore() {
        return TanningQuality.overallScore(integrity, workmanship);
    }

    public static boolean has(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(ROOT, Tag.TAG_COMPOUND) && tag.getCompound(ROOT).contains("Finish", Tag.TAG_STRING);
    }

    public static Optional<LeatherworkData> get(ItemStack stack) {
        if (!has(stack)) return Optional.empty();
        CompoundTag root = stack.getTag().getCompound(ROOT);
        Finish finish = Finish.byName(root.getString("Finish"));
        if (finish == null) return Optional.empty();
        String crafter = root.contains("Crafter", Tag.TAG_STRING) ? root.getString("Crafter") : null;
        return Optional.of(new LeatherworkData(finish, root.getInt("Integrity"), root.getInt("Workmanship"), TraitRef.load(root), crafter));
    }

    /** The UUID of the player who stitched the piece at the bench. Villager and loot leatherwork have none. */
    public static final String CRAFTER_UUID = "CrafterUUID";

    /** Records the player who stitched the piece. Only the bench calls this; {@link #apply} leaves it in place. */
    public static void stampCrafter(ItemStack stack, UUID player) {
        stack.getOrCreateTagElement(ROOT).putUUID(CRAFTER_UUID, player);
    }

    /** The player who stitched the piece, when a player did. */
    public static Optional<UUID> crafterId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(ROOT, Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag root = tag.getCompound(ROOT);
        return root.hasUUID(CRAFTER_UUID) ? Optional.of(root.getUUID(CRAFTER_UUID)) : Optional.empty();
    }

    /** Writes this identity onto the stack, keeping any durability carry already accumulated. */
    public void apply(ItemStack stack) {
        CompoundTag root = stack.getOrCreateTagElement(ROOT);
        root.putInt("Version", VERSION);
        root.putString("Finish", finish.getSerializedName());
        root.putInt("Integrity", integrity);
        root.putInt("Workmanship", workmanship);
        for (String key : new String[]{"Trait", "TraitEffect", "TraitStrength", "TraitColor"}) root.remove(key);
        if (trait != null) trait.save(root);
        if (crafter != null) root.putString("Crafter", crafter);
        else root.remove("Crafter");
    }
}
