package com.otectus.immersivetanning.smoketest;

import com.mojang.blaze3d.vertex.PoseStack;
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
