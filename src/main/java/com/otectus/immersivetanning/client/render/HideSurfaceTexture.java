package com.otectus.immersivetanning.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.otectus.immersivetanning.config.ClientConfig;
import com.otectus.immersivetanning.minigame.FleshingSurface;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The hide on one rack as a texture, painted from its surface cells: the irregular silhouette, coarse flesh with
 * relief and (optionally) its grain, clean leather streaked in the direction the blade actually went, and the rough
 * fuzz of leather scraped again. Repainted only when the surface changes; released when the rack goes.
 */
public final class HideSurfaceTexture {
    public static final int SCALE = 2;
    private static int counter;

    private final NativeImage image;
    private final DynamicTexture texture;
    private final ResourceLocation id;
    private int version = Integer.MIN_VALUE;
    private long seed;
    private Object painted;
    private boolean paintedGrain;
    private boolean paintedContrast;
    private long lastUsed = Util.getMillis();

    public HideSurfaceTexture(BlockPos pos) {
        image = new NativeImage(NativeImage.Format.RGBA, FleshingSurface.W * SCALE, FleshingSurface.H * SCALE, false);
        texture = new DynamicTexture(image);
        id = Minecraft.getInstance().getTextureManager().register("immersive_tanning_hide_" + (counter++), texture);
    }

    /** The texture for this surface, repainted if it changed. */
    public ResourceLocation of(FleshingSurface surface) {
        lastUsed = Util.getMillis();
        boolean grain = ClientConfig.get(ClientConfig.SHOW_GRAIN_HINTS);
        boolean contrast = ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES);
        if (painted != surface || version != surface.version() || seed != surface.seed()
                || paintedGrain != grain || paintedContrast != contrast) {
            paint(surface);
            painted = surface;
            version = surface.version();
            seed = surface.seed();
            paintedGrain = grain;
            paintedContrast = contrast;
            texture.upload();
        }
        return id;
    }

    public boolean usedWithin(long now, long ms) {
        return now - lastUsed <= ms;
    }

    public void close() {
        Minecraft.getInstance().getTextureManager().release(id);
    }

    private void paint(FleshingSurface s) {
        boolean grain = ClientConfig.get(ClientConfig.SHOW_GRAIN_HINTS);
        boolean contrast = ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES);
        int W = FleshingSurface.W;
        int H = FleshingSurface.H;
        for (int y = 0; y < H * SCALE; y++) {
            for (int x = 0; x < W * SCALE; x++) {
                int cx = x / SCALE;
                int cy = y / SCALE;
                if (!s.isHide(cx, cy)) {
                    image.setPixelRGBA(x, y, 0);
                    continue;
                }
                int noise = hash(x, y) & 15;
                boolean edge = !s.isHide(Math.max(0, cx - 1), cy) || !s.isHide(Math.min(W - 1, cx + 1), cy)
                        || !s.isHide(cx, Math.max(0, cy - 1)) || !s.isHide(cx, Math.min(H - 1, cy + 1));
                // Broad, quiet fibre planes keep the grain and the blade's real strokes legible at close range.
                float fibre = ((hash(x / 3, y / 5) & 7) - 3) * 0.004F;
                int rgb = shade(edge ? 0xAE8762 : 0xE6CBA3, 1F - noise * 0.0025F + fibre);
                float t = s.thickness(cx, cy);
                int region = s.region(cx, cy);
                if (t > 0F) {
                    int deep = contrast ? 0xC81E1E : 0xB9695C;
                    int thin = contrast ? 0xF07070 : 0xDEAB94;
                    rgb = mix(thin, deep, t);
                    rgb = shade(rgb, 0.98F + fibre - noise * 0.002F);
                    // A thin pale lip makes the remaining raised patches distinct from the scraped hide.
                    if (s.thickness(Math.max(0, cx - 1), cy) < t * 0.5F
                            || s.thickness(cx, Math.max(0, cy - 1)) < t * 0.5F) rgb = shade(rgb, 1.12F);
                    if (grain && region >= 0) {
                        float g = s.grain(region);
                        float stripe = -(x + 0.5F) / SCALE * Mth.sin(g) + (y + 0.5F) / SCALE * Mth.cos(g);
                        float band = Mth.positiveModulo(stripe, 4.4F);
                        if (band < 0.65F) rgb = shade(rgb, contrast ? 0.6F : 0.80F);
                        else if (band < 1F) rgb = shade(rgb, 1.12F);
                    }
                } else if (region >= 0) {
                    rgb = shade(0xEAD7B4, 1F - noise * 0.0025F + fibre);
                    // Scrape streaks run with the pass that cleaned the cell.
                    int dir = s.passDirection(cx, cy);
                    float a = dir * Mth.PI / 4F;
                    float along = -(x + 0.5F) * Mth.sin(a) + (y + 0.5F) * Mth.cos(a);
                    if (Mth.positiveModulo(along, 4.5F) < 0.65F) rgb = shade(rgb, 0.94F);
                }
                int rough = s.roughness(cx, cy);
                if (rough > 0 && t <= 0F && ((hash(x * 3, y * 7) & 7) < rough * 2)) rgb = shade(rgb, 1F - 0.09F * rough);
                image.setPixelRGBA(x, y, abgr(0xFF000000 | rgb));
            }
        }
    }

    private static int hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    private static int shade(int rgb, float f) {
        int r = Mth.clamp(Math.round(((rgb >> 16) & 255) * f), 0, 255);
        int g = Mth.clamp(Math.round(((rgb >> 8) & 255) * f), 0, 255);
        int b = Mth.clamp(Math.round((rgb & 255) * f), 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0F, 1F);
        int r = Math.round(Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255));
        int g = Math.round(Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255));
        int bl = Math.round(Mth.lerp(t, a & 255, b & 255));
        return (r << 16) | (g << 8) | bl;
    }

    private static int abgr(int argb) {
        int a = (argb >>> 24) & 255;
        int r = (argb >> 16) & 255;
        int g = (argb >> 8) & 255;
        int b = argb & 255;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
