package com.otectus.immersivetanning.client.station;

import com.otectus.immersivetanning.blockentity.TannersVatBlockEntity;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.network.packet.OperatorPosePacket;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModTags;
import com.otectus.immersivetanning.station.VatGeometry;
import com.otectus.immersivetanning.work.WorkAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Working the Tanner's Vat. The opening takes what the hand holds (a bucket is poured, bark scattered, a hide
 * lowered; shift for the whole stack), the hook lifts out every tanned hide at once, each hide at the rim says exactly
 * what it waits for, and the paddle may be taken from the rim for one slow sweep through the liquor. Every click is one
 * transaction on the server; the vat keeps working when the player leaves.
 */
public final class VatBehavior extends StationBehavior {
    private boolean paddle;
    @Nullable
    private Gesture sweep;
    private float su = 0.5F;
    private float sv = 0.5F;
    private boolean onSurface;

    public VatBehavior(StationFocus.Session session) {
        super(session);
    }

    @Nullable
    private TannersVatBlockEntity vat() {
        return station() instanceof TannersVatBlockEntity vat ? vat : null;
    }

    @Override
    public StationShots.Task task() {
        return StationShots.Task.VAT;
    }

    @Override
    public boolean gestureActive() {
        return sweep != null && sweep.open();
    }

    @Override
    public List<Target> targets() {
        TannersVatBlockEntity vat = vat();
        List<Target> list = new ArrayList<>();
        if (vat == null) return list;
        boolean op = session.operator;
        ItemStack held = Minecraft.getInstance().player.getMainHandItem();
        Component opening;
        boolean usable = op;
        if (paddle) {
            opening = Component.translatable(vat.hasWater() ? "verb.immersive_tanning.sweep" : "verb.immersive_tanning.sweep_dry");
            usable = op && vat.hasWater();
        } else if (held.is(Items.WATER_BUCKET)) {
            opening = Component.translatable(vat.hasWater() ? "verb.immersive_tanning.vat_full" : "verb.immersive_tanning.pour");
            usable = op && !vat.hasWater();
        } else if (held.is(Items.BUCKET)) {
            opening = Component.translatable(vat.hasWater() ? "verb.immersive_tanning.drain" : "verb.immersive_tanning.vat_empty");
            usable = op && vat.hasWater();
        } else if (held.is(ModTags.TANNIN_SOURCES)) {
            opening = Component.translatable("verb.immersive_tanning.add_bark");
        } else if (held.is(ModItems.SCRAPED_HIDE.get())) {
            opening = Component.translatable("verb.immersive_tanning.lower_hide");
            usable = op && !vat.isFull();
        } else {
            opening = Component.translatable("verb.immersive_tanning.vat_hold");
            usable = false;
        }
        int shown = Math.min(vat.slots().size(), VatGeometry.SHOWN);
        for (int i = 0; i < shown; i++) {
            TannersVatBlockEntity.Slot slot = vat.slots().get(i);
            list.add(new Target("hide" + i, VatGeometry.handleBox(i), reason(vat, slot), op && !slot.finished(Minecraft.getInstance().level.getGameTime())));
        }
        if (vat.finishedCount() > 0) list.add(new Target("hook", VatGeometry.hookBox(), Component.translatable("verb.immersive_tanning.lift", vat.finishedCount()), op));
        list.add(new Target("paddle", VatGeometry.paddleBox(), Component.translatable(paddle ? "verb.immersive_tanning.paddle_back"
                : "verb.immersive_tanning.paddle"), op));
        list.add(new Target("opening", VatGeometry.openingBox(vat.hasWater()), opening, usable));
        return list;
    }

    /** Exactly what one hide waits for, the same reason the server would give. */
    static Component reason(TannersVatBlockEntity vat, TannersVatBlockEntity.Slot slot) {
        long now = Minecraft.getInstance().level.getGameTime();
        Component size = slot.hide().size().displayName();
        return switch (vat.reason(slot)) {
            case READY -> Component.translatable("verb.immersive_tanning.hide_ready", size);
            case STEEPING -> Component.translatable(slot.tended() ? "verb.immersive_tanning.hide_steeping_tended" : "verb.immersive_tanning.hide_steeping",
                    size, StringUtil.formatTickDuration((int) slot.remainingTicks(now)));
            case PAUSED -> Component.translatable("verb.immersive_tanning.hide_paused", size);
            case NEEDS_WATER -> Component.translatable("verb.immersive_tanning.hide_needs_water", size);
            case NEEDS_BARK -> Component.translatable("verb.immersive_tanning.hide_needs_bark", size, vat.missingBark(slot));
        };
    }

    @Override
    public boolean drawerTakes(ItemStack stack) {
        return stack.is(Items.WATER_BUCKET) || stack.is(Items.BUCKET) || stack.is(ModTags.TANNIN_SOURCES) || stack.is(ModItems.SCRAPED_HIDE.get());
    }

    @Override
    public void pointer(@Nullable PointerRay.Local ray, double guiX, double guiY) {
        super.pointer(ray, guiX, guiY);
        TannersVatBlockEntity vat = vat();
        if (ray == null || vat == null) return;
        float[] hit = VatGeometry.intersectSurface(ray.origin(), ray.dir(), vat.hasWater());
        onSurface = hit != null && hit[0] >= -0.05F && hit[0] <= 1.05F && hit[1] >= -0.05F && hit[1] <= 1.05F;
        if (hit != null) {
            su = Mth.clamp(hit[0], 0F, 1F);
            sv = Mth.clamp(hit[1], 0F, 1F);
            if (sweep != null && sweep.open()) {
                sweep.move(su, sv);
                WorkSounds.sweep(pos);
            }
        }
    }

    @Override
    public boolean press(@Nullable PointerRay.Local ray) {
        TannersVatBlockEntity vat = vat();
        if (vat == null || !session.operator) return false;
        boolean stack = Screen.hasShiftDown();
        if (paddle && onSurface && vat.hasWater() && (hovered == null || hovered.id().equals("opening") || hovered.id().startsWith("hide"))) {
            sweep = new Gesture(WorkAction.VAT_SWEEP, Gesture.Space.UNIT, 0.01F);
            sweep.begin(su, sv);
            return true;
        }
        Target t = hovered;
        if (t == null) return false;
        switch (t.id()) {
            case "paddle" -> paddle = !paddle;
            case "hook" -> StationFocus.send(WorkAction.VAT_LIFT);
            case "opening" -> {
                if (!t.enabled()) return false;
                ItemStack held = Minecraft.getInstance().player.getMainHandItem();
                int flags = stack ? WorkAction.STACK : 0;
                if (held.is(Items.WATER_BUCKET)) StationFocus.send(WorkAction.VAT_POUR);
                else if (held.is(Items.BUCKET)) StationFocus.send(WorkAction.VAT_DRAIN);
                else if (held.is(ModTags.TANNIN_SOURCES)) StationFocus.send(WorkAction.VAT_ADD, flags, new int[0], new short[0], new short[0], null, null);
                else if (held.is(ModItems.SCRAPED_HIDE.get())) StationFocus.send(WorkAction.VAT_LOWER, flags, new int[0], new short[0], new short[0], null, null);
            }
            default -> {
                if (t.id().startsWith("hide") && t.enabled() && stack) StationFocus.send(WorkAction.VAT_TAKE_BACK, Integer.parseInt(t.id().substring(4)));
            }
        }
        return false;
    }

    @Override
    public void release(@Nullable PointerRay.Local ray) {
        if (sweep != null && sweep.open()) {
            sweep.end(su, sv);
            WorkSounds.fade(pos);
        }
    }

    @Override
    public void secondary() {
        paddle = false;
    }

    @Override
    public void cancelGesture() {
        if (sweep != null && sweep.open()) sweep.cancel();
        WorkSounds.fade(pos);
    }

    @Nullable
    @Override
    public float[] follow(float dx, float dy, float seconds) {
        if (sweep == null || !sweep.open()) return null;
        TannersVatBlockEntity vat = vat();
        if (vat == null) return null;
        float speed = 0.45F * seconds;
        su = Mth.clamp(su + dx * speed, 0F, 1F);
        sv = Mth.clamp(sv - dy * speed, 0F, 1F);
        return PointerRay.projectLocal(pos, facing, VatGeometry.surfacePoint(su, sv, vat.hasWater()));
    }

    public boolean paddleInHand() {
        return paddle;
    }

    public boolean sweeping() {
        return sweep != null && sweep.open();
    }

    public float su() { return su; }
    public float sv() { return sv; }

    @Override
    @Nullable
    public float[] toolPose() {
        TannersVatBlockEntity vat = vat();
        if (!paddle || vat == null) return null;
        Vec3 p = VatGeometry.surfacePoint(su, sv, vat.hasWater());
        return new float[]{OperatorPosePacket.TOOL_PADDLE, sweeping() ? 1 : 0, (float) p.x, (float) p.y, (float) p.z};
    }

    @Override
    public void renderHud(GuiGraphics g, Font font, int width, int height, int mouseX, int mouseY, float partialTick) {
        TannersVatBlockEntity vat = vat();
        if (vat == null) return;
        List<Component> guide = new ArrayList<>();
        if (!session.operator) guide.add(Component.translatable("hint.immersive_tanning.watching", vat.operatorName()));
        guide.add(com.otectus.immersivetanning.block.TannersVatBlock.statusLine(vat));
        Component wait = com.otectus.immersivetanning.block.TannersVatBlock.waitReason(vat);
        if (wait != null) guide.add(wait);
        if (paddle) guide.add(Component.translatable(ServerConfig.get(ServerConfig.VAT_TENDING_CREDIT) > 0
                ? "hint.immersive_tanning.paddle" : "hint.immersive_tanning.paddle_show"));
        else if (hints()) guide.add(Component.translatable("hint.immersive_tanning.vat"));
        if (vat.slots().size() > VatGeometry.SHOWN) guide.add(Component.translatable("hint.immersive_tanning.vat_more", vat.slots().size() - VatGeometry.SHOWN));
        int guideTop = Hud.guide(g, font, guide, width, height);
        if (hovered != null && !gestureActive()) Hud.verb(g, font, hovered.verb(), mouseX, mouseY, width, guideTop - 4, hovered.enabled());
    }
}
