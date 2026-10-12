package com.otectus.immersivetanning.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays a screen out on a canvas of at least its minimum size. Every screen is designed for the 320 x 240 that
 * Minecraft guarantees at any GUI scale it offers, so the canvas is normally the screen itself; only a forced scale
 * below that shrinks the canvas uniformly, and then drawing, pointer input, dragging and clipping share one transform.
 * Tooltips are collected while the panel draws and drawn afterwards in real screen space at normal size, so they stay
 * legible and on screen however the canvas is fitted.
 */
public abstract class WorkshopScreen extends Screen {
    private final int minimumWidth, minimumHeight;
    protected int canvasWidth, canvasHeight;
    private float uiScale = 1F, originX, originY;
    private ItemStack pendingItemTooltip = ItemStack.EMPTY;
    private List<FormattedCharSequence> pendingTooltip;

    protected WorkshopScreen(Component title, int minimumWidth, int minimumHeight) {
        super(title);
        this.minimumWidth = minimumWidth;
        this.minimumHeight = minimumHeight;
    }

    @Override
    protected final void init() {
        canvasWidth = Math.max(width, minimumWidth);
        canvasHeight = Math.max(height, minimumHeight);
        uiScale = Math.min(width / (float) canvasWidth, height / (float) canvasHeight);
        originX = (width - canvasWidth * uiScale) / 2F;
        originY = (height - canvasHeight * uiScale) / 2F;
        initPanel();
    }

    protected abstract void initPanel();

    @Override
    public final void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        pendingItemTooltip = ItemStack.EMPTY;
        pendingTooltip = null;
        g.pose().pushPose();
        g.pose().translate(originX, originY, 0);
        g.pose().scale(uiScale, uiScale, 1F);
        renderPanel(g, (int) Math.floor(localX(mouseX)), (int) Math.floor(localY(mouseY)), partialTick);
        g.pose().popPose();
        if (!pendingItemTooltip.isEmpty()) g.renderTooltip(font, pendingItemTooltip, mouseX, mouseY);
        else if (pendingTooltip != null && !pendingTooltip.isEmpty()) g.renderTooltip(font, pendingTooltip, mouseX, mouseY);
    }

    protected void renderPanel(GuiGraphics g, int x, int y, float partialTick) { super.render(g, x, y, partialTick); }

    /** Shows the item's own tooltip (with every tooltip mod's additions) after the panel is drawn. */
    protected void tooltip(ItemStack stack) {
        pendingItemTooltip = stack;
        pendingTooltip = null;
    }

    /** Shows text lines, wrapped to a readable width, after the panel is drawn. */
    protected void tooltip(List<Component> lines) {
        int wrap = Math.max(120, Math.min(260, width - 24));
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component line : lines) out.addAll(font.split(line, wrap));
        pendingTooltip = out;
        pendingItemTooltip = ItemStack.EMPTY;
    }

    protected void tooltip(Component line) {
        tooltip(List.of(line));
    }

    private double localX(double x) { return (x - originX) / uiScale; }
    private double localY(double y) { return (y - originY) / uiScale; }

    protected int[] screenArea(int x, int y, int w, int h) {
        return new int[]{Math.round(originX + x * uiScale), Math.round(originY + y * uiScale), Math.round(w * uiScale), Math.round(h * uiScale)};
    }

    protected void scissor(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.enableScissor((int) Math.floor(originX + x0 * uiScale), (int) Math.floor(originY + y0 * uiScale),
                (int) Math.ceil(originX + x1 * uiScale), (int) Math.ceil(originY + y1 * uiScale));
    }

    @Override public final boolean mouseClicked(double x, double y, int button) { return panelClicked(localX(x), localY(y), button); }
    protected boolean panelClicked(double x, double y, int button) { return super.mouseClicked(x, y, button); }
    @Override public final boolean mouseReleased(double x, double y, int button) { return panelReleased(localX(x), localY(y), button); }
    protected boolean panelReleased(double x, double y, int button) { return super.mouseReleased(x, y, button); }
    @Override public final boolean mouseDragged(double x, double y, int button, double dx, double dy) { return panelDragged(localX(x), localY(y), button, dx / uiScale, dy / uiScale); }
    protected boolean panelDragged(double x, double y, int button, double dx, double dy) { return super.mouseDragged(x, y, button, dx, dy); }
    @Override public final boolean mouseScrolled(double x, double y, double delta) { return panelScrolled(localX(x), localY(y), delta); }
    protected boolean panelScrolled(double x, double y, double delta) { return super.mouseScrolled(x, y, delta); }
    @Override public void mouseMoved(double x, double y) { super.mouseMoved(localX(x), localY(y)); }
    @Override public boolean isPauseScreen() { return false; }
}
