package com.otectus.immersivetanning.client.compat;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A recipe viewer's "show recipes for this item", set by the viewer's plugin when it is installed (JEI: when its
 * runtime is available). Lets the pattern book offer the viewer without referring to any viewer class itself.
 */
public final class RecipeViewer {
    @Nullable
    private static volatile Consumer<ItemStack> show;

    public static void install(@Nullable Consumer<ItemStack> showRecipes) {
        show = showRecipes;
    }

    public static boolean available() {
        return show != null;
    }

    public static void show(ItemStack stack) {
        Consumer<ItemStack> s = show;
        if (s != null) s.accept(stack);
    }

    private RecipeViewer() {}
}
