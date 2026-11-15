package com.otectus.immersivetanning.client.compat.jei;

import com.otectus.immersivetanning.client.station.BenchBehavior;
import com.otectus.immersivetanning.client.station.StationFocus;
import com.otectus.immersivetanning.work.WorkAction;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI's answer to recipe transfer at a bench: while the player is working a Leatherworker's Bench (JEI opened from
 * its pattern book suspends focus), a recipe can be prepared there. It is the same request as the pattern book's
 * "Prepare": the server picks the leather group (the bench's last choice when it suffices) and checks everything.
 */
public final class BenchTransfer {

    public static boolean available() {
        StationFocus.Session session = StationFocus.session();
        return session != null && session.operator && session.behavior() instanceof BenchBehavior bench && bench.job() == null;
    }

    public static boolean prepare(ResourceLocation recipe) {
        if (!available()) return false;
        StationFocus.send(WorkAction.BENCH_PREPARE, 0, new int[]{-1}, new short[0], new short[0], recipe, null);
        Minecraft.getInstance().setScreen(null);
        return true;
    }

    private BenchTransfer() {}
}
