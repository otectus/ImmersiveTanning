package com.otectus.immersivetanning.util;

import com.otectus.immersivetanning.network.ModNetwork;
import com.otectus.immersivetanning.network.packet.StationNoticePacket;
import com.otectus.immersivetanning.registry.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * Station messages. A real player receives them as notices that stay readable above the hotbar for the client's
 * configured time (several at once, a repeat refreshing rather than stacking), so a second message no longer wipes the
 * first before it is read. Anything else (fake players) gets a plain action-bar message.
 */
public final class Feedback {

    public static void info(Player player, String key, Object... args) {
        info(player, message(key, args));
    }

    public static void info(Player player, Component message) {
        send(player, message, false);
    }

    /** A problem the player should act on, highlighted like a failure but without the failure sound. */
    public static void warn(Player player, Component message) {
        send(player, message, true);
    }

    public static void fail(Player player, String key, Object... args) {
        fail(player, message(key, args));
    }

    public static void fail(Player player, Component message) {
        send(player, message, true);
        player.playNotifySound(ModSounds.ACTION_FAIL.get(), SoundSource.BLOCKS, 0.5F, 1.0F);
    }

    public static Component message(String key, Object... args) {
        return Component.translatable("message.immersive_tanning." + key, args);
    }

    private static void send(Player player, Component message, boolean failure) {
        if (player instanceof ServerPlayer sp && !(sp instanceof FakePlayer) && sp.connection != null) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new StationNoticePacket(message, failure));
        } else {
            player.displayClientMessage(message, true);
        }
    }

    private Feedback() {}
}
