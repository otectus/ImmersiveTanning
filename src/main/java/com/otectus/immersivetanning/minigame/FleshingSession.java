package com.otectus.immersivetanning.minigame;

import com.otectus.immersivetanning.hide.HideSize;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** A hide being fleshed on a Tanner's Rack. The server keeps its own surface and replays the reported knife path. */
public final class FleshingSession extends TanningSession {
    public final FleshingSurface surface;
    /** Knife path length (grid cells) the player may still report; refilled with time. */
    float pathBudget = 96F;
    int lastPathMs;
    int lastQX;
    int lastQY;

    public FleshingSession(int id, UUID playerId, ResourceKey<Level> dimension, BlockPos pos, InteractionHand hand, long seed, long now, HideSize size) {
        super(id, playerId, dimension, pos, hand, seed, now);
        this.surface = new FleshingSurface(size, seed);
    }

    @Override
    public Kind kind() {
        return Kind.FLESHING;
    }
}
