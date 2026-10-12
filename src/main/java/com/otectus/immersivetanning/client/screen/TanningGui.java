package com.otectus.immersivetanning.client.screen;

import com.otectus.immersivetanning.config.ClientConfig;
import com.otectus.immersivetanning.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;

/**
 * The minigames' own indicators, drawn inside the dark displays of the vanilla screens ({@link VanillaGui#display}):
 * the hide, panels, seams, stitches, needle and knife, plus ratings and cue sounds. Colours honour the client's
 * high-contrast and colorblind options, every state also has a label or a shape, and none of this changes scoring.
 */
public final class TanningGui {
    /** Thread on the stitching board, and a stitch placed well. */
    public static final int STITCH = 0xFFE5DFD2;
    /** The frame a hide is laced into on the finish cards. */
    public static final int FRAME = 0xFF6B4A2E;

    /** A vanilla bar filled to {@code value} (0..1). */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float value, int color) {
        VanillaGui.bar(g, x, y, w, h, value, color);
    }

    /** The time left as a vanilla bar: amber while there is time, then red (orange when colorblind-safe). */
    public static void timeBar(GuiGraphics g, int x, int y, int w, int h, float remaining) {
        int color = remaining > 0.25F ? 0xFFE0A030 : (ClientConfig.get(ClientConfig.COLORBLIND_SAFE_COLORS) ? 0xFFFF9F1C : 0xFFD04030);
        VanillaGui.bar(g, x, y, w, h, remaining, color);
        // Quarter marks make the remaining time readable without colour.
        for (int i = 1; i < 4; i++) g.fill(x + w * i / 4, y, x + w * i / 4 + 1, y + h, 0x80000000);
    }

    /** A straight line of 1px squares (every {@code step} pixels, dashed when step > 1). */
    public static void line(GuiGraphics g, float x0, float y0, float x1, float y1, int size, int step, int dash, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = Mth.sqrt(dx * dx + dy * dy);
        int n = Math.max(1, Math.round(length));
        for (int i = 0; i <= n; i++) {
            if (step > 1 && (i % step) >= dash) continue;
            int px = Math.round(x0 + dx * i / n);
            int py = Math.round(y0 + dy * i / n);
            g.fill(px, py, px + size, py + size, color);
        }
    }

    /** A ring of 1px squares. */
    public static void ring(GuiGraphics g, float cx, float cy, float radius, int color) {
        int n = Math.max(12, Math.round(radius * 5));
        for (int i = 0; i < n; i++) {
            float a = i * Mth.TWO_PI / n;
            int px = Math.round(cx + Mth.cos(a) * radius);
            int py = Math.round(cy + Mth.sin(a) * radius);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    /** A filled disc of horizontal spans. */
    public static void disc(GuiGraphics g, float cx, float cy, float radius, int color) {
        int r = Math.max(1, Math.round(radius));
        for (int dy = -r; dy <= r; dy++) {
            int half = Math.round(Mth.sqrt(Math.max(0, r * r - dy * dy)));
            int py = Math.round(cy) + dy;
            g.fill(Math.round(cx) - half, py, Math.round(cx) + half + 1, py + 1, color);
        }
    }

    /** Fills a polygon (screen coordinates, x/y pairs) with the even-odd rule, one scanline at a time. */
    public static void polygon(GuiGraphics g, float[] pts, int color) {
        if (pts.length < 6) return;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 1; i < pts.length; i += 2) {
            minY = Math.min(minY, pts[i]);
            maxY = Math.max(maxY, pts[i]);
        }
        int n = pts.length / 2;
        float[] xs = new float[n];
        for (int y = Mth.floor(minY); y <= Mth.ceil(maxY); y++) {
            float sy = y + 0.5F;
            int count = 0;
            for (int i = 0; i < n; i++) {
                float ax = pts[i * 2], ay = pts[i * 2 + 1];
                float bx = pts[((i + 1) % n) * 2], by = pts[((i + 1) % n) * 2 + 1];
                if ((ay <= sy && by > sy) || (by <= sy && ay > sy)) {
                    xs[count++] = ax + (sy - ay) / (by - ay) * (bx - ax);
                }
            }
            java.util.Arrays.sort(xs, 0, count);
            for (int i = 0; i + 1 < count; i += 2) {
                g.fill(Math.round(xs[i]), y, Math.round(xs[i + 1]), y + 1, color);
            }
        }
    }

    public static int goodColor() {
        if (ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES)) return 0xFFFFFFFF;
        return ClientConfig.get(ClientConfig.COLORBLIND_SAFE_COLORS) ? 0xFF3D8BFF : 0xFF6CCB5F;
    }

    public static int missColor() {
        return ClientConfig.get(ClientConfig.COLORBLIND_SAFE_COLORS) ? 0xFFFF9F1C : 0xFFE05A4A;
    }

    public static int perfectColor() {
        if (ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES)) return 0xFFFFE600;
        return ClientConfig.get(ClientConfig.COLORBLIND_SAFE_COLORS) ? 0xFFBFE0FF : 0xFFFFD66B;
    }

    /** "Perfect", "Good", "Rough" or "Miss" for an accuracy in 0..1. */
    public static Component rating(float accuracy) {
        String key = accuracy >= 0.9F ? "perfect" : accuracy >= 0.6F ? "good" : accuracy > 0F ? "rough" : "miss";
        return Component.translatable("minigame.immersive_tanning.rating." + key);
    }

    public static int ratingColor(float accuracy) {
        if (accuracy >= 0.9F) return perfectColor();
        if (accuracy >= 0.6F) return goodColor();
        if (accuracy > 0F) return 0xFFE0C050;
        return missColor();
    }

    /** Text colour of the grade a score falls in. */
    public static int qualityColor(int score) {
        Integer rgb = com.otectus.immersivetanning.leather.TanningQuality.fromScore(score).color().getColor();
        return rgb == null ? VanillaGui.DISPLAY_TEXT : 0xFF000000 | rgb;
    }

    public static float markerScale() {
        return ClientConfig.get(ClientConfig.LARGE_MINIGAME_MARKERS) ? 1.6F : 1.0F;
    }

    public static int withAlpha(int rgb, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static int darken(int argb, float factor) {
        int r = Math.round(((argb >> 16) & 255) * factor);
        int gr = Math.round(((argb >> 8) & 255) * factor);
        int b = Math.round((argb & 255) * factor);
        return (argb & 0xFF000000) | (r << 16) | (gr << 8) | b;
    }

    public static void playUi(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    public static void playCue() {
        if (!ClientConfig.get(ClientConfig.TIMING_CUE_SOUNDS)) return;
        float volume = (float) ClientConfig.get(ClientConfig.SOUND_CUE_VOLUME);
        if (volume > 0F) playUi(ModSounds.TIMING_CUE.get(), 1.4F, volume);
    }

    private TanningGui() {}
}
