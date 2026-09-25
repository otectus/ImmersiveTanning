package com.otectus.immersivetanning.network.packet;

import com.otectus.immersivetanning.client.ClientPacketHandler;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.trait.TraitRef;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** Opens the fleshing screen with the seed from which both sides build the same hide. */
public record OpenFleshingPacket(int sessionId, BlockPos pos, HideSize size, long seed, int allowedMs, int readyDelayMs,
                                 @Nullable TraitRef trait) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeBlockPos(pos);
        buf.writeEnum(size);
        buf.writeLong(seed);
        buf.writeVarInt(allowedMs);
        buf.writeVarInt(readyDelayMs);
        TraitRef.writeNullable(buf, trait);
    }

    public static OpenFleshingPacket decode(FriendlyByteBuf buf) {
        return new OpenFleshingPacket(buf.readVarInt(), buf.readBlockPos(), buf.readEnum(HideSize.class), buf.readLong(),
                buf.readVarInt(), buf.readVarInt(), TraitRef.readNullable(buf));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openFleshing(this));
    }
}
