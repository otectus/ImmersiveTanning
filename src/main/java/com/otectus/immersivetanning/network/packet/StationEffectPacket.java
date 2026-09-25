package com.otectus.immersivetanning.network.packet;

import com.otectus.immersivetanning.client.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A station effect (scraping dust, stitch, splash) that each client spawns with its own particle settings. */
public record StationEffectPacket(BlockPos pos, int type, float strength) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(type);
        buf.writeFloat(strength);
    }

    public static StationEffectPacket decode(FriendlyByteBuf buf) {
        return new StationEffectPacket(buf.readBlockPos(), buf.readVarInt(), buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.stationEffect(this));
    }
}
