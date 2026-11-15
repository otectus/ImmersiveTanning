package com.otectus.immersivetanning.network.packet;

import com.otectus.immersivetanning.client.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A finished piece of work, told to the player who finished it: the score the server settled on (after progression
 * mods), a finish when it matters, and a few translation keys explaining it ("even_seams", "rough_cut"). Shown briefly
 * beside the work, never over it.
 */
public record WorkResultPacket(BlockPos pos, int kind, int score, int finish, List<String> explanation) {
    public static final int FLESHING = 0;
    public static final int LEATHERWORK = 1;
    public static final int REPAIR = 2;
    public static final int FINISHING = 3;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(kind);
        buf.writeVarInt(score);
        buf.writeVarInt(finish + 1);
        buf.writeVarInt(Math.min(explanation.size(), 6));
        for (int i = 0; i < Math.min(explanation.size(), 6); i++) buf.writeUtf(explanation.get(i), 64);
    }

    public static WorkResultPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int kind = buf.readVarInt();
        int score = buf.readVarInt();
        int finish = buf.readVarInt() - 1;
        int n = Math.min(buf.readVarInt(), 6);
        List<String> keys = new ArrayList<>(n);
        for (int i = 0; i < n; i++) keys.add(buf.readUtf(64));
        return new WorkResultPacket(pos, kind, score, finish, List.copyOf(keys));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.result(this)));
        ctx.get().setPacketHandled(true);
    }
}
