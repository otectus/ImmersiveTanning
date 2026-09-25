package com.otectus.immersivetanning.network.packet;

import com.otectus.immersivetanning.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The server's verdict on one press, so the client can correct its prediction. {@code index < 0}: ignored. */
public record StitchResultPacket(int sessionId, int sequence, int index, float accuracy, int next) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeVarInt(sequence);
        buf.writeVarInt(index + 1);
        buf.writeFloat(accuracy);
        buf.writeVarInt(next);
    }

    public static StitchResultPacket decode(FriendlyByteBuf buf) {
        return new StitchResultPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt() - 1, buf.readFloat(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.stitchResult(this));
    }
}
