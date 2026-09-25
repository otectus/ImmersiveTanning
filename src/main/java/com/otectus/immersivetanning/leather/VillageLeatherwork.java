package com.otectus.immersivetanning.leather;

import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.item.FinishedLeatherItem;
import com.otectus.immersivetanning.item.ProcessedHideItem;
import com.otectus.immersivetanning.item.RawHideItem;
import com.otectus.immersivetanning.trait.TraitRef;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Leatherwork and hides that someone else made or found: village trades, loot and quest rewards. A player's own work
 * is always scored by skill; these stand in for another craftsman's hand, so they carry a believable spread of
 * quality centred on Standard and Fine, with Masterwork rare.
 */
public final class VillageLeatherwork {

    /** One score with mean 66 and deviation 16, kept to 15-98 (Masterwork about 3%, Crude about 1%). */
    public static int score(RandomSource random) {
        return Mth.clamp(Math.round(66F + 16F * (float) random.nextGaussian()), 15, 98);
    }

    public static Finish finish(RandomSource random) {
        Finish[] values = Finish.values();
        return values[random.nextInt(values.length)];
    }

    /**
     * Writes leatherwork data onto {@code stack} (a copy is not made). Undyed leather gear takes the finish colour, as it
     * would from the bench; gear that is already dyed keeps its colour.
     */
    public static ItemStack make(ItemStack stack, Finish finish, int integrity, int workmanship, @Nullable TraitRef trait, @Nullable String crafter) {
        new LeatherworkData(finish, integrity, workmanship, trait, crafter).apply(stack);
        if (stack.getItem() instanceof DyeableLeatherItem dyeable && !dyeable.hasCustomColor(stack)) dyeable.setColor(stack, finish.leatherColor());
        return stack;
    }

    /** Gives a hide, a tanned hide, finished leather or a finished piece an innate trait; other items are unchanged. */
    public static ItemStack withTrait(ItemStack stack, TraitRef trait) {
        if (stack.getItem() instanceof RawHideItem raw) {
            ItemStack out = raw.create(trait);
            out.setCount(stack.getCount());
            return out;
        }
        if (stack.getItem() instanceof ProcessedHideItem processed) {
            HideData data = ProcessedHideItem.data(stack);
            ItemStack out = processed.create(new HideData(data.size(), data.integrity(), trait));
            out.setCount(stack.getCount());
            return out;
        }
        if (stack.getItem() instanceof FinishedLeatherItem) {
            LeatherData data = LeatherData.get(stack).orElseThrow();
            return new LeatherData(data.finish(), data.integrity(), trait).applyTo(stack);
        }
        LeatherworkData.get(stack).ifPresent(d -> new LeatherworkData(d.finish(), d.integrity(), d.workmanship(), trait, d.crafter()).apply(stack));
        return stack;
    }

    private VillageLeatherwork() {}
}
