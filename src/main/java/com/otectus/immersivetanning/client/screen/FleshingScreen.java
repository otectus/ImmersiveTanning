package com.otectus.immersivetanning.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import com.otectus.immersivetanning.config.ClientConfig;
import com.otectus.immersivetanning.minigame.FleshingSurface;
import com.otectus.immersivetanning.network.ModNetwork;
import com.otectus.immersivetanning.network.packet.CancelSessionPacket;
import com.otectus.immersivetanning.network.packet.FinishFleshingPacket;
import com.otectus.immersivetanning.network.packet.FleshStrokePacket;
import com.otectus.immersivetanning.network.packet.OpenFleshingPacket;
import com.otectus.immersivetanning.network.packet.SessionResultPacket;
import com.otectus.immersivetanning.registry.ModSounds;
import com.otectus.immersivetanning.util.Scores;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Fleshing: the hide lies on the beam with a few broad patches of flesh. Hold the mouse button and drag the knife
 * through them; along the grain a patch comes off in one pass, across it takes two. Lift the knife between patches,
 * because scraping clean hide is overworking. The screen predicts the result locally with the same surface the server
 * replays; only the server's Integrity counts.
 */
public class FleshingScreen extends Screen {
    private enum Stage { READY, PLAYING, WAITING, RESULT }

    private static final int MAX_WIDTH = 368;
    private static final int MAX_HEIGHT = 236;
    private static final int FLUSH_MS = 80;
    private static final float MIN_STEP_CELLS = 0.45F;

    private final OpenFleshingPacket session;
    private final FleshingSurface surface;
    private final long clientStartMs;
    private final NativeImage image;
    private final DynamicTexture texture;
    private final ResourceLocation textureId;
    private int uploadedVersion = -1;
    private Stage stage = Stage.READY;
    private Button doneButton;
    private Button finishButton;
    private Button nextButton;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int areaX;
    private int areaY;
    private int areaW;
    private int areaH;
    private boolean pressing;
    private int lastQX;
    private int lastQY;
    private boolean pendingBegin;
    private boolean pendingEnd;
    private final List<int[]> pending = new ArrayList<>();
    private long lastFlushMs;
    private int sequence;
    private long lastScrapeSound;
    private SessionResultPacket result;
    private boolean closedByServer;

    public FleshingScreen(OpenFleshingPacket session) {
        super(Component.translatable("screen.immersive_tanning.fleshing"));
        this.session = session;
        this.surface = new FleshingSurface(session.size(), session.seed());
        this.clientStartMs = Util.getMillis() + session.readyDelayMs();
        this.image = new NativeImage(NativeImage.Format.RGBA, FleshingSurface.W, FleshingSurface.H, false);
        this.texture = new DynamicTexture(image);
        this.textureId = Minecraft.getInstance().getTextureManager().register("immersive_tanning_fleshing", texture);
    }

    public int sessionId() {
        return session.sessionId();
    }

    /** The work area in screen coordinates: x, y, width, height. */
    public int[] workArea() {
        return new int[]{areaX, areaY, areaW, areaH};
    }

    @Override
    protected void init() {
        panelWidth = Math.min(MAX_WIDTH, width - 12);
        panelHeight = Math.min(MAX_HEIGHT, height - 8);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        int sidebar = Math.max(92, panelWidth / 4);
        int availableW = panelWidth - sidebar - 30;
        int availableH = panelHeight - 50;
        int scale = Math.max(1, Math.min(availableW / FleshingSurface.W, availableH / FleshingSurface.H));
        if (availableW >= FleshingSurface.W * 3 && availableH >= FleshingSurface.H * 3) scale = Math.max(scale, 3);
        areaW = FleshingSurface.W * scale;
        areaH = FleshingSurface.H * scale;
        if (areaW > availableW || areaH > availableH) {
            float fit = Math.min(availableW / (float) FleshingSurface.W, availableH / (float) FleshingSurface.H);
            areaW = Math.round(FleshingSurface.W * fit);
            areaH = Math.round(FleshingSurface.H * fit);
        }
        areaX = left + 12;
        areaY = top + 28 + (availableH - areaH) / 2;
        int sx = areaX + areaW + 12;
        int sw = left + panelWidth - 12 - sx;
        finishButton = TanningGui.button(sx, top + panelHeight - 50, sw, 18, Component.translatable("screen.immersive_tanning.fleshing.finish"), b -> finishEarly());
        doneButton = TanningGui.button(sx, top + panelHeight - 28, sw, 18, Component.translatable("gui.done"), b -> onClose());
        nextButton = TanningGui.button(sx, top + panelHeight - 50, sw, 18, Component.translatable("screen.immersive_tanning.fleshing.next_hide"), b -> nextHide());
        addRenderableWidget(finishButton);
        addRenderableWidget(doneButton);
        addRenderableWidget(nextButton);
        updateButtons();
    }

    private void updateButtons() {
        if (finishButton == null) return;
        finishButton.visible = stage == Stage.PLAYING;
        finishButton.active = stage == Stage.PLAYING && surface.coverage() > 0F;
        doneButton.visible = stage == Stage.RESULT;
        int hides = rawHidesCarried();
        nextButton.visible = stage == Stage.RESULT && hides > 0;
        nextButton.setMessage(Component.translatable("screen.immersive_tanning.fleshing.next_hide", hides));
    }

    /** Raw hides in the main inventory, which the "next hide" button can mount. */
    private int rawHidesCarried() {
        net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return 0;
        int n = 0;
        for (net.minecraft.world.item.ItemStack stack : player.getInventory().items) {
            if (com.otectus.immersivetanning.hide.HideItems.isMountable(player.level(), stack)) n += stack.getCount();
        }
        return n;
    }

    private void nextHide() {
        if (stage != Stage.RESULT || rawHidesCarried() == 0) return;
        ModNetwork.CHANNEL.sendToServer(new com.otectus.immersivetanning.network.packet.FleshNextPacket(session.pos()));
        nextButton.active = false;
    }

    private int elapsed() {
        return (int) (Util.getMillis() - clientStartMs);
    }

    // ------------------------------------------------------------------ server events

    public void onResult(SessionResultPacket packet) {
        if (packet.sessionId() != session.sessionId()) return;
        result = packet;
        stage = Stage.RESULT;
        pressing = false;
        updateButtons();
        TanningGui.playUi(ModSounds.RACK_MOUNT.get(), 1.2F, 0.8F);
    }

    public void closeByServer() {
        closedByServer = true;
        onClose();
    }

    // ------------------------------------------------------------------ input

    private int[] toQuantized(double mouseX, double mouseY) {
        float nx = (float) ((mouseX - areaX) / areaW);
        float ny = (float) ((mouseY - areaY) / areaH);
        return new int[]{FleshingSurface.quantize(nx), FleshingSurface.quantize(ny)};
    }

    private boolean inArea(double mouseX, double mouseY) {
        return mouseX >= areaX && mouseX < areaX + areaW && mouseY >= areaY && mouseY < areaY + areaH;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (stage == Stage.PLAYING && button == 0 && inArea(mouseX, mouseY) && !timeUp()) {
            int[] q = toQuantized(mouseX, mouseY);
            flush(false);
            pressing = true;
            pendingBegin = true;
            pending.add(q);
            surface.begin(q[0], q[1]);
            lastQX = q[0];
            lastQY = q[1];
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (pressing && stage == Stage.PLAYING && button == 0) {
            if (timeUp()) {
                release();
                return true;
            }
            int[] q = toQuantized(mouseX, mouseY);
            float dx = (q[0] - lastQX) / (float) FleshingSurface.COORD_MAX * FleshingSurface.W;
            float dy = (q[1] - lastQY) / (float) FleshingSurface.COORD_MAX * FleshingSurface.H;
            if (dx * dx + dy * dy >= MIN_STEP_CELLS * MIN_STEP_CELLS) {
                float before = surface.coverage();
                surface.drag(q[0], q[1]);
                pending.add(q);
                lastQX = q[0];
                lastQY = q[1];
                if (surface.coverage() > before && Util.getMillis() - lastScrapeSound > 140) {
                    lastScrapeSound = Util.getMillis();
                    TanningGui.playUi(ModSounds.RACK_SCRAPE.get(), 0.9F + (float) Math.random() * 0.25F, 0.45F);
                }
                if (pending.size() >= FleshStrokePacket.MAX_POINTS) flush(false);
                if (surface.isComplete()) {
                    // Declare the hide done so the server finishes at once with its own replayed score.
                    release();
                    ModNetwork.CHANNEL.sendToServer(new FinishFleshingPacket(session.sessionId()));
                    stage = Stage.WAITING;
                    updateButtons();
                }
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (pressing && button == 0) {
            release();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void release() {
        if (!pressing) return;
        pressing = false;
        surface.end();
        pendingEnd = true;
        flush(true);
    }

    private void flush(boolean force) {
        if (pending.isEmpty() && !pendingEnd) return;
        if (!force && Util.getMillis() - lastFlushMs < FLUSH_MS && pending.size() < FleshStrokePacket.MAX_POINTS) return;
        short[] xs = new short[pending.size()];
        short[] ys = new short[pending.size()];
        for (int i = 0; i < pending.size(); i++) {
            xs[i] = (short) pending.get(i)[0];
            ys[i] = (short) pending.get(i)[1];
        }
        ModNetwork.CHANNEL.sendToServer(new FleshStrokePacket(session.sessionId(), ++sequence, pendingBegin, pendingEnd, Math.max(0, elapsed()), xs, ys));
        pending.clear();
        pendingBegin = false;
        pendingEnd = false;
        lastFlushMs = Util.getMillis();
    }

    private void finishEarly() {
        if (stage != Stage.PLAYING) return;
        release();
        flush(true);
        ModNetwork.CHANNEL.sendToServer(new FinishFleshingPacket(session.sessionId()));
        stage = Stage.WAITING;
        updateButtons();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && stage == Stage.PLAYING && surface.coverage() > 0F) {
            finishEarly();
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && stage == Stage.RESULT && rawHidesCarried() > 0) {
            nextHide();
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE) && stage == Stage.RESULT) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private boolean timeUp() {
        return elapsed() > session.allowedMs();
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void tick() {
        if (stage == Stage.READY && elapsed() >= 0) {
            stage = Stage.PLAYING;
            updateButtons();
        }
        if (stage == Stage.PLAYING && timeUp()) {
            release();
            stage = Stage.WAITING;
            updateButtons();
        }
        if (finishButton != null) finishButton.active = stage == Stage.PLAYING && surface.coverage() > 0F;
        flush(false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (stage != Stage.RESULT && !closedByServer) ModNetwork.CHANNEL.sendToServer(new CancelSessionPacket(session.sessionId()));
        Minecraft.getInstance().getTextureManager().release(textureId);
        super.removed();
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        TanningGui.panel(g, left, top, panelWidth, panelHeight);
        Component title = Component.translatable("screen.immersive_tanning.fleshing.title", session.size().displayName());
        g.drawString(font, TanningGui.clipped(font, title, panelWidth - 24), left + 12, top + 10, TanningGui.TEXT_WARM, false);
        TanningGui.rule(g, left + 10, top + 21, panelWidth - 20);

        TanningGui.well(g, areaX - 3, areaY - 3, areaW + 6, areaH + 6);
        g.fillGradient(areaX, areaY, areaX + areaW, areaY + areaH, 0xFF4A3322, 0xFF2E1F14);
        // The beam under the hide.
        g.fillGradient(areaX, areaY + areaH / 2 - areaH / 10, areaX + areaW, areaY + areaH / 2 + areaH / 10, 0x405A3C26, 0x20000000);
        refreshTexture();
        g.blit(textureId, areaX, areaY, areaW, areaH, 0, 0, FleshingSurface.W, FleshingSurface.H, FleshingSurface.W, FleshingSurface.H);

        switch (stage) {
            case READY -> {
                g.fill(areaX, areaY, areaX + areaW, areaY + areaH, 0x90000000);
                int remaining = -elapsed();
                g.drawCenteredString(font, Component.translatable("screen.immersive_tanning.ready", Math.max(1, (remaining + 999) / 1000)),
                        areaX + areaW / 2, areaY + areaH / 2 - 16, TanningGui.TEXT_WARM);
                TanningGui.drawWrappedCentered(g, font, Component.translatable("screen.immersive_tanning.fleshing.instructions"),
                        areaX + areaW / 2, areaY + areaH / 2, areaW - 24, TanningGui.TEXT);
            }
            case PLAYING -> renderKnife(g, mouseX, mouseY);
            case WAITING -> {
                g.fill(areaX, areaY, areaX + areaW, areaY + areaH, 0x60000000);
                g.drawCenteredString(font, Component.translatable(timeUp() ? "screen.immersive_tanning.times_up" : "screen.immersive_tanning.finishing"),
                        areaX + areaW / 2, areaY + areaH / 2 - 4, TanningGui.TEXT);
            }
            case RESULT -> renderResult(g);
        }
        renderSidebar(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void refreshTexture() {
        if (uploadedVersion == surface.version()) return;
        uploadedVersion = surface.version();
        boolean grain = ClientConfig.get(ClientConfig.SHOW_GRAIN_HINTS);
        boolean contrast = ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES);
        for (int y = 0; y < FleshingSurface.H; y++) {
            for (int x = 0; x < FleshingSurface.W; x++) {
                if (!surface.isHide(x, y)) {
                    image.setPixelRGBA(x, y, 0);
                    continue;
                }
                int noise = ((x * 7349 + y * 1931 + (x * y) * 17) >> 2) & 7;
                boolean edge = !surface.isHide(Math.max(0, x - 1), y) || !surface.isHide(Math.min(FleshingSurface.W - 1, x + 1), y)
                        || !surface.isHide(x, Math.max(0, y - 1)) || !surface.isHide(x, Math.min(FleshingSurface.H - 1, y + 1));
                int base = edge ? 0xFFB39070 : 0xFFE0C8A2;
                int rgb = TanningGui.darken(base, 1F - noise * 0.012F);
                float t = surface.thickness(x, y);
                if (t > 0F) {
                    int flesh = contrast ? 0xFFD02020 : 0xFFB4545A;
                    int thin = contrast ? 0xFFF08080 : 0xFFD48E86;
                    rgb = lerpColor(thin, flesh, t);
                    int region = surface.region(x, y);
                    if (grain && region >= 0) {
                        float g = surface.grain(region);
                        float stripe = -(x + 0.5F) * Mth.sin(g) + (y + 0.5F) * Mth.cos(g);
                        if (Mth.positiveModulo(stripe, 3.4F) < 0.9F) rgb = TanningGui.darken(rgb, 0.82F);
                    }
                } else if (surface.region(x, y) >= 0) {
                    rgb = TanningGui.darken(0xFFEAD6B4, 1F - noise * 0.01F); // freshly scraped: a little paler
                }
                image.setPixelRGBA(x, y, toAbgr(rgb));
            }
        }
        texture.upload();
    }

    private static int lerpColor(int a, int b, float t) {
        t = Mth.clamp(t, 0F, 1F);
        int r = Math.round(Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255));
        int gr = Math.round(Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255));
        int bl = Math.round(Mth.lerp(t, a & 255, b & 255));
        return 0xFF000000 | (r << 16) | (gr << 8) | bl;
    }

    private static int toAbgr(int argb) {
        int a = (argb >>> 24) & 255;
        int r = (argb >> 16) & 255;
        int g = (argb >> 8) & 255;
        int b = argb & 255;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private void renderKnife(GuiGraphics g, int mouseX, int mouseY) {
        if (!inArea(mouseX, mouseY)) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        float radius = FleshingSurface.BLADE_RADIUS * areaW / FleshingSurface.W;
        TanningGui.ring(g, mouseX, mouseY, radius, pressing ? 0xC0FFF2D0 : 0x70FFF2D0);
        // A small tanner's knife: bone handle and a curved steel edge.
        g.fill(mouseX + 3, mouseY - 9, mouseX + 5, mouseY - 3, 0xFFE8DCC0);
        g.fill(mouseX - 4, mouseY - 2, mouseX + 7, mouseY, 0xFFB8C4C8);
        g.fill(mouseX - 3, mouseY, mouseX + 6, mouseY + 1, 0xFF6F7E86);
        g.pose().popPose();
    }

    private void renderResult(GuiGraphics g) {
        g.fill(areaX, areaY, areaX + areaW, areaY + areaH, 0xB0000000);
        int cx = areaX + areaW / 2;
        int y = areaY + areaH / 2 - 42;
        g.drawCenteredString(font, Component.translatable(result.timedOut() ? "screen.immersive_tanning.fleshing.times_up_result"
                : "screen.immersive_tanning.fleshing.complete"), cx, y, TanningGui.TEXT_WARM);
        y += 16;
        g.drawCenteredString(font, Component.translatable("screen.immersive_tanning.integrity", Scores.label(result.score())), cx, y,
                TanningGui.qualityColor(result.score()));
        y += 16;
        g.drawCenteredString(font, measure("coverage", result.coverage(), false), cx, y, TanningGui.TEXT);
        g.drawCenteredString(font, measure("control", result.control(), false), cx, y + 11, TanningGui.TEXT);
        g.drawCenteredString(font, measure("overwork", result.overwork(), true), cx, y + 22, TanningGui.TEXT);
        TanningGui.drawWrappedCentered(g, font, Component.translatable("screen.immersive_tanning.fleshing.next"), cx, y + 40, areaW - 24, TanningGui.TEXT_DIM);
    }

    /** "Coverage: 97%"-style lines; for overworking lower is better. */
    private Component measure(String key, float value, boolean lowerIsBetter) {
        int percent = Math.round(value * 100F);
        float good = lowerIsBetter ? 1F - value : value;
        String word = good >= 0.9F ? "excellent" : good >= 0.7F ? "good" : good >= 0.45F ? "fair" : "poor";
        return Component.translatable("screen.immersive_tanning.fleshing." + key, percent,
                Component.translatable("screen.immersive_tanning.measure." + word));
    }

    private void renderSidebar(GuiGraphics g) {
        int sx = areaX + areaW + 12;
        int sw = left + panelWidth - 12 - sx;
        int y = top + 30;
        g.drawString(font, TanningGui.clipped(font, Component.translatable("screen.immersive_tanning.fleshing.step"), sw), sx, y, TanningGui.TEXT_WARM, false);
        y += 14;
        TanningGui.well(g, sx, y, sw, 36);
        g.drawString(font, Component.translatable("screen.immersive_tanning.time"), sx + 5, y + 5, TanningGui.TEXT_DIM, false);
        int now = stage == Stage.READY ? 0 : Math.max(0, elapsed());
        Component seconds = Component.translatable("screen.immersive_tanning.seconds_short", Math.max(0, (session.allowedMs() - now + 999) / 1000));
        g.drawString(font, seconds, sx + sw - 5 - font.width(seconds), y + 5, TanningGui.TEXT, false);
        TanningGui.timeBar(g, sx + 5, y + 22, sw - 10, 5, stage == Stage.RESULT ? 0F : 1F - now / (float) Math.max(1, session.allowedMs()));
        y += 44;
        TanningGui.well(g, sx, y, sw, 36);
        g.drawString(font, Component.translatable("screen.immersive_tanning.fleshing.cleaned"), sx + 5, y + 5, TanningGui.TEXT_DIM, false);
        float coverage = result != null ? result.coverage() : surface.coverage();
        Component percent = Component.literal(Math.round(coverage * 100F) + "%");
        g.drawString(font, percent, sx + sw - 5 - font.width(percent), y + 5, TanningGui.TEXT, false);
        TanningGui.bar(g, sx + 5, y + 22, sw - 10, 5, coverage, TanningGui.goodColor());
        y += 44;
        if (session.trait() != null) {
            y += 10 * TanningGui.drawWrapped(g, font, Component.translatable("tooltip.immersive_tanning.trait", session.trait().displayName()),
                    sx, y, sw, TanningGui.TEXT) + 4;
        }
        if (stage == Stage.PLAYING || stage == Stage.READY) {
            TanningGui.drawWrapped(g, font, Component.translatable("screen.immersive_tanning.fleshing.hint"), sx, y, sw, TanningGui.TEXT_DIM);
        }
    }
}
