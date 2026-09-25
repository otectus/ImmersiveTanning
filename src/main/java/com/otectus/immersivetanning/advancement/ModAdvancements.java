package com.otectus.immersivetanning.advancement;

import com.otectus.immersivetanning.ImmersiveTanning;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tannery milestones use advancements with a single {@code minecraft:impossible} criterion, awarded from code. The
 * root is earned by picking up a hide. Core functionality is never locked behind them.
 */
public final class ModAdvancements {
    public static final String BARK = "bark_and_bite";
    public static final String FLESHED = "scraping_by";
    public static final String TANNED = "steeped";
    public static final String FINISHED = "a_fine_finish";
    public static final String LEATHERWORK = "stitch_in_time";
    public static final String MASTERWORK = "master_tanner";
    public static final String PATCHED_UP = "patched_up";
    public static final String TROPHY = "trophy_hide";

    public static void award(ServerPlayer player, String name) {
        if (player.getServer() == null) return;
        Advancement advancement = player.getServer().getAdvancements().getAdvancement(ImmersiveTanning.id(name));
        if (advancement == null) return;
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        if (progress.isDone()) return;
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    private ModAdvancements() {}
}
