package com.otectus.immersivetanning.network.packet;

import com.otectus.immersivetanning.minigame.SessionManager;
import com.otectus.immersivetanning.leather.Finish;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The finish chosen for the wet hide in hand. The server re-checks the hand and the rack. */
public record SelectFinishPacket(BlockPos pos, Finish finish) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeEnum(finish);
    }

    public static SelectFinishPacket decode(FriendlyByteBuf buf) {
        return new SelectFinishPacket(buf.readBlockPos(), buf.readEnum(Finish.class));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) SessionManager.selectFinish(player, pos, finish);
        });
        context.setPacketHandled(true);
    }
}
