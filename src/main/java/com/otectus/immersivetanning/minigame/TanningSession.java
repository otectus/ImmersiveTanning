package com.otectus.immersivetanning.minigame;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Common state of a fleshing or stitching session. The session clock starts at {@code startMs}
 * ({@link net.minecraft.Util#getMillis()} on the server) once the ready countdown ends.
 */
public abstract class TanningSession {
    public enum Kind { FLESHING, STITCHING }

    public final int id;
    public final UUID playerId;
    public final ResourceKey<Level> dimension;
    public final BlockPos pos;
    public final InteractionHand hand;
    public final long seed;
    protected long startMs = -1;
    protected int allowedMs;
    protected int lastValidMs;
    protected int lastSequence = -1;
    protected final long createdMs;

    protected TanningSession(int id, UUID playerId, ResourceKey<Level> dimension, BlockPos pos, InteractionHand hand, long seed, long now) {
        this.id = id;
        this.playerId = playerId;
        this.dimension = dimension;
        this.pos = pos.immutable();
        this.hand = hand;
        this.seed = seed;
        this.createdMs = now;
    }

    public abstract Kind kind();

    public boolean isPlaying() {
        return startMs >= 0;
    }

    public long startMs() {
        return startMs;
    }

    public int allowedMs() {
        return allowedMs;
    }

    public GlobalPos station() {
        return GlobalPos.of(dimension, pos);
    }
}
