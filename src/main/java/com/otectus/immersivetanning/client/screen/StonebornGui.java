package com.otectus.immersivetanning.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;

/** Stoneborn palette and pixel geometry shared visually with the other Immersive workshops. */
public class StonebornGui {
    public static final ResourceLocation STONE = new ResourceLocation("immersive_tanning", "textures/gui/stone.png");
    public static final ResourceLocation SLOT = new ResourceLocation("immersive_tanning", "textures/gui/slot.png");
    public static final int SURFACE = 0xFF37322A;
    public static final int INSET = 0xFF27241F;
    public static final int EDGE = 0xFF151310;
    public static final int BORDER = 0xFF4F473C;
    public static final int EDGE_LIGHT = 0xFF675D4E;
    public static final int ROW_HOVER = 0xFF494237;
    public static final int ROW_SELECTED = 0xFF524735;
    public static final int BRASS = 0xFFBD862B;
    public static final int BRASS_DARK = 0xFF8C6420;
    public static final int BRASS_LIGHT = 0xFFE0B250;
    public static final int TEXT = 0xFFE5DFD2;
    public static final int TEXT_DIM = 0xFFB9B0A0;
    public static final int TEXT_WARM = 0xFFE5C887;
    public static final int PRESSURE = 0xFF97ADAE;

    /** Tiled masonry stays at its native pixel density; only the surround is ornamented. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x80000000);
        g.fill(x, y, x + w, y + h, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, EDGE_LIGHT);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, BORDER);
        g.fill(x + 4, y + 4, x + w - 4, y + h - 4, EDGE);
        for (int py = 5; py < h - 5; py += 32) {
            for (int px = 5; px < w - 5; px += 32) {
                g.blit(STONE, x + px, y + py, 0, 0, Math.min(32, w - 5 - px), Math.min(32, h - 5 - py), 32, 32);
            }
        }
        for (int sx : new int[]{-1, 1}) for (int sy : new int[]{-1, 1}) {
            int cx = sx == 1 ? x + 2 : x + w - 3;
            int cy = sy == 1 ? y + 2 : y + h - 3;
            for (int i = 0; i < 7; i++) {
                int c = i < 3 ? BRASS_LIGHT : BRASS_DARK;
                g.fill(cx + sx * i, cy, cx + sx * i + 1, cy + 1, c);
                g.fill(cx, cy + sy * i, cx + 1, cy + sy * i + 1, c);
            }
            g.fill(cx + sx * 2, cy + sy * 2, cx + sx * 2 + 1, cy + sy * 2 + 1, BRASS);
        }
    }

    public static void heading(GuiGraphics g, Font font, Component title, int x, int y, int w) {
        g.fill(x + 6, y + 6, x + w - 6, y + 27, SURFACE);
        rule(g, x + 8, y + 26, w - 16);
        g.drawString(font, clipped(font, title, w - 28), x + 14, y + 12, TEXT_WARM, false);
    }

    public static void dialog(GuiGraphics g, int x, int y, int w, int h) {
        panel(g, x, y, w, h);
        g.fill(x + 6, y + 6, x + w - 6, y + h - 6, SURFACE);
    }

    /** A recessed area. */
    public static void well(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, EDGE_LIGHT);
        g.fill(x, y, x + w - 1, y + h - 1, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, INSET);
    }

    public static void row(GuiGraphics g, int x, int y, int w, int h, boolean selected, boolean hover) {
        g.fill(x, y, x + w, y + h, selected ? ROW_SELECTED : hover ? ROW_HOVER : SURFACE);
        if (selected) {
            g.fill(x, y, x + 2, y + h, BRASS_LIGHT);
            g.fill(x + 2, y, x + w, y + 1, BRASS_DARK);
        }
    }

    public static void slot(GuiGraphics g, int x, int y) {
        g.blit(SLOT, x, y, 0, 0, 18, 18, 18, 18);
    }

    public static void rule(GuiGraphics g, int x, int y, int width) {
        g.fill(x, y, x + width, y + 1, EDGE);
        g.fill(x, y + 1, x + width, y + 2, EDGE_LIGHT);
    }

    public static String clipped(Font font, Component text, int width) {
        if (font.width(text) <= width) return text.getString();
        return font.plainSubstrByWidth(text.getString(), Math.max(0, width - font.width("..."))) + "...";
    }

    public static int drawWrapped(GuiGraphics g, Font font, Component text, int x, int y, int width, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(text, width);
        int n = Math.min(lines.size(), Math.max(0, maxLines));
        for (int i = 0; i < n; i++) g.drawString(font, lines.get(i), x, y + i * 10, color, false);
        return n;
    }

    public static Button button(int x, int y, int width, int height, Component label, Button.OnPress onPress) {
        return new StoneButton(x, y, width, height, label, onPress);
    }

    private static final class StoneButton extends Button {
        private StoneButton(int x, int y, int width, int height, Component label, OnPress onPress) {
            super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            boolean hot = active && isHoveredOrFocused();
            g.fill(x, y, x + getWidth(), y + getHeight(), EDGE);
            g.fill(x + 1, y + 1, x + getWidth() - 1, y + getHeight() - 1, hot ? BRASS : BORDER);
            g.fill(x + 2, y + 2, x + getWidth() - 2, y + getHeight() - 2, !active ? INSET : hot ? ROW_HOVER : SURFACE);
            g.fill(x + 2, y + 2, x + getWidth() - 2, y + 3, active ? EDGE_LIGHT : BORDER);
            Font font = Minecraft.getInstance().font;
            g.drawCenteredString(font, clipped(font, getMessage(), getWidth() - 8), x + getWidth() / 2, y + (getHeight() - 8) / 2,
                    active ? TEXT : 0xFF827B6E);
        }
    }

    protected StonebornGui() {}
}
