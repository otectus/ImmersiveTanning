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
public class FinishSelectScreen extends Screen {
    private final OpenFinishPacket request;
    private int left;
    private int top;
    private int cardWidth;
    private int cardHeight;
    private int gap;
    private boolean vertical;

    public FinishSelectScreen(OpenFinishPacket request) {
        super(Component.translatable("screen.immersive_tanning.finish"));
        this.request = request;
    }

    @Override
    protected void init() {
        vertical = width < 330;
        gap = 8;
        if (vertical) {
            cardWidth = Math.min(260, width - 24);
            cardHeight = Math.min(62, (height - 70) / 3 - gap);
            left = (width - cardWidth) / 2;
            top = (height - (cardHeight * 3 + gap * 2)) / 2 + 14;
        } else {
            cardWidth = Math.min(116, (width - 24 - gap * 2) / 3);
            cardHeight = Math.min(150, height - 80);
            left = (width - (cardWidth * 3 + gap * 2)) / 2;
            top = (height - cardHeight) / 2 + 14;
        }
    }

    private int cardX(int i) {
        return vertical ? left : left + i * (cardWidth + gap);
    }

    private int cardY(int i) {
        return vertical ? top + i * (cardHeight + gap) : top;
    }

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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int card = cardAt(mouseX, mouseY);
        if (button == 0 && card >= 0) {
            choose(Finish.byOrdinal(card));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        HideData hide = request.hide();
        Component heading = Component.translatable("screen.immersive_tanning.finish.title");
        int headY = (vertical ? top : top) - 34;
        g.drawCenteredString(font, heading, width / 2, headY, TanningGui.TEXT_WARM);
        Component info = Component.translatable("screen.immersive_tanning.finish.hide", hide.size().displayName(), Scores.label(hide.integrity()));
        if (hide.trait() != null) info = Component.translatable("screen.immersive_tanning.finish.hide_trait", info, hide.trait().displayName());
        g.drawCenteredString(font, info, width / 2, headY + 12, TanningGui.TEXT_DIM);

        int hovered = cardAt(mouseX, mouseY);
        for (int i = 0; i < 3; i++) renderCard(g, Finish.byOrdinal(i), i, cardX(i), cardY(i), i == hovered);
        g.drawCenteredString(font, Component.translatable("screen.immersive_tanning.finish.keys"), width / 2,
                (vertical ? cardY(2) + cardHeight : top + cardHeight) + 8, TanningGui.TEXT_DIM);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderCard(GuiGraphics g, Finish finish, int index, int x, int y, boolean hovered) {
        int color = 0xFF000000 | finish.leatherColor();
        TanningGui.panel(g, x, y, cardWidth, cardHeight);
        if (hovered) g.renderOutline(x + 2, y + 2, cardWidth - 4, cardHeight - 4, TanningGui.TEXT_WARM);
        int textX;
        int textY;
        int textW;
        if (vertical) {
            renderPose(g, finish, x + 8, y + 8, 40, cardHeight - 16, color);
            textX = x + 56;
            textY = y + 8;
            textW = cardWidth - 64;
        } else {
            renderPose(g, finish, x + 12, y + 10, cardWidth - 24, 46, color);
            textX = x + 8;
            textY = y + 62;
            textW = cardWidth - 16;
        }
        Component name = Component.translatable("screen.immersive_tanning.finish.card", index + 1, finish.displayName());
        g.drawString(font, TanningGui.clipped(font, name, textW), textX, textY, 0xFF000000 | finish.textColor(), false);
        g.drawString(font, TanningGui.clipped(font, finish.tagline(), textW), textX, textY + 11, TanningGui.TEXT, false);
        int bottom = y + cardHeight - 4 - (finish == request.last() ? 11 : 0);
        int lines = (bottom - (textY + 24)) / 10;
        if (lines > 0) TanningGui.drawWrapped(g, font, finish.description(), textX, textY + 24, textW, TanningGui.TEXT_DIM, lines);
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
