package com.otectus.immersivetanning.util;

import com.otectus.immersivetanning.config.ClientConfig;
import com.otectus.immersivetanning.leather.TanningQuality;
import net.minecraft.network.chat.Component;

/** Formats a 0-100 score for players: always the grade word, plus the number when the client wants numbers. */
public final class Scores {

    public static Component label(int score) {
        Component grade = TanningQuality.fromScore(score).displayName();
        if (!ClientConfig.get(ClientConfig.SHOW_NUMERIC_SCORES)) return grade;
        return Component.translatable("tooltip.immersive_tanning.grade_with_score", grade, score);
    }

    private Scores() {}
}
