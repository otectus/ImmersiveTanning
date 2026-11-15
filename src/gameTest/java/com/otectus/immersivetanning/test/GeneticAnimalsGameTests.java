package com.otectus.immersivetanning.test;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.compat.geneticanimals.GeneticAnimals;
import com.otectus.immersivetanning.compat.geneticanimals.GeneticAnimalsDrops;
import com.otectus.immersivetanning.compat.geneticanimals.GeneticAnimalsHooks;
import com.otectus.immersivetanning.compat.geneticanimals.GeneticAnimalsTrades;
import com.otectus.immersivetanning.compat.geneticanimals.HideMode;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.hide.HideSourceLoader;
import com.otectus.immersivetanning.item.RawHideItem;
import com.otectus.immersivetanning.leather.BindingLeather;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.leather.LeatherworkData;
import com.otectus.immersivetanning.loot.HideDropModifier;
import com.otectus.immersivetanning.recipe.LeatherworkingRecipe;
import com.otectus.immersivetanning.recipe.TanningData;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModTags;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Genetic Animals integration. The policy, the decision API and the offer conversion run in every profile. With the
 * mod absent (the default profile) the integration must stay inert; with it present ({@code runGameTestServer
 * -PgeneticAnimals}, the pinned 0.11.12 release) real animals spawned through its own spawn path die, equip tack and
 * trade, and every result is compared with what Genetic Animals does on its own.
 */
@GameTestHolder(ImmersiveTanning.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GeneticAnimalsGameTests {
    private static final String TEMPLATE = ImmersiveTanning.MOD_ID + ":empty";
    private static final BlockPos SPOT = new BlockPos(2, 1, 2);
    /** Older than every Genetic Animals adult age (its largest default is the cow's 84000 ticks), with full yields. */
    private static final long ADULT = 400_000;
    private static final long CALF = 1_000;

    // ---- every profile --------------------------------------------------------------------------------------------

    @GameTest(template = "empty")
    public static void nativeLeatherBecomesTheFewestHides(GameTestHelper h) {
        int[][] large = {{0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {1, 0, 1}, {0, 1, 1}};
        for (int leather = 0; leather <= 5; leather++) {
            int[] got = GeneticAnimalsDrops.decompose(leather, HideSize.LARGE);
            h.assertTrue(java.util.Arrays.equals(got, large[leather]), leather + " leather, large at most: got " + java.util.Arrays.toString(got));
        }
        h.assertTrue(java.util.Arrays.equals(GeneticAnimalsDrops.decompose(5, HideSize.MEDIUM), new int[]{1, 2, 0}), "Capped at medium, 5 leather is two medium and a small");
        h.assertTrue(java.util.Arrays.equals(GeneticAnimalsDrops.decompose(3, HideSize.SMALL), new int[]{3, 0, 0}), "Capped at small, each leather is a small hide");

        HideSourceLoader.Decision cow = hide(HideSize.LARGE, 1);
        GeneticAnimalsDrops.Outcome many = GeneticAnimalsDrops.decide(cow, HideMode.NATIVE, false, true, false, 60, new int[3], 0, RandomSource.create(1));
        h.assertTrue(many.hides().size() == 2 && many.hides().stream().allMatch(s -> s.getCount() <= s.getMaxStackSize()) && count(many.hides(), HideSize.LARGE) == 20,
                "60 leather is twenty large hides in legal stacks (16 and 4), got " + many.hides());
        GeneticAnimalsDrops.Outcome rabbits = GeneticAnimalsDrops.decide(hide(HideSize.SMALL, 1), HideMode.NATIVE, false, true, false, 0,
                new int[]{3, 0, 0}, 0, RandomSource.create(1));
        h.assertTrue(count(rabbits.hides(), HideSize.SMALL) == 3 && rabbits.hides().stream().noneMatch(s -> size(s) != HideSize.SMALL),
                "Each rabbit hide is one small hide, never leather units");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void policyFollowsModeSourceAndSwitches(GameTestHelper h) {
        RandomSource random = RandomSource.create(7);
        int[] none = new int[3];
        HideSourceLoader.Decision cow = hide(HideSize.LARGE, 1);
        HideSourceLoader.Decision pig = hide(HideSize.MEDIUM, 1);

        h.assertTrue(GeneticAnimalsDrops.decide(new HideSourceLoader.Decision(HideSourceLoader.Kind.NO_SOURCE, null), HideMode.TANNING,
                false, true, false, 4, none, 0, random).hides().isEmpty(), "No source, no hide");
        HideSourceLoader.HideSource stop = new HideSourceLoader.HideSource(id("stop"), List.of(), List.of(), null, 1, null, 50);
        GeneticAnimalsDrops.Outcome stopped = GeneticAnimalsDrops.decide(new HideSourceLoader.Decision(HideSourceLoader.Kind.SUPPRESSED, stop),
                HideMode.TANNING, false, true, false, 4, none, 0, random);
        h.assertTrue(stopped.hides().isEmpty() && !stopped.consumeSkin() && stopped.reason().startsWith("explicit exclusion"),
                "An explicit \"none\" stops hides and keeps the animal's own skin, got " + stopped);

        GeneticAnimalsDrops.Outcome woolOnly = GeneticAnimalsDrops.decide(cow, HideMode.NATIVE, false, true, false, 0, none, 3, random);
        h.assertTrue(woolOnly.hides().isEmpty() && woolOnly.reason().equals("native skin absent"), "NATIVE: no skin, no hide, whatever the Looting");
        GeneticAnimalsDrops.Outcome four = GeneticAnimalsDrops.decide(cow, HideMode.NATIVE, false, true, false, 4, none, 3, random);
        h.assertTrue(count(four.hides(), HideSize.LARGE) == 1 && count(four.hides(), HideSize.SMALL) == 1 && four.hides().size() == 2 && four.consumeSkin(),
                "NATIVE: 4 leather is a large and a small hide, Looting adds nothing, got " + four.hides());

        GeneticAnimalsDrops.Outcome pork = GeneticAnimalsDrops.decide(pig, HideMode.TANNING, false, true, false, 0, none, 0, random);
        h.assertTrue(count(pork.hides(), HideSize.MEDIUM) == 1 && pork.hides().size() == 1, "TANNING: an adult pig drops a medium hide with no leather");
        h.assertTrue(GeneticAnimalsDrops.decide(pig, HideMode.TANNING, false, true, true, 0, none, 0, random).hides().isEmpty(), "TANNING: never a hide from a juvenile");
        h.assertTrue(GeneticAnimalsDrops.decide(pig, HideMode.TANNING, false, false, false, 0, none, 0, random).hides().isEmpty(),
                "No loot (doMobLoot off) means no hide");
        GeneticAnimalsDrops.Outcome carcass = GeneticAnimalsDrops.decide(pig, HideMode.TANNING, true, true, false, 2, none, 0, random);
        h.assertTrue(carcass.hides().isEmpty() && carcass.consumeSkin(), "A carcass kill leaves the skin to the carcass");

        boolean looting = ServerConfig.LOOTING_ADDS_HIDES.get();
        boolean replace = ServerConfig.REPLACE_LEATHER_DROPS.get();
        try {
            ServerConfig.LOOTING_ADDS_HIDES.set(true);
            ServerConfig.REPLACE_LEATHER_DROPS.set(false);
            boolean bonus = false;
            for (int i = 0; i < 40; i++) {
                GeneticAnimalsDrops.Outcome looted = GeneticAnimalsDrops.decide(pig, HideMode.TANNING, false, true, false, 0, none, 3, random);
                int n = count(looted.hides(), HideSize.MEDIUM);
                h.assertTrue(n >= 1 && n <= 4 && !looted.consumeSkin(), "TANNING with lootingAddsHides: one hide plus up to one per level, once; got " + n);
                bonus |= n > 1;
            }
            h.assertTrue(bonus, "Looting III added a hide at least once in 40 kills");
        } finally {
            ServerConfig.LOOTING_ADDS_HIDES.set(looting);
            ServerConfig.REPLACE_LEATHER_DROPS.set(replace);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void offersKeepEveryFieldWhenConverted(GameTestHelper h) {
        ItemStack named = new ItemStack(Items.LEATHER_HORSE_ARMOR);
        named.setHoverName(Component.literal("Parade"));
        MerchantOffer original = offer(new ItemStack(Items.LEATHER, 5), new ItemStack(Items.EMERALD, 2), named, 3, 12, 7, 0.2F, 4, -2, false);
        MerchantOffer converted = BindingLeather.offer(original);
        h.assertTrue(converted.getBaseCostA().is(ModItems.BINDING_LEATHER.get()) && converted.getBaseCostA().getCount() == 5, "Leather in the cost becomes Binding Leather");
        h.assertTrue(converted.getCostB().is(Items.EMERALD) && converted.getCostB().getCount() == 2 && ItemStack.matches(converted.getResult(), named),
                "The other cost and the result are untouched, NBT included");
        h.assertTrue(converted.getUses() == 3 && converted.getMaxUses() == 12 && converted.getXp() == 7 && converted.getPriceMultiplier() == 0.2F
                && converted.getDemand() == 4 && converted.getSpecialPriceDiff() == -2 && !converted.shouldRewardExp(),
                "Uses, maximum uses, experience, multiplier, demand, special price and the reward flag survive");
        h.assertTrue(BindingLeather.offer(converted) == converted, "Converting again changes nothing");
        MerchantOffer plain = offer(new ItemStack(Items.EMERALD), ItemStack.EMPTY, new ItemStack(Items.SADDLE), 0, 4, 1, 0.05F, 0, 0, true);
        h.assertTrue(BindingLeather.offer(plain) == plain, "Offers without leather are returned as they are");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void decisionsTellNoSourceFromSuppression(GameTestHelper h) {
        // The test datapack: panda "none" and mule count 0 at priority 20; plural forge tags on stand-ins.
        HideSourceLoader.Decision panda = HideSourceLoader.resolve(EntityType.PANDA);
        h.assertTrue(panda.kind() == HideSourceLoader.Kind.SUPPRESSED && panda.source().id().equals(id("test/panda_none")), "size none is an explicit suppression");
        h.assertTrue(HideSourceLoader.resolve(EntityType.MULE).kind() == HideSourceLoader.Kind.SUPPRESSED, "count 0 is an explicit suppression");
        h.assertTrue(HideSourceLoader.resolve(EntityType.ZOMBIE).kind() == HideSourceLoader.Kind.NO_SOURCE && HideSourceLoader.resolve(EntityType.ZOMBIE).source() == null,
                "No source is not a suppression");
        h.assertTrue(HideSourceLoader.forEntity(EntityType.PANDA) == null && HideSourceLoader.forEntity(EntityType.ZOMBIE) == null
                && HideSourceLoader.forEntity(EntityType.COW) != null, "forEntity keeps its old answer for old callers");
        h.assertTrue(HideSourceLoader.matching(EntityType.PANDA).size() == 2 && HideSourceLoader.matching(EntityType.PANDA).get(0).id().equals(id("test/panda_none")),
                "The suppression shadows the medium source it outranks");

        h.assertTrue(sizeOf(EntityType.IRON_GOLEM) == HideSize.LARGE, "#forge:cows works next to #forge:cow");
        h.assertTrue(sizeOf(EntityType.OCELOT) == HideSize.MEDIUM, "#forge:pigs works next to #forge:pig");
        h.assertTrue(sizeOf(EntityType.FROG) == HideSize.SMALL, "#forge:rabbits works next to #forge:rabbit");
        h.assertTrue(sizeOf(EntityType.RAVAGER) == HideSize.LARGE && sizeOf(EntityType.SHEEP) == HideSize.MEDIUM && sizeOf(EntityType.LLAMA) == HideSize.LARGE,
                "The singular tags and vanilla sheep and llamas are unchanged");
        h.assertTrue(HideSourceLoader.invalid().isEmpty(), "Every shipped and test hide source parses, skipped: " + HideSourceLoader.invalid());
        h.succeed();
    }

    /** A real /reload with a world datapack that overrides a source, then without it: the decision follows each time. */
    @GameTest(template = "empty", batch = "datapack_reload", timeoutTicks = 400)
    public static void reloadsChangeDecisionsWithoutStaleState(GameTestHelper h) throws IOException {
        MinecraftServer server = h.getLevel().getServer();
        Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("it_hide_override");
        String packId = "file/it_hide_override";
        try {
            Files.createDirectories(pack.resolve("data/it_test/immersive_tanning/hide_sources"));
            Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\": {\"pack_format\": 15, \"description\": \"hide source override\"}}");
            Files.writeString(pack.resolve("data/it_test/immersive_tanning/hide_sources/frog.json"),
                    "{\"entities\": [\"minecraft:frog\"], \"size\": \"none\", \"priority\": 90}");
            Files.writeString(pack.resolve("data/it_test/immersive_tanning/hide_sources/broken.json"),
                    "{\"entities\": [\"minecraft:frog\"], \"size\": \"huge\", \"priority\": 95}");
            server.getPackRepository().reload();
            List<String> packs = new ArrayList<>(server.getPackRepository().getSelectedIds());
            packs.add(packId);
            reload(server, packs);
            h.assertTrue(HideSourceLoader.resolve(EntityType.FROG).kind() == HideSourceLoader.Kind.SUPPRESSED, "The pack's suppression wins after the reload");
            h.assertTrue(HideSourceLoader.invalid().size() == 1 && HideSourceLoader.invalid().get(0).startsWith("it_test:broken")
                    && HideSourceLoader.invalid().get(0).contains("\"huge\""), "The broken higher-priority file is reported by name and reason: " + HideSourceLoader.invalid());
            packs.remove(packId);
            reload(server, packs);
            h.assertTrue(sizeOf(EntityType.FROG) == HideSize.SMALL && HideSourceLoader.invalid().isEmpty(), "Without the pack the small source is back, nothing stale");
        } finally {
            List<String> packs = new ArrayList<>(server.getPackRepository().getSelectedIds());
            if (packs.remove(packId)) reload(server, packs);
            try (var files = Files.walk(pack)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (IOException ignored) {
                // nothing to clean
            }
            server.getPackRepository().reload();
        }
        h.succeed();
    }

    /** The report and inspect commands, through the real dispatcher, as an operator. */
    @GameTest(template = "empty")
    public static void reportAndInspectCommands(GameTestHelper h) {
        List<String> report = command(h, "immersive_tanning report");
        h.assertTrue(report.stream().anyMatch(l -> l.startsWith("Genetic Animals")), "The report has the Genetic Animals line: " + report);
        List<String> panda = command(h, "immersive_tanning inspect type minecraft:panda");
        h.assertTrue(panda.stream().anyMatch(l -> l.contains("test/panda_none") && l.contains("stops its hides"))
                && panda.stream().anyMatch(l -> l.contains("also matches, not used: immersive_tanning:medium")), "Inspect names the suppression and what it shadows: " + panda);
        List<String> zombie = command(h, "immersive_tanning inspect type minecraft:zombie");
        h.assertTrue(zombie.stream().anyMatch(l -> l.contains("none matches")), "Inspect tells no source apart: " + zombie);
        List<String> cow = command(h, "immersive_tanning inspect type minecraft:cow");
        h.assertTrue(cow.stream().anyMatch(l -> l.contains("immersive_tanning:large") && l.contains("large x1"))
                && cow.stream().anyMatch(l -> l.startsWith("Stage: its loot table")), "Inspect shows a vanilla cow's source and stage: " + cow);
        if (ModList.get().isLoaded(GeneticAnimals.MOD_ID)) {
            List<String> ga = command(h, "immersive_tanning inspect type eanimod:enhanced_cow");
            h.assertTrue(ga.stream().anyMatch(l -> l.contains("compat/eanimod/large")) && ga.stream().anyMatch(l -> l.startsWith("NATIVE:"))
                    && ga.stream().anyMatch(l -> l.startsWith("Stage: its death drops")) && ga.stream().anyMatch(l -> l.contains("0.11.12")),
                    "Inspect explains a Genetic Animals cow: " + ga);
        }
        h.succeed();
    }

    private static List<String> command(GameTestHelper h, String command) {
        List<String> lines = new ArrayList<>();
        net.minecraft.commands.CommandSource sink = new net.minecraft.commands.CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                lines.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        MinecraftServer server = h.getLevel().getServer();
        server.getCommands().performPrefixedCommand(new net.minecraft.commands.CommandSourceStack(sink, Vec3.ZERO, net.minecraft.world.phys.Vec2.ZERO,
                h.getLevel(), 4, "test", Component.literal("test"), server, null), command);
        return lines;
    }

    /** With Genetic Animals present, its tests; without it, the checks that the integration stays inert. */
    @GameTestGenerator
    public static Collection<TestFunction> profile() {
        List<TestFunction> tests = new ArrayList<>();
        if (!ModList.get().isLoaded(GeneticAnimals.MOD_ID)) {
            tests.add(test("absent_integration_is_inert", GeneticAnimalsGameTests::absentIntegrationIsInert));
            return tests;
        }
        tests.add(test("sources_and_hooks", GeneticAnimalsGameTests::sourcesAndHooks));
        tests.add(test("inventory_matches_the_running_release", GeneticAnimalsGameTests::inventoryMatchesTheRunningRelease));
        tests.add(test("native_matches_its_own_skin", GeneticAnimalsGameTests::nativeMatchesItsOwnSkin));
        tests.add(test("tanning_mode_gives_species_hides", GeneticAnimalsGameTests::tanningModeGivesSpeciesHides));
        tests.add(test("drop_switches", GeneticAnimalsGameTests::dropSwitches));
        tests.add(test("cargo_is_never_skin", GeneticAnimalsGameTests::cargoIsNeverSkin));
        tests.add(test("one_result_per_death", GeneticAnimalsGameTests::oneResultPerDeath));
        tests.add(test("disabled_and_loot_modifier", GeneticAnimalsGameTests::disabledAndLootModifier));
        tests.add(test("carcass_only_when_seen", GeneticAnimalsGameTests::carcassOnlyWhenSeen));
        tests.add(test("tack_keeps_its_data", GeneticAnimalsGameTests::tackKeepsItsData));
        tests.add(test("trades_deal_in_binding_leather", GeneticAnimalsGameTests::tradesDealInBindingLeather));
        tests.add(test("tack_at_the_bench", GeneticAnimalsGameTests::tackAtTheBench));
        return tests;
    }

    // ---- Genetic Animals absent ----------------------------------------------------------------------------------

    private static void absentIntegrationIsInert(GameTestHelper h) {
        h.assertTrue(!GeneticAnimals.isLoaded() && !GeneticAnimals.enabled() && GeneticAnimals.version() == null, "Not installed, not enabled");
        for (GeneticAnimalsHooks.Hook hook : GeneticAnimalsHooks.Hook.values()) {
            h.assertTrue(GeneticAnimalsHooks.state(hook) == GeneticAnimalsHooks.State.ABSENT, hook + " is absent, not failed");
        }
        h.assertTrue(HideSourceLoader.unknownEntities().contains("eanimod:enhanced_cow"), "Its hide sources load and only name uninstalled entities");
        h.assertTrue(TanningData.current().recipe(id("leatherworking/compat/eanimod/collar_basic_leather")) == null
                && TanningData.current().recipe(id("leatherworking/compat/eanimod/bridle_basic_leather")) == null, "Its bench recipes do not load");
        h.assertTrue(!GeneticAnimals.owns(EntityType.COW) && GeneticAnimals.summary().contains("not installed"), "Nothing is treated as its own");
        h.succeed();
    }

    // ---- Genetic Animals present ---------------------------------------------------------------------------------

    private static void sourcesAndHooks(GameTestHelper h) {
        Map<String, HideSize> expected = new LinkedHashMap<>();
        expected.put("enhanced_cow", HideSize.LARGE);
        expected.put("enhanced_mooshroom", HideSize.LARGE);
        expected.put("enhanced_moobloom", HideSize.LARGE);
        expected.put("enhanced_llama", HideSize.LARGE);
        expected.put("enhanced_sheep", HideSize.MEDIUM);
        expected.put("enhanced_pig", HideSize.MEDIUM);
        expected.put("enhanced_rabbit", HideSize.SMALL);
        expected.forEach((path, size) -> {
            HideSourceLoader.Decision decision = HideSourceLoader.resolve(ga(path));
            h.assertTrue(decision.dropsHide() && decision.source().size() == size && decision.source().id().getPath().startsWith("compat/eanimod/")
                    && decision.source().matchedBy(ga(path)).equals(List.of("id")), path + " resolves to its own " + size + " source by id, got " + decision);
            h.assertTrue(GeneticAnimals.owns(ga(path)), path + " is decided at death");
        });
        h.assertTrue(HideSourceLoader.matching(ga("enhanced_cow")).size() == 2, "The cow also matches #forge:cows, shadowed by its own source");
        for (String path : List.of("enhanced_chicken", "enhanced_turtle", "enhanced_axolotl", "enhanced_horse")) {
            h.assertTrue(HideSourceLoader.resolve(ga(path)).kind() == HideSourceLoader.Kind.SUPPRESSED, path + " drops no hide");
        }
        for (String path : List.of("enhanced_entity_egg", "enhanced_axolotl_egg", "enhanced_entity_llama_spit")) {
            h.assertTrue(HideSourceLoader.resolve(ga(path)).kind() == HideSourceLoader.Kind.NO_SOURCE, path + " is no animal");
        }
        h.assertTrue(HideSourceLoader.resolve(EntityType.SHEEP).source().id().equals(id("medium"))
                && HideSourceLoader.resolve(EntityType.LLAMA).source().id().equals(id("large")), "Vanilla sheep and llamas keep the generic sources");
        h.assertTrue(GeneticAnimals.verified(), "The pinned release is the verified one, got " + GeneticAnimals.version());
        h.assertTrue(GeneticAnimals.hooksApplied(), "Every hook into its classes applied: " + GeneticAnimals.hookStates());
        h.succeed();
    }

    /**
     * The recorded inventory (compat/genetic-animals-0.11.12.json, also the source of docs/compat/genetic-animals.md)
     * against the registries of the release actually loaded: every entity and tack item it lists exists, nothing it
     * leaves out is registered, tack is unstackable, undamageable and dyeable, and its leather recipes are routed.
     */
    private static void inventoryMatchesTheRunningRelease(GameTestHelper h) {
        com.google.gson.JsonObject inventory;
        try (var in = GeneticAnimalsGameTests.class.getResourceAsStream("/compat/genetic-animals-0.11.12.json")) {
            if (in == null) throw new IllegalStateException("inventory resource missing");
            inventory = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        h.assertTrue(inventory.getAsJsonObject("mod").get("version").getAsString().equals(GeneticAnimals.version()), "The inventory is of the loaded release");
        java.util.Set<String> listed = inventory.getAsJsonObject("entities").keySet();
        java.util.Set<String> registered = new java.util.TreeSet<>();
        ForgeRegistries.ENTITY_TYPES.getKeys().stream().filter(k -> k.getNamespace().equals(GeneticAnimals.MOD_ID)).forEach(k -> registered.add(k.toString()));
        h.assertTrue(registered.equals(new java.util.TreeSet<>(listed)), "Every registered entity is listed and no other: " + registered + " vs " + listed);
        java.util.Set<String> tack = new java.util.TreeSet<>();
        for (var element : inventory.getAsJsonArray("tack")) {
            String id = element.getAsJsonObject().get("id").getAsString();
            tack.add(id);
            Item item = gaItem(id.substring(GeneticAnimals.MOD_ID.length() + 1));
            ItemStack stack = new ItemStack(item);
            h.assertTrue(stack.getMaxStackSize() == 1 && !stack.isDamageableItem() && item instanceof DyeableLeatherItem, id + " is unstackable, undamageable, dyeable");
        }
        java.util.Set<String> registeredTack = new java.util.TreeSet<>();
        ForgeRegistries.ITEMS.getKeys().stream().filter(k -> k.getNamespace().equals(GeneticAnimals.MOD_ID)
                && (k.getPath().startsWith("saddle_") || k.getPath().startsWith("bridle_") || k.getPath().startsWith("collar_"))).forEach(k -> registeredTack.add(k.toString()));
        h.assertTrue(registeredTack.equals(tack), "Every registered tack item is listed: " + registeredTack.size() + " registered, " + tack.size() + " listed");
        for (String id : inventory.getAsJsonObject("tack_recipes").keySet()) {
            ResourceLocation key = new ResourceLocation(id);
            boolean leather = id.contains("leather");
            boolean routed = TanningData.current().suppressedRecipes().containsKey(key);
            h.assertTrue(leather ? routed : h.getLevel().getServer().getRecipeManager().byKey(key).isPresent(),
                    id + (leather ? " is routed through the bench" : " keeps its crafting recipe"));
        }
        inventory.getAsJsonObject("entity_tags").entrySet().forEach(e -> {
            var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, new ResourceLocation(e.getKey().substring(1)));
            for (var value : e.getValue().getAsJsonArray()) {
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(value.getAsString()));
                h.assertTrue(type != null && type.is(tag), value.getAsString() + " is in " + e.getKey());
            }
        });
        h.succeed();
    }

    /**
     * Pairs of identical animals (one saved animal loaded twice, same random seed): one killed with the integration
     * off, one with it on. The second must drop exactly the hides the first one's skin is worth, and everything else
     * the same. Burning, Looting and every species are covered; no result is assumed.
     */
    private static void nativeMatchesItsOwnSkin(GameTestHelper h) {
        Map<String, int[]> skinSeen = new LinkedHashMap<>();
        ItemStack looting = new ItemStack(Items.IRON_SWORD);
        looting.enchant(Enchantments.MOB_LOOTING, 3);
        for (String path : List.of("enhanced_cow", "enhanced_mooshroom", "enhanced_moobloom", "enhanced_llama", "enhanced_sheep", "enhanced_pig", "enhanced_rabbit")) {
            HideSize cap = HideSourceLoader.resolve(ga(path)).source().size();
            int[] seen = new int[2];
            for (int animal = 0; animal < 3; animal++) {
                CompoundTag saved = adultSaved(h, path);
                for (int trial = 0; trial < 6; trial++) {
                    long seed = 1000L * animal + trial;
                    boolean burning = trial == 4;
                    ItemStack weapon = trial == 5 ? looting : ItemStack.EMPTY;
                    List<ItemStack> off = withEnabled(false, () -> kill(h, copy(h, path, saved, burning), weapon, seed));
                    List<ItemStack> on = kill(h, copy(h, path, saved, burning), weapon, seed);
                    int leather = count(off, Items.LEATHER) + count(off, ModItems.BINDING_LEATHER.get());
                    int rabbitHides = count(off, Items.RABBIT_HIDE);
                    int[] want = GeneticAnimalsDrops.decompose(leather, cap);
                    want[0] += rabbitHides;
                    String where = path + " #" + animal + " trial " + trial + (burning ? " (burning)" : "") + ": off " + off + ", on " + on;
                    h.assertTrue(count(on, HideSize.SMALL) == want[0] && count(on, HideSize.MEDIUM) == want[1] && count(on, HideSize.LARGE) == want[2],
                            "Hides match the skin it dropped, " + where);
                    h.assertTrue(count(on, Items.LEATHER) + count(on, ModItems.BINDING_LEATHER.get()) + count(on, Items.RABBIT_HIDE) == 0,
                            "Its skin is replaced, " + where);
                    h.assertTrue(others(off).equals(others(on)), "Meat, wool and everything else are the same, " + where);
                    seen[0] += leather + rabbitHides;
                    seen[1] += want[0] + want[1] + want[2];
                    if (path.equals("enhanced_pig")) h.assertTrue(want[0] + want[1] + want[2] == 0, "Its pigs drop no skin of their own, " + where);
                    if (burning) h.assertTrue(leather == 0, "A burning animal drops no leather, " + where);
                }
            }
            skinSeen.put(path, seen);
        }
        for (String path : List.of("enhanced_cow", "enhanced_mooshroom", "enhanced_moobloom", "enhanced_rabbit")) {
            h.assertTrue(skinSeen.get(path)[1] > 0, path + " gave some hide in 18 adult kills (not every kill), saw " + java.util.Arrays.toString(skinSeen.get(path)));
        }
        ImmersiveTanning.LOGGER.info("Genetic Animals native pairs (skin units, hides): {}", skinSeen.entrySet().stream()
                .map(e -> e.getKey() + "=" + java.util.Arrays.toString(e.getValue())).toList());
        h.succeed();
    }

    private static void tanningModeGivesSpeciesHides(GameTestHelper h) {
        withMode(HideMode.TANNING, () -> {
            List<ItemStack> pig = kill(h, copy(h, "enhanced_pig", adultSaved(h, "enhanced_pig"), false), ItemStack.EMPTY, 3);
            h.assertTrue(count(pig, HideSize.MEDIUM) == 1 && hides(pig) == 1 && (count(pig, Items.PORKCHOP) > 0 || count(pig, Items.COOKED_PORKCHOP) > 0 || pig.size() == 1),
                    "An adult pig drops one medium hide next to its pork, got " + pig);
            CompoundTag piglet = adultSaved(h, "enhanced_pig");
            piglet.putString("BirthTime", String.valueOf(h.getLevel().getGameTime() - CALF));
            List<ItemStack> young = kill(h, copy(h, "enhanced_pig", piglet, false), ItemStack.EMPTY, 3);
            h.assertTrue(hides(young) == 0, "A piglet drops no hide, got " + young);
            List<ItemStack> burning = kill(h, copy(h, "enhanced_cow", adultSaved(h, "enhanced_cow"), true), ItemStack.EMPTY, 3);
            h.assertTrue(count(burning, HideSize.LARGE) == 1 && hides(burning) == 1 && count(burning, Items.BEEF) == 0,
                    "A burning cow still drops its large hide and keeps its cooked beef, got " + burning);
            List<ItemStack> rabbit = kill(h, copy(h, "enhanced_rabbit", adultSaved(h, "enhanced_rabbit"), false), ItemStack.EMPTY, 3);
            h.assertTrue(count(rabbit, HideSize.SMALL) == 1 && hides(rabbit) == 1 && count(rabbit, Items.RABBIT_HIDE) == 0, "A rabbit drops one small hide, got " + rabbit);
            ItemStack looting = new ItemStack(Items.IRON_SWORD);
            looting.enchant(Enchantments.MOB_LOOTING, 3);
            boolean adds = ServerConfig.LOOTING_ADDS_HIDES.get();
            try {
                ServerConfig.LOOTING_ADDS_HIDES.set(true);
                CompoundTag saved = adultSaved(h, "enhanced_pig");
                for (int seed = 0; seed < 8; seed++) {
                    List<ItemStack> looted = kill(h, copy(h, "enhanced_pig", saved, false), looting, seed);
                    h.assertTrue(hides(looted) >= 1 && hides(looted) <= 4, "Looting III adds at most three hides, once, got " + looted);
                }
            } finally {
                ServerConfig.LOOTING_ADDS_HIDES.set(adds);
            }
        });
        h.succeed();
    }

    /** All four combinations of replaceLeatherDrops and replaceLeatherLoot on a cow that drops leather. */
    private static void dropSwitches(GameTestHelper h) {
        Kill kill = leatherKill(h, "enhanced_cow");
        boolean drops = ServerConfig.REPLACE_LEATHER_DROPS.get();
        boolean loot = ServerConfig.REPLACE_LEATHER_LOOT.get();
        try {
            for (boolean replaceDrops : new boolean[]{true, false}) {
                for (boolean replaceLoot : new boolean[]{true, false}) {
                    ServerConfig.REPLACE_LEATHER_DROPS.set(replaceDrops);
                    ServerConfig.REPLACE_LEATHER_LOOT.set(replaceLoot);
                    List<ItemStack> got = kill(h, copy(h, "enhanced_cow", kill.saved, false), ItemStack.EMPTY, kill.seed);
                    int leather = count(got, Items.LEATHER);
                    int binding = count(got, ModItems.BINDING_LEATHER.get());
                    String where = "replaceLeatherDrops " + replaceDrops + ", replaceLeatherLoot " + replaceLoot + ": " + got;
                    h.assertTrue(hides(got) > 0, "Hides drop either way, " + where);
                    if (replaceDrops) h.assertTrue(leather + binding == 0, "The skin is replaced, " + where);
                    else if (replaceLoot) h.assertTrue(binding == kill.leather && leather == 0, "The skin stays, as Binding Leather, " + where);
                    else h.assertTrue(leather == kill.leather && binding == 0, "The skin stays as leather, " + where);
                }
            }
        } finally {
            ServerConfig.REPLACE_LEATHER_DROPS.set(drops);
            ServerConfig.REPLACE_LEATHER_LOOT.set(loot);
        }
        h.succeed();
    }

    /** A cow carrying exactly what its death drops, and more: the cargo comes back untouched, the skin becomes hides. */
    private static void cargoIsNeverSkin(GameTestHelper h) {
        Kill kill = leatherKill(h, "enhanced_cow");
        LivingEntity cow = copy(h, "enhanced_cow", kill.saved, false);
        IItemHandler inventory = cow.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(() -> new IllegalStateException("no inventory"));
        inventory.insertItem(0, new ItemStack(Items.CHEST), false);
        ItemStack leather = new ItemStack(Items.LEATHER, 7);
        leather.setHoverName(Component.literal("Saddlebag leather"));
        ItemStack trophy = ModItems.rawHide(HideSize.LARGE).create(null).copyWithCount(2);
        ItemStack supple = new ItemStack(ModItems.leather(Finish.SUPPLE), 4);
        ItemStack saddle = new ItemStack(Items.SADDLE);
        new LeatherworkData(Finish.HARNESS, 80, 90, null, "Cargo Test").apply(saddle);
        saddle.setHoverName(Component.literal("Spare"));
        List<ItemStack> cargo = List.of(leather, new ItemStack(Items.RABBIT_HIDE, 3), trophy, new ItemStack(ModItems.BINDING_LEATHER.get(), 5), supple, saddle);
        for (int i = 0; i < cargo.size(); i++) {
            h.assertTrue(inventory.insertItem(7 + i, cargo.get(i).copy(), false).isEmpty(), "Cargo slot " + (7 + i) + " takes " + cargo.get(i));
        }
        List<ItemStack> got = kill(h, cow, ItemStack.EMPTY, kill.seed);
        for (ItemStack carried : cargo) {
            h.assertTrue(got.stream().anyMatch(s -> ItemStack.matches(s, carried)), "Carried " + carried + " " + carried.getTag() + " comes back exactly, got " + got);
        }
        h.assertTrue(got.stream().anyMatch(s -> s.is(Items.CHEST)), "The chest itself drops");
        int[] want = GeneticAnimalsDrops.decompose(kill.leather, HideSize.LARGE);
        h.assertTrue(count(got, HideSize.LARGE) == want[2] + 2 && count(got, HideSize.MEDIUM) == want[1] && count(got, HideSize.SMALL) == want[0],
                "The carried raw hides do not stop the cow's own hides (" + kill.leather + " leather), got " + got);
        h.assertTrue(count(got, Items.LEATHER) == 7 && count(got, ModItems.BINDING_LEATHER.get()) == 5 && count(got, Items.RABBIT_HIDE) == 3,
                "Only the carried leather, Binding Leather and rabbit hides remain, unconverted");
        h.succeed();
    }

    /** One result per death: handled twice, a hide taken by another mod, cancelled drops, and loot switched off. */
    private static void oneResultPerDeath(GameTestHelper h) {
        Kill kill = leatherKill(h, "enhanced_cow");
        int expected = hides(kill.on);

        Consumer<LivingDropsEvent> again = event -> com.otectus.immersivetanning.loot.HideDrops.onLivingDrops(event);
        List<ItemStack> twice = withListener(EventPriority.LOWEST, again, () -> kill(h, copy(h, "enhanced_cow", kill.saved, false), ItemStack.EMPTY, kill.seed));
        h.assertTrue(hides(twice) == expected, "Handling the drops a second time adds nothing, got " + twice);

        Consumer<LivingDropsEvent> thief = event -> event.getDrops().removeIf(e -> e.getItem().is(ModTags.RAW_HIDES));
        List<ItemStack> stolen = withListener(EventPriority.LOWEST, thief, () -> kill(h, copy(h, "enhanced_cow", kill.saved, false), ItemStack.EMPTY, kill.seed));
        h.assertTrue(hides(stolen) == 0, "A hide another mod removes is not made again, got " + stolen);

        Consumer<LivingDropsEvent> cancel = event -> event.setCanceled(true);
        List<ItemStack> cancelled = withListener(EventPriority.HIGH, cancel, () -> kill(h, copy(h, "enhanced_cow", kill.saved, false), ItemStack.EMPTY, kill.seed));
        h.assertTrue(cancelled.isEmpty(), "Cancelled drops spawn nothing, ours included, got " + cancelled);

        GameRules.BooleanValue mobLoot = h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT);
        try {
            mobLoot.set(false, h.getLevel().getServer());
            for (HideMode mode : HideMode.values()) {
                withMode(mode, () -> {
                    LivingEntity cow = copy(h, "enhanced_cow", kill.saved, false);
                    IItemHandler inventory = cow.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(() -> new IllegalStateException("no inventory"));
                    inventory.insertItem(0, new ItemStack(Items.CHEST), false);
                    inventory.insertItem(8, new ItemStack(Items.LEATHER, 2), false);
                    List<ItemStack> got = kill(h, cow, ItemStack.EMPTY, kill.seed);
                    h.assertTrue(hides(got) == 0 && count(got, Items.LEATHER) == 2 && count(got, Items.BEEF) == 0,
                            "doMobLoot off (" + mode + "): no loot and no hide, the cargo still drops, got " + got);
                });
            }
        } finally {
            mobLoot.set(true, h.getLevel().getServer());
        }
        h.succeed();
    }

    private static void disabledAndLootModifier(GameTestHelper h) {
        Kill kill = leatherKill(h, "enhanced_cow");
        List<ItemStack> off = withEnabled(false, () -> kill(h, copy(h, "enhanced_cow", kill.saved, false), ItemStack.EMPTY, kill.seed));
        h.assertTrue(hides(off) == 0 && count(off, ModItems.BINDING_LEATHER.get()) == kill.leather && count(off, Items.LEATHER) == 0,
                "Integration off: no hide, its leather becomes Binding Leather as everywhere, got " + off);
        withMode(HideMode.TANNING, () -> {
            List<ItemStack> pig = withEnabled(false, () -> kill(h, copy(h, "enhanced_pig", adultSaved(h, "enhanced_pig"), false), ItemStack.EMPTY, 1));
            h.assertTrue(hides(pig) == 0, "Integration off: the generic #forge:pigs alias does not give its pigs a hide, got " + pig);
        });

        LivingEntity cow = copy(h, "enhanced_cow", kill.saved, false);
        HideDropModifier modifier = new HideDropModifier(new LootItemCondition[0]);
        ObjectArrayList<ItemStack> loot = new ObjectArrayList<>(List.of(new ItemStack(Items.LEATHER, 2)));
        modifier.apply(loot, entityLoot(h, cow));
        h.assertTrue(loot.size() == 1 && loot.get(0).is(Items.LEATHER), "The loot modifier never decides its hides, got " + loot);
        LivingEntity vanilla = EntityType.COW.create(h.getLevel());
        vanilla.setPos(cow.position());
        ObjectArrayList<ItemStack> vanillaLoot = new ObjectArrayList<>(List.of(new ItemStack(Items.LEATHER, 2)));
        modifier.apply(vanillaLoot, entityLoot(h, vanilla));
        h.assertTrue(vanillaLoot.stream().anyMatch(s -> s.is(ModItems.rawHide(HideSize.LARGE))), "A vanilla cow's table still gets its hide there");
        cow.discard();
        h.succeed();
    }

    /** Butchery turns GA sheep (in #forge:sheep) into carcasses; only a carcass that really appears stops the hide. */
    private static void carcassOnlyWhenSeen(GameTestHelper h) {
        withMode(HideMode.TANNING, () -> {
            CompoundTag saved = adultSaved(h, "enhanced_sheep");
            ItemStack cleaver = new ItemStack(Items.GOLDEN_HOE);
            List<ItemStack> noCarcass = kill(h, copy(h, "enhanced_sheep", saved, false), cleaver, 5);
            h.assertTrue(count(noCarcass, HideSize.MEDIUM) == 1, "A carcass tool with no carcass mod answering keeps the hide, got " + noCarcass);

            LivingEntity sheep = copy(h, "enhanced_sheep", saved, false);
            ItemEntity carcass = new ItemEntity(h.getLevel(), sheep.getX(), sheep.getY(), sheep.getZ(), new ItemStack(Items.BONE_BLOCK));
            h.getLevel().addFreshEntity(carcass);
            List<ItemStack> butchered = kill(h, sheep, cleaver, 5);
            h.assertTrue(hides(butchered) == 0 && count(butchered, Items.LEATHER) + count(butchered, ModItems.BINDING_LEATHER.get()) == 0,
                    "A carcass that appears at the death takes the skin; no hide, got " + butchered);
        });
        h.succeed();
    }

    /** Bench-made tack put on by hand, swapped, saved and dropped keeps every bit of its data. */
    private static void tackKeepsItsData(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        LivingEntity cow = copy(h, "enhanced_cow", adultSaved(h, "enhanced_cow"), false);
        IItemHandler inventory = cow.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(() -> new IllegalStateException("no inventory"));

        ItemStack bridle = made(gaItem("bridle_basic_leather"), "Sunday Best", 0x336699, player);
        ItemStack bridleCopy = bridle.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, bridle);
        player.interactOn(cow, InteractionHand.MAIN_HAND);
        h.assertTrue(ItemStack.matches(inventory.getStackInSlot(3), bridleCopy), "The bridle on the cow is the one used, got " + inventory.getStackInSlot(3).getTag());
        h.assertTrue(player.getMainHandItem().isEmpty(), "One bridle was used");

        ItemStack second = made(gaItem("bridle_basic_leather"), "Workday", 0x993322, player);
        ItemStack secondCopy = second.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, second);
        player.interactOn(cow, InteractionHand.MAIN_HAND);
        h.assertTrue(ItemStack.matches(inventory.getStackInSlot(3), secondCopy) && ItemStack.matches(player.getMainHandItem(), bridleCopy),
                "Swapping puts the new bridle on and gives the old one back, once");

        ItemStack saddle = made(Items.SADDLE, "Bench Saddle", -1, player);
        ItemStack saddleCopy = saddle.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, saddle);
        player.interactOn(cow, InteractionHand.MAIN_HAND);
        h.assertTrue(ItemStack.matches(inventory.getStackInSlot(1), saddleCopy), "A bench-made vanilla saddle keeps its data on the cow, got " + inventory.getStackInSlot(1).getTag());

        ItemStack western = made(gaItem("saddle_basicpomel_leather"), "Rodeo", 0x224488, player);
        ItemStack westernCopy = western.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, western);
        player.interactOn(cow, InteractionHand.MAIN_HAND);
        h.assertTrue(ItemStack.matches(inventory.getStackInSlot(1), westernCopy) && ItemStack.matches(player.getMainHandItem(), saddleCopy),
                "Its own saddle keeps its data too, and the vanilla saddle comes back whole");

        CompoundTag saved = cow.saveWithoutId(new CompoundTag());
        saved.remove("UUID");
        LivingEntity reloaded = copy(h, "enhanced_cow", saved, false);
        IItemHandler reloadedInventory = reloaded.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(() -> new IllegalStateException("no inventory"));
        h.assertTrue(ItemStack.matches(reloadedInventory.getStackInSlot(3), secondCopy) && ItemStack.matches(reloadedInventory.getStackInSlot(1), westernCopy),
                "Saved and loaded, the tack is unchanged");
        List<ItemStack> dropped = kill(h, reloaded, ItemStack.EMPTY, 1);
        h.assertTrue(dropped.stream().anyMatch(s -> ItemStack.matches(s, secondCopy)) && dropped.stream().anyMatch(s -> ItemStack.matches(s, westernCopy)),
                "At death the tack drops exactly as it was, got " + dropped);
        cow.discard();

        LivingEntity plain = copy(h, "enhanced_cow", adultSaved(h, "enhanced_cow"), false);
        IItemHandler plainInventory = plain.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(() -> new IllegalStateException("no inventory"));
        withEnabled(false, () -> {
            player.setItemInHand(InteractionHand.MAIN_HAND, made(gaItem("bridle_basic_leather"), "Lost", 0x112233, player));
            player.interactOn(plain, InteractionHand.MAIN_HAND);
            return null;
        });
        h.assertTrue(!LeatherworkData.has(plainInventory.getStackInSlot(3)) && plainInventory.getStackInSlot(3).is(gaItem("bridle_basic_leather")),
                "With the integration off Genetic Animals rebuilds the bridle and its data is lost (the behaviour being fixed)");
        plain.discard();
        h.succeed();
    }

    /** Its offer factories (called as Genetic Animals calls them) and offers it saved earlier. */
    private static void tradesDealInBindingLeather(GameTestHelper h) {
        try {
            Class<?> trades = Class.forName("mokiyoki.enhancedanimals.util.EanimodVillagerTrades");
            trades.getConstructor().newInstance();
            Method villager = trades.getMethod("getEanimodTrade", VillagerProfession.class, int.class);
            Method wandering = trades.getMethod("getWanderingEanimodTrade");
            int binding = 0;
            for (int i = 0; i < 300; i++) {
                for (int level = 1; level <= 5; level++) {
                    MerchantOffer offer = (MerchantOffer) villager.invoke(null, VillagerProfession.LEATHERWORKER, level);
                    h.assertTrue(!hasLeather(offer), "A new leatherworker offer asks for no vanilla leather: " + describe(offer));
                    if (offer.getBaseCostA().is(ModItems.BINDING_LEATHER.get())) binding++;
                }
                MerchantOffer offer = (MerchantOffer) wandering.invoke(null);
                h.assertTrue(!hasLeather(offer), "A new wandering offer has no vanilla leather: " + describe(offer));
            }
            h.assertTrue(binding > 0, "Some leatherworker offers ask for Binding Leather in 1500 offers");
            boolean replace = ServerConfig.REPLACE_LEATHER_IN_TRADES.get();
            int vanilla = 0;
            try {
                ServerConfig.REPLACE_LEATHER_IN_TRADES.set(false);
                for (int i = 0; i < 300; i++) if (hasLeather((MerchantOffer) villager.invoke(null, VillagerProfession.LEATHERWORKER, 1 + i % 5))) vanilla++;
            } finally {
                ServerConfig.REPLACE_LEATHER_IN_TRADES.set(replace);
            }
            h.assertTrue(vanilla > 0, "With replaceLeatherInTrades off its offers keep vanilla leather (the hook is what converts them)");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Genetic Animals' trade factory: " + e, e);
        }

        FakePlayer player = TestSupport.player(h);
        Villager trader = EntityType.VILLAGER.create(h.getLevel());
        trader.setVillagerData(trader.getVillagerData().setProfession(VillagerProfession.LEATHERWORKER));
        MerchantOffers offers = trader.getOffers();
        ItemStack collar = new ItemStack(gaItem("collar_basic_cloth"));
        ((DyeableLeatherItem) collar.getItem()).setColor(collar, 0x55AA22);
        MerchantOffer saved = offer(new ItemStack(Items.LEATHER, 2), ItemStack.EMPTY, collar, 4, 11, 9, 0.2F, 3, -1, false);
        MerchantOffer foreign = offer(new ItemStack(Items.LEATHER, 6), ItemStack.EMPTY, new ItemStack(Items.EMERALD), 0, 16, 2, 0.05F, 0, 0, true);
        offers.add(saved);
        offers.add(foreign);
        MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Open(player, new MerchantMenu(9, player.getInventory(), trader)));
        MerchantOffer migrated = offers.get(offers.size() - 2);
        h.assertTrue(migrated.getBaseCostA().is(ModItems.BINDING_LEATHER.get()) && migrated.getBaseCostA().getCount() == 2 && ItemStack.matches(migrated.getResult(), collar)
                && migrated.getUses() == 4 && migrated.getMaxUses() == 11 && migrated.getXp() == 9 && migrated.getDemand() == 3
                && migrated.getSpecialPriceDiff() == -1 && !migrated.shouldRewardExp(), "An offer it saved earlier converts when the trades open, state intact");
        h.assertTrue(offers.get(offers.size() - 1) == foreign && foreign.getBaseCostA().is(Items.LEATHER), "Other mods' saved offers are left alone");
        h.assertTrue(GeneticAnimalsTrades.normalize(offers) == 0, "Opening the trades again changes nothing");
        trader.discard();
        h.succeed();
    }

    private static void tackAtTheBench(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Item collar = gaItem("collar_basic_leather");
        Item bridle = gaItem("bridle_basic_leather");
        LeatherworkingRecipe collarRecipe = TanningData.current().recipe(id("leatherworking/compat/eanimod/collar_basic_leather"));
        LeatherworkingRecipe bridleRecipe = TanningData.current().recipe(id("leatherworking/compat/eanimod/bridle_basic_leather"));
        h.assertTrue(collarRecipe != null && collarRecipe.leather() == 1 && collarRecipe.auxiliary().isEmpty() && collarRecipe.seam().equals(id("strap")),
                "The collar takes one leather on the strap seam");
        h.assertTrue(bridleRecipe != null && bridleRecipe.leather() == 4 && bridleRecipe.auxiliary().size() == 1
                && bridleRecipe.auxiliary().get(0).count() == 2 && bridleRecipe.auxiliary().get(0).ingredient().test(new ItemStack(Items.IRON_NUGGET)),
                "The bridle takes four leather and two iron nuggets");
        h.assertTrue(TanningData.current().recipesProducing(collar).size() == 1 && TanningData.current().recipesProducing(bridle).size() == 1,
                "One bench recipe each, nothing detected twice");
        h.assertTrue(server.getRecipeManager().byKey(new ResourceLocation("eanimod", "collar_basic_leather")).isEmpty()
                && server.getRecipeManager().byKey(new ResourceLocation("eanimod", "bridle_basic_leather")).isEmpty(), "Their crafting recipes are routed to the bench");
        h.assertTrue(server.getRecipeManager().byKey(new ResourceLocation("eanimod", "collar_basic_cloth")).isPresent()
                && server.getRecipeManager().byKey(new ResourceLocation("eanimod", "bridle_basic_cloth")).isPresent(), "Cloth tack keeps its own recipes");
        for (String id : List.of("saddle_basic_leather", "saddle_english_leather", "collar_basic_leather_goldbell", "bridle_basic_leather_gold")) {
            h.assertTrue(TanningData.current().recipesProducing(gaItem(id)).isEmpty(), id + " stays with the trades it comes from");
        }

        boolean tack = ServerConfig.GENETIC_ANIMALS_TACK_AT_BENCH.get();
        boolean replace = ServerConfig.REPLACE_LEATHER_EQUIPMENT_RECIPES.get();
        List<? extends String> blacklist = ServerConfig.MOD_BLACKLIST.get();
        try {
            ServerConfig.GENETIC_ANIMALS_TACK_AT_BENCH.set(false);
            TanningData.rebuild(server);
            h.assertTrue(TanningData.current().recipesProducing(collar).isEmpty() && server.getRecipeManager().byKey(new ResourceLocation("eanimod", "collar_basic_leather")).isPresent(),
                    "leatherTackAtBench off: no bench recipe and the crafting recipe is back");
            ServerConfig.GENETIC_ANIMALS_TACK_AT_BENCH.set(true);
            ServerConfig.REPLACE_LEATHER_EQUIPMENT_RECIPES.set(false);
            TanningData.rebuild(server);
            h.assertTrue(TanningData.current().recipesProducing(collar).size() == 1 && server.getRecipeManager().byKey(new ResourceLocation("eanimod", "collar_basic_leather")).isPresent(),
                    "replaceLeatherEquipmentRecipes off: both recipes");
            ServerConfig.REPLACE_LEATHER_EQUIPMENT_RECIPES.set(true);
            ServerConfig.MOD_BLACKLIST.set(List.of("eanimod"));
            TanningData.rebuild(server);
            h.assertTrue(TanningData.current().recipesProducing(bridle).isEmpty() && server.getRecipeManager().byKey(new ResourceLocation("eanimod", "bridle_basic_leather")).isPresent(),
                    "modBlacklist eanimod: crafting only");
        } finally {
            ServerConfig.GENETIC_ANIMALS_TACK_AT_BENCH.set(tack);
            ServerConfig.REPLACE_LEATHER_EQUIPMENT_RECIPES.set(replace);
            ServerConfig.MOD_BLACKLIST.set(blacklist);
            TanningData.rebuild(server);
        }
        h.assertTrue(TanningData.current().recipesProducing(collar).size() == 1, "Rebuilt back, still one collar recipe");
        h.succeed();
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private record Kill(CompoundTag saved, long seed, int leather, List<ItemStack> on) {}

    /** An adult cow (or other animal) and a seed with which it drops leather, and its hides with the integration on. */
    private static Kill leatherKill(GameTestHelper h, String path) {
        for (int animal = 0; animal < 8; animal++) {
            CompoundTag saved = adultSaved(h, path);
            for (long n = 0; n < 8; n++) {
                long seed = seedOf(n);
                List<ItemStack> off = withEnabled(false, () -> kill(h, copy(h, path, saved, false), ItemStack.EMPTY, seed));
                int leather = count(off, ModItems.BINDING_LEATHER.get()) + count(off, Items.LEATHER);
                if (leather > 0) {
                    List<ItemStack> on = kill(h, copy(h, path, saved, false), ItemStack.EMPTY, seed);
                    return new Kill(saved, seed, leather, on);
                }
            }
        }
        throw new IllegalStateException("No " + path + " dropped leather in 64 adult kills");
    }

    private static long seedOf(long n) {
        return 7919L * n + 17L;
    }

    /** An adult spawned through Genetic Animals' own spawn path (its genes set up), saved and removed. */
    private static CompoundTag adultSaved(GameTestHelper h, String path) {
        Entity spawned = ga(path).spawn(h.getLevel(), h.absolutePos(SPOT), MobSpawnType.SPAWN_EGG);
        if (spawned == null) throw new IllegalStateException("Could not spawn " + path);
        CompoundTag saved = spawned.saveWithoutId(new CompoundTag());
        spawned.discard();
        saved.remove("UUID");
        saved.putString("BirthTime", String.valueOf(h.getLevel().getGameTime() - ADULT));
        return saved;
    }

    /**
     * The saved animal, loaded into a fresh entity at the test spot. Genetic Animals makes a new animal immune to harm
     * for its first 100 ticks (its public {@code invulnerable} counter); that is set to 0, as if it had lived that long.
     */
    private static LivingEntity copy(GameTestHelper h, String path, CompoundTag saved, boolean burning) {
        Entity entity = ga(path).create(h.getLevel());
        if (entity == null) throw new IllegalStateException("Could not create " + path);
        entity.load(saved.copy());
        Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(SPOT));
        entity.moveTo(at.x, at.y, at.z, 0, 0);
        h.getLevel().addFreshEntity(entity);
        try {
            Class.forName("mokiyoki.enhancedanimals.entity.EnhancedAnimalAbstract").getField("invulnerable").setInt(entity, 0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Genetic Animals' spawn protection field: " + e, e);
        }
        if (burning) entity.setRemainingFireTicks(200);
        return (LivingEntity) entity;
    }

    /** Kills with the weapon after seeding the animal's random, and returns (and clears away) what dropped. */
    private static List<ItemStack> kill(GameTestHelper h, LivingEntity victim, ItemStack weapon, long seed) {
        FakePlayer player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon.copy());
        victim.getRandom().setSeed(seed);
        victim.hurt(h.getLevel().damageSources().playerAttack(player), 100_000F);
        if (!victim.isDeadOrDying()) throw new IllegalStateException(EntityType.getKey(victim.getType()) + " survived");
        List<ItemStack> out = new ArrayList<>();
        for (ItemEntity drop : h.getLevel().getEntitiesOfClass(ItemEntity.class, victim.getBoundingBox().inflate(3.0))) {
            out.add(drop.getItem().copy());
            drop.discard();
        }
        victim.discard();
        return out;
    }

    private static <T> T withEnabled(boolean enabled, java.util.function.Supplier<T> body) {
        boolean was = ServerConfig.GENETIC_ANIMALS_ENABLED.get();
        try {
            ServerConfig.GENETIC_ANIMALS_ENABLED.set(enabled);
            return body.get();
        } finally {
            ServerConfig.GENETIC_ANIMALS_ENABLED.set(was);
        }
    }

    private static void withMode(HideMode mode, Runnable body) {
        HideMode was = ServerConfig.GENETIC_ANIMALS_HIDE_MODE.get();
        try {
            ServerConfig.GENETIC_ANIMALS_HIDE_MODE.set(mode);
            body.run();
        } finally {
            ServerConfig.GENETIC_ANIMALS_HIDE_MODE.set(was);
        }
    }

    private static <T> T withListener(EventPriority priority, Consumer<LivingDropsEvent> listener, java.util.function.Supplier<T> body) {
        MinecraftForge.EVENT_BUS.addListener(priority, false, LivingDropsEvent.class, listener);
        try {
            return body.get();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
        }
    }

    private static LootContext entityLoot(GameTestHelper h, LivingEntity entity) {
        LootParams params = new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.ORIGIN, entity.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, h.getLevel().damageSources().generic()).create(LootContextParamSets.ENTITY);
        LootContext context = new LootContext.Builder(params).create(null);
        context.setQueriedLootTableId(entity.getLootTable());
        return context;
    }

    private static ItemStack made(Item item, String name, int color, FakePlayer player) {
        ItemStack stack = new ItemStack(item);
        new LeatherworkData(Finish.HARNESS, 70, 90, null, player.getName().getString()).apply(stack);
        LeatherworkData.stampCrafter(stack, player.getUUID());
        stack.setHoverName(Component.literal(name));
        if (color >= 0 && item instanceof DyeableLeatherItem dyeable) dyeable.setColor(stack, color);
        stack.enchant(Enchantments.UNBREAKING, 1);
        return stack;
    }

    private static MerchantOffer offer(ItemStack a, ItemStack b, ItemStack result, int uses, int maxUses, int xp, float multiplier, int demand,
                                       int special, boolean reward) {
        CompoundTag tag = new MerchantOffer(a, b, result, uses, maxUses, xp, multiplier, demand).createTag();
        tag.putInt("specialPrice", special);
        tag.putBoolean("rewardExp", reward);
        return new MerchantOffer(tag);
    }

    private static boolean hasLeather(MerchantOffer offer) {
        return offer.getBaseCostA().is(Items.LEATHER) || offer.getCostB().is(Items.LEATHER) || offer.getResult().is(Items.LEATHER);
    }

    private static String describe(MerchantOffer offer) {
        return offer.getBaseCostA() + " + " + offer.getCostB() + " -> " + offer.getResult();
    }

    private static Map<String, Integer> others(List<ItemStack> drops) {
        Map<String, Integer> out = new java.util.TreeMap<>();
        for (ItemStack s : drops) {
            if (s.is(Items.LEATHER) || s.is(ModItems.BINDING_LEATHER.get()) || s.is(Items.RABBIT_HIDE) || s.getItem() instanceof RawHideItem) continue;
            out.merge(ForgeRegistries.ITEMS.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
        }
        return out;
    }

    private static int count(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static int count(List<ItemStack> stacks, HideSize size) {
        return stacks.stream().filter(s -> size(s) == size).mapToInt(ItemStack::getCount).sum();
    }

    private static int hides(List<ItemStack> stacks) {
        return stacks.stream().filter(s -> s.getItem() instanceof RawHideItem).mapToInt(ItemStack::getCount).sum();
    }

    private static HideSize size(ItemStack stack) {
        return stack.getItem() instanceof RawHideItem raw ? raw.size() : null;
    }

    private static HideSize sizeOf(EntityType<?> type) {
        HideSourceLoader.HideSource source = HideSourceLoader.forEntity(type);
        return source == null ? null : source.size();
    }

    private static HideSourceLoader.Decision hide(HideSize size, int count) {
        return new HideSourceLoader.Decision(HideSourceLoader.Kind.HIDE,
                new HideSourceLoader.HideSource(id("test/" + size.getSerializedName()), List.of(), List.of(), size, count, null, 5));
    }

    private static EntityType<?> ga(String path) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(GeneticAnimals.MOD_ID, path));
        if (type == null || type == EntityType.PIG && !path.equals("pig")) throw new IllegalStateException("No entity eanimod:" + path);
        return type;
    }

    private static Item gaItem(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(GeneticAnimals.MOD_ID, path));
        if (item == null || item == Items.AIR) throw new IllegalStateException("No item eanimod:" + path);
        return item;
    }

    private static ResourceLocation id(String path) {
        return ImmersiveTanning.id(path);
    }

    private static TestFunction test(String name, Consumer<GameTestHelper> body) {
        return new TestFunction("defaultBatch", "geneticanimalsgametests." + name, TEMPLATE, 400, 0L, true, body);
    }

    private static void reload(MinecraftServer server, Collection<String> packs) {
        CompletableFuture<Void> done = server.reloadResources(packs);
        server.managedBlock(done::isDone);
        done.join();
    }

    private GeneticAnimalsGameTests() {}
}
