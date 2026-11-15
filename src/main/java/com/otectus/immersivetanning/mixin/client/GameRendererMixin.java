package com.otectus.immersivetanning.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.otectus.immersivetanning.client.station.StationFocus;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No walking bob while focus holds the camera at a station: a working shot stays still. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true, require = 1)
    private void immersiveTanning$stillShot(PoseStack pose, float partialTick, CallbackInfo ci) {
        if (StationFocus.ownsCamera()) ci.cancel();
    }
}
