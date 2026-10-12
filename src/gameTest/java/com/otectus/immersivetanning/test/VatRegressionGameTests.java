package com.otectus.immersivetanning.test;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.block.TannersVatBlock;
import com.otectus.immersivetanning.blockentity.TannersVatBlockEntity;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.registry.ModBlocks;
import com.otectus.immersivetanning.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * The Tanner's Vat regression matrix: resource order, exact tannin cost, a large hide never blocking a smaller one,
 * draining and refilling, automation, persistence and config boundaries. Player cases go through the real block
 * interaction with a fake player, so held stacks shrink exactly as they would in play.
 */
@GameTestHolder(ImmersiveTanning.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VatRegressionGameTests {

    private static TannersVatBlockEntity vat(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, ModBlocks.TANNERS_VAT.get());
        return (TannersVatBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
    }

    /** Right-clicks the vat at {@code pos} holding {@code stack}; returns what is left in hand. */
    private static ItemStack use(GameTestHelper h, FakePlayer player, BlockPos pos, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos abs = h.absolutePos(pos);
        h.getLevel().getBlockState(abs).use(h.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(abs.getX() + 0.5, abs.getY() + 0.9, abs.getZ() + 0.5), Direction.UP, abs, false));
        return player.getMainHandItem();
    }

    private static ItemStack water() {
        return new ItemStack(Items.WATER_BUCKET);
    }

    private static ItemStack bark(int count) {
        return new ItemStack(ModItems.BARK.get(), count);
    }

    private static ItemStack hide(HideSize size, int count) {
        return ModItems.SCRAPED_HIDE.get().create(size, 70, null).copyWithCount(count);
    }

    private static int started(TannersVatBlockEntity vat) {
        int n = 0;
        for (TannersVatBlockEntity.Slot s : vat.slots()) if (s.started()) n++;
        return n;
    }

    private static String key(Component c) {
        return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(c);
    }

    @GameTest(template = "empty")
    public static void waterThenBarkThenEachSizeStartsAtInsertion(GameTestHelper h) {
        BlockPos pos = new BlockPos(2, 1, 2);
        TannersVatBlockEntity vat = vat(h, pos);
        FakePlayer player = TestSupport.player(h);
        use(h, player, pos, water());
        h.assertTrue(use(h, player, pos, bark(4)).isEmpty() && vat.tannin() == 4, "Four bark become four charges");
        int[] costs = {1, 1, 2};
        HideSize[] sizes = {HideSize.SMALL, HideSize.MEDIUM, HideSize.LARGE};
        int tannin = 4;
        for (int i = 0; i < sizes.length; i++) {
            h.assertTrue(use(h, player, pos, hide(sizes[i], 1)).isEmpty(), sizes[i] + ": the hide leaves the hand");
            tannin -= costs[i];
            h.assertTrue(vat.slots().get(i).started() && vat.tannin() == tannin,
                    sizes[i] + " starts on insertion and costs " + costs[i] + ", tannin now " + vat.tannin());
        }
        h.assertTrue(vat.waitingCount() == 0 && TannersVatBlock.waitReason(vat) == null, "Nothing waits, so no reason is given");
        long before = h.getLevel().getGameTime();
        h.runAfterDelay(20, () -> {
            long now = h.getLevel().getGameTime();
            for (TannersVatBlockEntity.Slot s : vat.slots()) {
                h.assertTrue(s.progress(now) > 0F && s.remainingTicks(now) == s.duration() - (now - before),
                        "Every hide keeps tanning by world time without touching the vat");
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void everyResourceOrderEndsInTheSameState(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        String[][] orders = {{"water", "bark", "hide"}, {"bark", "water", "hide"}, {"hide", "bark", "water"}, {"hide", "water", "bark"}};
        BlockPos[] positions = {new BlockPos(0, 1, 0), new BlockPos(2, 1, 0), new BlockPos(4, 1, 0), new BlockPos(0, 1, 4)};
        for (int o = 0; o < orders.length; o++) {
            BlockPos pos = positions[o];
            TannersVatBlockEntity vat = vat(h, pos);
            for (String step : orders[o]) {
                switch (step) {
                    case "water" -> use(h, player, pos, water());
                    case "bark" -> use(h, player, pos, bark(3));
                    default -> {
                        use(h, player, pos, hide(HideSize.LARGE, 1));
                        use(h, player, pos, hide(HideSize.SMALL, 1));
                    }
                }
            }
            String order = String.join(" -> ", orders[o]);
            h.assertTrue(vat.slots().size() == 2 && started(vat) == 2 && vat.tannin() == 0 && vat.hasWater(),
                    order + ": both hides tanning, all three bark spent, got started=" + started(vat) + " tannin=" + vat.tannin());
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void largeHideWithOneBarkWaitsAndSaysWhy(GameTestHelper h) {
        BlockPos pos = new BlockPos(2, 1, 2);
        TannersVatBlockEntity vat = vat(h, pos);
        FakePlayer player = TestSupport.player(h);
        use(h, player, pos, water());
        use(h, player, pos, bark(1));
        use(h, player, pos, hide(HideSize.LARGE, 1));
        h.assertTrue(vat.waitingCount() == 1 && vat.tannin() == 1 && vat.missingTannin() == 1,
                "A large hide waits on one bark without spending it");
        Component reason = TannersVatBlock.waitReason(vat);
        h.assertTrue("message.immersive_tanning.vat_needs_tannin".equals(key(reason))
                        && ((TranslatableContents) reason.getContents()).getArgs()[0].equals(1),
                "The reason names the one missing bark, got " + key(reason));
        vat.advance(ServerConfig.get(ServerConfig.TANNING_TICKS) * 2);
        h.assertTrue(vat.takeFinished().isEmpty() && vat.finishedCount() == 0, "A waiting hide never finishes");
        use(h, player, pos, bark(1));
        h.assertTrue(started(vat) == 1 && vat.tannin() == 0, "One more bark starts it");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void waitingLargeHideDoesNotBlockASmallOne(GameTestHelper h) {
        BlockPos pos = new BlockPos(2, 1, 2);
        TannersVatBlockEntity vat = vat(h, pos);
        FakePlayer player = TestSupport.player(h);
        use(h, player, pos, water());
        use(h, player, pos, bark(1));
        use(h, player, pos, hide(HideSize.LARGE, 1));
        use(h, player, pos, hide(HideSize.SMALL, 1));
        h.assertTrue(!vat.slots().get(0).started() && vat.slots().get(1).started() && vat.tannin() == 0,
                "The small hide behind a waiting large one starts with the charge that is there");
        h.assertTrue(vat.missingTannin() == 2, "The large hide still needs its two charges, got " + vat.missingTannin());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void mixedBatchSpendsEveryChargeExactlyOnce(GameTestHelper h) {
        TannersVatBlockEntity vat = vat(h, new BlockPos(2, 1, 2));
        HideSize[] batch = {HideSize.LARGE, HideSize.SMALL, HideSize.MEDIUM, HideSize.LARGE};
        for (HideSize size : batch) vat.insertHide(new HideData(size, 60, null));
        vat.fillWater();
        int added = vat.addTannin(3);
        h.assertTrue(started(vat) == 2 && vat.tannin() == 0, "3 charges: large (2) and small (1) start, got " + started(vat));
        added += vat.addTannin(1);
        h.assertTrue(started(vat) == 3 && vat.tannin() == 0, "One more starts the medium hide");
        added += vat.addTannin(1);
        h.assertTrue(started(vat) == 3 && vat.tannin() == 1 && vat.missingTannin() == 1, "One charge is not enough for the last large hide");
        added += vat.addTannin(1);
        h.assertTrue(started(vat) == 4 && vat.tannin() == 0, "Its second charge starts it");
        int spent = 0;
        for (TannersVatBlockEntity.Slot s : vat.slots()) spent += s.hide().size().tannin();
        h.assertTrue(added == 6 && spent == added, "Every charge added was spent exactly once: added " + added + ", spent " + spent);
        long now = h.getLevel().getGameTime();
        for (TannersVatBlockEntity.Slot s : vat.slots()) h.assertTrue(s.started() && s.progress(now) < 1F, "Nothing is tanned yet");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void automationSimulatesWithoutChangesAndRunsTheSameTransition(GameTestHelper h) {
        TannersVatBlockEntity vat = vat(h, new BlockPos(2, 1, 2));
        IItemHandler items = vat.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
        IFluidHandler fluids = vat.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);

        ItemStack left = items.insertItem(0, hide(HideSize.MEDIUM, 6), true);
        h.assertTrue(left.getCount() == 6 - vat.capacity() && vat.slots().isEmpty(), "Simulated hides report the remainder and change nothing");
        left = items.insertItem(0, bark(3), true);
        h.assertTrue(left.isEmpty() && vat.tannin() == 0, "Simulated bark changes nothing");
        h.assertTrue(fluids.fill(new FluidStack(Fluids.WATER, 400), IFluidHandler.FluidAction.SIMULATE) == 400 && vat.water() == 0,
                "Simulated water changes nothing");

        items.insertItem(0, hide(HideSize.MEDIUM, 2), false);
        items.insertItem(0, bark(1), false);
        h.assertTrue(vat.slots().size() == 2 && vat.tannin() == 1 && started(vat) == 0, "Without water the hides wait");
        h.assertTrue(fluids.fill(new FluidStack(Fluids.WATER, 600), IFluidHandler.FluidAction.EXECUTE) == 600 && started(vat) == 0,
                "A partial fill is not yet a bucket");
        int filled = fluids.fill(new FluidStack(Fluids.WATER, 600), IFluidHandler.FluidAction.EXECUTE);
        h.assertTrue(filled == 400 && vat.hasWater() && started(vat) == 1 && vat.tannin() == 0,
                "Topping up to a bucket takes only 400 mB and starts what the tannin pays for, filled " + filled);
        items.insertItem(0, bark(1), false);
        h.assertTrue(started(vat) == 2 && vat.tannin() == 0, "More bark from a hopper starts the second hide");
        vat.advance(ServerConfig.get(ServerConfig.TANNING_TICKS));
        h.assertTrue(items.extractItem(1, 1, true).is(ModItems.WET_TANNED_HIDE.get()) && vat.finishedCount() == 2,
                "Simulated extraction leaves the hide in the vat");
        h.assertTrue(!items.extractItem(1, 1, false).isEmpty() && !items.extractItem(1, 1, false).isEmpty()
                && items.extractItem(1, 1, false).isEmpty() && vat.slots().isEmpty(), "Exactly two tanned hides come out");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void drainingPausesStartedHidesAndRefillResumesWithoutMoreBark(GameTestHelper h) {
        BlockPos pos = new BlockPos(2, 1, 2);
        TannersVatBlockEntity vat = vat(h, pos);
        FakePlayer player = TestSupport.player(h);
        use(h, player, pos, water());
        use(h, player, pos, bark(2));
        use(h, player, pos, hide(HideSize.LARGE, 1));
        TannersVatBlockEntity.Slot slot = vat.slots().get(0);
        long[] remaining = new long[1];
        float[] progress = new float[1];
        h.startSequence()
                .thenExecuteAfter(20, () -> {
                    h.assertTrue(use(h, player, pos, new ItemStack(Items.BUCKET)).is(Items.WATER_BUCKET), "The bucket takes the liquor");
                    long now = h.getLevel().getGameTime();
                    h.assertTrue(slot.paused() && !vat.hasWater(), "The started hide pauses when the water leaves");
                    remaining[0] = slot.remainingTicks(now);
                    progress[0] = slot.progress(now);
                    h.assertTrue(progress[0] > 0F && remaining[0] < slot.duration(), "It keeps the progress it made");
                    vat.advance(ServerConfig.get(ServerConfig.TANNING_TICKS) * 2);
                    h.assertTrue(vat.finishedCount() == 0, "A paused hide cannot finish, even when its clock is pushed");
                })
                .thenExecuteAfter(30, () -> {
                    long now = h.getLevel().getGameTime();
                    h.assertTrue(slot.progress(now) == progress[0] && slot.remainingTicks(now) == remaining[0],
                            "No progress while the vat is dry");
                    TannersVatBlockEntity.serverTick(h.getLevel(), h.absolutePos(pos), vat.getBlockState(), vat);
                    h.assertTrue(vat.finishedCount() == 0, "The server tick announces nothing while dry");
                    use(h, player, pos, water());
                    now = h.getLevel().getGameTime();
                    h.assertTrue(!slot.paused() && slot.started() && vat.tannin() == 0 && slot.remainingTicks(now) == remaining[0],
                            "A refill resumes it for exactly its remaining time and charges nothing");
                    vat.advance((int) remaining[0]);
                    List<ItemStack> out = vat.takeFinished();
                    h.assertTrue(out.size() == 1 && out.get(0).is(ModItems.WET_TANNED_HIDE.get()) && vat.slots().isEmpty(),
                            "It comes out tanned, once");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void saveAndReloadKeepTimingWithoutRecharge(GameTestHelper h) {
        BlockPos pos = new BlockPos(2, 1, 2);
        TannersVatBlockEntity vat = vat(h, pos);
        vat.fillWater();
        vat.addTannin(5);
        vat.insertHide(new HideData(HideSize.LARGE, 50, null));
        vat.insertHide(new HideData(HideSize.SMALL, 50, null));
        vat.advance(ServerConfig.get(ServerConfig.TANNING_TICKS));
        vat.insertHide(new HideData(HideSize.MEDIUM, 50, null));
        TannersVatBlockEntity.serverTick(h.getLevel(), h.absolutePos(pos), vat.getBlockState(), vat);
        long now = h.getLevel().getGameTime();
        CompoundTag saved = vat.saveWithoutMetadata();
        int tannin = vat.tannin();
        int finished = vat.finishedCount();

        vat.load(saved);
        TannersVatBlockEntity.serverTick(h.getLevel(), h.absolutePos(pos), vat.getBlockState(), vat);
        h.assertTrue(vat.tannin() == tannin && vat.finishedCount() == finished && finished == 2 && started(vat) == 3,
                "After a reload nothing is charged again and finished hides stay finished");
        h.assertTrue(vat.slots().get(2).remainingTicks(now) == vat.slots().get(2).duration(), "The running hide's clock is unchanged");
        h.assertTrue(vat.takeFinished().size() == 2 && vat.takeFinished().isEmpty(), "Finished hides come out once");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void olderDryVatPausesAndStuckSmallHideStartsOnLoad(GameTestHelper h) {
        BlockPos pos = new BlockPos(2, 1, 2);
        TannersVatBlockEntity vat = vat(h, pos);
        long now = h.getLevel().getGameTime();
        int duration = ServerConfig.get(ServerConfig.TANNING_TICKS);
        // A vat saved by 1.0.0 after draining: no version, a started hide whose clock kept running.
        CompoundTag old = new CompoundTag();
        old.putInt("Water", 0);
        old.putInt("Tannin", 0);
        ListTag hides = new ListTag();
        CompoundTag running = new HideData(HideSize.MEDIUM, 60, null).save();
        running.putBoolean("Started", true);
        running.putLong("Start", now - 100);
        running.putInt("Duration", duration);
        hides.add(running);
        old.put("Hides", hides);
        vat.load(old);
        TannersVatBlockEntity.serverTick(h.getLevel(), h.absolutePos(pos), vat.getBlockState(), vat);
        TannersVatBlockEntity.Slot slot = vat.slots().get(0);
        h.assertTrue(slot.paused() && slot.remainingTicks(now + 1000) == duration - 100, "An old dry vat's hide pauses where it was");

        // A vat saved by 1.0.0 with water, one charge and a small hide stuck behind a waiting large one.
        BlockPos second = new BlockPos(0, 1, 0);
        TannersVatBlockEntity stuck = vat(h, second);
        CompoundTag fifo = new CompoundTag();
        fifo.putInt("Water", TannersVatBlockEntity.WATER_CAPACITY);
        fifo.putInt("Tannin", 1);
        ListTag queue = new ListTag();
        queue.add(new HideData(HideSize.LARGE, 60, null).save());
        queue.add(new HideData(HideSize.SMALL, 60, null).save());
        fifo.put("Hides", queue);
        stuck.load(fifo);
        TannersVatBlockEntity.serverTick(h.getLevel(), h.absolutePos(second), stuck.getBlockState(), stuck);
        h.assertTrue(!stuck.slots().get(0).started() && stuck.slots().get(1).started() && stuck.tannin() == 0,
                "On load the stuck small hide starts with the charge that was waiting");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void maxTanninOfOneStillStartsLargeHides(GameTestHelper h) {
        int configured = ServerConfig.VAT_MAX_TANNIN.get();
        try {
            ServerConfig.VAT_MAX_TANNIN.set(1);
            TannersVatBlockEntity vat = vat(h, new BlockPos(2, 1, 2));
            h.assertTrue(vat.maxTannin() == 2, "A limit of 1 acts as 2, got " + vat.maxTannin());
            vat.fillWater();
            vat.insertHide(new HideData(HideSize.LARGE, 60, null));
            int taken = vat.addTannin(5);
            h.assertTrue(taken == 2 && started(vat) == 1 && vat.tannin() == 0, "Two charges fit and start the large hide, took " + taken);
        } finally {
            ServerConfig.VAT_MAX_TANNIN.set(configured);
        }
        h.succeed();
    }
}
