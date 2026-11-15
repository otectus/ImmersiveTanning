package com.otectus.immersivetanning.network.packet;

import com.otectus.immersivetanning.client.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The server's answer to one command, to its sender only: the sequence it answers, whether it was accepted, why not,
 * and the station's work revision afterwards, so the client can settle its prediction on the authoritative state.
 */
public record StationAckPacket(BlockPos pos, int sequence, boolean accepted, int code, int revision) {
    public static final int OK = 0;
    public static final int STALE = 1;
    public static final int REFUSED = 2;
    public static final int NO_TOOL = 3;
    public static final int NOT_OWNER = 4;
    public static final int WRONG_STAGE = 5;
    public static final int OUT_OF_PLACE = 6;
    public static final int NO_MATERIAL = 7;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(sequence);
        buf.writeBoolean(accepted);
        buf.writeVarInt(code);
        buf.writeVarInt(revision);
    }

    public static StationAckPacket decode(FriendlyByteBuf buf) {
        return new StationAckPacket(buf.readBlockPos(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.ack(this)));
        ctx.get().setPacketHandled(true);
    }
}
