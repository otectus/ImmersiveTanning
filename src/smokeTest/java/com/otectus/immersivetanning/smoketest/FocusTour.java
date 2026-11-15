package com.otectus.immersivetanning.smoketest;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.blockentity.LeatherworkersBenchBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersVatBlockEntity;
import com.otectus.immersivetanning.client.ClientPacketHandler;
import com.otectus.immersivetanning.client.station.BenchBehavior;
import com.otectus.immersivetanning.client.station.Hud;
import com.otectus.immersivetanning.client.station.StationBehavior;
import com.otectus.immersivetanning.client.station.StationFocus;
import com.otectus.immersivetanning.config.ClientConfig;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.item.ProcessedHideItem;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.minigame.FleshingSurface;
import com.otectus.immersivetanning.minigame.StitchPattern;
import com.otectus.immersivetanning.network.packet.BenchOffersPacket;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModRecipeTypes;
import com.otectus.immersivetanning.station.BenchGeometry;
import com.otectus.immersivetanning.station.RackGeometry;
import com.otectus.immersivetanning.station.VatGeometry;
import com.otectus.immersivetanning.work.LeatherworkJob;
import com.otectus.immersivetanning.work.WorkAction;
import com.otectus.immersivetanning.work.WorkPattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The focused view at each station staged by {@link ClientSmokeTest}, driven with real mouse and key events on the
 * focus screen (see {@link FocusDriver}) and photographed mid-gesture: strokes on a raw hide until "Finish roughly" is
 * offered (and fits the HUD at GUI scale 3), the rough finish and the hide's worked look; the tension lever moved to
 * Hardened, burnished and seated; the paddle swept through a vat and a ready hide lifted on the hook; an empty vat
 * filled through the view, where a waiting hide says what it waits for; at the bench the pattern book (also at GUI
 * scale 3, and with labels for results that share a name), a pair of boots cut, seated, stitched (one stitch with a
 * held Space) and tied off; and the camera modes: stationary keeps the player's own view, reduced motion cuts to the
 * shot without a glide. A step that fails skips to the next station; the camera mode and GUI scale are put back.
 */
final class FocusTour {
    /** Stations staged by ClientSmokeTest, relative to its floor's origin; the player stands south of each. */
    static final BlockPos RAW_RACK = new BlockPos(-3, 0, -2);
    static final BlockPos FRAME_RACK = new BlockPos(-5, 0, -2);
    static final BlockPos FULL_VAT = new BlockPos(6, 0, -2);
    static final BlockPos EMPTY_VAT = new BlockPos(8, 0, -2);
    static final BlockPos FREE_BENCH = new BlockPos(8, 0, 2);
    private static final ResourceLocation BOOTS = ImmersiveTanning.id("leatherworking/leather_boots");

    /** One input and the synced state that shows it worked; {@code opens} marks the first step at a station. */
    private record Step(String name, int timeoutTicks, boolean opens, Runnable action, BooleanSupplier done) {}

    private static Map<String, Object> checks;
    private static BlockPos base;
    private static List<Step> steps = List.of();
    private static int index;
    private static int stepTicks;
    private static int focusedSince = -1;
    private static boolean over = true;
    @Nullable
    private static ClientConfig.CameraMode originalMode;
    @Nullable
    private static Integer originalScale;
    private static HumanoidArm originalHand;
    private static ClientConfig.HandVisuals originalHands;
    private static int[] originalWindow;
    private static Double originalTransition;

    private static List<List<Vec3>> strokes = List.of();
    private static int nextStroke;
    private static int passes;
    private static int wetBefore;
    private static int bootsBefore;
    @Nullable
    private static Vec3 firstPose;
    private static int spaceStitch = -1;

    static void start(BlockPos origin, Map<String, Object> results) {
        checks = results;
        base = origin;
        if (base == null || Minecraft.getInstance().getSingleplayerServer() == null) {
            checks.put("focus_tour_started", false);
            return;
        }
        steps = steps();
        index = 0;
        stepTicks = 0;
        focusedSince = -1;
        over = false;
    }

    static boolean running() {
        return !over;
    }

    /** Runs the current step for one client tick. */
    static void tick() {
        if (over) return;
        FocusDriver.tick();
        Step step = steps.get(index);
        if (stepTicks++ == 0) step.action().run();
        if (step.done().getAsBoolean()) {
            checks.put("focus_" + step.name(), true);
            advance(false);
        } else if (stepTicks > step.timeoutTicks()) {
            checks.put("focus_" + step.name(), false);
            checks.put("focus_" + step.name() + "_state", state());
            advance(true);
        }
    }

    /** The next step; after a failure, the first step at the next station (with focus left and input cleared). */
    private static void advance(boolean failed) {
        stepTicks = 0;
        focusedSince = -1;
        index++;
        if (failed) {
            FocusDriver.clear();
            restoreGuiScale();
            restoreHands();
            restoreWindow();
            restoreTransition();
            if (StationFocus.active()) StationFocus.leave(true);
            while (index < steps.size() && !steps.get(index).opens()) {
                checks.put("focus_" + steps.get(index).name(), false);
                index++;
            }
        }
        if (index >= steps.size()) end();
    }

    private static void end() {
        over = true;
        FocusDriver.clear();
        restoreGuiScale();
        restoreHands();
        restoreWindow();
        restoreTransition();
        if (StationFocus.active()) StationFocus.leave(true);
        if (originalMode != null) ClientConfig.CAMERA_MODE.set(originalMode);
        originalMode = null;
        Minecraft.getInstance().setScreen(null);
    }

    private static List<Step> steps() {
        Item knife = ModItems.TANNERS_KNIFE.get();
        Item awl = ModItems.AWL.get();
        Item wet = ModItems.WET_TANNED_HIDE.get();
        Item scraped = ModItems.SCRAPED_HIDE.get();
        List<Step> out = new ArrayList<>();

        // ---- a raw hide on the beam
        out.add(new Step("rack_view_leans_in", 120, true, () -> {
            setMode(ClientConfig.CameraMode.CINEMATIC);
            // A client tick can arrive after the default short glide has already ended (texture upload / GC).
            // Give this timing assertion an explicit duration and sample from the first focused tick.
            originalTransition = ClientConfig.get(ClientConfig.TRANSITION_SECONDS);
            ClientConfig.TRANSITION_SECONDS.set(0.8);
            standAt(RAW_RACK);
        }, () -> {
            if (stepTicks == 8) holdAndUse(is(knife), RAW_RACK);
            int since = focusedTicks(RAW_RACK);
            if (since == 0) firstPose = cameraPos();
            if (since == 6) {
                double movement = firstPose == null ? 0 : firstPose.distanceTo(cameraPos());
                checks.put("focus_cinematic_glides_in", movement > 0.02);
                checks.put("focus_cinematic_glide_distance", movement);
            }
            if (since < 22) return false;
            checks.put("focus_camera_leans_in", StationFocus.ownsCamera() && cameraFromEyes() > 0.3);
            restoreTransition();
            return true;
        }));
        out.add(new Step("strokes_scrape_until_finish_roughly_is_offered", 900, false, () -> {
            strokes = fleshStrokes(RAW_RACK);
            nextStroke = 0;
        }, () -> {
            if (!FocusDriver.idle()) return false;
            FleshingSurface surface = surface(RAW_RACK);
            if (surface != null && surface.canFinishRoughly() && surface.coverage() >= 0.4F) return true;
            if (nextStroke >= strokes.size()) return false;
            FocusDriver.drag(at(RAW_RACK), strokes.get(nextStroke), 4, nextStroke == 3 ? () -> shot("smoketest_fleshing") : null,
                    () -> rack(RAW_RACK) != null && rack(RAW_RACK).mode() == TannersRackBlockEntity.Mode.RAW);
            nextStroke++;
            return false;
        }));
        out.add(new Step("finish_roughly_fits_at_gui_scale_3", 80, false, () -> setGuiScale(3), () -> {
            if (stepTicks < 8) return false;
            StationBehavior b = behavior();
            boolean fits = b != null && b.hudButtons().stream().anyMatch(button -> button.id().equals("rough") && inWindow(button));
            if (!fits && stepTicks < 40) return false;
            checks.put("focus_rack_buttons_scale3", b == null ? "none" : describe(b.hudButtons()));
            shot("smoketest_fleshing_scale3");
            restoreGuiScale();
            return fits;
        }));
        out.add(new Step("finish_roughly_ends_the_fleshing", 100, false, () -> {
            FocusDriver.wait(4);
            FocusDriver.key(GLFW.GLFW_KEY_F);
        }, () -> {
            TannersRackBlockEntity rack = rack(RAW_RACK);
            if (rack == null || rack.mode() != TannersRackBlockEntity.Mode.SCRAPED) return false;
            if (stepTicks < 16) return false; // the result line and the scraped look are drawn
            shot("smoketest_fleshing_result");
            checks.put("focus_scraped_hide_keeps_its_look", rack.scrapedLook() != null && rack.scrapedLook().coverage() >= 0.3F);
            return true;
        }));
        out.add(new Step("leaving_gives_the_view_back", 80, false, () -> FocusDriver.key(GLFW.GLFW_KEY_ESCAPE), () -> {
            if (StationFocus.active() || Minecraft.getInstance().screen != null || stepTicks < 14) return false;
            checks.put("focus_mouse_grabbed_after_leaving", Minecraft.getInstance().mouseHandler.isMouseGrabbed() ? "yes" : "no (window not focused)");
            return cameraFromEyes() < 0.05;
        }));

        // ---- a wet hide in the frame: the tension lever, burnishing, seating
        out.add(new Step("frame_view_leans_in", 120, true, () -> standAt(FRAME_RACK), () -> {
            if (stepTicks == 8) holdAndUse(is(knife), FRAME_RACK);
            return focusedTicks(FRAME_RACK) >= 14 && rack(FRAME_RACK).mode() == TannersRackBlockEntity.Mode.FRAMING;
        }));
        out.add(new Step("lever_moves_to_hardened", 80, false, () -> {
            float from = RackGeometry.detentDegrees(rack(FRAME_RACK).finish());
            List<Vec3> path = new ArrayList<>();
            for (int i = 0; i <= 8; i++) path.add(RackGeometry.leverEnd(Mth.lerp(i / 8F, from, -38F)));
            FocusDriver.drag(at(FRAME_RACK), path, 8, () -> shot("smoketest_finish_lever"));
        }, () -> rack(FRAME_RACK).finish() == Finish.HARDENED));
        out.add(new Step("hardened_burnished_and_seated", 400, false, () -> passes = 0, () -> {
            if (!FocusDriver.idle()) return false;
            TannersRackBlockEntity rack = rack(FRAME_RACK);
            if (rack.mode() == TannersRackBlockEntity.Mode.DRYING) return rack.finish() == Finish.HARDENED;
            if (rack.mode() != TannersRackBlockEntity.Mode.FRAMING) return false;
            if (rack.treated()) {
                FocusDriver.click(at(FRAME_RACK), RackGeometry.LEVER_PIVOT);
                FocusDriver.wait(4);
            } else if (passes < 16) {
                FocusDriver.click(at(FRAME_RACK), RackGeometry.SLICKER);
                float y = 0.2F + (passes % 5) * 0.15F;
                FocusDriver.drag(at(FRAME_RACK), FocusDriver.line(FocusDriver.frame(0.12F, y), FocusDriver.frame(0.88F, y + 0.05F), 16), 6,
                        passes == 1 ? () -> shot("smoketest_finish_burnish") : null);
                passes++;
            }
            return false;
        }));

        // ---- the full vat: the paddle swept through it, a ready hide lifted on the hook
        out.add(new Step("vat_view_leans_in", 120, true, () -> standAt(FULL_VAT), () -> {
            if (stepTicks == 8) {
                TannersVatBlockEntity vat = vat(FULL_VAT);
                checks.put("vat_one_hide_ready", vat != null && vat.finishedCount() == 1);
                holdAndUse(is(knife), FULL_VAT);
            }
            return focusedTicks(FULL_VAT) >= 14;
        }));
        out.add(new Step("vat_hover_names_what_a_hide_waits_for", 60, false, () -> FocusDriver.move(at(FULL_VAT), VatGeometry.handle(1)), () -> {
            if (!FocusDriver.idle() || stepTicks < 6) return false;
            StationBehavior.Target target = behavior() == null ? null : behavior().hovered();
            boolean named = target != null && target.id().equals("hide1") && !target.verb().getString().isEmpty();
            if (!named && stepTicks < 40) return false;
            checks.put("focus_vat_hover_detail", target == null ? "nothing hovered" : target.id() + ": " + target.verb().getString());
            shot("smoketest_vat_focus");
            return named;
        }));
        out.add(new Step("paddle_sweeps_the_batch", 160, false, () -> {
            FocusDriver.click(at(FULL_VAT), VatGeometry.paddleBox().getCenter());
            FocusDriver.drag(at(FULL_VAT), TanneryPlaythrough.sweepPath(), 10, () -> shot("smoketest_vat_paddle"));
        }, () -> vat(FULL_VAT).slots().stream().anyMatch(TannersVatBlockEntity.Slot::tended)));
        out.add(new Step("hook_lifts_the_ready_hide", 100, false, () -> {
            wetBefore = count(wet);
            FocusDriver.click(at(FULL_VAT), VatGeometry.paddleBox().getCenter()); // the paddle goes back on the rim first
            FocusDriver.click(at(FULL_VAT), VatGeometry.HOOK);
        }, () -> vat(FULL_VAT).finishedCount() == 0 && count(wet) == wetBefore + 1));

        // ---- an empty vat: water, one bark, a large hide that waits for more, a small one that starts
        out.add(new Step("empty_vat_filled_through_the_view", 120, true, () -> {
            standAt(EMPTY_VAT);
            onServer(player -> {
                player.getInventory().setItem(8, new ItemStack(Items.WATER_BUCKET));
                player.getInventory().add(ModItems.SCRAPED_HIDE.get().create(HideSize.SMALL, 80, null));
            });
        }, () -> {
            if (stepTicks == 8) holdAndUse(is(Items.WATER_BUCKET), EMPTY_VAT);
            return vat(EMPTY_VAT) != null && vat(EMPTY_VAT).hasWater() && focusedTicks(EMPTY_VAT) >= 14;
        }));
        out.add(new Step("bark_and_hides_through_the_opening", 160, false, () -> {
            Vec3 opening = VatGeometry.openingBox(true).getCenter();
            drawer(is(ModItems.BARK.get()));
            FocusDriver.click(at(EMPTY_VAT), opening);
            drawer(stack -> stack.is(scraped) && ProcessedHideItem.data(stack).size() == HideSize.LARGE);
            FocusDriver.click(at(EMPTY_VAT), opening);
            drawer(stack -> stack.is(scraped) && ProcessedHideItem.data(stack).size() == HideSize.SMALL);
            FocusDriver.click(at(EMPTY_VAT), opening);
        }, () -> FocusDriver.idle() && vat(EMPTY_VAT).slots().size() == 2));
        out.add(new Step("waiting_hide_says_what_it_waits_for", 60, false, () -> FocusDriver.move(at(EMPTY_VAT), VatGeometry.handle(0)), () -> {
            if (!FocusDriver.idle() || stepTicks < 6) return false;
            TannersVatBlockEntity vat = vat(EMPTY_VAT);
            boolean order = vat.slots().size() == 2 && !vat.slots().get(0).started() && vat.slots().get(1).started() && vat.tannin() == 0;
            StationBehavior.Target target = behavior() == null ? null : behavior().hovered();
            String expected = vat.slots().isEmpty() ? "" : Component.translatable("verb.immersive_tanning.hide_needs_bark",
                    vat.slots().get(0).hide().size().displayName(), vat.missingBark(vat.slots().get(0))).getString();
            boolean explained = target != null && target.id().equals("hide0") && target.verb().getString().equals(expected);
            if (!explained && stepTicks < 40) return false;
            checks.put("vat_small_starts_past_waiting_large", order);
            checks.put("focus_vat_waiting_detail", target == null ? "nothing hovered" : target.id() + ": " + target.verb().getString());
            shot("smoketest_vat_waiting");
            return explained;
        }));

        // ---- the bench: the pattern book, then boots by hand
        out.add(new Step("bench_book_opens", 160, true, () -> standAt(FREE_BENCH), () -> {
            if (stepTicks == 8) holdAndUse(is(awl), FREE_BENCH);
            BenchBehavior b = bench();
            if (b != null && !b.bookOpen() && focusedTicks(FREE_BENCH) >= 14 && FocusDriver.idle()) {
                FocusDriver.click(at(FREE_BENCH), BenchGeometry.BOOK.getCenter());
            }
            return b != null && b.bookOffers(BOOTS);
        }));
        out.add(new Step("book_fits_at_gui_scale_3", 80, false, () -> bench().selectInBook(BOOTS), () -> {
            if (stepTicks == 4) shot("smoketest_bench_book");
            if (stepTicks == 5) setGuiScale(3);
            if (stepTicks < 14) return false;
            BenchBehavior b = bench();
            boolean fits = b != null && !b.hudButtons().isEmpty() && b.hudButtons().stream().allMatch(FocusTour::inWindow);
            checks.put("focus_book_buttons_scale3", b == null ? "none" : describe(b.hudButtons()));
            shot("smoketest_bench_book_scale3");
            restoreGuiScale();
            return fits;
        }));
        out.add(new Step("book_labels_results_that_share_a_name", 80, false, () -> {
            BenchOffersPacket offer = variantOffer();
            if (offer != null) ClientPacketHandler.offers(offer);
        }, () -> {
            BenchBehavior b = bench();
            if (b == null || stepTicks < 4) return false;
            Map<String, Object> detail = variantLabels(b);
            checks.put("bench_variant_labels", Boolean.TRUE.equals(detail.get("pass")));
            checks.put("bench_variant_labels_detail", detail);
            if (detail.get("select") instanceof ResourceLocation id) b.selectInBook(id);
            FocusDriver.wait(3);
            FocusDriver.run(() -> shot("smoketest_bench_variants"));
            return true;
        }));
        out.add(new Step("book_shows_the_real_patterns_again", 80, false, () -> {
            FocusDriver.run(() -> {
                if (bench() != null) bench().openBook();
            });
        }, () -> FocusDriver.idle() && bench() != null && bench().bookOffers(BOOTS) && bench().bookVariantLabel(new ItemStack(Items.MUSIC_DISC_13)) == null));
        out.add(new Step("boots_prepared_from_the_book", 80, false, () -> {
            bootsBefore = count(Items.LEATHER_BOOTS);
            bench().prepareFromBook(BOOTS, Finish.SUPPLE);
        }, () -> job(FREE_BENCH) != null));
        out.add(new Step("cuts_traced_with_the_knife", 600, false, () -> cut(FREE_BENCH),
                () -> job(FREE_BENCH) != null && job(FREE_BENCH).stage() == LeatherworkJob.Stage.ASSEMBLY));
        out.add(new Step("panels_seated_on_the_jig", 400, false, () -> seat(FREE_BENCH),
                () -> job(FREE_BENCH) != null && job(FREE_BENCH).stage() == LeatherworkJob.Stage.STITCHING));
        out.add(new Step("held_space_pulls_one_stitch", 120, false, () -> spaceStitch(FREE_BENCH), () -> {
            LeatherworkJob job = job(FREE_BENCH);
            if (!FocusDriver.idle() || job == null || spaceStitch < 0) return false;
            return job.nextStitch() == spaceStitch + 1 && !Float.isNaN(job.stitchQuality(spaceStitch));
        }));
        out.add(new Step("bench_view_fits_small_window", 100, false, () -> {
            Minecraft mc = Minecraft.getInstance();
            originalWindow = new int[]{mc.getWindow().getWidth(), mc.getWindow().getHeight()};
            GLFW.glfwRestoreWindow(mc.getWindow().getWindow());
            GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 854, 480);
        }, () -> {
            if (stepTicks == 8) {
                // Tiling window managers can ignore a native resize. Exercise the actual smaller render target and
                // GUI layout anyway, and assert its dimensions instead of silently accepting a full-size capture.
                Minecraft mc = Minecraft.getInstance();
                mc.getWindow().setWidth(854);
                mc.getWindow().setHeight(480);
                mc.resizeDisplay();
            }
            if (stepTicks < 10) return false;
            if (stepTicks == 10) {
                LeatherworkJob job = job(FREE_BENCH);
                if (job != null) {
                    float[] f = job.stitchFrame(job.nextStitch());
                    FocusDriver.move(at(FREE_BENCH), FocusDriver.plane(f[4], f[5]));
                }
            }
            if (stepTicks < 16) return false;
            StationBehavior b = behavior();
            Minecraft mc = Minecraft.getInstance();
            boolean small = mc.getMainRenderTarget().width == 854 && mc.getMainRenderTarget().height == 480;
            checks.put("focus_small_framebuffer_854x480", small);
            checks.put("focus_small_window_buttons", b != null && !b.hudButtons().isEmpty() && b.hudButtons().stream().allMatch(FocusTour::inWindow));
            shot("smoketest_bench_small_window");
            restoreWindow();
            return small;
        }));
        out.add(new Step("seams_stitched_and_tied", 800, false, () -> stitchRest(FREE_BENCH), () -> {
            LeatherworkersBenchBlockEntity bench = benchEntity(FREE_BENCH);
            return bench != null && bench.job() == null && bench.hasOutput();
        }));
        out.add(new Step("result_shown_and_collected", 100, false, () -> {
            FocusDriver.wait(8);
            FocusDriver.run(() -> shot("smoketest_bench_result"));
            FocusDriver.click(at(FREE_BENCH), BenchGeometry.OUTPUT_PAD.getCenter());
        }, () -> count(Items.LEATHER_BOOTS) == bootsBefore + 1));

        // Staged visual fixtures complement the real boots playthrough and the server's completion tests for all patterns.
        for (String name : new String[]{"boots", "helmet", "chestplate", "leggings", "saddle", "horse_armor", "strap"}) {
            for (boolean sewn : new boolean[]{false, true}) {
                String phase = sewn ? "seams" : "cut";
                out.add(new Step("pattern_" + name + "_" + phase, 100, false, () -> onServer(player -> {
                    if (player.serverLevel().getBlockEntity(at(FREE_BENCH)) instanceof LeatherworkersBenchBlockEntity bench) {
                        bench.setJob(ClientSmokeTest.stagedJob(player, name, sewn));
                    }
                }), () -> {
                    LeatherworkJob job = job(FREE_BENCH);
                    if (job == null || !job.pattern().id().equals(ImmersiveTanning.id(name)) || stepTicks < 12) return false;
                    if (sewn != (job.stage() == LeatherworkJob.Stage.STITCHING)) return false;
                    if (stepTicks == 12) {
                        float[] at = sewn ? job.stitchFrame(job.nextStitch()) : job.pattern().cutPath(0);
                        FocusDriver.move(at(FREE_BENCH), FocusDriver.plane(at[sewn ? 4 : 0], at[sewn ? 5 : 1]));
                    }
                    if (stepTicks < 22) return false;
                    shot("smoketest_pattern_" + name + "_" + phase);
                    return true;
                }));
            }
        }
        out.add(new Step("pattern_gallery_cleared", 80, false, () -> onServer(player -> {
            if (player.serverLevel().getBlockEntity(at(FREE_BENCH)) instanceof LeatherworkersBenchBlockEntity bench) bench.setJob(null);
        }), () -> job(FREE_BENCH) == null));

        // ---- the camera modes
        out.add(new Step("stationary_keeps_the_players_view", 120, true, () -> {
            leave();
            setMode(ClientConfig.CameraMode.STATIONARY);
        }, () -> {
            if (stepTicks == 10) holdAndUse(is(awl), FREE_BENCH);
            if (focusedTicks(FREE_BENCH) < 10) return false;
            checks.put("focus_stationary_keeps_the_view", StationFocus.stationary() && !StationFocus.ownsCamera() && cameraFromEyes() < 0.05);
            shot("smoketest_focus_stationary");
            return true;
        }));
        out.add(new Step("reduced_motion_cuts_to_the_shot", 120, true, () -> {
            leave();
            setMode(ClientConfig.CameraMode.REDUCED_MOTION);
        }, () -> {
            if (stepTicks == 10) holdAndUse(is(awl), FREE_BENCH);
            int since = focusedTicks(FREE_BENCH);
            if (since == 1) firstPose = cameraPos();
            if (since < 5) return false;
            double moved = firstPose == null ? -1 : firstPose.distanceTo(cameraPos());
            checks.put("focus_reduced_motion_cuts", firstPose != null && moved < 0.02 && cameraFromEyes() > 0.3);
            checks.put("focus_reduced_motion_detail", "moved " + moved + " after the first frame, " + cameraFromEyes() + " from the eyes");
            shot("smoketest_focus_reduced_motion");
            return true;
        }));
        out.add(new Step("view_left_and_mode_restored", 60, false, FocusTour::leave, () -> !StationFocus.active() && stepTicks > 4));
        return out;
    }

    // ------------------------------------------------------------------ fleshing

    /** Careful passes along each flesh patch's grain on the client's copy of the hide (as in the playthrough). */
    private static List<List<Vec3>> fleshStrokes(BlockPos relative) {
        FleshingSurface surface = surface(relative);
        List<List<Vec3>> out = new ArrayList<>();
        if (surface == null) return out;
        for (int p = 0; p < surface.patches(); p++) {
            float cos = Mth.cos(surface.grain(p));
            float sin = Mth.sin(surface.grain(p));
            for (float off = -9F; off <= 9F; off += 2.6F) {
                float cx = surface.patchX(p) - sin * off;
                float cy = surface.patchY(p) + cos * off;
                List<Vec3> stroke = new ArrayList<>();
                for (int i = 0; i <= 24; i++) {
                    float t = i / 24F;
                    float u = Mth.clamp(Mth.lerp(t, cx - cos * 11F, cx + cos * 11F) / FleshingSurface.W, 0.01F, 0.99F);
                    float v = Mth.clamp(Mth.lerp(t, cy - sin * 11F, cy + sin * 11F) / FleshingSurface.H, 0.01F, 0.99F);
                    stroke.add(FocusDriver.hide(u, v));
                }
                out.add(stroke);
            }
        }
        return out;
    }

    @Nullable
    private static FleshingSurface surface(BlockPos relative) {
        TannersRackBlockEntity rack = rack(relative);
        return rack == null || rack.work() == null ? null : rack.work().surface();
    }

    // ------------------------------------------------------------------ bench

    @Nullable
    private static BenchBehavior bench() {
        StationFocus.Session s = StationFocus.session();
        return s != null && s.pos.equals(at(FREE_BENCH)) && s.behavior() instanceof BenchBehavior b ? b : null;
    }

    @Nullable
    private static LeatherworkersBenchBlockEntity benchEntity(BlockPos relative) {
        return Minecraft.getInstance().level.getBlockEntity(at(relative)) instanceof LeatherworkersBenchBlockEntity bench ? bench : null;
    }

    @Nullable
    private static LeatherworkJob job(BlockPos relative) {
        LeatherworkersBenchBlockEntity bench = benchEntity(relative);
        return bench == null ? null : bench.job();
    }

    private static void cut(BlockPos relative) {
        LeatherworkJob job = job(relative);
        if (job == null) return;
        for (int c = 0; c < job.pattern().cuts().size(); c++) {
            float[] path = job.pattern().cutPath(c);
            List<Vec3> points = new ArrayList<>();
            for (int i = 0; i <= 20; i++) {
                float[] p = StitchPattern.pointAt(path, i / 20F);
                points.add(FocusDriver.plane(p[0], p[1]));
            }
            FocusDriver.drag(at(relative), points, 6, c == 0 ? () -> shot("smoketest_bench_cutting") : null);
        }
    }

    private static void seat(BlockPos relative) {
        LeatherworkJob job = job(relative);
        if (job == null) return;
        WorkPattern pattern = job.pattern();
        for (int p = 0; p < pattern.panels().size(); p++) {
            float[] from = pattern.staging(p);
            WorkPattern.Placement to = pattern.placement(p);
            int panel = p;
            FocusDriver.drag(at(relative), FocusDriver.line(FocusDriver.plane(from[0], from[1]), FocusDriver.plane(to.x(), to.y()), 8), 6,
                    () -> {
                        FocusDriver.rotatePanel(to.rot() - from[2]);
                        if (panel == 0) shot("smoketest_bench_assembly");
                    });
        }
    }

    /**
     * One stitch from the keyboard: the pointer on the lit holes, Space pressed and held (three auto-repeats arrive as
     * a held key sends them), the thread drawn by moving the pointer, Space released. Exactly one stitch results.
     */
    private static void spaceStitch(BlockPos relative) {
        LeatherworkJob job = job(relative);
        if (job == null) return;
        int a = job.nextStitch();
        spaceStitch = a;
        float[] f = job.stitchFrame(a);
        float pull = job.pattern().joinOf(a).pullIdeal();
        BlockPos pos = at(relative);
        FocusDriver.move(pos, FocusDriver.plane(f[4], f[5]));
        FocusDriver.wait(1);
        FocusDriver.keyDown(GLFW.GLFW_KEY_SPACE, 3);
        FocusDriver.wait(3);
        List<Vec3> path = new ArrayList<>();
        for (int i = 1; i <= 6; i++) path.add(FocusDriver.plane(f[4] + f[6] * pull * i / 6F, f[5] + f[7] * pull * i / 6F));
        FocusDriver.glide(pos, path, 5);
        FocusDriver.run(() -> shot("smoketest_bench_thread"));
        FocusDriver.run(() -> {
            Minecraft mc = Minecraft.getInstance();
            originalHand = mc.options.mainHand().get();
            originalHands = ClientConfig.get(ClientConfig.HAND_VISUALS);
            mc.options.mainHand().set(HumanoidArm.LEFT);
            ClientConfig.HAND_VISUALS.set(ClientConfig.HandVisuals.HANDS_AND_TOOLS);
        });
        FocusDriver.wait(3);
        FocusDriver.run(() -> shot("smoketest_bench_left_hand"));
        FocusDriver.run(() -> ClientConfig.HAND_VISUALS.set(ClientConfig.HandVisuals.TOOLS_ONLY));
        FocusDriver.wait(3);
        FocusDriver.run(() -> shot("smoketest_bench_tools_only"));
        FocusDriver.run(FocusTour::restoreHands);
        FocusDriver.keyUp(GLFW.GLFW_KEY_SPACE);
        FocusDriver.wait(4);
    }

    private static void stitchRest(BlockPos relative) {
        LeatherworkJob job = job(relative);
        if (job == null) return;
        int first = job.nextStitch();
        for (int a = first; a < job.stitchCount(); a++) {
            float[] f = job.stitchFrame(a);
            float pull = job.pattern().joinOf(a).pullIdeal();
            List<Vec3> path = new ArrayList<>();
            for (int i = 0; i <= 6; i++) path.add(FocusDriver.plane(f[4] + f[6] * pull * i / 6F, f[5] + f[7] * pull * i / 6F));
            FocusDriver.drag(at(relative), path, 5, a == first + 1 ? () -> shot("smoketest_bench_stitching") : null);
        }
        float[] last = job.stitchFrame(job.stitchCount() - 1);
        FocusDriver.wait(4);
        FocusDriver.click(at(relative), FocusDriver.plane(last[4] + 0.04F, last[5] + 0.04F).add(0, 0.25, 0));
    }

    /**
     * Results that share a name: in the pack the bench's own dragon-scale chestplates; in the dev client three music
     * discs stand in, sent to the open book as the server's offer would be, next to a piece with a name of its own.
     */
    @Nullable
    private static BenchOffersPacket variantOffer() {
        Minecraft mc = Minecraft.getInstance();
        boolean real = mc.level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.LEATHERWORKING.get()).stream()
                .anyMatch(r -> r.getId().getPath().startsWith("leatherworking/compat/iceandfire/") && r.getId().getPath().endsWith("_chestplate"));
        if (real) return null;
        List<BenchOffersPacket.Entry> entries = new ArrayList<>();
        List<BenchOffersPacket.Group> groups = List.of(new BenchOffersPacket.Group(Finish.SUPPLE, null, 6, 80));
        for (Item disc : List.of(Items.MUSIC_DISC_13, Items.MUSIC_DISC_CAT, Items.MUSIC_DISC_BLOCKS)) {
            ResourceLocation id = ImmersiveTanning.id("smoketest/" + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(disc).getPath());
            entries.add(new BenchOffersPacket.Entry(id, new ItemStack(disc), 2, List.of(new ItemStack(Items.PRISMARINE_SHARD, 8)),
                    BenchOffersPacket.CATEGORY_OTHER, null, false, groups, 2, 2, 6));
        }
        entries.add(new BenchOffersPacket.Entry(BOOTS, new ItemStack(Items.LEATHER_BOOTS), 4, List.of(), BenchOffersPacket.CATEGORY_ARMOR,
                null, false, groups, 4, 2, 10));
        return new BenchOffersPacket(at(FREE_BENCH), entries, null, null, null);
    }

    /** The open book's labels: one for each result that shares its name, all different, none for a name of its own. */
    private static Map<String, Object> variantLabels(BenchBehavior book) {
        Minecraft mc = Minecraft.getInstance();
        List<ItemStack> shared = new ArrayList<>();
        ResourceLocation select = null;
        for (var recipe : mc.level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.LEATHERWORKING.get())) {
            String path = recipe.getId().getPath();
            if (path.startsWith("leatherworking/compat/iceandfire/") && path.endsWith("_chestplate")) {
                shared.add(recipe.result());
                if (select == null) select = recipe.getId();
            }
        }
        if (shared.isEmpty()) {
            for (Item disc : List.of(Items.MUSIC_DISC_13, Items.MUSIC_DISC_CAT, Items.MUSIC_DISC_BLOCKS)) shared.add(new ItemStack(disc));
            select = ImmersiveTanning.id("smoketest/music_disc_cat");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        List<String> rows = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (ItemStack stack : shared) {
            Component label = book.bookVariantLabel(stack);
            rows.add(stack.getHoverName().getString() + (label == null ? "" : " (" + label.getString() + ")"));
            if (label != null) labels.add(label.getString());
        }
        boolean unique = book.bookVariantLabel(new ItemStack(Items.LEATHER_BOOTS)) == null;
        out.put("pass", shared.size() >= 3 && labels.size() == shared.size() && labels.stream().distinct().count() == labels.size() && unique);
        out.put("rows", rows);
        out.put("select", select);
        return out;
    }

    // ------------------------------------------------------------------ the view and the settings

    private static void restoreHands() {
        if (originalHand != null) Minecraft.getInstance().options.mainHand().set(originalHand);
        if (originalHands != null) ClientConfig.HAND_VISUALS.set(originalHands);
        originalHand = null;
        originalHands = null;
    }

    private static void restoreWindow() {
        if (originalWindow == null) return;
        Minecraft mc = Minecraft.getInstance();
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), originalWindow[0], originalWindow[1]);
        mc.getWindow().setWidth(originalWindow[0]);
        mc.getWindow().setHeight(originalWindow[1]);
        mc.resizeDisplay();
        originalWindow = null;
    }

    private static void restoreTransition() {
        if (originalTransition == null) return;
        ClientConfig.TRANSITION_SECONDS.set(originalTransition);
        originalTransition = null;
    }

    private static void setMode(ClientConfig.CameraMode mode) {
        if (originalMode == null) originalMode = ClientConfig.get(ClientConfig.CAMERA_MODE);
        ClientConfig.CAMERA_MODE.set(mode);
    }

    private static void setGuiScale(int scale) {
        Minecraft mc = Minecraft.getInstance();
        if (originalScale == null) originalScale = mc.options.guiScale().get();
        mc.options.guiScale().set(scale);
        mc.resizeDisplay();
    }

    private static void restoreGuiScale() {
        if (originalScale == null) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.guiScale().set(originalScale);
        mc.resizeDisplay();
        originalScale = null;
    }

    private static Vec3 cameraPos() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    }

    private static double cameraFromEyes() {
        Minecraft mc = Minecraft.getInstance();
        return cameraPos().distanceTo(mc.player.getEyePosition(mc.getFrameTime()));
    }

    private static boolean inWindow(Hud.Button button) {
        var window = Minecraft.getInstance().getWindow();
        return button.x() >= 0 && button.y() >= 0 && button.x() + button.w() <= window.getGuiScaledWidth()
                && button.y() + button.h() <= window.getGuiScaledHeight();
    }

    private static String describe(List<Hud.Button> buttons) {
        var window = Minecraft.getInstance().getWindow();
        StringBuilder out = new StringBuilder("gui " + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight() + ":");
        for (Hud.Button b : buttons) out.append(' ').append(b.id()).append('@').append(b.x()).append(',').append(b.y()).append('+').append(b.w()).append('x').append(b.h());
        return out.toString();
    }

    /** Ticks since focus at this station was first seen in the current step; -1 while not focused there. */
    private static int focusedTicks(BlockPos relative) {
        if (!FocusDriver.focusedAt(at(relative))) {
            focusedSince = -1;
            return -1;
        }
        if (focusedSince < 0) focusedSince = stepTicks;
        return stepTicks - focusedSince;
    }

    @Nullable
    private static StationBehavior behavior() {
        StationFocus.Session s = StationFocus.session();
        return s == null ? null : s.behavior();
    }

    // ------------------------------------------------------------------ input and state

    /** Leaves any focus, then stands the player (creative, not a spectator: spectators cannot work) south of a station. */
    private static void standAt(BlockPos relative) {
        leave();
        BlockPos pos = at(relative);
        onServer(player -> {
            player.setGameMode(GameType.CREATIVE);
            player.teleportTo(player.serverLevel(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 2.2, 180F, 30F);
        });
    }

    private static void leave() {
        FocusDriver.clear();
        if (StationFocus.active()) StationFocus.leave(true);
        Minecraft.getInstance().setScreen(null);
    }

    /** The material drawer: brings the first matching stack to hand when the step runs (the server moves it). */
    private static void drawer(Predicate<ItemStack> what) {
        FocusDriver.run(() -> {
            Inventory inv = Minecraft.getInstance().player.getInventory();
            if (what.test(inv.getSelected())) return;
            for (int slot = 0; slot < inv.items.size(); slot++) {
                if (what.test(inv.items.get(slot))) {
                    StationFocus.send(WorkAction.SELECT_SLOT, slot);
                    return;
                }
            }
        });
        FocusDriver.wait(3);
    }

    private static BlockPos at(BlockPos relative) {
        return base.offset(relative);
    }

    private static Predicate<ItemStack> is(Item item) {
        return stack -> stack.is(item);
    }

    private static void holdAndUse(Predicate<ItemStack> what, BlockPos relative) {
        Minecraft mc = Minecraft.getInstance();
        Inventory inv = mc.player.getInventory();
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            if (what.test(inv.getItem(slot))) {
                inv.selected = slot;
                BlockPos pos = at(relative);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false));
                return;
            }
        }
    }

    private static void onServer(Consumer<ServerPlayer> task) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) server.execute(() -> task.accept(server.getPlayerList().getPlayers().get(0)));
    }

    private static int count(Item item) {
        return Minecraft.getInstance().player.getInventory().countItem(item);
    }

    @Nullable
    private static TannersRackBlockEntity rack(BlockPos relative) {
        return Minecraft.getInstance().level.getBlockEntity(at(relative)) instanceof TannersRackBlockEntity rack ? rack : null;
    }

    @Nullable
    private static TannersVatBlockEntity vat(BlockPos relative) {
        return Minecraft.getInstance().level.getBlockEntity(at(relative)) instanceof TannersVatBlockEntity vat ? vat : null;
    }

    private static void shot(String name) {
        ClientSmokeTest.shot(Minecraft.getInstance(), name);
    }

    /** What the client sees, for a step that timed out. */
    private static String state() {
        Minecraft mc = Minecraft.getInstance();
        StringBuilder out = new StringBuilder("screen ").append(mc.screen == null ? "none" : mc.screen.getClass().getSimpleName())
                .append(", focus ").append(StationFocus.session() == null ? "none" : StationFocus.session().pos.toShortString())
                .append(", held ").append(mc.player.getMainHandItem());
        TannersRackBlockEntity raw = rack(RAW_RACK);
        if (raw != null) {
            out.append(", raw rack ").append(raw.mode());
            FleshingSurface surface = surface(RAW_RACK);
            if (surface != null) out.append(" clean ").append(Math.round(surface.coverage() * 100F)).append('%');
        }
        TannersRackBlockEntity frame = rack(FRAME_RACK);
        if (frame != null) out.append(", frame rack ").append(frame.mode()).append(' ').append(frame.finish()).append(" treated ").append(frame.treatment());
        for (BlockPos pos : List.of(FULL_VAT, EMPTY_VAT)) {
            TannersVatBlockEntity vat = vat(pos);
            if (vat != null) out.append(", vat ").append(pos.toShortString()).append(" water ").append(vat.hasWater()).append(" tannin ")
                    .append(vat.tannin()).append(" hides ").append(vat.slots().size()).append(" ready ").append(vat.finishedCount());
        }
        LeatherworkJob job = job(FREE_BENCH);
        out.append(", bench job ").append(job == null ? "none" : job.stage() + " stitch " + job.nextStitch() + "/" + job.stitchCount());
        BenchBehavior b = bench();
        if (b != null) out.append(", book ").append(b.bookOpen() ? "open" : "shut");
        out.append(", last refusal ").append(StationFocus.lastRefusal());
        return out.toString();
    }

    private FocusTour() {}
}
