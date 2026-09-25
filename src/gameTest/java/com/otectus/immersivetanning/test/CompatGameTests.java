package com.otectus.immersivetanning.test;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.hide.HideItems;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.hide.HideSourceLoader;
import com.otectus.immersivetanning.item.RawHideItem;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.leather.LeatherworkData;
import com.otectus.immersivetanning.loot.HideDrops;
import com.otectus.immersivetanning.minigame.SessionManager;
import com.otectus.immersivetanning.recipe.EquipmentSeams;
import com.otectus.immersivetanning.recipe.TanningData;
import com.otectus.immersivetanning.registry.ModBlocks;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModTags;
import com.otectus.immersivetanning.trait.HideTraitLoader;
import com.otectus.immersivetanning.trait.TraitEffects;
import com.otectus.immersivetanning.trait.TraitRef;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Modpack integration: hides through Forge entity tags and the drops fallback, foreign hides on the rack, carcass
 * kills, trait damage tags, loot functions, village trades, bench experience and tag-chosen seams. The test datapack
 * (src/gameTest/resources) stands vanilla content in for modded content.
 */
@GameTestHolder(ImmersiveTanning.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompatGameTests {

    @GameTest(template = "empty")
    public static void forgeEntityTagsDropHides(GameTestHelper h) {
        HideSourceLoader.HideSource ravager = HideSourceLoader.forEntity(EntityType.RAVAGER);
        h.assertTrue(ravager != null && ravager.size() == HideSize.LARGE, "An animal in #forge:cow drops a large hide like a cow");
        HideSourceLoader.HideSource apex = HideSourceLoader.all().stream().filter(s -> s.trait() != null && s.trait().getPath().equals("ironhide"))
                .findFirst().orElse(null);
        h.assertTrue(apex != null && apex.traitRef() != null && apex.traitRef().strength() > 1.5F, "Apex creatures carry a stronger trait");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void foreignHidesMountOnTheRack(GameTestHelper h) {
        var level = h.getLevel();
        HideData rabbit = HideItems.resolve(level, new ItemStack(Items.RABBIT_HIDE)).orElse(null);
        h.assertTrue(rabbit != null && rabbit.size() == HideSize.SMALL && !rabbit.isFleshed(), "Rabbit hide mounts as a small raw hide");
        HideData membrane = HideItems.resolve(level, new ItemStack(Items.PHANTOM_MEMBRANE)).orElse(null);
        h.assertTrue(membrane != null && membrane.trait() != null && membrane.trait().id().equals(ImmersiveTanning.id("lightweight")),
                "A mounting recipe can give the hide a trait");
        h.assertTrue(HideItems.resolve(level, new ItemStack(Items.BONE)).isEmpty(), "Other items are not hides");
        h.assertTrue(HideItems.recipe(level, new ItemStack(ModItems.rawHide(HideSize.LARGE))).isEmpty()
                && HideItems.isMountable(level, new ItemStack(ModItems.rawHide(HideSize.LARGE))), "Raw hides need no recipe");

        h.setBlock(TestSupport.STATION, ModBlocks.TANNERS_RACK.get());
        TannersRackBlockEntity rack = (TannersRackBlockEntity) level.getBlockEntity(h.absolutePos(TestSupport.STATION));
        FakePlayer player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PHANTOM_MEMBRANE, 2));
        TestSupport.use(h, player);
        h.assertTrue(rack.mode() == TannersRackBlockEntity.Mode.RAW && rack.hide().size() == HideSize.SMALL
                && rack.hide().trait() != null, "The membrane is laid on the beam as a small lightweight hide");
        h.assertTrue(player.getMainHandItem().getCount() == 1, "One membrane is used");
        ItemStack back = rack.takeRaw();
        h.assertTrue(back.getItem() instanceof RawHideItem raw && raw.size() == HideSize.SMALL, "Taken back, it is an ordinary raw hide");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void carcassKillsLeaveTheSkinToTheButcher(GameTestHelper h) {
        Cow cow = EntityType.COW.create(h.getLevel());
        FakePlayer player = TestSupport.player(h);
        HideSourceLoader.HideSource source = HideSourceLoader.forEntity(EntityType.COW);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_HOE));
        h.assertTrue(HideDrops.butcheredAsCarcass(cow, player), "A carcass tool on a carcass animal is a carcass kill");
        List<ItemStack> loot = new ArrayList<>(List.of(new ItemStack(Items.LEATHER, 2), new ItemStack(Items.BEEF)));
        HideDrops.apply(loot, cow, source, player, 0, h.getLevel().getRandom());
        h.assertTrue(loot.size() == 1 && loot.get(0).is(Items.BEEF), "A carcass kill drops neither leather nor a hide");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        h.assertTrue(!HideDrops.butcheredAsCarcass(cow, player), "A sword kill is not a carcass kill");
        loot = new ArrayList<>(List.of(new ItemStack(Items.LEATHER, 2)));
        HideDrops.apply(loot, cow, source, player, 0, h.getLevel().getRandom());
        h.assertTrue(loot.size() == 1 && loot.get(0).is(ModItems.rawHide(HideSize.LARGE)), "A sword kill drops the hide instead of leather");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_HOE));
        cow.setBaby(true);
        h.assertTrue(!HideDrops.butcheredAsCarcass(cow, player), "Calves are never carcasses");
        h.assertTrue(!HideDrops.butcheredAsCarcass(EntityType.STRIDER.create(h.getLevel()), player), "Animals without a skin keep their hide");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void dropsWithoutALootTableGetHides(GameTestHelper h) {
        var level = h.getLevel();
        Cow cow = EntityType.COW.create(level);
        cow.setPos(h.absoluteVec(TestSupport.STATION.getCenter()));
        List<ItemEntity> drops = new ArrayList<>();
        drops.add(new ItemEntity(level, cow.getX(), cow.getY(), cow.getZ(), new ItemStack(Items.LEATHER, 3)));
        drops.add(new ItemEntity(level, cow.getX(), cow.getY(), cow.getZ(), new ItemStack(Items.BEEF)));
        HideDrops.onLivingDrops(new LivingDropsEvent(cow, level.damageSources().generic(), drops, 0, true));
        h.assertTrue(drops.size() == 2 && drops.stream().anyMatch(e -> e.getItem().is(ModItems.rawHide(HideSize.LARGE)))
                && drops.stream().noneMatch(e -> e.getItem().is(Items.LEATHER)), "Custom leather drops become the hide");

        drops.add(new ItemEntity(level, cow.getX(), cow.getY(), cow.getZ(), new ItemStack(Items.LEATHER)));
        HideDrops.onLivingDrops(new LivingDropsEvent(cow, level.damageSources().generic(), drops, 0, true));
        h.assertTrue(drops.stream().filter(e -> e.getItem().is(ModItems.rawHide(HideSize.LARGE))).count() == 1
                && drops.stream().noneMatch(e -> e.getItem().is(Items.LEATHER) || e.getItem().is(ModItems.BINDING_LEATHER.get())),
                "Drops that already hold a hide get no second one, and leather dropped on top of it goes");

        Zombie zombie = EntityType.ZOMBIE.create(level);
        List<ItemEntity> zombieDrops = new ArrayList<>();
        zombieDrops.add(new ItemEntity(level, 0, 0, 0, new ItemStack(Items.LEATHER)));
        HideDrops.onLivingDrops(new LivingDropsEvent(zombie, level.damageSources().generic(), zombieDrops, 0, true));
        h.assertTrue(zombieDrops.get(0).getItem().is(ModItems.BINDING_LEATHER.get()), "Other mobs' custom leather becomes Binding Leather");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void traitsResistTheirDamageTags(GameTestHelper h) {
        var damageTypes = h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        h.assertTrue(is(damageTypes, DamageTypes.LAVA, ModTags.RESISTED_BY_HEATPROOF), "Heatproof resists fire damage");
        h.assertTrue(is(damageTypes, DamageTypes.FREEZE, ModTags.RESISTED_BY_INSULATING), "Insulating resists freezing");
        h.assertTrue(is(damageTypes, DamageTypes.DROWN, ModTags.RESISTED_BY_WATER_RESISTANT), "Water-Resistant resists drowning");
        h.assertTrue(!is(damageTypes, DamageTypes.FALL, ModTags.RESISTED_BY_HEATPROOF), "Falling is not heat");
        TraitRef heatproof = HideTraitLoader.get(ImmersiveTanning.id("heatproof"));
        h.assertTrue(TraitEffects.get(heatproof.effect()).damageMultiplier(h.getLevel().damageSources().lava(), heatproof) < 1F,
                "A heatproof piece reduces lava damage");
        TraitRef ironhide = HideTraitLoader.get(ImmersiveTanning.id("ironhide"));
        Object[] args = TraitEffects.get(ironhide.effect()).descriptionArgs(ironhide);
        h.assertTrue(args.length == 1 && "40".equals(args[0]), "Descriptions state the trait's own strength, got " + List.of(args));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lootFunctionsMakeLeatherworkAndTrophies(GameTestHelper h) {
        var server = h.getLevel().getServer();
        LootParams params = new LootParams.Builder(h.getLevel()).create(LootContextParamSets.EMPTY);
        List<ItemStack> boots = server.getLootData().getLootTable(ImmersiveTanning.id("test/village_boots")).getRandomItems(params);
        LeatherworkData data = boots.isEmpty() ? null : LeatherworkData.get(boots.get(0)).orElse(null);
        h.assertTrue(data != null && data.finish() == Finish.HARNESS && data.integrity() == 80 && data.workmanship() >= 88
                && data.trait() != null && data.trait().id().equals(ImmersiveTanning.id("tough")), "set_leatherwork writes the piece");
        List<ItemStack> hides = server.getLootData().getLootTable(ImmersiveTanning.id("test/trophy_hide")).getRandomItems(params);
        HideData hide = hides.isEmpty() ? null : HideData.of(hides.get(0), HideSize.LARGE);
        h.assertTrue(hides.size() == 1 && hides.get(0).getCount() == 2 && hide.trait() != null
                && hide.trait().id().equals(ImmersiveTanning.id("insulating")), "set_hide_trait makes trophy hides");
        h.assertTrue(ItemStack.isSameItemSameTags(hides.get(0), ModItems.rawHide(HideSize.LARGE).create(hide.trait())),
                "Trophy hides from loot stack with dropped ones");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void leatherworkersTradeTheCraft(GameTestHelper h) {
        Villager villager = EntityType.VILLAGER.create(h.getLevel());
        var trades = VillagerTrades.TRADES.get(VillagerProfession.LEATHERWORKER);
        h.assertTrue(offers(trades.get(1), villager, h).stream().anyMatch(o -> o.getResult().is(ModItems.TANNERS_KNIFE.get())),
                "Novice leatherworkers sell the Tanner's Knife");
        h.assertTrue(offers(trades.get(1), villager, h).stream().anyMatch(o -> o.getBaseCostA().is(ModItems.rawHide(HideSize.MEDIUM))),
                "Leatherworkers buy hides");
        List<MerchantOffer> journeyman = offers(trades.get(3), villager, h);
        h.assertTrue(journeyman.stream().anyMatch(o -> o.getResult().is(Items.LEATHER_BOOTS) && LeatherworkData.has(o.getResult())),
                "Journeymen sell village leatherwork");
        h.assertTrue(offers(trades.get(5), villager, h).stream().filter(o -> o.getResult().is(Items.SADDLE) || o.getResult().is(Items.LEATHER_BOOTS)
                || o.getResult().is(Items.LEATHER_HELMET)).allMatch(o -> LeatherworkData.has(o.getResult())), "Master offers are leatherwork too");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void mixedTagsAreNotEquipment(GameTestHelper h) {
        Ingredient leather = Ingredient.of(Items.LEATHER);
        Ingredient arrows = Ingredient.of(Items.ARROW, Items.BOW);
        NonNullList<Ingredient> quiver = NonNullList.of(Ingredient.EMPTY, leather, leather, arrows, leather);
        var recipe = new ShapedRecipe(ImmersiveTanning.id("test/quiver"), "", CraftingBookCategory.EQUIPMENT, 2, 2, quiver,
                new ItemStack(Items.LEATHER_BOOTS));
        List<String> notes = new ArrayList<>();
        h.assertTrue(TanningData.detect(recipe, new ItemStack(Items.LEATHER_BOOTS), notes) != null,
                "An ingredient that merely includes a damageable item is an auxiliary, got " + notes);
        NonNullList<Ingredient> upgrade = NonNullList.of(Ingredient.EMPTY, leather, Ingredient.of(Items.IRON_BOOTS));
        var upgradeRecipe = new ShapedRecipe(ImmersiveTanning.id("test/upgrade"), "", CraftingBookCategory.EQUIPMENT, 2, 1, upgrade,
                new ItemStack(Items.LEATHER_BOOTS));
        h.assertTrue(TanningData.detect(upgradeRecipe, new ItemStack(Items.LEATHER_BOOTS), notes) == null, "A piece of gear as an ingredient is an upgrade");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void progressionHooks(GameTestHelper h) {
        h.assertTrue(SessionManager.benchExperience(new LeatherworkData(Finish.SUPPLE, 50, 50, null, null)) == 4, "Standard work gives the base experience");
        h.assertTrue(SessionManager.benchExperience(new LeatherworkData(Finish.SUPPLE, 100, 100, null, null)) == 6, "Masterwork gives half again");
        h.assertTrue(SessionManager.benchExperience(new LeatherworkData(Finish.SUPPLE, 0, 0, null, null)) == 2, "Crude work gives half");
        h.assertTrue(EquipmentSeams.classify(new ItemStack(Items.CARROT_ON_A_STICK)).equals(ImmersiveTanning.id("saddle")),
                "An item tag picks the seam");
        h.assertTrue(new ItemStack(ModItems.TANNERS_KNIFE.get()).is(net.minecraft.tags.ItemTags.create(new net.minecraft.resources.ResourceLocation("forge", "skinning_knives"))),
                "The Tanner's Knife is a skinning knife");
        h.succeed();
    }

    private static boolean is(net.minecraft.core.Registry<DamageType> registry, ResourceKey<DamageType> key, net.minecraft.tags.TagKey<DamageType> tag) {
        return registry.getHolderOrThrow(key).is(tag);
    }

    private static List<MerchantOffer> offers(VillagerTrades.ItemListing[] listings, Villager villager, GameTestHelper h) {
        List<MerchantOffer> out = new ArrayList<>();
        for (VillagerTrades.ItemListing listing : listings) {
            MerchantOffer offer = listing.getOffer(villager, h.getLevel().getRandom());
            if (offer != null) out.add(offer);
        }
        return out;
    }

    private CompatGameTests() {}
}
