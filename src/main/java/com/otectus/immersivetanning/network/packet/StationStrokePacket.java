package com.otectus.immersivetanning.network.packet;

import com.otectus.immersivetanning.client.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Accepted tool contact, relayed to everyone watching the station except its operator: the points the server took
 * (the same quantised points it scored), so watchers replay the scraping on their copy of the hide and see the knife,
 * paddle or thread where it really went. A watcher who arrives later gets the material as it is, not this history.
 */
public record StationStrokePacket(BlockPos pos, int kind, int entity, boolean begins, boolean ends, short[] xs, short[] ys) {
    public static final int FLESH = 0;
    public static final int TREAT = 1;
    public static final int SWEEP = 2;
    public static final int CUT = 3;
    public static final int PULL = 4;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(kind);
        buf.writeVarInt(entity);
        buf.writeBoolean(begins);
        buf.writeBoolean(ends);
        int n = Math.min(Math.min(xs.length, ys.length), StationCommandPacket.MAX_POINTS);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeShort(xs[i]);
            buf.writeShort(ys[i]);
        }
    }

    public static StationStrokePacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int kind = buf.readVarInt();
        int entity = buf.readVarInt();
        boolean begins = buf.readBoolean();
        boolean ends = buf.readBoolean();
        int n = Math.min(buf.readVarInt(), StationCommandPacket.MAX_POINTS);
        short[] xs = new short[n];
        short[] ys = new short[n];
        for (int i = 0; i < n; i++) {
            xs[i] = buf.readShort();
            ys[i] = buf.readShort();
        }
        return new StationStrokePacket(pos, kind, entity, begins, ends, xs, ys);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.stroke(this)));
        ctx.get().setPacketHandled(true);
    }
}
