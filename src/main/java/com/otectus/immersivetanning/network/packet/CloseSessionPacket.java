package com.otectus.immersivetanning.network.packet;

import com.otectus.immersivetanning.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The server ended a session (station gone, tool changed, moved away...) and says why. */
public record CloseSessionPacket(int sessionId, Component reason) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeComponent(reason);
    }

    public static CloseSessionPacket decode(FriendlyByteBuf buf) {
        return new CloseSessionPacket(buf.readVarInt(), buf.readComponent());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.closeSession(this));
    }
}
