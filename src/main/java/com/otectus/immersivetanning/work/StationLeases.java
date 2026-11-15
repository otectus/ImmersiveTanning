package com.otectus.immersivetanning.work;

import com.otectus.immersivetanning.blockentity.StationBlockEntity;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.network.ModNetwork;
import com.otectus.immersivetanning.network.packet.StationFocusPacket;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which station each player holds a lease at, so leaving, dying, changing dimension, walking away or idling ends it.
 * Server only; never saved. Ending a lease ends nothing else: the work stays in the station.
 */
public final class StationLeases {
    private static final Map<UUID, GlobalPos> BY_PLAYER = new HashMap<>();
    private static int tick;

    public static void index(UUID player, StationBlockEntity station) {
        if (station.getLevel() == null) return;
        GlobalPos previous = BY_PLAYER.put(player, GlobalPos.of(station.getLevel().dimension(), station.getBlockPos()));
        if (previous != null && !previous.pos().equals(station.getBlockPos()) && station.getLevel().getServer() != null) {
            StationBlockEntity old = at(station.getLevel().getServer(), previous);
            if (old != null) old.release(player);
        }
    }

    public static void forget(UUID player, StationBlockEntity station) {
        GlobalPos pos = BY_PLAYER.get(player);
        if (pos != null && station.getLevel() != null && pos.pos().equals(station.getBlockPos()) && pos.dimension() == station.getLevel().dimension()) {
            BY_PLAYER.remove(player);
        }
    }

    @Nullable
    public static GlobalPos of(UUID player) {
        return BY_PLAYER.get(player);
    }

    @Nullable
    public static StationBlockEntity at(MinecraftServer server, GlobalPos pos) {
        ServerLevel level = server.getLevel(pos.dimension());
        if (level == null || !level.isLoaded(pos.pos())) return null;
        BlockEntity be = level.getBlockEntity(pos.pos());
        return be instanceof StationBlockEntity station ? station : null;
    }

    /** Ends a player's lease wherever it is, telling their client why (null: no message). */
    public static void end(ServerPlayer player, @Nullable Component reason) {
        GlobalPos pos = BY_PLAYER.remove(player.getUUID());
        if (pos == null || player.getServer() == null) return;
        StationBlockEntity station = at(player.getServer(), pos);
        if (station != null) station.release(player.getUUID());
        close(player, pos, reason);
    }

    public static void close(ServerPlayer player, GlobalPos pos, @Nullable Component reason) {
        if (player instanceof FakePlayer || player.connection == null) return;
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                StationFocusPacket.close(pos.pos(), reason == null ? Component.empty() : reason));
    }

    /** The station's chunk unloads: every lease there ends (its work was saved with the chunk). */
    public static void stationUnloaded(StationBlockEntity station) {
        if (station.getLevel() == null || station.getLevel().getServer() == null) return;
        MinecraftServer server = station.getLevel().getServer();
        for (StationLease lease : station.leases()) {
            ServerPlayer player = server.getPlayerList().getPlayer(lease.player);
            station.release(lease.player);
            if (player != null) close(player, GlobalPos.of(station.getLevel().dimension(), station.getBlockPos()), message("session_interrupted"));
        }
    }

    public static void onLogout(ServerPlayer player) {
        end(player, null);
    }

    public static void onDeath(ServerPlayer player) {
        end(player, message("session_interrupted"));
    }

    public static void onDimensionChange(ServerPlayer player) {
        end(player, null);
    }

    /** Every few ticks: leases whose player is gone, away, out of reach or idle end. */
    public static void tick(MinecraftServer server) {
        if (BY_PLAYER.isEmpty() || ++tick % 10 != 0) return;
        long idleTicks = ServerConfig.get(ServerConfig.LEASE_IDLE_SECONDS) * 20L;
        for (Map.Entry<UUID, GlobalPos> entry : Map.copyOf(BY_PLAYER).entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            StationBlockEntity station = at(server, entry.getValue());
            if (player == null || station == null) {
                BY_PLAYER.remove(entry.getKey());
                if (station != null) station.release(entry.getKey());
                if (player != null) close(player, entry.getValue(), message("station_missing"));
                continue;
            }
            StationLease lease = station.lease(player.getUUID());
            if (lease == null) {
                BY_PLAYER.remove(entry.getKey());
                continue;
            }
            boolean idle = station.getLevel().getGameTime() - lease.lastActiveTick() > idleTicks;
            if (!WorkRules.canWork(player, station) || idle) {
                end(player, message(idle ? "session_expired" : "session_interrupted"));
            } else if (lease.gestureSince() > 0 && net.minecraft.Util.getMillis() - lease.gestureSince() > 2500L) {
                WorkCommandHandler.watchdog(player, station, lease);
            }
        }
    }

    public static void onServerStopping() {
        BY_PLAYER.clear();
    }

    private static Component message(String key) {
        return Component.translatable("message.immersive_tanning." + key);
    }

    private StationLeases() {}
}
