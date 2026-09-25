package com.otectus.immersivetanning.registry;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.recipe.HideMountingRecipeSerializer;
import com.otectus.immersivetanning.recipe.LeatherConversionRecipe;
import com.otectus.immersivetanning.recipe.LeatherworkingRecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, ImmersiveTanning.MOD_ID);

    public static final RegistryObject<LeatherworkingRecipeSerializer> LEATHERWORKING = RECIPE_SERIALIZERS.register("leatherworking",
            LeatherworkingRecipeSerializer::new);

    public static final RegistryObject<SimpleCraftingRecipeSerializer<LeatherConversionRecipe>> LEATHER_CONVERSION = RECIPE_SERIALIZERS.register("leather_conversion",
            () -> new SimpleCraftingRecipeSerializer<>(LeatherConversionRecipe::new));

    public static final RegistryObject<HideMountingRecipeSerializer> HIDE_MOUNTING = RECIPE_SERIALIZERS.register("hide_mounting",
            HideMountingRecipeSerializer::new);

    private ModRecipeSerializers() {}
}
