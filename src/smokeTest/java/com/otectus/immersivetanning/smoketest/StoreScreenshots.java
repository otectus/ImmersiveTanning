package com.otectus.immersivetanning.smoketest;

import com.google.gson.GsonBuilder;
import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.block.LeatherworkersBenchBlock;
import com.otectus.immersivetanning.block.TannersRackBlock;
import com.otectus.immersivetanning.blockentity.LeatherworkersBenchBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersVatBlockEntity;
import com.otectus.immersivetanning.client.screen.FinishSelectScreen;
import com.otectus.immersivetanning.client.screen.FleshingScreen;
import com.otectus.immersivetanning.client.screen.LeatherworkScreen;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.minigame.FleshingSurface;
import com.otectus.immersivetanning.minigame.StitchRun;
import com.otectus.immersivetanning.network.packet.OpenBenchPacket;
import com.otectus.immersivetanning.registry.ModBlocks;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.trait.HideTraitLoader;
import com.otectus.immersivetanning.trait.TraitRef;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Opt-in CurseForge screenshot capture, confined to the test agent and enabled only by
 * {@code -Dimmersive_tanning.screenshots.output=<dir>}. It builds a tannery yard and drives real, server-scored
 * fleshing, finishing and stitching sessions, saving framebuffer captures without HUD, chat or toasts.
 */
@Mod.EventBusSubscriber(modid = SmokeTestAgent.MOD_ID, value = Dist.CLIENT)
public final class StoreScreenshots {
    private static final String OUTPUT = System.getProperty("immersive_tanning.screenshots.output");
    private static final int WIDTH = 1920, HEIGHT = 1080;
    private static final Map<String, Object> EVIDENCE = new LinkedHashMap<>();
    private static final List<String> SHOTS = new ArrayList<>();
    private static CompletableFuture<Void> pending;
    private static int step, action, baseY, pieceSlot = -1;
    private static long entered, started;
    private static boolean finished, renamed, fleshShot, stitchShot;
    private static volatile float lockYaw = Float.NaN, lockPitch;
    private static double cursorX = 0.3, cursorY = 0.3;
    private static BlockPos rawRack, emptyRack, bench, vat;
    // Fleshing driver: the current knife stroke in surface cells.
    private static float[] stroke;
    private static float strokeT;
    private static float lastCellX = 32, lastCellY = 24;

    static boolean enabled() { return OUTPUT != null; }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (enabled()) event.setCanceled(true);
    }

    /** The player profile is built from the session user when the world is created, so rename it on the title screen. */
    @SubscribeEvent
    public static void onScreen(ScreenEvent.Init.Post event) {
        if (!enabled() || renamed || !(event.getScreen() instanceof TitleScreen)) return;
        renamed = true;
        try {
            var name = net.minecraft.client.User.class.getDeclaredField("name");
            name.setAccessible(true);
            name.set(Minecraft.getInstance().getUser(), "Otectus");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not rename the showcase player", e);
        }
    }

    /** Stitches are timed per frame; client ticks are too coarse for the ring windows. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!enabled() || event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        lockView(mc);
        if (step != 7 || !(mc.screen instanceof LeatherworkScreen screen)) return;
        try {
            if (!field(screen, "stage").toString().equals("PLAYING")) return;
            StitchRun run = (StitchRun) field(screen, "run");
            if (run == null || run.isComplete()) return;
            int elapsed = (int) (Util.getMillis() - (long) field(screen, "clientStartMs"));
            if (!stitchShot && run.next() == 6 && elapsed >= run.time(6) - 260) {
                shot(mc, "stitching");
                stitchShot = true;
            }
            if (elapsed >= run.time(run.next()) - 4) {
                // One physical press per stitch: the gate ignores a key that is still held.
                screen.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0);
                screen.keyReleased(GLFW.GLFW_KEY_SPACE, 0, 0);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Stitch input failed", e);
        }
    }

    static void tick(Minecraft mc) throws Exception {
        if (finished) return;
        if (started == 0) started = Util.getMillis();
        if (Util.getMillis() - started > 240_000) throw new IllegalStateException("Store screenshots stalled at step " + step);
        mc.getToasts().clear();
        lockView(mc);
        if (mc.screen != null && !(mc.screen instanceof InventoryScreen)) placeCursor(mc);
        if (pending != null && !pending.isDone()) return;
        if (pending != null) { pending.join(); pending = null; }
        long age = Util.getMillis() - entered;
        switch (step) {
            case 0 -> {
                Files.createDirectories(Path.of(OUTPUT));
                baseY = mc.player.blockPosition().getY();
                rawRack = new BlockPos(-8, baseY, -3);
                emptyRack = new BlockPos(0, baseY, -3);
                vat = new BlockPos(2, baseY, -3);
                bench = new BlockPos(7, baseY, -5);
                mc.getWindow().setWindowed(WIDTH, HEIGHT);
                mc.options.guiScale().set(3);
                mc.resizeDisplay();
                mc.options.fov().set(70);
                mc.options.bobView().set(false);
                mc.options.renderDistance().set(12);
                mc.options.pauseOnLostFocus = false;
                mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0D);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                // World shots are hand-free (F1); screens still draw over the world.
                mc.options.hideGui = true;
                mc.getTutorial().setStep(TutorialSteps.NONE);
                server(mc, StoreScreenshots::buildYard);
                select(mc, 0);
                advance(1);
            }
            case 1 -> {
                if (mc.getWindow().getWidth() != WIDTH && age > 1500 && action == 0) { mc.getWindow().toggleFullScreen(); action++; }
                if (age > 6500) {
                    EVIDENCE.put("framebuffer", mc.getMainRenderTarget().width + "x" + mc.getMainRenderTarget().height);
                    shot(mc, "yard_wide");
                    server(mc, (level, player) -> look(player, -7.0, 1.55, -0.9, -7.6, 0.85, -2.6));
                    advance(2);
                }
            }
            case 2 -> {
                if (action == 0 && age > 2500) { shot(mc, "rack_raw"); action++; }
                if (action == 1 && age > 2800) { use(mc, rawRack); advance(3); }
            }
            case 3 -> {
                if (!(mc.screen instanceof FleshingScreen screen)) break;
                String stage = field(screen, "stage").toString();
                FleshingSurface surface = (FleshingSurface) field(screen, "surface");
                if (stage.equals("PLAYING")) {
                    flesh(screen, surface);
                    if (!fleshShot && surface.coverage() > .5F && stroke != null && strokeT > .4F) {
                        shot(mc, "fleshing");
                        fleshShot = true;
                    }
                    if (surface.coverage() >= .97F || age > 22_000) {
                        if (stroke != null) release(screen);
                        screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                    }
                } else if (stage.equals("RESULT")) {
                    if (action == 0) {
                        EVIDENCE.put("fleshing_coverage", surface.coverage());
                        EVIDENCE.put("fleshing_score", surface.score());
                        action++;
                        entered = Util.getMillis();
                    } else if (age > 1600) {
                        shot(mc, "fleshing_result");
                        mc.screen.onClose();
                        // Hang a fresh hide so the yard shots keep the whole progression on its racks.
                        server(mc, (level, player) -> ((TannersRackBlockEntity) level.getBlockEntity(rawRack)).mountRaw(HideData.raw(HideSize.LARGE, null)));
                        advance(4);
                    }
                }
            }
            case 4 -> {
                if (action == 0 && age > 400) {
                    server(mc, (level, player) -> look(player, 3.5, 2.5, -0.8, 3.5, 0.45, -2.8));
                    action++;
                }
                if (action == 1 && age > 3000) { shot(mc, "vats"); action++; }
                if (action == 2 && age > 3300) {
                    int wet = slotOf(mc, ModItems.WET_TANNED_HIDE.get());
                    select(mc, wet);
                    server(mc, (level, player) -> look(player, 0.4, 1.7, -0.8, 0.5, 0.8, -2.5));
                    action++;
                }
                if (action == 3 && age > 4300) { use(mc, emptyRack); advance(5); }
            }
            case 5 -> {
                if (mc.screen instanceof FinishSelectScreen screen) {
                    if (action == 0 && age > 1500) { shot(mc, "finish_select"); action++; }
                    if (action == 1 && age > 1800) {
                        screen.keyPressed(GLFW.GLFW_KEY_2, 0, 0);
                        screen.keyReleased(GLFW.GLFW_KEY_2, 0, 0);
                        action++;
                    }
                } else if (action >= 2) {
                    server(mc, (level, player) -> look(player, -3.4, 1.45, 0.75, -3.6, 0.75, -3.2));
                    advance(6);
                }
            }
            case 6 -> {
                if (action == 0 && age > 3000) { shot(mc, "racks"); action++; }
                if (action == 1 && age > 3300) {
                    select(mc, 1);
                    server(mc, (level, player) -> look(player, 7.3, 1.75, -2.6, 7.5, 0.8, -4.5));
                    action++;
                }
                if (action == 2 && age > 4600) { use(mc, bench); action++; }
                if (action == 3 && mc.screen instanceof LeatherworkScreen screen && age > 6200) {
                    shot(mc, "bench_select");
                    choose(screen, Items.LEATHER_CHESTPLATE, Finish.HARNESS);
                    advance(7);
                }
            }
            case 7 -> {
                if (!(mc.screen instanceof LeatherworkScreen screen)) break;
                String stage = field(screen, "stage").toString();
                if (action == 0 && stage.equals("READY") && age > 600) { shot(mc, "cutting"); action++; }
                if (stage.equals("RESULT")) {
                    if (action < 2) { action = 2; entered = Util.getMillis(); }
                    else if (age > 1700) {
                        shot(mc, "stitch_result");
                        mc.screen.onClose();
                        advance(8);
                    }
                }
            }
            case 8 -> {
                if (action == 0 && age > 800) {
                    pieceSlot = slotOf(mc, Items.LEATHER_CHESTPLATE);
                    if (pieceSlot < 0) throw new IllegalStateException("Stitching did not produce the tunic");
                    EVIDENCE.put("tunic", mc.player.getInventory().getItem(pieceSlot).getTag() == null ? "" : mc.player.getInventory().getItem(pieceSlot).getTag().toString());
                    hideJeiOverlay();
                    mc.options.guiScale().set(4);
                    mc.resizeDisplay();
                    final int slot = pieceSlot;
                    mc.setScreen(new InventoryScreen(mc.player) {
                        @Override public void render(GuiGraphics g, int mx, int my, float partial) {
                            int x = slot < 9 ? leftPos + 8 + slot * 18 + 8 : leftPos + 8 + (slot % 9) * 18 + 8;
                            int y = slot < 9 ? topPos + 142 + 8 : topPos + 84 + (slot / 9 - 1) * 18 + 8;
                            super.render(g, x, y, partial);
                        }
                    });
                    action++;
                }
                if (action == 1 && age > 2400) {
                    shot(mc, "tooltip");
                    mc.setScreen(null);
                    mc.options.guiScale().set(3);
                    mc.resizeDisplay();
                    select(mc, 0);
                    server(mc, (level, player) -> look(player, 1.6, 2.3, 5.2, -1.5, 0.7, -2.5));
                    advance(9);
                }
            }
            case 9 -> {
                if (age > 3000) {
                    shot(mc, "yard_outro");
                    EVIDENCE.put("shots", SHOTS);
                    EVIDENCE.put("pass", true);
                    Files.writeString(Path.of(OUTPUT, "result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(EVIDENCE));
                    finished = true;
                    mc.stop();
                }
            }
        }
    }

    // ------------------------------------------------------------------ fleshing driver

    /** Greedy knife: stroke along each patch's grain through the nearest flesh still on the hide, at a hand's pace. */
    private static void flesh(FleshingScreen screen, FleshingSurface surface) {
        int[] area = screen.workArea();
        if (stroke == null) {
            int best = -1;
            float bestScore = Float.MAX_VALUE;
            for (int y = 0; y < FleshingSurface.H; y++) for (int x = 0; x < FleshingSurface.W; x++) {
                if (!surface.isHide(x, y) || surface.thickness(x, y) <= 0F) continue;
                float d = (x - lastCellX) * (x - lastCellX) + (y - lastCellY) * (y - lastCellY);
                float score = d - surface.thickness(x, y) * 40F;
                if (score < bestScore) { bestScore = score; best = x + y * FleshingSurface.W; }
            }
            if (best < 0) return;
            float cx = best % FleshingSurface.W + .5F, cy = best / FleshingSurface.W + .5F;
            float g = surface.grain(surface.region((int) cx, (int) cy));
            float ux = (float) Math.cos(g), uy = (float) Math.sin(g);
            float back = reach(surface, cx, cy, -ux, -uy), ahead = reach(surface, cx, cy, ux, uy);
            stroke = new float[]{cx - ux * back, cy - uy * back, cx + ux * ahead, cy + uy * ahead};
            strokeT = 0F;
            screen.mouseClicked(sx(area, stroke[0]), sy(area, stroke[1]), 0);
        }
        float length = (float) Math.hypot(stroke[2] - stroke[0], stroke[3] - stroke[1]);
        float stepT = length <= 0F ? 1F : 1F / length;
        for (int i = 0; i < 4 && strokeT < 1F; i++) {
            strokeT = Math.min(1F, strokeT + stepT);
            float x = stroke[0] + (stroke[2] - stroke[0]) * strokeT, y = stroke[1] + (stroke[3] - stroke[1]) * strokeT;
            screen.mouseDragged(sx(area, x), sy(area, y), 0, 0, 0);
            cursorX = sx(area, x);
            cursorY = sy(area, y);
        }
        if (strokeT >= 1F) release(screen);
    }

    private static void release(FleshingScreen screen) {
        int[] area = screen.workArea();
        lastCellX = stroke[2];
        lastCellY = stroke[3];
        screen.mouseReleased(sx(area, stroke[2]), sy(area, stroke[3]), 0);
        stroke = null;
    }

    /** How far the knife can travel from a point in one direction without leaving the hide (at most 7 cells). */
    private static float reach(FleshingSurface surface, float x, float y, float ux, float uy) {
        float d = 0F;
        while (d < 7F) {
            float nx = x + ux * (d + .5F), ny = y + uy * (d + .5F);
            int cx = (int) Math.floor(nx), cy = (int) Math.floor(ny);
            if (cx < 0 || cy < 0 || cx >= FleshingSurface.W || cy >= FleshingSurface.H || !surface.isHide(cx, cy)) break;
            d += .5F;
        }
        return d;
    }

    private static double sx(int[] area, float cellX) { return area[0] + cellX / FleshingSurface.W * area[2]; }
    private static double sy(int[] area, float cellY) { return area[1] + cellY / FleshingSurface.H * area[3]; }

    // ------------------------------------------------------------------ bench

    /** Clicks the offered piece's row, as a player would. */
    private static void choose(LeatherworkScreen screen, net.minecraft.world.item.Item item, Finish finish) throws Exception {
        var offer = (OpenBenchPacket) field(screen, "offer");
        int index = -1;
        for (int i = 0; i < offer.entries().size(); i++) {
            var entry = offer.entries().get(i);
            if (entry.result().is(item) && entry.finish() == finish) { index = i; break; }
        }
        if (index < 0) throw new IllegalStateException("Bench did not offer " + item + " in " + finish);
        int scroll = Math.max(0, index - 2);
        setField(screen, "scroll", scroll);
        int left = (int) field(screen, "left"), top = (int) field(screen, "top");
        // Rows start under the title (top + 20) and are 26 px apart; aim for the middle of the 24 px button face.
        screen.mouseClicked(left + 40, top + 20 + (index - scroll) * 26 + 12, 0);
        EVIDENCE.put("bench_choice", index);
    }

    // ------------------------------------------------------------------ helpers

    private static int slotOf(Minecraft mc, net.minecraft.world.item.Item item) {
        for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).is(item)) return i;
        return -1;
    }

    private static void shot(Minecraft mc, String name) {
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            String file = String.format("%02d_%s.png", SHOTS.size() + 1, name);
            image.writeToFile(Path.of(OUTPUT, file));
            SHOTS.add(file);
        } catch (Exception e) {
            throw new IllegalStateException("Screenshot " + name + " failed", e);
        }
    }

    /** JEI's item list is noise in a tooltip shot; its toggle is internal, so reach it reflectively and carry on without it. */
    private static void hideJeiOverlay() {
        try {
            Object state = Class.forName("mezz.jei.common.Internal").getMethod("getClientToggleState").invoke(null);
            if ((boolean) state.getClass().getMethod("isOverlayEnabled").invoke(state))
                state.getClass().getMethod("toggleOverlayEnabled").invoke(state);
            EVIDENCE.put("jei_overlay_hidden", true);
        } catch (Exception | LinkageError e) {
            EVIDENCE.put("jei_overlay_hidden", e.toString());
        }
    }

    /** Pins the view to the last scripted camera so pointer motion reaching the window cannot turn the shot. */
    private static void lockView(Minecraft mc) {
        var player = mc.player;
        if (player == null || Float.isNaN(lockYaw)) return;
        player.setYRot(lockYaw);
        player.setXRot(lockPitch);
        player.yRotO = lockYaw;
        player.xRotO = lockPitch;
        player.setYHeadRot(lockYaw);
        player.yHeadRotO = lockYaw;
        player.yBodyRot = lockYaw;
        player.yBodyRotO = lockYaw;
    }

    /** Puts the pointer where the script is working (a corner otherwise); gamescope ignores cursor warps. */
    private static void placeCursor(Minecraft mc) {
        double scale = mc.getWindow().getGuiScale();
        try {
            setField(mc.mouseHandler, "xpos", cursorX * scale);
            setField(mc.mouseHandler, "ypos", cursorY * scale);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not place the cursor", e);
        }
        if (!(mc.screen instanceof FleshingScreen)) { cursorX = .3; cursorY = .3; }
    }

    private static void select(Minecraft mc, int slot) {
        mc.player.getInventory().selected = slot;
        server(mc, (level, player) -> { player.getInventory().selected = slot; player.inventoryMenu.broadcastChanges(); });
    }

    private static void use(Minecraft mc, BlockPos position) {
        server(mc, (level, player) -> level.getBlockState(position).use(level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false)));
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private static void server(Minecraft mc, BiConsumer<ServerLevel, ServerPlayer> work) {
        var server = mc.getSingleplayerServer();
        if (server == null) throw new IllegalStateException("Store screenshots need an integrated server");
        pending = CompletableFuture.runAsync(() -> work.accept(server.overworld(), server.getPlayerList().getPlayers().get(0)), server);
    }

    /** Places the eye at (x, baseY + y, z) looking at (tx, baseY + ty, tz); flight keeps elevated cameras still. */
    private static void look(ServerPlayer player, double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - y, dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        lockYaw = yaw;
        lockPitch = pitch;
        player.teleportTo(player.serverLevel(), x, baseY + y - player.getEyeHeight(), z, yaw, pitch);
    }

    private static void cmd(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput().withPermission(4), command);
    }

    private static void fill(MinecraftServer server, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        cmd(server, "fill " + x1 + " " + (baseY + y1) + " " + z1 + " " + x2 + " " + (baseY + y2) + " " + z2 + " " + block);
    }

    private static void set(MinecraftServer server, int x, int y, int z, String block) {
        cmd(server, "setblock " + x + " " + (baseY + y) + " " + z + " " + block);
    }

    private static void rack(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, ModBlocks.TANNERS_RACK.get().defaultBlockState().setValue(TannersRackBlock.FACING, Direction.SOUTH));
    }

    private static void buildYard(ServerLevel level, ServerPlayer player) {
        MinecraftServer server = level.getServer();
        level.setDayTime(1200);
        level.setWeatherParameters(60000, 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(false, server);
        cmd(server, "kill @e[type=!player]");
        fill(server, -24, 0, -24, 24, 12, 24, "air");
        fill(server, -24, -1, -24, 24, -1, 24, "grass_block");
        RandomSource random = RandomSource.create(20260927L);
        for (int x = -24; x <= 24; x++) for (int z = -24; z <= 24; z++) {
            // A trodden yard: bare earth wears into the meadow towards its edges.
            float inside = Math.max(Math.max(-11 - x, x - 10), Math.max(-7 - z, z - 3));
            boolean worn = random.nextFloat() < (inside <= -2 ? .85F : inside <= 0 ? .55F : inside <= 2 ? .2F : 0F);
            float r = random.nextFloat();
            if (worn) {
                set(server, x, -1, z, r < .6F ? "coarse_dirt" : r < .85F ? "dirt" : "rooted_dirt");
            } else if (inside <= 0) {
                continue;
            } else if (r < .2F) {
                set(server, x, 0, z, "grass");
            } else if (r < .225F) {
                set(server, x, 0, z, random.nextBoolean() ? "dandelion" : "cornflower");
            }
        }
        // Workshop wall behind the yard, with a lean-to over the bench.
        fill(server, -11, 0, -8, 10, 0, -8, "cobblestone");
        fill(server, -11, 1, -8, 10, 3, -8, "spruce_planks");
        for (int x : new int[]{-11, -7, -3, 1, 5, 10}) fill(server, x, 0, -8, x, 4, -8, "stripped_dark_oak_log");
        fill(server, -11, 4, -8, 10, 4, -8, "dark_oak_log[axis=x]");
        fill(server, -11, 5, -8, 10, 5, -8, "spruce_stairs[facing=south]");
        fill(server, -5, 2, -8, -4, 2, -8, "spruce_trapdoor[half=bottom,open=true,facing=south]");
        fill(server, 5, 4, -7, 10, 4, -5, "spruce_slab[type=bottom]");
        fill(server, 5, 4, -4, 10, 4, -4, "spruce_stairs[facing=north,half=bottom]");
        for (int x : new int[]{5, 10}) fill(server, x, 0, -4, x, 3, -4, "stripped_spruce_log");
        set(server, 7, 3, -5, "lantern[hanging=true]");
        // Fence line, props and the bark store.
        fill(server, -11, 0, 4, -11, 0, -7, "oak_fence");
        fill(server, 10, 0, 4, 10, 0, -3, "oak_fence");
        set(server, -11, 1, 4, "lantern");
        set(server, 10, 1, 4, "lantern");
        set(server, -10, 0, -7, "hay_block");
        set(server, -10, 1, -7, "hay_block");
        set(server, -9, 0, -7, "hay_block");
        set(server, -2, 0, -7, "barrel[facing=up]");
        set(server, -1, 0, -7, "barrel[facing=up]");
        set(server, -2, 1, -7, "barrel[facing=up]");
        fill(server, 0, 0, -7, 2, 0, -7, "stripped_oak_log[axis=x]");
        fill(server, 0, 1, -7, 1, 1, -7, "stripped_oak_log[axis=x]");
        fill(server, 0, 0, -6, 2, 0, -6, "oak_log[axis=x]");
        set(server, 9, 0, -7, "barrel[facing=up]");
        set(server, 9, 0, -6, "chest[facing=west]");
        set(server, 5, 0, -7, "water_cauldron[level=3]");
        set(server, -8, 0, 2, "composter");
        int[][] trees = {{-6, -12}, {1, -13}, {8, -12}, {14, -7}, {-15, -5}, {-16, 5}, {15, 6}, {-4, 14}};
        String[] kinds = {"birch", "oak", "birch", "fancy_oak", "oak", "birch", "oak", "fancy_oak"};
        for (int i = 0; i < trees.length; i++)
            cmd(server, "place feature minecraft:" + kinds[i] + " " + trees[i][0] + " " + baseY + " " + trees[i][1]);
        // Racks: the one to flesh, three drying in each finish, and an empty one for the finishing choice.
        TraitRef tough = HideTraitLoader.get(ImmersiveTanning.id("tough"));
        rack(level, rawRack);
        ((TannersRackBlockEntity) level.getBlockEntity(rawRack)).mountRaw(HideData.raw(HideSize.LARGE, null));
        Finish[] finishes = {Finish.SUPPLE, Finish.HARNESS, Finish.HARDENED};
        float[] progress = {0.35F, 0.7F, 1F};
        for (int i = 0; i < 3; i++) {
            BlockPos pos = new BlockPos(-6 + i * 2, baseY, -3);
            rack(level, pos);
            TannersRackBlockEntity be = (TannersRackBlockEntity) level.getBlockEntity(pos);
            be.mountWet(new HideData(HideSize.LARGE, 80, i == 2 ? tough : null), finishes[i]);
            be.advance(Math.round(ServerConfig.get(ServerConfig.DRYING_TICKS) * progress[i]));
        }
        rack(level, emptyRack);
        // Vats: one steeping a batch, one fresh with liquor.
        for (BlockPos pos : new BlockPos[]{vat, vat.east(2)}) {
            level.setBlockAndUpdate(pos, ModBlocks.TANNERS_VAT.get().defaultBlockState());
            TannersVatBlockEntity be = (TannersVatBlockEntity) level.getBlockEntity(pos);
            be.fillWater();
            be.addTannin(6);
        }
        TannersVatBlockEntity steeping = (TannersVatBlockEntity) level.getBlockEntity(vat);
        steeping.insertHide(new HideData(HideSize.LARGE, 80, null));
        steeping.advance(ServerConfig.get(ServerConfig.TANNING_TICKS) / 2);
        steeping.insertHide(new HideData(HideSize.MEDIUM, 70, null));
        ((TannersVatBlockEntity) level.getBlockEntity(vat.east(2))).insertHide(new HideData(HideSize.SMALL, 90, null));
        // Bench under the lean-to.
        BlockState benchState = ModBlocks.LEATHERWORKERS_BENCH.get().defaultBlockState().setValue(LeatherworkersBenchBlock.FACING, Direction.SOUTH);
        level.setBlockAndUpdate(bench, benchState);
        // A finished set on a stand by the bench.
        ArmorStand stand = EntityType.ARMOR_STAND.create(level);
        if (stand != null) {
            stand.moveTo(6.5, baseY, -2.0, 55F, 0F);
            stand.setItemSlot(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, Finish.HARDENED));
            stand.setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, Finish.HARNESS));
            stand.setItemSlot(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, Finish.HARNESS));
            stand.setItemSlot(EquipmentSlot.FEET, dyed(Items.LEATHER_BOOTS, Finish.SUPPLE));
            level.addFreshEntity(stand);
        }
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(ModItems.TANNERS_KNIFE.get()));
        player.getInventory().setItem(1, new ItemStack(ModItems.AWL.get()));
        player.getInventory().setItem(2, ModItems.WET_TANNED_HIDE.get().create(HideSize.LARGE, 90, null));
        player.getInventory().setItem(3, new ItemStack(ModItems.BARK.get(), 16));
        player.getInventory().setItem(9, ModItems.leather(Finish.HARNESS).create(90, null, 12));
        player.getInventory().setItem(10, ModItems.leather(Finish.SUPPLE).create(80, null, 6));
        player.getInventory().setItem(11, ModItems.leather(Finish.HARDENED).create(80, tough, 6));
        player.getInventory().setItem(12, new ItemStack(Items.STRING, 8));
        player.inventoryMenu.broadcastChanges();
        look(player, -1.5, 2.4, 4.0, -2.0, 0.6, -3.0);
    }

    private static ItemStack dyed(net.minecraft.world.item.Item item, Finish finish) {
        ItemStack stack = new ItemStack(item);
        ((DyeableLeatherItem) item).setColor(stack, finish.leatherColor());
        return stack;
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = find(target.getClass(), name);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        find(target.getClass(), name).set(target, value);
    }

    private static Field find(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void advance(int next) {
        step = next;
        action = 0;
        entered = Util.getMillis();
    }

    private StoreScreenshots() {}
}
