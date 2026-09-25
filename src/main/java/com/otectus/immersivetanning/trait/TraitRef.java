package com.otectus.immersivetanning.trait;

import com.otectus.immersivetanning.api.TraitEffect;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * An innate hide trait as carried by a hide, its leather and the finished equipment. The effect and strength travel
 * with the stack, so items keep behaving the same (on server and client) even if a datapack later changes the
 * trait definition.
 */
public record TraitRef(ResourceLocation id, ResourceLocation effect, float strength, int color) {

    public MutableComponent displayName() {
        return displayName(id).withStyle(s -> s.withColor(color));
    }

    public static MutableComponent displayName(ResourceLocation id) {
        return Component.translatableWithFallback("hide_trait." + id.getNamespace() + "." + id.getPath(), fallbackName(id.getPath()));
    }

    /** Effect description shown in detailed tooltips. */
    public Component effectDescription() {
        TraitEffect handler = TraitEffects.get(effect);
        Object[] args = handler == null ? new Object[0] : handler.descriptionArgs(this);
        return Component.translatableWithFallback("trait_effect." + effect.getNamespace() + "." + effect.getPath(), effect.toString(), args)
                .withStyle(ChatFormatting.DARK_GRAY);
    }

    public void save(CompoundTag tag) {
        tag.putString("Trait", id.toString());
        tag.putString("TraitEffect", effect.toString());
        tag.putFloat("TraitStrength", strength);
        tag.putInt("TraitColor", color);
    }

    @Nullable
    public static TraitRef load(CompoundTag tag) {
        if (!tag.contains("Trait", Tag.TAG_STRING)) return null;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Trait"));
        ResourceLocation effect = ResourceLocation.tryParse(tag.getString("TraitEffect"));
        if (id == null || effect == null) return null;
        float strength = tag.contains("TraitStrength") ? tag.getFloat("TraitStrength") : 1.0F;
        return new TraitRef(id, effect, strength, tag.getInt("TraitColor"));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(id);
        buf.writeResourceLocation(effect);
        buf.writeFloat(strength);
        buf.writeInt(color);
    }

    public static TraitRef read(FriendlyByteBuf buf) {
        return new TraitRef(buf.readResourceLocation(), buf.readResourceLocation(), buf.readFloat(), buf.readInt());
    }

    public static void writeNullable(FriendlyByteBuf buf, @Nullable TraitRef trait) {
        buf.writeBoolean(trait != null);
        if (trait != null) trait.write(buf);
    }

    @Nullable
    public static TraitRef readNullable(FriendlyByteBuf buf) {
        return buf.readBoolean() ? read(buf) : null;
    }

    /** Two references describe the same trait when their ids match; strength and colour follow the id. */
    public static boolean same(@Nullable TraitRef a, @Nullable TraitRef b) {
        if (a == null || b == null) return a == b;
        return a.id.equals(b.id);
    }

    private static String fallbackName(String path) {
        StringBuilder out = new StringBuilder();
        for (String word : path.substring(path.lastIndexOf('/') + 1).split("_")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.toString();
    }
}
