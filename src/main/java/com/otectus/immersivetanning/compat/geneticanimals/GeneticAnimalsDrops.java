package com.otectus.immersivetanning.compat.geneticanimals;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.hide.HideItems;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.hide.HideSourceLoader;
import com.otectus.immersivetanning.loot.DeathDrops;
import com.otectus.immersivetanning.loot.HideDrops;
import com.otectus.immersivetanning.recipe.HideMountingRecipe;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModTags;
import com.otectus.immersivetanning.trait.TraitRef;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Genetic Animals death drops. Its animals have no loot tables: their meat, wool and skin come from Java code at death,
 * followed by everything they carry, all in one {@code LivingDropsEvent}. So their hides are decided here, once per
 * death, from the {@link DeathDrops} record of which item came from where; the loot modifier leaves them alone.
 *
 * <p>NATIVE (default) converts the skin the animal really dropped: each leather is one leather's worth of hide, made
 * up from the fewest hides up to the source's size (4 leather: a large and a small hide), and each rabbit hide (or
 * other mountable skin) is one hide of its mounting size. No skin, no hide. TANNING gives each adult its source's hide
 * like a vanilla animal, Looting included. Either way the animal's own skin goes only when {@code replaceLeatherDrops}
 * is on, carried and worn items are never touched, and other drops are left as they are, except that leather outside
 * the animal's cargo becomes Binding Leather when {@code replaceLeatherLoot} is on.</p>
 */
public final class GeneticAnimalsDrops {
    private static final int MAX_WARNINGS = 8;
    private static final AtomicInteger WARNINGS = new AtomicInteger();

    /** One death's result: the hides to add, whether the animal's own skin drops go, and why (for logs and tests). */
    public record Outcome(List<ItemStack> hides, boolean consumeSkin, String reason) {
        static Outcome none(String reason) {
            return new Outcome(List.of(), false, reason);
        }
    }

    /**
     * Hides worth {@code leather} leather in the fewest pieces no larger than {@code cap}, as counts indexed by
     * {@link HideSize#ordinal()}. Sizes yield 1, 2 and 3 leather, so taking the largest first is always fewest.
     */
    public static int[] decompose(int leather, HideSize cap) {
        int[] counts = new int[HideSize.values().length];
        int left = Math.max(0, leather);
        for (int i = cap.ordinal(); i >= 0; i--) {
            HideSize size = HideSize.values()[i];
            counts[i] = left / size.leather();
            left -= counts[i] * size.leather();
        }
        return counts;
    }

    /**
     * Decides one death. {@code leather} is the leather the animal's own loot dropped, {@code mounted} its other skins
     * (rabbit hides) by the hide size they mount as. Reads only the config; the random is used for Looting in TANNING.
     */
    public static Outcome decide(HideSourceLoader.Decision decision, HideMode mode, boolean carcass, boolean lootRan, boolean baby,
                                 int leather, int[] mounted, int looting, RandomSource random) {
        if (decision.kind() == HideSourceLoader.Kind.NO_SOURCE) return Outcome.none("no hide source");
        HideSourceLoader.HideSource source = decision.source();
        if (decision.kind() == HideSourceLoader.Kind.SUPPRESSED) return Outcome.none("explicit exclusion by " + source.id());
        if (!lootRan) return Outcome.none("no loot (doMobLoot off, or the animal drops none)");
        if (carcass) return new Outcome(List.of(), true, "carcass kill: the carcass supplies the skin");
        boolean replace = ServerConfig.get(ServerConfig.REPLACE_LEATHER_DROPS);
        if (mode == HideMode.TANNING) {
            if (baby) return Outcome.none("juvenile");
            return new Outcome(HideDrops.hides(source, looting, random), replace, "species hide");
        }
        int[] counts = decompose(leather, source.size());
        for (int i = 0; i < mounted.length; i++) counts[Math.min(i, source.size().ordinal())] += mounted[i];
        List<ItemStack> hides = stacks(counts, source.traitRef());
        if (hides.isEmpty()) return Outcome.none("native skin absent");
        return new Outcome(hides, replace, "native skin: " + leather + " leather, " + (mounted[0] + mounted[1] + mounted[2]) + " other skins");
    }

    private static List<ItemStack> stacks(int[] counts, TraitRef trait) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = counts.length - 1; i >= 0; i--) {
            ItemStack hide = ModItems.rawHide(HideSize.values()[i]).create(trait);
            for (int left = counts[i]; left > 0; ) {
                int n = Math.min(left, hide.getMaxStackSize());
                out.add(hide.copyWithCount(n));
                left -= n;
            }
        }
        return out;
    }

    /** {@code LivingDropsEvent} for a Genetic Animals entity (from {@link HideDrops#onLivingDrops}). */
    public static void handle(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        Collection<ItemEntity> drops = event.getDrops();
        if (!GeneticAnimals.enabled()) {
            // Integration off: its drops are its own, apart from the global Binding Leather rule every mob follows.
            HideDrops.toBindingLeather(drops, drop -> true);
            debug(victim, "integration disabled", 0, 0, 0);
            return;
        }
        if (GeneticAnimalsHooks.state(GeneticAnimalsHooks.Hook.DEATH_DROPS) != GeneticAnimalsHooks.State.APPLIED) {
            warn(victim.getType(), "cannot tell its skin from its cargo (LivingEntity#dropAllDeathLoot was rewritten by another mod); left untouched");
            return;
        }
        DeathDrops record = DeathDrops.of(victim);
        if (record == null) {
            warn(victim.getType(), "drops arrived without a record of where they came from (posted outside the death itself?); left untouched");
            return;
        }
        if (!record.claim()) return;

        Set<ItemEntity> skin = Collections.newSetFromMap(new IdentityHashMap<>());
        int leather = 0;
        int carried = 0;
        int[] mounted = new int[HideSize.values().length];
        try {
            for (ItemEntity drop : drops) {
                if (record.isCarried(drop)) {
                    carried++;
                    continue;
                }
                ItemStack stack = drop.getItem();
                if (!record.isNatural(drop) || !stack.is(ModTags.REPLACED_BY_HIDES)) continue;
                skin.add(drop);
                Optional<HideMountingRecipe> mount = HideItems.recipe(victim.level(), stack);
                if (mount.isPresent()) mounted[mount.get().size().ordinal()] = Math.addExact(mounted[mount.get().size().ordinal()], stack.getCount());
                else leather = Math.addExact(leather, stack.getCount());
            }
        } catch (ArithmeticException e) {
            warn(victim.getType(), "skin count overflowed; drops left untouched");
            return;
        }

        HideSourceLoader.Decision decision = HideSourceLoader.resolve(victim.getType());
        Outcome outcome = decide(decision, GeneticAnimals.mode(), record.carcass(), record.lootRan(), victim.isBaby(), leather, mounted,
                event.getLootingLevel(), victim.getRandom());
        if (outcome.consumeSkin()) drops.removeIf(skin::contains);
        for (ItemStack hide : outcome.hides()) {
            ItemEntity drop = new ItemEntity(victim.level(), victim.getX(), victim.getY() + 0.5, victim.getZ(), hide);
            drop.setDefaultPickUpDelay();
            drops.add(drop);
        }
        HideDrops.toBindingLeather(drops, drop -> !record.isCarried(drop));
        debug(victim, outcome.reason() + " -> " + count(outcome.hides()) + " hides" + (outcome.consumeSkin() ? ", skin replaced" : ""),
                leather, mounted[0] + mounted[1] + mounted[2], carried);
    }

    private static int count(List<ItemStack> stacks) {
        int n = 0;
        for (ItemStack s : stacks) n += s.getCount();
        return n;
    }

    /** One line per death at debug level: what the animal dropped of its own, what it carried, and the result. */
    private static void debug(LivingEntity victim, String result, int leather, int otherSkins, int carried) {
        if (!ImmersiveTanning.LOGGER.isDebugEnabled()) return;
        ImmersiveTanning.LOGGER.debug("Genetic Animals death {} ({}, mode {}): natural leather {}, other skins {}, carried items kept {}: {}",
                EntityType.getKey(victim.getType()), victim.isBaby() ? "juvenile" : "adult", GeneticAnimals.mode(), leather, otherSkins, carried, result);
    }

    /** At most a few warnings per session, so a misbehaving combination cannot flood the log. */
    private static void warn(EntityType<?> type, String message) {
        int n = WARNINGS.incrementAndGet();
        if (n <= MAX_WARNINGS) {
            ImmersiveTanning.LOGGER.warn("Genetic Animals integration: {} {}{}", EntityType.getKey(type), message,
                    n == MAX_WARNINGS ? " (further warnings of this kind are not logged)" : "");
        }
    }

    private GeneticAnimalsDrops() {}
}
