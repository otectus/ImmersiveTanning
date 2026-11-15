package com.otectus.immersivetanning.compat.geneticanimals;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.leather.BindingLeather;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Genetic Animals' merchant offers deal in Binding Leather, like every other offer, when {@code replaceLeatherInTrades}
 * is on. It adds its offers straight to villagers and wandering traders (from its own tick and spawn handlers), past
 * the listings Immersive Tanning wraps, so its offer factories are hooked instead ({@link #created}). Offers it saved
 * before this, or while the setting was off, are converted the first time someone opens that merchant's trades.
 * Only leather changes: wool, emeralds, saddles, its random dye and every count, use and price stay as they were.
 */
@Mod.EventBusSubscriber(modid = ImmersiveTanning.MOD_ID)
public final class GeneticAnimalsTrades {

    /** An offer as Genetic Animals creates it (from the factory hooks). */
    @Nullable
    public static MerchantOffer created(@Nullable MerchantOffer offer) {
        if (offer == null || !ServerConfig.get(ServerConfig.REPLACE_LEATHER_IN_TRADES)) return offer;
        return BindingLeather.offer(offer);
    }

    /** Before the offers go to the player's screen, so the screen and the trade both see the converted offer. */
    @SubscribeEvent
    public static void onTradesOpened(PlayerContainerEvent.Open event) {
        if (!(event.getContainer() instanceof MerchantMenu menu) || !GeneticAnimals.isLoaded()) return;
        if (!ServerConfig.get(ServerConfig.REPLACE_LEATHER_IN_TRADES)) return;
        normalize(menu.getOffers());
    }

    /** Converts, in place, the Genetic Animals offers that still deal in vanilla leather. Returns how many changed. */
    public static int normalize(MerchantOffers offers) {
        int changed = 0;
        for (int i = 0; i < offers.size(); i++) {
            MerchantOffer offer = offers.get(i);
            if (!isGeneticAnimals(offer)) continue;
            MerchantOffer converted = BindingLeather.offer(offer);
            if (converted != offer) {
                offers.set(i, converted);
                changed++;
            }
        }
        return changed;
    }

    /** An offer of Genetic Animals': one of its items is in it. Other offers are left as they were saved. */
    public static boolean isGeneticAnimals(MerchantOffer offer) {
        return isGeneticAnimals(offer.getBaseCostA()) || isGeneticAnimals(offer.getCostB()) || isGeneticAnimals(offer.getResult());
    }

    private static boolean isGeneticAnimals(ItemStack stack) {
        return !stack.isEmpty() && GeneticAnimals.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
    }

    private GeneticAnimalsTrades() {}
}
