package com.otectus.immersivetanning.client.compat.jei;

import com.otectus.immersivetanning.client.screen.StonebornGui;
import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.client.gui.GuiGraphics;

/** Recipe-card surfaces match the workstation screens without changing JEI's surrounding controls. */
final class StonebornJei {
    static IDrawable background(int width, int height) {
        return new IDrawable() {
            @Override public int getWidth() { return width; }
            @Override public int getHeight() { return height; }
            @Override public void draw(GuiGraphics g, int x, int y) { StonebornGui.dialog(g, x, y, width, height); }
        };
    }
    static IDrawable slot() {
        return new IDrawable() {
            @Override public int getWidth() { return 18; }
            @Override public int getHeight() { return 18; }
            @Override public void draw(GuiGraphics g, int x, int y) { StonebornGui.slot(g, x, y); }
        };
    }
    static IDrawable arrow() {
        return new IDrawable() {
            @Override public int getWidth() { return 22; }
            @Override public int getHeight() { return 16; }
            @Override public void draw(GuiGraphics g, int x, int y) {
                g.fill(x + 2, y + 8, x + 16, y + 10, StonebornGui.EDGE_LIGHT);
                for (int i = 0; i <= 6; i++) {
                    g.fill(x + 12 + i, y + 3 + i, x + 13 + i, y + 4 + i, StonebornGui.BRASS_LIGHT);
                    g.fill(x + 18 - i, y + 9 + i, x + 19 - i, y + 10 + i, StonebornGui.BRASS_LIGHT);
                }
            }
        };
    }
    private StonebornJei() {}
}
