package com.otectus.immersivetanning.recipe;

import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModRecipeSerializers;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * One vanilla leather alone in a crafting grid becomes one Binding Leather, so leather a player already holds is never
 * stranded. It compares items directly: an ordinary ingredient cannot name vanilla leather any more (see IngredientMixin).
 */
public class LeatherConversionRecipe extends CustomRecipe {

    public LeatherConversionRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        int leather = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            if (!stack.is(Items.LEATHER)) return false;
            leather++;
        }
        return leather == 1;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
        return new ItemStack(ModItems.BINDING_LEATHER.get());
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.LEATHER_CONVERSION.get();
    }
}
