package com.otectus.immersivetanning.test;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.item.TannersKnifeItem;
import com.otectus.immersivetanning.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.function.Consumer;

/**
 * The Tanner's Knife strips bark through the server's own interaction path (the one a right-click packet takes):
 * once per log, keeping the log's axis, for the configured bark and one point of wear, and never for a refused strip.
 */
@GameTestHolder(ImmersiveTanning.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KnifeGameTests {

    @GameTest(template = "empty")
    public static void knifeStripsLogsOnEveryAxisForBarkOnce(GameTestHelper h) {
        int perLog = ServerConfig.get(ServerConfig.BARK_PER_LOG);
        FakePlayer player = knifeHolder(h);
        ItemStack knife = player.getMainHandItem();
        int strips = 0;
        for (Direction.Axis axis : Direction.Axis.values()) {
            BlockPos pos = new BlockPos(1 + axis.ordinal(), 1, 1);
            h.setBlock(pos, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis));
            InteractionResult result = strip(h, player, pos);
            BlockState after = h.getBlockState(pos);
            strips++;
            h.assertTrue(after.is(Blocks.STRIPPED_OAK_LOG) && after.getValue(RotatedPillarBlock.AXIS) == axis,
                    "An oak log on the " + axis + " axis strips and keeps its axis, got " + after + " (" + result + ")");
            h.assertTrue(bark(player) == strips * perLog, "Each strip gives " + perLog + " bark, got " + bark(player) + " after " + strips);
            h.assertTrue(knife.getDamageValue() == strips, "Each strip wears the knife by one, got " + knife.getDamageValue());
        }
        BlockPos stem = new BlockPos(1, 1, 3);
        h.setBlock(stem, Blocks.CRIMSON_STEM);
        strip(h, player, stem);
        h.assertTrue(h.getBlockState(stem).is(Blocks.STRIPPED_CRIMSON_STEM) && bark(player) == 4 * perLog, "Nether stems strip too");

        int barkBefore = bark(player);
        int wearBefore = knife.getDamageValue();
        strip(h, player, new BlockPos(1, 1, 1));
        h.assertTrue(bark(player) == barkBefore && knife.getDamageValue() == wearBefore, "A stripped log gives nothing more");
        BlockPos stone = new BlockPos(3, 1, 3);
        h.setBlock(stone, Blocks.STONE);
        strip(h, player, stone);
        h.assertTrue(h.getBlockState(stone).is(Blocks.STONE) && bark(player) == barkBefore && knife.getDamageValue() == wearBefore,
                "Stone gives nothing");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aRefusedStripAwardsNothing(GameTestHelper h) {
        int perLog = ServerConfig.get(ServerConfig.BARK_PER_LOG);
        FakePlayer player = knifeHolder(h);
        BlockPos pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, Blocks.OAK_LOG);
        // Another mod refusing the strip (the cancellable tool-modification event) must leave everything untouched.
        Consumer<BlockEvent.BlockToolModificationEvent> veto = e -> {
            if (e.getHeldItemStack().getItem() instanceof TannersKnifeItem && e.getToolAction() == ToolActions.AXE_STRIP) e.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.BlockToolModificationEvent.class, veto);
        try {
            strip(h, player, pos);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(veto);
        }
        h.assertTrue(h.getBlockState(pos).is(Blocks.OAK_LOG) && bark(player) == 0 && player.getMainHandItem().getDamageValue() == 0,
                "A refused strip leaves the log, gives no bark and costs no wear");
        strip(h, player, pos);
        h.assertTrue(h.getBlockState(pos).is(Blocks.STRIPPED_OAK_LOG) && bark(player) == perLog, "Without the refusal it strips");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeStripsWithoutWearAndAFullInventoryDropsTheBark(GameTestHelper h) {
        int perLog = ServerConfig.get(ServerConfig.BARK_PER_LOG);
        FakePlayer creative = knifeHolder(h);
        creative.setGameMode(GameType.CREATIVE);
        BlockPos pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, Blocks.BIRCH_LOG);
        strip(h, creative, pos);
        h.assertTrue(h.getBlockState(pos).is(Blocks.STRIPPED_BIRCH_LOG) && bark(creative) == perLog
                && creative.getMainHandItem().getDamageValue() == 0, "Creative strips for bark without wear");

        FakePlayer full = knifeHolder(h);
        for (int slot = 1; slot < full.getInventory().items.size(); slot++) full.getInventory().items.set(slot, new ItemStack(Items.COBBLESTONE, 64));
        BlockPos second = new BlockPos(3, 1, 1);
        h.setBlock(second, Blocks.SPRUCE_LOG);
        strip(h, full, second);
        int dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(full.blockPosition()).inflate(2)).stream()
                .filter(e -> e.getItem().is(ModItems.BARK.get())).mapToInt(e -> e.getItem().getCount()).sum();
        h.assertTrue(h.getBlockState(second).is(Blocks.STRIPPED_SPRUCE_LOG) && bark(full) == 0 && dropped == perLog,
                "A full inventory drops exactly the bark, dropped " + dropped);
        h.succeed();
    }

    private static FakePlayer knifeHolder(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TANNERS_KNIFE.get()));
        return player;
    }

    /** Right-clicks the block's top with the main-hand item, as the server handles a player's use packet. */
    private static InteractionResult strip(GameTestHelper h, FakePlayer player, BlockPos relative) {
        BlockPos pos = h.absolutePos(relative);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return player.gameMode.useItemOn(player, h.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    private static int bark(FakePlayer player) {
        return player.getInventory().countItem(ModItems.BARK.get());
    }

    private KnifeGameTests() {}
}
