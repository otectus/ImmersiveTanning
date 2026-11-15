package com.otectus.immersivetanning.mixin;

import com.otectus.immersivetanning.loot.DeathDrops;
import com.otectus.immersivetanning.loot.DeathDropsHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records where a tracked entity's death drops come from ({@link DeathDrops}): its own death loot
 * ({@code dropCustomDeathLoot}) and what it carried ({@code dropEquipment}), by bracketing those two calls in
 * {@code dropAllDeathLoot} while Forge captures the drops. Entities other than Genetic Animals get no record. The
 * brackets do not stop the game if another mod has rewritten that method: {@code TanningMixinPlugin} checks them, and
 * without them Genetic Animals' drops are left untouched ({@code GeneticAnimalsHooks.Hook.DEATH_DROPS}).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements DeathDropsHolder {
    private static final String CUSTOM_LOOT = "Lnet/minecraft/world/entity/LivingEntity;dropCustomDeathLoot(Lnet/minecraft/world/damagesource/DamageSource;IZ)V";
    private static final String EQUIPMENT = "Lnet/minecraft/world/entity/LivingEntity;dropEquipment()V";

    @Unique
    @Nullable
    private DeathDrops immersive_tanning$deathDrops;

    @Override
    @Nullable
    public DeathDrops immersive_tanning$deathDrops() {
        return immersive_tanning$deathDrops;
    }

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void immersive_tanning$beginDeathDrops(DamageSource source, CallbackInfo ci) {
        immersive_tanning$deathDrops = DeathDrops.begin((LivingEntity) (Object) this);
    }

    @Inject(method = "dropAllDeathLoot", at = @At(value = "INVOKE", target = CUSTOM_LOOT), require = 0)
    private void immersive_tanning$openDeathLoot(DamageSource source, CallbackInfo ci) {
        if (immersive_tanning$deathDrops != null) immersive_tanning$deathDrops.openNatural(((LivingEntity) (Object) this).captureDrops());
    }

    @Inject(method = "dropAllDeathLoot", at = @At(value = "INVOKE", target = CUSTOM_LOOT, shift = At.Shift.AFTER), require = 0)
    private void immersive_tanning$closeDeathLoot(DamageSource source, CallbackInfo ci) {
        if (immersive_tanning$deathDrops != null) immersive_tanning$deathDrops.closeNatural(((LivingEntity) (Object) this).captureDrops());
    }

    @Inject(method = "dropAllDeathLoot", at = @At(value = "INVOKE", target = EQUIPMENT), require = 0)
    private void immersive_tanning$openCargo(DamageSource source, CallbackInfo ci) {
        if (immersive_tanning$deathDrops != null) immersive_tanning$deathDrops.openCargo(((LivingEntity) (Object) this).captureDrops());
    }

    @Inject(method = "dropAllDeathLoot", at = @At(value = "INVOKE", target = EQUIPMENT, shift = At.Shift.AFTER), require = 0)
    private void immersive_tanning$closeCargo(DamageSource source, CallbackInfo ci) {
        if (immersive_tanning$deathDrops != null) immersive_tanning$deathDrops.closeCargo(((LivingEntity) (Object) this).captureDrops());
    }

    @Inject(method = "dropAllDeathLoot", at = @At("RETURN"))
    private void immersive_tanning$endDeathDrops(DamageSource source, CallbackInfo ci) {
        immersive_tanning$deathDrops = null;
    }
}
