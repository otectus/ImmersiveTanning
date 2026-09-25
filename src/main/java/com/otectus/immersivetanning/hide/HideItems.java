package com.otectus.immersivetanning.hide;

import com.otectus.immersivetanning.item.RawHideItem;
import com.otectus.immersivetanning.recipe.HideMountingRecipe;
import com.otectus.immersivetanning.registry.ModRecipeTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * What the Tanner's Rack accepts as a raw hide: this mod's raw hides, then any item a hide mounting recipe names
 * (Butchery skins, modded pelts). Works on both sides; on the client a mounted trait is not resolved, which only
 * matters to the server.
 */
public final class HideItems {

    /** The raw hide a stack mounts as, if the rack accepts it. */
    public static Optional<HideData> resolve(Level level, ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        if (stack.getItem() instanceof RawHideItem raw) return Optional.of(raw.data(stack));
        return recipe(level, stack).map(HideMountingRecipe::hide);
    }

    public static boolean isMountable(Level level, ItemStack stack) {
        return stack.getItem() instanceof RawHideItem || recipe(level, stack).isPresent();
    }

    /** The mounting recipe for a foreign hide item; empty for this mod's raw hides and anything else. */
    public static Optional<HideMountingRecipe> recipe(Level level, ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof RawHideItem) return Optional.empty();
        for (HideMountingRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.HIDE_MOUNTING.get())) {
            if (recipe.accepts(stack)) return Optional.of(recipe);
        }
        return Optional.empty();
    }

    private HideItems() {}
}
