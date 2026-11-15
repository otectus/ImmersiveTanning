package com.otectus.immersivetanning.network.packet;

import com.otectus.immersivetanning.client.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The server granted a lease at a station (the client leans in to work or watch), or ended one (the client lets go,
 * showing the reason when there is one). Only the server opens focus, after a real use of the block within reach.
 */
public record StationFocusPacket(boolean open, BlockPos pos, long instance, int lease, boolean operator, boolean assisted, Component reason) {

    public static StationFocusPacket open(BlockPos pos, long instance, int lease, boolean operator, boolean assisted) {
        return new StationFocusPacket(true, pos, instance, lease, operator, assisted, Component.empty());
    }

    public static StationFocusPacket close(BlockPos pos, Component reason) {
        return new StationFocusPacket(false, pos, 0L, 0, false, false, reason);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(open);
        buf.writeBlockPos(pos);
        buf.writeLong(instance);
        buf.writeVarInt(lease);
        buf.writeBoolean(operator);
        buf.writeBoolean(assisted);
        buf.writeComponent(reason);
    }

    public static StationFocusPacket decode(FriendlyByteBuf buf) {
        return new StationFocusPacket(buf.readBoolean(), buf.readBlockPos(), buf.readLong(), buf.readVarInt(), buf.readBoolean(),
                buf.readBoolean(), buf.readComponent());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.focus(this)));
        ctx.get().setPacketHandled(true);
    }
}
