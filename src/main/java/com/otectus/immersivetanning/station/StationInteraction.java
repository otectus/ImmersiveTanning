package com.otectus.immersivetanning.station;

import com.otectus.immersivetanning.blockentity.LeatherworkersBenchBlockEntity;
import com.otectus.immersivetanning.blockentity.StationBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersVatBlockEntity;
import com.otectus.immersivetanning.hide.HideItems;
import com.otectus.immersivetanning.item.TannersKnifeItem;
import com.otectus.immersivetanning.item.UnfinishedLeatherworkItem;
import com.otectus.immersivetanning.network.packet.StationFocusPacket;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModTags;
import com.otectus.immersivetanning.util.Feedback;
import com.otectus.immersivetanning.work.StationLease;
import com.otectus.immersivetanning.work.WorkNet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;

/**
 * One route for using a station, so a press resolves to exactly one action:
 * <ul>
 *     <li><b>use</b> leans in to work (the focused view); a held item that belongs there is placed once as part of
 *     it (a raw hide on the beam, a wet hide in the frame, water, bark or hides into the vat, a workpiece on the bench);</li>
 *     <li><b>sneak + use</b> is the quick world action without the view: insert, take, or lace a wet hide with the
 *     last finish;</li>
 *     <li>automation (fake players) gets the quick action too, and never works a hide or a piece itself.</li>
 * </ul>
 */
public final class StationInteraction {

    public static InteractionResult use(Level level, BlockPos pos, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (player.isSecondaryUseActive() && held.getItem() instanceof BlockItem) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof StationBlockEntity station) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.PASS;
        }
        if (sp instanceof FakePlayer || sp.connection == null) quick(sp, station, held, hand, sp.isSecondaryUseActive());
        else if (sp.isSecondaryUseActive()) quick(sp, station, held, hand, true);
        else {
            place(sp, station, held);
            openFocus(sp, station);
        }
        return InteractionResult.CONSUME;
    }

    /** Grants a lease and leans the player's view in (watching, when someone else works the station). */
    public static void openFocus(ServerPlayer player, StationBlockEntity station) {
        StationLease lease = station.grant(player, false, player.level().getGameTime());
        WorkNet.to(player, StationFocusPacket.open(station.getBlockPos(), station.instanceId(), lease.id, lease.operating(), lease.assisted()));
        if (!lease.operating()) Feedback.info(player, "watching", station.operatorName());
    }

    /** The held item that belongs at the station is placed, once, as part of using it. */
    private static void place(ServerPlayer player, StationBlockEntity station, ItemStack held) {
        if (held.isEmpty() || station.isLockedFor(player.getUUID())) return;
        if (station instanceof TannersRackBlockEntity rack) {
            if (rack.isEmpty()) RackStationLogic.mount(player, rack, held);
        } else if (station instanceof TannersVatBlockEntity vat) {
            if (held.is(Items.WATER_BUCKET)) VatStationLogic.pour(player, vat, InteractionHand.MAIN_HAND);
            else if (held.is(ModTags.TANNIN_SOURCES)) VatStationLogic.addBark(player, vat, held, held.getCount());
            else if (held.is(ModItems.SCRAPED_HIDE.get())) VatStationLogic.lower(player, vat, held, held.getCount());
        } else if (station instanceof LeatherworkersBenchBlockEntity bench) {
            if (held.getItem() instanceof UnfinishedLeatherworkItem) BenchStationLogic.mountWorkpiece(player, bench, held);
        }
    }

    private static void quick(ServerPlayer player, StationBlockEntity station, ItemStack held, InteractionHand hand, boolean sneaking) {
        if (station.isLockedFor(player.getUUID())) {
            Feedback.fail(player, "station_in_use");
            return;
        }
        if (station instanceof TannersRackBlockEntity rack) quickRack(player, rack, held, sneaking);
        else if (station instanceof TannersVatBlockEntity vat) VatStationLogic.quick(player, vat, hand);
        else if (station instanceof LeatherworkersBenchBlockEntity bench) quickBench(player, bench, held);
    }

    private static void quickRack(ServerPlayer player, TannersRackBlockEntity rack, ItemStack held, boolean sneaking) {
        switch (rack.mode()) {
            case EMPTY -> {
                if (HideItems.resolve(player.level(), held).isPresent()) {
                    RackStationLogic.mount(player, rack, held);
                    Feedback.info(player, "flesh_with_knife");
                } else if (held.is(ModItems.WET_TANNED_HIDE.get())) {
                    // The last-finish shortcut; automation only laces the hide in, it never chooses a finish.
                    if (sneaking) RackStationLogic.quickFinish(player, rack, held);
                    else RackStationLogic.mount(player, rack, held);
                } else if (held.is(ModItems.SCRAPED_HIDE.get())) {
                    Feedback.info(player, "scraped_to_vat");
                } else {
                    Feedback.info(player, "rack_empty");
                }
            }
            case RAW -> {
                if (held.isEmpty() && sneaking) RackStationLogic.take(player, rack);
                else Feedback.info(player, held.getItem() instanceof TannersKnifeItem ? "use_to_work" : "flesh_with_knife");
            }
            case SCRAPED, FRAMING -> {
                if (held.isEmpty() && sneaking) RackStationLogic.take(player, rack);
                else Feedback.info(player, rack.mode() == TannersRackBlockEntity.Mode.SCRAPED ? "scraped_ready" : "choose_finish");
            }
            case DRYING -> {
                if (rack.isDry() || (held.isEmpty() && sneaking)) RackStationLogic.take(player, rack);
                else {
                    int percent = Math.round(rack.dryProgress(player.level().getGameTime()) * 100F);
                    Feedback.info(player, "drying_progress", rack.finish().displayName(), percent);
                }
            }
        }
    }

    private static void quickBench(ServerPlayer player, LeatherworkersBenchBlockEntity bench, ItemStack held) {
        if (held.getItem() instanceof UnfinishedLeatherworkItem && bench.job() == null) {
            BenchStationLogic.mountWorkpiece(player, bench, held);
        } else if (bench.hasOutput()) {
            BenchStationLogic.collect(player, bench);
        } else {
            Feedback.info(player, bench.job() != null ? "use_to_work" : "use_bench");
        }
    }

    private StationInteraction() {}
}
