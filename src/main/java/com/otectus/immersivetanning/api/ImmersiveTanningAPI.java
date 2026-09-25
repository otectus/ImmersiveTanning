package com.otectus.immersivetanning.api;

import com.otectus.immersivetanning.leather.LeatherworkData;
import com.otectus.immersivetanning.trait.TraitEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Public entry points for other mods. Everything else in Immersive Tanning is implementation detail.
 * <ul>
 *     <li>Hides, traits, seams and bench recipes are data: see the README for the datapack formats.</li>
 *     <li>Custom trait behaviour is code: register a {@link TraitEffect} and name it in a hide trait JSON.</li>
 * </ul>
 */
public final class ImmersiveTanningAPI {

    /** Registers a trait effect under {@code id}. Call during mod construction or common setup. */
    public static void registerTraitEffect(ResourceLocation id, TraitEffect effect) {
        TraitEffects.register(id, effect);
    }

    /** The finish, Integrity, Workmanship and trait of a piece made at the Leatherworker's Bench, if any. */
    public static Optional<LeatherworkData> leatherwork(ItemStack stack) {
        return LeatherworkData.get(stack);
    }

    private ImmersiveTanningAPI() {}
}
