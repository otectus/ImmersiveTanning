package com.otectus.immersivetanning.client.screen;

import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.network.ModNetwork;
import com.otectus.immersivetanning.network.packet.OpenFinishPacket;
import com.otectus.immersivetanning.network.packet.SelectFinishPacket;
import com.otectus.immersivetanning.registry.ModSounds;
import com.otectus.immersivetanning.util.Scores;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The one strategic choice of the tannery: how is this hide going to be finished? Three large cards, each with its
 * headline identity. The choice takes two seconds; the consequences last for the leather's whole life.
 * Keys 1-3 choose directly; sneak-using a wet hide on a rack skips this screen and repeats the last finish.
 */
public class FinishSelectScreen extends WorkshopScreen {
    private final OpenFinishPacket request;
    private int left;
    private int top;
    private int cardWidth;
    private int cardHeight;
    private int gap;
    private static final int W = 416, H = 288;

    public FinishSelectScreen(OpenFinishPacket request) {
        super(Component.translatable("screen.immersive_tanning.finish"), 432, 304);
        this.request = request;
    }

    @Override
    protected void initPanel() {
        gap = 8;
        cardWidth = 124;
        cardHeight = 194;
        left = (canvasWidth - W) / 2;
        top = (canvasHeight - H) / 2;
    }

    private int cardX(int i) { return left + 14 + i * (cardWidth + gap); }

    private int cardY(int i) { return top + 60; }

    private int cardAt(double mouseX, double mouseY) {
        for (int i = 0; i < 3; i++) {
            int x = cardX(i);
            int y = cardY(i);
            if (mouseX >= x && mouseX < x + cardWidth && mouseY >= y && mouseY < y + cardHeight) return i;
        }
        return -1;
    }

    private void choose(Finish finish) {
        ModNetwork.CHANNEL.sendToServer(new SelectFinishPacket(request.pos(), finish));
        TanningGui.playUi(ModSounds.RACK_MOUNT.get(), 0.9F, 0.6F);
        onClose();
    }

    @Override
    protected boolean panelClicked(double mouseX, double mouseY, int button) {
        int card = cardAt(mouseX, mouseY);
        if (button == 0 && card >= 0) {
            choose(Finish.byOrdinal(card));
            return true;
        }
        return super.panelClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_3) {
            choose(Finish.byOrdinal(key - GLFW.GLFW_KEY_1));
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            choose(request.last());
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        StonebornGui.panel(g, left, top, W, H);
        StonebornGui.heading(g, font, Component.translatable("screen.immersive_tanning.finish.title"), left, top, W);
        HideData hide = request.hide();
        Component info = Component.translatable("screen.immersive_tanning.finish.hide", hide.size().displayName(), Scores.label(hide.integrity()));
        if (hide.trait() != null) info = Component.translatable("screen.immersive_tanning.finish.hide_trait", info, hide.trait().displayName());
        g.fill(left + 12, top + 32, left + W - 12, top + 55, StonebornGui.SURFACE);
        TanningGui.drawWrapped(g, font, info, left + 18, top + 35, W - 36, TanningGui.TEXT_DIM, 2);
        int hovered = cardAt(mouseX, mouseY);
        for (int i = 0; i < 3; i++) renderCard(g, Finish.byOrdinal(i), i, cardX(i), cardY(i), i == hovered);
        g.fill(left + 12, top + 262, left + W - 12, top + H - 8, StonebornGui.SURFACE);
        g.drawCenteredString(font, Component.translatable("screen.immersive_tanning.finish.keys"), left + W / 2, top + 268, TanningGui.TEXT_DIM);
        super.renderPanel(g, mouseX, mouseY, partialTick);
        if (hovered >= 0) {
            Finish finish = Finish.byOrdinal(hovered);
            int availableLines = (cardHeight - 98 - (finish == request.last() ? 11 : 0)) / 10;
            if (font.split(finish.description(), cardWidth - 16).size() > availableLines)
                g.renderTooltip(font, font.split(finish.description(), 220), mouseX, mouseY);
        }
    }

    private void renderCard(GuiGraphics g, Finish finish, int index, int x, int y, boolean hovered) {
        int color = 0xFF000000 | finish.leatherColor();
        TanningGui.well(g, x, y, cardWidth, cardHeight);
        if (hovered) g.renderOutline(x, y, cardWidth, cardHeight, TanningGui.TEXT_WARM);
        renderPose(g, finish, x + 14, y + 10, cardWidth - 28, 42, color);
        int textX = x + 8, textY = y + 60, textW = cardWidth - 16;
        Component name = Component.translatable("screen.immersive_tanning.finish.card", index + 1, finish.displayName());
        g.drawString(font, TanningGui.clipped(font, name, textW), textX, textY, 0xFF000000 | finish.textColor(), false);
        TanningGui.drawWrapped(g, font, finish.tagline(), textX, textY + 11, textW, TanningGui.TEXT, 2);
        int bottom = y + cardHeight - 4 - (finish == request.last() ? 11 : 0);
        int lines = (bottom - (textY + 34)) / 10;
        if (lines > 0) TanningGui.drawWrapped(g, font, finish.description(), textX, textY + 34, textW, TanningGui.TEXT_DIM, lines);
        if (finish == request.last()) {
            Component last = Component.translatable("screen.immersive_tanning.finish.last");
            g.drawString(font, last, x + cardWidth - 6 - font.width(last), y + cardHeight - 13, TanningGui.TEXT_WARM, false);
        }
    }

    /** The hide's posture in the frame: loose and sagging, tensioned, or stretched taut. */
    private void renderPose(GuiGraphics g, Finish finish, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + 2, y + h, TanningGui.LEATHER_DARK);
        g.fill(x + w - 2, y, x + w, y + h, TanningGui.LEATHER_DARK);
        g.fill(x, y, x + w, y + 2, TanningGui.LEATHER_DARK);
        int inset = switch (finish) {
            case SUPPLE -> Math.max(4, w / 5);
            case HARNESS -> Math.max(3, w / 8);
            case HARDENED -> 3;
        };
        int top = y + 4;
        int bottom = y + h - (finish == Finish.SUPPLE ? 6 : 3);
        float[] pts = finish == Finish.SUPPLE
                ? new float[]{x + inset, top, x + w - inset, top, x + w - inset - 2, bottom - 4, x + w / 2F, bottom, x + inset + 2, bottom - 4}
                : new float[]{x + inset, top, x + w - inset, top, x + w - inset, bottom, x + inset, bottom};
        TanningGui.polygon(g, pts, TanningGui.withAlpha(color, 170));
        int cords = finish == Finish.HARDENED ? 4 : finish == Finish.HARNESS ? 2 : 1;
        for (int i = 0; i < cords; i++) {
            float t = cords == 1 ? 0.5F : i / (float) (cords - 1);
            float cx = x + inset + t * (w - 2 * inset);
            TanningGui.line(g, cx, y + 2, cx, top, 1, 1, 1, TanningGui.STITCH);
        }
        if (finish == Finish.HARDENED) {
            TanningGui.line(g, x + 2, top + 2, x + inset, top + 2, 1, 1, 1, TanningGui.STITCH);
            TanningGui.line(g, x + w - 2, top + 2, x + w - inset, top + 2, 1, 1, 1, TanningGui.STITCH);
            TanningGui.line(g, x + 2, bottom - 2, x + inset, bottom - 2, 1, 1, 1, TanningGui.STITCH);
            TanningGui.line(g, x + w - 2, bottom - 2, x + w - inset, bottom - 2, 1, 1, 1, TanningGui.STITCH);
        }
    }
}
