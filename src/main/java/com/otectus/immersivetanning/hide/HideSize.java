package com.otectus.immersivetanning.hide;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla hides collapse into three sizes instead of one item per animal. Size decides how much leather a hide
 * yields, how much tannin it needs and how many flesh patches it carries.
 */
public enum HideSize implements StringRepresentable {
    SMALL("small", 1, 1, 3),
    MEDIUM("medium", 2, 1, 4),
    LARGE("large", 3, 2, 5);

    private final String name;
    private final int leather;
    private final int tannin;
    private final int fleshPatches;

    HideSize(String name, int leather, int tannin, int fleshPatches) {
        this.name = name;
        this.leather = leather;
        this.tannin = tannin;
        this.fleshPatches = fleshPatches;
    }

    /** Finished leather a dried hide of this size yields. */
    public int leather() { return leather; }

    /** Tannin charges (bark) the vat spends on a hide of this size. */
    public int tannin() { return tannin; }

    public int fleshPatches() { return fleshPatches; }

    /** The largest tannin cost of any size: the least a vat must be able to hold. */
    public static int maxTannin() {
        int max = 0;
        for (HideSize size : values()) max = Math.max(max, size.tannin);
        return max;
    }

    @Override
    public String getSerializedName() { return name; }

    public Component displayName() {
        return Component.translatable("hide_size.immersive_tanning." + name);
    }

    @Nullable
    public static HideSize byName(String name) {
        for (HideSize size : values()) {
            if (size.name.equals(name)) return size;
        }
        return null;
    }

    public static HideSize byOrdinal(int ordinal) {
        HideSize[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }
}
