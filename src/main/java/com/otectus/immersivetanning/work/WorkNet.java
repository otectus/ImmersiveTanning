package com.otectus.immersivetanning.work;

import com.otectus.immersivetanning.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Sending helpers for the work layer: to one player, or to everyone watching a station but its operator. */
public final class WorkNet {

    public static void to(ServerPlayer player, Object packet) {
        if (player instanceof FakePlayer || player.connection == null) return;
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** Every player tracking the station's chunk except {@code except}. */
    public static void toWatchers(ServerLevel level, BlockPos pos, @Nullable ServerPlayer except, Object packet) {
        for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(new ChunkPos(pos), false)) {
            if (player != except) to(player, packet);
        }
    }

    private WorkNet() {}
}
