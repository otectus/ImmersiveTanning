package com.otectus.immersivetanning.network;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.network.packet.CancelSessionPacket;
import com.otectus.immersivetanning.network.packet.CloseSessionPacket;
import com.otectus.immersivetanning.network.packet.FinishFleshingPacket;
import com.otectus.immersivetanning.network.packet.FleshNextPacket;
import com.otectus.immersivetanning.network.packet.FleshStrokePacket;
import com.otectus.immersivetanning.network.packet.OpenBenchPacket;
import com.otectus.immersivetanning.network.packet.OpenFinishPacket;
import com.otectus.immersivetanning.network.packet.OpenFleshingPacket;
import com.otectus.immersivetanning.network.packet.SelectBenchRecipePacket;
import com.otectus.immersivetanning.network.packet.SelectFinishPacket;
import com.otectus.immersivetanning.network.packet.SessionResultPacket;
import com.otectus.immersivetanning.network.packet.StationEffectPacket;
import com.otectus.immersivetanning.network.packet.StationNoticePacket;
import com.otectus.immersivetanning.network.packet.StitchPressPacket;
import com.otectus.immersivetanning.network.packet.StitchResultPacket;
import com.otectus.immersivetanning.network.packet.StitchStartPacket;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Small, purpose-specific packets. Clients report inputs only; the server owns every outcome. */
public final class ModNetwork {
    public static final String VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(ImmersiveTanning.id("main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        int id = 0;
        // Server -> client
        CHANNEL.messageBuilder(OpenFleshingPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenFleshingPacket::encode).decoder(OpenFleshingPacket::decode)
                .consumerMainThread(OpenFleshingPacket::handle).add();
        CHANNEL.messageBuilder(OpenFinishPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenFinishPacket::encode).decoder(OpenFinishPacket::decode)
                .consumerMainThread(OpenFinishPacket::handle).add();
        CHANNEL.messageBuilder(OpenBenchPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenBenchPacket::encode).decoder(OpenBenchPacket::decode)
                .consumerMainThread(OpenBenchPacket::handle).add();
        CHANNEL.messageBuilder(StitchStartPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StitchStartPacket::encode).decoder(StitchStartPacket::decode)
                .consumerMainThread(StitchStartPacket::handle).add();
        CHANNEL.messageBuilder(StitchResultPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StitchResultPacket::encode).decoder(StitchResultPacket::decode)
                .consumerMainThread(StitchResultPacket::handle).add();
        CHANNEL.messageBuilder(SessionResultPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SessionResultPacket::encode).decoder(SessionResultPacket::decode)
                .consumerMainThread(SessionResultPacket::handle).add();
        CHANNEL.messageBuilder(CloseSessionPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CloseSessionPacket::encode).decoder(CloseSessionPacket::decode)
                .consumerMainThread(CloseSessionPacket::handle).add();
        CHANNEL.messageBuilder(StationEffectPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StationEffectPacket::encode).decoder(StationEffectPacket::decode)
                .consumerMainThread(StationEffectPacket::handle).add();
        // Client -> server
        CHANNEL.messageBuilder(FleshStrokePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FleshStrokePacket::encode).decoder(FleshStrokePacket::decode)
                .consumerNetworkThread(FleshStrokePacket::handle).add();
        CHANNEL.messageBuilder(FinishFleshingPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FinishFleshingPacket::encode).decoder(FinishFleshingPacket::decode)
                .consumerNetworkThread(FinishFleshingPacket::handle).add();
        CHANNEL.messageBuilder(SelectFinishPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SelectFinishPacket::encode).decoder(SelectFinishPacket::decode)
                .consumerNetworkThread(SelectFinishPacket::handle).add();
        CHANNEL.messageBuilder(SelectBenchRecipePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SelectBenchRecipePacket::encode).decoder(SelectBenchRecipePacket::decode)
                .consumerNetworkThread(SelectBenchRecipePacket::handle).add();
        CHANNEL.messageBuilder(StitchPressPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(StitchPressPacket::encode).decoder(StitchPressPacket::decode)
                .consumerNetworkThread(StitchPressPacket::handle).add();
        CHANNEL.messageBuilder(FleshNextPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FleshNextPacket::encode).decoder(FleshNextPacket::decode)
                .consumerNetworkThread(FleshNextPacket::handle).add();
        CHANNEL.messageBuilder(CancelSessionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CancelSessionPacket::encode).decoder(CancelSessionPacket::decode)
                .consumerNetworkThread(CancelSessionPacket::handle).add();
        // Server -> client (protocol 2)
        CHANNEL.messageBuilder(StationNoticePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StationNoticePacket::encode).decoder(StationNoticePacket::decode)
                .consumerMainThread(StationNoticePacket::handle).add();
    }

    private ModNetwork() {}
}
