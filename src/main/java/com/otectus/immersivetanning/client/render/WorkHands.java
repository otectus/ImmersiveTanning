package com.otectus.immersivetanning.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.otectus.immersivetanning.config.ClientConfig;
import com.otectus.immersivetanning.client.station.StationFocus;
import com.otectus.immersivetanning.station.StationFrame;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Tools and hands at the work. A tool is placed by its working point - the knife's edge, the awl's tip - on the
 * contact, and turned around that point so its shaft rises towards the hand and its flat side faces the viewer; a
 * blade clearing flesh is never drawn hovering above it. The operator's own arms (their skin, slim or wide, sleeves
 * as they wear them, main hand as set) reach in from the viewer's side to the grip and to the supporting hold; with
 * "tools only" just the tools are drawn. Watchers see the tool where the operator's accepted work put it.
 */
public final class WorkHands {
    private record ArmDraw(Matrix4f pose, Matrix3f normal, boolean right, int light) {}
    private static final List<ArmDraw> ARMS = new ArrayList<>(2);
    private static final MultiBufferSource.BufferSource ARM_BUFFERS = MultiBufferSource.immediate(new BufferBuilder(1024));

    /** Arm geometry is collected by the station, then blended after the world (including vat liquor) is drawn. */
    public static void beginFrame() {
        ARMS.clear();
    }

    public static void endFrame() {
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (player != null && StationFocus.active() && armsShown()
                && mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer) {
            PlayerModel<AbstractClientPlayer> model = renderer.getModel();
            float alpha = (float) ClientConfig.get(ClientConfig.ARM_OPACITY);
            for (ArmDraw draw : ARMS) {
                PoseStack pose = new PoseStack();
                pose.last().pose().set(draw.pose());
                pose.last().normal().set(draw.normal());
                ModelPart arm = draw.right() ? model.rightArm : model.leftArm;
                ModelPart sleeve = draw.right() ? model.rightSleeve : model.leftSleeve;
                renderPart(arm, pose, player, draw.light(), alpha);
                if (player.isModelPartShown(draw.right() ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE)) {
                    renderPart(sleeve, pose, player, draw.light(), alpha);
                }
            }
            ARM_BUFFERS.endBatch();
        }
        ARMS.clear();
    }

    private static void renderPart(ModelPart part, PoseStack pose, AbstractClientPlayer player, int light, float alpha) {
        PartPose previous = part.storePose();
        boolean visible = part.visible;
        // Some animation mods scale shared model parts. Working arms always use the vanilla dimensions;
        // restore every field afterwards so another player render cannot inherit our pose.
        float sx = part.xScale, sy = part.yScale, sz = part.zScale;
        try {
            part.setPos(0, 0, 0);
            part.setRotation(0, 0, 0);
            part.xScale = part.yScale = part.zScale = 1F;
            part.visible = true;
            part.render(pose, ARM_BUFFERS.getBuffer(RenderType.entityTranslucentCull(player.getSkinTextureLocation())),
                    light, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, alpha);
        } finally {
            part.loadPose(previous);
            part.visible = visible;
            part.xScale = sx;
            part.yScale = sy;
            part.zScale = sz;
        }
    }

    /** Grip and contact in the actual held models, before item-display transforms (local model pixels). */
    public enum Tool {
        KNIFE(8F, 3.6F, 8F, 13.25F, 0.32F),
        AWL(8F, 3.6F, 8F, 15F, 0.28F);

        final Vector3f grip;
        final Vector3f point;
        final float scale;

        Tool(float gu, float gv, float pu, float pv, float scale) {
            this.grip = new Vector3f((gu - 8F) / 16F, (gv - 8F) / 16F, 0F);
            this.point = new Vector3f((pu - 8F) / 16F, (pv - 8F) / 16F, 0F);
            this.scale = scale;
        }

        /** Distance from the working point to the grip, in local pixels. */
        public float reach() {
            return grip.distance(point) * 16F * scale;
        }
    }

    /**
     * Draws a tool with its working point at {@code point} (local pixels), its shaft along {@code shaft} (from grip to
     * point) and its flat side towards {@code face}. Returns the grip position in local pixels.
     */
    public static Vec3 tool(PoseStack pose, MultiBufferSource buffers, ItemStack stack, Tool tool, Vec3 point, Vec3 shaft, Vec3 face,
                            int light, Level level, int seed) {
        Vector3f s = new Vector3f(tool.point).sub(tool.grip).normalize();
        Vector3f mFace = new Vector3f(0, 0, 1);
        Vector3f mThird = new Vector3f(mFace).cross(s).normalize();
        Vector3f a = new Vector3f((float) shaft.x, (float) shaft.y, (float) shaft.z).normalize();
        Vector3f f = new Vector3f((float) face.x, (float) face.y, (float) face.z);
        f.sub(new Vector3f(a).mul(f.dot(a)));
        if (f.lengthSquared() < 1.0E-6F) {
            f.set(Math.abs(a.y) > 0.9F ? 1 : 0, Math.abs(a.y) > 0.9F ? 0 : 1, 0);
            f.sub(new Vector3f(a).mul(f.dot(a)));
        }
        f.normalize();
        Vector3f t = new Vector3f(f).cross(a).normalize();
        Matrix3f world = new Matrix3f().setColumn(0, a).setColumn(1, f).setColumn(2, t);
        Matrix3f model = new Matrix3f().setColumn(0, s).setColumn(1, mFace).setColumn(2, mThird);
        Matrix3f rotation = world.mul(model.transpose(new Matrix3f()), new Matrix3f());
        Quaternionf q = new Quaternionf().setFromNormalized(rotation);
        pose.pushPose();
        pose.translate(point.x / 16.0, point.y / 16.0, point.z / 16.0);
        pose.mulPose(q);
        pose.scale(tool.scale, tool.scale, tool.scale);
        pose.translate(-tool.point.x, -tool.point.y, -tool.point.z);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, pose,
                buffers, level, seed);
        pose.popPose();
        Vector3f grip = new Vector3f(tool.grip).sub(tool.point).mul(tool.scale * 16F);
        rotation.transform(grip);
        return point.add(grip.x, grip.y, grip.z);
    }

    /** Whether arms are drawn at all (the option, and a player model to take them from). */
    public static boolean armsShown() {
        return ClientConfig.get(ClientConfig.HAND_VISUALS) == ClientConfig.HandVisuals.HANDS_AND_TOOLS;
    }

    /** The working arm's side: the player's main hand. */
    public static boolean rightHanded() {
        return Minecraft.getInstance().options.mainHand().get() == HumanoidArm.RIGHT;
    }

    /**
     * Places a rigid vanilla arm with its palm at the grip. The screen-edge anchor only aims the arm:
     * it never determines its length, width or thickness. Transparency keeps the work visible.
     */
    public static void arm(PoseStack pose, BlockPos pos, Direction facing, boolean rightArm, Vec3 hand, int light) {
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (player == null || !armsShown()) return;
        EntityRenderer<?> renderer = mc.getEntityRenderDispatcher().getRenderer(player);
        if (!(renderer instanceof PlayerRenderer)) return;
        Camera camera = mc.gameRenderer.getMainCamera();
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        Vector3f look = camera.getLookVector();
        // Aim toward the lower corner at the work's depth. Do not stretch to that corner:
        // both wide and slim skins retain their original 12-pixel arm length at every station.
        double side = rightArm ? -1.0 : 1.0;
        Vec3 handWorld = StationFrame.pxToWorld(pos, facing, hand.x, hand.y, hand.z);
        Vec3 lookDir = new Vec3(look.x(), look.y(), look.z());
        double depth = Math.max(0.2, handWorld.subtract(camera.getPosition()).dot(lookDir));
        double halfHeight = depth * Math.tan(Math.toRadians(StationFocus.fov(mc.options.fov().get()) * 0.5));
        double aspect = mc.getWindow().getWidth() / (double) Math.max(1, mc.getWindow().getHeight());
        Vec3 anchorWorld = camera.getPosition().add(lookDir.scale(depth))
                .add(new Vec3(left.x(), left.y(), left.z()).scale(side * halfHeight * aspect * 0.85))
                .add(new Vec3(up.x(), up.y(), up.z()).scale(-halfHeight * 1.25));
        Vec3 shoulder = StationFrame.toLocalPx(pos, facing, anchorWorld);
        Vec3 towardCamera = StationFrame.dirToLocal(facing, lookDir.scale(-1));
        pose.pushPose();
        poseArm(pose, hand, shoulder.subtract(hand), towardCamera, rightArm, "slim".equals(player.getModelName()));
        ARMS.add(new ArmDraw(new Matrix4f(pose.last().pose()), new Matrix3f(pose.last().normal()), rightArm, light));
        pose.popPose();
    }

    /** Rigid attachment shared by every station: model pixels remain 1/16 of a block, including the slim skin. */
    public static void poseArm(PoseStack pose, Vec3 hand, Vec3 axis, Vec3 towardCamera, boolean rightArm, boolean slim) {
        Vector3f e = new Vector3f((float) axis.x, (float) axis.y, (float) axis.z);
        if (e.lengthSquared() < 1.0E-6F) e.set(0, -1, 0);
        e.normalize();
        Vector3f front = new Vector3f((float) towardCamera.x, (float) towardCamera.y, (float) towardCamera.z);
        front.sub(new Vector3f(e).mul(front.dot(e)));
        if (front.lengthSquared() < 1.0E-6F) {
            front.set(Math.abs(e.y) < 0.9F ? 0 : 1, Math.abs(e.y) < 0.9F ? 1 : 0, 0);
            front.sub(new Vector3f(e).mul(front.dot(e)));
        }
        front.normalize();
        Vector3f third = new Vector3f(front).cross(e).normalize();
        // Vanilla: palm y = 10, shoulder y = -2; front = -z. Rotation and translation only.
        Matrix3f world = new Matrix3f().setColumn(0, e).setColumn(1, front).setColumn(2, third);
        Matrix3f modelBasis = new Matrix3f().setColumn(0, new Vector3f(0, -1, 0)).setColumn(1, new Vector3f(0, 0, -1))
                .setColumn(2, new Vector3f(-1, 0, 0));
        Matrix3f rotation = world.mul(modelBasis.transpose(new Matrix3f()), new Matrix3f());
        float cx = rightArm ? (slim ? -0.5F : -1F) : (slim ? 0.5F : 1F);
        pose.translate(hand.x / 16.0, hand.y / 16.0, hand.z / 16.0);
        pose.mulPose(new Quaternionf().setFromNormalized(rotation));
        pose.translate(-cx / 16.0, -10.0 / 16.0, 0);
    }

    private WorkHands() {}
}
