package com.otectus.immersivetanning.client.station;

import com.otectus.immersivetanning.config.ClientConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The little text focus draws over the world: a verb beside the cursor, a line or two of guidance at the bottom, and
 * small text buttons. Translucent plates, readable at any GUI scale, high contrast when asked for; never a window.
 */
public final class Hud {
    /** A clickable text button: where it was drawn this frame. */
    public record Button(String id, int x, int y, int w, int h, boolean enabled) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static int plate() {
        return ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES) ? 0xF0000000 : 0xA0101010;
    }

    /** The verb beside the cursor, wrapped and kept above the guide's top (passed as the available height). */
    public static void verb(GuiGraphics g, Font font, Component text, int mx, int my, int width, int height, boolean enabled) {
        List<FormattedCharSequence> lines = font.split(text, Math.max(40, Math.min(240, width - 20)));
        int w = lines.stream().mapToInt(font::width).max().orElse(0) + 8;
        int h = lines.size() * 10 + 4;
        int x = Math.max(2, Math.min(mx + 12, width - w - 2));
        int y = my + 10;
        if (y + h > height) y = my - h - 10;
        y = Math.max(22, Math.min(y, height - h - 2));
        g.fill(x, y, x + w, y + h, plate());
        if (ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES)) g.renderOutline(x, y, w, h, 0xFFFFFFFF);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x + 4, y + 2 + i * 10, enabled ? 0xFFFFFFFF : 0xFFB0B0B0, false);
        }
    }

    /** Guidance at the bottom centre, above the hotbar: one to three lines. */
    public static int guide(GuiGraphics g, Font font, List<Component> lines, int width, int height) {
        int y = height - 54;
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        int maxWidth = Math.min(width - 40, 360);
        for (Component c : lines) wrapped.addAll(font.split(c, maxWidth));
        if (wrapped.isEmpty()) return y;
        int w = 0;
        for (FormattedCharSequence s : wrapped) w = Math.max(w, font.width(s));
        int h = wrapped.size() * 10 + 4;
        int top = y - h;
        g.fill(width / 2 - w / 2 - 5, top - 1, width / 2 + w / 2 + 5, top + h, plate());
        for (int i = 0; i < wrapped.size(); i++) {
            FormattedCharSequence s = wrapped.get(i);
            g.drawString(font, s, width / 2 - font.width(s) / 2, top + 2 + i * 10, i == 0 ? 0xFFFFE9B0 : 0xFFE0E0E0, false);
        }
        return top;
    }

    /** A small text button; returns where it is so clicks can find it. */
    public static Button button(GuiGraphics g, Font font, String id, Component label, int x, int y, boolean enabled, boolean hovered) {
        int w = font.width(label) + 10;
        int h = 14;
        g.fill(x, y, x + w, y + h, enabled ? (hovered ? 0xE0503A24 : 0xC0302014) : 0x90202020);
        g.renderOutline(x, y, w, h, enabled ? 0xFFB08A5A : 0xFF606060);
        g.drawString(font, label, x + 5, y + 3, enabled ? 0xFFFFF0D0 : 0xFF909090, false);
        return new Button(id, x, y, w, h, enabled);
    }

    /** A labelled three-zone gauge, readable without colour: Slack | Even | Puckered with a marker. */
    public static void tension(GuiGraphics g, Font font, int x, int y, float pull, float min, float ideal, float max, Component state) {
        int w = 120;
        float span = max * 1.4F;
        int a = x + Math.round(w * min / span);
        int b = x + Math.round(w * max / span);
        g.fill(x - 3, y - 13, x + w + 3, y + 14, plate());
        boolean accessible = ClientConfig.get(ClientConfig.COLORBLIND_SAFE_COLORS);
        g.fill(x, y, a, y + 6, accessible ? 0xFF807B72 : 0xFF6A7A9A);
        g.fill(a, y, b, y + 6, accessible ? 0xFF579BC4 : 0xFF7AA06A);
        g.fill(b, y, x + w, y + 6, 0xFFB07050);
        for (int i = x; i < a; i += 4) g.fill(i, y + 2, i + 2, y + 4, 0x60FFFFFF);
        for (int i = b; i < x + w; i += 3) g.fill(i, y, i + 1, y + 6, 0x60000000);
        int m = x + Math.round(w * Math.min(pull, span) / span);
        g.fill(m - 1, y - 3, m + 1, y + 9, 0xFFFFFFFF);
        int i = x + Math.round(w * ideal / span);
        g.fill(i, y + 6, i + 1, y + 9, 0xFFFFFFFF);
        g.drawString(font, state, x, y - 11, 0xFFFFFFFF, false);
    }

    private Hud() {}
}
