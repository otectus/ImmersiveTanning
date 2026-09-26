package com.otectus.immersivetanning.client.screen;

import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.leather.TanningQuality;
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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The Leatherworker's Bench. First choose a piece and the leather to cut it from; the panels are then cut for you
 * (presentation only) and the seam is stitched: a needle runs along the seam and the awl is pressed (click or Space)
 * as it reaches each stitch hole. Misses leave uneven stitches but never abort the piece. Presses are predicted
 * locally and corrected by the server, which alone decides the Workmanship.
 */
public class LeatherworkScreen extends WorkshopScreen {
    private enum Stage { SELECT, WAITING, READY, PLAYING, RESULT }

    private static final int MAX_WIDTH = 384;
    private static final int MAX_HEIGHT = 256;
    private static final int ROW_HEIGHT = 24;

    private final OpenBenchPacket offer;
    private Stage stage = Stage.SELECT;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int scroll;
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
    private SessionResultPacket result;
    private boolean closedByServer;

    public LeatherworkScreen(OpenBenchPacket offer) {
        super(Component.translatable("screen.immersive_tanning.bench"), 400, 272);
        this.offer = offer;
    }

    public int sessionId() {
        return offer.sessionId();
    }

    @Override
    protected void initPanel() {
        panelWidth = Math.min(MAX_WIDTH, canvasWidth - 12);
        panelHeight = Math.min(MAX_HEIGHT, canvasHeight - 8);
        left = (canvasWidth - panelWidth) / 2;
        top = (canvasHeight - panelHeight) / 2;
        doneButton = TanningGui.button(left + panelWidth - 102, top + panelHeight - 28, 90, 20, Component.translatable("gui.done"), b -> onClose());
        addRenderableWidget(doneButton);
        doneButton.visible = stage == Stage.RESULT;
    }

    private int elapsed() {
        return (int) (Util.getMillis() - clientStartMs);
    }

    // ------------------------------------------------------------------ area layout

    private int areaX() {
        return left + 12;
    }

    private int areaY() {
        return top + 28;
    }

    private int areaW() {
        return Math.max(120, panelWidth - 24 - sidebarWidth() - 10);
    }

    private int areaH() {
        return panelHeight - 40;
    }

    private int sidebarWidth() {
        return Math.max(90, panelWidth / 4);
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
            press();
            return true;
        }
        return super.panelClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_SPACE && stage == Stage.PLAYING) {
            press();
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE) && stage == Stage.RESULT) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    protected boolean panelScrolled(double mouseX, double mouseY, double delta) {
        if (stage == Stage.SELECT) {
            scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, offer.entries().size() - visibleRows()));
            return true;
        }
        return super.panelScrolled(mouseX, mouseY, delta);
    }

    private void press() {
        int now = elapsed();
        if (now < 0 || run.isComplete()) return;
        StitchRun.Press press = run.press(now);
        int seq = ++sequence;
        ModNetwork.CHANNEL.sendToServer(new StitchPressPacket(offer.sessionId(), seq, now));
        if (press.outcome() == StitchRun.Outcome.IGNORED) return;
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
        TanningGui.panel(g, left, top, panelWidth, panelHeight);
        Component title = start == null ? Component.translatable("screen.immersive_tanning.bench.select")
                : Component.translatable("screen.immersive_tanning.bench.title", start.finish().displayName(), start.result().getHoverName());
        g.drawString(font, TanningGui.clipped(font, title, panelWidth - 24), left + 12, top + 10, TanningGui.TEXT_WARM, false);
        TanningGui.rule(g, left + 10, top + 21, panelWidth - 20);
        switch (stage) {
            case SELECT -> renderSelect(g, mouseX, mouseY);
            case WAITING -> g.drawCenteredString(font, Component.translatable("screen.immersive_tanning.bench.cutting"),
                    left + panelWidth / 2, top + panelHeight / 2, TanningGui.TEXT);
            case READY, PLAYING, RESULT -> renderStitching(g);
        }
        super.renderPanel(g, mouseX, mouseY, partialTick);
    }

    // --- selection

    private int visibleRows() {
        return Math.max(1, (panelHeight - 58) / ROW_HEIGHT);
    }

    private int rowAt(double mouseX, double mouseY) {
        int x = left + 12;
        int y = top + 30;
        int w = panelWidth - 24;
        if (mouseX < x || mouseX >= x + w || mouseY < y) return -1;
        int row = (int) ((mouseY - y) / ROW_HEIGHT);
        if (row >= visibleRows()) return -1;
        int index = scroll + row;
        return index < offer.entries().size() ? index : -1;
    }

    private void renderSelect(GuiGraphics g, int mouseX, int mouseY) {
        int x = left + 12;
        int y = top + 30;
        int w = panelWidth - 24;
        List<OpenBenchPacket.Entry> entries = offer.entries();
        int hovered = rowAt(mouseX, mouseY);
        int rows = Math.min(visibleRows(), entries.size() - scroll);
        for (int r = 0; r < rows; r++) {
            int index = scroll + r;
            OpenBenchPacket.Entry e = entries.get(index);
            int ry = y + r * ROW_HEIGHT;
            TanningGui.well(g, x, ry, w, ROW_HEIGHT - 2);
            if (index == hovered) g.fill(x + 1, ry + 1, x + w - 1, ry + ROW_HEIGHT - 3, StonebornGui.ROW_HOVER);
            g.fill(x + 1, ry + 1, x + 4, ry + ROW_HEIGHT - 3, 0xFF000000 | e.finish().leatherColor());
            g.renderItem(e.result(), x + 7, ry + 3);
            MutableComponent name = Component.translatable("screen.immersive_tanning.bench.entry", e.finish().displayName(), e.result().getHoverName());
            if (e.trait() != null) name = Component.translatable("screen.immersive_tanning.bench.entry_trait", name, e.trait().displayName());
            int textW = w - 36 - auxWidth(e);
            g.drawString(font, TanningGui.clipped(font, name, textW), x + 27, ry + 3, TanningGui.TEXT, false);
            Component detail = Component.translatable("screen.immersive_tanning.bench.detail", e.leather(), Scores.label(e.integrity()));
            g.drawString(font, TanningGui.clipped(font, detail, textW), x + 27, ry + 12, TanningGui.TEXT_DIM, false);
            int ax = x + w - 4 - auxWidth(e);
            for (ItemStack aux : e.auxiliary().stream().limit(4).toList()) {
                g.renderItem(aux, ax, ry + 3);
                g.renderItemDecorations(font, aux, ax, ry + 3);
                ax += 18;
            }
        }
        if (entries.size() > visibleRows()) {
            Component more = Component.translatable("screen.immersive_tanning.bench.scroll", scroll + 1, scroll + rows, entries.size());
            g.drawString(font, more, x, top + panelHeight - 18, TanningGui.TEXT_DIM, false);
        }
        g.drawString(font, Component.translatable("screen.immersive_tanning.bench.choose"), x + w - font.width(Component.translatable("screen.immersive_tanning.bench.choose")),
                top + panelHeight - 18, TanningGui.TEXT_DIM, false);
        if (hovered >= 0) {
            OpenBenchPacket.Entry e = entries.get(hovered);
            if (mouseX >= x + 7 && mouseX < x + 23) g.renderTooltip(font, e.result(), mouseX, mouseY);
            else {
                java.util.List<Component> details = new java.util.ArrayList<>();
                details.add(Component.translatable("screen.immersive_tanning.bench.entry", e.finish().displayName(), e.result().getHoverName()));
                if (e.trait() != null) details.add(Component.translatable("tooltip.immersive_tanning.trait", e.trait().displayName()));
                for (ItemStack aux : e.auxiliary()) details.add(Component.translatable("screen.immersive_tanning.bench.component", aux.getCount(), aux.getHoverName()));
                g.renderComponentTooltip(font, details, mouseX, mouseY);
            }
        }
    }

    private int auxWidth(OpenBenchPacket.Entry e) {
        return Math.min(4, e.auxiliary().size()) * 18;
    }

    // --- stitching

    private void renderStitching(GuiGraphics g) {
        int ax = areaX();
        int ay = areaY();
        int aw = areaW();
        int ah = areaH();
        TanningGui.well(g, ax - 2, ay - 2, aw + 4, ah + 4);
        g.fillGradient(ax, ay, ax + aw, ay + ah, StonebornGui.SURFACE, StonebornGui.INSET);
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
            g.drawCenteredString(font, Component.translatable("screen.immersive_tanning.ready", Math.max(1, (-now + 999) / 1000)),
                    ax + aw / 2, ay + ah / 2 - 16, TanningGui.TEXT_WARM);
            TanningGui.drawWrappedCentered(g, font, Component.translatable("screen.immersive_tanning.bench.instructions"), ax + aw / 2, ay + ah / 2, aw - 24, TanningGui.TEXT);
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
                    if (until > -pattern.windowMs() && until < pattern.beatMs() * 1.5F) {
                        float approach = Mth.clamp(until / (float) (pattern.beatMs() * 1.5F), 0F, 1F);
                        float radius = (3F + approach * 9F) * scale;
                        TanningGui.ring(g, px, py, radius, until <= pattern.windowMs() ? TanningGui.perfectColor() : 0xB0E8D6B0);
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
        y += 10 * TanningGui.drawWrappedCentered(g, font, name, cx, y, aw - 16, TanningGui.TEXT);
        y += 4;
        g.drawCenteredString(font, Component.translatable("screen.immersive_tanning.workmanship", Scores.label(result.score())), cx, y, TanningGui.qualityColor(result.score()));
        g.drawCenteredString(font, Component.translatable("screen.immersive_tanning.integrity", Scores.label(start.integrity())), cx, y + 11, TanningGui.qualityColor(start.integrity()));
        TanningGui.drawWrappedCentered(g, font, Component.translatable("screen.immersive_tanning.bench.next"), cx, y + 28, aw - 24, TanningGui.TEXT_DIM);
    }

    private void renderSidebar(GuiGraphics g, int now) {
        int sx = areaX() + areaW() + 10;
        int sw = left + panelWidth - 12 - sx;
        int y = top + 30;
        g.drawString(font, TanningGui.clipped(font, Component.translatable("screen.immersive_tanning.bench.step"), sw), sx, y, TanningGui.TEXT_WARM, false);
        y += 14;
        TanningGui.well(g, sx, y, sw, 36);
        int done = stage == Stage.RESULT ? run.count() : run.next();
        g.drawString(font, Component.translatable("screen.immersive_tanning.bench.stitches"), sx + 5, y + 5, TanningGui.TEXT_DIM, false);
        Component count = Component.literal(done + "/" + run.count());
        g.drawString(font, count, sx + sw - 5 - font.width(count), y + 5, TanningGui.TEXT, false);
        TanningGui.bar(g, sx + 5, y + 22, sw - 10, 5, done / (float) run.count(), TanningGui.STITCH);
        y += 44;
        TanningGui.well(g, sx, y, sw, 30);
        g.drawCenteredString(font, Component.translatable("screen.immersive_tanning.workmanship_label"), sx + sw / 2, y + 5, TanningGui.TEXT_DIM);
        float running = run.runningAccuracy();
        Component grade = stage == Stage.RESULT ? Scores.label(result.score())
                : running >= 0 ? TanningQuality.fromScore(Math.round(running * 100F)).displayName() : Component.literal("-");
        g.drawCenteredString(font, grade, sx + sw / 2, y + 17, TanningGui.TEXT);
        y += 38;
        TraitRef trait = start.trait();
        if (trait != null) {
            y += 10 * TanningGui.drawWrapped(g, font, Component.translatable("tooltip.immersive_tanning.trait", trait.displayName()), sx, y, sw, TanningGui.TEXT, 2) + 4;
        }
        if (stage == Stage.PLAYING) {
            boolean feedback = lastAccuracy >= 0 && Util.getMillis() - lastFeedbackMs < 800;
            TanningGui.drawWrappedCentered(g, font, feedback ? TanningGui.rating(lastAccuracy) : Component.translatable("screen.immersive_tanning.bench.press"),
                    sx + sw / 2, y, sw - 4, feedback ? TanningGui.ratingColor(lastAccuracy) : TanningGui.TEXT_DIM);
        }
    }
}
