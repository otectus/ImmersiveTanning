package com.otectus.immersivetanning.leather;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/**
 * The one strategic choice of the tannery, made when a wet hide is mounted to dry. Each finish has exactly one
 * headline identity: Supple moves freely and quietly, Harness lasts and repairs well, Hardened protects best.
 */
public enum Finish implements StringRepresentable {
    SUPPLE("supple", 0xD8AE72, 0xE8C38A),
    HARNESS("harness", 0x9C5A30, 0xD08A52),
    HARDENED("hardened", 0x5A3620, 0xB07A50);

    private final String name;
    private final int leatherColor;
    private final int textColor;

    Finish(String name, int leatherColor, int textColor) {
        this.name = name;
        this.leatherColor = leatherColor;
        this.textColor = textColor;
    }

    @Override
    public String getSerializedName() { return name; }

    /** RGB of the finished leather; also used to colour leather armor made from it. */
    public int leatherColor() { return leatherColor; }

    /** RGB used for the finish name in tooltips and screens. */
    public int textColor() { return textColor; }

    public MutableComponent displayName() {
        return Component.translatable("finish.immersive_tanning." + name).withStyle(s -> s.withColor(textColor));
    }

    /** "Light & Quiet", "Rugged & Repairable", "Protective". */
    public MutableComponent tagline() {
        return Component.translatable("finish.immersive_tanning." + name + ".tagline");
    }

    /** One sentence describing what the finish does, for selection screens and detailed tooltips. */
    public MutableComponent description() {
        return Component.translatable("finish.immersive_tanning." + name + ".description");
    }

    @Nullable
    public static Finish byName(String name) {
        for (Finish finish : values()) {
            if (finish.name.equals(name)) return finish;
        }
        return null;
    }

    public static Finish byOrdinal(int ordinal) {
        Finish[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }
}
