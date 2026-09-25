package com.otectus.immersivetanning.network.packet;

import com.otectus.immersivetanning.minigame.SessionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** From the fleshing result: mount the next raw hide from the inventory on the same rack and flesh it. */
public record FleshNextPacket(BlockPos pos) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public static FleshNextPacket decode(FriendlyByteBuf buf) {
        return new FleshNextPacket(buf.readBlockPos());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) SessionManager.fleshNext(player, pos);
        });
        context.setPacketHandled(true);
    }
}
