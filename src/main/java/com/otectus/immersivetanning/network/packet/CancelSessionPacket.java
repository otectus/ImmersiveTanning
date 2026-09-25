package com.otectus.immersivetanning.network.packet;

import com.otectus.immersivetanning.minigame.SessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The player closed a session screen before it finished. */
public record CancelSessionPacket(int sessionId) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
    }

    public static CancelSessionPacket decode(FriendlyByteBuf buf) {
        return new CancelSessionPacket(buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) SessionManager.cancelFromClient(player, sessionId);
        });
        context.setPacketHandled(true);
    }
}
