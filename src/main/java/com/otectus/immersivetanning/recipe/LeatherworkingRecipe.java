package com.otectus.immersivetanning.recipe;

import com.otectus.immersivetanning.minigame.StitchPattern;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModRecipeSerializers;
import com.otectus.immersivetanning.registry.ModRecipeTypes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One piece the Leatherworker's Bench can make: how many units of finished leather (all of one finish and trait),
 * which other ingredients, and which seam is stitched. Explicit recipes come from datapacks; automatic ones are
 * generated from crafting recipes at reload and inserted into the recipe manager, so clients and JEI see them too.
 */
public class LeatherworkingRecipe implements Recipe<Container> {
    private final ResourceLocation id;
    private final int leather;
    private final List<AuxiliaryIngredient> auxiliary;
    private final ItemStack result;
    @Nullable
    private final ResourceLocation seam;
    private final boolean auto;
    @Nullable
    private final ResourceLocation sourceRecipe;
    /** Filled in on the server during rebuild and carried to clients. */
    private ResourceLocation resolvedSeam = StitchPattern.GENERIC;

    public LeatherworkingRecipe(ResourceLocation id, int leather, List<AuxiliaryIngredient> auxiliary, ItemStack result,
                                @Nullable ResourceLocation seam, boolean auto, @Nullable ResourceLocation sourceRecipe) {
        this.id = id;
        this.leather = leather;
        this.auxiliary = List.copyOf(auxiliary);
        this.result = result;
        this.seam = seam;
        this.auto = auto;
        this.sourceRecipe = sourceRecipe;
        if (seam != null) resolvedSeam = seam;
    }

    public int leather() { return leather; }
    public List<AuxiliaryIngredient> auxiliary() { return auxiliary; }
    public ItemStack result() { return result; }
    @Nullable public ResourceLocation explicitSeam() { return seam; }
    public boolean isAuto() { return auto; }
    @Nullable public ResourceLocation sourceRecipe() { return sourceRecipe; }
    public ResourceLocation seam() { return resolvedSeam; }
    public Item resultItem() { return result.getItem(); }

    public void resolveSeam(ResourceLocation seam) {
        this.resolvedSeam = seam;
    }

    @Override
    public boolean matches(Container container, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess access) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess access) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        auxiliary.forEach(a -> list.add(a.ingredient()));
        return list;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean isIncomplete() {
        return false;
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(ModItems.LEATHERWORKERS_BENCH.get());
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.LEATHERWORKING.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.LEATHERWORKING.get();
    }
}
