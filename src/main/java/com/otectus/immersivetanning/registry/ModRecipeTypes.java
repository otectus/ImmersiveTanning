package com.otectus.immersivetanning.registry;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.recipe.HideMountingRecipe;
import com.otectus.immersivetanning.recipe.LeatherworkingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipeTypes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, ImmersiveTanning.MOD_ID);

    public static final RegistryObject<RecipeType<LeatherworkingRecipe>> LEATHERWORKING = RECIPE_TYPES.register("leatherworking",
            () -> RecipeType.simple(ImmersiveTanning.id("leatherworking")));

    public static final RegistryObject<RecipeType<HideMountingRecipe>> HIDE_MOUNTING = RECIPE_TYPES.register("hide_mounting",
            () -> RecipeType.simple(ImmersiveTanning.id("hide_mounting")));

    private ModRecipeTypes() {}
}
