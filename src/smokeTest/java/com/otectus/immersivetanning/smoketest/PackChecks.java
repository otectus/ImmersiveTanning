package com.otectus.immersivetanning.smoketest;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.hide.HideItems;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.hide.HideSourceLoader;
import com.otectus.immersivetanning.item.RawHideItem;
import com.otectus.immersivetanning.recipe.EquipmentSeams;
import com.otectus.immersivetanning.recipe.LeatherworkingRecipe;
import com.otectus.immersivetanning.recipe.TanningData;
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
import net.minecraft.world.InteractionHand;
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
