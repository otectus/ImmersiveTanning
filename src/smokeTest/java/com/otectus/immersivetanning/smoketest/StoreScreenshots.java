package com.otectus.immersivetanning.smoketest;

import com.google.gson.GsonBuilder;
import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.block.LeatherworkersBenchBlock;
import com.otectus.immersivetanning.block.TannersRackBlock;
import com.otectus.immersivetanning.block.TannersVatBlock;
import com.otectus.immersivetanning.blockentity.LeatherworkersBenchBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersVatBlockEntity;
import com.otectus.immersivetanning.client.station.BenchBehavior;
import com.otectus.immersivetanning.client.station.StationFocus;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.minigame.FleshingSurface;
import com.otectus.immersivetanning.minigame.StitchPattern;
import com.otectus.immersivetanning.registry.ModBlocks;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.station.BenchGeometry;
import com.otectus.immersivetanning.station.RackGeometry;
import com.otectus.immersivetanning.station.VatGeometry;
import com.otectus.immersivetanning.trait.HideTraitLoader;
import com.otectus.immersivetanning.trait.TraitRef;
import com.otectus.immersivetanning.work.LeatherworkJob;
import com.otectus.immersivetanning.work.WorkPattern;
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
import net.minecraft.resources.ResourceLocation;
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
 * {@code -Dimmersive_tanning.screenshots.output=<dir>}. It builds a tannery yard and works its stations in the focused
 * view with real, server-scored input (see {@link FocusDriver}): fleshing, the tension lever and cords, the pattern
 * book, cutting, assembly and stitching, saving framebuffer captures without the vanilla HUD, chat or toasts (the
 * station's own guide stays, as it is part of the work).
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
    private static boolean finished, renamed, fleshShot;
    private static volatile float lockYaw = Float.NaN, lockPitch;
    private static double cursorX = 0.3, cursorY = 0.3;
    private static BlockPos rawRack, emptyRack, bench, vat;
    private static int passes;

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

    /** Pins the scripted view every frame (pointer motion reaching the window cannot turn it). */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!enabled() || event.phase != TickEvent.Phase.START) return;
        lockView(Minecraft.getInstance());
    }

    static void tick(Minecraft mc) throws Exception {
        if (finished) return;
        if (started == 0) started = Util.getMillis();
        if (Util.getMillis() - started > 300_000) throw new IllegalStateException("Store screenshots stalled at step " + step + ", action " + action);
        mc.getToasts().clear();
        lockView(mc);
        FocusDriver.tick();
        if (mc.screen != null && !(mc.screen instanceof InventoryScreen) && !StationFocus.active()) placeCursor(mc);
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
                if (age > 1500 && action == 0) {
                    // A tiling window manager can ignore native resizing. Capture at the requested resolution anyway.
                    mc.getWindow().setWidth(WIDTH);
                    mc.getWindow().setHeight(HEIGHT);
                    mc.resizeDisplay();
                    action++;
                }
                if (age > 6500 && action == 1) {
                    EVIDENCE.put("framebuffer", mc.getMainRenderTarget().width + "x" + mc.getMainRenderTarget().height);
                    shot(mc, "yard_wide");
                    server(mc, (level, player) -> look(player, -9.0, 2.7, 3.5, -2.0, 0.95, -3.2));
                    action++;
                }
                if (age > 8800 && action == 2) {
                    shot(mc, "yard_angle");
                    server(mc, (level, player) -> look(player, -7.0, 1.8, 1.0, -1.5, 0.85, -3.0));
                    action++;
                }
                if (age > 11000 && action == 3) {
                    shot(mc, "yard_detail");
                    server(mc, (level, player) -> look(player, -7.0, 1.55, -0.9, -7.6, 0.85, -2.6));
                    advance(2);
                }
            }
            case 2 -> {
                if (action == 0 && age > 2500) { shot(mc, "rack_raw"); action++; }
                if (action == 1 && age > 2800) { use(mc, rawRack); advance(3); }
            }
            case 3 -> {
                // Fleshing in the focused view: careful passes along the grain, a shot mid-stroke, then the result.
                TannersRackBlockEntity rack = (TannersRackBlockEntity) mc.level.getBlockEntity(rawRack);
                if (action == 0 && FocusDriver.focusedAt(rawRack) && age > 1500) {
                    queueFlesh(mc, rack);
                    action++;
                } else if (action == 1 && rack.mode() == TannersRackBlockEntity.Mode.SCRAPED) {
                    action = 2;
                    entered = Util.getMillis();
                } else if (action == 1 && FocusDriver.idle()) {
                    FleshingSurface surface = rack.work() == null ? null : rack.work().surface();
                    if (surface != null) {
                        EVIDENCE.put("fleshing_coverage", surface.coverage());
                        EVIDENCE.put("fleshing_score", surface.score());
                    }
                    if (surface != null && (surface.coverage() < .9F && age < 40_000)) queueFlesh(mc, rack);
                    else FocusDriver.key(GLFW.GLFW_KEY_F);
                } else if (action == 2 && age > 1600) {
                    shot(mc, "fleshing_result");
                    StationFocus.leave(true);
                    // Hang a fresh hide so the yard shots keep the whole progression on its racks.
                    server(mc, (level, player) -> ((TannersRackBlockEntity) level.getBlockEntity(rawRack)).mountRaw(HideData.raw(HideSize.LARGE, null)));
                    advance(4);
                }
            }
            case 4 -> {
                if (action == 0 && age > 400) {
                    server(mc, (level, player) -> look(player, 3.5, 2.5, -0.8, 3.5, 0.45, -2.8));
                    action++;
                }
                if (action == 1 && age > 3000) { shot(mc, "vats"); action++; }
                if (action == 2 && age > 3300) {
                    use(mc, vat);
                    action++;
                }
                if (action == 3 && FocusDriver.focusedAt(vat) && age > 4600) {
                    FocusDriver.click(vat, VatGeometry.paddleBox().getCenter());
                    FocusDriver.wait(4);
                    List<Vec3> sweep = new ArrayList<>();
                    for (int i = 0; i <= 30; i++) {
                        float angle = (float) (i / 30.0 * Math.PI * 2);
                        sweep.add(VatGeometry.surfacePoint(.5F + .32F * (float) Math.cos(angle), .5F + .32F * (float) Math.sin(angle), true));
                    }
                    FocusDriver.drag(vat, sweep, 20, () -> shot(mc, "vat_stirring"));
                    action++;
                }
                if (action == 4 && FocusDriver.idle() && age > 6000) {
                    EVIDENCE.put("vat_tended", ((TannersVatBlockEntity) mc.level.getBlockEntity(vat)).slots().stream().anyMatch(TannersVatBlockEntity.Slot::tended));
                    StationFocus.leave(true);
                    int wet = slotOf(mc, ModItems.WET_TANNED_HIDE.get());
                    select(mc, wet);
                    server(mc, (level, player) -> look(player, 0.4, 1.7, -0.8, 0.5, 0.8, -2.5));
                    action++;
                }
                if (action == 5 && age > 8000) { use(mc, emptyRack); advance(5); }
            }
            case 5 -> {
                // Harness: the lever swung through Supple and back to its middle detent, the cords drawn, the lever seated.
                TannersRackBlockEntity rack = (TannersRackBlockEntity) mc.level.getBlockEntity(emptyRack);
                if (action == 0 && FocusDriver.focusedAt(emptyRack) && rack.mode() == TannersRackBlockEntity.Mode.FRAMING && age > 1500) {
                    float from = RackGeometry.detentDegrees(rack.finish());
                    List<Vec3> path = new ArrayList<>();
                    for (float a : new float[]{from, 12F, 24F, 38F, 24F, 12F, 0F}) path.add(RackGeometry.leverEnd(a));
                    FocusDriver.drag(emptyRack, path, 10, () -> shot(mc, "finish_lever"));
                    action++;
                    passes = 0;
                } else if (action == 1 && FocusDriver.idle() && rack.mode() == TannersRackBlockEntity.Mode.FRAMING) {
                    if (rack.treated()) {
                        FocusDriver.click(emptyRack, RackGeometry.LEVER_PIVOT);
                        action++;
                    } else if (passes < 6) {
                        FocusDriver.click(emptyRack, RackGeometry.cordBox().getCenter());
                        FocusDriver.drag(emptyRack, FocusDriver.line(FocusDriver.frame(.5F, .18F), FocusDriver.frame(.5F, .9F), 20), 12,
                                passes == 0 ? () -> shot(mc, "finishing") : null);
                        passes++;
                    } else {
                        throw new IllegalStateException("Harness cords did not finish: " + rack.treatment());
                    }
                } else if (action == 2 && rack.mode() == TannersRackBlockEntity.Mode.DRYING) {
                    EVIDENCE.put("finish", rack.finish().getSerializedName());
                    StationFocus.leave(true);
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
                if (action == 3 && FocusDriver.focusedAt(bench) && age > 6000 && FocusDriver.idle()) {
                    FocusDriver.click(bench, BenchGeometry.BOOK.getCenter());
                    action++;
                }
                ResourceLocation tunic = ImmersiveTanning.id("leatherworking/leather_chestplate");
                if (action == 4 && StationFocus.session() != null && StationFocus.session().behavior() instanceof BenchBehavior book && book.bookOffers(tunic)) {
                    action++;
                    entered = Util.getMillis();
                    return; // let the catalogue render before photographing it
                }
                if (action == 5 && age > 1200 && StationFocus.session() != null && StationFocus.session().behavior() instanceof BenchBehavior book) {
                    shot(mc, "pattern_book");
                    book.selectInBook(tunic);
                    book.prepareFromBook(tunic, Finish.HARNESS);
                    advance(7);
                }
            }
            case 7 -> {
                LeatherworkersBenchBlockEntity be = (LeatherworkersBenchBlockEntity) mc.level.getBlockEntity(bench);
                LeatherworkJob job = be.job();
                if (action == 0 && job != null && FocusDriver.idle()) {
                    queueCuts(mc, job);
                    action++;
                } else if (action == 1 && job != null && job.stage() == LeatherworkJob.Stage.ASSEMBLY && FocusDriver.idle()) {
                    queueSeats(mc, job);
                    action++;
                } else if (action == 2 && job != null && job.stage() == LeatherworkJob.Stage.STITCHING && FocusDriver.idle()) {
                    queueStitches(mc, job);
                    action++;
                } else if (action == 3 && job == null && be.hasOutput()) {
                    action++;
                    entered = Util.getMillis();
                } else if (action == 4 && age > 1700) {
                    shot(mc, "stitch_result");
                    FocusDriver.click(bench, BenchGeometry.OUTPUT_PAD.getCenter());
                    action++;
                } else if (action == 5 && FocusDriver.idle() && slotOf(mc, Items.LEATHER_CHESTPLATE) >= 0) {
                    StationFocus.leave(true);
                    advance(8);
                }
            }
            case 8 -> {
                if (action == 0 && age > 800) {
                    pieceSlot = slotOf(mc, Items.LEATHER_CHESTPLATE);
                    if (pieceSlot < 0) throw new IllegalStateException("Stitching did not produce the tunic");
                    EVIDENCE.put("tunic", mc.player.getInventory().getItem(pieceSlot).getTag() == null ? "" : mc.player.getInventory().getItem(pieceSlot).getTag().toString());
                    hideJeiOverlay();
                    mc.options.guiScale().set(3);
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
                if (action == 0 && age > 3000) {
                    shot(mc, "yard_outro");
                    server(mc, (level, player) -> look(player, 4.4, 1.9, 1.1, 6.5, .95, -2.0));
                    action++;
                }
                if (action == 1 && age > 6000) {
                    shot(mc, "finished_gear");
                    mc.options.fov().set(60);
                    server(mc, StoreScreenshots::stageCollection);
                    action++;
                }
                if (action == 2 && age > 9500) {
                    shot(mc, "workstation_collection");
                    server(mc, (level, player) -> look(player, -4.5, 2.2, 3.7, -.2, .65, .3));
                    action++;
                }
                if (action == 3 && age > 12500) {
                    shot(mc, "workstation_collection_angle");
                    EVIDENCE.put("shots", SHOTS);
                    EVIDENCE.put("pass", true);
                    Files.writeString(Path.of(OUTPUT, "result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(EVIDENCE));
                    finished = true;
                    mc.stop();
                }
            }
        }
    }

    // ------------------------------------------------------------------ focus input

    /** Careful passes along each flesh patch's grain on the client's copy of the hide, a shot mid-stroke past half clean. */
    private static void queueFlesh(Minecraft mc, TannersRackBlockEntity rack) {
        if (rack.work() == null) return;
        FleshingSurface surface = rack.work().surface();
        for (int p = 0; p < surface.patches(); p++) {
            float cos = (float) Math.cos(surface.grain(p)), sin = (float) Math.sin(surface.grain(p));
            for (float off = -9F; off <= 9F; off += 2.6F) {
                float cx = surface.patchX(p) - sin * off, cy = surface.patchY(p) + cos * off;
                List<Vec3> stroke = new ArrayList<>();
                for (int i = 0; i <= 24; i++) {
                    float t = i / 24F;
                    float u = Math.max(.01F, Math.min(.99F, (cx - cos * 11F + cos * 22F * t) / FleshingSurface.W));
                    float v = Math.max(.01F, Math.min(.99F, (cy - sin * 11F + sin * 22F * t) / FleshingSurface.H));
                    stroke.add(FocusDriver.hide(u, v));
                }
                FocusDriver.drag(rawRack, stroke, 6, () -> {
                    if (!fleshShot && rack.work() != null && rack.work().surface().coverage() > .5F) {
                        shot(mc, "fleshing");
                        fleshShot = true;
                    }
                }, () -> rack.mode() == TannersRackBlockEntity.Mode.RAW);
            }
        }
    }

    private static void queueCuts(Minecraft mc, LeatherworkJob job) {
        for (int c = 0; c < job.pattern().cuts().size(); c++) {
            float[] path = job.pattern().cutPath(c);
            List<Vec3> points = new ArrayList<>();
            for (int i = 0; i <= 20; i++) {
                float[] p = StitchPattern.pointAt(path, i / 20F);
                points.add(FocusDriver.plane(p[0], p[1]));
            }
            FocusDriver.drag(bench, points, 8, c == 1 ? () -> shot(mc, "cutting") : null);
        }
    }

    private static void queueSeats(Minecraft mc, LeatherworkJob job) {
        WorkPattern pattern = job.pattern();
        for (int p = 0; p < pattern.panels().size(); p++) {
            float[] from = pattern.staging(p);
            WorkPattern.Placement to = pattern.placement(p);
            int panel = p;
            FocusDriver.drag(bench, FocusDriver.line(FocusDriver.plane(from[0], from[1]), FocusDriver.plane(to.x(), to.y()), 8), 8,
                    () -> {
                        FocusDriver.rotatePanel(to.rot() - from[2]);
                        if (panel == pattern.panels().size() - 1) shot(mc, "assembly");
                    });
        }
    }

    private static void queueStitches(Minecraft mc, LeatherworkJob job) {
        for (int a = 0; a < job.stitchCount(); a++) {
            float[] f = job.stitchFrame(a);
            float pull = job.pattern().joinOf(a).pullIdeal();
            List<Vec3> path = new ArrayList<>();
            for (int i = 0; i <= 6; i++) path.add(FocusDriver.plane(f[4] + f[6] * pull * i / 6F, f[5] + f[7] * pull * i / 6F));
            FocusDriver.drag(bench, path, 6, a == job.stitchCount() / 2 ? () -> shot(mc, "stitching") : null);
        }
        float[] last = job.stitchFrame(job.stitchCount() - 1);
        FocusDriver.wait(4);
        FocusDriver.click(bench, FocusDriver.plane(last[4] + .04F, last[5] + .04F).add(0, .25, 0));
    }

    // ------------------------------------------------------------------ helpers

    private static int slotOf(Minecraft mc, net.minecraft.world.item.Item item) {
        for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).is(item)) return i;
        return -1;
    }

    private static void shot(Minecraft mc, String name) {
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            if (image.getWidth() != WIDTH || image.getHeight() != HEIGHT) {
                throw new IllegalStateException("Expected " + WIDTH + "x" + HEIGHT + ", got " + image.getWidth() + "x" + image.getHeight());
            }
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
        cursorX = .3;
        cursorY = .3;
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
            level.setBlockAndUpdate(pos, ModBlocks.TANNERS_VAT.get().defaultBlockState().setValue(TannersVatBlock.FACING, Direction.SOUTH));
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
        look(player, -1.0, 3.3, 7.8, -0.5, 1.1, -3.5);
    }

    private static ItemStack dyed(net.minecraft.world.item.Item item, Finish finish) {
        ItemStack stack = new ItemStack(item);
        ((DyeableLeatherItem) item).setColor(stack, finish.leatherColor());
        return stack;
    }

    /** A compact set for the cover: all three stations large enough to read, with the working yard behind them. */
    private static void stageCollection(ServerLevel level, ServerPlayer player) {
        BlockPos displayRack = new BlockPos(-3, baseY, 0);
        BlockPos displayVat = new BlockPos(-1, baseY, 0);
        BlockPos displayBench = new BlockPos(1, baseY, 0);
        rack(level, displayRack);
        TannersRackBlockEntity rack = (TannersRackBlockEntity) level.getBlockEntity(displayRack);
        rack.mountWet(new HideData(HideSize.LARGE, 90, null), Finish.SUPPLE);
        rack.advance(ServerConfig.get(ServerConfig.DRYING_TICKS));
        level.setBlockAndUpdate(displayVat, ModBlocks.TANNERS_VAT.get().defaultBlockState().setValue(TannersVatBlock.FACING, Direction.SOUTH));
        TannersVatBlockEntity vat = (TannersVatBlockEntity) level.getBlockEntity(displayVat);
        vat.fillWater();
        vat.addTannin(5);
        vat.insertHide(new HideData(HideSize.LARGE, 90, null));
        vat.insertHide(new HideData(HideSize.MEDIUM, 85, null));
        level.setBlockAndUpdate(displayBench, ModBlocks.LEATHERWORKERS_BENCH.get().defaultBlockState().setValue(LeatherworkersBenchBlock.FACING, Direction.SOUTH));
        ((LeatherworkersBenchBlockEntity) level.getBlockEntity(displayBench)).setJob(ClientSmokeTest.stagedJob(player, "saddle", true));
        look(player, .1, 2.45, 5.0, -.2, .70, .3);
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
        ImmersiveTanning.LOGGER.info("SHOWCASE step {} -> {} ({} captures)", step, next, SHOTS.size());
        step = next;
        action = 0;
        entered = Util.getMillis();
    }

    private StoreScreenshots() {}
}
