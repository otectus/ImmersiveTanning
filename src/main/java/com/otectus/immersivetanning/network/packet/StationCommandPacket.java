package com.otectus.immersivetanning.network.packet;

import com.otectus.immersivetanning.work.WorkAction;
import com.otectus.immersivetanning.work.WorkCommandHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * One intent at one station: the envelope every hands-on action travels in. It names the station (position and
 * instance, so a replaced block is a different station), the lease and job it was meant for, a monotonic sequence, the
 * action, a few small integers and up to {@link #MAX_POINTS} quantised points (12 bits per axis, in the task's own
 * coordinates). Never a score, an item or a verdict. A malformed or oversized command is dropped whole.
 */
public record StationCommandPacket(BlockPos pos, long instance, int lease, int job, int sequence, WorkAction action, int flags,
                                   int[] ints, short[] xs, short[] ys, @Nullable ResourceLocation id, @Nullable ResourceLocation id2) {
    public static final int MAX_POINTS = 48;
    public static final int MAX_INTS = 6;
    public static final int COORD_MAX = 4095;

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    public int arg(int index, int fallback) {
        return index < ints.length ? ints[index] : fallback;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeLong(instance);
        buf.writeVarInt(lease);
        buf.writeVarInt(job);
        buf.writeVarInt(sequence);
        buf.writeVarInt(action.ordinal());
        buf.writeByte(flags);
        buf.writeVarInt(Math.min(ints.length, MAX_INTS));
        for (int i = 0; i < Math.min(ints.length, MAX_INTS); i++) buf.writeVarInt(ints[i]);
        int n = Math.min(Math.min(xs.length, ys.length), MAX_POINTS);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeShort(xs[i]);
            buf.writeShort(ys[i]);
        }
        buf.writeBoolean(id != null);
        if (id != null) buf.writeResourceLocation(id);
        buf.writeBoolean(id2 != null);
        if (id2 != null) buf.writeResourceLocation(id2);
    }

    /** Null for anything malformed: unknown action, too many values, coordinates out of range. */
    @Nullable
    public static StationCommandPacket decode(FriendlyByteBuf buf) {
        try {
            BlockPos pos = buf.readBlockPos();
            long instance = buf.readLong();
            int lease = buf.readVarInt();
            int job = buf.readVarInt();
            int sequence = buf.readVarInt();
            WorkAction action = WorkAction.byId(buf.readVarInt());
            int flags = buf.readByte();
            int intCount = buf.readVarInt();
            if (action == null || intCount < 0 || intCount > MAX_INTS) return skip(buf);
            int[] ints = new int[intCount];
            for (int i = 0; i < intCount; i++) ints[i] = buf.readVarInt();
            int n = buf.readVarInt();
            if (n < 0 || n > MAX_POINTS) return skip(buf);
            short[] xs = new short[n];
            short[] ys = new short[n];
            for (int i = 0; i < n; i++) {
                xs[i] = buf.readShort();
                ys[i] = buf.readShort();
                if (xs[i] < 0 || xs[i] > COORD_MAX || ys[i] < 0 || ys[i] > COORD_MAX) return skip(buf);
            }
            ResourceLocation id = buf.readBoolean() ? buf.readResourceLocation() : null;
            ResourceLocation id2 = buf.readBoolean() ? buf.readResourceLocation() : null;
            return new StationCommandPacket(pos, instance, lease, job, sequence, action, flags, ints, xs, ys, id, id2);
        } catch (RuntimeException e) {
            return skip(buf);
        }
    }

    @Nullable
    private static StationCommandPacket skip(FriendlyByteBuf buf) {
        buf.skipBytes(buf.readableBytes());
        return null;
    }

    public static void handle(@Nullable StationCommandPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        if (packet != null && player != null) context.enqueueWork(() -> WorkCommandHandler.handle(player, packet));
        context.setPacketHandled(true);
    }

    /** A quantised coordinate as 0..1. */
    public static float unit(short q) {
        return q / (float) COORD_MAX;
    }

    public static short quantize(float v) {
        return (short) Math.max(0, Math.min(COORD_MAX, Math.round(v * COORD_MAX)));
    }

    /** Work-plane coordinates may reach a little past the mat; they travel offset and scaled into 0..1. */
    public static short quantizePlane(float v) {
        return quantize((v + 0.25F) / 1.5F);
    }

    public static float plane(short q) {
        return unit(q) * 1.5F - 0.25F;
    }
}
