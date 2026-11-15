package com.otectus.immersivetanning.client.station;

import com.otectus.immersivetanning.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Map;

/**
 * Continuous contact sounds (the blade scraping, the paddle in the liquor), one positional loop per station that
 * follows the work: louder and brighter while flesh comes away, quieter and lower on clean leather, faded out on
 * release, on leaving, when the station unloads and on disconnect. Never a new sound per tick.
 */
public final class WorkSounds {
    private static final Map<BlockPos, Loop> LOOPS = new HashMap<>();

    private static final class Loop extends AbstractTickableSoundInstance {
        private float target;
        private float pitchTarget = 1F;
        private int quiet;

        Loop(BlockPos pos, SoundEvent event) {
            super(event, SoundSource.BLOCKS, RandomSource.create(pos.asLong()));
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.x = pos.getX() + 0.5;
            this.y = pos.getY() + 0.4;
            this.z = pos.getZ() + 0.5;
        }

        void feed(boolean removing) {
            target = removing ? 0.55F : 0.22F;
            pitchTarget = removing ? 1.0F : 0.82F;
            quiet = 0;
        }

        void fade() {
            target = 0F;
        }

        @Override
        public void tick() {
            if (++quiet > 6) target = 0F;
            volume += (target - volume) * 0.4F;
            pitch += (pitchTarget - pitch) * 0.3F;
            if (target <= 0F && volume < 0.02F) stop();
        }

        boolean done() {
            return isStopped();
        }
    }

    private static void feed(BlockPos pos, SoundEvent event, boolean removing) {
        Loop loop = LOOPS.get(pos);
        if (loop == null || loop.done()) {
            loop = new Loop(pos, event);
            LOOPS.put(pos.immutable(), loop);
            Minecraft.getInstance().getSoundManager().play(loop);
        }
        loop.feed(removing);
    }

    /** The local operator's blade is moving on the hide ({@code removing}: flesh comes away). */
    public static void localScrape(BlockPos pos, boolean removing) {
        feed(pos, ModSounds.RACK_SCRAPE.get(), removing);
    }

    public static void remoteScrape(BlockPos pos, boolean removing, boolean ended) {
        if (ended) {
            fade(pos);
            return;
        }
        feed(pos, ModSounds.RACK_SCRAPE.get(), removing);
    }

    public static void sweep(BlockPos pos) {
        feed(pos, ModSounds.VAT_SWEEP.get(), true);
    }

    public static void fade(BlockPos pos) {
        Loop loop = LOOPS.get(pos);
        if (loop != null) loop.fade();
    }

    public static void stopAll() {
        for (Loop loop : LOOPS.values()) {
            loop.fade();
            Minecraft.getInstance().getSoundManager().stop(loop);
        }
        LOOPS.clear();
    }

    /** Drops finished loops (called with the client tick). */
    public static void tick() {
        LOOPS.values().removeIf(Loop::done);
    }

    private WorkSounds() {}
}
