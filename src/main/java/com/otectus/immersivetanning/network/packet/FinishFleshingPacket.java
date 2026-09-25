package com.otectus.immersivetanning.network.packet;

import com.otectus.immersivetanning.minigame.SessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The player declares the hide done before the time runs out. */
public record FinishFleshingPacket(int sessionId) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
    }

    public static FinishFleshingPacket decode(FriendlyByteBuf buf) {
        return new FinishFleshingPacket(buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) SessionManager.finishFleshingEarly(player, sessionId);
        });
        context.setPacketHandled(true);
    }
}
