package com.otectus.immersivetanning.smoketest;

import com.google.gson.GsonBuilder;
import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.block.LeatherworkersBenchBlock;
import com.otectus.immersivetanning.block.TannersRackBlock;
import com.otectus.immersivetanning.block.TannersVatBlock;
import com.otectus.immersivetanning.blockentity.LeatherworkersBenchBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersVatBlockEntity;
import com.otectus.immersivetanning.client.compat.jei.LeatherworkingCategory;
import com.otectus.immersivetanning.client.compat.jei.TanneryCategory;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.effect.LeatherworkPolicy;
import com.otectus.immersivetanning.leather.LeatherworkData;
import com.otectus.immersivetanning.leather.LeatherworkProfile;
import com.otectus.immersivetanning.minigame.SeamPatterns;
import com.otectus.immersivetanning.minigame.StitchPattern;
import com.otectus.immersivetanning.recipe.TanningData;
import com.otectus.immersivetanning.registry.ModBlocks;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModRecipeTypes;
import com.otectus.immersivetanning.trait.HideTraitLoader;
import com.otectus.immersivetanning.trait.TraitRef;
import com.otectus.immersivetanning.work.LeatherworkJob;
import com.otectus.immersivetanning.work.MaterialLedger;
import com.otectus.immersivetanning.work.WorkPattern;
import com.otectus.immersivetanning.work.WorkPatterns;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Drives a dev client from the title screen into a fresh flat world, checks that Immersive Tanning built its data,
 * stages every station state, tours each in the focused view with real input ({@link FocusTour}), photographs the
 * screens that remain (tooltips, inventory, JEI), plays the tannery through ({@link TanneryPlaythrough}), writes
 * {@code smoketest-result.json} and quits. Screenshots land in {@code run/screenshots/smoketest_*.png}.
 */
@Mod.EventBusSubscriber(modid = SmokeTestAgent.MOD_ID, value = Dist.CLIENT)
public final class ClientSmokeTest {
    private static final String OUTPUT = System.getProperty(SmokeTestAgent.OUTPUT_PROPERTY);
    /** Pack check groups requested by the Ultima harness (see PackChecks); empty in the dev smoke test. */
    private static final java.util.Set<String> EXPECT = java.util.Arrays.stream(System.getProperty("immersive_tanning.smoketest.expect", "").split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    /** A dedicated server to join instead of a fresh world (the server check, -PjoinServer=host:port). */
    private static final String JOIN = System.getProperty("immersive_tanning.smoketest.join");
    private static final Map<String, Object> checks = new LinkedHashMap<>();
    private static final long START = Util.getMillis();
    private static int titleTicks;
    private static int worldTicks = -1;
    private static boolean worldRequested;
    private static boolean finished;
    private static Integer originalGuiScale;
    private static Boolean originalVsync;
    private static Integer originalFrameLimit;
    private static int settledAt = -1;
    private static int quietTicks;
    /** Ticks the timeline waited while the focus tour ran. */
    private static int paused;
    private static BlockPos base;

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (OUTPUT == null || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.player == null || mc.level == null) {
                if (mc.screen instanceof TitleScreen && ++titleTicks == 1) {
                    originalVsync = mc.options.enableVsync().get();
                    originalFrameLimit = mc.options.framerateLimit().get();
                    mc.options.enableVsync().set(false);
                    mc.options.framerateLimit().set(120);
                    mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                    mc.getToasts().clear();
                    sizeWindow(mc);
                }
                if (mc.screen instanceof TitleScreen && titleTicks == 40 && !worldRequested) {
                    worldRequested = true;
                    checks.put("title_ms", Util.getMillis() - START);
                    log("title screen reached");
                    if (JOIN != null) ServerJoinCheck.connect(mc, JOIN);
                    else createWorld(mc);
                }
                return;
            }
            worldTicks++;
            if (JOIN != null) {
                if (ServerJoinCheck.tick(mc, checks)) finish(mc, allPassed(), "joined " + JOIN);
                return;
            }
            if (settledAt < 0 && !settle(mc)) return;
            if (StoreScreenshots.enabled()) {
                StoreScreenshots.tick(mc);
                return;
            }
            if (FocusTour.running()) {
                FocusTour.tick();
                paused++;
                return;
            }
            step(mc, worldTicks - settledAt + 40 - paused);
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
            case 64 -> {
                IntegratedServer server = mc.getSingleplayerServer();
                if (server != null && base != null && !EXPECT.isEmpty()) server.execute(() -> PackChecks.world(server, base, EXPECT, checks));
            }
            case 160 -> {
                IntegratedServer server = mc.getSingleplayerServer();
                if (server != null && !EXPECT.isEmpty()) server.execute(() -> PackChecks.collectKills(server, checks));
            }
            case 233 -> {
                // Genetic Animals' animals cannot be hurt for 100 ticks after they spawn (staged at tick 64).
                IntegratedServer server = mc.getSingleplayerServer();
                if (EXPECT.contains("geneticanimals") && net.minecraftforge.fml.ModList.get().isLoaded("jei")) GeneticAnimalsChecks.jei(checks);
                if (server != null && EXPECT.contains("geneticanimals")) server.execute(() -> GeneticAnimalsChecks.kill(server, checks));
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
            // Every station in the focused view with real input; the timeline waits while the tour runs.
            case 237 -> {
                mc.options.hideGui = false;
                FocusTour.start(base, checks);
            }
            case 240 -> mc.setScreen(new TooltipSheet());
            case 252 -> shot(mc, "smoketest_tooltips");
            case 254 -> mc.setScreen(new InventoryScreen(mc.player));
            case 266 -> shot(mc, "smoketest_inventory");
            case 268 -> {
                mc.setScreen(null);
                if (SmokeJeiPlugin.runtime != null) {
                    SmokeJeiPlugin.runtime.getRecipesGui().showTypes(List.of(TanneryCategory.TYPE));
                    checks.put("jei_runtime", true);
                } else {
                    checks.put("jei_runtime", false);
                }
            }
            case 284 -> shot(mc, "smoketest_jei_tannery");
            case 286 -> {
                if (SmokeJeiPlugin.runtime != null) SmokeJeiPlugin.runtime.getRecipesGui().showTypes(List.of(LeatherworkingCategory.TYPE));
            }
            case 302 -> shot(mc, "smoketest_jei_leatherworking");
            case 304 -> {
                if (SmokeJeiPlugin.runtime != null) {
                    var focus = SmokeJeiPlugin.runtime.getJeiHelpers().getFocusFactory().createFocus(
                            mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT, mezz.jei.api.constants.VanillaTypes.ITEM_STACK, new ItemStack(Items.BOOK));
                    SmokeJeiPlugin.runtime.getRecipesGui().show(focus);
                }
            }
            case 320 -> shot(mc, "smoketest_jei_book");
            case 322 -> {
                mc.setScreen(null);
                verifyWorld(mc);
            }
            case 332 -> {
                checks.put("art_models", ArtSheet.checkModels(mc));
                checks.put("panel_uvs_continuous", ArtSheet.checkPanelUvs());
                checks.put("hide_normals_face_work", ArtSheet.checkHideNormals());
                checks.put("working_arms_keep_vanilla_dimensions", ArtSheet.checkArmDimensions());
                mc.setScreen(new ArtSheet());
            }
            case 344 -> shot(mc, "smoketest_art");
            // The knife strips a log through the real client and server: the client only predicts the strip, the
            // server makes it once and pays the bark and the wear; a stripped log and stone give nothing.
            case 350 -> {
                mc.setScreen(null);
                knifeStage(mc);
            }
            case 356 -> knifeUse(mc, KNIFE_LOG, "knife_client_predicts_without_changing_the_block");
            case 362 -> knifeUse(mc, KNIFE_LOG, null);
            case 364 -> knifeUse(mc, KNIFE_STONE, null);
            case 370 -> knifeCheck(mc);
            // A scale-backed piece names its profile and its attribute lines carry the scale-backed policy's numbers.
            case 372 -> {
                scaleBackedTooltip(mc);
                mc.setScreen(new ScaleBackedSheet());
            }
            case 384 -> shot(mc, "smoketest_tooltips_scale_backed");
            // JEI finds the chestplate's bench recipe (dragon scales in the pack, leather here) and no crafting recipe.
            case 388 -> {
                mc.setScreen(null);
                jeiBenchOutput(mc);
            }
            case 404 -> shot(mc, "smoketest_jei_bench_output");
            // The whole tannery through the real client and network; the dev client has no Farmer's Delight.
            case 408 -> {
                mc.setScreen(null);
                if (EXPECT.isEmpty()) TanneryPlaythrough.start(mc, base, checks);
                else finish(mc, allPassed(), "complete");
            }
            default -> {
                if (t > 408 && TanneryPlaythrough.tick(mc)) finish(mc, allPassed(), "complete");
            }
        }
    }

    /**
     * A landscape 1280x720 window. A tiling Wayland compositor ignores the requested size, so on Hyprland this
     * client's own window (matched by process id) is floated and sized; nothing else on the desktop is touched.
     */
    private static void sizeWindow(Minecraft mc) {
        mc.getWindow().setWindowed(1280, 720);
        if (System.getenv("HYPRLAND_INSTANCE_SIGNATURE") == null) return;
        String target = "window = \"pid:" + ProcessHandle.current().pid() + "\"";
        Thread thread = new Thread(() -> {
            // Hyprland 0.55+ takes Lua dispatchers.
            for (String dispatch : new String[]{"hl.dsp.window.float({ action = \"enable\", " + target + " })",
                    "hl.dsp.window.resize({ x = 1280, y = 720, " + target + " })", "hl.dsp.window.move({ x = 40, y = 40, " + target + " })"}) {
                try {
                    new ProcessBuilder("hyprctl", "dispatch", dispatch).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .start().waitFor();
                } catch (IOException e) {
                    log("hyprctl unavailable: " + e.getMessage());
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "smoketest-window");
        thread.setDaemon(true);
        thread.start();
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
        checks.put("seam_patterns", SeamPatterns.has(ImmersiveTanning.id("saddle")) && SeamPatterns.has(ImmersiveTanning.id("strap")));
        checks.put("work_patterns", WorkPatterns.ids().size() >= 9 && WorkPatterns.get(ImmersiveTanning.id("boots")) != null
                && WorkPatterns.failures().isEmpty());
        checks.put("work_patterns_detail", WorkPatterns.ids().stream().map(Object::toString).sorted().toList() + " failures " + WorkPatterns.failures());
        // A piece that is neither worn nor wears out says its finish is a record; worn armor does not.
        String recordOnly = Component.translatable("tooltip.immersive_tanning.record_only").getString();
        boolean saddleSays = leatherwork(Items.SADDLE, Finish.HARNESS, 80, 85, null).getTooltipLines(mc.player, net.minecraft.world.item.TooltipFlag.NORMAL)
                .stream().anyMatch(c -> c.getString().equals(recordOnly));
        boolean bootsSay = leatherwork(Items.LEATHER_BOOTS, Finish.HARNESS, 80, 85, null).getTooltipLines(mc.player, net.minecraft.world.item.TooltipFlag.NORMAL)
                .stream().anyMatch(c -> c.getString().equals(recordOnly));
        checks.put("tooltip_record_only_on_saddle_not_boots", saddleSays && !bootsSay);
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
            BlockPos framePos = base.offset(FocusTour.FRAME_RACK);
            place(level, framePos, rack);
            HideData framed = new HideData(HideSize.LARGE, 85, null);
            ((TannersRackBlockEntity) level.getBlockEntity(framePos)).mountWet(ModItems.WET_TANNED_HIDE.get().create(framed), framed, Finish.HARNESS);
            BlockState vatState = ModBlocks.TANNERS_VAT.get().defaultBlockState().setValue(TannersVatBlock.FACING, Direction.SOUTH);
            BlockPos vatPos = base.offset(6, 0, -2);
            place(level, vatPos, vatState);
            TannersVatBlockEntity vat = (TannersVatBlockEntity) level.getBlockEntity(vatPos);
            vat.fillWater();
            vat.addTannin(8);
            vat.insertHide(new HideData(HideSize.LARGE, 80, null));
            vat.advance(ServerConfig.get(ServerConfig.TANNING_TICKS));
            vat.insertHide(new HideData(HideSize.MEDIUM, 60, null));
            vat.insertHide(new HideData(HideSize.SMALL, 90, null));
            BlockPos emptyVat = base.offset(8, 0, -2);
            place(level, emptyVat, vatState);
            BlockPos benchPos = base.offset(6, 0, 2);
            place(level, benchPos, ModBlocks.LEATHERWORKERS_BENCH.get().defaultBlockState().setValue(LeatherworkersBenchBlock.FACING, Direction.SOUTH));
            ((LeatherworkersBenchBlockEntity) level.getBlockEntity(benchPos)).setJob(stagedJob(player));
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

    /** A chestplate half stitched on the bench: every cut and seat made, the first half of the stitches pulled well. */
    private static LeatherworkJob stagedJob(ServerPlayer player) {
        return stagedJob(player, "chestplate", true);
    }

    /** Test fixture for photographing every gear pattern through the production bench renderer. */
    static LeatherworkJob stagedJob(ServerPlayer player, String name, boolean sewn) {
        WorkPattern pattern = WorkPatterns.get(ImmersiveTanning.id(name));
        if (pattern == null) pattern = WorkPatterns.generic();
        MaterialLedger ledger = new MaterialLedger();
        ledger.add(MaterialLedger.Role.LEATHER, ModItems.leather(Finish.HARNESS).create(80, null, 8));
        var item = switch (name) {
            case "boots" -> Items.LEATHER_BOOTS;
            case "helmet" -> Items.LEATHER_HELMET;
            case "leggings" -> Items.LEATHER_LEGGINGS;
            case "saddle" -> Items.SADDLE;
            case "horse_armor" -> Items.LEATHER_HORSE_ARMOR;
            case "strap" -> Items.LEAD;
            default -> Items.LEATHER_CHESTPLATE;
        };
        LeatherworkJob job = LeatherworkJob.craft(player.getUUID(), player.getGameProfile().getName(), ImmersiveTanning.id("leatherworking/leather_" + name),
                new ItemStack(item), Finish.HARNESS, null, 80, 8, ledger, pattern);
        if (!sewn) return job;
        for (int c = 0; c < pattern.cuts().size(); c++) {
            float[] path = pattern.cutPath(c);
            int n = 80;
            float[] xs = new float[n + 1];
            float[] ys = new float[n + 1];
            for (int i = 0; i <= n; i++) {
                float[] p = StitchPattern.pointAt(path, i / (float) n);
                xs[i] = p[0];
                ys[i] = p[1];
            }
            job.cut(true, true, xs, ys, 1F);
        }
        for (int p = 0; p < pattern.panels().size(); p++) {
            WorkPattern.Placement target = pattern.placement(p);
            job.place(p, target.x(), target.y(), target.rot(), 1F);
        }
        for (int a = 0; a < job.stitchCount() / 2; a++) {
            float[] f = job.stitchFrame(a);
            float pull = pattern.joinOf(a).pullIdeal();
            job.puncture(a, f[4], f[5], 1F);
            job.releaseThread(f[4] + f[6] * pull, f[5] + f[7] * pull, false);
        }
        return job;
    }

    /** Knife stage positions, relative to the staged floor's origin (a free corner, within reach of the player). */
    private static final BlockPos KNIFE_LOG = new BlockPos(-6, 0, 5);
    private static final BlockPos KNIFE_STONE = new BlockPos(-4, 0, 5);
    private static volatile int knifeBark;

    /** A survival player beside an oak log lying east-west and a stone block, the knife in hand. */
    private static void knifeStage(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || base == null) return;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            player.setGameMode(GameType.SURVIVAL);
            player.teleportTo(level, base.getX() - 4.5, base.getY(), base.getZ() + 3.5, 0F, 45F);
            level.setBlockAndUpdate(base.offset(KNIFE_LOG), Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
            level.setBlockAndUpdate(base.offset(KNIFE_STONE), Blocks.STONE.defaultBlockState());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TANNERS_KNIFE.get()));
            knifeBark = player.getInventory().countItem(ModItems.BARK.get());
        });
    }

    /** A right-click on the block with the held knife, through the client's own interaction path. */
    private static void knifeUse(Minecraft mc, BlockPos relative, String predictionCheck) {
        if (base == null || mc.gameMode == null || mc.player == null) return;
        BlockPos pos = base.offset(relative);
        boolean log = mc.level.getBlockState(pos).is(Blocks.OAK_LOG);
        InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        if (predictionCheck != null) {
            checks.put(predictionCheck, log && result.consumesAction() && mc.level.getBlockState(pos).is(Blocks.OAK_LOG));
        }
    }

    private static void knifeCheck(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || base == null) return;
        checks.put("knife_client_sees_the_stripped_log", mc.level.getBlockState(base.offset(KNIFE_LOG)).is(Blocks.STRIPPED_OAK_LOG));
        server.execute(() -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            BlockState log = level.getBlockState(base.offset(KNIFE_LOG));
            int bark = player.getInventory().countItem(ModItems.BARK.get()) - knifeBark;
            int wear = player.getMainHandItem().getDamageValue();
            checks.put("knife_strips_once_for_bark_and_wear", log.is(Blocks.STRIPPED_OAK_LOG) && log.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.X
                    && bark == ServerConfig.get(ServerConfig.BARK_PER_LOG) && wear == 1 && level.getBlockState(base.offset(KNIFE_STONE)).is(Blocks.STONE));
            checks.put("knife_detail", "log=" + log + ", bark=" + bark + ", wear=" + wear);
            player.setGameMode(GameType.CREATIVE);
        });
    }

    /**
     * A scale-backed chestplate: Ice and Fire's red one in the pack, a diamond stand-in here (not in the tag without the
     * test datapack, so the profile record makes it scale-backed).
     */
    private static ItemStack scaleBackedPiece(Finish finish, int integrity, int workmanship) {
        net.minecraft.world.item.Item dragon = ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("iceandfire", "armor_red_chestplate"));
        ItemStack stack = leatherwork(dragon == null || dragon == Items.AIR ? Items.DIAMOND_CHESTPLATE : dragon, finish, integrity, workmanship, null);
        stack.getOrCreateTagElement(LeatherworkData.ROOT).putString(LeatherworkProfile.KEY, "scale_backed");
        return stack;
    }

    /** The client tooltip of a Masterwork Hardened scale-backed piece against the policy's own numbers. */
    private static void scaleBackedTooltip(Minecraft mc) {
        ItemStack piece = scaleBackedPiece(Finish.HARDENED, 86, 91);
        LeatherworkPolicy policy = LeatherworkPolicy.of(piece);
        LeatherworkData data = LeatherworkData.get(piece).orElseThrow();
        net.minecraft.world.entity.EquipmentSlot slot = net.minecraft.world.entity.LivingEntity.getEquipmentSlotForItem(piece);
        double nativeArmor = 0;
        double nativeToughness = 0;
        for (var m : new ItemStack(piece.getItem()).getAttributeModifiers(slot).get(Attributes.ARMOR)) nativeArmor += m.getAmount();
        for (var m : new ItemStack(piece.getItem()).getAttributeModifiers(slot).get(Attributes.ARMOR_TOUGHNESS)) nativeToughness += m.getAmount();
        double efficacy = policy.efficacy(data);
        double bonus = policy.hardenedToughness() * efficacy;
        String armor = attributeLine(nativeArmor * efficacy + policy.hardenedArmor() * efficacy, Attributes.ARMOR);
        List<String> lines = piece.getTooltipLines(mc.player, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
        // Vanilla lists the piece's own toughness and the Hardened bonus apart; tooltip mods (Ultima has one) add them up.
        boolean toughness = lines.contains(attributeLine(nativeToughness + bonus, Attributes.ARMOR_TOUGHNESS))
                || lines.contains(attributeLine(bonus, Attributes.ARMOR_TOUGHNESS)) && (nativeToughness == 0 || lines.contains(attributeLine(nativeToughness, Attributes.ARMOR_TOUGHNESS)));
        checks.put("scale_backed_tooltip_names_the_profile", policy.isScaleBacked()
                && lines.contains(Component.translatable("tooltip.immersive_tanning.scale_backed").getString()));
        checks.put("scale_backed_tooltip_shows_the_policy_armor", lines.contains(armor) && toughness);
        checks.put("scale_backed_tooltip_lines", lines);
    }

    private static String attributeLine(double amount, net.minecraft.world.entity.ai.attributes.Attribute attribute) {
        return Component.translatable("attribute.modifier.plus.0", ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(amount),
                Component.translatable(attribute.getDescriptionId())).getString();
    }

    private static void jeiBenchOutput(Minecraft mc) {
        if (SmokeJeiPlugin.runtime == null) {
            checks.put("jei_bench_output_not_crafting", false);
            return;
        }
        net.minecraft.world.item.Item dragon = ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("iceandfire", "armor_red_chestplate"));
        net.minecraft.world.item.Item item = dragon == null || dragon == Items.AIR ? Items.LEATHER_CHESTPLATE : dragon;
        var jei = SmokeJeiPlugin.runtime;
        var focus = jei.getJeiHelpers().getFocusFactory().createFocus(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT,
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, new ItemStack(item));
        List<com.otectus.immersivetanning.recipe.LeatherworkingRecipe> bench = jei.getRecipeManager().createRecipeLookup(LeatherworkingCategory.TYPE)
                .limitFocus(List.of(focus)).get().filter(r -> r.result().is(item)).toList();
        long crafting = jei.getRecipeManager().createRecipeLookup(mezz.jei.api.constants.RecipeTypes.CRAFTING).limitFocus(List.of(focus)).get().count();
        checks.put("jei_bench_output_not_crafting", bench.size() == 1 && crafting == 0);
        checks.put("jei_bench_output_detail", bench.stream().map(r -> r.getId() + ": " + r.leather() + " leather, "
                + r.auxiliary().stream().map(a -> a.count() + " " + a.displayStack().getHoverName().getString()).toList()).toList() + ", crafting recipes " + crafting);
        jei.getRecipesGui().show(focus);
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

    static void shot(Minecraft mc, String name) {
        mc.getToasts().clear();
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
        if (originalGuiScale != null) { mc.options.guiScale().set(originalGuiScale); mc.resizeDisplay(); }
        if (originalVsync != null) mc.options.enableVsync().set(originalVsync);
        if (originalFrameLimit != null) mc.options.framerateLimit().set(originalFrameLimit);
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

    /** Tooltips of scale-backed pieces: Masterwork Hardened and Standard Supple. */
    private static final class ScaleBackedSheet extends Screen {
        private ScaleBackedSheet() {
            super(Component.literal("Scale-backed tooltips"));
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            renderBackground(g);
            ItemStack[] stacks = {scaleBackedPiece(Finish.HARDENED, 86, 91), scaleBackedPiece(Finish.SUPPLE, 50, 50)};
            int[][] at = {{6, 16}, {width / 2 + 6, 16}};
            for (int i = 0; i < stacks.length; i++) {
                g.renderComponentTooltip(font, Screen.getTooltipFromItem(minecraft, stacks[i]), at[i][0], at[i][1]);
            }
        }
    }

    private ClientSmokeTest() {}
}
