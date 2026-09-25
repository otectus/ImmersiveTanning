package com.otectus.immersivetanning.leather;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Grade words for 0-100 craftsmanship scores. A player's scores are always results of skill, never random; only work
 * from someone else's hand (village trades, loot, see VillageLeatherwork) carries a spread of quality.
 */
public enum TanningQuality {
    CRUDE("crude", ChatFormatting.GRAY),
    STANDARD("standard", ChatFormatting.WHITE),
    FINE("fine", ChatFormatting.AQUA),
    MASTERWORK("masterwork", ChatFormatting.GOLD);

    private final String name;
    private final ChatFormatting color;

    TanningQuality(String name, ChatFormatting color) {
        this.name = name;
        this.color = color;
    }

    public String id() { return name; }

    public ChatFormatting color() { return color; }

    public MutableComponent displayName() {
        return Component.translatable("quality.immersive_tanning." + name).withStyle(color);
    }

    /** Score bands: 0-34 Crude, 35-69 Standard, 70-89 Fine, 90-100 Masterwork. */
    public static TanningQuality fromScore(int score) {
        if (score < 35) return CRUDE;
        if (score < 70) return STANDARD;
        if (score < 90) return FINE;
        return MASTERWORK;
    }

    public static int overallScore(int integrity, int workmanship) {
        return Math.round((integrity + workmanship) / 2.0F);
    }
}
