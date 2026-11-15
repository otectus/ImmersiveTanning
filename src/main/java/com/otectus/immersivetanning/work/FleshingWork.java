package com.otectus.immersivetanning.work;

import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.minigame.FleshingSurface;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The fleshing of one raw hide on a rack: its surface (seed, every cell, the quality so far), whose work it is, and
 * the knife it is done with. It is saved with the rack, and with the hide when a partly scraped hide is taken off, so
 * leaving, reloading or remounting resumes the same hide with the same history.
 *
 * <p>Transient fields (the stroke budget, the last point) belong to the operator holding the rack and are not saved:
 * a stroke never survives a save, the blade is simply lifted.</p>
 */
public final class FleshingWork {
    public static final int VERSION = 1;
    /** Blade path (grid cells) the server accepts per millisecond: only teleports are refused. */
    public static final float CELLS_PER_MS = 2.0F;
    public static final float MAX_BUDGET = 256F;

    private FleshingSurface surface;
    @Nullable
    private UUID owner;
    private String ownerName = "";
    private int strokes;
    private int revision;
    private long challengeMs;
    private int knifeSlot = -1;
    private int appliedSequence;

    // transient
    float budget = 96F;
    long lastPacketMs;
    int lastQX;
    int lastQY;

    private FleshingWork(FleshingSurface surface) {
        this.surface = surface;
    }

    public static FleshingWork start(HideSize size, long seed) {
        return new FleshingWork(new FleshingSurface(size, seed));
    }

    public FleshingSurface surface() { return surface; }
    @Nullable public UUID owner() { return owner; }
    public String ownerName() { return ownerName; }
    public int strokes() { return strokes; }
    public int revision() { return revision; }
    public long challengeMs() { return challengeMs; }
    public int knifeSlot() { return knifeSlot; }
    public int appliedSequence() { return appliedSequence; }
    public boolean started() { return surface.hasWork(); }

    public boolean ownedBy(UUID player) {
        return owner == null || owner.equals(player);
    }

    public void claim(UUID player, String name) {
        if (owner == null) {
            owner = player;
            ownerName = name;
        }
    }

    /** A deliberate handoff: the next player to scrape the hide takes it over. */
    public void release() {
        owner = null;
        ownerName = "";
        revision++;
    }

    public void reserveKnife(int slot) {
        if (knifeSlot < 0) knifeSlot = slot;
    }

    public void countStroke() {
        strokes++;
    }

    public void accepted(int sequence) {
        appliedSequence = sequence;
        revision++;
    }

    public void addChallengeMs(long ms) {
        challengeMs += ms;
    }

    /** Refills the stroke budget for the time since the operator's last batch and returns it. */
    public float refillBudget(long nowMs) {
        if (lastPacketMs > 0) budget = Math.min(MAX_BUDGET, budget + (nowMs - lastPacketMs) * CELLS_PER_MS);
        lastPacketMs = nowMs;
        return budget;
    }

    /** What is left of the budget after a batch. */
    public void spendBudget(float left) {
        budget = Math.max(0F, left);
    }

    public int lastQX() {
        return lastQX;
    }

    public int lastQY() {
        return lastQY;
    }

    public void lastPoint(int qx, int qy) {
        lastQX = qx;
        lastQY = qy;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", VERSION);
        tag.put("Surface", surface.save());
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
        tag.putInt("Strokes", strokes);
        tag.putInt("Revision", revision);
        tag.putLong("ChallengeMs", challengeMs);
        tag.putInt("KnifeSlot", knifeSlot);
        tag.putInt("Applied", appliedSequence);
        return tag;
    }

    @Nullable
    public static FleshingWork load(@Nullable CompoundTag tag, HideSize size) {
        if (tag == null || !tag.contains("Surface", Tag.TAG_COMPOUND)) return null;
        FleshingSurface surface = FleshingSurface.load(tag.getCompound("Surface"), size);
        if (surface.size() != size) surface = new FleshingSurface(size, surface.seed());
        FleshingWork work = new FleshingWork(surface);
        work.owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        work.ownerName = tag.getString("OwnerName");
        work.strokes = tag.getInt("Strokes");
        work.revision = tag.getInt("Revision");
        work.challengeMs = Math.max(0L, tag.getLong("ChallengeMs"));
        work.knifeSlot = tag.contains("KnifeSlot") ? tag.getInt("KnifeSlot") : -1;
        work.appliedSequence = tag.getInt("Applied");
        return work;
    }

    /** Replaces the surface with a newer authoritative copy (client side). */
    public void replaceSurface(FleshingSurface newer) {
        this.surface = newer;
    }
}
