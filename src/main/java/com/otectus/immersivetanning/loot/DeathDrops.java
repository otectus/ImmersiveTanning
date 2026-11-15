package com.otectus.immersivetanning.loot;

import com.otectus.immersivetanning.compat.geneticanimals.GeneticAnimals;
import com.otectus.immersivetanning.registry.ModTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Where each item of one death came from, for animals whose skin and whose inventory arrive in the same drops list
 * (Genetic Animals: its Java death loot, then everything the animal carried). {@code LivingEntityMixin} records it
 * around the calls in {@code LivingEntity#dropAllDeathLoot}, and {@code MobMixin} marks worn equipment, so the
 * {@code LivingDropsEvent} handler can tell the animal's own loot from its cargo by identity, never by item type.
 *
 * <p>It lives on the dying entity for the length of that one call (cleared on return; a throwing call leaves it on an
 * entity that is being removed anyway), is never saved, and holds only the item entities of that death.</p>
 */
public final class DeathDrops {
    private final Set<ItemEntity> natural = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<ItemEntity> carried = Collections.newSetFromMap(new IdentityHashMap<>());
    private final boolean carcass;
    private int naturalStart = -1;
    private int wornStart = -1;
    private int cargoStart = -1;
    private boolean lootRan;
    private boolean handled;

    private DeathDrops(boolean carcass) {
        this.carcass = carcass;
    }

    /** A record for this death, or null for entities whose drops need none (everything but Genetic Animals). */
    @Nullable
    public static DeathDrops begin(LivingEntity entity) {
        if (entity.level().isClientSide || !GeneticAnimals.owns(entity.getType())) return null;
        return new DeathDrops(carcassSpawned(entity));
    }

    /** The record of the death the entity is dropping its loot for, if any. */
    @Nullable
    public static DeathDrops of(LivingEntity entity) {
        return entity instanceof DeathDropsHolder holder ? holder.immersive_tanning$deathDrops() : null;
    }

    /**
     * Whether another mod turned this death into a carcass: a {@code #immersive_tanning:carcasses} item that appeared
     * this tick exactly where the animal stands (Butchery spawns it from {@code LivingDeathEvent}, just before the loot).
     * Seen, not predicted, so a cleaver that no carcass mod answers never costs the animal its skin.
     */
    static boolean carcassSpawned(LivingEntity entity) {
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        AABB here = new AABB(x - 0.01, y - 0.01, z - 0.01, x + 0.01, y + 0.01, z + 0.01);
        return !entity.level().getEntitiesOfClass(ItemEntity.class, here, item -> item.tickCount == 0
                && item.position().distanceToSqr(x, y, z) < 1.0E-6 && item.getItem().is(ModTags.CARCASSES)).isEmpty();
    }

    // Windows over the drops being captured, opened before and closed after each call in dropAllDeathLoot.

    public void openNatural(@Nullable Collection<ItemEntity> capture) {
        lootRan = true;
        naturalStart = size(capture);
    }

    public void closeNatural(@Nullable Collection<ItemEntity> capture) {
        for (ItemEntity drop : since(capture, naturalStart)) {
            if (!carried.contains(drop)) natural.add(drop);
        }
        naturalStart = -1;
    }

    /** Equipment worn in vanilla slots, which {@code Mob#dropCustomDeathLoot} drops inside the death-loot call. */
    public void openWorn(@Nullable Collection<ItemEntity> capture) {
        wornStart = size(capture);
    }

    public void closeWorn(@Nullable Collection<ItemEntity> capture) {
        carried.addAll(since(capture, wornStart));
        wornStart = -1;
    }

    /** The animal's inventory: chest contents, saddle, bridle, collar, blanket. */
    public void openCargo(@Nullable Collection<ItemEntity> capture) {
        cargoStart = size(capture);
    }

    public void closeCargo(@Nullable Collection<ItemEntity> capture) {
        carried.addAll(since(capture, cargoStart));
        cargoStart = -1;
    }

    /** Dropped by the animal's own death loot (not worn, not carried). */
    public boolean isNatural(ItemEntity drop) {
        return natural.contains(drop) && !carried.contains(drop);
    }

    /** Carried or worn by the animal: kept exactly as it was. */
    public boolean isCarried(ItemEntity drop) {
        return carried.contains(drop);
    }

    /** Whether the loot calls ran at all ({@code doMobLoot} on and the mob dropping loot). */
    public boolean lootRan() {
        return lootRan;
    }

    public boolean carcass() {
        return carcass;
    }

    /** True the first time only, so one death is turned into hides once however often its drops are handled. */
    public boolean claim() {
        if (handled) return false;
        handled = true;
        return true;
    }

    private static int size(@Nullable Collection<ItemEntity> capture) {
        return capture == null ? 0 : capture.size();
    }

    private static List<ItemEntity> since(@Nullable Collection<ItemEntity> capture, int start) {
        if (capture == null || start < 0 || start >= capture.size()) return List.of();
        List<ItemEntity> out = new ArrayList<>(capture.size() - start);
        int i = 0;
        for (ItemEntity drop : capture) {
            if (i++ >= start) out.add(drop);
        }
        return out;
    }
}
