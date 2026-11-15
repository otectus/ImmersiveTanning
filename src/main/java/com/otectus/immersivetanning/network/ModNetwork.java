package com.otectus.immersivetanning.network;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.network.packet.BenchOffersPacket;
import com.otectus.immersivetanning.network.packet.OperatorPosePacket;
import com.otectus.immersivetanning.network.packet.StationAckPacket;
import com.otectus.immersivetanning.network.packet.StationCommandPacket;
import com.otectus.immersivetanning.network.packet.StationEffectPacket;
import com.otectus.immersivetanning.network.packet.StationFocusPacket;
import com.otectus.immersivetanning.network.packet.StationNoticePacket;
import com.otectus.immersivetanning.network.packet.StationStrokePacket;
import com.otectus.immersivetanning.network.packet.WorkResultPacket;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Small, purpose-specific packets. Clients report intents only (one validated {@link StationCommandPacket} envelope);
 * the server owns every outcome. Protocol 3 (1.3) replaced the screen and rhythm packets of protocol 2 with station
 * commands, focus leases, acknowledgements and relays for watchers; a client and server of different protocols are
 * refused at login.
 */
public final class ModNetwork {
    public static final String VERSION = "3";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(ImmersiveTanning.id("main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        int id = 0;
        // Server -> client
        CHANNEL.messageBuilder(StationFocusPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StationFocusPacket::encode).decoder(StationFocusPacket::decode)
                .consumerNetworkThread(StationFocusPacket::handle).add();
        CHANNEL.messageBuilder(StationAckPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StationAckPacket::encode).decoder(StationAckPacket::decode)
                .consumerNetworkThread(StationAckPacket::handle).add();
        CHANNEL.messageBuilder(StationStrokePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StationStrokePacket::encode).decoder(StationStrokePacket::decode)
                .consumerNetworkThread(StationStrokePacket::handle).add();
        CHANNEL.messageBuilder(BenchOffersPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BenchOffersPacket::encode).decoder(BenchOffersPacket::decode)
                .consumerNetworkThread(BenchOffersPacket::handle).add();
        CHANNEL.messageBuilder(WorkResultPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WorkResultPacket::encode).decoder(WorkResultPacket::decode)
                .consumerNetworkThread(WorkResultPacket::handle).add();
        CHANNEL.messageBuilder(StationEffectPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StationEffectPacket::encode).decoder(StationEffectPacket::decode)
                .consumerMainThread(StationEffectPacket::handle).add();
        CHANNEL.messageBuilder(StationNoticePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StationNoticePacket::encode).decoder(StationNoticePacket::decode)
                .consumerMainThread(StationNoticePacket::handle).add();
        // Client -> server
        CHANNEL.messageBuilder(StationCommandPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(StationCommandPacket::encode).decoder(StationCommandPacket::decode)
                .consumerNetworkThread(StationCommandPacket::handle).add();
        // Both ways: the operator's tool pose, relayed by the server to watchers.
        CHANNEL.messageBuilder(OperatorPosePacket.class, id++)
                .encoder(OperatorPosePacket::encode).decoder(OperatorPosePacket::decode)
                .consumerNetworkThread(OperatorPosePacket::handle).add();
    }

    private ModNetwork() {}
}
