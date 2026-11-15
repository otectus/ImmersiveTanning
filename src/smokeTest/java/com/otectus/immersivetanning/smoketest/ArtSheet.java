package com.otectus.immersivetanning.smoketest;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.otectus.immersivetanning.client.render.WorkDraw;
import com.otectus.immersivetanning.client.render.WorkHands;
import com.otectus.immersivetanning.client.renderer.MeshUtil;
import com.otectus.immersivetanning.station.RackGeometry;
import com.mojang.math.Axis;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Test-only inventory and perspective contact sheet, rendered through the actual item model pipeline. */
final class ArtSheet extends Screen {
    private record Sample(String label, ItemStack stack) {}

    private static final ItemDisplayContext[] TOOL_CONTEXTS = {
            ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.GROUND};
    private static final String[] CONTEXT_LABELS = {"First / right", "First / left", "Third person", "Dropped"};

    ArtSheet() {
        super(Component.literal("Tannery artwork"));
    }

    private static List<Sample> samples() {
        List<Sample> result = new ArrayList<>();
        for (HideSize size : HideSize.values()) {
            result.add(new Sample(size.getSerializedName() + " / raw", new ItemStack(ModItems.rawHide(size))));
            result.add(new Sample(size.getSerializedName() + " / scraped", ModItems.SCRAPED_HIDE.get().create(size, 80, null)));
            result.add(new Sample(size.getSerializedName() + " / wet", ModItems.WET_TANNED_HIDE.get().create(size, 80, null)));
        }
        for (Finish finish : Finish.values()) {
            result.add(new Sample(finish.getSerializedName() + " leather", ModItems.leather(finish).create(80, null, 1)));
        }
        result.add(new Sample("Binding leather", new ItemStack(ModItems.BINDING_LEATHER.get())));
        result.add(new Sample("Leather scraps", new ItemStack(ModItems.LEATHER_SCRAPS.get())));
        result.add(new Sample("Bark", new ItemStack(ModItems.BARK.get())));
        result.add(new Sample("Tanner's knife", new ItemStack(ModItems.TANNERS_KNIFE.get())));
        result.add(new Sample("Awl", new ItemStack(ModItems.AWL.get())));
        result.add(new Sample("Tanner's rack", new ItemStack(ModItems.TANNERS_RACK.get())));
        result.add(new Sample("Tanner's vat", new ItemStack(ModItems.TANNERS_VAT.get())));
        result.add(new Sample("Leatherworker's bench", new ItemStack(ModItems.LEATHERWORKERS_BENCH.get())));
        return result;
    }

    static boolean checkModels(Minecraft mc) {
        for (Sample sample : samples()) {
            var model = mc.getItemRenderer().getModel(sample.stack(), mc.level, mc.player, 0);
            if (model == mc.getModelManager().getMissingModel()) return false;
            for (ItemDisplayContext context : ItemDisplayContext.values()) {
                var resolved = model.applyTransform(context, new PoseStack(), context.name().endsWith("LEFT_HAND"));
                if (resolved.getParticleIcon().contents().name().getPath().equals("missingno")
                        || resolved.getQuads(null, null, RandomSource.create(0)).isEmpty()) return false;
            }
        }
        for (var item : List.of(ModItems.TANNERS_KNIFE.get(), ModItems.AWL.get())) {
            var model = mc.getItemRenderer().getModel(new ItemStack(item), mc.level, mc.player, 0);
            if (model.applyTransform(ItemDisplayContext.GUI, new PoseStack(), false)
                    == model.applyTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, new PoseStack(), false)) return false;
        }
        return true;
    }

    /** Palm attachment and the vanilla 4x12x4 / 3x12x4 proportions survive every aim, including degenerate anchors. */
    static boolean checkArmDimensions() {
        Vec3 hand = new Vec3(7, 16, 11);
        for (boolean slim : new boolean[]{false, true}) for (boolean right : new boolean[]{false, true}) {
            for (Vec3 axis : new Vec3[]{new Vec3(2, -8, -4), new Vec3(-40, -80, 2), new Vec3(0, 0, 1), Vec3.ZERO}) {
                PoseStack pose = new PoseStack();
                WorkHands.poseArm(pose, hand, axis, new Vec3(0, 0, 1), right, slim);
                var matrix = pose.last().pose();
                float cx = right ? (slim ? -.5F : -1F) : (slim ? .5F : 1F);
                Vector3f palm = matrix.transformPosition(new Vector3f(cx / 16F, 10F / 16F, 0));
                if (palm.distance(new Vector3f(7F / 16F, 1, 11F / 16F)) > 0.00001F) return false;
                Vector3f x = matrix.transformDirection(new Vector3f((slim ? 3F : 4F) / 16F, 0, 0));
                Vector3f y = matrix.transformDirection(new Vector3f(0, 12F / 16F, 0));
                Vector3f z = matrix.transformDirection(new Vector3f(0, 0, 4F / 16F));
                if (Math.abs(x.length() - (slim ? 3F : 4F) / 16F) > 0.00001F
                        || Math.abs(y.length() - 0.75F) > 0.00001F || Math.abs(z.length() - 0.25F) > 0.00001F
                        || Math.abs(x.dot(y)) > 0.00001F || Math.abs(y.dot(z)) > 0.00001F || Math.abs(z.dot(x)) > 0.00001F) return false;
            }
        }
        return true;
    }

    /** A concave panel must have one continuous grain map, independent of how its outline was triangulated. */
    static boolean checkPanelUvs() {
        class Capture implements VertexConsumer {
            double x, z;
            int vertices;
            boolean valid = true;
            public VertexConsumer vertex(double x, double y, double z) { this.x = x * 16; this.z = z * 16; return this; }
            public VertexConsumer color(int r, int g, int b, int a) { return this; }
            public VertexConsumer uv(float u, float v) {
                float[] uv = WorkDraw.LEATHER;
                valid &= Math.abs(u - (uv[0] + (uv[2] - uv[0]) * (x - 1) / 5)) < 0.00001;
                valid &= Math.abs(v - (uv[1] + (uv[3] - uv[1]) * (z - 1) / 6)) < 0.00001;
                return this;
            }
            public VertexConsumer overlayCoords(int u, int v) { return this; }
            public VertexConsumer uv2(int u, int v) { return this; }
            public VertexConsumer normal(float x, float y, float z) { valid &= y > 0.99F; return this; }
            public void endVertex() { vertices++; }
            public void defaultColor(int r, int g, int b, int a) {}
            public void unsetDefaultColor() {}
        }
        Capture capture = new Capture();
        WorkDraw.fill(new PoseStack(), capture, new float[]{1, 1, 6, 1, 6, 3, 4, 3, 4, 7, 1, 7},
                16, 0xFFFFFFFF, LightTexture.FULL_BRIGHT, WorkDraw.LEATHER);
        return capture.valid && capture.vertices == 16;
    }

    /** Compare each strip's light normal to the shared work surface, including the crest and steep front. */
    static boolean checkHideNormals() {
        class Capture implements VertexConsumer {
            float[] expected;
            boolean valid = true;
            public VertexConsumer vertex(double x, double y, double z) { return this; }
            public VertexConsumer color(int r, int g, int b, int a) { return this; }
            public VertexConsumer uv(float u, float v) { return this; }
            public VertexConsumer overlayCoords(int u, int v) { return this; }
            public VertexConsumer uv2(int u, int v) { return this; }
            public VertexConsumer normal(float x, float y, float z) {
                valid &= x * expected[0] + y * expected[1] + z * expected[2] > 0.99F;
                return this;
            }
            public void endVertex() {}
            public void defaultColor(int r, int g, int b, int a) {}
            public void unsetDefaultColor() {}
        }
        Capture capture = new Capture();
        for (float v : new float[]{0.04F, 0.18F, 0.32F, 0.5F, 0.8F, 0.96F}) {
            float[][][] strip = {{RackGeometry.fleshPoint(0.2F, v), RackGeometry.fleshPoint(0.8F, v)},
                    {RackGeometry.fleshPoint(0.2F, v + 0.001F), RackGeometry.fleshPoint(0.8F, v + 0.001F)}};
            capture.expected = RackGeometry.fleshNormal(v);
            MeshUtil.sheet(new PoseStack(), capture, strip, 0xFFFFFFFF, LightTexture.FULL_BRIGHT, true);
        }
        capture.expected = new float[]{0, 1, 0};
        MeshUtil.sheet(new PoseStack(), capture, new float[][][]{{{0, 0, 0}, {1, 0, 0}}, {{0, 0, 1}, {1, 0, 1}}},
                0xFFFFFFFF, LightTexture.FULL_BRIGHT, true);
        return capture.valid;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF242428);
        float scale = Math.min(width / 640F, height / 400F);
        g.pose().pushPose();
        g.pose().translate((width - 640 * scale) / 2, (height - 400 * scale) / 2, 0);
        g.pose().scale(scale, scale, scale);
        g.drawCenteredString(font, "IMMERSIVE TANNING / INVENTORY & TOOL MODELS", 320, 8, 0xECD7AC);
        List<Sample> samples = samples();
        for (int i = 0; i < samples.size(); i++) {
            Sample sample = samples.get(i);
            int x = 4 + i % 5 * 127;
            int y = 26 + i / 5 * 58;
            g.fill(x, y, x + 123, y + 54, 0xFF343236);
            g.pose().pushPose();
            g.pose().translate(x + 44, y + 3, 0);
            g.pose().scale(2, 2, 2);
            g.renderItem(sample.stack(), 0, 0);
            g.pose().popPose();
            g.drawCenteredString(font, sample.label(), x + 61, y + 41, 0xDBCEC0);
        }
        g.drawCenteredString(font, "SOLID TOOLS / DISPLAY CONTEXTS", 320, 269, 0xECD7AC);
        for (int tool = 0; tool < 2; tool++) {
            ItemStack stack = new ItemStack(tool == 0 ? ModItems.TANNERS_KNIFE.get() : ModItems.AWL.get());
            for (int i = 0; i < TOOL_CONTEXTS.length; i++) {
                int x = 40 + (tool * 4 + i) * 80;
                g.pose().pushPose();
                g.pose().translate(x, 331, 180);
                g.pose().scale(65, -65, 65);
                g.pose().mulPose(Axis.YP.rotationDegrees(25));
                minecraft.getItemRenderer().renderStatic(stack, TOOL_CONTEXTS[i], LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, g.pose(), g.bufferSource(), minecraft.level, 0);
                g.flush();
                g.pose().popPose();
                g.drawCenteredString(font, CONTEXT_LABELS[i], x, 374, 0xDBCEC0);
            }
        }
        g.pose().popPose();
    }
}
