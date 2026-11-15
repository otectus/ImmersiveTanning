package com.otectus.immersivetanning.test;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.api.ILeatherworkRecipeGate;
import com.otectus.immersivetanning.api.ImmersiveTanningAPI;
import com.otectus.immersivetanning.api.event.LeatherworkStitchedEvent;
import com.otectus.immersivetanning.blockentity.LeatherworkersBenchBlockEntity;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.effect.LeatherworkEffects;
import com.otectus.immersivetanning.effect.LeatherworkPolicy;
import com.otectus.immersivetanning.item.AwlItem;
import com.otectus.immersivetanning.item.LeatherScrapsItem;
import com.otectus.immersivetanning.leather.FieldRepair;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.leather.LeatherworkData;
import com.otectus.immersivetanning.leather.LeatherworkProfile;
import com.otectus.immersivetanning.network.ModNetwork;
import com.otectus.immersivetanning.network.packet.BenchOffersPacket;
import com.otectus.immersivetanning.station.BenchStationLogic;
import com.otectus.immersivetanning.work.LeatherworkJob;
import com.otectus.immersivetanning.work.WorkAction;
import com.otectus.immersivetanning.registry.ModBlocks;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.trait.HideTraitLoader;
import com.otectus.immersivetanning.trait.TraitRef;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.NetworkDirection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static com.otectus.immersivetanning.test.TestSupport.STATION;

/**
 * Scale-backed leatherwork: another mod's armor stitched over finished leather (Ice and Fire's dragon-scale armor; the
 * test datapack stands diamond armor and prismarine shards in for it). The bench takes the backing and the scales
 * together and gives them back exactly once; the piece keeps its own data; finish and quality follow the moderate
 * scale-backed policy; Leather Scraps leave the piece to its own repairs.
 */
@GameTestHolder(ImmersiveTanning.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScaleBackedGameTests {
    private static final ResourceLocation STAND_IN = ImmersiveTanning.id("test/scale_chestplate");
    private static final double EPSILON = 1.0E-9;

    // ------------------------------------------------------------------ the bench

    @GameTest(template = "empty")
    public static void theBenchCutsBackingAndScalesTogetherAndRecordsThePiece(GameTestHelper h) {
        LeatherworkersBenchBlockEntity bench = bench(h);
        FakePlayer player = worker(h);
        Inventory inv = player.getInventory();
        inv.add(ModItems.leather(Finish.SUPPLE).create(90, null, 1));
        inv.add(ModItems.leather(Finish.SUPPLE).create(60, null, 2));
        inv.add(ModItems.leather(Finish.HARDENED).create(80, null, 2));
        inv.add(new ItemStack(Items.PRISMARINE_SHARD, 8));
        TestSupport.Operator op = TestSupport.operate(h, player);

        BenchOffersPacket.Entry entry = offer(player, bench);
        h.assertTrue(entry != null && entry.craftable() && entry.leather() == 2 && entry.result().is(Items.DIAMOND_CHESTPLATE)
                && entry.auxiliary().size() == 1 && entry.auxiliary().get(0).is(Items.PRISMARINE_SHARD) && entry.auxiliary().get(0).getCount() == 8,
                "The pattern book offers the piece, showing 2 leather and 8 scales");
        BenchOffersPacket.Group supple = entry.groups().stream().filter(g -> g.finish() == Finish.SUPPLE).findFirst().orElse(null);
        BenchOffersPacket.Group hardened = entry.groups().stream().filter(g -> g.finish() == Finish.HARDENED).findFirst().orElse(null);
        h.assertTrue(supple != null && supple.integrity() == 75 && hardened != null && hardened.integrity() == 80,
                "Each group shows the Integrity of its best two leather (Supple 90 and 60, Hardened 80 and 80)");

        List<LeatherworkStitchedEvent> stitched = new ArrayList<>();
        Consumer<LeatherworkStitchedEvent> listener = stitched::add;
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LeatherworkStitchedEvent.class, listener);
        try {
            prepare(op, Finish.SUPPLE);
            h.assertTrue(bench.job() != null && bench.hasEscrow(), "Preparing the piece lays its materials on the bench");
            h.assertTrue(inv.countItem(ModItems.leather(Finish.SUPPLE)) == 1 && inv.countItem(Items.PRISMARINE_SHARD) == 0
                    && inv.countItem(ModItems.leather(Finish.HARDENED)) == 2, "The best two Supple leather and all eight scales leave together");
            TestSupport.craftCleanly(op, bench);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
        }
        h.assertTrue(bench.job() == null && !bench.hasEscrow() && bench.hasOutput(), "The knot finishes the piece and consumes its materials once");
        op.act(WorkAction.BENCH_COLLECT);
        ItemStack piece = StationGameTests.findItem(inv, Items.DIAMOND_CHESTPLATE);
        LeatherworkData data = LeatherworkData.get(piece).orElse(null);
        h.assertTrue(data != null && data.finish() == Finish.SUPPLE && data.integrity() == 75 && data.workmanship() >= 95,
                "The piece carries the backing's finish and Integrity and the work's Workmanship");
        h.assertTrue(piece.getTag().getBoolean("StandIn"), "The recipe's own result data survives next to the leatherwork");
        h.assertTrue(LeatherworkProfile.of(piece) == LeatherworkProfile.SCALE_BACKED
                && "scale_backed".equals(piece.getTag().getCompound(LeatherworkData.ROOT).getString(LeatherworkProfile.KEY)),
                "The piece records that it is scale-backed");
        h.assertTrue(LeatherworkData.crafterId(piece).map(player.getUUID()::equals).orElse(false) && ImmersiveTanningAPI.playerMade(piece),
                "The bench records the player who tied it off");
        h.assertTrue(stitched.size() == 1 && stitched.get(0).getRecipeId().equals(STAND_IN) && stitched.get(0).getLeatherUsed() == 2
                && !stitched.get(0).isTimedOut(), "Progression mods hear about the piece once, untimed");
        h.assertTrue(!h.getEntities(EntityType.EXPERIENCE_ORB, STATION, 3).isEmpty(), "Finishing the piece gives experience");
        h.assertTrue(inv.countItem(ModItems.LEATHER_SCRAPS.get()) == 0 && dropped(h, ModItems.leather(Finish.SUPPLE)) == 0
                && dropped(h, Items.PRISMARINE_SHARD) == 0, "Two leather leave no offcut, and nothing is dropped at the bench");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void tooFewOrWrongScalesCannotStartThePiece(GameTestHelper h) {
        LeatherworkersBenchBlockEntity bench = bench(h);
        FakePlayer player = worker(h);
        Inventory inv = player.getInventory();
        inv.add(ModItems.leather(Finish.SUPPLE).create(80, null, 2));
        inv.add(new ItemStack(Items.PRISMARINE_SHARD, 7));
        inv.add(new ItemStack(Items.PRISMARINE_CRYSTALS, 8));
        TestSupport.Operator op = TestSupport.operate(h, player);
        BenchOffersPacket offers = BenchStationLogic.offers(player, bench);
        BenchOffersPacket.Entry standIn = offers.entries().stream().filter(e -> e.recipe().equals(STAND_IN)).findFirst().orElseThrow();
        BenchOffersPacket.Entry mismatch = offers.entries().stream().filter(e -> e.recipe().equals(ImmersiveTanning.id("test/scale_mismatch"))).findFirst().orElseThrow();
        h.assertTrue(!standIn.craftable() && standIn.auxMissing() && mismatch.craftable(),
                "Seven of the right scales and eight of the wrong ones do not make the piece; one they cover is makeable");
        prepare(op, Finish.SUPPLE);
        h.assertTrue(bench.job() == null && inv.countItem(ModItems.leather(Finish.SUPPLE)) == 2 && inv.countItem(Items.PRISMARINE_SHARD) == 7
                && inv.countItem(Items.PRISMARINE_CRYSTALS) == 8, "Asking for it anyway takes nothing");
        inv.add(new ItemStack(Items.PRISMARINE_SHARD));
        h.assertTrue(offer(player, bench).craftable(), "With eight scales the piece is makeable");
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            if (inv.getItem(slot).is(Items.PRISMARINE_SHARD)) {
                inv.getItem(slot).shrink(1);
                break;
            }
        }
        prepare(op, Finish.SUPPLE);
        h.assertTrue(bench.job() == null && inv.countItem(ModItems.leather(Finish.SUPPLE)) == 2 && inv.countItem(Items.PRISMARINE_SHARD) == 7,
                "A scale gone before preparing leaves everything where it was");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void backingAndScalesComeBackExactlyOnce(GameTestHelper h) {
        LeatherworkersBenchBlockEntity bench = bench(h);
        FakePlayer player = worker(h);
        Inventory inv = player.getInventory();
        inv.add(ModItems.leather(Finish.SUPPLE).create(70, null, 2));
        inv.add(new ItemStack(Items.PRISMARINE_SHARD, 8));
        TestSupport.Operator op = TestSupport.operate(h, player);

        prepare(op, Finish.SUPPLE);
        h.assertTrue(bench.job() != null && inv.countItem(Items.PRISMARINE_SHARD) == 0 && inv.countItem(ModItems.leather(Finish.SUPPLE)) == 0,
                "Preparing takes the backing and the scales");
        op.act(WorkAction.BENCH_CANCEL);
        h.assertTrue(materials(inv) && !bench.hasEscrow(), "Cancelling before any cut returns the backing and the scales");
        op.act(WorkAction.BENCH_CANCEL);
        h.assertTrue(materials(inv), "A second cancel returns nothing more");

        // Leaving (logging out) keeps the prepared piece on the bench with its materials; the player can come back to it.
        prepare(op, Finish.SUPPLE);
        com.otectus.immersivetanning.work.StationLeases.onLogout(player);
        h.assertTrue(bench.job() != null && bench.hasEscrow() && !bench.isLocked() && inv.countItem(Items.PRISMARINE_SHARD) == 0,
                "Logging out leaves the piece and its materials on the bench");

        // Breaking the bench with an uncut piece gives the materials back once, at the bench.
        h.setBlock(STATION, Blocks.AIR);
        h.assertTrue(dropped(h, ModItems.leather(Finish.SUPPLE)) == 2 && dropped(h, Items.PRISMARINE_SHARD) == 8
                && inv.countItem(ModItems.leather(Finish.SUPPLE)) == 0 && inv.countItem(Items.PRISMARINE_SHARD) == 0,
                "Breaking the bench gives the backing and the scales back once, at the bench");
        h.assertTrue(h.getEntities(EntityType.ITEM, STATION, 4).stream().noneMatch(e -> e.getItem().is(ModItems.UNFINISHED_LEATHERWORK.get())),
                "An uncut piece drops its materials, not a workpiece as well");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void anUnloadedBenchKeepsItsWorkWhenItLoadsAgain(GameTestHelper h) {
        LeatherworkersBenchBlockEntity bench = bench(h);
        FakePlayer player = worker(h);
        Inventory inv = player.getInventory();
        inv.add(ModItems.leather(Finish.SUPPLE).create(70, null, 2));
        inv.add(new ItemStack(Items.PRISMARINE_SHARD, 8));
        TestSupport.Operator op = TestSupport.operate(h, player);
        prepare(op, Finish.SUPPLE);
        TestSupport.cutCleanly(op, bench);
        int revision = bench.job().revision();

        // The chunk unloads mid-piece: the lease ends, the work stays in the saved bench.
        bench.onChunkUnloaded();
        h.assertTrue(!bench.isLocked() && bench.hasEscrow(), "Unloading ends the lease and keeps the work");
        BlockPos pos = h.absolutePos(STATION);
        CompoundTag saved = bench.saveWithFullMetadata();
        h.getLevel().removeBlockEntity(pos);
        BlockEntity loaded = BlockEntity.loadStatic(pos, h.getLevel().getBlockState(pos), saved);
        h.assertTrue(loaded instanceof LeatherworkersBenchBlockEntity, "The saved bench loads again");
        LeatherworkersBenchBlockEntity reloaded = (LeatherworkersBenchBlockEntity) loaded;
        h.getLevel().setBlockEntity(reloaded);
        reloaded.onLoad();
        LeatherworkersBenchBlockEntity.serverTick(h.getLevel(), pos, reloaded.getBlockState(), reloaded);
        h.assertTrue(reloaded.job() != null && reloaded.job().revision() == revision && reloaded.job().stage() == LeatherworkJob.Stage.ASSEMBLY
                && reloaded.hasEscrow(), "On its next load the bench still holds the piece, its cuts and its materials");
        h.assertTrue(dropped(h, ModItems.leather(Finish.SUPPLE)) == 0 && inv.countItem(ModItems.leather(Finish.SUPPLE)) == 0,
                "Nothing is dropped or handed back: the work simply waits");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void poorOrMissedStitchingStillMakesThePiece(GameTestHelper h) {
        LeatherworkersBenchBlockEntity bench = bench(h);
        FakePlayer player = worker(h);
        Inventory inv = player.getInventory();
        inv.add(ModItems.leather(Finish.HARDENED).create(70, null, 4));
        inv.add(new ItemStack(Items.PRISMARINE_SHARD, 16));
        TestSupport.Operator op = TestSupport.operate(h, player);

        prepare(op, Finish.HARDENED);
        TestSupport.craftPoorly(op, bench);
        op.act(WorkAction.BENCH_COLLECT);
        prepare(op, Finish.HARDENED);
        TestSupport.craftCleanly(op, bench);
        op.act(WorkAction.BENCH_COLLECT);

        List<Integer> workmanship = new ArrayList<>();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack.is(Items.DIAMOND_CHESTPLATE)) LeatherworkData.get(stack).ifPresent(d -> workmanship.add(d.workmanship()));
        }
        workmanship.sort(null);
        h.assertTrue(bench.job() == null && !bench.hasEscrow(), "Both pieces end and consume their materials");
        h.assertTrue(workmanship.size() == 2 && workmanship.get(0) < 70 && workmanship.get(1) >= 95,
                "Rushed work still makes the piece, at a lower Workmanship, got " + workmanship);
        h.assertTrue(inv.countItem(ModItems.leather(Finish.HARDENED)) == 0 && inv.countItem(Items.PRISMARINE_SHARD) == 0,
                "Each piece took its backing and scales once");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void recipeGatesApplyToScaleBackedPieces(GameTestHelper h) {
        LeatherworkersBenchBlockEntity bench = bench(h);
        FakePlayer player = worker(h);
        Inventory inv = player.getInventory();
        inv.add(ModItems.leather(Finish.SUPPLE).create(70, null, 2));
        inv.add(new ItemStack(Items.PRISMARINE_SHARD, 8));
        UUID gated = player.getUUID();
        ILeatherworkRecipeGate gate = (p, recipe) -> p.getUUID().equals(gated) && recipe.getId().equals(STAND_IN)
                ? Optional.of(Component.literal("Not yet")) : Optional.empty();
        ImmersiveTanningAPI.registerRecipeGate(gate);
        try {
            TestSupport.Operator op = TestSupport.operate(h, player);
            BenchOffersPacket.Entry entry = offer(player, bench);
            h.assertTrue(entry != null && !entry.craftable() && entry.denial() != null && entry.denial().getString().equals("Not yet"),
                    "The pattern book shows the gate's reason before anything is taken");
            prepare(op, Finish.SUPPLE);
            h.assertTrue(bench.job() == null && materials(inv), "and the bench refuses it before anything is taken");
        } finally {
            ImmersiveTanningAPI.unregisterRecipeGate(gate);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ the policy

    @GameTest(template = "empty")
    public static void scaleBackedFinishesFollowTheModeratePolicy(GameTestHelper h) {
        ItemStack plain = new ItemStack(Items.DIAMOND_CHESTPLATE);
        double nativeArmor = sum(plain, EquipmentSlot.CHEST, Attributes.ARMOR, AttributeModifier.Operation.ADDITION);
        double nativeToughness = sum(plain, EquipmentSlot.CHEST, Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADDITION);
        int nativeModifiers = plain.getAttributeModifiers(EquipmentSlot.CHEST).size();

        ItemStack best = piece(Items.DIAMOND_CHESTPLATE, Finish.HARDENED, 80, 100, null);
        double armor = sum(best, EquipmentSlot.CHEST, Attributes.ARMOR, AttributeModifier.Operation.ADDITION);
        double toughness = sum(best, EquipmentSlot.CHEST, Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADDITION);
        h.assertTrue(close(armor, nativeArmor * 1.05 + 0.5 * 1.05) && close(toughness, nativeToughness + 0.25 * 1.05),
                "Masterwork Hardened: own armor x1.05 plus 0.525 flat, toughness +0.2625, got " + armor + " / " + toughness);
        h.assertTrue(best.getAttributeModifiers(EquipmentSlot.CHEST).size() == nativeModifiers + 1
                && best.getAttributeModifiers(EquipmentSlot.CHEST).get(Attributes.ARMOR).size() == 1,
                "One armor modifier (the piece's own, rescaled) and one added toughness modifier, nothing doubled");
        ItemStack crude = piece(Items.DIAMOND_CHESTPLATE, Finish.HARDENED, 80, 0, null);
        h.assertTrue(close(sum(crude, EquipmentSlot.CHEST, Attributes.ARMOR, AttributeModifier.Operation.ADDITION), nativeArmor * 0.95 + 0.5 * 0.95)
                && close(sum(crude, EquipmentSlot.CHEST, Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADDITION), nativeToughness + 0.25 * 0.95),
                "Crude Hardened: own armor x0.95 plus 0.475 flat");
        h.assertTrue(LeatherworkPolicy.leather().efficacy(LeatherworkData.get(best).orElseThrow()) * (1 + LeatherworkPolicy.leather().hardenedArmorShare())
                * nativeArmor > armor + 7, "The ordinary leather values would add far more (+75% of the piece's armor)");

        ItemStack supple = piece(Items.DIAMOND_CHESTPLATE, Finish.SUPPLE, 80, 50, null);
        h.assertTrue(close(sum(supple, EquipmentSlot.CHEST, Attributes.ARMOR, AttributeModifier.Operation.ADDITION), nativeArmor)
                && close(sum(supple, EquipmentSlot.CHEST, Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.MULTIPLY_BASE), 0.01),
                "Standard Supple keeps the armor as it is and adds 1% speed");

        Zombie wearer = EntityType.ZOMBIE.create(h.getLevel());
        wearer.setItemSlot(EquipmentSlot.HEAD, piece(Items.DIAMOND_HELMET, Finish.SUPPLE, 80, 50, null));
        wearer.setItemSlot(EquipmentSlot.CHEST, supple);
        wearer.setItemSlot(EquipmentSlot.LEGS, piece(Items.DIAMOND_LEGGINGS, Finish.SUPPLE, 80, 50, null));
        wearer.setItemSlot(EquipmentSlot.FEET, piece(Items.DIAMOND_BOOTS, Finish.SUPPLE, 80, 50, null));
        double visibility = ForgeHooks.getEntityVisibilityMultiplier(wearer, null, 1.0);
        h.assertTrue(close(visibility, Math.pow(1 - 0.04, 4)), "A full Supple set is noticed 4% less far per piece, got " + visibility);
        VanillaGameEvent step = new VanillaGameEvent(h.getLevel(), GameEvent.STEP, wearer.position(), GameEvent.Context.of(wearer));
        h.assertTrue(MinecraftForge.EVENT_BUS.post(step), "Supple scale-backed boots keep footsteps from sculk");

        ItemStack harness = piece(Items.DIAMOND_CHESTPLATE, Finish.HARNESS, 100, 50, null);
        ItemStack rough = piece(Items.DIAMOND_CHESTPLATE, Finish.SUPPLE, 0, 50, null);
        h.assertTrue(close(LeatherworkEffects.durability(harness, LeatherworkData.get(harness).orElseThrow()), 1.1 * 1.2)
                && close(LeatherworkEffects.durability(rough, LeatherworkData.get(rough).orElseThrow()), 0.9),
                "Integrity spans 0.9-1.1x durability and Harness adds 1.2x");
        h.assertTrue(Math.abs(damage(harness, 132) - 100) <= 1 && Math.abs(damage(rough, 90) - 100) <= 1,
                "Durability damage converts by the same multipliers");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryLeatherworkKeepsItsValues(GameTestHelper h) {
        ItemStack hardened = DataGameTests.boots(Finish.HARDENED, 100);
        ItemStack supple = DataGameTests.boots(Finish.SUPPLE, 100);
        ItemStack harness = DataGameTests.boots(Finish.HARNESS, 100);
        h.assertTrue(LeatherworkProfile.of(hardened) == LeatherworkProfile.LEATHER
                && !hardened.getTag().getCompound(LeatherworkData.ROOT).contains(LeatherworkProfile.KEY), "Ordinary leatherwork records no profile");
        h.assertTrue(close(DataGameTests.armor(hardened), 1 * (1.15 + 0.75 * 1.15)) && close(DataGameTests.toughness(hardened), 0.5 * 1.15),
                "Masterwork Hardened leather boots keep +75% armor and +0.5 toughness, got " + DataGameTests.armor(hardened));
        h.assertTrue(close(DataGameTests.speed(supple), 0.025 * 1.15), "Supple leather keeps +2.5% speed per piece");
        h.assertTrue(close(LeatherworkEffects.durability(harness, LeatherworkData.get(harness).orElseThrow()), (0.8 + 0.4 * 0.8) * 1.5),
                "Harness leather keeps 1.5x durability on Integrity's 0.8-1.2x");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void profilesOutliveTagsAndNeverFallBackToLeather(GameTestHelper h) {
        ItemStack unrecorded = piece(Items.DIAMOND_CHESTPLATE, Finish.HARDENED, 80, 100, null);
        unrecorded.getTag().getCompound(LeatherworkData.ROOT).remove(LeatherworkProfile.KEY);
        h.assertTrue(LeatherworkProfile.of(unrecorded) == LeatherworkProfile.SCALE_BACKED
                && close(sum(unrecorded, EquipmentSlot.CHEST, Attributes.ARMOR, AttributeModifier.Operation.ADDITION), 8 * 1.05 + 0.5 * 1.05),
                "Without a record the tag decides (pieces from before 1.2)");
        ItemStack boots = DataGameTests.boots(Finish.HARDENED, 100);
        boots.getTag().getCompound(LeatherworkData.ROOT).putString(LeatherworkProfile.KEY, "scale_backed");
        h.assertTrue(LeatherworkProfile.of(boots) == LeatherworkProfile.SCALE_BACKED && !FieldRepair.isRepairable(boots),
                "A record outlives the tag");

        ItemStack unknown = piece(Items.DIAMOND_CHESTPLATE, Finish.HARDENED, 100, 100, null);
        unknown.getTag().getCompound(LeatherworkData.ROOT).putString(LeatherworkProfile.KEY, "chain_backed");
        h.assertTrue(LeatherworkProfile.of(unknown) == LeatherworkProfile.UNRECOGNISED
                && close(sum(unknown, EquipmentSlot.CHEST, Attributes.ARMOR, AttributeModifier.Operation.ADDITION), 8)
                && close(sum(unknown, EquipmentSlot.CHEST, Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADDITION), 2)
                && close(LeatherworkEffects.durability(unknown, LeatherworkData.get(unknown).orElseThrow()), 1.0)
                && !FieldRepair.isRepairable(unknown), "An unknown profile gets no finish or quality effects, never the leather values");

        double minimum = ServerConfig.SCALE_MINIMUM_EFFICACY_MULTIPLIER.get();
        try {
            ServerConfig.SCALE_MINIMUM_EFFICACY_MULTIPLIER.set(1.2);
            ItemStack inverted = piece(Items.DIAMOND_CHESTPLATE, Finish.HARDENED, 100, 100, null);
            h.assertTrue(close(sum(inverted, EquipmentSlot.CHEST, Attributes.ARMOR, AttributeModifier.Operation.ADDITION), 8)
                    && close(sum(inverted, EquipmentSlot.CHEST, Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADDITION), 2)
                    && close(LeatherworkEffects.durability(inverted, LeatherworkData.get(inverted).orElseThrow()), 1.0),
                    "An inverted scaleBacked range turns finish and quality off rather than using the leather values");
        } finally {
            ServerConfig.SCALE_MINIMUM_EFFICACY_MULTIPLIER.set(minimum);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void hideTraitsActOnScaleBackedPiecesOnce(GameTestHelper h) {
        TraitRef tough = HideTraitLoader.get(ImmersiveTanning.id("tough"));
        TraitRef lightweight = HideTraitLoader.get(ImmersiveTanning.id("lightweight"));
        ItemStack toughPiece = piece(Items.DIAMOND_CHESTPLATE, Finish.SUPPLE, 50, 50, tough);
        h.assertTrue(close(LeatherworkEffects.durability(toughPiece, LeatherworkData.get(toughPiece).orElseThrow()), 1.25),
                "The backing's Tough trait adds its quarter of durability, as on leather");
        ItemStack light = piece(Items.DIAMOND_CHESTPLATE, Finish.SUPPLE, 50, 50, lightweight);
        Collection<AttributeModifier> speeds = light.getAttributeModifiers(EquipmentSlot.CHEST).get(Attributes.MOVEMENT_SPEED);
        h.assertTrue(speeds.size() == 2 && speeds.stream().map(AttributeModifier::getId).distinct().count() == 2
                && close(sum(light, EquipmentSlot.CHEST, Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.MULTIPLY_BASE), 0.01 + 0.015),
                "Supple and Lightweight each add their own speed modifier once");
        h.assertTrue(close(sum(light, EquipmentSlot.CHEST, Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.MULTIPLY_BASE), 0.025)
                && light.getAttributeModifiers(EquipmentSlot.CHEST).size() == speeds.size() + new ItemStack(Items.DIAMOND_CHESTPLATE).getAttributeModifiers(EquipmentSlot.CHEST).size(),
                "Asking again adds nothing more");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void enchantmentsActOnScaleBackedPiecesAsBefore(GameTestHelper h) {
        ItemStack stitched = piece(Items.DIAMOND_CHESTPLATE, Finish.HARDENED, 80, 100, null);
        stitched.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 4);
        stitched.enchant(Enchantments.UNBREAKING, 3);
        new LeatherworkData(Finish.HARNESS, 80, 100, null, "Test").apply(stitched);
        ItemStack plain = new ItemStack(Items.DIAMOND_CHESTPLATE);
        plain.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 4);
        var source = h.getLevel().damageSources().generic();
        h.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(Enchantments.ALL_DAMAGE_PROTECTION, stitched) == 4
                && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, stitched) == 3
                && LeatherworkData.get(stitched).map(LeatherworkData::finish).orElse(null) == Finish.HARNESS
                && LeatherworkProfile.of(stitched) == LeatherworkProfile.SCALE_BACKED, "Rewriting the leatherwork keeps the enchantments and the profile");
        h.assertTrue(EnchantmentHelper.getDamageProtection(List.of(stitched), source) == EnchantmentHelper.getDamageProtection(List.of(plain), source),
                "Protection protects a stitched piece exactly as a plain one");
        h.succeed();
    }

    // ------------------------------------------------------------------ repair

    @GameTest(template = "empty")
    public static void scrapsLeaveScaleBackedArmorToItsOwnRepairs(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        ItemStack awl = new ItemStack(ModItems.AWL.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, awl);
        player.getInventory().add(new ItemStack(ModItems.LEATHER_SCRAPS.get(), 4));
        ItemStack scraps = FieldRepair.findScraps(player);
        ItemStack chest = piece(Items.DIAMOND_CHESTPLATE, Finish.HARNESS, 80, 50, null);
        chest.setDamageValue(100);

        h.assertTrue(!FieldRepair.isRepairable(chest) && !FieldRepair.repair(player, chest, scraps, awl)
                && chest.getDamageValue() == 100 && scraps.getCount() == 4, "Scraps do not patch a scale-backed piece");
        Slot slot = new Slot(new SimpleContainer(chest), 0, 0, 0);
        h.assertTrue(!((LeatherScrapsItem) scraps.getItem()).overrideStackedOnOther(scraps, slot, ClickAction.SECONDARY, player)
                && chest.getDamageValue() == 100 && scraps.getCount() == 4, "Right-clicking scraps onto it in the inventory does nothing");

        player.setItemSlot(EquipmentSlot.CHEST, chest);
        h.assertTrue(FieldRepair.mostWornEquipped(player) == null && FieldRepair.mostWornUnpatchable(player) == chest,
                "The awl finds no piece to patch, and knows the worn one is scale-backed");
        AwlItem awlItem = (AwlItem) awl.getItem();
        h.assertTrue(awlItem.use(h.getLevel(), player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.PASS, "Using the awl starts no patch");
        awlItem.finishUsingItem(awl, h.getLevel(), player);
        h.assertTrue(chest.getDamageValue() == 100 && scraps.getCount() == 4 && awl.getDamageValue() == 0, "Nothing is patched or spent");

        ItemStack leather = DataGameTests.boots(Finish.HARNESS, 50);
        leather.setDamageValue(30);
        h.assertTrue(FieldRepair.repair(player, leather, scraps, awl) && leather.getDamageValue() < 30, "Ordinary leather still patches");

        try {
            ServerConfig.SCALE_ALLOW_SCRAP_REPAIR.set(true);
            h.assertTrue(FieldRepair.isRepairable(chest) && FieldRepair.repair(player, chest, scraps, awl) && chest.getDamageValue() < 100,
                    "allowScrapRepair lets scraps patch it");
        } finally {
            ServerConfig.SCALE_ALLOW_SCRAP_REPAIR.set(false);
        }

        ItemStack worn = piece(Items.DIAMOND_CHESTPLATE, Finish.HARNESS, 80, 50, null);
        worn.setDamageValue(300);
        AnvilMenu anvil = new AnvilMenu(0, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(STATION)));
        anvil.getSlot(0).set(worn);
        anvil.getSlot(1).set(new ItemStack(Items.DIAMOND, 2));
        ItemStack mended = anvil.getSlot(2).getItem();
        h.assertTrue(mended.is(Items.DIAMOND_CHESTPLATE) && mended.getDamageValue() < 300 && LeatherworkData.get(mended).isPresent()
                && LeatherworkProfile.of(mended) == LeatherworkProfile.SCALE_BACKED, "The anvil repairs it with its own material and keeps the leatherwork");
        h.succeed();
    }

    // ------------------------------------------------------------------ helpers

    static ItemStack piece(Item item, Finish finish, int integrity, int workmanship, @Nullable TraitRef trait) {
        ItemStack stack = new ItemStack(item);
        new LeatherworkData(finish, integrity, workmanship, trait, "Test").apply(stack);
        return stack;
    }

    static double sum(ItemStack stack, EquipmentSlot slot, Attribute attribute, AttributeModifier.Operation op) {
        double total = 0;
        for (AttributeModifier m : stack.getAttributeModifiers(slot).get(attribute)) {
            if (m.getOperation() == op) total += m.getAmount();
        }
        return total;
    }

    private static boolean close(double a, double b) {
        return Math.abs(a - b) < EPSILON;
    }

    private static int damage(ItemStack stack, int points) {
        int applied = 0;
        for (int i = 0; i < points; i++) applied += LeatherworkEffects.adjustDamage(stack, 1);
        return applied;
    }

    private static LeatherworkersBenchBlockEntity bench(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.LEATHERWORKERS_BENCH.get());
        return (LeatherworkersBenchBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
    }

    /** A player with an awl in hand and a knife in the inventory. */
    private static FakePlayer worker(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.AWL.get()));
        player.getInventory().add(new ItemStack(ModItems.TANNERS_KNIFE.get()));
        return player;
    }

    private static void prepare(TestSupport.Operator op, Finish finish) {
        op.send(WorkAction.BENCH_PREPARE, 0, new int[]{finish.ordinal()}, new short[0], new short[0], STAND_IN, null);
    }

    @Nullable
    private static BenchOffersPacket.Entry offer(FakePlayer player, LeatherworkersBenchBlockEntity bench) {
        return BenchStationLogic.offers(player, bench).entries().stream().filter(e -> e.recipe().equals(STAND_IN)).findFirst().orElse(null);
    }

    /** The two Supple leather and eight scales are all back in the inventory. */
    private static boolean materials(Inventory inv) {
        return inv.countItem(ModItems.leather(Finish.SUPPLE)) == 2 && inv.countItem(Items.PRISMARINE_SHARD) == 8;
    }

    private static int dropped(GameTestHelper h, Item item) {
        return h.getEntities(EntityType.ITEM, STATION, 4).stream().map(ItemEntity::getItem).filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private ScaleBackedGameTests() {}
}
