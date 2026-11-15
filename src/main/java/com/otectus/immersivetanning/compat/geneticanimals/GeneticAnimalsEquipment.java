package com.otectus.immersivetanning.compat.geneticanimals;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Tack put on a Genetic Animals animal by hand. That mod builds a new stack of the same item and copies only its colour
 * (bridles and its own saddles) or nothing at all (the vanilla saddle), so a bench-made piece would lose its finish,
 * quality, trait and maker, and any name or enchantment. Its hooks ({@code mixin/compat/eanimod}) hand the stack it
 * built to {@link #keep}, which puts a one-item copy of the stack the player used in its place. Genetic Animals still
 * takes the item from the hand and gives back the piece it replaces; through its inventory screen items move whole.
 */
public final class GeneticAnimalsEquipment {

    /** A copy of {@code used} for the animal when {@code built} is the same item, otherwise {@code built} unchanged. */
    public static ItemStack keep(@Nullable ItemStack used, ItemStack built) {
        if (used == null || used.isEmpty() || built.isEmpty() || used.getItem() != built.getItem() || !GeneticAnimals.enabled()) return built;
        return used.copyWithCount(1);
    }

    private GeneticAnimalsEquipment() {}
}
