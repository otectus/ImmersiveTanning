package com.otectus.immersivetanning.network.packet;

import com.otectus.immersivetanning.client.ClientPacketHandler;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.minigame.StitchPattern;
import com.otectus.immersivetanning.trait.TraitRef;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** The leather is cut: stitching starts after the ready delay with this seam. */
public record StitchStartPacket(int sessionId, StitchPattern pattern, int readyDelayMs, ItemStack result, Finish finish,
                                @Nullable TraitRef trait, int integrity) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
        pattern.write(buf);
        buf.writeVarInt(readyDelayMs);
        buf.writeItem(result);
        buf.writeEnum(finish);
        TraitRef.writeNullable(buf, trait);
        buf.writeVarInt(integrity);
    }

    public static StitchStartPacket decode(FriendlyByteBuf buf) {
        return new StitchStartPacket(buf.readVarInt(), StitchPattern.read(buf), buf.readVarInt(), buf.readItem(),
                buf.readEnum(Finish.class), TraitRef.readNullable(buf), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.stitchStart(this));
    }
}
