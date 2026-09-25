package com.otectus.immersivetanning.network.packet;

import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.minigame.SessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** The piece and leather group the player chose. The server re-checks it against what it offered. */
public record SelectBenchRecipePacket(int sessionId, ResourceLocation recipe, Finish finish, @Nullable ResourceLocation trait) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeResourceLocation(recipe);
        buf.writeEnum(finish);
        buf.writeBoolean(trait != null);
        if (trait != null) buf.writeResourceLocation(trait);
    }

    public static SelectBenchRecipePacket decode(FriendlyByteBuf buf) {
        return new SelectBenchRecipePacket(buf.readVarInt(), buf.readResourceLocation(), buf.readEnum(Finish.class),
                buf.readBoolean() ? buf.readResourceLocation() : null);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) SessionManager.selectBenchRecipe(player, sessionId, recipe, finish, trait);
        });
        context.setPacketHandled(true);
    }
}
