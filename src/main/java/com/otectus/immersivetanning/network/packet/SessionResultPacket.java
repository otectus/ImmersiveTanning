package com.otectus.immersivetanning.network.packet;

import com.otectus.immersivetanning.client.ClientPacketHandler;
import com.otectus.immersivetanning.minigame.TanningSession;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Final server-computed result. For fleshing, {@code score} is the hide's Integrity and the three measures explain it;
 * for stitching it is the Workmanship.
 */
public record SessionResultPacket(int sessionId, TanningSession.Kind kind, int score, float coverage, float control,
                                  float overwork, boolean timedOut) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeEnum(kind);
        buf.writeVarInt(score);
        buf.writeFloat(coverage);
        buf.writeFloat(control);
        buf.writeFloat(overwork);
        buf.writeBoolean(timedOut);
    }

    public static SessionResultPacket decode(FriendlyByteBuf buf) {
        return new SessionResultPacket(buf.readVarInt(), buf.readEnum(TanningSession.Kind.class), buf.readVarInt(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.sessionResult(this));
    }
}
