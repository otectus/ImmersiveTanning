package com.otectus.immersivetanning.mixin;

import com.otectus.immersivetanning.effect.LeatherworkEffects;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Applied after the default priority, so it also reaches an ItemStack#hurt that another mod overwrote. */
@Mixin(value = ItemStack.class, priority = 1500)
public abstract class ItemStackMixin {

    /** Scales incoming durability damage for leatherwork before Unbreaking is applied. */
    @ModifyVariable(method = "hurt(ILnet/minecraft/util/RandomSource;Lnet/minecraft/server/level/ServerPlayer;)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int immersive_tanning$scaleDurabilityDamage(int amount) {
        return LeatherworkEffects.adjustDamage((ItemStack) (Object) this, amount);
    }
}
