package com.otectus.immersivetanning.loot;

import org.jetbrains.annotations.Nullable;

/** Implemented by every {@code LivingEntity} through {@code LivingEntityMixin}; see {@link DeathDrops}. */
public interface DeathDropsHolder {
    @Nullable
    DeathDrops immersive_tanning$deathDrops();
}
