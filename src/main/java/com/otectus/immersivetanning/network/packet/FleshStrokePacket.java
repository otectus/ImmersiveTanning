package com.otectus.immersivetanning.network.packet;

import com.otectus.immersivetanning.minigame.SessionManager;
import net.minecraft.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A batch of knife positions (12-bit fixed point per axis). {@code begins} places the knife at the first point;
 * {@code ends} lifts it after the last. Points only: the server replays them and computes everything else.
 */
public record FleshStrokePacket(int sessionId, int sequence, boolean begins, boolean ends, int clientMs, short[] xs, short[] ys) {
    public static final int MAX_POINTS = 64;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeVarInt(sequence);
        buf.writeBoolean(begins);
        buf.writeBoolean(ends);
        buf.writeVarInt(Math.max(0, clientMs));
        int n = Math.min(xs.length, MAX_POINTS);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeShort(xs[i]);
            buf.writeShort(ys[i]);
        }
    }

    public static FleshStrokePacket decode(FriendlyByteBuf buf) {
        int session = buf.readVarInt();
        int sequence = buf.readVarInt();
        boolean begins = buf.readBoolean();
        boolean ends = buf.readBoolean();
        int clientMs = buf.readVarInt();
        int n = Math.min(buf.readVarInt(), MAX_POINTS);
        short[] xs = new short[n];
        short[] ys = new short[n];
        for (int i = 0; i < n; i++) {
            xs[i] = buf.readShort();
            ys[i] = buf.readShort();
        }
        return new FleshStrokePacket(session, sequence, begins, ends, clientMs, xs, ys);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        long arrival = Util.getMillis();
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) SessionManager.fleshStroke(player, this, arrival);
        });
        context.setPacketHandled(true);
    }
}
