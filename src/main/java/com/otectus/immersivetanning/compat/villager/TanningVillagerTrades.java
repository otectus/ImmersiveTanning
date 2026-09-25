package com.otectus.immersivetanning.compat.villager;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.leather.LeatherworkData;
import com.otectus.immersivetanning.leather.VillageLeatherwork;
import com.otectus.immersivetanning.recipe.EquipmentSeams;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.trait.HideTraitLoader;
import com.otectus.immersivetanning.trait.TraitRef;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SaddleItem;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * The tannery in villages. Leatherworkers sell the tools, buy hides and sell leatherwork of their own making (village
 * quality, signed with the villager's name); their vanilla leather armor and saddles become leatherwork too. More
 * Villagers' hunter buys hides and sells trophy hides, and wandering traders sometimes carry one. Every listing checks
 * the config when its offer is generated, so disabling trades needs no restart.
 */
@Mod.EventBusSubscriber(modid = ImmersiveTanning.MOD_ID)
public final class TanningVillagerTrades {
    private static final ResourceLocation HUNTER = new ResourceLocation("morevillagers", "hunter");
    private static final ResourceLocation[] TROPHY_TRAITS = {ImmersiveTanning.id("insulating"), ImmersiveTanning.id("heatproof"),
            ImmersiveTanning.id("tough"), ImmersiveTanning.id("water_resistant"), ImmersiveTanning.id("lightweight")};

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onVillagerTrades(VillagerTradesEvent event) {
        VillagerProfession profession = event.getType();
        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();
        if (profession == VillagerProfession.LEATHERWORKER) {
            trades.values().forEach(list -> list.replaceAll(TanningVillagerTrades::villageMade));
            trades.get(1).add(sell(() -> new ItemStack(ModItems.TANNERS_KNIFE.get()), 3, 8, 2));
            trades.get(1).add(sell(() -> new ItemStack(ModItems.AWL.get()), 2, 8, 2));
            trades.get(1).add(buy(() -> new ItemStack(ModItems.rawHide(HideSize.MEDIUM), 3), 16, 2));
            trades.get(2).add(sell(() -> new ItemStack(ModItems.BARK.get(), 12), 1, 12, 5));
            trades.get(2).add(buy(() -> new ItemStack(ModItems.rawHide(HideSize.LARGE), 2), 16, 10));
            trades.get(3).add(leatherwork(Items.LEATHER_BOOTS, Finish.SUPPLE, null, 5, 15));
            trades.get(3).add(leatherwork(Items.LEATHER_LEGGINGS, Finish.HARNESS, null, 7, 15));
            trades.get(4).add(leatherwork(Items.LEATHER_CHESTPLATE, Finish.HARDENED, null, 10, 20));
            trades.get(4).add(leatherwork(Items.SADDLE, Finish.HARNESS, null, 8, 20));
            trades.get(5).add(leatherwork(Items.LEATHER_BOOTS, Finish.SUPPLE, ImmersiveTanning.id("insulating"), 16, 30));
            trades.get(5).add(leatherwork(Items.LEATHER_HELMET, Finish.HARDENED, ImmersiveTanning.id("heatproof"), 16, 30));
        } else if (HUNTER.equals(ForgeRegistries.VILLAGER_PROFESSIONS.getKey(profession))) {
            trades.get(1).add(buy(() -> new ItemStack(ModItems.rawHide(HideSize.MEDIUM), 3), 16, 2));
            trades.get(2).add(sell(() -> new ItemStack(ModItems.TANNERS_KNIFE.get()), 3, 8, 5));
            trades.get(3).add(buy(() -> new ItemStack(ModItems.rawHide(HideSize.LARGE), 2), 16, 10));
            trades.get(4).add(trophyHide(12, 3, 15));
        }
    }

    @SubscribeEvent
    public static void onWandererTrades(WandererTradesEvent event) {
        event.getRareTrades().add(trophyHide(10, 2, 1));
    }

    private static boolean enabled() {
        return ServerConfig.get(ServerConfig.ENABLE_VILLAGER_TRADES);
    }

    private static VillagerTrades.ItemListing sell(Supplier<ItemStack> item, int emeralds, int maxUses, int xp) {
        return (trader, random) -> enabled() ? new MerchantOffer(new ItemStack(Items.EMERALD, emeralds), item.get(), maxUses, xp, 0.05F) : null;
    }

    private static VillagerTrades.ItemListing buy(Supplier<ItemStack> wanted, int maxUses, int xp) {
        return (trader, random) -> enabled() ? new MerchantOffer(wanted.get(), new ItemStack(Items.EMERALD), maxUses, xp, 0.05F) : null;
    }

    /** A piece from the villager's own bench. Better work costs more; a trait piece is only offered once traits load. */
    private static VillagerTrades.ItemListing leatherwork(Item item, Finish finish, @Nullable ResourceLocation traitId, int emeralds, int xp) {
        return (trader, random) -> {
            if (!enabled()) return null;
            TraitRef trait = traitId == null ? null : HideTraitLoader.get(traitId);
            if (traitId != null && trait == null) return null;
            ItemStack stack = VillageLeatherwork.make(new ItemStack(item), finish, VillageLeatherwork.score(random),
                    VillageLeatherwork.score(random), trait, crafter(trader));
            int price = Math.max(1, emeralds + (LeatherworkData.get(stack).orElseThrow().overallScore() - 60) / 10);
            return new MerchantOffer(new ItemStack(Items.EMERALD, price), stack, 3, xp, 0.2F);
        };
    }

    private static VillagerTrades.ItemListing trophyHide(int emeralds, int maxUses, int xp) {
        return (trader, random) -> {
            if (!enabled()) return null;
            TraitRef trait = HideTraitLoader.get(TROPHY_TRAITS[random.nextInt(TROPHY_TRAITS.length)]);
            if (trait == null) return null;
            return new MerchantOffer(new ItemStack(Items.EMERALD, emeralds), ModItems.rawHide(HideSize.LARGE).create(trait), maxUses, xp, 0.05F);
        };
    }

    /** A vanilla leatherworker offer of leather gear or a saddle becomes a piece of village leatherwork. */
    private static VillagerTrades.ItemListing villageMade(VillagerTrades.ItemListing listing) {
        return (trader, random) -> {
            MerchantOffer offer = listing.getOffer(trader, random);
            if (offer == null || !enabled()) return offer;
            ItemStack result = offer.getResult();
            if (LeatherworkData.has(result) || !(EquipmentSeams.isLeatherArmor(result) || result.getItem() instanceof SaddleItem)) return offer;
            ItemStack made = VillageLeatherwork.make(result.copy(), VillageLeatherwork.finish(random), VillageLeatherwork.score(random),
                    VillageLeatherwork.score(random), null, crafter(trader));
            return new MerchantOffer(offer.getBaseCostA(), offer.getCostB(), made, offer.getUses(), offer.getMaxUses(), offer.getXp(),
                    offer.getPriceMultiplier(), offer.getDemand());
        };
    }

    /** The villager's name for "Crafted by": MCA villagers have their own names; untranslated keys are left out. */
    @Nullable
    private static String crafter(@Nullable Entity trader) {
        if (trader == null) return null;
        String name = trader.getName().getString();
        return name.isBlank() || name.startsWith("entity.") ? null : name;
    }

    private TanningVillagerTrades() {}
}
