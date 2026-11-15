package com.otectus.immersivetanning.smoketest;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.advancement.ModAdvancements;
import com.otectus.immersivetanning.block.LeatherworkersBenchBlock;
import com.otectus.immersivetanning.block.TannersRackBlock;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersVatBlockEntity;
import com.otectus.immersivetanning.block.TannersVatBlock;
import com.otectus.immersivetanning.blockentity.LeatherworkersBenchBlockEntity;
import com.otectus.immersivetanning.client.station.BenchBehavior;
import com.otectus.immersivetanning.client.station.StationFocus;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.item.ProcessedHideItem;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.leather.LeatherData;
import com.otectus.immersivetanning.leather.LeatherworkData;
import com.otectus.immersivetanning.minigame.FleshingSurface;
import com.otectus.immersivetanning.minigame.StitchPattern;
import com.otectus.immersivetanning.station.BenchGeometry;
import com.otectus.immersivetanning.station.RackGeometry;
import com.otectus.immersivetanning.station.VatGeometry;
import com.otectus.immersivetanning.work.LeatherworkJob;
import com.otectus.immersivetanning.work.WorkPattern;
import com.otectus.immersivetanning.registry.ModBlocks;
import com.otectus.immersivetanning.registry.ModItems;
import net.minecraft.Util;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The whole tannery played through the real client and network, in the dev client where Farmer's Delight is absent:
 * two cows killed at the player's feet (the hides picked up by standing on them), bark stripped from two logs with the
 * Tanner's Knife, then every craft at the stations in the focused view with real mouse events on the parts as they are
 * drawn: both hides scraped on the beam (the second laid over it with "Next hide"), water, bark and hides into the vat
 * through its opening and one paddle sweep, the hides lifted out at the hook, the first wet hide framed, the tension
 * lever moved to Supple, smoothed and seated, the second laced in by sneak-use with the same finish, the leather
 * collected, and boots chosen in the pattern book, cut, seated, stitched, tied off and collected, then worn. Each step
 * is input followed by a wait, with a timeout, for its synced result; the vat and racks are fast-forwarded with
 * {@code /immersive_tanning advance}, as an operator would. The milestones on the way are revoked first, so the run has
 * to earn each of them again.
 */
@Mod.EventBusSubscriber(modid = SmokeTestAgent.MOD_ID, value = Dist.CLIENT)
final class TanneryPlaythrough {
    /** Layout relative to the playthrough origin, where the player stands facing north. */
    private static final BlockPos LOG_A = new BlockPos(-2, 0, -2);
    private static final BlockPos LOG_B = new BlockPos(-1, 0, -2);
    private static final BlockPos RACK_A = new BlockPos(0, 0, -2);
    private static final BlockPos RACK_B = new BlockPos(1, 0, -2);
    private static final BlockPos VAT = new BlockPos(2, 0, -2);
    private static final BlockPos BENCH = new BlockPos(3, 0, -2);
    /** Ticks the vat and racks still need after the advance command; the run waits them out on world time. */
    private static final int LAST_TICKS = 20;
    private static final List<String> MILESTONES = List.of("root", ModAdvancements.BARK, ModAdvancements.FLESHED,
            ModAdvancements.TANNED, ModAdvancements.FINISHED, ModAdvancements.LEATHERWORK);

    /** One input and the synced state that shows it worked. {@code done} runs every tick and may give further input. */
    private record Step(String name, int timeoutTicks, Runnable action, BooleanSupplier done) {}

    private static Map<String, Object> checks;
    private static BlockPos origin;
    private static List<Step> steps = List.of();
    private static int index;
    private static int stepTicks;
    private static boolean over = true;

    private static int expectedIntegrity;
    private static volatile boolean serverChecked;

    static void start(Minecraft mc, BlockPos base, Map<String, Object> results) {
        checks = results;
        checks.put("playthrough_without_farmers_delight", !ModList.get().isLoaded("farmersdelight"));
        if (base == null || mc.getSingleplayerServer() == null) {
            checks.put("playthrough_started", false);
            return;
        }
        origin = base.offset(0, 0, 14);
        steps = steps();
        index = 0;
        stepTicks = 0;
        over = false;
    }

    /** Runs the current step for one client tick; true once the playthrough is over, passed or failed. */
    static boolean tick(Minecraft mc) {
        if (over) return true;
        FocusDriver.tick();
        Step step = steps.get(index);
        if (stepTicks++ == 0) step.action().run();
        if (step.done().getAsBoolean()) {
            checks.put("playthrough_" + step.name(), true);
            stepTicks = 0;
            if (++index == steps.size()) end();
        } else if (stepTicks > step.timeoutTicks()) {
            checks.put("playthrough_" + step.name(), false);
            checks.put("playthrough_state", state(mc));
            end();
        }
        return over;
    }

    private static void end() {
        over = true;
        FocusDriver.clear();
        Minecraft.getInstance().options.keyShift.setDown(false);
        if (StationFocus.active()) StationFocus.leave(true);
    }

    private static List<Step> steps() {
        int bark = 2 * ServerConfig.get(ServerConfig.BARK_PER_LOG);
        Item knife = ModItems.TANNERS_KNIFE.get();
        Item rawHide = ModItems.rawHide(HideSize.LARGE);
        Item scraped = ModItems.SCRAPED_HIDE.get();
        Item wet = ModItems.WET_TANNED_HIDE.get();
        Item supple = ModItems.leather(Finish.SUPPLE);
        List<Step> out = new ArrayList<>();
        out.add(new Step("cows_drop_hides_picked_up", 200, () -> {
            Minecraft.getInstance().setScreen(null);
            onServer(TanneryPlaythrough::stage);
        }, () -> count(rawHide) == 2 && count(knife) == 1));
        out.add(new Step("knife_strips_logs_for_bark", 60, () -> {
            holdAndUse(is(knife), LOG_A);
            use(LOG_B);
        }, () -> block(LOG_A).is(Blocks.STRIPPED_OAK_LOG) && block(LOG_B).is(Blocks.STRIPPED_OAK_LOG) && count(ModItems.BARK.get()) == bark));
        out.add(new Step("raw_hide_mounts_and_focus_leans_in", 80, () -> holdAndUse(is(rawHide), RACK_A),
                () -> rack(RACK_A) != null && rack(RACK_A).mode() == TannersRackBlockEntity.Mode.RAW && count(rawHide) == 1
                        && FocusDriver.focusedAt(at(RACK_A)) && StationFocus.ownsCamera()));
        out.add(new Step("fleshing_scrapes_first_hide", 1600, () -> flesh(RACK_A, true), () -> scrapedOn(RACK_A)));
        out.add(new Step("scraped_hide_picked_up", 80, () -> FocusDriver.click(at(RACK_A), FocusDriver.hide(0.5F, 0.55F)),
                () -> count(scraped) == 1 && rack(RACK_A).isEmpty()));
        out.add(new Step("next_hide_laid_over_the_beam", 80, () -> FocusDriver.click(at(RACK_A), new Vec3(8, 5.4, 1.8)),
                () -> rack(RACK_A).mode() == TannersRackBlockEntity.Mode.RAW && count(rawHide) == 0));
        out.add(new Step("fleshing_scrapes_second_hide", 1600, () -> flesh(RACK_A, false), () -> scrapedOn(RACK_A)));
        out.add(new Step("second_hide_picked_up_and_focus_left", 80, () -> {
            FocusDriver.click(at(RACK_A), FocusDriver.hide(0.5F, 0.55F));
            FocusDriver.key(GLFW.GLFW_KEY_ESCAPE);
        }, () -> count(scraped) >= 1 && rack(RACK_A).isEmpty() && !StationFocus.active() && Minecraft.getInstance().screen == null));
        out.add(new Step("vat_takes_water_and_leans_in", 80, () -> {
            List<Integer> integrity = new ArrayList<>();
            for (ItemStack stack : Minecraft.getInstance().player.getInventory().items) {
                if (stack.is(scraped)) for (int i = 0; i < stack.getCount(); i++) integrity.add(ProcessedHideItem.data(stack).integrity());
            }
            checks.put("playthrough_fleshed_integrity", integrity);
            holdAndUse(is(Items.WATER_BUCKET), VAT);
        }, () -> vat() != null && vat().hasWater() && count(Items.BUCKET) == 1 && FocusDriver.focusedAt(at(VAT))));
        out.add(new Step("bark_scattered_through_the_opening", 200, () -> {
            drawer(is(ModItems.BARK.get()));
            for (int i = 0; i < bark; i++) FocusDriver.click(at(VAT), VatGeometry.openingBox(true).getCenter());
        }, () -> vat().tannin() == bark && count(ModItems.BARK.get()) == 0));
        out.add(new Step("hides_lowered_through_the_opening", 200, () -> {
            drawer(is(scraped));
            FocusDriver.click(at(VAT), VatGeometry.openingBox(true).getCenter());
        }, () -> {
            if (count(scraped) > 0 && FocusDriver.idle()) {
                drawer(is(scraped)); // hides of two Integrities are two stacks
                FocusDriver.click(at(VAT), VatGeometry.openingBox(true).getCenter());
            }
            return vat().slots().size() == 2 && vat().waitingCount() == 0 && count(scraped) == 0;
        }));
        out.add(new Step("paddle_sweeps_the_batch_once", 200, () -> {
            FocusDriver.click(at(VAT), VatGeometry.paddleBox().getCenter());
            FocusDriver.drag(at(VAT), sweepPath(), 10);
            FocusDriver.run(() -> ClientSmokeTest.shot(Minecraft.getInstance(), "smoketest_playthrough_vat"));
        }, () -> vat().slots().stream().allMatch(TannersVatBlockEntity.Slot::tended)));
        out.add(new Step("vat_tans_on_world_time", 200, () -> advance(VAT, ServerConfig.get(ServerConfig.TANNING_TICKS) - LAST_TICKS),
                () -> vat().finishedCount() == 2));
        out.add(new Step("hook_lifts_the_wet_hides", 80, () -> {
            FocusDriver.click(at(VAT), VatGeometry.paddleBox().getCenter()); // the paddle goes back on the rim first
            FocusDriver.click(at(VAT), VatGeometry.HOOK);
        }, () -> count(wet) == 2 && vat().slots().isEmpty()));
        out.add(new Step("wet_hide_framed_with_focus", 80, () -> {
            FocusDriver.key(GLFW.GLFW_KEY_ESCAPE);
            FocusDriver.run(() -> holdAndUse(is(wet), RACK_A));
            FocusDriver.wait(12);
        }, () -> rack(RACK_A).mode() == TannersRackBlockEntity.Mode.FRAMING && FocusDriver.focusedAt(at(RACK_A)) && FocusDriver.idle()));
        out.add(new Step("lever_moves_to_supple", 80, () -> {
            float from = RackGeometry.detentDegrees(rack(RACK_A).finish());
            FocusDriver.drag(at(RACK_A), List.of(RackGeometry.leverEnd(from), RackGeometry.leverEnd((from + 38F) / 2F), RackGeometry.leverEnd(38F)), 4);
        }, () -> rack(RACK_A).finish() == Finish.SUPPLE));
        out.add(new Step("supple_smoothed_and_seated", 260, () -> {
            FocusDriver.click(at(RACK_A), RackGeometry.SLICKER);
            for (int pass = 0; pass < 5; pass++) {
                float y = 0.2F + pass * 0.15F;
                FocusDriver.drag(at(RACK_A), FocusDriver.line(FocusDriver.frame(0.12F, y), FocusDriver.frame(0.88F, y + 0.05F), 16), 6);
            }
            FocusDriver.run(() -> ClientSmokeTest.shot(Minecraft.getInstance(), "smoketest_playthrough_finish"));
            FocusDriver.click(at(RACK_A), RackGeometry.LEVER_PIVOT);
        }, () -> drying(RACK_A, Finish.SUPPLE) && count(wet) == 1));
        out.add(new Step("sneak_use_repeats_supple", 80, () -> FocusDriver.key(GLFW.GLFW_KEY_ESCAPE), () -> {
            // Sneak is held once the view is left: leaving grabs the mouse, which reads every key from the keyboard again.
            if (!StationFocus.active() && FocusDriver.idle()) Minecraft.getInstance().options.keyShift.setDown(true);
            if (stepTicks == 10) holdAndUse(is(wet), RACK_B); // by now the server has heard the sneak
            return drying(RACK_B, Finish.SUPPLE) && count(wet) == 0;
        }));
        out.add(new Step("racks_dry_on_world_time", 200, () -> {
            advance(RACK_A, ServerConfig.get(ServerConfig.DRYING_TICKS) - LAST_TICKS);
            advance(RACK_B, ServerConfig.get(ServerConfig.DRYING_TICKS) - LAST_TICKS);
        }, () -> rack(RACK_A).isDry() && rack(RACK_A).isSettled() && rack(RACK_B).isDry() && rack(RACK_B).isSettled()));
        out.add(new Step("racks_give_supple_leather", 80, () -> {
            ClientSmokeTest.shot(Minecraft.getInstance(), "smoketest_playthrough_racks");
            holdAndUse(ItemStack::isEmpty, RACK_A);
            use(RACK_B);
        }, () -> {
            if (count(supple) != 6 || !rack(RACK_A).isEmpty() || !rack(RACK_B).isEmpty()) {
                Minecraft.getInstance().options.keyShift.setDown(true);
                return false;
            }
            Minecraft.getInstance().options.keyShift.setDown(false);
            return true;
        }));
        out.add(new Step("bench_book_opens", 120, () -> {
            holdAndUse(is(ModItems.AWL.get()), BENCH);
            FocusDriver.wait(12);
            FocusDriver.click(at(BENCH), BenchGeometry.BOOK.getCenter());
        }, () -> FocusDriver.focusedAt(at(BENCH)) && StationFocus.session().behavior() instanceof BenchBehavior b
                && b.bookOffers(ImmersiveTanning.id("leatherworking/leather_boots"))));
        out.add(new Step("boots_prepared_from_the_book", 80, () -> {
            List<Integer> integrity = new ArrayList<>();
            for (ItemStack stack : Minecraft.getInstance().player.getInventory().items) {
                LeatherData.get(stack).ifPresent(data -> {
                    for (int i = 0; i < stack.getCount(); i++) integrity.add(data.integrity());
                });
            }
            integrity.sort(Comparator.reverseOrder());
            expectedIntegrity = Math.round(integrity.stream().limit(4).mapToInt(Integer::intValue).sum() / 4F);
            ClientSmokeTest.shot(Minecraft.getInstance(), "smoketest_playthrough_bench");
            if (StationFocus.session().behavior() instanceof BenchBehavior b) b.prepareFromBook(ImmersiveTanning.id("leatherworking/leather_boots"), Finish.SUPPLE);
        }, () -> job() != null && count(supple) == 2));
        out.add(new Step("cuts_traced_with_the_knife", 600, TanneryPlaythrough::cut,
                () -> job() != null && job().stage() == LeatherworkJob.Stage.ASSEMBLY));
        out.add(new Step("panels_seated_on_the_jig", 400, TanneryPlaythrough::seat,
                () -> job() != null && job().stage() == LeatherworkJob.Stage.STITCHING));
        out.add(new Step("seams_stitched_and_tied", 800, TanneryPlaythrough::stitch, () -> {
            LeatherworkersBenchBlockEntity bench = benchEntity();
            return bench != null && bench.job() == null && bench.hasOutput();
        }));
        out.add(new Step("boots_collected_from_the_pad", 80, () -> {
            FocusDriver.click(at(BENCH), BenchGeometry.OUTPUT_PAD.getCenter());
            FocusDriver.key(GLFW.GLFW_KEY_ESCAPE);
        }, () -> count(Items.LEATHER_BOOTS) == 1 && !StationFocus.active()));
        out.add(new Step("boots_carry_the_work", 60, TanneryPlaythrough::checkBoots, () -> serverChecked));
        out.add(new Step("worn_boots_speed_the_player", 60, () -> onServer(TanneryPlaythrough::equipBoots), TanneryPlaythrough::wornSpeed));
        out.add(new Step("inventory_shows_the_boots", 40,
                () -> Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player)), () -> {
            if (stepTicks < 12) return false;
            ClientSmokeTest.shot(Minecraft.getInstance(), "smoketest_playthrough_boots");
            Minecraft.getInstance().setScreen(null);
            return true;
        }));
        return out;
    }

    /** Server side: a clean floor with two logs and the stations, the milestones revoked, two cows' hides at the player's feet. */
    private static void stage(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (int x = -4; x <= 5; x++) for (int z = -4; z <= 3; z++) {
            level.setBlockAndUpdate(origin.offset(x, -1, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
            for (int y = 0; y <= 3; y++) level.setBlockAndUpdate(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(at(LOG_A), Blocks.OAK_LOG.defaultBlockState());
        level.setBlockAndUpdate(at(LOG_B), Blocks.OAK_LOG.defaultBlockState());
        BlockState rack = ModBlocks.TANNERS_RACK.get().defaultBlockState().setValue(TannersRackBlock.FACING, Direction.SOUTH);
        level.setBlockAndUpdate(at(RACK_A), rack);
        level.setBlockAndUpdate(at(RACK_B), rack);
        level.setBlockAndUpdate(at(VAT), ModBlocks.TANNERS_VAT.get().defaultBlockState().setValue(TannersVatBlock.FACING, Direction.SOUTH));
        level.setBlockAndUpdate(at(BENCH), ModBlocks.LEATHERWORKERS_BENCH.get().defaultBlockState()
                .setValue(LeatherworkersBenchBlock.FACING, Direction.SOUTH));
        for (String name : MILESTONES) {
            Advancement advancement = player.getServer().getAdvancements().getAdvancement(ImmersiveTanning.id(name));
            if (advancement == null) continue;
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
            for (String criterion : progress.getCompletedCriteria()) player.getAdvancements().revoke(advancement, criterion);
        }
        player.getInventory().clearContent();
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(level, origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 180F, 35F);
        for (int i = 0; i < 2; i++) {
            Cow cow = EntityType.COW.spawn(level, origin, MobSpawnType.COMMAND);
            if (cow != null) cow.hurt(level.damageSources().playerAttack(player), 1000F);
        }
        player.getInventory().setItem(0, new ItemStack(ModItems.TANNERS_KNIFE.get()));
        player.getInventory().setItem(1, new ItemStack(ModItems.AWL.get()));
        player.getInventory().setItem(2, new ItemStack(Items.WATER_BUCKET));
        player.inventoryMenu.sendAllDataToRemote();
    }

    // ------------------------------------------------------------------ fleshing

    /**
     * Careful strokes through the focus screen: parallel passes along each flesh patch's grain on the client's copy of
     * the hide, each a press, a drag over a few ticks and a release, then "finish roughly" if a few cells remain.
     */
    private static void flesh(BlockPos relative, boolean shot) {
        TannersRackBlockEntity rack = rack(relative);
        if (rack == null || rack.work() == null) return;
        FleshingSurface surface = rack.work().surface();
        List<List<Vec3>> strokes = new ArrayList<>();
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
                strokes.add(stroke);
            }
        }
        BlockPos pos = at(relative);
        // Strokes queued after the hide is done (scraped clean, or finished roughly) are skipped, not pressed on what is
        // there by then.
        java.util.function.BooleanSupplier raw = () -> rack(relative) != null && rack(relative).mode() == TannersRackBlockEntity.Mode.RAW;
        for (int i = 0; i < strokes.size(); i++) {
            FocusDriver.drag(pos, strokes.get(i), 4, null, raw);
            if (shot && i == strokes.size() / 2) FocusDriver.run(() -> ClientSmokeTest.shot(Minecraft.getInstance(), "smoketest_playthrough_fleshing"));
        }
        FocusDriver.run(() -> {
            if (rack(relative) != null && rack(relative).mode() == TannersRackBlockEntity.Mode.RAW) {
                var screen = FocusDriver.screen();
                if (screen != null) {
                    screen.keyPressed(GLFW.GLFW_KEY_F, 0, 0);
                    screen.keyReleased(GLFW.GLFW_KEY_F, 0, 0);
                }
            }
        });
    }

    private static boolean scrapedOn(BlockPos relative) {
        TannersRackBlockEntity rack = rack(relative);
        return rack != null && rack.mode() == TannersRackBlockEntity.Mode.SCRAPED;
    }

    // ------------------------------------------------------------------ vat and bench input

    /**
     * The material drawer: brings the first matching stack to hand (the server moves it), as a click in the drawer
     * does. The slot is looked up when the step runs, after earlier queued input has moved things.
     */
    private static void drawer(Predicate<ItemStack> what) {
        FocusDriver.run(() -> {
            Inventory inv = Minecraft.getInstance().player.getInventory();
            if (what.test(inv.getSelected())) return;
            for (int slot = 0; slot < inv.items.size(); slot++) {
                if (what.test(inv.items.get(slot))) {
                    StationFocus.send(com.otectus.immersivetanning.work.WorkAction.SELECT_SLOT, slot);
                    return;
                }
            }
        });
        FocusDriver.wait(3);
    }

    /** One slow sweep there and back across the liquor: well past the 0.9 surface widths a sweep must cover. */
    static List<Vec3> sweepPath() {
        List<Vec3> out = new ArrayList<>(FocusDriver.line(VatGeometry.surfacePoint(0.12F, 0.3F, true), VatGeometry.surfacePoint(0.88F, 0.45F, true), 14));
        out.addAll(FocusDriver.line(VatGeometry.surfacePoint(0.88F, 0.45F, true), VatGeometry.surfacePoint(0.15F, 0.72F, true), 14).subList(1, 15));
        return out;
    }

    private static LeatherworkersBenchBlockEntity benchEntity() {
        return Minecraft.getInstance().level.getBlockEntity(at(BENCH)) instanceof LeatherworkersBenchBlockEntity bench ? bench : null;
    }

    private static LeatherworkJob job() {
        LeatherworkersBenchBlockEntity bench = benchEntity();
        return bench == null ? null : bench.job();
    }

    /** Each cut traced with the knife from its start, a press-drag-release on the leather as drawn. */
    private static void cut() {
        LeatherworkJob job = job();
        if (job == null) return;
        for (int c = 0; c < job.pattern().cuts().size(); c++) {
            float[] path = job.pattern().cutPath(c);
            List<Vec3> points = new ArrayList<>();
            int n = 20;
            for (int i = 0; i <= n; i++) {
                float[] p = StitchPattern.pointAt(path, i / (float) n);
                points.add(FocusDriver.plane(p[0], p[1]));
            }
            FocusDriver.drag(at(BENCH), points, 6);
        }
        FocusDriver.run(() -> ClientSmokeTest.shot(Minecraft.getInstance(), "smoketest_playthrough_cut"));
    }

    /** Each freed panel lifted from the far edge and let go over its outline on the jig. */
    private static void seat() {
        LeatherworkJob job = job();
        if (job == null) return;
        WorkPattern pattern = job.pattern();
        for (int p = 0; p < pattern.panels().size(); p++) {
            float[] from = pattern.staging(p);
            WorkPattern.Placement to = pattern.placement(p);
            FocusDriver.drag(at(BENCH), FocusDriver.line(FocusDriver.plane(from[0], from[1]), FocusDriver.plane(to.x(), to.y()), 8), 6,
                    () -> FocusDriver.rotatePanel(to.rot() - from[2]));
        }
    }

    /** Each stitch: the awl pressed through the lit holes, the thread drawn straight away from the seam, released; then the knot. */
    private static void stitch() {
        LeatherworkJob job = job();
        if (job == null) return;
        for (int a = 0; a < job.stitchCount(); a++) {
            float[] f = job.stitchFrame(a);
            float pull = job.pattern().joinOf(a).pullIdeal();
            List<Vec3> path = new ArrayList<>();
            for (int i = 0; i <= 6; i++) path.add(FocusDriver.plane(f[4] + f[6] * pull * i / 6F, f[5] + f[7] * pull * i / 6F));
            FocusDriver.drag(at(BENCH), path, 5);
            if (a == 2) FocusDriver.run(() -> ClientSmokeTest.shot(Minecraft.getInstance(), "smoketest_playthrough_stitching"));
        }
        float[] last = job.stitchFrame(job.stitchCount() - 1);
        FocusDriver.wait(4);
        FocusDriver.click(at(BENCH), FocusDriver.plane(last[4] + 0.04F, last[5] + 0.04F).add(0, 0.25, 0));
    }

    // ------------------------------------------------------------------ the result

    private static void checkBoots() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(null);
        ItemStack boots = ItemStack.EMPTY;
        for (ItemStack stack : mc.player.getInventory().items) if (stack.is(Items.LEATHER_BOOTS)) boots = stack;
        LeatherworkData data = LeatherworkData.get(boots).orElse(null);
        checks.put("playthrough_boots_detail", data + ", expected Integrity " + expectedIntegrity);
        checks.put("playthrough_boots_supple_and_well_made", data != null && data.finish() == Finish.SUPPLE && data.trait() == null
                && data.integrity() == expectedIntegrity && data.workmanship() >= 90
                && LeatherworkData.crafterId(boots).map(mc.player.getUUID()::equals).orElse(false));
        checks.put("playthrough_bench_leftovers", count(ModItems.leather(Finish.SUPPLE)) == 2
                && count(ModItems.LEATHER_SCRAPS.get()) == 4 / ServerConfig.get(ServerConfig.LEATHER_PER_OFFCUT));
        serverChecked = false;
        onServer(player -> {
            Map<String, Boolean> earned = new LinkedHashMap<>();
            for (String name : MILESTONES) {
                Advancement advancement = player.getServer().getAdvancements().getAdvancement(ImmersiveTanning.id(name));
                earned.put(name, advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone());
            }
            checks.put("playthrough_milestones", earned);
            checks.put("playthrough_milestones_earned", !earned.containsValue(false));
            serverChecked = true;
        });
    }

    private static void equipBoots(ServerPlayer player) {
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.items.size(); slot++) {
            if (inv.items.get(slot).is(Items.LEATHER_BOOTS)) {
                player.setItemSlot(EquipmentSlot.FEET, inv.removeItemNoUpdate(slot));
                return;
            }
        }
    }

    /** The client player, wearing the boots, moves faster by the Supple finish's leatherwork modifier. */
    private static boolean wornSpeed() {
        AttributeInstance speed = Minecraft.getInstance().player.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed != null && speed.getModifiers().stream().anyMatch(m -> "Leatherwork".equals(m.getName()) && m.getAmount() > 0);
    }

    // ------------------------------------------------------------------ input and state

    private static BlockPos at(BlockPos relative) {
        return origin.offset(relative);
    }

    private static Predicate<ItemStack> is(Item item) {
        return stack -> stack.is(item);
    }

    /** Selects the first hotbar slot whose stack matches, as a player scrolls to it; the next use sends the selection. */
    private static boolean hold(Predicate<ItemStack> what) {
        Inventory inv = Minecraft.getInstance().player.getInventory();
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            if (what.test(inv.getItem(slot))) {
                inv.selected = slot;
                return true;
            }
        }
        return false;
    }

    /** A right-click on the top of the block with the held item, through the client's own interaction path. */
    private static void use(BlockPos relative) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos pos = at(relative);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false));
    }

    private static void holdAndUse(Predicate<ItemStack> what, BlockPos relative) {
        if (hold(what)) use(relative);
    }

    /** The operator's fast-forward, typed by the client as a command. */
    private static void advance(BlockPos relative, int ticks) {
        BlockPos pos = at(relative);
        Minecraft.getInstance().player.connection.sendCommand("immersive_tanning advance " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + ticks);
    }

    private static void onServer(Consumer<ServerPlayer> task) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) server.execute(() -> task.accept(server.getPlayerList().getPlayers().get(0)));
    }

    private static int count(Item item) {
        return Minecraft.getInstance().player.getInventory().countItem(item);
    }

    private static BlockState block(BlockPos relative) {
        return Minecraft.getInstance().level.getBlockState(at(relative));
    }

    private static TannersRackBlockEntity rack(BlockPos relative) {
        return Minecraft.getInstance().level.getBlockEntity(at(relative)) instanceof TannersRackBlockEntity rack ? rack : null;
    }

    private static TannersVatBlockEntity vat() {
        return Minecraft.getInstance().level.getBlockEntity(at(VAT)) instanceof TannersVatBlockEntity vat ? vat : null;
    }

    private static boolean drying(BlockPos relative, Finish finish) {
        TannersRackBlockEntity rack = rack(relative);
        return rack != null && rack.mode() == TannersRackBlockEntity.Mode.DRYING && rack.finish() == finish;
    }

    /** What the client sees, for a step that timed out (and, read across threads for diagnosis only, the server's inventory). */
    private static String state(Minecraft mc) {
        StringBuilder out = new StringBuilder("screen ").append(mc.screen == null ? "none" : mc.screen.getClass().getSimpleName())
                .append(", held ").append(mc.player.getMainHandItem()).append(", carrying");
        for (ItemStack stack : mc.player.getInventory().items) if (!stack.isEmpty()) out.append(' ').append(stack);
        IntegratedServer server = mc.getSingleplayerServer();
        ServerPlayer onServer = server == null ? null : server.getPlayerList().getPlayer(mc.player.getUUID());
        if (onServer != null) {
            out.append(", server carrying");
            for (ItemStack stack : onServer.getInventory().items) if (!stack.isEmpty()) out.append(' ').append(stack);
        }
        for (BlockPos pos : List.of(RACK_A, RACK_B)) {
            TannersRackBlockEntity rack = rack(pos);
            out.append(", rack ").append(pos.toShortString()).append(rack == null ? " missing"
                    : " " + rack.mode() + " " + rack.finish() + (rack.isDry() ? " dry" : "") + (rack.isSettled() ? " settled" : ""));
        }
        TannersVatBlockEntity vat = vat();
        if (vat != null) {
            out.append(", vat water ").append(vat.hasWater()).append(" tannin ").append(vat.tannin()).append(" hides ").append(vat.slots().size())
                    .append(" waiting ").append(vat.waitingCount()).append(" ready ").append(vat.finishedCount());
        }
        LeatherworkJob job = job();
        if (job != null) {
            out.append(", bench job ").append(job.stage()).append(" cut ").append(job.activeCut()).append(" stitch ").append(job.nextStitch())
                    .append('/').append(job.stitchCount());
        }
        out.append(", last refusal ").append(StationFocus.lastRefusal());
        return out.toString();
    }

    private TanneryPlaythrough() {}
}
