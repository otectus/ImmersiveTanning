package com.otectus.immersivetanning.hide;

import com.otectus.immersivetanning.trait.TraitRef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A hide travelling through the tannery: its size, the Integrity earned while fleshing ({@link #UNFLESHED} before)
 * and an optional innate trait. Stored in the {@code immersive_tanning} compound of the hide stack and in station
 * block entities.
 */
public record HideData(HideSize size, int integrity, @Nullable TraitRef trait) {
    public static final String ROOT = "immersive_tanning";
    public static final int UNFLESHED = -1;
    /** Integrity is kept in steps of this size so tanned hides and leather stack. */
    public static final int INTEGRITY_STEP = 10;

    public HideData {
        integrity = integrity < 0 ? UNFLESHED : Mth.clamp(integrity, 0, 100);
    }

    public static HideData raw(HideSize size, @Nullable TraitRef trait) {
        return new HideData(size, UNFLESHED, trait);
    }

    public boolean isFleshed() {
        return integrity >= 0;
    }

    public HideData fleshed(int score) {
        return new HideData(size, roundIntegrity(score), trait);
    }

    public static int roundIntegrity(int score) {
        int clamped = Mth.clamp(score, 0, 100);
        return Math.round(clamped / (float) INTEGRITY_STEP) * INTEGRITY_STEP;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Size", size.getSerializedName());
        tag.putInt("Integrity", integrity);
        if (trait != null) trait.save(tag);
        return tag;
    }

    public static HideData load(CompoundTag tag, HideSize fallbackSize) {
        HideSize size = HideSize.byName(tag.getString("Size"));
        int integrity = tag.contains("Integrity", Tag.TAG_ANY_NUMERIC) ? tag.getInt("Integrity") : UNFLESHED;
        return new HideData(size != null ? size : fallbackSize, integrity, TraitRef.load(tag));
    }

    /** Reads the data of a hide stack. Raw hide items imply their size, processed hides store it. */
    public static HideData of(ItemStack stack, HideSize fallbackSize) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(ROOT, Tag.TAG_COMPOUND)) return raw(fallbackSize, null);
        return load(tag.getCompound(ROOT), fallbackSize);
    }

    /** Writes this data onto a stack, replacing any previous hide data. */
    public ItemStack applyTo(ItemStack stack) {
        CompoundTag root = new CompoundTag();
        if (stack.getTag() != null && stack.getTag().contains(ROOT, Tag.TAG_COMPOUND)) root = stack.getTag().getCompound(ROOT);
        root.putString("Size", size.getSerializedName());
        if (isFleshed()) root.putInt("Integrity", integrity);
        else root.remove("Integrity");
        for (String key : new String[]{"Trait", "TraitEffect", "TraitStrength", "TraitColor"}) root.remove(key);
        if (trait != null) trait.save(root);
        stack.getOrCreateTag().put(ROOT, root);
        return stack;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(size);
        buf.writeVarInt(integrity + 1);
        TraitRef.writeNullable(buf, trait);
    }

    public static HideData read(FriendlyByteBuf buf) {
        return new HideData(buf.readEnum(HideSize.class), buf.readVarInt() - 1, TraitRef.readNullable(buf));
    }
}
