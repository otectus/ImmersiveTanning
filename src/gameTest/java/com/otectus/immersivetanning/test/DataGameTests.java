package com.otectus.immersivetanning.test;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.effect.LeatherworkEffects;
import com.otectus.immersivetanning.hide.HideSourceLoader;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.leather.FieldRepair;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.leather.LeatherworkData;
import com.otectus.immersivetanning.recipe.LeatherworkingRecipe;
import com.otectus.immersivetanning.recipe.TanningData;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.trait.HideTraitLoader;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Datapack content, recipe routing, finish effects, durability and field repair. */
@GameTestHolder(ImmersiveTanning.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DataGameTests {

    @GameTest(template = "empty")
    public static void hideDataLoads(GameTestHelper h) {
        HideSourceLoader.HideSource cow = HideSourceLoader.forEntity(EntityType.COW);
        HideSourceLoader.HideSource rabbit = HideSourceLoader.forEntity(EntityType.RABBIT);
        HideSourceLoader.HideSource strider = HideSourceLoader.forEntity(EntityType.STRIDER);
        h.assertTrue(cow != null && cow.size() == HideSize.LARGE && cow.traitRef() == null, "Cows drop plain large hides");
        h.assertTrue(rabbit != null && rabbit.size() == HideSize.SMALL, "Rabbits drop small hides");
        h.assertTrue(strider != null && strider.traitRef() != null && strider.traitRef().id().equals(ImmersiveTanning.id("heatproof")),
                "Striders drop heatproof hides");
        h.assertTrue(HideSourceLoader.forEntity(EntityType.ZOMBIE) == null, "Zombies drop no hide");
        h.assertTrue(HideTraitLoader.all().size() >= 5, "The five built-in traits load");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void leatherEquipmentIsRoutedThroughTheBench(GameTestHelper h) {
        TanningData data = TanningData.current();
        for (String name : new String[]{"leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots", "leather_horse_armor", "saddle"}) {
            LeatherworkingRecipe recipe = data.recipe(ImmersiveTanning.id("leatherworking/" + name));
            h.assertTrue(recipe != null, "Bench recipe " + name + " is active");
        }
        var manager = h.getLevel().getServer().getRecipeManager();
        h.assertTrue(manager.byKey(new ResourceLocation("minecraft", "leather_chestplate")).isEmpty(), "The crafting-table chestplate recipe is disabled");
        h.assertTrue(manager.byKey(new ResourceLocation("minecraft", "book")).isPresent(), "Books still craft with leather");
        h.assertTrue(manager.byKey(ImmersiveTanning.id("binding_leather")).isPresent(), "Finished leather cuts into Binding Leather");
        h.assertTrue(data.recipe(ImmersiveTanning.id("leatherworking/leather_chestplate")).seam().equals(ImmersiveTanning.id("chestplate")),
                "Recipes carry their seam");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void automaticDetectionReadsCraftingRecipes(GameTestHelper h) {
        Ingredient leather = Ingredient.of(Items.LEATHER);
        Ingredient string = Ingredient.of(Items.STRING);
        NonNullList<Ingredient> helmet = NonNullList.of(Ingredient.EMPTY, leather, leather, leather, leather, string, leather);
        var recipe = new ShapedRecipe(ImmersiveTanning.id("test/helmet"), "", CraftingBookCategory.EQUIPMENT, 3, 2, helmet,
                new ItemStack(Items.LEATHER_HELMET));
        java.util.List<String> notes = new java.util.ArrayList<>();
        LeatherworkingRecipe detected = TanningData.detect(recipe, new ItemStack(Items.LEATHER_HELMET), notes);
        h.assertTrue(detected != null && detected.leather() == 5 && detected.isAuto(), "Five leather slots become five leather");
        h.assertTrue(detected.auxiliary().size() == 1 && detected.auxiliary().get(0).count() == 1
                && detected.auxiliary().get(0).ingredient().test(new ItemStack(Items.STRING)), "The string becomes an auxiliary");

        NonNullList<Ingredient> withSword = NonNullList.of(Ingredient.EMPTY, leather, Ingredient.of(Items.IRON_SWORD));
        var sword = new ShapelessRecipe(ImmersiveTanning.id("test/sword"), "", CraftingBookCategory.EQUIPMENT, new ItemStack(Items.LEATHER_BOOTS), withSword);
        h.assertTrue(TanningData.detect(sword, new ItemStack(Items.LEATHER_BOOTS), notes) == null, "Equipment ingredients are never consumed");

        var book = new ShapelessRecipe(ImmersiveTanning.id("test/book"), "", CraftingBookCategory.MISC, new ItemStack(Items.BOOK),
                NonNullList.of(Ingredient.EMPTY, leather, Ingredient.of(Items.PAPER)));
        h.assertTrue(TanningData.detect(book, new ItemStack(Items.BOOK), notes) == null, "Stackable results are left to the crafting table");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void finishesChangeHowLeatherPlays(GameTestHelper h) {
        ItemStack supple = boots(Finish.SUPPLE, 100);
        ItemStack hardened = boots(Finish.HARDENED, 100);
        ItemStack harness = boots(Finish.HARNESS, 100);
        double plainArmor = armor(new ItemStack(Items.LEATHER_BOOTS));
        h.assertTrue(armor(hardened) > plainArmor + 0.5, "Hardened adds armor: " + armor(hardened) + " vs " + plainArmor);
        h.assertTrue(toughness(hardened) > 0, "Hardened adds toughness");
        h.assertTrue(speed(supple) > 0.02, "Supple adds movement speed per piece");
        h.assertTrue(speed(harness) == 0 && speed(hardened) == 0, "Only Supple speeds you up");
        double harnessDurability = LeatherworkEffects.durability(LeatherworkData.get(harness).orElseThrow());
        double suppleDurability = LeatherworkEffects.durability(LeatherworkData.get(supple).orElseThrow());
        h.assertTrue(harnessDurability > suppleDurability * 1.4, "Harness lasts longest: " + harnessDurability + " vs " + suppleDurability);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void integrityConvertsDurabilityDamage(GameTestHelper h) {
        ItemStack crude = new ItemStack(Items.LEATHER_CHESTPLATE);
        new LeatherworkData(Finish.SUPPLE, 0, 50, null, null).apply(crude);
        ItemStack fine = new ItemStack(Items.LEATHER_CHESTPLATE);
        new LeatherworkData(Finish.HARNESS, 100, 50, null, null).apply(fine);
        int crudeTotal = 0;
        int fineTotal = 0;
        for (int i = 0; i < 90; i++) {
            crudeTotal += LeatherworkEffects.adjustDamage(crude, 1);
            fineTotal += LeatherworkEffects.adjustDamage(fine, 1);
        }
        double expectedFine = 90 / (ServerConfig.get(ServerConfig.MAXIMUM_DURABILITY_MULTIPLIER) * ServerConfig.get(ServerConfig.HARNESS_DURABILITY_MULTIPLIER));
        h.assertTrue(crudeTotal > 90, "Crude Integrity wears faster, took " + crudeTotal);
        h.assertTrue(Math.abs(fineTotal - expectedFine) <= 1, "Harness at full Integrity wears at 1/(1.2*1.5), took " + fineTotal);
        h.assertTrue(LeatherworkEffects.adjustDamage(new ItemStack(Items.LEATHER_CHESTPLATE), 7) == 7, "Plain leather is unchanged");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void scrapsAndAnAwlPatchLeatherHarnessBest(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        ItemStack awl = new ItemStack(ModItems.AWL.get());
        ItemStack scraps = new ItemStack(ModItems.LEATHER_SCRAPS.get(), 4);
        ItemStack harness = boots(Finish.HARNESS, 50);
        ItemStack supple = boots(Finish.SUPPLE, 50);
        harness.setDamageValue(60);
        supple.setDamageValue(60);
        h.assertTrue(FieldRepair.repair(player, harness, scraps, awl), "Harness boots take a patch");
        h.assertTrue(FieldRepair.repair(player, supple, scraps, awl), "Supple boots take a patch");
        int harnessRepaired = 60 - harness.getDamageValue();
        int suppleRepaired = 60 - supple.getDamageValue();
        h.assertTrue(harnessRepaired >= suppleRepaired * 2 - 1, "Harness repairs twice as well: " + harnessRepaired + " vs " + suppleRepaired);
        h.assertTrue(scraps.getCount() == 2 && awl.getDamageValue() == 2, "Each patch spends a scrap and awl durability");
        h.assertTrue(LeatherworkData.get(harness).map(LeatherworkData::finish).orElse(null) == Finish.HARNESS, "Repair keeps the finish");
        ItemStack iron = new ItemStack(Items.IRON_CHESTPLATE);
        iron.setDamageValue(10);
        h.assertTrue(!FieldRepair.repair(player, iron, scraps, awl), "Scraps do not patch iron");
        h.succeed();
    }

    static ItemStack boots(Finish finish, int workmanship) {
        ItemStack stack = new ItemStack(Items.LEATHER_BOOTS);
        new LeatherworkData(finish, 80, workmanship, null, "Test").apply(stack);
        return stack;
    }

    static double armor(ItemStack stack) {
        return sum(stack, Attributes.ARMOR, AttributeModifier.Operation.ADDITION);
    }

    static double toughness(ItemStack stack) {
        return sum(stack, Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADDITION);
    }

    static double speed(ItemStack stack) {
        return sum(stack, Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.MULTIPLY_BASE);
    }

    static double sum(ItemStack stack, net.minecraft.world.entity.ai.attributes.Attribute attribute, AttributeModifier.Operation op) {
        double total = 0;
        for (AttributeModifier m : stack.getAttributeModifiers(EquipmentSlot.FEET).get(attribute)) {
            if (m.getOperation() == op) total += m.getAmount();
        }
        return total;
    }

    private DataGameTests() {}
}
