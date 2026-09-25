package com.otectus.immersivetanning.client.compat.jei;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.recipe.LeatherworkingRecipe;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModRecipeTypes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * JEI support. The client recipe manager already holds every active bench recipe (explicit and automatically
 * detected) and no longer holds the crafting recipes the server routed through the bench, so JEI shows exactly the
 * rules the server enforces.
 */
@JeiPlugin
public class ImmersiveTanningJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ImmersiveTanning.id("jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new TanneryCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new LeatherworkingCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(TanneryCategory.TYPE, List.of(HideSize.values()));
        if (Minecraft.getInstance().level != null) {
            List<LeatherworkingRecipe> recipes = new ArrayList<>(Minecraft.getInstance().level.getRecipeManager()
                    .getAllRecipesFor(ModRecipeTypes.LEATHERWORKING.get()));
            recipes.sort(Comparator.comparing(r -> BuiltInRegistries.ITEM.getKey(r.resultItem()).toString()));
            registration.addRecipes(LeatherworkingCategory.TYPE, recipes);
        }
        info(registration, new ItemStack(ModItems.TANNERS_RACK.get()), "rack");
        info(registration, new ItemStack(ModItems.TANNERS_VAT.get()), "vat");
        info(registration, new ItemStack(ModItems.LEATHERWORKERS_BENCH.get()), "bench");
        info(registration, new ItemStack(ModItems.TANNERS_KNIFE.get()), "knife");
        info(registration, new ItemStack(ModItems.AWL.get()), "awl");
        info(registration, new ItemStack(ModItems.BARK.get()), "bark");
        info(registration, new ItemStack(ModItems.LEATHER_SCRAPS.get()), "scraps");
        info(registration, new ItemStack(ModItems.BINDING_LEATHER.get()), "binding");
        for (HideSize size : HideSize.values()) info(registration, new ItemStack(ModItems.rawHide(size)), "hide");
        for (Finish finish : Finish.values()) info(registration, new ItemStack(ModItems.leather(finish)), "leather_" + finish.getSerializedName());
    }

    private static void info(IRecipeRegistration registration, ItemStack stack, String key) {
        registration.addItemStackInfo(stack, Component.translatable("jei.immersive_tanning.info." + key));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModItems.TANNERS_RACK.get()), TanneryCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModItems.TANNERS_VAT.get()), TanneryCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModItems.LEATHERWORKERS_BENCH.get()), LeatherworkingCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModItems.AWL.get()), LeatherworkingCategory.TYPE);
    }
}
