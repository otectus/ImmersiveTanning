package com.otectus.immersivetanning.api;

import net.minecraft.server.level.ServerPlayer;

/**
 * Per-player time allowance for fleshing a hide. Answers multiply; the product is clamped to
 * {@code 1.0..minigames.maxAssistTimeMultiplier} (server config), so assistance can only lengthen the session and never
 * past the operator's ceiling. The client receives the final allowance in the fleshing start packet. Stitching is
 * paced by its seam pattern and is not assisted. Server thread only; a source that throws counts as {@code 1.0}.
 */
@FunctionalInterface
public interface ITanningAssistSource {
    double fleshingTimeMultiplier(ServerPlayer player);
}
