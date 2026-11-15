package com.otectus.immersivetanning.leather;

import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Binding Leather takes vanilla leather's place everywhere it is used or produced:
 * <ul>
 *     <li>recipe ingredients - every {@code Ingredient} that lists vanilla leather lists Binding Leather instead
 *     ({@code IngredientMixin}), so books, item frames, anvil repair of leather armor and modded recipes all take it;</li>
 *     <li>recipe results - crafting, cooking and stonecutting recipes that make vanilla leather make Binding Leather
 *     ({@code TanningData.rebuild});</li>
 *     <li>loot and villager trades that would hand out or ask for vanilla leather.</li>
 * </ul>
 * Vanilla leather a player still holds converts one to one in a crafting grid.
 */
public final class BindingLeather {

    /** Whether registration has happened, so early ingredient lookups never touch an empty registry object. */
    public static boolean available() {
        return ModItems.BINDING_LEATHER.isPresent();
    }

    /**
     * An ingredient's resolved stacks with vanilla leather swapped for Binding Leather (same count), or null when the
     * ingredient does not list vanilla leather. Duplicates created by the swap are dropped.
     */
    @Nullable
    public static ItemStack[] swap(ItemStack[] stacks) {
        boolean found = false;
        for (ItemStack stack : stacks) {
            if (stack.is(Items.LEATHER)) {
                found = true;
                break;
            }
        }
        if (!found || !available()) return null;
        List<ItemStack> out = new ArrayList<>(stacks.length);
        boolean binding = false;
        for (ItemStack stack : stacks) {
            ItemStack next = stack.is(Items.LEATHER) ? new ItemStack(ModItems.BINDING_LEATHER.get(), stack.getCount()) : stack;
            if (next.is(ModItems.BINDING_LEATHER.get())) {
                if (binding) continue;
                binding = true;
            }
            out.add(next);
        }
        return out.toArray(ItemStack[]::new);
    }

    /** The stack itself, or Binding Leather in its place if it is vanilla leather. */
    public static ItemStack replace(ItemStack stack) {
        if (!stack.is(Items.LEATHER) || !available()) return stack;
        ItemStack out = new ItemStack(ModItems.BINDING_LEATHER.get(), stack.getCount());
        if (stack.getTag() != null) out.setTag(stack.getTag().copy());
        return out;
    }

    /** A villager or wandering-trader listing whose offers ask for or pay Binding Leather instead of vanilla leather. */
    public static VillagerTrades.ItemListing trade(VillagerTrades.ItemListing listing) {
        return (trader, random) -> {
            MerchantOffer offer = listing.getOffer(trader, random);
            if (offer == null || !ServerConfig.get(ServerConfig.REPLACE_LEATHER_IN_TRADES)) return offer;
            return offer(offer);
        };
    }

    /**
     * The offer with vanilla leather in either cost or the result swapped for Binding Leather, or the same offer when it
     * holds none (so applying it again changes nothing). Everything else is kept: uses, maximum uses, experience and
     * whether it rewards it, demand, price multiplier, special price, and every stack's count and NBT.
     */
    public static MerchantOffer offer(MerchantOffer offer) {
        ItemStack a = offer.getBaseCostA();
        ItemStack b = offer.getCostB();
        ItemStack result = offer.getResult();
        if (!a.is(Items.LEATHER) && !b.is(Items.LEATHER) && !result.is(Items.LEATHER)) return offer;
        // The saved form carries every field, including the reward flag and special price no constructor takes.
        CompoundTag tag = offer.createTag();
        tag.put("buy", replace(a).save(new CompoundTag()));
        tag.put("buyB", replace(b).save(new CompoundTag()));
        tag.put("sell", replace(result).save(new CompoundTag()));
        return new MerchantOffer(tag);
    }

    private BindingLeather() {}
}
