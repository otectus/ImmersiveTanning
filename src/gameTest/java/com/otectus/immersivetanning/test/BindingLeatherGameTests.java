package com.otectus.immersivetanning.test;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.leather.BindingLeather;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.loot.BindingLeatherLootModifier;
import com.otectus.immersivetanning.recipe.LeatherConversionRecipe;
import com.otectus.immersivetanning.recipe.TanningData;
import com.otectus.immersivetanning.registry.ModItems;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.common.Tags;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/** Binding Leather takes vanilla leather's place in ingredients, results, anvil repair, loot and trades. */
@GameTestHolder(ImmersiveTanning.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BindingLeatherGameTests {

    @GameTest(template = "empty")
    public static void ingredientsTakeBindingLeatherInsteadOfLeather(GameTestHelper h) {
        ItemStack binding = new ItemStack(ModItems.BINDING_LEATHER.get());
        ItemStack leather = new ItemStack(Items.LEATHER);
        Ingredient item = Ingredient.of(Items.LEATHER);
        h.assertTrue(item.test(binding) && !item.test(leather), "An ingredient naming leather takes Binding Leather, not leather");
        Ingredient tag = Ingredient.of(Tags.Items.LEATHER);
        h.assertTrue(tag.test(binding) && !tag.test(leather), "#forge:leather takes Binding Leather, not leather");
        h.assertTrue(!tag.test(new ItemStack(ModItems.leather(Finish.HARNESS))), "Finished leather is cut into Binding Leather first");
        h.assertTrue(item.getItems().length == 1 && item.getItems()[0].is(ModItems.BINDING_LEATHER.get()), "Recipe viewers show Binding Leather");
        h.assertTrue(Items.LEATHER_CHESTPLATE.isValidRepairItem(new ItemStack(Items.LEATHER_CHESTPLATE), binding)
                && !Items.LEATHER_CHESTPLATE.isValidRepairItem(new ItemStack(Items.LEATHER_CHESTPLATE), leather), "Anvils repair leather armor with Binding Leather");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void recipesCraftWithAndMakeBindingLeather(GameTestHelper h) {
        var manager = h.getLevel().getServer().getRecipeManager();
        var access = h.getLevel().registryAccess();
        TransientCraftingContainer grid = grid();
        grid.setItem(0, new ItemStack(ModItems.BINDING_LEATHER.get()));
        for (int i = 1; i <= 3; i++) grid.setItem(i, new ItemStack(Items.PAPER));
        Optional<CraftingRecipe> book = manager.getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel());
        h.assertTrue(book.isPresent() && book.get().assemble(grid, access).is(Items.BOOK), "Binding Leather and paper make a book");
        grid.setItem(0, new ItemStack(Items.LEATHER));
        h.assertTrue(manager.getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel()).isEmpty(), "Vanilla leather no longer makes a book");

        var rabbitLeather = manager.byKey(new ResourceLocation("minecraft", "leather")).orElseThrow();
        h.assertTrue(rabbitLeather.getResultItem(access).is(ModItems.BINDING_LEATHER.get()), "Rabbit hide makes Binding Leather");
        h.assertTrue(TanningData.current().bindingResults() >= 1, "Leather-making recipes are counted");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void leftoverLeatherConvertsOneToOne(GameTestHelper h) {
        var manager = h.getLevel().getServer().getRecipeManager();
        TransientCraftingContainer grid = grid();
        grid.setItem(4, new ItemStack(Items.LEATHER));
        Optional<CraftingRecipe> recipe = manager.getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel());
        h.assertTrue(recipe.isPresent() && recipe.get() instanceof LeatherConversionRecipe, "A lone leather converts");
        h.assertTrue(recipe.get().assemble(grid, h.getLevel().registryAccess()).is(ModItems.BINDING_LEATHER.get()), "It becomes Binding Leather");
        grid.setItem(5, new ItemStack(Items.LEATHER));
        h.assertTrue(manager.getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel()).map(r -> !(r instanceof LeatherConversionRecipe)).orElse(true),
                "Two leathers are not a conversion");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lootAndTradesDealInBindingLeather(GameTestHelper h) {
        ObjectArrayList<ItemStack> loot = new ObjectArrayList<>();
        loot.add(new ItemStack(Items.LEATHER, 3));
        loot.add(new ItemStack(Items.BONE));
        BindingLeatherLootModifier.convert(loot);
        h.assertTrue(loot.get(0).is(ModItems.BINDING_LEATHER.get()) && loot.get(0).getCount() == 3 && loot.get(1).is(Items.BONE),
                "Loot leather becomes Binding Leather, other loot is untouched");

        MerchantOffer buy = new MerchantOffer(new ItemStack(Items.LEATHER, 6), ItemStack.EMPTY, new ItemStack(Items.EMERALD), 0, 16, 2, 0.05F, 0);
        MerchantOffer swapped = BindingLeather.offer(buy);
        h.assertTrue(swapped.getBaseCostA().is(ModItems.BINDING_LEATHER.get()) && swapped.getBaseCostA().getCount() == 6
                && swapped.getResult().is(Items.EMERALD) && swapped.getMaxUses() == 16, "A leather trade asks for Binding Leather");
        MerchantOffer other = new MerchantOffer(new ItemStack(Items.EMERALD), ItemStack.EMPTY, new ItemStack(Items.BREAD), 0, 16, 2, 0.05F, 0);
        h.assertTrue(BindingLeather.offer(other) == other, "Other trades are left alone");
        h.succeed();
    }

    static TransientCraftingContainer grid() {
        AbstractContainerMenu menu = new AbstractContainerMenu(null, 0) {
            @Override
            public ItemStack quickMoveStack(Player player, int slot) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        };
        return new TransientCraftingContainer(menu, 3, 3);
    }

    private BindingLeatherGameTests() {}
}
