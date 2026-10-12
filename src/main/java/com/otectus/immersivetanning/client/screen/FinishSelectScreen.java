package com.otectus.immersivetanning.client.screen;

import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.network.ModNetwork;
import com.otectus.immersivetanning.network.packet.OpenFinishPacket;
import com.otectus.immersivetanning.network.packet.SelectFinishPacket;
import com.otectus.immersivetanning.registry.ModSounds;
import com.otectus.immersivetanning.util.Scores;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The one strategic choice of the tannery: how is this hide going to be finished? Three large cards on a vanilla
 * container panel, each a vanilla button face with the hide's posture in a dark display and the finish's headline
 * identity. The choice takes two seconds; the consequences last for the leather's whole life.
 * Keys 1-3 choose directly; sneak-using a wet hide on a rack skips this screen and repeats the last finish.
 */
public class FinishSelectScreen extends WorkshopScreen {
    private final OpenFinishPacket request;
    private static final int MAX_WIDTH = 416;
    private static final int MAX_HEIGHT = 276;
    private static final int GAP = 6;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int cardWidth;
    private int cardHeight;
    private int cardTop;
    private Component info;
    /** Whether each card's description was cut short when last drawn, so hovering it shows the whole text. */
    private final boolean[] clipped = new boolean[3];

    public FinishSelectScreen(OpenFinishPacket request) {
        super(Component.translatable("screen.immersive_tanning.finish"), 320, 240);
        this.request = request;
    }

    @Override
    protected void initPanel() {
        panelWidth = Math.min(MAX_WIDTH, canvasWidth - 12);
        panelHeight = Math.min(MAX_HEIGHT, canvasHeight - 4);
        left = (canvasWidth - panelWidth) / 2;
        top = (canvasHeight - panelHeight) / 2;
        HideData hide = request.hide();
        info = Component.translatable("screen.immersive_tanning.finish.hide", hide.size().displayName(), Scores.label(hide.integrity()));
        if (hide.trait() != null) info = Component.translatable("screen.immersive_tanning.finish.hide_trait", info, hide.trait().displayName());
        int infoLines = Math.max(1, Math.min(2, font.split(info, panelWidth - 24).size()));
        cardTop = 18 + 8 + infoLines * 10 + 6;
        cardWidth = (panelWidth - 16 - 2 * GAP) / 3;
        cardHeight = panelHeight - cardTop - 22;
    }

    private int cardX(int i) { return left + 8 + i * (cardWidth + GAP); }

    private int cardY(int i) { return top + cardTop; }

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
        VanillaGui.panel(g, left, top, panelWidth, panelHeight);
        Component title = Component.translatable("screen.immersive_tanning.finish.title");
        g.drawString(font, VanillaGui.clipped(font, title, panelWidth - 16), left + 8, top + 7, VanillaGui.LABEL, false);
        // The grade and trait keep their own colours, which are chosen for dark backgrounds.
        VanillaGui.readout(g, font, info, left + 8, top + 18, panelWidth - 16, 2);
        int hovered = cardAt(mouseX, mouseY);
        for (int i = 0; i < 3; i++) renderCard(g, Finish.byOrdinal(i), i, cardX(i), cardY(i), i == hovered);
        Component keys = Component.translatable("screen.immersive_tanning.finish.keys");
        VanillaGui.centered(g, font, Component.literal(VanillaGui.clipped(font, keys, panelWidth - 16)), left + panelWidth / 2,
                top + panelHeight - 14, VanillaGui.LABEL_DIM);
        super.renderPanel(g, mouseX, mouseY, partialTick);
        if (hovered >= 0 && clipped[hovered]) tooltip(Finish.byOrdinal(hovered).description());
        else if (VanillaGui.isClipped(font, title, panelWidth - 16) && mouseX >= left + 8 && mouseX < left + panelWidth - 8
                && mouseY >= top + 5 && mouseY < top + 17) tooltip(title);
    }

    private void renderCard(GuiGraphics g, Finish finish, int index, int x, int y, boolean hovered) {
        int color = 0xFF000000 | finish.leatherColor();
        VanillaGui.row(g, x, y, cardWidth, cardHeight, true, hovered);
        VanillaGui.display(g, x + 5, y + 5, cardWidth - 10, 46);
        renderPose(g, finish, x + 12, y + 8, cardWidth - 24, 40, color);
        int textX = x + 6, textY = y + 56, textW = cardWidth - 12;
        Component name = Component.translatable("screen.immersive_tanning.finish.card", index + 1, finish.displayName());
        g.drawString(font, VanillaGui.clipped(font, name, textW), textX, textY, 0xFF000000 | finish.textColor(), true);
        VanillaGui.wrapped(g, font, finish.tagline(), textX, textY + 11, textW, VanillaGui.ROW_TEXT, 2, true);
        int bottom = y + cardHeight - 4 - (finish == request.last() ? 11 : 0);
        int lines = Math.max(0, (bottom - (textY + 34)) / 10);
        VanillaGui.wrapped(g, font, finish.description(), textX, textY + 34, textW, VanillaGui.ROW_TEXT_DIM, lines, true);
        clipped[index] = VanillaGui.isWrapClipped(font, finish.description(), textW, lines);
        if (finish == request.last()) {
            Component last = Component.translatable("screen.immersive_tanning.finish.last");
            g.drawString(font, last, x + cardWidth - 6 - font.width(last), y + cardHeight - 14, 0xFFFFFFA0, true);
        }
    }

    /** The hide's posture in the frame: loose and sagging, tensioned, or stretched taut. */
    private void renderPose(GuiGraphics g, Finish finish, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + 2, y + h, TanningGui.FRAME);
        g.fill(x + w - 2, y, x + w, y + h, TanningGui.FRAME);
        g.fill(x, y, x + w, y + 2, TanningGui.FRAME);
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
