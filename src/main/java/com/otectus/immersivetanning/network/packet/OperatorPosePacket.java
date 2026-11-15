package com.otectus.immersivetanning.network.packet;

import com.otectus.immersivetanning.client.ClientPacketHandler;
import com.otectus.immersivetanning.work.WorkCommandHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Where the operator's tool (or a carried panel) is, a few times a second: presentation only, so watchers see a tool
 * that moves with the work. The operator sends it; the server checks the lease and a rate and relays it to watchers
 * with the operator's entity id. It changes nothing in the work. Positions are station-local pixels times 64.
 */
public record OperatorPosePacket(BlockPos pos, int lease, int entity, int tool, boolean pressed, short x, short y, short z,
                                 int held, short rot) {
    public static final int TOOL_NONE = 0;
    public static final int TOOL_KNIFE = 1;
    public static final int TOOL_AWL = 2;
    public static final int TOOL_PADDLE = 3;
    public static final int TOOL_SLICKER = 4;
    public static final int TOOL_HAND = 5;
    public static final float SCALE = 64F;

    public static short pack(double pixels) {
        return (short) Math.max(-32000, Math.min(32000, Math.round(pixels * SCALE)));
    }

    public static float unpack(short value) {
        return value / SCALE;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(lease);
        buf.writeVarInt(entity);
        buf.writeByte(tool);
        buf.writeBoolean(pressed);
        buf.writeShort(x);
        buf.writeShort(y);
        buf.writeShort(z);
        buf.writeVarInt(held + 1);
        buf.writeShort(rot);
    }

    public static OperatorPosePacket decode(FriendlyByteBuf buf) {
        return new OperatorPosePacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readBoolean(),
                buf.readShort(), buf.readShort(), buf.readShort(), buf.readVarInt() - 1, buf.readShort());
    }

    /** Registered for both directions: the operator's report to the server, the server's relay to watchers. */
    public static void handle(OperatorPosePacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context.getDirection().getReceptionSide().isServer()) {
            ServerPlayer player = context.getSender();
            if (player != null) context.enqueueWork(() -> WorkCommandHandler.relayPose(player, packet));
        } else {
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.pose(packet)));
        }
        context.setPacketHandled(true);
    }
}
