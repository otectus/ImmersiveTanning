package com.otectus.immersivetanning.smoketest;

import com.google.gson.GsonBuilder;
import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.block.LeatherworkersBenchBlock;
import com.otectus.immersivetanning.block.TannersRackBlock;
import com.otectus.immersivetanning.blockentity.LeatherworkersBenchBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersVatBlockEntity;
import com.otectus.immersivetanning.client.compat.jei.LeatherworkingCategory;
import com.otectus.immersivetanning.client.compat.jei.TanneryCategory;
import com.otectus.immersivetanning.client.screen.FinishSelectScreen;
import com.otectus.immersivetanning.client.screen.FleshingScreen;
import com.otectus.immersivetanning.client.screen.LeatherworkScreen;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.leather.LeatherworkData;
import com.otectus.immersivetanning.minigame.SeamPatterns;
import com.otectus.immersivetanning.minigame.TanningSession;
import com.otectus.immersivetanning.network.packet.OpenBenchPacket;
import com.otectus.immersivetanning.network.packet.OpenFinishPacket;
import com.otectus.immersivetanning.network.packet.OpenFleshingPacket;
import com.otectus.immersivetanning.network.packet.SessionResultPacket;
import com.otectus.immersivetanning.network.packet.StitchStartPacket;
import com.otectus.immersivetanning.recipe.TanningData;
import com.otectus.immersivetanning.registry.ModBlocks;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModRecipeTypes;
import com.otectus.immersivetanning.trait.HideTraitLoader;
import com.otectus.immersivetanning.trait.TraitRef;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Drives a dev client from the title screen into a fresh flat world, checks that Immersive Tanning built its data,
 * stages every station state and every screen for framebuffer screenshots, writes {@code smoketest-result.json} and
 * quits. Screenshots land in {@code run/screenshots/smoketest_*.png}.
 */
@Mod.EventBusSubscriber(modid = SmokeTestAgent.MOD_ID, value = Dist.CLIENT)
public final class ClientSmokeTest {
    private static final String OUTPUT = System.getProperty(SmokeTestAgent.OUTPUT_PROPERTY);
    /** Pack check groups requested by the Ultima harness (see PackChecks); empty in the dev smoke test. */
    private static final java.util.Set<String> EXPECT = java.util.Arrays.stream(System.getProperty("immersive_tanning.smoketest.expect", "").split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Map<String, Object> checks = new LinkedHashMap<>();
    private static final long START = Util.getMillis();
    private static int titleTicks;
    private static int worldTicks = -1;
    private static boolean worldRequested;
    private static boolean finished;
    private static int settledAt = -1;
    private static int quietTicks;
    private static BlockPos base;

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (OUTPUT == null || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.player == null || mc.level == null) {
                if (mc.screen instanceof TitleScreen && ++titleTicks == 1) mc.getWindow().setWindowed(1280, 720);
                if (mc.screen instanceof TitleScreen && titleTicks == 40 && !worldRequested) {
                    worldRequested = true;
                    checks.put("title_ms", Util.getMillis() - START);
                    createWorld(mc);
                }
                return;
            }
            worldTicks++;
            if (settledAt < 0 && !settle(mc)) return;
            step(mc, worldTicks - settledAt + 40);
        } catch (Exception e) {
            ImmersiveTanning.LOGGER.error("SMOKETEST failed at world tick {}", worldTicks, e);
            checks.put("agent_exception", e.toString());
            finish(mc, false, "agent exception");
        }
    }

    /**
     * Packs open their own screens on first join (Ultima: the Origins heritage choice), and staging and shots need them
     * gone: waits until no screen has been open for 2 s. Origins' Select button takes the offered origin as a player
     * would; any other screen is closed. Without a pack this settles after 40 ticks.
     */
    private static boolean settle(Minecraft mc) {
        if (worldTicks % 5 == 0) settlePackScreen(mc);
        quietTicks = mc.screen == null ? quietTicks + 1 : 0;
        boolean quiet = worldTicks >= 40 && quietTicks >= 40;
        if (quiet || worldTicks >= 1200) {
            settledAt = worldTicks;
            checks.put("pack_screens_settled_ticks", settledAt);
            log("pack screens " + (quiet ? "settled" : "still open") + " after " + worldTicks + " ticks");
            return true;
        }
        return false;
    }

    private static void settlePackScreen(Minecraft mc) {
        Screen screen = mc.screen;
        if (screen == null) return;
        if (screen.getClass().getName().contains(".origins.")) {
            for (net.minecraft.client.gui.components.events.GuiEventListener child : screen.children()) {
                if (child instanceof net.minecraft.client.gui.components.Button button && button.active
                        && "Select".equals(button.getMessage().getString())) {
                    log("choosing the offered origin on " + screen.getClass().getName());
                    button.onPress();
                    return;
                }
            }
            return;
        }
        log("closing pack screen " + screen.getClass().getName());
        mc.setScreen(null);
    }

    private static void step(Minecraft mc, int t) {
        if (!EXPECT.isEmpty() && t > 40 && t <= 236 && t % 5 == 1) settlePackScreen(mc);
        switch (t) {
            case 60 -> {
                checks.put("world_ms", Util.getMillis() - START);
                runChecks(mc);
                stageWorld(mc);
                if (!EXPECT.isEmpty()) PackChecks.data(checks, EXPECT, mc.level.registryAccess());
            }
            case 62 -> {
                IntegratedServer server = mc.getSingleplayerServer();
                if (server != null && base != null && !EXPECT.isEmpty()) server.execute(() -> PackChecks.startKills(server, base, EXPECT));
            }
            case 160 -> {
                IntegratedServer server = mc.getSingleplayerServer();
                if (server != null && !EXPECT.isEmpty()) server.execute(() -> PackChecks.collectKills(server, checks));
            }
            case 70 -> camera(mc, 2.5, 5.5, 180F, 24F);
            case 110 -> {
                mc.options.hideGui = true;
                shot(mc, "smoketest_world");
            }
            case 112 -> camera(mc, 0.0, 2.6, 180F, 18F);
            case 135 -> shot(mc, "smoketest_racks");
            case 137 -> camera(mc, 3.0, 1.0, 180F, 20F);
            case 155 -> shot(mc, "smoketest_rack_hardened");
            case 157 -> camera(mc, -1.0, 1.0, 180F, 20F);
            case 175 -> shot(mc, "smoketest_rack_supple");
            case 177 -> camera(mc, -3.0, 0.4, 180F, 38F);
            case 195 -> shot(mc, "smoketest_rack_raw");
            case 197 -> camera(mc, 7.0, 0.3, 180F, 48F);
            case 215 -> shot(mc, "smoketest_vats");
            case 217 -> camera(mc, 8.0, 3.7, 180F, 40F);
            case 235 -> shot(mc, "smoketest_benches");
            case 237 -> {
                mc.options.hideGui = false;
                mc.setScreen(new FleshingScreen(new OpenFleshingPacket(999_001, BlockPos.ZERO, HideSize.LARGE, 42L, 25_000, 0, null)));
            }
            case 241 -> scrape(mc);
            case 252 -> shot(mc, "smoketest_fleshing");
            case 254 -> {
                if (mc.screen instanceof FleshingScreen s) {
                    s.onResult(new SessionResultPacket(999_001, TanningSession.Kind.FLESHING, 80, 0.93F, 0.97F, 0.22F, false));
                }
            }
            case 264 -> shot(mc, "smoketest_fleshing_result");
            case 266 -> mc.setScreen(new FinishSelectScreen(new OpenFinishPacket(BlockPos.ZERO,
                    new HideData(HideSize.LARGE, 80, HideTraitLoader.get(ImmersiveTanning.id("heatproof"))), Finish.SUPPLE)));
            case 278 -> shot(mc, "smoketest_finish_select");
            case 280 -> mc.setScreen(new LeatherworkScreen(benchOffer()));
            case 292 -> shot(mc, "smoketest_bench_select");
            case 294 -> {
                if (mc.screen instanceof LeatherworkScreen s) {
                    s.onStart(new StitchStartPacket(999_002, SeamPatterns.get(ImmersiveTanning.id("chestplate")), 1500,
                            new ItemStack(Items.LEATHER_CHESTPLATE), Finish.HARNESS, null, 80));
                }
            }
            case 309 -> shot(mc, "smoketest_bench_cutting");
            case 324, 333, 342 -> {
                if (mc.screen instanceof LeatherworkScreen s) s.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE, 0, 0);
            }
            case 345 -> shot(mc, "smoketest_bench_stitching");
            case 347 -> {
                if (mc.screen instanceof LeatherworkScreen s) {
                    s.onResult(new SessionResultPacket(999_002, TanningSession.Kind.STITCHING, 88, 0F, 0F, 0F, false));
                }
            }
            case 360 -> shot(mc, "smoketest_bench_result");
            case 362 -> mc.setScreen(new TooltipSheet());
            case 374 -> shot(mc, "smoketest_tooltips");
            case 376 -> mc.setScreen(new InventoryScreen(mc.player));
            case 388 -> shot(mc, "smoketest_inventory");
            case 390 -> {
                mc.setScreen(null);
                if (SmokeJeiPlugin.runtime != null) {
                    SmokeJeiPlugin.runtime.getRecipesGui().showTypes(List.of(TanneryCategory.TYPE));
                    checks.put("jei_runtime", true);
                } else {
                    checks.put("jei_runtime", false);
                }
            }
            case 406 -> shot(mc, "smoketest_jei_tannery");
            case 408 -> {
                if (SmokeJeiPlugin.runtime != null) SmokeJeiPlugin.runtime.getRecipesGui().showTypes(List.of(LeatherworkingCategory.TYPE));
            }
            case 424 -> shot(mc, "smoketest_jei_leatherworking");
            case 426 -> {
                if (SmokeJeiPlugin.runtime != null) {
                    var focus = SmokeJeiPlugin.runtime.getJeiHelpers().getFocusFactory().createFocus(
                            mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT, mezz.jei.api.constants.VanillaTypes.ITEM_STACK, new ItemStack(Items.BOOK));
                    SmokeJeiPlugin.runtime.getRecipesGui().show(focus);
                }
            }
            case 442 -> shot(mc, "smoketest_jei_book");
            case 444 -> {
                mc.setScreen(null);
                verifyWorld(mc);
            }
            case 454 -> {
                checks.put("art_models", ArtSheet.checkModels(mc));
                mc.setScreen(new ArtSheet());
            }
            case 466 -> shot(mc, "smoketest_art");
            case 478 -> finish(mc, allPassed(), "complete");
            default -> {
            }
        }
    }

    private static void createWorld(Minecraft mc) {
        String name = "SmokeTest" + System.currentTimeMillis();
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(),
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20260924L, false, false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }

    private static void runChecks(Minecraft mc) {
        TanningData data = TanningData.current();
        checks.put("summary", data.summary());
        checks.put("bench_recipes_server", data.recipes().size() >= 6);
        checks.put("bench_recipes_client", mc.level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.LEATHERWORKING.get()).size() >= 6);
        checks.put("chestplate_crafting_disabled_client", mc.level.getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation("minecraft", "leather_chestplate")).isEmpty());
        checks.put("seam_patterns", SeamPatterns.has(ImmersiveTanning.id("saddle")));
        checks.put("hide_traits", HideTraitLoader.all().size());
        ItemStack binding = new ItemStack(ModItems.BINDING_LEATHER.get());
        checks.put("client_ingredient_takes_binding", net.minecraft.world.item.crafting.Ingredient.of(Items.LEATHER).test(binding)
                && !net.minecraft.world.item.crafting.Ingredient.of(Items.LEATHER).test(new ItemStack(Items.LEATHER)));
        checks.put("client_book_recipe_takes_binding", mc.level.getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation("minecraft", "book"))
                .map(r -> r.getIngredients().stream().anyMatch(i -> i.test(binding))).orElse(false));
        checks.put("client_rabbit_leather_makes_binding", mc.level.getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation("minecraft", "leather"))
                .map(r -> r.getResultItem(mc.level.registryAccess()).is(ModItems.BINDING_LEATHER.get())).orElse(false));
    }

    private static void stageWorld(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            level.setDayTime(6000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            base = player.blockPosition();
            for (int x = -8; x <= 10; x++) for (int z = -8; z <= 8; z++) {
                level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
                for (int y = 0; y <= 4; y++) level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
            TraitRef tough = HideTraitLoader.get(ImmersiveTanning.id("tough"));
            BlockState rack = ModBlocks.TANNERS_RACK.get().defaultBlockState().setValue(TannersRackBlock.FACING, Direction.SOUTH);
            place(level, base.offset(-3, 0, -2), rack);
            ((TannersRackBlockEntity) level.getBlockEntity(base.offset(-3, 0, -2))).mountRaw(HideData.raw(HideSize.LARGE, null));
            Finish[] finishes = {Finish.SUPPLE, Finish.HARNESS, Finish.HARDENED};
            float[] progress = {0.25F, 0.7F, 1F};
            for (int i = 0; i < 3; i++) {
                BlockPos pos = base.offset(-1 + i * 2, 0, -2);
                place(level, pos, rack);
                TannersRackBlockEntity be = (TannersRackBlockEntity) level.getBlockEntity(pos);
                be.mountWet(new HideData(HideSize.LARGE, 80, i == 2 ? tough : null), finishes[i]);
                be.advance(Math.round(ServerConfig.get(ServerConfig.DRYING_TICKS) * progress[i]));
            }
            BlockPos vatPos = base.offset(6, 0, -2);
            place(level, vatPos, ModBlocks.TANNERS_VAT.get().defaultBlockState());
            TannersVatBlockEntity vat = (TannersVatBlockEntity) level.getBlockEntity(vatPos);
            vat.fillWater();
            vat.addTannin(8);
            vat.insertHide(new HideData(HideSize.LARGE, 80, null));
            vat.advance(ServerConfig.get(ServerConfig.TANNING_TICKS));
            vat.insertHide(new HideData(HideSize.MEDIUM, 60, null));
            vat.insertHide(new HideData(HideSize.SMALL, 90, null));
            BlockPos emptyVat = base.offset(8, 0, -2);
            place(level, emptyVat, ModBlocks.TANNERS_VAT.get().defaultBlockState());
            BlockPos benchPos = base.offset(6, 0, 2);
            place(level, benchPos, ModBlocks.LEATHERWORKERS_BENCH.get().defaultBlockState().setValue(LeatherworkersBenchBlock.FACING, Direction.SOUTH));
            ((LeatherworkersBenchBlockEntity) level.getBlockEntity(benchPos)).beginSession(player.getUUID(), List.of(),
                    new ItemStack(Items.LEATHER_CHESTPLATE), Finish.HARNESS.leatherColor());
            ((LeatherworkersBenchBlockEntity) level.getBlockEntity(benchPos)).unlock();
            place(level, base.offset(8, 0, 2), ModBlocks.LEATHERWORKERS_BENCH.get().defaultBlockState().setValue(LeatherworkersBenchBlock.FACING, Direction.SOUTH));

            player.getInventory().clearContent();
            player.getInventory().add(new ItemStack(ModItems.TANNERS_KNIFE.get()));
            player.getInventory().add(new ItemStack(ModItems.AWL.get()));
            for (HideSize size : HideSize.values()) player.getInventory().add(new ItemStack(ModItems.rawHide(size), 3));
            player.getInventory().add(ModItems.SCRAPED_HIDE.get().create(HideSize.LARGE, 80, null));
            player.getInventory().add(ModItems.WET_TANNED_HIDE.get().create(HideSize.MEDIUM, 70, null));
            for (Finish finish : Finish.values()) player.getInventory().add(ModItems.leather(finish).create(80, null, 6));
            player.getInventory().add(new ItemStack(ModItems.BARK.get(), 12));
            player.getInventory().add(new ItemStack(ModItems.LEATHER_SCRAPS.get(), 5));
            player.getInventory().add(new ItemStack(ModItems.BINDING_LEATHER.get(), 8));
            player.getInventory().add(leatherwork(Items.LEATHER_BOOTS, Finish.SUPPLE, 86, 91, null));
            player.getInventory().add(leatherwork(Items.LEATHER_CHESTPLATE, Finish.HARNESS, 96, 94, null));
            player.getInventory().add(leatherwork(Items.LEATHER_HELMET, Finish.HARDENED, 70, 55, tough));
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, leatherwork(Items.LEATHER_BOOTS, Finish.SUPPLE, 86, 91, null));
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, leatherwork(Items.LEATHER_CHESTPLATE, Finish.HARNESS, 96, 94, null));
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, leatherwork(Items.LEATHER_HELMET, Finish.HARDENED, 70, 55, tough));
        });
    }

    private static void verifyWorld(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || base == null) return;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            TannersRackBlockEntity dry = (TannersRackBlockEntity) level.getBlockEntity(base.offset(3, 0, -2));
            checks.put("hardened_rack_dry_and_settled", dry != null && dry.isDry() && dry.isSettled());
            TannersVatBlockEntity vat = (TannersVatBlockEntity) level.getBlockEntity(base.offset(6, 0, -2));
            checks.put("vat_one_hide_ready", vat != null && vat.finishedCount() == 1);
        });
    }

    private static ItemStack leatherwork(net.minecraft.world.item.Item item, Finish finish, int integrity, int workmanship, TraitRef trait) {
        ItemStack stack = new ItemStack(item);
        new LeatherworkData(finish, integrity, workmanship, trait, "Otectus").apply(stack);
        if (item instanceof net.minecraft.world.item.DyeableLeatherItem dyeable) dyeable.setColor(stack, finish.leatherColor());
        return stack;
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlockAndUpdate(pos, state);
    }

    private static OpenBenchPacket benchOffer() {
        TraitRef tough = HideTraitLoader.get(ImmersiveTanning.id("tough"));
        return new OpenBenchPacket(999_002, BlockPos.ZERO, List.of(
                new OpenBenchPacket.Entry(ImmersiveTanning.id("leatherworking/leather_boots"), new ItemStack(Items.LEATHER_BOOTS), 4, Finish.SUPPLE, null, 80, List.of()),
                new OpenBenchPacket.Entry(ImmersiveTanning.id("leatherworking/leather_chestplate"), new ItemStack(Items.LEATHER_CHESTPLATE), 8, Finish.HARNESS, null, 90, List.of()),
                new OpenBenchPacket.Entry(ImmersiveTanning.id("leatherworking/leather_helmet"), new ItemStack(Items.LEATHER_HELMET), 5, Finish.HARDENED, tough, 70, List.of()),
                new OpenBenchPacket.Entry(ImmersiveTanning.id("leatherworking/saddle"), new ItemStack(Items.SADDLE), 5, Finish.HARNESS, null, 90,
                        List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.STRING, 2))),
                new OpenBenchPacket.Entry(ImmersiveTanning.id("leatherworking/leather_horse_armor"), new ItemStack(Items.LEATHER_HORSE_ARMOR), 7, Finish.HARDENED, null, 60, List.of())));
    }

    /** Drags the knife through the hide with real mouse events, like a player would. */
    private static void scrape(Minecraft mc) {
        if (!(mc.screen instanceof FleshingScreen screen)) return;
        int[] area = screen.workArea();
        for (int row = 0; row < 5; row++) {
            double y = area[1] + area[3] * (0.3 + row * 0.08);
            double x0 = area[0] + area[2] * 0.25;
            double x1 = area[0] + area[2] * 0.62;
            screen.mouseClicked(x0, y, 0);
            for (int i = 1; i <= 30; i++) {
                double x = x0 + (x1 - x0) * i / 30.0;
                screen.mouseDragged(x, y, 0, (x1 - x0) / 30.0, 0);
            }
            screen.mouseReleased(x1, y, 0);
        }
        screen.mouseMoved(area[0] + area[2] * 0.7, area[1] + area[3] * 0.6);
    }

    private static void camera(Minecraft mc, double dx, double dz, float yaw, float pitch) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || base == null) return;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(server.overworld(), base.getX() + 0.5 + dx, base.getY() + 1.2, base.getZ() + 0.5 + dz, yaw, pitch);
        });
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> log("screenshot " + name));
    }

    private static boolean allPassed() {
        for (Object value : checks.values()) {
            if (value instanceof Boolean b && !b) return false;
        }
        return !checks.containsKey("agent_exception");
    }

    private static void finish(Minecraft mc, boolean pass, String reason) {
        finished = true;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pass", pass);
        result.put("reason", reason);
        result.put("checks", checks);
        try {
            Files.createDirectories(Path.of(OUTPUT));
            Files.writeString(Path.of(OUTPUT, "smoketest-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            ImmersiveTanning.LOGGER.error("SMOKETEST could not write result", e);
        }
        log("finished: pass=" + pass + " (" + reason + ")");
        mc.stop();
    }

    private static void log(String message) {
        ImmersiveTanning.LOGGER.info("SMOKETEST {}", message);
    }

    /** Renders the tooltips of a few representative stacks side by side. */
    private static final class TooltipSheet extends Screen {
        private TooltipSheet() {
            super(Component.literal("Tooltips"));
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            renderBackground(g);
            TraitRef tough = HideTraitLoader.get(ImmersiveTanning.id("tough"));
            ItemStack[] stacks = {
                    leatherwork(Items.LEATHER_BOOTS, Finish.SUPPLE, 86, 91, null),
                    leatherwork(Items.LEATHER_CHESTPLATE, Finish.HARNESS, 96, 94, null),
                    ModItems.leather(Finish.HARDENED).create(70, tough, 3),
                    ModItems.WET_TANNED_HIDE.get().create(HideSize.LARGE, 80, tough)};
            int[][] at = {{6, 16}, {width / 2 + 6, 16}, {6, height / 2 + 8}, {width / 2 + 6, height / 2 + 8}};
            for (int i = 0; i < stacks.length; i++) {
                g.renderComponentTooltip(font, Screen.getTooltipFromItem(minecraft, stacks[i]), at[i][0], at[i][1]);
            }
        }
    }

    private ClientSmokeTest() {}
}
