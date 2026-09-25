package com.otectus.immersivetanning.recipe;

import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModRecipeSerializers;
import com.otectus.immersivetanning.registry.ModRecipeTypes;
import com.otectus.immersivetanning.trait.HideTraitLoader;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Another mod's skin, pelt or fur that the Tanner's Rack accepts as a raw hide of one size, so every route to leather
 * (a hide drop, a skinned carcass, a modded pelt) ends at the same rack. The item becomes an ordinary raw hide the
 * moment it is mounted. A recipe, not a reload listener, because recipes reach clients (JEI, tooltips) and honour
 * {@code forge:mod_loaded} conditions.
 */
public class HideMountingRecipe implements Recipe<Container> {
    private final ResourceLocation id;
    private final Ingredient ingredient;
    private final HideSize size;
    @Nullable
    private final ResourceLocation trait;

    public HideMountingRecipe(ResourceLocation id, Ingredient ingredient, HideSize size, @Nullable ResourceLocation trait) {
        this.id = id;
        this.ingredient = ingredient;
        this.size = size;
        this.trait = trait;
    }

    public Ingredient ingredient() { return ingredient; }
    public HideSize size() { return size; }
    @Nullable public ResourceLocation trait() { return trait; }

    public boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && ingredient.test(stack);
    }

    /** The raw hide this item becomes. The trait resolves on the server, where hide traits are loaded. */
    public HideData hide() {
        return HideData.raw(size, trait == null ? null : HideTraitLoader.get(trait));
    }

    @Override
    public boolean matches(Container container, Level level) {
        return container.getContainerSize() > 0 && accepts(container.getItem(0));
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess access) {
        return new ItemStack(ModItems.rawHide(size));
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess access) {
        return new ItemStack(ModItems.rawHide(size));
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, ingredient);
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(ModItems.TANNERS_RACK.get());
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.HIDE_MOUNTING.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.HIDE_MOUNTING.get();
    }
}
