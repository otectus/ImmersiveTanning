package com.otectus.immersivetanning.api;

import com.otectus.immersivetanning.trait.TraitRef;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.event.ItemAttributeModifierEvent;

/**
 * What an innate hide trait does. Every method is evaluated per equipped piece, so each piece contributes a share and
 * a full set simply stacks the shares. Register custom effects with {@link ImmersiveTanningAPI#registerTraitEffect}
 * during mod construction or common setup; a hide trait JSON then names the effect by id.
 */
public interface TraitEffect {

    /** Adds attribute modifiers for one equipped piece carrying the trait. Use ids that are unique per slot. */
    default void addModifiers(ItemAttributeModifierEvent event, TraitRef trait) {}

    /** Multiplier applied to incoming damage for one equipped piece (1 = unchanged). */
    default float damageMultiplier(DamageSource source, TraitRef trait) {
        return 1.0F;
    }

    /** Multiplier applied to the piece's own durability (1 = unchanged). */
    default double durabilityMultiplier(TraitRef trait) {
        return 1.0;
    }

    /**
     * Arguments for the effect's description, the translation key {@code trait_effect.<namespace>.<path>}, so the
     * numbers it states follow the trait's strength.
     */
    default Object[] descriptionArgs(TraitRef trait) {
        return new Object[0];
    }
}
