package com.otectus.immersivetanning.client.station;

import com.mojang.blaze3d.vertex.PoseStack;
import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.client.render.WorkHands;
import com.otectus.immersivetanning.config.ClientConfig;
import com.otectus.immersivetanning.station.StationFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * The client hooks of station focus: its tick, the pointer matrices, field of view and camera angles (applied last,
 * so focus is the one owner), no vanilla first-person hands, crosshair or block outline while working, a subtle
 * outline on the part under the cursor, and a clean slate whenever the level changes or the player disconnects.
 */
@Mod.EventBusSubscriber(modid = ImmersiveTanning.MOD_ID, value = Dist.CLIENT)
public final class FocusEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        StationFocus.tick();
        WorkSounds.tick();
        ClientWork.sweep();
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            WorkHands.beginFrame();
            PointerRay.capture(event.getProjectionMatrix(), new Matrix4f(event.getPoseStack().last().pose()), event.getCamera().getPosition());
            StationFocus.verifyCamera(event.getCamera().getPosition());
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            outline(event.getPoseStack(), event.getCamera().getPosition());
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            WorkHands.endFrame();
        }
    }

    /** A subtle outline on the hovered part (stronger with high contrast or large targets). */
    private static void outline(PoseStack pose, Vec3 camera) {
        StationFocus.Session s = StationFocus.session();
        if (s == null || s.behavior().gestureActive()) return;
        StationBehavior.Target t = s.behavior().hovered();
        if (t == null || !t.enabled()) return;
        AABB box = t.box();
        BlockPos pos = s.pos;
        Vec3 a = StationFrame.pxToWorld(pos, s.behavior().facing(), box.minX, box.minY, box.minZ);
        Vec3 b = StationFrame.pxToWorld(pos, s.behavior().facing(), box.maxX, box.maxY, box.maxZ);
        AABB world = new AABB(a, b).move(-camera.x, -camera.y, -camera.z);
        boolean strong = ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES) || ClientConfig.get(ClientConfig.LARGE_TARGETS);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), world, 1F, 0.95F, 0.75F, strong ? 0.9F : 0.45F);
        buffers.endBatch(RenderType.lines());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) return;
        double fov = StationFocus.fov(event.getFOV());
        if (fov != event.getFOV()) event.setFOV(fov);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!StationFocus.ownsCamera()) return;
        event.setYaw(event.getCamera().getYRot());
        event.setPitch(event.getCamera().getXRot());
        event.setRoll(0F);
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent event) {
        if (StationFocus.active()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (StationFocus.active() && event.getOverlay() == VanillaGuiOverlay.CROSSHAIR.type()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onHighlight(RenderHighlightEvent.Block event) {
        if (StationFocus.active()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            StationFocus.reset();
            ClientWork.clear();
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        StationFocus.reset();
        ClientWork.clear();
    }

    private FocusEvents() {}
}
