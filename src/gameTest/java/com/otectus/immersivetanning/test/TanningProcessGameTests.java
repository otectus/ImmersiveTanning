package com.otectus.immersivetanning.test;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.blockentity.LeatherworkersBenchBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersVatBlockEntity;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.effect.LeatherworkPolicy;
import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.item.ProcessedHideItem;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.leather.LeatherData;
import com.otectus.immersivetanning.leather.LeatherworkData;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModTags;
import net.minecraft.core.BlockPos;
import com.otectus.immersivetanning.work.WorkAction;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * The whole tannery in one run, as a player meets it without Farmer's Delight: knife, awl and stations crafted from
 * vanilla materials and placed, hides from two cows, bark stripped from logs with the Tanner's Knife (the only tannin
 * here), both hides fleshed on one rack ("Next hide" mounts the second), one batch in the vat, finishing and drying on
 * two racks, and boots stitched at the bench and worn. Every right-click goes through
 * {@code ServerPlayerGameMode#useItemOn}, the path a use packet takes, and minigame input through the handlers the
 * packets call. Each stage hands its real output to the next; the vat and the racks finish their last ticks on world
 * time. The milestones on the way are checked by the client smoke test's playthrough: Forge never awards advancements
 * to a fake player.
 */
@GameTestHolder(ImmersiveTanning.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TanningProcessGameTests {
    private static final BlockPos LOG_A = new BlockPos(0, 1, 0);
    private static final BlockPos LOG_B = new BlockPos(1, 1, 0);
    private static final BlockPos VAT = new BlockPos(2, 1, 0);
    private static final BlockPos BENCH = new BlockPos(4, 1, 0);
    private static final BlockPos RACK_A = new BlockPos(0, 1, 2);
    private static final BlockPos RACK_B = new BlockPos(1, 1, 2);
    private static final BlockPos COWS = new BlockPos(3, 1, 4);
    /** Ticks the vat and the racks still need after they are fast-forwarded; the test waits them out. */
    private static final int LAST_TICKS = 10;
    /** Real ticks for those last ones to pass and the ready cue (checked every 10 ticks) to run. */
    private static final int WAIT_TICKS = 25;

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fromCowsAndLogsToWornBootsWithoutFarmersDelight(GameTestHelper h) {
        h.assertTrue(!ModList.get().isLoaded("farmersdelight") && !ForgeRegistries.ITEMS.containsKey(new ResourceLocation("farmersdelight", "tree_bark")),
                "This run has no Farmer's Delight, so the knife's bark is the only tannin");
        FakePlayer player = TestSupport.player(h);

        // Two cows, killed by hand, drop a large hide each (picked up two ticks later).
        for (int i = 0; i < 2; i++) {
            Cow cow = h.spawn(EntityType.COW, COWS.east(i));
            cow.hurt(h.getLevel().damageSources().playerAttack(player), 1000F);
        }

        // The tools and stations come from vanilla materials; the crafted stacks are the ones used below.
        ItemStack none = ItemStack.EMPTY;
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        ItemStack stick = new ItemStack(Items.STICK);
        ItemStack string = new ItemStack(Items.STRING);
        ItemStack planks = new ItemStack(Items.OAK_PLANKS);
        ItemStack knife = craft(h, ModItems.TANNERS_KNIFE.get(), none, iron, none, stick);
        ItemStack awl = craft(h, ModItems.AWL.get(), none, new ItemStack(Items.IRON_NUGGET), none, stick);
        ItemStack rack = craft(h, ModItems.TANNERS_RACK.get(), stick, stick, stick, stick, string, stick, stick, none, stick);
        ItemStack vat = craft(h, ModItems.TANNERS_VAT.get(), planks, none, planks, iron, none, iron, planks, planks, planks);
        ItemStack bench = craft(h, ModItems.LEATHERWORKERS_BENCH.get(), string, iron, none, planks, planks, none, planks, planks, none);
        for (BlockPos pos : List.of(RACK_A, RACK_B, VAT, BENCH)) h.setBlock(pos.below(), Blocks.STONE);
        place(h, player, rack.copy(), RACK_A);
        place(h, player, rack, RACK_B);
        place(h, player, vat, VAT);
        place(h, player, bench, BENCH);
        player.getInventory().add(knife);
        player.getInventory().add(awl);
        h.setBlock(LOG_A, Blocks.OAK_LOG);
        h.setBlock(LOG_B, Blocks.OAK_LOG);

        List<HideData> fleshed = new ArrayList<>();
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    for (ItemEntity drop : h.getEntities(EntityType.ITEM, COWS, 3)) {
                        drop.setNoPickUpDelay();
                        drop.playerTouch(player);
                    }
                    Item largeHide = ModItems.rawHide(HideSize.LARGE);
                    h.assertTrue(count(player, largeHide) == 2 && count(player, Items.LEATHER) == 0,
                            "Each cow drops a large hide and no leather, picked up " + count(player, largeHide));

                    // Bark: nothing in the inventory is tannin until the knife strips the two logs.
                    h.assertTrue(units(player, s -> s.is(ModTags.TANNIN_SOURCES)) == 0, "No tannin before stripping");
                    hold(h, player, s -> s.is(ModItems.TANNERS_KNIFE.get()), "the knife");
                    use(h, player, LOG_A);
                    use(h, player, LOG_B);
                    int bark = count(player, ModItems.BARK.get());
                    h.assertTrue(h.getBlockState(LOG_A).is(Blocks.STRIPPED_OAK_LOG) && h.getBlockState(LOG_B).is(Blocks.STRIPPED_OAK_LOG)
                            && bark == 2 * ServerConfig.get(ServerConfig.BARK_PER_LOG), "The knife strips both logs for bark, got " + bark);
                    h.assertTrue(bark >= 2 * HideSize.LARGE.tannin() && units(player, s -> s.is(ModTags.TANNIN_SOURCES)) == bark,
                            "Two logs pay for two large hides, and bark is all the tannin there is");

                    // Fleshing: a raw hide mounts with a use; leaning in, the knife cleans it on the beam, the clean hide is
                    // picked up, "Next hide" lays the second over the beam and it is cleaned the same way.
                    hold(h, player, s -> s.is(largeHide), "a raw hide");
                    use(h, player, RACK_A);
                    TannersRackBlockEntity first = rack(h, RACK_A);
                    h.assertTrue(first.mode() == TannersRackBlockEntity.Mode.RAW && count(player, largeHide) == 1, "One raw hide mounts on the rack");
                    hold(h, player, s -> s.is(ModItems.TANNERS_KNIFE.get()), "the knife");
                    TestSupport.Operator fleshing = TestSupport.operate(h, player, RACK_A);
                    TestSupport.fleshCarefully(fleshing, first);
                    h.assertTrue(first.mode() == TannersRackBlockEntity.Mode.SCRAPED, "The fleshed hide waits on the beam");
                    fleshing.act(WorkAction.RACK_TAKE);
                    h.assertTrue(first.isEmpty(), "Picking it up empties the beam");
                    fleshing.act(WorkAction.NEXT_HIDE);
                    h.assertTrue(first.mode() == TannersRackBlockEntity.Mode.RAW && count(player, largeHide) == 0, "\"Next hide\" mounts the second hide");
                    TestSupport.fleshCarefully(fleshing, first);
                    fleshing.act(WorkAction.RACK_TAKE);
                    fleshing.act(WorkAction.RELEASE);
                    fleshed.addAll(hides(player, ModItems.SCRAPED_HIDE.get()));
                    h.assertTrue(fleshed.size() == 2 && fleshed.stream().allMatch(d -> d.size() == HideSize.LARGE && d.trait() == null
                            && d.integrity() >= 80 && d.integrity() % HideData.INTEGRITY_STEP == 0), "Careful fleshing scrapes both hides well, got " + fleshed);
                    h.assertTrue(player.getMainHandItem().getDamageValue() == 4, "Two strips and two hides wear the knife by four, got "
                            + player.getMainHandItem().getDamageValue());

                    // The vat: a bucket of water, the knife's bark, then the scraped hides; both start at once.
                    TannersVatBlockEntity tub = vat(h);
                    player.getInventory().add(new ItemStack(Items.WATER_BUCKET));
                    hold(h, player, s -> s.is(Items.WATER_BUCKET), "the water bucket");
                    use(h, player, VAT);
                    h.assertTrue(tub.hasWater() && player.getMainHandItem().is(Items.BUCKET), "The bucket empties into the vat");
                    hold(h, player, s -> s.is(ModItems.BARK.get()), "the bark");
                    use(h, player, VAT);
                    h.assertTrue(tub.tannin() == bark && count(player, ModItems.BARK.get()) == 0, "All the bark becomes tannin, got " + tub.tannin());
                    // One use sinks a whole stack; hides of different Integrity are two stacks.
                    for (int stacks = 0; stacks < 2 && count(player, ModItems.SCRAPED_HIDE.get()) > 0; stacks++) {
                        hold(h, player, s -> s.is(ModItems.SCRAPED_HIDE.get()), "a scraped hide");
                        use(h, player, VAT);
                    }
                    h.assertTrue(tub.slots().size() == 2 && tub.waitingCount() == 0 && tub.steepingCount() == 2
                            && tub.tannin() == bark - 2 * HideSize.LARGE.tannin(), "Both large hides start, each paying its tannin; left " + tub.tannin());
                    hold(h, player, ItemStack::isEmpty, "an empty hand");
                    use(h, player, VAT);
                    h.assertTrue(count(player, ModItems.WET_TANNED_HIDE.get()) == 0 && tub.slots().size() == 2, "An empty hand takes nothing that is still tanning");
                    tub.advance(ServerConfig.get(ServerConfig.TANNING_TICKS) - LAST_TICKS);
                    h.assertTrue(tub.finishedCount() == 0, "Short of the full time nothing is tanned yet");
                })
                .thenExecuteAfter(WAIT_TICKS, () -> {
                    TannersVatBlockEntity tub = vat(h);
                    h.assertTrue(tub.finishedCount() == 2, "World time finishes both hides");
                    hold(h, player, ItemStack::isEmpty, "an empty hand");
                    use(h, player, VAT);
                    List<HideData> wet = hides(player, ModItems.WET_TANNED_HIDE.get());
                    h.assertTrue(tub.slots().isEmpty() && summary(wet).equals(summary(fleshed)),
                            "Both come out tanned with their size, Integrity and trait, got " + wet);

                    // Finishing: the first hide is laced into the frame, the lever moved to Supple, smoothed and seated; the
                    // second repeats the last finish with sneak-use. Both dry as Supple.
                    hold(h, player, s -> s.is(ModItems.WET_TANNED_HIDE.get()), "a wet tanned hide");
                    use(h, player, RACK_A);
                    TannersRackBlockEntity first = rack(h, RACK_A);
                    h.assertTrue(first.mode() == TannersRackBlockEntity.Mode.FRAMING, "A wet hide waits in the frame for its finish");
                    TestSupport.Operator framing = TestSupport.operate(h, player, RACK_A);
                    framing.act(WorkAction.SEAT);
                    h.assertTrue(first.mode() == TannersRackBlockEntity.Mode.FRAMING, "The lever does not seat before the finish is worked");
                    framing.act(WorkAction.DETENT, Finish.SUPPLE.ordinal());
                    TestSupport.treat(framing);
                    framing.act(WorkAction.SEAT);
                    framing.act(WorkAction.RELEASE);
                    hold(h, player, s -> s.is(ModItems.WET_TANNED_HIDE.get()), "the second wet tanned hide");
                    player.setShiftKeyDown(true);
                    use(h, player, RACK_B);
                    player.setShiftKeyDown(false);
                    TannersRackBlockEntity second = rack(h, RACK_B);
                    h.assertTrue(first.mode() == TannersRackBlockEntity.Mode.DRYING && first.finish() == Finish.SUPPLE
                            && second.mode() == TannersRackBlockEntity.Mode.DRYING && second.finish() == Finish.SUPPLE
                            && count(player, ModItems.WET_TANNED_HIDE.get()) == 0, "The worked finish, then sneak-use with the same, dries both");
                    first.advance(ServerConfig.get(ServerConfig.DRYING_TICKS) - LAST_TICKS);
                    second.advance(ServerConfig.get(ServerConfig.DRYING_TICKS) - LAST_TICKS);
                    hold(h, player, ItemStack::isEmpty, "an empty hand");
                    use(h, player, RACK_A);
                    h.assertTrue(!first.isDry() && leather(player).isEmpty(), "A hide still drying gives no leather");
                })
                .thenExecuteAfter(WAIT_TICKS, () -> {
                    TannersRackBlockEntity first = rack(h, RACK_A);
                    TannersRackBlockEntity second = rack(h, RACK_B);
                    h.assertTrue(first.isDry() && first.isSettled() && second.isDry() && second.isSettled(),
                            "World time dries both hides and the ready cue has played");
                    hold(h, player, ItemStack::isEmpty, "an empty hand");
                    use(h, player, RACK_A);
                    use(h, player, RACK_B);
                    List<LeatherData> leather = leather(player);
                    List<Integer> integrities = new ArrayList<>();
                    for (HideData hide : fleshed) for (int i = 0; i < HideSize.LARGE.leather(); i++) integrities.add(hide.integrity());
                    h.assertTrue(first.isEmpty() && second.isEmpty() && leather.size() == 6
                            && leather.stream().allMatch(d -> d.finish() == Finish.SUPPLE && d.trait() == null)
                            && sorted(leather.stream().map(LeatherData::integrity).toList()).equals(sorted(integrities)),
                            "Each large hide dries into three Supple Leather of its Integrity, got " + leather);

                    // The bench: boots are prepared from the best four leather, cut, seated, stitched and tied off; the piece
                    // waits on the output pad and is collected.
                    int expectedIntegrity = Math.round(sorted(integrities).subList(0, 4).stream().mapToInt(Integer::intValue).sum() / 4F);
                    hold(h, player, s -> s.is(ModItems.AWL.get()), "the awl");
                    TestSupport.Operator benchwork = TestSupport.operate(h, player, BENCH);
                    LeatherworkersBenchBlockEntity table = bench(h);
                    benchwork.send(WorkAction.BENCH_PREPARE, 0, new int[]{Finish.SUPPLE.ordinal()}, new short[0], new short[0],
                            ImmersiveTanning.id("leatherworking/leather_boots"), null);
                    h.assertTrue(table.job() != null && table.hasEscrow() && leather(player).size() == 2, "Boots take four of the six leather");
                    TestSupport.craftCleanly(benchwork, table);
                    h.assertTrue(table.job() == null && !table.hasEscrow() && table.hasOutput(), "The knot commits the boots once");
                    benchwork.act(WorkAction.BENCH_COLLECT);
                    benchwork.act(WorkAction.RELEASE);

                    ItemStack boots = StationGameTests.findItem(player.getInventory(), Items.LEATHER_BOOTS);
                    LeatherworkData data = LeatherworkData.get(boots).orElse(null);
                    h.assertTrue(data != null && data.finish() == Finish.SUPPLE && data.trait() == null && data.workmanship() >= 95
                            && data.integrity() == expectedIntegrity, "Supple boots of near-perfect Workmanship and Integrity " + expectedIntegrity + ", got " + data);
                    h.assertTrue(LeatherworkData.crafterId(boots).map(player.getUUID()::equals).orElse(false)
                            && ((DyeableLeatherItem) Items.LEATHER_BOOTS).getColor(boots) == Finish.SUPPLE.leatherColor(),
                            "The boots record who stitched them and take the finish's colour");
                    h.assertTrue(count(player, ModItems.LEATHER_SCRAPS.get()) == 4 / ServerConfig.get(ServerConfig.LEATHER_PER_OFFCUT)
                            && player.getMainHandItem().getDamageValue() == 1, "Offcuts come back as scraps and the awl wears by one");
                    h.assertTrue(StationGameTests.findItem(player.getInventory(), ModItems.TANNERS_KNIFE.get()).getDamageValue() == 5,
                            "Cutting the boots wears the knife once more");

                    // Worn, the boots carry their finish and quality: armor and speed from Workmanship, durability from Integrity.
                    LeatherworkPolicy policy = LeatherworkPolicy.of(boots);
                    double efficacy = policy.efficacy(data);
                    double armor = additions(boots, Attributes.ARMOR, AttributeModifier.Operation.ADDITION);
                    double speed = additions(boots, Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.MULTIPLY_BASE);
                    h.assertTrue(Math.abs(armor - efficacy) < 1.0E-6 && speed > 0 && Math.abs(speed - policy.suppleSpeed() * efficacy) < 1.0E-6,
                            "Worn boots give armor " + efficacy + " and speed " + policy.suppleSpeed() * efficacy + ", got " + armor + " and " + speed);
                    int expectedDamage = (int) Math.floor(10 / policy.durability(data));
                    boots.hurt(10, h.getLevel().getRandom(), null);
                    h.assertTrue(expectedDamage < 10 && boots.getDamageValue() == expectedDamage,
                            "Good Integrity spreads ten points of wear to " + expectedDamage + ", got " + boots.getDamageValue());
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ helpers

    /** The shaped crafting result of a 3x3 grid filled row by row, which must be {@code expected}. */
    private static ItemStack craft(GameTestHelper h, Item expected, ItemStack... grid) {
        TransientCraftingContainer container = BindingLeatherGameTests.grid();
        for (int i = 0; i < grid.length; i++) container.setItem(i, grid[i].copy());
        ItemStack result = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, container, h.getLevel())
                .map(r -> r.assemble(container, h.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
        h.assertTrue(result.is(expected), new ItemStack(expected).getHoverName().getString() + " is crafted from vanilla materials, got " + result);
        return result;
    }

    /** Places the station item on the stone below {@code pos}, as a player does. */
    private static void place(GameTestHelper h, FakePlayer player, ItemStack item, BlockPos pos) {
        String name = item.getHoverName().getString();
        player.setItemInHand(InteractionHand.MAIN_HAND, item);
        use(h, player, pos.below());
        h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(pos)) != null && player.getMainHandItem().isEmpty(), name + " places as a station");
    }

    /** Right-clicks the top of the block at {@code relative} with the main-hand item, as the server handles a use packet. */
    private static InteractionResult use(GameTestHelper h, FakePlayer player, BlockPos relative) {
        BlockPos pos = h.absolutePos(relative);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
        return player.gameMode.useItemOn(player, h.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    /** Selects the first stack matching {@code what}, as a player picks a hotbar slot (swapping it in from the backpack). */
    private static void hold(GameTestHelper h, FakePlayer player, Predicate<ItemStack> what, String description) {
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.items.size(); slot++) {
            if (!what.test(inv.items.get(slot))) continue;
            if (slot < Inventory.getSelectionSize()) {
                inv.selected = slot;
            } else {
                ItemStack selected = inv.items.get(inv.selected);
                inv.items.set(inv.selected, inv.items.get(slot));
                inv.items.set(slot, selected);
            }
            return;
        }
        h.fail("The player has " + description);
    }

    private static TannersRackBlockEntity rack(GameTestHelper h, BlockPos pos) {
        return (TannersRackBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
    }

    private static TannersVatBlockEntity vat(GameTestHelper h) {
        return (TannersVatBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(VAT));
    }

    private static LeatherworkersBenchBlockEntity bench(GameTestHelper h) {
        return (LeatherworkersBenchBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(BENCH));
    }

    private static int count(FakePlayer player, Item item) {
        return player.getInventory().countItem(item);
    }

    private static int units(FakePlayer player, Predicate<ItemStack> what) {
        return player.getInventory().items.stream().filter(what).mapToInt(ItemStack::getCount).sum();
    }

    /** One entry per hide of {@code item} in the inventory. */
    private static List<HideData> hides(FakePlayer player, Item item) {
        List<HideData> out = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) for (int i = 0; i < stack.getCount(); i++) out.add(ProcessedHideItem.data(stack));
        }
        return out;
    }

    /** One entry per finished leather in the inventory. */
    private static List<LeatherData> leather(FakePlayer player) {
        List<LeatherData> out = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            LeatherData.get(stack).ifPresent(data -> {
                for (int i = 0; i < stack.getCount(); i++) out.add(data);
            });
        }
        return out;
    }

    /** Size, Integrity and trait of each hide, in a fixed order. */
    private static List<String> summary(List<HideData> hides) {
        return hides.stream().map(d -> d.size().getSerializedName() + "/" + d.integrity() + "/" + (d.trait() == null ? "-" : d.trait().id()))
                .sorted().toList();
    }

    /** Best first. */
    private static List<Integer> sorted(List<Integer> values) {
        List<Integer> out = new ArrayList<>(values);
        out.sort(Comparator.reverseOrder());
        return out;
    }

    /** The boots' modifiers of one operation for {@code attribute} when worn. */
    private static double additions(ItemStack stack, Attribute attribute, AttributeModifier.Operation operation) {
        return stack.getAttributeModifiers(EquipmentSlot.FEET).get(attribute).stream()
                .filter(m -> m.getOperation() == operation).mapToDouble(AttributeModifier::getAmount).sum();
    }

    private TanningProcessGameTests() {}
}
