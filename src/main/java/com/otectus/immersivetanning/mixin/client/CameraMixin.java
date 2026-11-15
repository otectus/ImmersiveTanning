package com.otectus.immersivetanning.mixin.client;

import com.otectus.immersivetanning.client.station.StationFocus;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Station focus owns the camera while it frames a task: applied at the end of the camera's own setup (after vanilla
 * and after other mods' updates of it), so exactly one update stands. The player's body is not drawn from the shot
 * (the view is not "detached"); everything else - the world, other players, lighting - renders as usual.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Inject(method = "setup", at = @At("TAIL"), require = 1)
    private void immersiveTanning$focus(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
        StationFocus.Pose pose = StationFocus.camera(partialTick);
        if (pose == null) return;
        this.detached = false;
        setRotation(pose.yaw(), pose.pitch());
        setPosition(pose.position().x, pose.position().y, pose.position().z);
    }
}
