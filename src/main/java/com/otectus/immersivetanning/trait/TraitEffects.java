package com.otectus.immersivetanning.trait;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.api.TraitEffect;
import com.otectus.immersivetanning.registry.ModTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The built-in trait effects and the registry that custom effects join. */
public final class TraitEffects {
    public static final ResourceLocation HEATPROOF = ImmersiveTanning.id("heatproof");
    public static final ResourceLocation INSULATING = ImmersiveTanning.id("insulating");
    public static final ResourceLocation LIGHTWEIGHT = ImmersiveTanning.id("lightweight");
    public static final ResourceLocation TOUGH = ImmersiveTanning.id("tough");
    public static final ResourceLocation WATER_RESISTANT = ImmersiveTanning.id("water_resistant");

    private static final Map<ResourceLocation, TraitEffect> EFFECTS = new ConcurrentHashMap<>();

    static {
        EFFECTS.put(HEATPROOF, new TraitEffect() {
            @Override
            public float damageMultiplier(DamageSource source, TraitRef trait) {
                return source.is(ModTags.RESISTED_BY_HEATPROOF) ? reduce(0.125F, trait) : 1.0F;
            }

            @Override
            public Object[] descriptionArgs(TraitRef trait) {
                return new Object[]{percent(0.125 * trait.strength())};
            }
        });
        EFFECTS.put(INSULATING, new TraitEffect() {
            @Override
            public float damageMultiplier(DamageSource source, TraitRef trait) {
                return source.is(ModTags.RESISTED_BY_INSULATING) ? reduce(0.25F, trait) : 1.0F;
            }

            @Override
            public Object[] descriptionArgs(TraitRef trait) {
                return new Object[]{percent(0.25 * trait.strength())};
            }
        });
        EFFECTS.put(LIGHTWEIGHT, new TraitEffect() {
            @Override
            public void addModifiers(ItemAttributeModifierEvent event, TraitRef trait) {
                add(event, Attributes.MOVEMENT_SPEED, "lightweight", 0.015 * trait.strength());
            }

            @Override
            public Object[] descriptionArgs(TraitRef trait) {
                return new Object[]{percent(0.015 * trait.strength())};
            }
        });
        EFFECTS.put(TOUGH, new TraitEffect() {
            @Override
            public double durabilityMultiplier(TraitRef trait) {
                return 1.0 + 0.25 * trait.strength();
            }

            @Override
            public Object[] descriptionArgs(TraitRef trait) {
                return new Object[]{percent(0.25 * trait.strength())};
            }
        });
        EFFECTS.put(WATER_RESISTANT, new TraitEffect() {
            @Override
            public void addModifiers(ItemAttributeModifierEvent event, TraitRef trait) {
                add(event, ForgeMod.SWIM_SPEED.get(), "water_resistant", 0.05 * trait.strength());
            }

            @Override
            public float damageMultiplier(DamageSource source, TraitRef trait) {
                return source.is(ModTags.RESISTED_BY_WATER_RESISTANT) ? reduce(0.15F, trait) : 1.0F;
            }

            @Override
            public Object[] descriptionArgs(TraitRef trait) {
                return new Object[]{percent(0.05 * trait.strength()), percent(0.15 * trait.strength())};
            }
        });
    }

    public static void register(ResourceLocation id, TraitEffect effect) {
        if (EFFECTS.putIfAbsent(id, effect) != null) {
            throw new IllegalStateException("Trait effect " + id + " is already registered");
        }
    }

    public static boolean isKnown(ResourceLocation id) {
        return EFFECTS.containsKey(id);
    }

    @Nullable
    public static TraitEffect get(ResourceLocation id) {
        return EFFECTS.get(id);
    }

    /** A share as a percentage for tooltips: 0.125 reads "12.5", 0.25 reads "25". */
    public static String percent(double share) {
        double value = Math.round(share * 1000.0) / 10.0;
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static float reduce(float perPiece, TraitRef trait) {
        return Math.max(0F, 1F - perPiece * trait.strength());
    }

    /** Adds a MULTIPLY_BASE modifier whose id is stable per effect and slot, so pieces in different slots stack. */
    private static void add(ItemAttributeModifierEvent event, Attribute attribute, String name, double amount) {
        EquipmentSlot slot = event.getSlotType();
        UUID id = UUID.nameUUIDFromBytes(("immersive_tanning:trait:" + name + ":" + slot.getName()).getBytes(StandardCharsets.UTF_8));
        event.addModifier(attribute, new AttributeModifier(id, "Hide trait", amount, AttributeModifier.Operation.MULTIPLY_BASE));
    }

    private TraitEffects() {}
}
