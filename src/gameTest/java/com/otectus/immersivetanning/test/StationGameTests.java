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
import com.otectus.immersivetanning.registry.ModBlocks;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.trait.HideTraitLoader;
import com.otectus.immersivetanning.trait.TraitRef;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import com.otectus.immersivetanning.work.WorkAction;
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
        TestSupport.Operator op = TestSupport.operate(h, player);
        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.WET_TANNED_HIDE.get().create(HideSize.MEDIUM, 50, null));
        op.act(WorkAction.RACK_MOUNT);
        h.assertTrue(rack.finish() == Finish.HARNESS && player.getMainHandItem().getCount() == 1, "An occupied rack refuses a second hide");
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
        TestSupport.Operator op = TestSupport.operate(h, player);
        h.assertTrue(rack.isLocked() && rack.operator().equals(player.getUUID()), "Leaning in makes the player the rack's operator");

        TestSupport.fleshCarefully(op, rack);

        h.assertTrue(rack.mode() == TannersRackBlockEntity.Mode.SCRAPED, "The clean hide stays on the beam until it is picked up");
        HideData data = rack.hide();
        h.assertTrue(data.size() == HideSize.MEDIUM && data.integrity() >= 80 && data.integrity() % HideData.INTEGRITY_STEP == 0,
                "Careful fleshing gives high Integrity in steps of ten, got " + data.integrity());
        h.assertTrue(player.getMainHandItem().getDamageValue() == 1, "The knife wears by one for the hide");
        op.act(WorkAction.RACK_TAKE);
        h.assertTrue(rack.isEmpty(), "Picking it up empties the beam");
        ItemStack scraped = findItem(player.getInventory(), ModItems.SCRAPED_HIDE.get());
        h.assertTrue(!scraped.isEmpty() && ProcessedHideItem.data(scraped).integrity() == data.integrity(), "The player receives the scraped hide");
        op.act(WorkAction.RACK_TAKE);
        h.assertTrue(player.getInventory().countItem(ModItems.SCRAPED_HIDE.get()) == 1, "Taking twice takes nothing more");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nextHideMountsFromTheInventoryAndStartsFleshing(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.TANNERS_RACK.get());
        TannersRackBlockEntity rack = (TannersRackBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        FakePlayer player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TANNERS_KNIFE.get()));
        player.getInventory().add(new ItemStack(ModItems.rawHide(HideSize.SMALL), 2));
        TestSupport.Operator op = TestSupport.operate(h, player);
        op.act(WorkAction.NEXT_HIDE);
        h.assertTrue(rack.mode() == TannersRackBlockEntity.Mode.RAW && rack.hide().size() == HideSize.SMALL, "The next hide is mounted");
        h.assertTrue(rack.work() != null && !rack.work().started(), "It carries untouched fleshing work");
        h.assertTrue(player.getInventory().countItem(ModItems.rawHide(HideSize.SMALL)) == 1, "One hide left the inventory");
        long seed = rack.work().surface().seed();
        op.act(WorkAction.RELEASE);
        h.assertTrue(rack.mode() == TannersRackBlockEntity.Mode.RAW && !rack.isLocked() && rack.work().surface().seed() == seed,
                "Leaving keeps the same hide and patches on the rack, unlocked");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fullStitchingSessionMakesIdentifiedLeatherwork(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.LEATHERWORKERS_BENCH.get());
        LeatherworkersBenchBlockEntity bench = (LeatherworkersBenchBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        FakePlayer player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.AWL.get()));
        player.getInventory().add(new ItemStack(ModItems.TANNERS_KNIFE.get()));
        player.getInventory().add(ModItems.leather(Finish.SUPPLE).create(90, null, 3));
        player.getInventory().add(ModItems.leather(Finish.SUPPLE).create(70, null, 3));
        TestSupport.Operator op = TestSupport.operate(h, player);

        op.send(WorkAction.BENCH_PREPARE, 0, new int[]{Finish.SUPPLE.ordinal()}, new short[0], new short[0],
                ImmersiveTanning.id("leatherworking/leather_boots"), null);
        h.assertTrue(bench.job() != null && bench.hasEscrow() && !bench.job().started(), "Preparing boots lays out four leather on the bench");
        h.assertTrue(countLeather(player.getInventory()) == 2, "Four of six leather left the inventory");
        h.assertTrue(bench.job().pattern().id().equals(ImmersiveTanning.id("boots")), "Boots are worked on the boots pattern");

        TestSupport.craftCleanly(op, bench);
        h.assertTrue(bench.job() == null && bench.hasOutput(), "The knot commits the piece to the output pad");
        h.assertTrue(countLeather(player.getInventory()) == 2, "Nothing came back: the leather was consumed exactly once");
        op.act(WorkAction.BENCH_COLLECT);
        ItemStack boots = findItem(player.getInventory(), Items.LEATHER_BOOTS);
        LeatherworkData data = LeatherworkData.get(boots).orElse(null);
        h.assertTrue(data != null && data.finish() == Finish.SUPPLE && data.workmanship() >= 95,
                "Clean cuts, true seats and ideal pulls give near-perfect Workmanship, got " + (data == null ? -1 : data.workmanship()));
        h.assertTrue(data.integrity() == 85, "Integrity is the mean of the best leather cut (90,90,90,70), got " + data.integrity());
        h.assertTrue(LeatherworkData.crafterId(boots).map(player.getUUID()::equals).orElse(false)
                && com.otectus.immersivetanning.api.ImmersiveTanningAPI.playerMade(boots), "The bench records the player who tied it off");
        h.assertTrue(((net.minecraft.world.item.DyeableLeatherItem) Items.LEATHER_BOOTS).getColor(boots) == Finish.SUPPLE.leatherColor(),
                "Leather armor takes the colour of its finish");
        h.assertTrue(findItem(player.getInventory(), ModItems.LEATHER_SCRAPS.get()).getCount() == 1, "Offcuts come back as scraps with the piece");
        h.assertTrue(player.getMainHandItem().getDamageValue() == 1 && findItem(player.getInventory(), ModItems.TANNERS_KNIFE.get()).getDamageValue() == 1,
                "The awl and the knife each wear once for the piece");
        h.assertTrue(!bench.hasOutput(), "Collecting empties the output pad");
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
        TestSupport.Operator op = TestSupport.operate(h, player);
        op.send(WorkAction.BENCH_PREPARE, 0, new int[]{Finish.HARNESS.ordinal()}, new short[0], new short[0],
                ImmersiveTanning.id("leatherworking/saddle"), null);
        h.assertTrue(countLeather(player.getInventory()) == 0 && player.getInventory().countItem(Items.STRING) == 0, "The saddle takes its materials");
        op.act(WorkAction.BENCH_CANCEL);
        h.assertTrue(countLeather(player.getInventory()) == 5 && player.getInventory().countItem(Items.IRON_INGOT) == 1
                && player.getInventory().countItem(Items.STRING) == 2, "Cancelling before any cut refunds leather and components");
        h.assertTrue(!bench.hasEscrow() && bench.job() == null, "The bench is free again");
        op.act(WorkAction.BENCH_CANCEL);
        h.assertTrue(countLeather(player.getInventory()) == 5, "A second cancel refunds nothing");
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
