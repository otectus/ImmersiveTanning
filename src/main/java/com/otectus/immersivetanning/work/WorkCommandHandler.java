package com.otectus.immersivetanning.work;

import com.otectus.immersivetanning.blockentity.LeatherworkersBenchBlockEntity;
import com.otectus.immersivetanning.blockentity.StationBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersVatBlockEntity;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.network.packet.OperatorPosePacket;
import com.otectus.immersivetanning.network.packet.StationAckPacket;
import com.otectus.immersivetanning.network.packet.StationCommandPacket;
import com.otectus.immersivetanning.station.BenchStationLogic;
import com.otectus.immersivetanning.station.RackStationLogic;
import com.otectus.immersivetanning.station.StationInteraction;
import com.otectus.immersivetanning.station.VatStationLogic;
import net.minecraft.Util;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The one server entry point of hands-on work. Every command is checked, in order: the station's chunk is loaded (it
 * is never loaded for a command), the block is a station and the same instance, the player holds a lease there with
 * that id, the sequence is new, the rate allows it (a gesture's release always passes), and the player may still work
 * there (alive, same dimension, within reach, allowed to interact). Then the station's logic checks the work stage,
 * tools, materials, ownership and every coordinate. The sender gets an acknowledgement with the work revision.
 */
public final class WorkCommandHandler {

    /** A logic's answer to one command. */
    public record Reply(boolean accepted, int code) {
        public static final Reply OK = new Reply(true, StationAckPacket.OK);

        public static Reply refused(int code) {
            return new Reply(false, code);
        }
    }

    public static void handle(ServerPlayer player, StationCommandPacket p) {
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(p.pos())) return;
        BlockEntity be = level.getBlockEntity(p.pos());
        if (!(be instanceof StationBlockEntity station) || station.instanceId() != p.instance()) {
            ack(player, p, Reply.refused(StationAckPacket.STALE), 0);
            StationLeases.close(player, GlobalPos.of(level.dimension(), p.pos()), Component.empty());
            return;
        }
        if (p.action() == WorkAction.FOCUS) {
            // Moving straight to a neighbouring station: granted like a use of the block, within reach and sight.
            if (WorkRules.canWork(player, station) && WorkRules.canSee(player, p.pos())) StationInteraction.openFocus(player, station);
            return;
        }
        StationLease lease = station.lease(player.getUUID());
        if (lease == null || lease.id != p.lease()) {
            ack(player, p, Reply.refused(StationAckPacket.STALE), 0);
            if (p.action() != WorkAction.RELEASE) StationLeases.close(player, GlobalPos.of(level.dimension(), p.pos()), Component.empty());
            return;
        }
        if (!lease.sequence(p.sequence())) return; // duplicate or replayed
        long now = Util.getMillis();
        boolean release = p.action() == WorkAction.RELEASE || p.has(WorkAction.END);
        if (!release && !lease.rate(now)) return;
        if (p.action() == WorkAction.RELEASE) {
            station.release(player.getUUID());
            return;
        }
        if (!WorkRules.canWork(player, station)) {
            StationLeases.end(player, Component.translatable("message.immersive_tanning.session_interrupted"));
            return;
        }
        lease.touch(level.getGameTime());
        if (p.action().stroke()) {
            if (p.has(WorkAction.BEGIN)) lease.gesture(now, true);
            if (p.has(WorkAction.END)) lease.gesture(now, false);
            else if (lease.gestureSince() > 0) lease.gesture(now, true);
        }
        Reply reply;
        if (p.action() == WorkAction.KEEPALIVE) {
            lease.setAssisted(p.arg(0, 0) == 1 && ServerConfig.get(ServerConfig.ALLOW_ASSISTED_PROFILE));
            return;
        } else if (p.action() == WorkAction.SELECT_SLOT) {
            reply = selectSlot(player, p.arg(0, -1)) ? Reply.OK : Reply.refused(StationAckPacket.REFUSED);
        } else if (!lease.operating()) {
            reply = Reply.refused(StationAckPacket.REFUSED);
        } else if (station instanceof TannersRackBlockEntity rack) {
            reply = RackStationLogic.handle(player, rack, lease, p);
        } else if (station instanceof TannersVatBlockEntity vat) {
            reply = VatStationLogic.handle(player, vat, lease, p);
        } else if (station instanceof LeatherworkersBenchBlockEntity bench) {
            reply = BenchStationLogic.handle(player, bench, lease, p);
        } else {
            reply = Reply.refused(StationAckPacket.REFUSED);
        }
        ack(player, p, reply, revision(station));
    }

    private static void ack(ServerPlayer player, StationCommandPacket p, Reply reply, int revision) {
        WorkNet.to(player, new StationAckPacket(p.pos(), p.sequence(), reply.accepted(), reply.code(), revision));
    }

    private static int revision(StationBlockEntity station) {
        if (station instanceof TannersRackBlockEntity rack && rack.work() != null) return rack.work().revision();
        if (station instanceof LeatherworkersBenchBlockEntity bench && bench.job() != null) return bench.job().revision();
        return 0;
    }

    /**
     * The material drawer: a stack from anywhere in the inventory comes to hand, swapped with what the hand held
     * (nothing is created or lost; the server moves both stacks).
     */
    private static boolean selectSlot(ServerPlayer player, int slot) {
        Inventory inv = player.getInventory();
        if (slot < 0 || slot >= inv.items.size() || slot == inv.selected) return false;
        if (Inventory.isHotbarSlot(slot)) {
            inv.selected = slot;
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(slot));
            return true;
        }
        ItemStack wanted = inv.getItem(slot);
        ItemStack held = inv.getSelected();
        inv.setItem(slot, held);
        inv.setItem(inv.selected, wanted);
        inv.setChanged();
        return true;
    }

    /** A gesture whose release never arrived: the tool is lifted, nothing more is counted. */
    public static void watchdog(ServerPlayer player, StationBlockEntity station, StationLease lease) {
        lease.gesture(0L, false);
        if (station instanceof TannersRackBlockEntity rack) {
            RackStationLogic.lift(rack);
            rack.changed();
        } else if (station instanceof LeatherworkersBenchBlockEntity bench) {
            BenchStationLogic.lift(bench);
        } else {
            lease.sweep = 0F;
        }
    }

    /** Relays the operator's tool pose to watchers, at most about ten times a second. Presentation only. */
    public static void relayPose(ServerPlayer player, OperatorPosePacket p) {
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(p.pos()) || !(level.getBlockEntity(p.pos()) instanceof StationBlockEntity station)) return;
        StationLease lease = station.lease(player.getUUID());
        if (lease == null || lease.id != p.lease() || !lease.operating()) return;
        long now = Util.getMillis();
        if (now - lease.lastPoseMs < 80L) return;
        lease.lastPoseMs = now;
        WorkNet.toWatchers(level, p.pos(), player, new OperatorPosePacket(p.pos(), 0, player.getId(), p.tool(), p.pressed(),
                p.x(), p.y(), p.z(), p.held(), p.rot()));
    }

    private WorkCommandHandler() {}
}
