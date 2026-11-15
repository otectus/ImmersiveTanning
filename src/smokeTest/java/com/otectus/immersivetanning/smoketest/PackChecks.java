package com.otectus.immersivetanning.smoketest;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.blockentity.TannersVatBlockEntity;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.effect.LeatherworkPolicy;
import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.hide.HideItems;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.hide.HideSourceLoader;
import com.otectus.immersivetanning.item.RawHideItem;
import com.otectus.immersivetanning.leather.FieldRepair;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.leather.LeatherworkData;
import com.otectus.immersivetanning.leather.LeatherworkProfile;
import com.otectus.immersivetanning.recipe.EquipmentSeams;
import com.otectus.immersivetanning.recipe.LeatherworkingRecipe;
import com.otectus.immersivetanning.recipe.TanningData;
import com.otectus.immersivetanning.registry.ModBlocks;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModRecipeTypes;
import com.otectus.immersivetanning.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Difficulty;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks that only mean something inside a modpack (the Ultima harness passes {@code -Dimmersive_tanning.smoketest.expect}):
 * modded animals drop hides, foreign skins mount, carcass kills leave the skin to Butchery, modded magic counts for
 * traits, modded gear routes through the bench, and villages trade. Every group runs only when its mods are loaded, so
 * an expected group whose mods are missing fails loudly instead of passing silently.
 */
final class PackChecks {
    private static final List<Kill> KILLS = new ArrayList<>();
    private static final Map<String, String> STATES = new LinkedHashMap<>();

    /** One real kill staged in the world: which entity, with which weapon, and whether a hide must drop. */
    private record Kill(String name, String entity, String weapon, boolean expectHide, BlockPos pos) {}

    static void data(Map<String, Object> checks, Set<String> expect, net.minecraft.core.RegistryAccess access) {
        checks.put("pack_summary", TanningData.current().summary());
        checks.put("pack_hide_sources_cover", HideSourceLoader.coveredEntityTypes());
        checks.put("pack_hide_sources_not_installed", HideSourceLoader.unknownEntities());
        if (expect.contains("dragn")) {
            group(checks, "dragn", "dragnlivestock", "dragnpets");
            source(checks, "dragn_o_cow_large", "dragnlivestock:o_cow", HideSize.LARGE, null);
            source(checks, "dragn_o_sheep_medium", "dragnlivestock:o_sheep", HideSize.MEDIUM, null);
            source(checks, "dragn_o_horse_large", "dragnlivestock:o_horse", HideSize.LARGE, null);
            source(checks, "dragn_o_rabbit_small", "dragnlivestock:o_rabbit", HideSize.SMALL, null);
            source(checks, "dragn_o_wolf_medium", "dragnpets:o_wolf", HideSize.MEDIUM, null);
            source(checks, "dragn_caribou_insulating", "dragnlivestock:caribou", HideSize.LARGE, "insulating");
        }
        if (expect.contains("lycanites")) {
            group(checks, "lycanites", "lycanitesmobs");
            source(checks, "lycanites_maka_large", "lycanitesmobs:maka", HideSize.LARGE, null);
            source(checks, "lycanites_makaalpha_ironhide", "lycanitesmobs:makaalpha", HideSize.LARGE, "ironhide");
            source(checks, "lycanites_maug_insulating", "lycanitesmobs:maug", HideSize.MEDIUM, "insulating");
        }
        if (expect.contains("alexsmobs")) {
            group(checks, "alexsmobs", "alexsmobs");
            source(checks, "alexsmobs_bison_insulating", "alexsmobs:bison", HideSize.LARGE, "insulating");
            source(checks, "alexsmobs_rhinoceros_ironhide", "alexsmobs:rhinoceros", HideSize.LARGE, "ironhide");
            source(checks, "alexsmobs_seal_water_resistant", "alexsmobs:seal", HideSize.MEDIUM, "water_resistant");
        }
        if (expect.contains("butchery")) {
            group(checks, "butchery", "butchery");
            mount(checks, "butchery_cow_skin_large", "butchery:cow_skin", HideSize.LARGE, null);
            mount(checks, "butchery_polar_bear_skin_insulating", "butchery:polar_bear_skin", HideSize.LARGE, "insulating");
            mount(checks, "butchery_pig_skin_medium", "butchery:pig_skin", HideSize.MEDIUM, null);
            checks.put("butchery_cleaver_is_carcass_tool", stack("butchery:iron_cleaver").is(ModTags.CARCASS_TOOLS));
            checks.put("butchery_knife_skins", new ItemStack(ModItems.TANNERS_KNIFE.get())
                    .is(ItemTags.create(new ResourceLocation("forge", "skinning_knives"))));
            checks.put("butchery_o_cow_is_carcass_animal", entity("dragnlivestock:o_cow") != null
                    && entity("dragnlivestock:o_cow").is(ModTags.CARCASS_ANIMALS));
        }
        if (expect.contains("magic")) {
            group(checks, "magic", "irons_spellbooks", "iceandfire");
            checks.put("magic_fire_spells_heatproof", damage(access, "irons_spellbooks:fire_magic", ModTags.RESISTED_BY_HEATPROOF));
            checks.put("magic_ice_spells_insulating", damage(access, "irons_spellbooks:ice_magic", ModTags.RESISTED_BY_INSULATING));
            checks.put("magic_dragon_fire_heatproof", damage(access, "iceandfire:dragon_fire", ModTags.RESISTED_BY_HEATPROOF));
        }
        if (expect.contains("bench")) {
            bench(checks, "bench_spartan_quiver", "spartanweaponry:small_arrow_quiver", null);
            bench(checks, "bench_dragn_light_saddle", "dragnlivestock:light_saddle", ImmersiveTanning.id("saddle"));
            bench(checks, "bench_dragn_wagon_harness", "dragnlivestock:wagon_harness", ImmersiveTanning.id("saddle"));
            bench(checks, "bench_falconry_glove", "alexsmobs:falconry_glove", null);
        }
        if (expect.contains("iceandfire")) iceAndFire(checks);
        if (expect.contains("geneticanimals")) {
            group(checks, "geneticanimals", "eanimod");
            GeneticAnimalsChecks.data(checks);
        }
        if (expect.contains("trades")) {
            ServerLevel level = net.minecraft.client.Minecraft.getInstance().getSingleplayerServer().overworld();
            Entity villager = EntityType.VILLAGER.create(level);
            boolean knife = false;
            boolean leatherwork = false;
            var trades = VillagerTrades.TRADES.get(VillagerProfession.LEATHERWORKER);
            for (int tier = 1; tier <= 5 && trades != null; tier++) {
                for (VillagerTrades.ItemListing listing : trades.getOrDefault(tier, new VillagerTrades.ItemListing[0])) {
                    MerchantOffer offer = listing.getOffer(villager, level.getRandom());
                    if (offer == null) continue;
                    knife |= offer.getResult().is(ModItems.TANNERS_KNIFE.get());
                    leatherwork |= com.otectus.immersivetanning.leather.LeatherworkData.has(offer.getResult());
                }
            }
            checks.put("trades_leatherworker_sells_knife", knife);
            checks.put("trades_leatherworker_sells_leatherwork", leatherwork);
        }
    }

    private static final String[] SLOTS = {"helmet", "chestplate", "leggings", "boots"};
    private static final EquipmentSlot[] EQUIPMENT = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** Ice and Fire's own scale counts and the leather backing the bench adds. */
    private static final int[] SCALES = {5, 8, 7, 4};
    private static final int[] BACKING = {1, 2, 2, 1};

    /**
     * Ice and Fire dragon-scale armor at the bench. Every scale in Ice and Fire's own fire, ice and lightning tags has
     * its four pieces, at the original scale counts plus the leather backing, making the native armor, with the crafting
     * recipe gone on server and client. Then the scale-backed policy on real pieces (the native numbers as read in game,
     * a full set at three quality marks) and the repair rules.
     */
    private static void iceAndFire(Map<String, Object> checks) {
        group(checks, "iceandfire", "iceandfire");
        if (!ModList.get().isLoaded("iceandfire")) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        TanningData data = TanningData.current();
        List<String> problems = new ArrayList<>();
        Map<String, Integer> scalesByElement = new LinkedHashMap<>();
        int pieces = 0;
        for (String element : List.of("fire", "ice", "lightning")) {
            List<Item> scales = BuiltInRegistries.ITEM.getTag(ItemTags.create(new ResourceLocation("iceandfire", "scales/dragon/" + element)))
                    .map(set -> set.stream().map(net.minecraft.core.Holder::value).toList()).orElse(List.of());
            scalesByElement.put(element, scales.size());
            for (Item scale : scales) {
                for (int i = 0; i < SLOTS.length; i++) {
                    ResourceLocation seam = ImmersiveTanning.id(SLOTS[i]);
                    LeatherworkingRecipe recipe = data.recipes().stream().filter(r -> r.seam().equals(seam) && r.auxiliary().size() == 1
                            && r.auxiliary().get(0).ingredient().test(new ItemStack(scale))).findFirst().orElse(null);
                    String name = BuiltInRegistries.ITEM.getKey(scale) + " " + SLOTS[i];
                    if (recipe == null) {
                        problems.add(name + ": no bench recipe");
                        continue;
                    }
                    pieces++;
                    ItemStack result = recipe.result();
                    ResourceLocation replaced = recipe.replaces().isEmpty() ? null : recipe.replaces().get(0);
                    if (recipe.leather() != BACKING[i] || recipe.auxiliary().get(0).count() != SCALES[i]) {
                        problems.add(name + ": costs " + recipe.leather() + " leather and " + recipe.auxiliary().get(0).count() + " scales");
                    }
                    if (!result.is(ModTags.SCALE_BACKED) || LivingEntity.getEquipmentSlotForItem(result) != EQUIPMENT[i]) {
                        problems.add(name + ": makes " + BuiltInRegistries.ITEM.getKey(result.getItem()));
                    }
                    if (replaced == null || server.getRecipeManager().byKey(replaced).isPresent() || mc.level.getRecipeManager().byKey(replaced).isPresent()
                            || data.suppressedRecipes().get(replaced) != result.getItem()) {
                        problems.add(name + ": crafting recipe " + replaced + " is still active");
                    }
                    if (mc.level.getRecipeManager().byKey(recipe.getId()).isEmpty()) problems.add(name + ": the client lacks " + recipe.getId());
                }
            }
        }
        checks.put("iceandfire_scales_by_element", scalesByElement);
        checks.put("iceandfire_bench_pieces", pieces);
        checks.put("iceandfire_problems", problems);
        checks.put("iceandfire_every_colour_and_slot", pieces == 48 && problems.isEmpty() && scalesByElement.values().stream().allMatch(n -> n == 4));
        checks.put("iceandfire_notes", data.notes().stream().filter(n -> n.contains("iceandfire")).toList());

        LeatherworkPolicy policy = LeatherworkPolicy.scaleBacked();
        Map<String, Object> natives = new LinkedHashMap<>();
        Map<String, Object> table = new LinkedHashMap<>();
        boolean matches = true;
        for (int quality : new int[]{0, 50, 100}) {
            double armor = 0;
            double hardenedArmor = 0;
            double hardenedToughness = 0;
            double speed = 0;
            double visibility = 1;
            double durability = 0;
            double harness = 0;
            for (int i = 0; i < SLOTS.length; i++) {
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("iceandfire", "armor_red_" + SLOTS[i]));
                ItemStack plain = new ItemStack(item);
                double nativeArmor = sum(plain, EQUIPMENT[i], Attributes.ARMOR, AttributeModifier.Operation.ADDITION);
                double nativeToughness = sum(plain, EQUIPMENT[i], Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADDITION);
                if (quality == 50) {
                    natives.put(SLOTS[i], "armor " + nativeArmor + ", toughness " + nativeToughness + ", durability " + plain.getMaxDamage()
                            + ", modifiers " + plain.getAttributeModifiers(EQUIPMENT[i]).size());
                }
                ItemStack hardened = made(item, Finish.HARDENED, quality);
                ItemStack supple = made(item, Finish.SUPPLE, quality);
                ItemStack harnessPiece = made(item, Finish.HARNESS, quality);
                double efficacy = policy.efficacy(LeatherworkData.get(hardened).orElseThrow());
                double gotArmor = sum(hardened, EQUIPMENT[i], Attributes.ARMOR, AttributeModifier.Operation.ADDITION);
                double gotToughness = sum(hardened, EQUIPMENT[i], Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADDITION);
                matches &= LeatherworkProfile.of(hardened) == LeatherworkProfile.SCALE_BACKED
                        && Math.abs(gotArmor - (nativeArmor * efficacy + policy.hardenedArmor() * efficacy)) < 1.0E-6
                        && Math.abs(gotToughness - (nativeToughness + policy.hardenedToughness() * efficacy)) < 1.0E-6
                        && hardened.getAttributeModifiers(EQUIPMENT[i]).size() == plain.getAttributeModifiers(EQUIPMENT[i]).size() + 1;
                armor += sum(supple, EQUIPMENT[i], Attributes.ARMOR, AttributeModifier.Operation.ADDITION);
                hardenedArmor += gotArmor;
                hardenedToughness += gotToughness;
                speed += sum(supple, EQUIPMENT[i], Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.MULTIPLY_BASE);
                visibility *= 1 - net.minecraft.util.Mth.clamp(policy.suppleVisibility() * efficacy, 0.0, 0.5);
                durability += plain.getMaxDamage() * LeatherworkPolicy.of(supple).durability(LeatherworkData.get(supple).orElseThrow());
                harness += plain.getMaxDamage() * LeatherworkPolicy.of(harnessPiece).durability(LeatherworkData.get(harnessPiece).orElseThrow());
            }
            table.put("quality_" + quality, String.format(java.util.Locale.ROOT, "armor %.2f; Hardened %.2f armor, %.2f toughness; Supple +%.1f%% speed, "
                    + "%.1f%% less detection; effective durability %.0f, Harness %.0f", armor, hardenedArmor, hardenedToughness, speed * 100,
                    (1 - visibility) * 100, durability, harness));
        }
        checks.put("iceandfire_red_armor_native", natives);
        checks.put("iceandfire_red_set_by_quality", table);
        checks.put("iceandfire_policy_matches_on_real_pieces", matches);

        Item chest = ForgeRegistries.ITEMS.getValue(new ResourceLocation("iceandfire", "armor_red_chestplate"));
        ItemStack worn = made(chest, Finish.HARNESS, 50);
        worn.setDamageValue(100);
        checks.put("iceandfire_scraps_do_not_patch", !FieldRepair.isRepairable(worn));
    }

    /**
     * Checks on the server thread: the knife on Regions Unexplored logs through the real interaction path, Farmer's
     * Delight tree bark in the vat, Ice and Fire's breath protection on a stitched set, and the anvil on stitched pieces.
     */
    static void world(MinecraftServer server, BlockPos base, Set<String> expect, Map<String, Object> checks) {
        ServerLevel level = server.overworld();
        if (expect.contains("geneticanimals") && ModList.get().isLoaded("eanimod")) GeneticAnimalsChecks.stage(server, base, checks);
        FakePlayer tanner = FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(
                java.util.UUID.nameUUIDFromBytes("immersive_tanning:pack_tanner".getBytes(java.nio.charset.StandardCharsets.UTF_8)), "PackTanner"));
        if (expect.contains("knife")) {
            group(checks, "knife", "regions_unexplored", "farmersdelight");
            BlockPos pos = base.offset(-7, 0, 7);
            for (String id : List.of("regions_unexplored:baobab_log", "regions_unexplored:pine_log")) {
                String name = "knife_strips_" + id.replace(':', '_');
                net.minecraft.world.level.block.Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
                if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) {
                    checks.put(name, false);
                    continue;
                }
                level.setBlockAndUpdate(pos, block.defaultBlockState());
                tanner.getInventory().clearContent();
                tanner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TANNERS_KNIFE.get()));
                tanner.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5, 180F, 30F);
                InteractionResult result = tanner.gameMode.useItemOn(tanner, level, tanner.getMainHandItem(), InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                net.minecraft.world.level.block.state.BlockState after = level.getBlockState(pos);
                int bark = tanner.getInventory().countItem(ModItems.BARK.get());
                checks.put(name, !after.is(block) && bark == ServerConfig.get(ServerConfig.BARK_PER_LOG) && tanner.getMainHandItem().getDamageValue() == 1);
                checks.put(name + "_detail", "result " + result + ", now " + after + ", bark " + bark);
            }
            level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            BlockPos vatPos = base.offset(-7, 0, 5);
            level.setBlockAndUpdate(vatPos, ModBlocks.TANNERS_VAT.get().defaultBlockState());
            TannersVatBlockEntity vat = (TannersVatBlockEntity) level.getBlockEntity(vatPos);
            vat.fillWater();
            tanner.setItemInHand(InteractionHand.MAIN_HAND, stack("farmersdelight:tree_bark").copyWithCount(3));
            level.getBlockState(vatPos).use(level, tanner, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(vatPos), Direction.UP, vatPos, false));
            checks.put("knife_vat_takes_farmers_delight_bark", stack("farmersdelight:tree_bark").is(ModTags.TANNIN_SOURCES) && vat.tannin() == 3
                    && tanner.getMainHandItem().isEmpty());
            level.setBlockAndUpdate(vatPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        if (expect.contains("iceandfire") && ModList.get().isLoaded("iceandfire")) {
            // Ice and Fire's own hurt handler reduces its dragon breath for scale armor. A stitched set (Standard Supple:
            // the same armor as a plain set) must take exactly what a plain set takes, and less than no armor.
            float bare = breath(level, base, null);
            float plain = breath(level, base, false);
            float stitched = breath(level, base, true);
            checks.put("iceandfire_breath_protection_kept", plain < bare && Math.abs(stitched - plain) < 1.0E-4F);
            checks.put("iceandfire_breath_damage", "no armor " + bare + ", plain set " + plain + ", stitched set " + stitched);
            Item chest = ForgeRegistries.ITEMS.getValue(new ResourceLocation("iceandfire", "armor_red_chestplate"));
            ItemStack first = made(chest, Finish.HARNESS, 50);
            first.setDamageValue(300);
            ItemStack second = new ItemStack(chest);
            second.setDamageValue(300);
            net.minecraft.world.inventory.AnvilMenu anvil = new net.minecraft.world.inventory.AnvilMenu(0, tanner.getInventory(),
                    net.minecraft.world.inventory.ContainerLevelAccess.create(level, base));
            anvil.getSlot(0).set(first);
            anvil.getSlot(1).set(second);
            ItemStack out = anvil.getSlot(2).getItem();
            checks.put("iceandfire_anvil_combines_stitched_pieces", out.is(chest) && out.getDamageValue() < 300 && LeatherworkData.get(out).isPresent()
                    && LeatherworkProfile.of(out) == LeatherworkProfile.SCALE_BACKED);
            // Ice and Fire's own repair material, each colour's scales, still mends a stitched piece on the anvil.
            ItemStack scaled = made(chest, Finish.HARNESS, 50);
            scaled.setDamageValue(300);
            net.minecraft.world.inventory.AnvilMenu mending = new net.minecraft.world.inventory.AnvilMenu(0, tanner.getInventory(),
                    net.minecraft.world.inventory.ContainerLevelAccess.create(level, base));
            mending.getSlot(0).set(scaled);
            mending.getSlot(1).set(stack("iceandfire:dragonscales_red").copyWithCount(2));
            ItemStack mended = mending.getSlot(2).getItem();
            checks.put("iceandfire_anvil_mends_with_its_own_scales", mended.is(chest) && mended.getDamageValue() < 300 && LeatherworkData.get(mended).isPresent());
            checks.put("iceandfire_anvil_detail", "combined: damage " + out.getDamageValue() + ", with two red scales: damage " + mended.getDamageValue()
                    + " of " + out.getMaxDamage());
        }
        tanner.getInventory().clearContent();
    }

    /** Health a fresh zombie loses to 10 dragon fire: bare (null), in the plain red set, or in the stitched red set. */
    private static float breath(ServerLevel level, BlockPos base, Boolean stitched) {
        net.minecraft.world.entity.monster.Zombie zombie = EntityType.ZOMBIE.create(level);
        zombie.moveTo(base.getX() + 0.5, base.getY(), base.getZ() - 6.5, 0F, 0F);
        if (stitched != null) {
            for (int i = 0; i < SLOTS.length; i++) {
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("iceandfire", "armor_red_" + SLOTS[i]));
                zombie.setItemSlot(EQUIPMENT[i], stitched ? made(item, Finish.SUPPLE, 50) : new ItemStack(item));
            }
        }
        float before = zombie.getHealth();
        var fire = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("iceandfire", "dragon_fire")));
        zombie.hurt(new net.minecraft.world.damagesource.DamageSource(fire), 10F);
        float lost = before - zombie.getHealth();
        zombie.discard();
        return lost;
    }

    private static ItemStack made(Item item, Finish finish, int quality) {
        ItemStack stack = new ItemStack(item);
        new LeatherworkData(finish, quality, quality, null, "PackTest").apply(stack);
        return stack;
    }

    private static double sum(ItemStack stack, EquipmentSlot slot, net.minecraft.world.entity.ai.attributes.Attribute attribute, AttributeModifier.Operation operation) {
        double total = 0;
        for (AttributeModifier m : stack.getAttributeModifiers(slot).get(attribute)) {
            if (m.getOperation() == operation) total += m.getAmount();
        }
        return total;
    }

    /** Spawns each expected kill's entity 40+ blocks from the staged stations and kills it with the weapon. */
    static void startKills(MinecraftServer server, BlockPos base, Set<String> expect) {
        ServerLevel level = server.overworld();
        List<Kill> kills = new ArrayList<>();
        if (expect.contains("dragn")) kills.add(new Kill("kill_o_cow_sword", "dragnlivestock:o_cow", "minecraft:iron_sword", true, null));
        if (expect.contains("butchery")) kills.add(new Kill("kill_o_cow_cleaver", "dragnlivestock:o_cow", "butchery:iron_cleaver", false, null));
        if (expect.contains("lycanites")) kills.add(new Kill("kill_maka_sword", "lycanitesmobs:maka", "minecraft:iron_sword", true, null));
        if (expect.contains("alexsmobs")) kills.add(new Kill("kill_bison_sword", "alexsmobs:bison", "minecraft:iron_sword", true, null));
        server.setDifficulty(Difficulty.NORMAL, true);
        FakePlayer hunter = FakePlayerFactory.getMinecraft(level);
        for (int i = 0; i < kills.size(); i++) {
            Kill k = kills.get(i);
            BlockPos pos = base.offset(40 + i * 16, 0, 24);
            KILLS.add(new Kill(k.name, k.entity, k.weapon, k.expectHide, pos));
            EntityType<?> type = entity(k.entity);
            Entity e = type == null ? null : type.create(level);
            if (!(e instanceof LivingEntity victim)) continue;
            victim.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0F, 0F);
            if (victim instanceof net.minecraft.world.entity.Mob mob && mob.isBaby()) mob.setBaby(false);
            level.addFreshEntity(victim);
            hunter.moveTo(pos.getX() + 2.5, pos.getY(), pos.getZ() + 0.5, 90F, 0F);
            hunter.setItemInHand(InteractionHand.MAIN_HAND, stack(k.weapon));
            victim.hurt(level.damageSources().playerAttack(hunter), 10_000F);
            STATES.put(k.name, "baby=" + victim.isBaby() + ", dead=" + victim.isDeadOrDying() + ", removed=" + victim.isRemoved());
        }
        hunter.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** Reads what each kill left on the ground (after Butchery's delayed clean-up has run). */
    static void collectKills(MinecraftServer server, Map<String, Object> checks) {
        ServerLevel level = server.overworld();
        for (Kill k : KILLS) {
            List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(k.pos).inflate(6));
            List<String> ids = new ArrayList<>();
            boolean hide = false;
            boolean leather = false;
            for (ItemEntity item : items) {
                ItemStack s = item.getItem();
                ids.add(BuiltInRegistries.ITEM.getKey(s.getItem()) + (s.getCount() > 1 ? " x" + s.getCount() : ""));
                hide |= s.getItem() instanceof RawHideItem;
                leather |= s.is(Items.LEATHER) || s.is(ModItems.BINDING_LEATHER.get());
            }
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("drops", ids);
            detail.put("victim", STATES.get(k.name));
            detail.put("pass", k.expectHide ? hide && !leather : !hide);
            checks.put(k.name + "_drops", detail);
            checks.put(k.name, k.expectHide ? hide && !leather : !hide);
            items.forEach(Entity::discard);
        }
        server.setDifficulty(Difficulty.PEACEFUL, true);
    }

    private static void group(Map<String, Object> checks, String name, String... mods) {
        for (String mod : mods) checks.put(name + "_mod_" + mod + "_loaded", ModList.get().isLoaded(mod));
    }

    private static void source(Map<String, Object> checks, String name, String entityId, HideSize size, String trait) {
        EntityType<?> type = entity(entityId);
        HideSourceLoader.HideSource source = type == null ? null : HideSourceLoader.forEntity(type);
        boolean traitOk = trait == null ? source != null && source.trait() == null
                : source != null && source.trait() != null && source.trait().equals(ImmersiveTanning.id(trait)) && source.traitRef() != null;
        checks.put(name, source != null && source.size() == size && traitOk);
    }

    private static void mount(Map<String, Object> checks, String name, String itemId, HideSize size, String trait) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        ServerLevel level = mc.getSingleplayerServer().overworld();
        HideData hide = HideItems.resolve(level, stack(itemId)).orElse(null);
        boolean traitOk = trait == null ? hide != null && hide.trait() == null
                : hide != null && hide.trait() != null && hide.trait().id().equals(ImmersiveTanning.id(trait));
        boolean client = mc.level != null && HideItems.isMountable(mc.level, stack(itemId));
        checks.put(name, hide != null && hide.size() == size && traitOk && client);
    }

    private static void bench(Map<String, Object> checks, String name, String itemId, ResourceLocation seam) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
        List<LeatherworkingRecipe> recipes = item == null || item == Items.AIR ? List.of() : TanningData.current().recipesProducing(item);
        boolean client = net.minecraft.client.Minecraft.getInstance().level.getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.LEATHERWORKING.get()).stream().anyMatch(r -> r.resultItem() == item);
        boolean seamOk = seam == null || recipes.stream().allMatch(r -> r.seam().equals(seam))
                && EquipmentSeams.classify(new ItemStack(item)).equals(seam);
        checks.put(name, !recipes.isEmpty() && client && seamOk);
        String id = itemId;
        checks.put(name + "_detail", "maxStack=" + (item == null ? 0 : new ItemStack(item).getMaxStackSize()) + ", recipes=" + recipes.size()
                + ", notes=" + TanningData.current().notes().stream().filter(n -> n.contains(id)).toList());
    }

    private static boolean damage(net.minecraft.core.RegistryAccess access, String id, net.minecraft.tags.TagKey<net.minecraft.world.damagesource.DamageType> tag) {
        var registry = access.registryOrThrow(Registries.DAMAGE_TYPE);
        var key = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(id));
        return registry.getHolder(key).map(h -> h.is(tag)).orElse(false);
    }

    private static EntityType<?> entity(String id) {
        ResourceLocation key = new ResourceLocation(id);
        return ForgeRegistries.ENTITY_TYPES.containsKey(key) ? ForgeRegistries.ENTITY_TYPES.getValue(key) : null;
    }

    private static ItemStack stack(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private PackChecks() {}
}
