package com.otectus.immersivetanning.smoketest;

import com.otectus.immersivetanning.compat.geneticanimals.GeneticAnimals;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.hide.HideSourceLoader;
import com.otectus.immersivetanning.item.RawHideItem;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.leather.LeatherworkData;
import com.otectus.immersivetanning.recipe.TanningData;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModRecipeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code geneticanimals} pack-test group: Genetic Animals next to the reobfuscated jar, through the game's own
 * paths. Animals are spawned through its spawn path, tack is put on by a player's right-click, and they are killed by
 * a player once its 100-tick spawn protection is over, so nothing reaches into Genetic Animals' internals.
 */
final class GeneticAnimalsChecks {
    private static final String MOD = "eanimod";
    private static final List<Staged> STAGED = new ArrayList<>();

    /** One animal waiting to be killed: what it carries and wears, and the weapon. */
    private record Staged(String name, LivingEntity animal, List<ItemStack> kept, String weapon) {}

    static void data(Map<String, Object> checks) {
        checks.put("ga_mod_eanimod_loaded", ModList.get().isLoaded(MOD));
        checks.put("ga_verified_release", GeneticAnimals.verified());
        checks.put("ga_version_detail", String.valueOf(GeneticAnimals.version()));
        checks.put("ga_hooks_applied", GeneticAnimals.hooksApplied());
        checks.put("ga_hooks_detail", String.join(", ", GeneticAnimals.hookStates()));
        Map<String, HideSize> sizes = new LinkedHashMap<>();
        sizes.put("enhanced_cow", HideSize.LARGE);
        sizes.put("enhanced_mooshroom", HideSize.LARGE);
        sizes.put("enhanced_moobloom", HideSize.LARGE);
        sizes.put("enhanced_llama", HideSize.LARGE);
        sizes.put("enhanced_sheep", HideSize.MEDIUM);
        sizes.put("enhanced_pig", HideSize.MEDIUM);
        sizes.put("enhanced_rabbit", HideSize.SMALL);
        sizes.forEach((path, size) -> {
            EntityType<?> type = type(path);
            HideSourceLoader.HideSource source = type == null ? null : HideSourceLoader.forEntity(type);
            checks.put("ga_source_" + path, source != null && source.size() == size && GeneticAnimals.owns(type));
        });
        for (String path : List.of("collar_basic_leather", "bridle_basic_leather")) {
            Item item = item(path);
            boolean client = item != null && net.minecraft.client.Minecraft.getInstance().level.getRecipeManager()
                    .getAllRecipesFor(ModRecipeTypes.LEATHERWORKING.get()).stream().anyMatch(r -> r.resultItem() == item);
            checks.put("ga_bench_" + path, item != null && TanningData.current().recipesProducing(item).size() == 1 && client);
        }
        MinecraftServer server = net.minecraft.client.Minecraft.getInstance().getSingleplayerServer();
        checks.put("ga_crafting_routed_to_bench", server != null && server.getRecipeManager().byKey(new ResourceLocation(MOD, "collar_basic_leather")).isEmpty()
                && server.getRecipeManager().byKey(new ResourceLocation(MOD, "collar_basic_cloth")).isPresent());
    }

    /** Spawns the animals, loads the llama's cargo and saddles and bridles a cow by right-click. */
    static void stage(MinecraftServer server, BlockPos base, Map<String, Object> checks) {
        ServerLevel level = server.overworld();
        FakePlayer tanner = FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(
                java.util.UUID.nameUUIDFromBytes("immersive_tanning:ga_tanner".getBytes(java.nio.charset.StandardCharsets.UTF_8)), "GaTanner"));
        boolean butchery = ModList.get().isLoaded("butchery");
        int slot = 0;
        for (int i = 0; i < 3; i++) STAGED.add(new Staged("ga_kill_cow_" + i, spawn(level, base, "enhanced_cow", slot++), List.of(), "minecraft:iron_sword"));
        STAGED.add(new Staged("ga_kill_pig", spawn(level, base, "enhanced_pig", slot++), List.of(), "minecraft:iron_sword"));
        STAGED.add(new Staged("ga_kill_rabbit", spawn(level, base, "enhanced_rabbit", slot++), List.of(), "minecraft:iron_sword"));
        STAGED.add(new Staged("ga_kill_sheep", spawn(level, base, "enhanced_sheep", slot++), List.of(), "minecraft:iron_sword"));
        if (butchery) {
            STAGED.add(new Staged("ga_kill_sheep_cleaver", spawn(level, base, "enhanced_sheep", slot++), List.of(), "butchery:iron_cleaver"));
            STAGED.add(new Staged("ga_kill_cow_cleaver", spawn(level, base, "enhanced_cow", slot++), List.of(), "butchery:iron_cleaver"));
        }

        LivingEntity llama = spawn(level, base, "enhanced_llama", slot++);
        List<ItemStack> cargo = new ArrayList<>();
        if (llama != null) {
            IItemHandler inventory = llama.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
            ItemStack leather = new ItemStack(Items.LEATHER, 7);
            leather.setHoverName(Component.literal("Saddlebag leather"));
            cargo = List.of(leather, new ItemStack(Items.RABBIT_HIDE, 3), ModItems.rawHide(HideSize.LARGE).create(null).copyWithCount(2),
                    new ItemStack(ModItems.BINDING_LEATHER.get(), 5), new ItemStack(ModItems.leather(Finish.HARNESS), 4));
            boolean loaded = inventory != null && inventory.insertItem(0, new ItemStack(Items.CHEST), false).isEmpty();
            for (int i = 0; loaded && i < cargo.size(); i++) loaded = inventory.insertItem(7 + i, cargo.get(i).copy(), false).isEmpty();
            checks.put("ga_llama_cargo_loaded", loaded);
        }
        STAGED.add(new Staged("ga_kill_llama_cargo", llama, cargo, "minecraft:iron_sword"));

        LivingEntity cow = spawn(level, base, "enhanced_cow", slot);
        List<ItemStack> tack = new ArrayList<>();
        if (cow != null) {
            IItemHandler inventory = cow.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
            ItemStack bridle = made(item("bridle_basic_leather"), "Sunday Best", 0x336699, tanner);
            ItemStack saddle = made(Items.SADDLE, "Bench Saddle", -1, tanner);
            tack = List.of(bridle.copy(), saddle.copy());
            tanner.moveTo(cow.getX() + 1.5, cow.getY(), cow.getZ(), 90F, 0F);
            tanner.setItemInHand(InteractionHand.MAIN_HAND, bridle);
            tanner.interactOn(cow, InteractionHand.MAIN_HAND);
            tanner.setItemInHand(InteractionHand.MAIN_HAND, saddle);
            tanner.interactOn(cow, InteractionHand.MAIN_HAND);
            boolean kept = inventory != null && ItemStack.matches(inventory.getStackInSlot(3), tack.get(0)) && ItemStack.matches(inventory.getStackInSlot(1), tack.get(1));
            checks.put("ga_tack_keeps_data_on_animal", kept);
            checks.put("ga_tack_detail", inventory == null ? "no inventory" : "bridle " + inventory.getStackInSlot(3).getTag() + ", saddle " + inventory.getStackInSlot(1).getTag());
            tanner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        STAGED.add(new Staged("ga_kill_tacked_cow", cow, tack, "minecraft:iron_sword"));

        // Its offer factory, called as its own tick handler calls it.
        try {
            Class<?> trades = Class.forName("mokiyoki.enhancedanimals.util.EanimodVillagerTrades");
            trades.getConstructor().newInstance();
            java.lang.reflect.Method factory = trades.getMethod("getEanimodTrade", VillagerProfession.class, int.class);
            int leather = 0;
            int binding = 0;
            for (int i = 0; i < 400; i++) {
                MerchantOffer offer = (MerchantOffer) factory.invoke(null, VillagerProfession.LEATHERWORKER, 1 + i % 5);
                if (offer.getBaseCostA().is(Items.LEATHER) || offer.getCostB().is(Items.LEATHER)) leather++;
                if (offer.getBaseCostA().is(ModItems.BINDING_LEATHER.get())) binding++;
            }
            checks.put("ga_trades_binding_leather", leather == 0 && binding > 0);
            checks.put("ga_trades_detail", "400 leatherworker offers: " + binding + " ask for Binding Leather, " + leather + " for vanilla leather");
        } catch (ReflectiveOperationException e) {
            checks.put("ga_trades_binding_leather", false);
            checks.put("ga_trades_detail", e.toString());
        }
    }

    /** JEI (client thread): the Genetic Animals pages are on the raw hides and on its leather collar. */
    static void jei(Map<String, Object> checks) {
        var runtime = SmokeJeiPlugin.runtime;
        if (runtime == null) {
            checks.put("ga_jei_pages", false);
            checks.put("ga_jei_pages_detail", "no JEI runtime");
            return;
        }
        String hides = Component.translatable("jei.immersive_tanning.info.genetic_animals_hides").getString();
        String tack = Component.translatable("jei.immersive_tanning.info.genetic_animals_tack").getString();
        Item collar = item("collar_basic_leather");
        boolean hidePage = false;
        boolean tackPage = false;
        for (var recipe : runtime.getRecipeManager().createRecipeLookup(mezz.jei.api.constants.RecipeTypes.INFORMATION).get().toList()) {
            String text = recipe.getDescription().stream().map(net.minecraft.network.chat.FormattedText::getString).reduce("", (a, b) -> a + b);
            boolean onHide = recipe.getIngredients().stream().anyMatch(i -> i.getItemStack().map(s -> s.getItem() instanceof RawHideItem).orElse(false));
            boolean onCollar = collar != null && recipe.getIngredients().stream().anyMatch(i -> i.getItemStack().map(s -> s.is(collar)).orElse(false));
            hidePage |= onHide && text.startsWith(hides.substring(0, 40));
            tackPage |= onCollar && text.startsWith(tack.substring(0, 40));
        }
        checks.put("ga_jei_pages", hidePage && tackPage);
        checks.put("ga_jei_pages_detail", "raw hide page " + hidePage + ", collar page " + tackPage);
    }

    /** Kills every staged animal (its spawn protection is long over) and checks what it dropped. */
    static void kill(MinecraftServer server, Map<String, Object> checks) {
        ServerLevel level = server.overworld();
        FakePlayer hunter = FakePlayerFactory.getMinecraft(level);
        for (Staged staged : STAGED) {
            if (staged.animal == null) {
                checks.put(staged.name, false);
                continue;
            }
            LivingEntity animal = staged.animal;
            hunter.moveTo(animal.getX() + 1.5, animal.getY(), animal.getZ(), 90F, 0F);
            hunter.setItemInHand(InteractionHand.MAIN_HAND, stack(staged.weapon));
            boolean hurt = animal.hurt(level.damageSources().playerAttack(hunter), 10_000F);
            List<ItemStack> drops = new ArrayList<>();
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(animal.blockPosition()).inflate(3))) {
                drops.add(item.getItem().copy());
                item.discard();
            }
            int leather = count(drops, Items.LEATHER);
            int binding = count(drops, ModItems.BINDING_LEATHER.get());
            int rabbitHides = count(drops, Items.RABBIT_HIDE);
            int units = 0;
            for (ItemStack s : drops) if (s.getItem() instanceof RawHideItem raw) units += raw.size().leather() * s.getCount();
            boolean carcass = drops.stream().anyMatch(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().endsWith("_carcass"));
            boolean pass;
            if (!animal.isDeadOrDying()) pass = false;
            else if (staged.name.startsWith("ga_kill_cow_") && !staged.name.endsWith("cleaver")) pass = leather + binding == 0 && units <= 5;
            else if (staged.name.equals("ga_kill_pig")) pass = units == 0;
            else if (staged.name.equals("ga_kill_rabbit")) pass = rabbitHides == 0 && leather + binding == 0;
            else if (staged.name.equals("ga_kill_sheep")) pass = leather + binding == 0 && (!carcass || units == 0);
            else if (staged.name.equals("ga_kill_sheep_cleaver")) pass = carcass && units == 0 && leather + binding == 0;
            else if (staged.name.equals("ga_kill_cow_cleaver")) pass = !carcass && leather + binding == 0 && units <= 5;
            else pass = staged.kept.stream().allMatch(k -> drops.stream().anyMatch(s -> ItemStack.matches(s, k)))
                    && drops.stream().noneMatch(s -> s.is(Items.LEATHER) && !s.hasCustomHoverName());
            checks.put(staged.name, pass);
            checks.put(staged.name + "_detail", "hurt " + hurt + ", dead " + animal.isDeadOrDying() + ", hide units " + units + ", drops "
                    + drops.stream().map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()) + " x" + s.getCount() + (s.hasCustomHoverName() ? " (named)" : "")).toList());
            if (animal.isAlive()) animal.discard();
        }
        hunter.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        STAGED.clear();
    }

    /** An adult through Genetic Animals' spawn path, kept in place (no AI) until it is killed. */
    private static LivingEntity spawn(ServerLevel level, BlockPos base, String path, int slot) {
        EntityType<?> type = type(path);
        if (type == null) return null;
        BlockPos pos = base.offset(-40 - (slot % 4) * 6, 0, 40 + (slot / 4) * 6);
        Entity entity = type.spawn(level, pos, MobSpawnType.SPAWN_EGG);
        if (!(entity instanceof LivingEntity living)) return null;
        CompoundTag tag = living.saveWithoutId(new CompoundTag());
        tag.putString("BirthTime", String.valueOf(level.getGameTime() - 400_000L));
        living.load(tag);
        if (living instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        }
        return living;
    }

    private static ItemStack made(Item item, String name, int color, FakePlayer player) {
        ItemStack stack = new ItemStack(item);
        new LeatherworkData(Finish.HARNESS, 70, 90, null, player.getName().getString()).apply(stack);
        LeatherworkData.stampCrafter(stack, player.getUUID());
        stack.setHoverName(Component.literal(name));
        if (color >= 0 && item instanceof DyeableLeatherItem dyeable) dyeable.setColor(stack, color);
        return stack;
    }

    private static int count(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static EntityType<?> type(String path) {
        ResourceLocation key = new ResourceLocation(MOD, path);
        return ForgeRegistries.ENTITY_TYPES.containsKey(key) ? ForgeRegistries.ENTITY_TYPES.getValue(key) : null;
    }

    private static Item item(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(MOD, path));
        return item == null || item == Items.AIR ? null : item;
    }

    private static ItemStack stack(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private GeneticAnimalsChecks() {}
}
