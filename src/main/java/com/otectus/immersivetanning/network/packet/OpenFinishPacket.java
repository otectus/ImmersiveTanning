package com.otectus.immersivetanning.network.packet;

import com.otectus.immersivetanning.client.ClientPacketHandler;
import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.leather.Finish;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Asks the player how to finish the wet hide they are mounting on a rack. */
public record OpenFinishPacket(BlockPos pos, HideData hide, Finish last) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        hide.write(buf);
        buf.writeEnum(last);
    }

    public static OpenFinishPacket decode(FriendlyByteBuf buf) {
        return new OpenFinishPacket(buf.readBlockPos(), HideData.read(buf), buf.readEnum(Finish.class));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openFinish(this));
    }
}
