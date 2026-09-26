package com.otectus.immersivetanning.test;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.blockentity.LeatherworkersBenchBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersVatBlockEntity;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.item.ProcessedHideItem;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.leather.LeatherData;
import com.otectus.immersivetanning.leather.LeatherworkData;
import com.otectus.immersivetanning.minigame.FleshingSession;
import com.otectus.immersivetanning.minigame.FleshingSurface;
import com.otectus.immersivetanning.minigame.SessionManager;
import com.otectus.immersivetanning.minigame.StitchRun;
import com.otectus.immersivetanning.minigame.StitchingSession;
import com.otectus.immersivetanning.minigame.TanningSession;
import com.otectus.immersivetanning.network.packet.FleshStrokePacket;
import com.otectus.immersivetanning.registry.ModBlocks;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.trait.HideTraitLoader;
import com.otectus.immersivetanning.trait.TraitRef;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static com.otectus.immersivetanning.test.TestSupport.STATION;

/** The stations and complete, server-validated minigame sessions driven with a fake player. */
@GameTestHolder(ImmersiveTanning.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationGameTests {

    @GameTest(template = "empty")
    public static void vatTansBatchesWithWaterAndBark(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.TANNERS_VAT.get());
        TannersVatBlockEntity vat = (TannersVatBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        TraitRef tough = HideTraitLoader.get(ImmersiveTanning.id("tough"));
        h.assertTrue(tough != null, "The tough trait is defined by data");
        h.assertTrue(vat.insertHide(new HideData(HideSize.LARGE, 80, tough)), "A scraped hide goes in");
        h.assertTrue(vat.insertHide(new HideData(HideSize.SMALL, 60, null)), "A second hide goes in");
        h.assertTrue(vat.waitingCount() == 2, "Without water and bark both hides wait");
        vat.fillWater();
        vat.addTannin(2);
        h.assertTrue(vat.waitingCount() == 1 && vat.tannin() == 0, "The large hide spends two tannin, the small one waits");
        vat.addTannin(3);
        h.assertTrue(vat.waitingCount() == 0 && vat.tannin() == 2, "More bark starts the small hide");
        h.assertTrue(vat.takeFinished().isEmpty(), "Nothing is tanned yet");
        vat.advance(ServerConfig.get(ServerConfig.TANNING_TICKS));
        List<ItemStack> wet = vat.takeFinished();
        h.assertTrue(wet.size() == 2, "Both hides come out tanned, got " + wet.size());
        HideData large = ProcessedHideItem.data(wet.get(0));
        h.assertTrue(wet.get(0).is(ModItems.WET_TANNED_HIDE.get()) && large.size() == HideSize.LARGE && large.integrity() == 80
                && large.trait() != null && large.trait().id().equals(tough.id()), "Size, Integrity and trait survive the vat");
        h.assertTrue(vat.slots().isEmpty(), "The vat is empty again");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void vatTakesWaterBarkAndAStackOfHidesFromThePlayer(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.TANNERS_VAT.get());
        TannersVatBlockEntity vat = (TannersVatBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        FakePlayer player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        TestSupport.use(h, player);
        h.assertTrue(vat.hasWater() && player.getMainHandItem().is(Items.BUCKET), "The bucket empties into the vat");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BARK.get(), 5));
        TestSupport.use(h, player);
        h.assertTrue(vat.tannin() == 5 && player.getMainHandItem().isEmpty(), "Bark becomes tannin charges");
        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.SCRAPED_HIDE.get().create(HideSize.MEDIUM, 70, null).copyWithCount(6));
        TestSupport.use(h, player);
        h.assertTrue(vat.slots().size() == vat.capacity(), "A whole stack goes in up to capacity");
        h.assertTrue(player.getMainHandItem().getCount() == 6 - vat.capacity(), "The rest stays in hand");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rackDriesIntoTheChosenFinish(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.TANNERS_RACK.get());
        TannersRackBlockEntity rack = (TannersRackBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        TraitRef heatproof = HideTraitLoader.get(ImmersiveTanning.id("heatproof"));
        rack.mountWet(new HideData(HideSize.LARGE, 90, heatproof), Finish.HARDENED);
        h.assertTrue(!rack.isDry() && rack.collectLeather().isEmpty(), "A wet hide is not leather yet");
        rack.advance(ServerConfig.get(ServerConfig.DRYING_TICKS));
        h.assertTrue(rack.isDry(), "The hide dries by world time");
        ItemStack leather = rack.collectLeather();
        LeatherData data = LeatherData.get(leather).orElseThrow();
        h.assertTrue(leather.is(ModItems.leather(Finish.HARDENED)) && leather.getCount() == 3, "A large hide yields three Hardened Leather");
        h.assertTrue(data.integrity() == 90 && data.trait() != null && data.trait().id().equals(heatproof.id()), "Integrity and trait carry into the leather");
        h.assertTrue(rack.isEmpty(), "The rack is free again");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rackTakesRawAndWetHidesFromThePlayer(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.TANNERS_RACK.get());
        TannersRackBlockEntity rack = (TannersRackBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        FakePlayer player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.rawHide(HideSize.SMALL), 2));
        TestSupport.use(h, player);
        h.assertTrue(rack.mode() == TannersRackBlockEntity.Mode.RAW && player.getMainHandItem().getCount() == 1, "A raw hide mounts for fleshing");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        TestSupport.use(h, player);
        h.assertTrue(rack.isEmpty() && player.getInventory().countItem(ModItems.rawHide(HideSize.SMALL)) == 1, "Sneaking with an empty hand takes it back");

        // Sneak-mounting a wet hide repeats the last finish (Harness for a new player) without a screen.
        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.WET_TANNED_HIDE.get().create(HideSize.MEDIUM, 50, null));
        TestSupport.use(h, player);
        h.assertTrue(rack.mode() == TannersRackBlockEntity.Mode.DRYING && rack.finish() == Finish.HARNESS, "Sneak-mount uses the last finish");
        SessionManager.selectFinish(player, h.absolutePos(STATION), Finish.SUPPLE);
        h.assertTrue(rack.finish() == Finish.HARNESS, "An occupied rack refuses a second hide");
        player.setShiftKeyDown(false);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void animalsDropHidesInsteadOfLeather(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        var cow = h.spawn(net.minecraft.world.entity.EntityType.COW, STATION.above());
        var hoglin = h.spawn(net.minecraft.world.entity.EntityType.HOGLIN, STATION.above());
        cow.hurt(h.getLevel().damageSources().playerAttack(player), 1000F);
        hoglin.hurt(h.getLevel().damageSources().playerAttack(player), 1000F);
        h.runAfterDelay(2, () -> {
            List<ItemStack> drops = new ArrayList<>();
            h.getEntities(net.minecraft.world.entity.EntityType.ITEM, STATION, 4).forEach(e -> drops.add(e.getItem()));
            h.assertTrue(drops.stream().noneMatch(s -> s.is(Items.LEATHER)), "Leather drops are replaced");
            List<ItemStack> hides = drops.stream().filter(s -> s.is(ModItems.rawHide(HideSize.LARGE))).toList();
            h.assertTrue(hides.size() == 2, "The cow and the hoglin each drop a large hide, got " + drops);
            boolean plain = false;
            boolean trophy = false;
            for (ItemStack hide : hides) {
                TraitRef trait = HideData.of(hide, HideSize.LARGE).trait();
                if (trait == null) plain = true;
                else if (trait.id().equals(ImmersiveTanning.id("tough"))) trophy = true;
            }
            h.assertTrue(plain && trophy, "Cow hide stays plain; hoglin hide keeps its Tough trait");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void fullFleshingSessionYieldsAScrapedHide(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.TANNERS_RACK.get());
        TannersRackBlockEntity rack = (TannersRackBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        rack.mountRaw(HideData.raw(HideSize.MEDIUM, null));
        FakePlayer player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TANNERS_KNIFE.get()));
        TestSupport.use(h, player);
        TanningSession session = SessionManager.byPlayer(player.getUUID()).orElse(null);
        h.assertTrue(session instanceof FleshingSession, "The knife opens a fleshing session");
        FleshingSession fleshing = (FleshingSession) session;
        h.assertTrue(rack.isLocked(), "The rack is locked while fleshing");

        // Replay a careful path through the server's own copy of the hide, as the client would send it.
        FleshingSurface mirror = new FleshingSurface(HideSize.MEDIUM, fleshing.seed);
        List<int[]> points = new ArrayList<>();
        List<Boolean> begins = new ArrayList<>();
        for (int p = 0; p < mirror.patches(); p++) {
            float cos = Mth.cos(mirror.grain(p));
            float sin = Mth.sin(mirror.grain(p));
            for (float off = -9F; off <= 9F; off += 2.2F) {
                float cx = mirror.patchX(p) - sin * off;
                float cy = mirror.patchY(p) + cos * off;
                for (int i = 0; i <= 24; i++) {
                    float t = i / 24F;
                    float x = Mth.lerp(t, cx - cos * 11F, cx + cos * 11F);
                    float y = Mth.lerp(t, cy - sin * 11F, cy + sin * 11F);
                    points.add(new int[]{FleshingSurface.quantize(x / FleshingSurface.W), FleshingSurface.quantize(y / FleshingSurface.H)});
                    begins.add(i == 0);
                }
            }
        }
        int sequence = 0;
        int clientMs = 100;
        for (int start = 0; start < points.size(); ) {
            int end = start + 1;
            while (end < points.size() && !begins.get(end)) end++;
            short[] xs = new short[end - start];
            short[] ys = new short[end - start];
            for (int i = start; i < end; i++) {
                xs[i - start] = (short) points.get(i)[0];
                ys[i - start] = (short) points.get(i)[1];
            }
            clientMs += 150;
            SessionManager.fleshStroke(player, new FleshStrokePacket(fleshing.id, ++sequence, true, true, clientMs, xs, ys), fleshing.startMs() + clientMs);
            if (SessionManager.byPlayer(player.getUUID()).isEmpty()) break;
            start = end;
        }
        if (SessionManager.byPlayer(player.getUUID()).isPresent()) SessionManager.finishFleshingEarly(player, fleshing.id);

        h.assertTrue(SessionManager.byPlayer(player.getUUID()).isEmpty(), "The session ends");
        h.assertTrue(rack.isEmpty() && !rack.isLocked(), "The fleshed hide leaves the rack");
        ItemStack scraped = findItem(player.getInventory(), ModItems.SCRAPED_HIDE.get());
        h.assertTrue(!scraped.isEmpty(), "The player receives the scraped hide");
        HideData data = ProcessedHideItem.data(scraped);
        h.assertTrue(data.size() == HideSize.MEDIUM && data.integrity() >= 80 && data.integrity() % HideData.INTEGRITY_STEP == 0,
                "Careful fleshing gives high Integrity in steps of ten, got " + data.integrity());
        h.assertTrue(player.getMainHandItem().getDamageValue() == 1, "The knife wears by one");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nextHideMountsFromTheInventoryAndStartsFleshing(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.TANNERS_RACK.get());
        TannersRackBlockEntity rack = (TannersRackBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        FakePlayer player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TANNERS_KNIFE.get()));
        player.getInventory().add(new ItemStack(ModItems.rawHide(HideSize.SMALL), 2));
        SessionManager.fleshNext(player, h.absolutePos(STATION));
        h.assertTrue(rack.mode() == TannersRackBlockEntity.Mode.RAW && rack.hide().size() == HideSize.SMALL, "The next hide is mounted");
        h.assertTrue(SessionManager.byPlayer(player.getUUID()).orElse(null) instanceof FleshingSession, "Fleshing starts at once");
        h.assertTrue(player.getInventory().countItem(ModItems.rawHide(HideSize.SMALL)) == 1, "One hide left the inventory");
        SessionManager.cancelFromClient(player, SessionManager.byPlayer(player.getUUID()).orElseThrow().id);
        h.assertTrue(rack.mode() == TannersRackBlockEntity.Mode.RAW && !rack.isLocked(), "Cancelling leaves the hide on the rack, unlocked");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fullStitchingSessionMakesIdentifiedLeatherwork(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.LEATHERWORKERS_BENCH.get());
        LeatherworkersBenchBlockEntity bench = (LeatherworkersBenchBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        FakePlayer player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.AWL.get()));
        player.getInventory().add(ModItems.leather(Finish.SUPPLE).create(90, null, 3));
        player.getInventory().add(ModItems.leather(Finish.SUPPLE).create(70, null, 3));
        TestSupport.use(h, player);
        StitchingSession session = (StitchingSession) SessionManager.byPlayer(player.getUUID()).orElse(null);
        h.assertTrue(session != null, "The awl opens the bench");

        SessionManager.selectBenchRecipe(player, session.id, ImmersiveTanning.id("leatherworking/leather_boots"), Finish.SUPPLE, null);
        h.assertTrue(session.isPlaying() && bench.hasEscrow(), "Choosing boots cuts four leather into escrow");
        h.assertTrue(countLeather(player.getInventory()) == 2, "Four of six leather left the inventory");
        StitchRun run = session.run();
        for (int i = 0; i < run.count(); i++) {
            SessionManager.stitchPress(player, session.id, i + 1, run.time(i), session.startMs() + run.time(i));
        }
        h.assertTrue(SessionManager.byPlayer(player.getUUID()).isEmpty(), "The last stitch finishes the piece");
        h.assertTrue(!bench.hasEscrow() && !bench.isLocked(), "Escrow is consumed exactly once");
        ItemStack boots = findItem(player.getInventory(), Items.LEATHER_BOOTS);
        LeatherworkData data = LeatherworkData.get(boots).orElse(null);
        h.assertTrue(data != null && data.finish() == Finish.SUPPLE && data.workmanship() == 100, "On-beat stitching gives Workmanship 100");
        h.assertTrue(data.integrity() == 85, "Integrity is the mean of the best leather cut (90,90,90,70), got " + data.integrity());
        h.assertTrue(LeatherworkData.crafterId(boots).map(player.getUUID()::equals).orElse(false)
                && com.otectus.immersivetanning.api.ImmersiveTanningAPI.playerMade(boots), "The bench records the player who stitched it");
        h.assertTrue(((net.minecraft.world.item.DyeableLeatherItem) Items.LEATHER_BOOTS).getColor(boots) == Finish.SUPPLE.leatherColor(),
                "Leather armor takes the colour of its finish");
        h.assertTrue(findItem(player.getInventory(), ModItems.LEATHER_SCRAPS.get()).getCount() == 1, "Offcuts come back as scraps");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void cancelledStitchingRefundsEverything(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.LEATHERWORKERS_BENCH.get());
        LeatherworkersBenchBlockEntity bench = (LeatherworkersBenchBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        FakePlayer player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.AWL.get()));
        player.getInventory().add(ModItems.leather(Finish.HARNESS).create(60, null, 5));
        player.getInventory().add(new ItemStack(Items.IRON_INGOT));
        player.getInventory().add(new ItemStack(Items.STRING, 2));
        TestSupport.use(h, player);
        StitchingSession session = (StitchingSession) SessionManager.byPlayer(player.getUUID()).orElseThrow();
        SessionManager.selectBenchRecipe(player, session.id, ImmersiveTanning.id("leatherworking/saddle"), Finish.HARNESS, null);
        h.assertTrue(countLeather(player.getInventory()) == 0 && player.getInventory().countItem(Items.STRING) == 0, "The saddle takes its materials");
        SessionManager.cancelFromClient(player, session.id);
        h.assertTrue(countLeather(player.getInventory()) == 5 && player.getInventory().countItem(Items.IRON_INGOT) == 1
                && player.getInventory().countItem(Items.STRING) == 2, "Cancelling refunds leather and components");
        h.assertTrue(!bench.hasEscrow() && !bench.isLocked(), "The bench is free again");
        h.succeed();
    }

    static ItemStack findItem(Inventory inventory, net.minecraft.world.item.Item item) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(item)) return inventory.getItem(i);
        }
        return ItemStack.EMPTY;
    }

    static int countLeather(Inventory inventory) {
        int n = 0;
        for (Finish finish : Finish.values()) n += inventory.countItem(ModItems.leather(finish));
        return n;
    }

    private StationGameTests() {}
}
