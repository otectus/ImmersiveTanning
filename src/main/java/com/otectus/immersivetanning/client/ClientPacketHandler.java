package com.otectus.immersivetanning.client;

import com.otectus.immersivetanning.client.screen.FinishSelectScreen;
import com.otectus.immersivetanning.client.screen.FleshingScreen;
import com.otectus.immersivetanning.client.screen.LeatherworkScreen;
import com.otectus.immersivetanning.network.packet.CloseSessionPacket;
import com.otectus.immersivetanning.network.packet.OpenBenchPacket;
import com.otectus.immersivetanning.network.packet.OpenFinishPacket;
import com.otectus.immersivetanning.network.packet.OpenFleshingPacket;
import com.otectus.immersivetanning.network.packet.SessionResultPacket;
import com.otectus.immersivetanning.network.packet.StationEffectPacket;
import com.otectus.immersivetanning.network.packet.StationNoticePacket;
import com.otectus.immersivetanning.network.packet.StitchResultPacket;
import com.otectus.immersivetanning.network.packet.StitchStartPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Client handlers for server packets. Runs on the client main thread. */
public final class ClientPacketHandler {

    public static void openFleshing(OpenFleshingPacket packet) {
        Minecraft.getInstance().setScreen(new FleshingScreen(packet));
    }

    public static void openFinish(OpenFinishPacket packet) {
        Minecraft.getInstance().setScreen(new FinishSelectScreen(packet));
    }

    public static void openBench(OpenBenchPacket packet) {
        Minecraft.getInstance().setScreen(new LeatherworkScreen(packet));
    }

    public static void stitchStart(StitchStartPacket packet) {
        if (Minecraft.getInstance().screen instanceof LeatherworkScreen screen) screen.onStart(packet);
    }

    public static void stitchResult(StitchResultPacket packet) {
        if (Minecraft.getInstance().screen instanceof LeatherworkScreen screen) screen.onStitchResult(packet);
    }

    public static void sessionResult(SessionResultPacket packet) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof FleshingScreen fleshing) fleshing.onResult(packet);
        else if (screen instanceof LeatherworkScreen bench) bench.onResult(packet);
    }

    public static void closeSession(CloseSessionPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof FleshingScreen fleshing && fleshing.sessionId() == packet.sessionId()) fleshing.closeByServer();
        else if (mc.screen instanceof LeatherworkScreen bench && bench.sessionId() == packet.sessionId()) bench.closeByServer();
        if (mc.player != null && !packet.reason().getString().isEmpty()) StationNotices.show(packet.reason(), true);
    }

    public static void stationNotice(StationNoticePacket packet) {
        StationNotices.show(packet.message(), packet.failure());
    }

    public static void stationEffect(StationEffectPacket packet) {
        ClientEffects.spawn(packet.pos(), packet.type(), packet.strength());
    }

    private ClientPacketHandler() {}
}
