package com.otectus.immersivetanning.work;

import java.util.Random;
import java.util.UUID;

/**
 * A player's temporary permission to work (or watch) at a station. Server only and never saved. Commands carry the
 * lease id and a sequence number; the server ignores anything older, anything for another lease, and anything beyond
 * a modest rate (the release of a gesture is never dropped).
 */
public final class StationLease {
    public enum Role { OPERATOR, OBSERVER }

    private static final Random IDS = new Random();
    /** Commands per second a lease may send on average, and the burst it may bank. */
    public static final float RATE = 24F;
    public static final float BURST = 36F;

    public final UUID player;
    public final int id;
    public final Role role;
    private boolean assisted;
    private long lastActiveTick;
    private int lastSequence = -1;
    private float tokens = BURST;
    private long lastRefillMs;
    /** Work-plane / surface path the lease may still move a tool by; refilled with time (teleports are refused). */
    private float budget = 3F;
    private long lastBudgetMs;
    /** Server time a gesture began, or 0 (a gesture left open is ended by the watchdog). */
    public long gestureSince;
    /** Path of the current paddle sweep (vat), work-surface units. */
    public float sweep;
    /** Last relayed pose (ms), to keep watchers' updates modest. */
    public long lastPoseMs;
    /** Last point of a tool path, for budgets (surface units). */
    public float lastX = Float.NaN;
    public float lastY = Float.NaN;

    public StationLease(UUID player, Role role, boolean assisted, long gameTime) {
        this.player = player;
        this.id = IDS.nextInt(Integer.MAX_VALUE - 1) + 1;
        this.role = role;
        this.assisted = assisted;
        this.lastActiveTick = gameTime;
    }

    /** Whether the player asked for (and the server allows) the assisted interaction profile. */
    public boolean assisted() {
        return assisted;
    }

    public void setAssisted(boolean assisted) {
        this.assisted = assisted;
    }

    /** Tolerance scale of the lease's interaction profile. */
    public float toleranceScale() {
        return assisted ? 1.5F : 1F;
    }

    public boolean operating() {
        return role == Role.OPERATOR;
    }

    public void touch(long gameTime) {
        lastActiveTick = gameTime;
    }

    public long lastActiveTick() {
        return lastActiveTick;
    }

    /** Accepts a strictly newer sequence number. */
    public boolean sequence(int sequence) {
        if (sequence <= lastSequence) return false;
        lastSequence = sequence;
        return true;
    }

    public int lastSequence() {
        return lastSequence;
    }

    private boolean unthrottled;

    /**
     * Test hook: a scripted operator (GameTests drive whole jobs within one tick) is not limited by the rate or the
     * tool-path budgets, which measure real time. Everything else is checked as for a player.
     */
    public void unthrottleForTests() {
        unthrottled = true;
    }

    public boolean unthrottled() {
        return unthrottled;
    }

    /** Takes one command from the rate budget. */
    public boolean rate(long nowMs) {
        if (unthrottled) return true;
        if (lastRefillMs > 0) tokens = Math.min(BURST, tokens + (nowMs - lastRefillMs) * RATE / 1000F);
        lastRefillMs = nowMs;
        if (tokens < 1F) return false;
        tokens -= 1F;
        return true;
    }

    /** Refills and returns the tool-path budget (units per second {@code perSecond}, at most {@code max}). */
    public float budget(long nowMs, float perSecond, float max) {
        if (unthrottled) return max;
        if (lastBudgetMs > 0) budget = Math.min(max, budget + (nowMs - lastBudgetMs) * perSecond / 1000F);
        lastBudgetMs = nowMs;
        return budget;
    }

    public void spend(float amount) {
        budget = Math.max(0F, budget - amount);
    }

    public void gesture(long nowMs, boolean open) {
        gestureSince = open ? nowMs : 0L;
    }

    public long gestureSince() {
        return gestureSince;
    }
}
