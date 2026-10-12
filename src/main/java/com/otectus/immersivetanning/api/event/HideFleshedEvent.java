package com.otectus.immersivetanning.api.event;

import com.otectus.immersivetanning.hide.HideData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Posted on the Forge bus when a player finishes fleshing a raw hide on a Tanner's Rack, before the scraped hide is
 * created. {@link #getScore()} is the fleshing score (0..100) the hide's Integrity will be rounded from; listeners
 * may raise or lower it with {@link #setScore(int)}. Server side only, not cancellable. Progression mods reward or
 * improve fleshing here.
 */
public class HideFleshedEvent extends PlayerEvent {
    private final HideData hide;
    private final BlockPos rack;
    private final boolean timedOut;
    private int score;

    public HideFleshedEvent(ServerPlayer player, HideData hide, int score, BlockPos rack, boolean timedOut) {
        super(player);
        this.hide = hide;
        this.rack = rack;
        this.timedOut = timedOut;
        this.score = Mth.clamp(score, 0, 100);
    }

    @Override
    public ServerPlayer getEntity() {
        return (ServerPlayer) super.getEntity();
    }

    /** The raw hide as it was mounted: size and innate trait. */
    public HideData getHide() { return hide; }
    public BlockPos getRack() { return rack; }
    /** Whether the fleshing time ran out (the hide finishes at whatever the score was). */
    public boolean isTimedOut() { return timedOut; }
    /** The fleshing score, 0..100, that Integrity is rounded from. */
    public int getScore() { return score; }
    /** Replaces the score; clamped to 0..100. */
    public void setScore(int value) { this.score = Mth.clamp(value, 0, 100); }
}
