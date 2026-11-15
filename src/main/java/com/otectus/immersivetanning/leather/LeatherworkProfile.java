package com.otectus.immersivetanning.leather;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.registry.ModTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which effect policy a piece of leatherwork follows (see {@code effect/LeatherworkPolicy}). Ordinary leatherwork is
 * the default. Scale-backed leatherwork is another mod's armor stitched at the bench over a leather backing
 * ({@code #immersive_tanning:scale_backed}, such as Ice and Fire's dragon-scale armor): its own armor does the work.
 *
 * <p>Writing leatherwork onto a tagged item records the profile in the piece's own {@code immersive_tanning} compound,
 * so a piece stays scale-backed when a datapack later changes the tag. Only the scale-backed profile is ever recorded;
 * a piece without a record follows the tag. A record this version does not know follows no finish or quality policy
 * at all, never the stronger ordinary leather values.</p>
 */
public enum LeatherworkProfile {
    LEATHER,
    SCALE_BACKED,
    UNRECOGNISED;

    public static final String KEY = "Profile";
    private static final String SCALE_BACKED_NAME = "scale_backed";
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    public static LeatherworkProfile of(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(LeatherworkData.ROOT, Tag.TAG_COMPOUND)) {
            CompoundTag root = tag.getCompound(LeatherworkData.ROOT);
            if (root.contains(KEY, Tag.TAG_STRING)) {
                String recorded = root.getString(KEY);
                if (recorded.equals(SCALE_BACKED_NAME)) return SCALE_BACKED;
                if (WARNED.add(recorded)) {
                    ImmersiveTanning.LOGGER.warn("Leatherwork profile '{}' is unknown to this version; pieces carrying it get no finish or quality effects", recorded);
                }
                return UNRECOGNISED;
            }
        }
        return stack.is(ModTags.SCALE_BACKED) ? SCALE_BACKED : LEATHER;
    }

    /** Records the scale-backed profile on a tagged piece; ordinary leatherwork is left without a record. */
    public static void record(ItemStack stack) {
        if (stack.is(ModTags.SCALE_BACKED)) stack.getOrCreateTagElement(LeatherworkData.ROOT).putString(KEY, SCALE_BACKED_NAME);
    }
}
