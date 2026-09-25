package com.otectus.immersivetanning.minigame;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.otectus.immersivetanning.ImmersiveTanning;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * A seam: the leather panels a recipe cuts and the seams stitched along them. Recipes change the shape and rhythm of
 * the seam, never the rules. Coordinates are normalised to the work area (0..1, y down).
 * <pre>
 * {
 *   "panels": [[[0.2,0.2],[0.8,0.2],[0.7,0.8],[0.3,0.8]]],
 *   "seams":  [{"path": [[0.25,0.25],[0.5,0.2],[0.75,0.25]], "stitches": 6}],
 *   "rhythm": [1, 1, 2],     // beat multipliers, cycled from the first stitch
 *   "beat_ms": 520, "window_ms": 170, "perfect_ms": 45, "lead_ms": 900
 * }
 * </pre>
 */
public record StitchPattern(ResourceLocation id, List<float[]> panels, List<float[]> seams, int[] stitches, float[] rhythm,
                            int beatMs, int windowMs, int perfectMs, int leadMs) {
    public static final ResourceLocation GENERIC = ImmersiveTanning.id("generic");
    public static final StitchPattern DEFAULT = new StitchPattern(GENERIC,
            List.of(new float[]{0.14F, 0.28F, 0.86F, 0.28F, 0.86F, 0.72F, 0.14F, 0.72F}),
            List.of(new float[]{0.2F, 0.36F, 0.8F, 0.36F}, new float[]{0.8F, 0.64F, 0.2F, 0.64F}),
            new int[]{5, 5}, new float[]{1F}, 520, 170, 45, 900);

    public StitchPattern {
        if (seams.isEmpty() || seams.size() != stitches.length) throw new IllegalArgumentException("every seam needs a stitch count");
        if (rhythm.length == 0) rhythm = new float[]{1F};
    }

    public int totalStitches() {
        int n = 0;
        for (int s : stitches) n += s;
        return n;
    }

    // ------------------------------------------------------------------ derived timing and positions

    /** Server-side and client-side schedule: the ideal time of every stitch, in session milliseconds. */
    public int[] stitchTimes() {
        int[] times = new int[totalStitches()];
        float t = leadMs;
        int k = 0;
        for (int seam = 0; seam < seams.size(); seam++) {
            if (seam > 0) t += beatMs * 2F; // the needle lifts and moves to the next seam
            for (int i = 0; i < stitches[seam]; i++) {
                if (k > 0 && i > 0) t += beatMs * rhythm[(k - 1) % rhythm.length];
                times[k++] = Math.round(t);
            }
        }
        return times;
    }

    /** Seam index of every stitch. */
    public int[] stitchSeams() {
        int[] out = new int[totalStitches()];
        int k = 0;
        for (int seam = 0; seam < seams.size(); seam++) {
            for (int i = 0; i < stitches[seam]; i++) out[k++] = seam;
        }
        return out;
    }

    /** Arc-length share (0..1) along its seam of every stitch; stitches sit evenly with half a gap at each end. */
    public float[] stitchArc() {
        float[] out = new float[totalStitches()];
        int k = 0;
        for (int seam = 0; seam < seams.size(); seam++) {
            for (int i = 0; i < stitches[seam]; i++) out[k++] = (i + 0.5F) / stitches[seam];
        }
        return out;
    }

    public static float length(float[] path) {
        float total = 0F;
        for (int i = 2; i + 1 < path.length; i += 2) {
            total += Mth.sqrt(sq(path[i] - path[i - 2]) + sq(path[i + 1] - path[i - 1]));
        }
        return total;
    }

    /** The point at an arc-length share along a polyline. */
    public static float[] pointAt(float[] path, float share) {
        float target = length(path) * Mth.clamp(share, 0F, 1F);
        for (int i = 2; i + 1 < path.length; i += 2) {
            float seg = Mth.sqrt(sq(path[i] - path[i - 2]) + sq(path[i + 1] - path[i - 1]));
            if (target <= seg || i + 2 >= path.length) {
                float t = seg <= 0F ? 0F : Mth.clamp(target / seg, 0F, 1F);
                return new float[]{Mth.lerp(t, path[i - 2], path[i]), Mth.lerp(t, path[i - 1], path[i + 1])};
            }
            target -= seg;
        }
        return new float[]{path[0], path[1]};
    }

    /** Direction (radians) of a polyline at an arc-length share. */
    public static float angleAt(float[] path, float share) {
        float target = length(path) * Mth.clamp(share, 0F, 1F);
        for (int i = 2; i + 1 < path.length; i += 2) {
            float seg = Mth.sqrt(sq(path[i] - path[i - 2]) + sq(path[i + 1] - path[i - 1]));
            if (target <= seg || i + 2 >= path.length) {
                return (float) Math.atan2(path[i + 1] - path[i - 1], path[i] - path[i - 2]);
            }
            target -= seg;
        }
        return 0F;
    }

    private static float sq(float v) {
        return v * v;
    }

    // ------------------------------------------------------------------ serialisation

    public static StitchPattern fromJson(ResourceLocation id, JsonObject json) {
        List<float[]> panels = new ArrayList<>();
        if (json.has("panels")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "panels")) panels.add(points(e.getAsJsonArray(), 3));
        }
        List<float[]> seams = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "seams")) {
            JsonObject seam = GsonHelper.convertToJsonObject(e, "seam");
            seams.add(points(GsonHelper.getAsJsonArray(seam, "path"), 2));
            int n = GsonHelper.getAsInt(seam, "stitches");
            if (n < 1 || n > 40) throw new JsonParseException("stitches must be 1-40");
            counts.add(n);
        }
        if (seams.isEmpty() || seams.size() > 8) throw new JsonParseException("a pattern needs 1-8 seams");
        float[] rhythm = new float[]{1F};
        if (json.has("rhythm")) {
            JsonArray array = GsonHelper.getAsJsonArray(json, "rhythm");
            rhythm = new float[array.size()];
            for (int i = 0; i < rhythm.length; i++) {
                rhythm[i] = array.get(i).getAsFloat();
                if (rhythm[i] < 0.5F || rhythm[i] > 4F) throw new JsonParseException("rhythm values must be 0.5-4");
            }
        }
        int beat = GsonHelper.getAsInt(json, "beat_ms", 520);
        int window = GsonHelper.getAsInt(json, "window_ms", 170);
        int perfect = GsonHelper.getAsInt(json, "perfect_ms", 45);
        int lead = GsonHelper.getAsInt(json, "lead_ms", 900);
        if (beat < 250 || beat > 3000) throw new JsonParseException("beat_ms must be 250-3000");
        if (window < 60 || window >= beat || perfect < 0 || perfect >= window) throw new JsonParseException("invalid window_ms/perfect_ms");
        return new StitchPattern(id, List.copyOf(panels), List.copyOf(seams), counts.stream().mapToInt(Integer::intValue).toArray(),
                rhythm, beat, window, perfect, Math.max(300, lead));
    }

    private static float[] points(JsonArray array, int minimum) {
        if (array.size() < minimum) throw new JsonParseException("a path needs at least " + minimum + " points");
        float[] out = new float[array.size() * 2];
        for (int i = 0; i < array.size(); i++) {
            JsonArray p = array.get(i).getAsJsonArray();
            out[i * 2] = Mth.clamp(p.get(0).getAsFloat(), 0F, 1F);
            out[i * 2 + 1] = Mth.clamp(p.get(1).getAsFloat(), 0F, 1F);
        }
        return out;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(id);
        buf.writeVarInt(panels.size());
        panels.forEach(p -> writeFloats(buf, p));
        buf.writeVarInt(seams.size());
        for (int i = 0; i < seams.size(); i++) {
            writeFloats(buf, seams.get(i));
            buf.writeVarInt(stitches[i]);
        }
        writeFloats(buf, rhythm);
        buf.writeVarInt(beatMs);
        buf.writeVarInt(windowMs);
        buf.writeVarInt(perfectMs);
        buf.writeVarInt(leadMs);
    }

    public static StitchPattern read(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        int panelCount = Math.min(buf.readVarInt(), 16);
        List<float[]> panels = new ArrayList<>();
        for (int i = 0; i < panelCount; i++) panels.add(readFloats(buf));
        int seamCount = Math.min(buf.readVarInt(), 8);
        List<float[]> seams = new ArrayList<>();
        int[] stitches = new int[seamCount];
        for (int i = 0; i < seamCount; i++) {
            seams.add(readFloats(buf));
            stitches[i] = buf.readVarInt();
        }
        return new StitchPattern(id, panels, seams, stitches, readFloats(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    private static void writeFloats(FriendlyByteBuf buf, float[] values) {
        buf.writeVarInt(values.length);
        for (float v : values) buf.writeFloat(v);
    }

    private static float[] readFloats(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 256);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) out[i] = buf.readFloat();
        return out;
    }
}
