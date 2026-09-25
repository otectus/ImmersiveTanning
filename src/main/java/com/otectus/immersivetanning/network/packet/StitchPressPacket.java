package com.otectus.immersivetanning.network.packet;

import com.otectus.immersivetanning.minigame.SessionManager;
import net.minecraft.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** One press of the awl: a sequence number and the client session time. */
public record StitchPressPacket(int sessionId, int sequence, int clientMs) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeVarInt(sequence);
        buf.writeVarInt(Math.max(0, clientMs));
    }

    public static StitchPressPacket decode(FriendlyByteBuf buf) {
        return new StitchPressPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        long arrival = Util.getMillis();
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) SessionManager.stitchPress(player, sessionId, sequence, clientMs, arrival);
        });
        context.setPacketHandled(true);
    }
}
