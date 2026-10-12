package com.otectus.immersivetanning.network.packet;

import com.otectus.immersivetanning.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A station message (status, instruction or failure). The client keeps it readable above the hotbar for its configured
 * time instead of letting the next action-bar message replace it; {@code failure} marks problems the player must act on.
 */
public record StationNoticePacket(Component message, boolean failure) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeComponent(message);
        buf.writeBoolean(failure);
    }

    public static StationNoticePacket decode(FriendlyByteBuf buf) {
        return new StationNoticePacket(buf.readComponent(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.stationNotice(this));
    }
}
