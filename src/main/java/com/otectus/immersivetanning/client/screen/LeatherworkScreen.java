package com.otectus.immersivetanning.client.screen;

import com.otectus.immersivetanning.config.ClientConfig;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.leather.TanningQuality;
import com.otectus.immersivetanning.minigame.PressGate;
import com.otectus.immersivetanning.minigame.StitchPattern;
import com.otectus.immersivetanning.minigame.StitchRun;
import com.otectus.immersivetanning.network.ModNetwork;
import com.otectus.immersivetanning.network.packet.CancelSessionPacket;
import com.otectus.immersivetanning.network.packet.OpenBenchPacket;
import com.otectus.immersivetanning.network.packet.SelectBenchRecipePacket;
import com.otectus.immersivetanning.network.packet.SessionResultPacket;
import com.otectus.immersivetanning.network.packet.StitchPressPacket;
import com.otectus.immersivetanning.network.packet.StitchResultPacket;
import com.otectus.immersivetanning.network.packet.StitchStartPacket;
import com.otectus.immersivetanning.registry.ModSounds;
import com.otectus.immersivetanning.trait.TraitRef;
import com.otectus.immersivetanning.util.Scores;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The Leatherworker's Bench. First choose a piece and the leather to cut it from; the panels are then cut for you
 * (presentation only) and the seam is stitched: a needle runs along the seam and the awl is pressed (click or Space)
 * as it reaches each stitch hole. Misses leave uneven stitches but never abort the piece. Presses are predicted
 * locally and corrected by the server, which alone decides the Workmanship.
 *
 * <p>One physical action is one press ({@link PressGate}): Space's auto-repeat and a click landing with Space are not
 * extra presses. A press too early to count says so ("Too early") instead of vanishing, and the approach ring shows
 * when a press will count: faint while it would be ignored, then bright with the hole lit once it scores.</p>
 */
public class LeatherworkScreen extends WorkshopScreen {
    private enum Stage { SELECT, WAITING, READY, PLAYING, RESULT }

    private static final int MAX_WIDTH = 384;
    private static final int MAX_HEIGHT = 248;
    private static final int ROW_HEIGHT = 26;
    private static final int ROW_INNER = 24;
    /** Space or Enter on the result only closes it once it has been up this long, and only as a fresh press. */
    private static final long RESULT_KEY_GRACE_MS = 600;

    private final OpenBenchPacket offer;
    private Stage stage = Stage.SELECT;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int scroll;
    private boolean draggingScrollbar;
    private Button doneButton;

    private StitchStartPacket start;
    private StitchRun run;
    private float[][] stitchPoints;
    private int[] stitchSeams;
    private float[] stitchArc;
    private long clientStartMs;
    private int sequence;
    private int cuedIndex = -1;
    private float lastAccuracy = -1F;
    private long lastFeedbackMs;
    private long earlyMs = Long.MIN_VALUE;
    private final PressGate gate = new PressGate();
    private SessionResultPacket result;
    private long resultShownMs;
    private boolean closedByServer;

    public LeatherworkScreen(OpenBenchPacket offer) {
        super(Component.translatable("screen.immersive_tanning.bench"), 320, 240);
        this.offer = offer;
    }

    public int sessionId() {
        return offer.sessionId();
    }

    /** The local prediction of the run (null before the server starts it). For the client smoke test. */
    public StitchRun run() {
        return run;
    }

    /** Whether the "Too early" notice for an ignored press is showing. */
    public boolean tooEarlyShown() {
        return earlyMs > lastFeedbackMs && Util.getMillis() - earlyMs < ClientConfig.get(ClientConfig.FEEDBACK_LABEL_MS);
    }

    @Override
    protected void initPanel() {
        panelWidth = Math.min(MAX_WIDTH, canvasWidth - 12);
        panelHeight = Math.min(MAX_HEIGHT, canvasHeight - 4);
        left = (canvasWidth - panelWidth) / 2;
        top = (canvasHeight - panelHeight) / 2;
        scroll = Mth.clamp(scroll, 0, maxScroll());
        doneButton = VanillaGui.button(sidebarX(), top + panelHeight - 28, sidebarWidth(), 20, Component.translatable("gui.done"), b -> onClose());
        addRenderableWidget(doneButton);
        doneButton.visible = stage == Stage.RESULT;
    }

    private int elapsed() {
        return (int) (Util.getMillis() - clientStartMs);
    }

    // ------------------------------------------------------------------ area layout

    private int areaX() {
        return left + 10;
    }

    private int areaY() {
        return top + 20;
    }

    private int areaW() {
        return Math.max(120, panelWidth - 28 - sidebarWidth());
    }

    private int areaH() {
        return panelHeight - 30;
    }

    private int sidebarWidth() {
        return Math.max(90, panelWidth / 4);
    }

    private int sidebarX() {
        return areaX() + areaW() + 10;
    }

    private float sx(float nx) {
        int size = Math.min(areaW(), areaH());
        return areaX() + (areaW() - size) / 2F + nx * size;
    }

    private float sy(float ny) {
        int size = Math.min(areaW(), areaH());
        return areaY() + (areaH() - size) / 2F + ny * size;
    }

    // ------------------------------------------------------------------ server events

    public void onStart(StitchStartPacket packet) {
        if (packet.sessionId() != offer.sessionId()) return;
        start = packet;
        run = new StitchRun(packet.pattern());
        StitchPattern pattern = packet.pattern();
        stitchSeams = pattern.stitchSeams();
        stitchArc = pattern.stitchArc();
        stitchPoints = new float[run.count()][];
        for (int i = 0; i < run.count(); i++) stitchPoints[i] = StitchPattern.pointAt(pattern.seams().get(stitchSeams[i]), stitchArc[i]);
        clientStartMs = Util.getMillis() + packet.readyDelayMs();
        stage = Stage.READY;
        TanningGui.playUi(ModSounds.BENCH_CUT.get(), 1.0F, 0.8F);
    }

    public void onStitchResult(StitchResultPacket packet) {
        if (packet.sessionId() != offer.sessionId() || run == null) return;
        run.correct(packet.index(), packet.accuracy(), packet.next());
    }

    public void onResult(SessionResultPacket packet) {
        if (packet.sessionId() != offer.sessionId()) return;
        result = packet;
        resultShownMs = Util.getMillis();
        stage = Stage.RESULT;
        doneButton.visible = true;
        TanningGui.playUi(ModSounds.BENCH_FINISH.get(), 1.0F, 0.9F);
    }

    public void closeByServer() {
        closedByServer = true;
        onClose();
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean panelClicked(double mouseX, double mouseY, int button) {
        if (stage == Stage.SELECT && button == 0) {
            if (maxScroll() > 0 && VanillaGui.onScrollbar(mouseX, mouseY, scrollbarX(), listTop(), listHeight())) {
                draggingScrollbar = true;
                scrollFromMouse(mouseY);
                return true;
            }
            int row = rowAt(mouseX, mouseY);
            if (row >= 0) {
                OpenBenchPacket.Entry e = offer.entries().get(row);
                ModNetwork.CHANNEL.sendToServer(new SelectBenchRecipePacket(offer.sessionId(), e.recipe(), e.finish(),
                        e.trait() == null ? null : e.trait().id()));
                stage = Stage.WAITING;
                return true;
            }
        }
        if (stage == Stage.PLAYING && button == 0 && mouseX >= areaX() && mouseX < areaX() + areaW()
                && mouseY >= areaY() && mouseY < areaY() + areaH()) {
            if (gate.click(Util.getMillis())) press();
            return true;
        }
        return super.panelClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        long wall = Util.getMillis();
        if (key == GLFW.GLFW_KEY_SPACE && stage == Stage.PLAYING) {
            if (gate.keyDown(key, wall)) press();
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE) && stage == Stage.RESULT) {
            // A key still held from the last stitch (or its auto-repeat) must not close the result before it is read.
            boolean fresh = !gate.isHeld(key);
            gate.keyDown(key, wall);
            if (fresh && wall - resultShownMs >= RESULT_KEY_GRACE_MS) onClose();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        gate.keyUp(key);
        return super.keyReleased(key, scanCode, modifiers);
    }

    @Override
    protected boolean panelScrolled(double mouseX, double mouseY, double delta) {
        if (stage == Stage.SELECT) {
            scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, maxScroll());
            return true;
        }
        return super.panelScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected boolean panelDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (stage == Stage.SELECT && button == 0 && draggingScrollbar) {
            scrollFromMouse(mouseY);
            return true;
        }
        return super.panelDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean panelReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.panelReleased(mouseX, mouseY, button);
    }

    private void press() {
        int now = elapsed();
        if (now < 0 || run.isComplete()) return;
        StitchRun.Press press = run.press(now);
        int seq = ++sequence;
        ModNetwork.CHANNEL.sendToServer(new StitchPressPacket(offer.sessionId(), seq, now));
        if (press.outcome() == StitchRun.Outcome.IGNORED) {
            // Too early to count: nothing is scored or used up, and the player is told so instead of pressing into silence.
            earlyMs = Util.getMillis();
            if (ClientConfig.get(ClientConfig.TIMING_CUE_SOUNDS)) TanningGui.playUi(SoundEvents.NOTE_BLOCK_HAT.value(), 0.7F, 0.25F);
            return;
        }
        lastAccuracy = press.accuracy();
        lastFeedbackMs = Util.getMillis();
        boolean hit = press.outcome() == StitchRun.Outcome.HIT;
        TanningGui.playUi(ModSounds.BENCH_STITCH.get(), hit ? 0.8F + press.accuracy() * 0.4F : 0.6F, hit ? 0.9F : 0.5F);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void tick() {
        if (stage == Stage.READY && elapsed() >= 0) stage = Stage.PLAYING;
        if (stage == Stage.PLAYING && run != null) run.advanceTo(elapsed());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (stage != Stage.RESULT && !closedByServer) ModNetwork.CHANNEL.sendToServer(new CancelSessionPacket(offer.sessionId()));
        super.removed();
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        VanillaGui.panel(g, left, top, panelWidth, panelHeight);
        Component title = start == null ? Component.translatable("screen.immersive_tanning.bench.select")
                : Component.translatable("screen.immersive_tanning.bench.title", start.finish().displayName(), start.result().getHoverName());
        int titleWidth = panelWidth - 16;
        g.drawString(font, VanillaGui.clipped(font, title, titleWidth), left + 8, top + 7, VanillaGui.LABEL, false);
        switch (stage) {
            case SELECT -> renderSelect(g, mouseX, mouseY);
            case WAITING -> VanillaGui.centered(g, font, Component.translatable("screen.immersive_tanning.bench.cutting"),
                    left + panelWidth / 2, top + panelHeight / 2 - 4, VanillaGui.LABEL);
            case READY, PLAYING, RESULT -> renderStitching(g);
        }
        super.renderPanel(g, mouseX, mouseY, partialTick);
        if (VanillaGui.isClipped(font, title, titleWidth) && mouseX >= left + 8 && mouseX < left + 8 + titleWidth
                && mouseY >= top + 5 && mouseY < top + 17) {
            tooltip(title);
        }
    }

    // --- selection

    private int listX() {
        return left + 8;
    }

    private int listTop() {
        return top + 20;
    }

    private int listWidth() {
        return panelWidth - 16 - 16;
    }

    private int scrollbarX() {
        return listX() + listWidth() + 2;
    }

    private int visibleRows() {
        return Math.max(1, (listHeight() + ROW_HEIGHT - ROW_INNER) / ROW_HEIGHT);
    }

    /** The list's frame runs down to the hint line; whole rows fill it from the top. */
    private int listHeight() {
        return panelHeight - 44;
    }

    private int maxScroll() {
        return Math.max(0, offer.entries().size() - visibleRows());
    }

    private void scrollFromMouse(double mouseY) {
        float ratio = (float) ((mouseY - listTop() - 7) / Math.max(1, listHeight() - 15));
        scroll = Mth.clamp(Math.round(ratio * maxScroll()), 0, maxScroll());
    }

    private int rowAt(double mouseX, double mouseY) {
        int x = listX();
        int y = listTop();
        int w = listWidth();
        if (mouseX < x || mouseX >= x + w || mouseY < y) return -1;
        int row = (int) ((mouseY - y) / ROW_HEIGHT);
        if (row >= visibleRows()) return -1;
        int index = scroll + row;
        return index < offer.entries().size() ? index : -1;
    }

    private void renderSelect(GuiGraphics g, int mouseX, int mouseY) {
        int x = listX();
        int y = listTop();
        int w = listWidth();
        int listHeight = listHeight();
        List<OpenBenchPacket.Entry> entries = offer.entries();
        VanillaGui.inset(g, x - 1, y - 1, w + 2, listHeight + 1);
        int hovered = rowAt(mouseX, mouseY);
        int rows = Math.min(visibleRows(), entries.size() - scroll);
        scissor(g, x, y, x + w, y + listHeight);
        for (int r = 0; r < rows; r++) {
            int index = scroll + r;
            OpenBenchPacket.Entry e = entries.get(index);
            int ry = y + r * ROW_HEIGHT;
            VanillaGui.row(g, x, ry, w, ROW_INNER, true, index == hovered);
            g.renderItem(e.result(), x + 4, ry + 4);
            MutableComponent name = Component.translatable("screen.immersive_tanning.bench.entry", e.finish().displayName(), e.result().getHoverName());
            if (e.trait() != null) name = Component.translatable("screen.immersive_tanning.bench.entry_trait", name, e.trait().displayName());
            int textW = w - 30 - auxWidth(e);
            g.drawString(font, VanillaGui.clipped(font, name, textW), x + 24, ry + 3, VanillaGui.ROW_TEXT, true);
            Component detail = Component.translatable("screen.immersive_tanning.bench.detail", e.leather(), Scores.label(e.integrity()));
            g.drawString(font, VanillaGui.clipped(font, detail, textW), x + 24, ry + 13, VanillaGui.ROW_TEXT_DIM, true);
            int ax = x + w - 4 - auxWidth(e);
            for (ItemStack aux : e.auxiliary().stream().limit(4).toList()) {
                g.renderItem(aux, ax, ry + 4);
                g.renderItemDecorations(font, aux, ax, ry + 4);
                ax += 17;
            }
        }
        g.disableScissor();
        VanillaGui.scrollbar(g, scrollbarX(), y - 1, listHeight + 1, maxScroll() == 0 ? 0F : scroll / (float) maxScroll(), maxScroll() > 0);
        Component choose = Component.translatable("screen.immersive_tanning.bench.choose");
        VanillaGui.centered(g, font, Component.literal(VanillaGui.clipped(font, choose, panelWidth - 16)), left + panelWidth / 2,
                top + panelHeight - 14, VanillaGui.LABEL_DIM);
        if (hovered >= 0) {
            OpenBenchPacket.Entry e = entries.get(hovered);
            if (mouseX >= x + 4 && mouseX < x + 20) tooltip(e.result());
            else {
                List<Component> details = new java.util.ArrayList<>();
                details.add(Component.translatable("screen.immersive_tanning.bench.entry", e.finish().displayName(), e.result().getHoverName()));
                if (e.trait() != null) details.add(Component.translatable("tooltip.immersive_tanning.trait", e.trait().displayName()));
                for (ItemStack aux : e.auxiliary()) details.add(Component.translatable("screen.immersive_tanning.bench.component", aux.getCount(), aux.getHoverName()));
                tooltip(details);
            }
        }
    }

    private int auxWidth(OpenBenchPacket.Entry e) {
        return Math.min(4, e.auxiliary().size()) * 17;
    }

    // --- stitching

    private void renderStitching(GuiGraphics g) {
        int ax = areaX();
        int ay = areaY();
        int aw = areaW();
        int ah = areaH();
        VanillaGui.display(g, ax - 2, ay - 2, aw + 4, ah + 4);
        StitchPattern pattern = start.pattern();
        Finish finish = start.finish();
        int leather = 0xFF000000 | finish.leatherColor();
        int now = stage == Stage.RESULT ? Integer.MAX_VALUE / 2 : elapsed();

        // Panels, cut out of the leather during the countdown.
        float cut = stage == Stage.READY ? Mth.clamp(1F + now / (float) Math.max(1, start.readyDelayMs()), 0F, 1F) : 1F;
        for (float[] panel : pattern.panels()) {
            float[] pts = new float[panel.length];
            for (int i = 0; i < panel.length; i += 2) {
                pts[i] = sx(panel[i]);
                pts[i + 1] = sy(panel[i + 1]);
            }
            TanningGui.polygon(g, pts, TanningGui.withAlpha(leather, cut >= 1F ? 255 : 90));
            int edges = pts.length / 2;
            float drawn = cut * edges;
            for (int i = 0; i < edges; i++) {
                if (i >= drawn) break;
                float part = Math.min(1F, drawn - i);
                float x0 = pts[i * 2], y0 = pts[i * 2 + 1];
                float x1 = pts[((i + 1) % edges) * 2], y1 = pts[((i + 1) % edges) * 2 + 1];
                TanningGui.line(g, x0, y0, Mth.lerp(part, x0, x1), Mth.lerp(part, y0, y1), 1, 1, 1, TanningGui.darken(leather, 0.55F));
                if (stage == Stage.READY && part < 1F) drawKnife(g, Mth.lerp(part, x0, x1), Mth.lerp(part, y0, y1));
            }
        }
        // Seam guides.
        for (float[] seam : pattern.seams()) {
            for (int i = 2; i + 1 < seam.length; i += 2) {
                TanningGui.line(g, sx(seam[i - 2]), sy(seam[i - 1]), sx(seam[i]), sy(seam[i + 1]), 1, 4, 2, TanningGui.withAlpha(0xE8D6B0, 110));
            }
        }
        if (stage == Stage.READY) {
            g.fill(ax, ay, ax + aw, ay + ah, 0x50000000);
            VanillaGui.centered(g, font, Component.translatable("screen.immersive_tanning.ready", Math.max(1, (-now + 999) / 1000)),
                    ax + aw / 2, ay + ah / 2 - 16, VanillaGui.DISPLAY_HEADING);
            VanillaGui.wrappedCentered(g, font, Component.translatable("screen.immersive_tanning.bench.instructions"), ax + aw / 2, ay + ah / 2,
                    aw - 16, VanillaGui.DISPLAY_TEXT, false);
        } else {
            renderStitches(g, now);
            if (stage == Stage.PLAYING) renderNeedle(g, now);
        }
        if (stage == Stage.RESULT) renderResult(g);
        renderSidebar(g, now);
    }

    private void renderStitches(GuiGraphics g, int now) {
        StitchPattern pattern = start.pattern();
        float scale = TanningGui.markerScale();
        for (int i = 0; i < run.count(); i++) {
            float px = sx(stitchPoints[i][0]);
            float py = sy(stitchPoints[i][1]);
            float accuracy = run.accuracy(i);
            if (Float.isNaN(accuracy)) {
                TanningGui.disc(g, px, py, 1.3F * scale, 0xFF1A100A);
                if (i == run.next() && stage == Stage.PLAYING) {
                    int until = run.time(i) - now;
                    int window = pattern.windowMs();
                    if (until > -window && until < pattern.beatMs() * 1.5F) {
                        float approach = Mth.clamp(until / (float) (pattern.beatMs() * 1.5F), 0F, 1F);
                        float radius = (3F + approach * 9F) * scale;
                        // Faint while a press would be ignored, solid once it would be taken, bright with the hole lit
                        // while it scores: the same thresholds StitchRun judges by.
                        boolean scores = until <= window;
                        int ring = scores ? TanningGui.perfectColor() : until <= 2 * window ? 0xB0E8D6B0 : 0x50E8D6B0;
                        TanningGui.ring(g, px, py, radius, ring);
                        if (scores) TanningGui.disc(g, px, py, 1.9F * scale, TanningGui.perfectColor());
                    }
                    if (until <= 0 && cuedIndex != i) {
                        cuedIndex = i;
                        TanningGui.playCue();
                    }
                }
            } else {
                // A stitch: a short diagonal of thread, straight when accurate and skewed when rough.
                float angle = StitchPattern.angleAt(pattern.seams().get(stitchSeams[i]), stitchArc[i]) + Mth.HALF_PI * 0.6F;
                float skew = (1F - accuracy) * 0.9F * ((i % 2 == 0) ? 1F : -1F);
                float len = 4F * scale;
                float dx = Mth.cos(angle + skew) * len;
                float dy = Mth.sin(angle + skew) * len;
                int color = accuracy <= 0F ? TanningGui.missColor() : accuracy >= 0.9F ? TanningGui.STITCH : TanningGui.ratingColor(accuracy);
                TanningGui.line(g, px - dx, py - dy, px + dx, py + dy, accuracy <= 0F ? 1 : 2, 1, 1, color);
            }
        }
    }

    /** The needle's position along the seams at a session time. */
    private float[] needleAt(int now) {
        StitchPattern pattern = start.pattern();
        int count = run.count();
        if (now <= run.time(0)) {
            float t = Mth.clamp(now / (float) Math.max(1, run.time(0)), 0F, 1F);
            return StitchPattern.pointAt(pattern.seams().get(stitchSeams[0]), stitchArc[0] * t);
        }
        for (int k = 0; k + 1 < count; k++) {
            if (now < run.time(k + 1)) {
                float t = (now - run.time(k)) / (float) Math.max(1, run.time(k + 1) - run.time(k));
                if (stitchSeams[k] == stitchSeams[k + 1]) {
                    return StitchPattern.pointAt(pattern.seams().get(stitchSeams[k]), Mth.lerp(t, stitchArc[k], stitchArc[k + 1]));
                }
                if (t < 0.4F) return StitchPattern.pointAt(pattern.seams().get(stitchSeams[k]), Mth.lerp(t / 0.4F, stitchArc[k], 1F));
                return StitchPattern.pointAt(pattern.seams().get(stitchSeams[k + 1]), stitchArc[k + 1] * (t - 0.4F) / 0.6F);
            }
        }
        float t = Mth.clamp((now - run.time(count - 1)) / (float) pattern.beatMs(), 0F, 1F);
        return StitchPattern.pointAt(pattern.seams().get(stitchSeams[count - 1]), Mth.lerp(t, stitchArc[count - 1], 1F));
    }

    private void renderNeedle(GuiGraphics g, int now) {
        float[] p = needleAt(now);
        float px = sx(p[0]);
        float py = sy(p[1]);
        float scale = TanningGui.markerScale();
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        TanningGui.disc(g, px, py, 2.6F * scale, 0xFF101010);
        TanningGui.disc(g, px, py, 1.6F * scale, 0xFFD8DEE2);
        TanningGui.line(g, px, py, px + 6 * scale, py - 7 * scale, 1, 1, 1, 0xFFD8DEE2);
        g.pose().popPose();
    }

    private void drawKnife(GuiGraphics g, float x, float y) {
        int px = Math.round(x);
        int py = Math.round(y);
        g.fill(px - 1, py - 7, px + 1, py - 1, 0xFFE8DCC0);
        g.fill(px - 3, py - 1, px + 4, py + 1, 0xFFB8C4C8);
    }

    private void renderResult(GuiGraphics g) {
        int ax = areaX();
        int ay = areaY();
        int aw = areaW();
        int ah = areaH();
        g.fill(ax, ay, ax + aw, ay + ah, 0xB0000000);
        int cx = ax + aw / 2;
        int y = ay + ah / 2 - 44;
        g.pose().pushPose();
        g.pose().translate(cx - 16, y, 0);
        g.pose().scale(2F, 2F, 1F);
        g.renderItem(start.result(), 0, 0);
        g.pose().popPose();
        y += 38;
        int overall = TanningQuality.overallScore(start.integrity(), result.score());
        Component name = Component.translatable("tooltip.immersive_tanning.named", TanningQuality.fromScore(overall).displayName(),
                start.finish().displayName(), start.result().getHoverName());
        y += 10 * VanillaGui.wrappedCentered(g, font, name, cx, y, aw - 16, VanillaGui.DISPLAY_TEXT, false);
        y += 4;
        VanillaGui.centered(g, font, Component.translatable("screen.immersive_tanning.workmanship", Scores.label(result.score())), cx, y, TanningGui.qualityColor(result.score()));
        VanillaGui.centered(g, font, Component.translatable("screen.immersive_tanning.integrity", Scores.label(start.integrity())), cx, y + 11, TanningGui.qualityColor(start.integrity()));
        VanillaGui.wrappedCentered(g, font, Component.translatable("screen.immersive_tanning.bench.next"), cx, y + 28, aw - 16, VanillaGui.DISPLAY_TEXT_DIM, false);
    }

    private void renderSidebar(GuiGraphics g, int now) {
        int sx = sidebarX();
        int sw = sidebarWidth();
        int y = top + 20;
        g.drawString(font, VanillaGui.clipped(font, Component.translatable("screen.immersive_tanning.bench.step"), sw), sx, y, VanillaGui.LABEL, false);
        y += 16;
        int done = stage == Stage.RESULT ? run.count() : run.next();
        g.drawString(font, Component.translatable("screen.immersive_tanning.bench.stitches"), sx, y, VanillaGui.LABEL, false);
        Component count = Component.literal(done + "/" + run.count());
        g.drawString(font, count, sx + sw - font.width(count), y, VanillaGui.LABEL, false);
        TanningGui.bar(g, sx + 1, y + 12, sw - 2, 5, done / (float) run.count(), TanningGui.STITCH);
        y += 26;
        g.drawString(font, VanillaGui.clipped(font, Component.translatable("screen.immersive_tanning.workmanship_label"), sw), sx, y, VanillaGui.LABEL, false);
        float running = run.runningAccuracy();
        Component grade = stage == Stage.RESULT ? Scores.label(result.score())
                : running >= 0 ? TanningQuality.fromScore(Math.round(running * 100F)).displayName() : Component.literal("-");
        VanillaGui.display(g, sx, y + 11, sw, 18);
        VanillaGui.centered(g, font, Component.literal(VanillaGui.clipped(font, grade, sw - 8)), sx + sw / 2, y + 16, VanillaGui.DISPLAY_TEXT);
        y += 36;
        TraitRef trait = start.trait();
        if (trait != null) {
            y += VanillaGui.readout(g, font, Component.translatable("tooltip.immersive_tanning.trait", trait.displayName()), sx, y, sw, 2) + 6;
        }
        if (stage == Stage.PLAYING) {
            long wall = Util.getMillis();
            int labelMs = ClientConfig.get(ClientConfig.FEEDBACK_LABEL_MS);
            boolean early = tooEarlyShown();
            boolean feedback = !early && lastAccuracy >= 0 && wall - lastFeedbackMs < labelMs;
            Component text = early ? Component.translatable("screen.immersive_tanning.bench.too_early")
                    : feedback ? TanningGui.rating(lastAccuracy) : Component.translatable("screen.immersive_tanning.bench.press");
            int lines = font.split(text, sw - 8).size();
            VanillaGui.display(g, sx, y, sw, 8 + lines * 10);
            VanillaGui.wrappedCentered(g, font, text, sx + sw / 2, y + 5, sw - 8,
                    early ? VanillaGui.DISPLAY_HEADING : feedback ? TanningGui.ratingColor(lastAccuracy) : VanillaGui.DISPLAY_TEXT_DIM, false);
        }
    }
}
