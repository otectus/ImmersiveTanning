package com.otectus.immersivetanning.minigame;

import com.otectus.immersivetanning.config.ServerConfig;

/**
 * Server-side validation of client-reported input times. The client reports milliseconds since its own session
 * start. Because that start was shifted by one-way latency and every input arrives one-way late, an honest report
 * sits roughly one round trip below the server's measurement. Reports are clamped into
 * [measured - min(ping, cap) - tolerance, measured + tolerance], and never move backwards.
 */
public final class SessionTiming {

    public static int validate(int clientMs, long serverElapsedMs, int latencyMs, int lastValidMs) {
        int compensation = Math.min(Math.max(latencyMs, 0), ServerConfig.get(ServerConfig.MAX_LATENCY_COMPENSATION_MS));
        int tolerance = ServerConfig.get(ServerConfig.TIMING_TOLERANCE_MS);
        long low = serverElapsedMs - compensation - tolerance;
        long high = serverElapsedMs + tolerance;
        long value = Math.max(low, Math.min(high, clientMs));
        value = Math.max(value, lastValidMs);
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
    }

    /** The latest server-measured time at which an honest input for the deadline could still arrive. */
    public static long graceMs(int latencyMs) {
        int compensation = Math.min(Math.max(latencyMs, 0), ServerConfig.get(ServerConfig.MAX_LATENCY_COMPENSATION_MS));
        return compensation + ServerConfig.get(ServerConfig.TIMING_TOLERANCE_MS);
    }

    private SessionTiming() {}
}
