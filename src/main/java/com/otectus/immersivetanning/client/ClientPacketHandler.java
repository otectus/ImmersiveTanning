package com.otectus.immersivetanning.client;

import com.otectus.immersivetanning.client.station.ClientWork;
import com.otectus.immersivetanning.client.station.StationBehavior;
import com.otectus.immersivetanning.client.station.StationFocus;
import com.otectus.immersivetanning.network.packet.BenchOffersPacket;
import com.otectus.immersivetanning.network.packet.OperatorPosePacket;
import com.otectus.immersivetanning.network.packet.StationAckPacket;
import com.otectus.immersivetanning.network.packet.StationEffectPacket;
import com.otectus.immersivetanning.network.packet.StationFocusPacket;
import com.otectus.immersivetanning.network.packet.StationNoticePacket;
import com.otectus.immersivetanning.network.packet.StationStrokePacket;
import com.otectus.immersivetanning.network.packet.WorkResultPacket;
import net.minecraft.client.Minecraft;

/** Client handlers for server packets. Runs on the client main thread. */
public final class ClientPacketHandler {

    public static void focus(StationFocusPacket packet) {
        StationFocus.open(packet);
    }

    public static void ack(StationAckPacket packet) {
        StationFocus.noteAck(packet);
        StationBehavior behavior = StationFocus.behaviorAt(packet.pos());
        if (behavior != null) behavior.ack(packet);
    }

    public static void stroke(StationStrokePacket packet) {
        ClientWork.stroke(packet);
    }

    public static void pose(OperatorPosePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && packet.entity() == mc.player.getId()) return;
        ClientWork.pose(packet);
    }

    public static void offers(BenchOffersPacket packet) {
        StationBehavior behavior = StationFocus.behaviorAt(packet.pos());
        if (behavior != null) behavior.offers(packet);
    }

    public static void result(WorkResultPacket packet) {
        StationBehavior behavior = StationFocus.behaviorAt(packet.pos());
        if (behavior != null) behavior.result(packet);
    }

    public static void stationNotice(StationNoticePacket packet) {
        StationNotices.show(packet.message(), packet.failure());
    }

    public static void stationEffect(StationEffectPacket packet) {
        ClientEffects.spawn(packet.pos(), packet.type(), packet.strength());
        ClientWork.animate(packet.pos(), packet.type(), packet.strength());
    }

    private ClientPacketHandler() {}
}
