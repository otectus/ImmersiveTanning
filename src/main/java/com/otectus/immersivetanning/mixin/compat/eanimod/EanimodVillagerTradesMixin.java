package com.otectus.immersivetanning.mixin.compat.eanimod;

import com.otectus.immersivetanning.compat.geneticanimals.GeneticAnimalsTrades;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Genetic Animals 0.11.12 creates its leatherworker, shepherd and wandering-trader offers here and adds them to the
 * merchant directly. Each offer it returns is converted to Binding Leather before it is added
 * ({@link GeneticAnimalsTrades#created}); its choice of item, dye, price and uses is untouched.
 */
@Pseudo
@Mixin(targets = "mokiyoki.enhancedanimals.util.EanimodVillagerTrades", remap = false)
public abstract class EanimodVillagerTradesMixin {

    @Inject(method = "getEanimodTrade", at = @At("RETURN"), cancellable = true, require = 0)
    private static void immersive_tanning$bindingLeatherOffer(VillagerProfession profession, int level, CallbackInfoReturnable<MerchantOffer> cir) {
        cir.setReturnValue(GeneticAnimalsTrades.created(cir.getReturnValue()));
    }

    @Inject(method = "getWanderingEanimodTrade", at = @At("RETURN"), cancellable = true, require = 0)
    private static void immersive_tanning$bindingLeatherWanderingOffer(CallbackInfoReturnable<MerchantOffer> cir) {
        cir.setReturnValue(GeneticAnimalsTrades.created(cir.getReturnValue()));
    }
}
