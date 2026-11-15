package com.otectus.immersivetanning.mixin;

import com.otectus.immersivetanning.loot.DeathDrops;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks what a tracked mob wore in its vanilla equipment slots: {@code Mob#dropCustomDeathLoot} drops it from inside
 * the death-loot call, but it is equipment, not the animal's skin ({@link DeathDrops}).
 */
@Mixin(Mob.class)
public abstract class MobMixin {

    @Inject(method = "dropCustomDeathLoot", at = @At("HEAD"))
    private void immersive_tanning$openWorn(DamageSource source, int looting, boolean recentlyHit, CallbackInfo ci) {
        DeathDrops drops = DeathDrops.of((LivingEntity) (Object) this);
        if (drops != null) drops.openWorn(((LivingEntity) (Object) this).captureDrops());
    }

    @Inject(method = "dropCustomDeathLoot", at = @At("RETURN"))
    private void immersive_tanning$closeWorn(DamageSource source, int looting, boolean recentlyHit, CallbackInfo ci) {
        DeathDrops drops = DeathDrops.of((LivingEntity) (Object) this);
        if (drops != null) drops.closeWorn(((LivingEntity) (Object) this).captureDrops());
    }
}
