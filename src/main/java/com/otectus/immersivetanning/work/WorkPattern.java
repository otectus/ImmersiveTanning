package com.otectus.immersivetanning.work;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.minigame.StitchPattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a bench piece is made of and how its work is judged: shaped panels, representative contour cuts, where each
 * panel is seated, and the seams that join them. Server datapacks define it
 * ({@code data/<namespace>/immersive_tanning/work_patterns/<name>.json}); a job keeps its own copy, and the client
 * receives that copy with the job, so a reload or a resource pack never changes a piece in progress. Item costs stay in
 * the recipe: geometry never decides what is consumed.
 *
 * <p>Coordinates: a panel's outline, cuts and seam paths are in the panel's own space (0..1, x right, y down). The
 * panel sits on the bench's work plane ({@code BenchGeometry}, also 0..1) at its {@code layout} while it is cut, waits
 * at a staging place once freed, and is seated at its assembly target. Tolerances, pulls and every distance the server
 * judges are in work-plane units.</p>
 */
public final class WorkPattern {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_PANELS = 6;
    public static final int MAX_POINTS = 32;
    public static final int MAX_CUTS = 6;
    public static final int MAX_JOINS = 6;
    public static final int MAX_ACTIONS_PER_JOIN = 12;
    public static final int MAX_ACTIONS = 16;

    public static final ResourceLocation GENERIC = ImmersiveTanning.id("generic_wrap");
    public static final ResourceLocation PATCH_ID = ImmersiveTanning.id("patch");

    public record Panel(String id, float[] outline, float w, float h, float layoutX, float layoutY, float layoutRot) {}

    public record Cut(String id, int panel, float[] path, float tolerance) {}

    public record Placement(int panel, String anchor, float x, float y, float rot, float posTol, float rotTol) {}

    public record Join(String id, int panelA, float[] pathA, int panelB, float[] pathB, int actions, float pullMin, float pullIdeal, float pullMax) {}

    private final ResourceLocation id;
    private final List<Panel> panels;
    private final List<Cut> cuts;
    private final List<Placement> assembly;
    private final List<Join> joins;
    private final float cuttingWeight;
    private final float assemblyWeight;
    private final float stitchingWeight;
    private final ResourceLocation presentation;
    private final boolean legacy;

    public WorkPattern(ResourceLocation id, List<Panel> panels, List<Cut> cuts, List<Placement> assembly, List<Join> joins,
                       float cuttingWeight, float assemblyWeight, float stitchingWeight, ResourceLocation presentation, boolean legacy) {
        this.id = id;
        this.panels = List.copyOf(panels);
        this.cuts = List.copyOf(cuts);
        this.assembly = List.copyOf(assembly);
        this.joins = List.copyOf(joins);
        float sum = cuttingWeight + assemblyWeight + stitchingWeight;
        if (!(sum > 0F)) {
            cuttingWeight = 0.25F;
            assemblyWeight = 0.15F;
            stitchingWeight = 0.6F;
            sum = 1F;
        }
        this.cuttingWeight = cuttingWeight / sum;
        this.assemblyWeight = assemblyWeight / sum;
        this.stitchingWeight = stitchingWeight / sum;
        this.presentation = presentation;
        this.legacy = legacy;
    }

    public ResourceLocation id() { return id; }
    public List<Panel> panels() { return panels; }
    public List<Cut> cuts() { return cuts; }
    public List<Join> joins() { return joins; }
    public float cuttingWeight() { return cuttingWeight; }
    public float assemblyWeight() { return assemblyWeight; }
    public float stitchingWeight() { return stitchingWeight; }
    public ResourceLocation presentation() { return presentation; }
    /** Adapted from a 1.2 seam pattern rather than authored. */
    public boolean legacy() { return legacy; }

    public int totalActions() {
        int n = 0;
        for (Join j : joins) n += j.actions();
        return n;
    }

    /** The assembly target of a panel (every panel has one). */
    public Placement placement(int panel) {
        for (Placement p : assembly) if (p.panel() == panel) return p;
        Panel p = panels.get(panel);
        return new Placement(panel, "jig", p.layoutX(), p.layoutY(), p.layoutRot(), 0.08F, 15F);
    }

    // ------------------------------------------------------------------ work-plane geometry

    /** A panel-local point placed at (cx, cy, rot degrees) on the work plane. */
    public static float[] place(Panel panel, float px, float py, float cx, float cy, float rot) {
        float lx = (px - 0.5F) * panel.w();
        float ly = (py - 0.5F) * panel.h();
        float r = rot * Mth.DEG_TO_RAD;
        float cos = Mth.cos(r);
        float sin = Mth.sin(r);
        return new float[]{cx + lx * cos - ly * sin, cy + lx * sin + ly * cos};
    }

    /** A panel-local polyline placed on the work plane. */
    public static float[] placePath(Panel panel, float[] path, float cx, float cy, float rot) {
        float[] out = new float[path.length];
        for (int i = 0; i + 1 < path.length; i += 2) {
            float[] p = place(panel, path[i], path[i + 1], cx, cy, rot);
            out[i] = p[0];
            out[i + 1] = p[1];
        }
        return out;
    }

    /** A cut's path on the work plane, where the panel lies while it is cut. */
    public float[] cutPath(int cut) {
        Cut c = cuts.get(cut);
        Panel p = panels.get(c.panel());
        return placePath(p, c.path(), p.layoutX(), p.layoutY(), p.layoutRot());
    }

    /** Where a freed panel waits for assembly: {x, y, rot}, along the far edge of the mat. */
    public float[] staging(int panel) {
        int n = panels.size();
        float x = 0.16F + 0.68F * (n == 1 ? 0.5F : panel / (float) (n - 1));
        Panel p = panels.get(panel);
        float y = 0.06F + Math.max(p.w(), p.h()) * 0.32F;
        return new float[]{x, y, p.layoutRot()};
    }

    /**
     * The hole pair and pull frame of one stitch action with the panels where they were seated: {ax, ay, bx, by, cx,
     * cy, nx, ny} - both holes, their midpoint and the unit normal across the seam.
     */
    public float[] stitch(int action, float[] seatX, float[] seatY, float[] seatRot) {
        int k = action;
        for (Join j : joins) {
            if (k >= j.actions()) {
                k -= j.actions();
                continue;
            }
            float share = (k + 0.5F) / j.actions();
            Panel pa = panels.get(j.panelA());
            Panel pb = panels.get(j.panelB());
            float[] a = StitchPattern.pointAt(placePath(pa, j.pathA(), seatX[j.panelA()], seatY[j.panelA()], seatRot[j.panelA()]), share);
            float[] b = StitchPattern.pointAt(placePath(pb, j.pathB(), seatX[j.panelB()], seatY[j.panelB()], seatRot[j.panelB()]), share);
            float cx = (a[0] + b[0]) / 2F;
            float cy = (a[1] + b[1]) / 2F;
            float[] mid = new float[j.pathA().length];
            float[] wa = placePath(pa, j.pathA(), seatX[j.panelA()], seatY[j.panelA()], seatRot[j.panelA()]);
            float[] wb = placePath(pb, j.pathB(), seatX[j.panelB()], seatY[j.panelB()], seatRot[j.panelB()]);
            int n = Math.min(wa.length, wb.length);
            for (int i = 0; i < n; i++) mid[i] = (wa[i] + wb[i]) / 2F;
            float angle = StitchPattern.angleAt(n == mid.length ? mid : wa, share);
            return new float[]{a[0], a[1], b[0], b[1], cx, cy, -Mth.sin(angle), Mth.cos(angle)};
        }
        return new float[]{0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0F, 1F};
    }

    /** The join an action belongs to. */
    public Join joinOf(int action) {
        int k = action;
        for (Join j : joins) {
            if (k < j.actions()) return j;
            k -= j.actions();
        }
        return joins.get(joins.size() - 1);
    }

    // ------------------------------------------------------------------ JSON

    public static WorkPattern fromJson(ResourceLocation id, JsonObject json) {
        int version = GsonHelper.getAsInt(json, "schema_version", SCHEMA_VERSION);
        if (version != SCHEMA_VERSION) throw new JsonParseException("unsupported schema_version " + version + " (this version reads " + SCHEMA_VERSION + ")");
        JsonArray panelArray = GsonHelper.getAsJsonArray(json, "panels");
        if (panelArray.isEmpty() || panelArray.size() > MAX_PANELS) throw new JsonParseException("a pattern needs 1-" + MAX_PANELS + " panels");
        List<Panel> panels = new ArrayList<>();
        Map<String, Integer> panelIndex = new HashMap<>();
        int i = 0;
        for (JsonElement e : panelArray) {
            JsonObject o = GsonHelper.convertToJsonObject(e, "panel");
            String pid = GsonHelper.getAsString(o, "id");
            if (panelIndex.put(pid, i) != null) throw new JsonParseException("duplicate panel id " + pid);
            float[] outline = points(GsonHelper.getAsJsonArray(o, "outline"), 3, "outline of " + pid);
            float[] size = pair(o, "size", 0.42F, 0.42F, 0.08F, 0.95F, "size of " + pid);
            float defaultX = panelArray.size() == 1 ? 0.5F : 0.27F + 0.46F * i / (panelArray.size() - 1F);
            float[] layout = pair(o, "layout", defaultX, 0.52F, 0F, 1F, "layout of " + pid);
            float rot = angle(GsonHelper.getAsFloat(o, "layout_rotation_deg", 0F), "layout_rotation_deg of " + pid);
            panels.add(new Panel(pid, outline, size[0], size[1], layout[0], layout[1], rot));
            i++;
        }

        List<Cut> cuts = new ArrayList<>();
        if (json.has("cuts")) {
            JsonArray array = GsonHelper.getAsJsonArray(json, "cuts");
            if (array.size() > MAX_CUTS) throw new JsonParseException("at most " + MAX_CUTS + " cuts");
            for (JsonElement e : array) {
                JsonObject o = GsonHelper.convertToJsonObject(e, "cut");
                String cid = GsonHelper.getAsString(o, "id");
                int panel = panelRef(panelIndex, GsonHelper.getAsString(o, "panel"), "cut " + cid);
                float[] path = points(GsonHelper.getAsJsonArray(o, "path"), 2, "path of cut " + cid);
                if (StitchPattern.length(path) < 0.05F) throw new JsonParseException("cut " + cid + " is too short (degenerate)");
                float tolerance = range(GsonHelper.getAsFloat(o, "tolerance", 0.06F), 0.01F, 0.25F, "tolerance of cut " + cid);
                cuts.add(new Cut(cid, panel, path, tolerance));
            }
        }

        List<Placement> assembly = new ArrayList<>();
        boolean[] seated = new boolean[panels.size()];
        if (json.has("assembly")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "assembly")) {
                JsonObject o = GsonHelper.convertToJsonObject(e, "assembly");
                int panel = panelRef(panelIndex, GsonHelper.getAsString(o, "panel"), "assembly");
                if (seated[panel]) throw new JsonParseException("panel " + panels.get(panel).id() + " is seated twice");
                seated[panel] = true;
                float[] target = points(GsonHelper.getAsJsonArray(o, "target"), 1, "assembly target");
                float rot = angle(GsonHelper.getAsFloat(o, "rotation_deg", 0F), "rotation_deg");
                float posTol = range(GsonHelper.getAsFloat(o, "position_tolerance", 0.08F), 0.02F, 0.3F, "position_tolerance");
                float rotTol = range(GsonHelper.getAsFloat(o, "rotation_tolerance_deg", 15F), 2F, 90F, "rotation_tolerance_deg");
                assembly.add(new Placement(panel, GsonHelper.getAsString(o, "anchor", "jig"), target[0], target[1], rot, posTol, rotTol));
            }
        }

        JsonArray joinArray = GsonHelper.getAsJsonArray(json, "joins");
        if (joinArray.isEmpty() || joinArray.size() > MAX_JOINS) throw new JsonParseException("a pattern needs 1-" + MAX_JOINS + " joins");
        List<Join> joins = new ArrayList<>();
        int actions = 0;
        for (JsonElement e : joinArray) {
            JsonObject o = GsonHelper.convertToJsonObject(e, "join");
            String jid = GsonHelper.getAsString(o, "id");
            JsonObject a = GsonHelper.getAsJsonObject(o, "a");
            JsonObject b = GsonHelper.getAsJsonObject(o, "b");
            int pa = panelRef(panelIndex, GsonHelper.getAsString(a, "panel"), "join " + jid);
            int pb = panelRef(panelIndex, GsonHelper.getAsString(b, "panel"), "join " + jid);
            float[] pathA = points(GsonHelper.getAsJsonArray(a, "path"), 2, "path a of join " + jid);
            float[] pathB = points(GsonHelper.getAsJsonArray(b, "path"), 2, "path b of join " + jid);
            if (StitchPattern.length(pathA) < 0.04F || StitchPattern.length(pathB) < 0.04F) throw new JsonParseException("join " + jid + " has a degenerate path");
            int n = GsonHelper.getAsInt(o, "actions", 4);
            if (n < 1 || n > MAX_ACTIONS_PER_JOIN) throw new JsonParseException("join " + jid + ": actions must be 1-" + MAX_ACTIONS_PER_JOIN);
            actions += n;
            float min = 0.18F;
            float ideal = 0.25F;
            float max = 0.35F;
            if (o.has("pull")) {
                JsonObject pull = GsonHelper.getAsJsonObject(o, "pull");
                min = finite(GsonHelper.getAsFloat(pull, "min", min), "pull.min");
                ideal = finite(GsonHelper.getAsFloat(pull, "ideal", ideal), "pull.ideal");
                max = finite(GsonHelper.getAsFloat(pull, "max", max), "pull.max");
            }
            if (!(min > 0.02F && min < ideal && ideal < max && max <= 0.8F)) throw new JsonParseException("join " + jid + ": pull needs 0.02 < min < ideal < max <= 0.8");
            joins.add(new Join(jid, pa, pathA, pb, pathB, n, min, ideal, max));
        }
        if (actions > MAX_ACTIONS) throw new JsonParseException("at most " + MAX_ACTIONS + " stitch actions in all joins");

        float wc = 0.25F;
        float wa = 0.15F;
        float ws = 0.6F;
        if (json.has("quality_weights")) {
            JsonObject w = GsonHelper.getAsJsonObject(json, "quality_weights");
            wc = range(GsonHelper.getAsFloat(w, "cutting", wc), 0F, 1F, "quality_weights.cutting");
            wa = range(GsonHelper.getAsFloat(w, "assembly", wa), 0F, 1F, "quality_weights.assembly");
            ws = range(GsonHelper.getAsFloat(w, "stitching", ws), 0F, 1F, "quality_weights.stitching");
            if (wc + wa + ws <= 0F) throw new JsonParseException("quality_weights must not all be 0");
        }
        ResourceLocation presentation = json.has("presentation")
                ? new ResourceLocation(GsonHelper.getAsString(json, "presentation")) : GENERIC;
        return new WorkPattern(id, panels, cuts, assembly, joins, wc, wa, ws, presentation, false);
    }

    private static int panelRef(Map<String, Integer> index, String id, String where) {
        Integer i = index.get(id);
        if (i == null) throw new JsonParseException(where + " names unknown panel " + id);
        return i;
    }

    private static float[] points(JsonArray array, int minimum, String what) {
        if (array.size() < minimum) throw new JsonParseException(what + " needs at least " + minimum + " points");
        // A single [x, y] pair is accepted where one point is meant (assembly target).
        if (minimum == 1 && array.size() == 2 && array.get(0).isJsonPrimitive()) {
            return new float[]{unit(array.get(0).getAsFloat(), what), unit(array.get(1).getAsFloat(), what)};
        }
        if (array.size() > MAX_POINTS) throw new JsonParseException(what + " has more than " + MAX_POINTS + " points");
        float[] out = new float[array.size() * 2];
        for (int i = 0; i < array.size(); i++) {
            JsonArray p = GsonHelper.convertToJsonArray(array.get(i), what);
            if (p.size() != 2) throw new JsonParseException(what + ": every point is [x, y]");
            out[i * 2] = unit(p.get(0).getAsFloat(), what);
            out[i * 2 + 1] = unit(p.get(1).getAsFloat(), what);
        }
        return out;
    }

    private static float[] pair(JsonObject o, String key, float dx, float dy, float min, float max, String what) {
        if (!o.has(key)) return new float[]{dx, dy};
        JsonArray a = GsonHelper.getAsJsonArray(o, key);
        if (a.size() != 2) throw new JsonParseException(what + " is [x, y]");
        return new float[]{range(a.get(0).getAsFloat(), min, max, what), range(a.get(1).getAsFloat(), min, max, what)};
    }

    private static float unit(float v, String what) {
        return range(v, 0F, 1F, what);
    }

    private static float finite(float v, String what) {
        if (!Float.isFinite(v)) throw new JsonParseException(what + " is not a finite number");
        return v;
    }

    private static float range(float v, float min, float max, String what) {
        finite(v, what);
        if (v < min || v > max) throw new JsonParseException(what + " must be " + min + "-" + max + ", got " + v);
        return v;
    }

    private static float angle(float v, String what) {
        return range(v, -360F, 360F, what);
    }

    // ------------------------------------------------------------------ legacy seam patterns

    /**
     * A 1.2 seam pattern as a work pattern: its panel polygons become panels where they lay, the longest edges of the
     * first panel become two or three representative cuts, every panel is seated back where it was cut, and each
     * seam becomes a join of about half its old stitch count (2-6, at most 8 in all). Rhythm and timing are ignored.
     */
    public static WorkPattern fromLegacy(StitchPattern legacy) {
        List<float[]> polygons = legacy.panels().isEmpty()
                ? List.of(new float[]{0.14F, 0.28F, 0.86F, 0.28F, 0.86F, 0.72F, 0.14F, 0.72F}) : legacy.panels();
        List<Panel> panels = new ArrayList<>();
        List<float[]> bounds = new ArrayList<>();
        for (int p = 0; p < Math.min(polygons.size(), MAX_PANELS); p++) {
            float[] poly = polygons.get(p);
            float minX = 1F, minY = 1F, maxX = 0F, maxY = 0F;
            for (int i = 0; i + 1 < poly.length; i += 2) {
                minX = Math.min(minX, poly[i]);
                maxX = Math.max(maxX, poly[i]);
                minY = Math.min(minY, poly[i + 1]);
                maxY = Math.max(maxY, poly[i + 1]);
            }
            float w = Math.max(0.08F, maxX - minX);
            float h = Math.max(0.08F, maxY - minY);
            int count = Math.min(poly.length / 2, MAX_POINTS);
            float[] outline = new float[count * 2];
            for (int i = 0; i < count; i++) {
                outline[i * 2] = Mth.clamp((poly[i * 2] - minX) / w, 0F, 1F);
                outline[i * 2 + 1] = Mth.clamp((poly[i * 2 + 1] - minY) / h, 0F, 1F);
            }
            float cx = 0.5F + ((minX + maxX) / 2F - 0.5F) * 0.86F;
            float cy = 0.5F + ((minY + maxY) / 2F - 0.5F) * 0.86F;
            panels.add(new Panel("panel_" + p, outline, w * 0.86F, h * 0.86F, cx, cy, 0F));
            bounds.add(new float[]{minX, minY, w, h});
        }

        // The longest edges of the first panel are the representative cuts.
        Panel first = panels.get(0);
        float[] o = first.outline();
        int edges = o.length / 2;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < edges; i++) order.add(i);
        order.sort((a, b) -> Float.compare(edgeLength(o, b, edges, first), edgeLength(o, a, edges, first)));
        List<Cut> cuts = new ArrayList<>();
        for (int k = 0; k < Math.min(3, edges); k++) {
            int e = order.get(k);
            int f = (e + 1) % edges;
            float[] path = {o[e * 2], o[e * 2 + 1], o[f * 2], o[f * 2 + 1]};
            if (StitchPattern.length(path) < 0.05F) continue;
            cuts.add(new Cut("edge_" + k, 0, path, 0.07F));
        }

        List<Placement> assembly = new ArrayList<>();
        for (int p = 0; p < panels.size(); p++) {
            Panel panel = panels.get(p);
            assembly.add(new Placement(p, "jig", panel.layoutX(), panel.layoutY(), 0F, 0.09F, 18F));
        }

        List<Join> joins = new ArrayList<>();
        int budget = 8;
        for (int s = 0; s < Math.min(legacy.seams().size(), MAX_JOINS) && budget > 0; s++) {
            float[] seam = legacy.seams().get(s);
            int panel = containing(bounds, seam);
            float[] b = bounds.get(panel);
            float[] local = new float[seam.length];
            for (int i = 0; i + 1 < seam.length; i += 2) {
                local[i] = Mth.clamp((seam[i] - b[0]) / b[2], 0F, 1F);
                local[i + 1] = Mth.clamp((seam[i + 1] - b[1]) / b[3], 0F, 1F);
            }
            float[] other = offset(local, 0.05F);
            int actions = Math.min(budget, Mth.clamp(Math.round(legacy.stitches()[s] / 2F), 2, 6));
            budget -= actions;
            if (StitchPattern.length(local) < 0.04F) continue;
            joins.add(new Join("seam_" + s, panel, local, panel, other, actions, 0.18F, 0.25F, 0.35F));
        }
        if (joins.isEmpty()) {
            joins.add(new Join("seam", 0, new float[]{0.15F, 0.3F, 0.85F, 0.3F}, 0, new float[]{0.15F, 0.36F, 0.85F, 0.36F}, 4, 0.18F, 0.25F, 0.35F));
        }
        return new WorkPattern(legacy.id(), panels, cuts, assembly, joins, 0.25F, 0.15F, 0.6F, GENERIC, true);
    }

    private static float edgeLength(float[] o, int e, int edges, Panel p) {
        int f = (e + 1) % edges;
        float dx = (o[f * 2] - o[e * 2]) * p.w();
        float dy = (o[f * 2 + 1] - o[e * 2 + 1]) * p.h();
        return Mth.sqrt(dx * dx + dy * dy);
    }

    private static int containing(List<float[]> bounds, float[] seam) {
        float[] mid = StitchPattern.pointAt(seam, 0.5F);
        for (int i = 0; i < bounds.size(); i++) {
            float[] b = bounds.get(i);
            if (mid[0] >= b[0] && mid[0] <= b[0] + b[2] && mid[1] >= b[1] && mid[1] <= b[1] + b[3]) return i;
        }
        return 0;
    }

    /** A polyline shifted sideways by {@code d} (panel units), kept inside the panel. */
    private static float[] offset(float[] path, float d) {
        float[] out = new float[path.length];
        int n = path.length / 2;
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - 1);
            int b = Math.min(n - 1, i + 1);
            float dx = path[b * 2] - path[a * 2];
            float dy = path[b * 2 + 1] - path[a * 2 + 1];
            float len = Mth.sqrt(dx * dx + dy * dy);
            float nx = len < 1.0E-5F ? 0F : -dy / len;
            float ny = len < 1.0E-5F ? 1F : dx / len;
            out[i * 2] = Mth.clamp(path[i * 2] + nx * d, 0F, 1F);
            out[i * 2 + 1] = Mth.clamp(path[i * 2 + 1] + ny * d, 0F, 1F);
        }
        return out;
    }

    // ------------------------------------------------------------------ built-ins

    /** The generic wrap: a front and a back panel trimmed along their tops, seated side by side and closed at the side. */
    public static WorkPattern genericWrap() {
        float[] square = {0.1F, 0.1F, 0.9F, 0.1F, 0.9F, 0.9F, 0.1F, 0.9F};
        List<Panel> panels = List.of(new Panel("front", square, 0.36F, 0.42F, 0.3F, 0.55F, 0F),
                new Panel("back", square, 0.36F, 0.42F, 0.7F, 0.55F, 0F));
        List<Cut> cuts = List.of(new Cut("front_trim", 0, new float[]{0.1F, 0.1F, 0.9F, 0.1F}, 0.06F),
                new Cut("back_trim", 1, new float[]{0.1F, 0.1F, 0.9F, 0.1F}, 0.06F));
        List<Placement> assembly = List.of(new Placement(0, "jig_front", 0.36F, 0.56F, 0F, 0.08F, 15F),
                new Placement(1, "jig_back", 0.64F, 0.56F, 0F, 0.08F, 15F));
        List<Join> joins = List.of(new Join("side", 0, new float[]{0.85F, 0.2F, 0.85F, 0.8F}, 1, new float[]{0.15F, 0.2F, 0.15F, 0.8F},
                5, 0.18F, 0.25F, 0.35F));
        return new WorkPattern(GENERIC, panels, cuts, assembly, joins, 0.25F, 0.15F, 0.6F, GENERIC, false);
    }

    /** A bench repair: one patch seated over the worn place and a short seam around it. No cuts. */
    public static WorkPattern patch() {
        float[] patch = {0.15F, 0.2F, 0.85F, 0.15F, 0.9F, 0.8F, 0.2F, 0.85F};
        List<Panel> panels = List.of(new Panel("patch", patch, 0.26F, 0.24F, 0.5F, 0.3F, 0F));
        List<Placement> assembly = List.of(new Placement(0, "worn_place", 0.5F, 0.58F, 0F, 0.1F, 20F));
        List<Join> joins = List.of(new Join("patch_seam", 0, new float[]{0.2F, 0.22F, 0.8F, 0.18F}, 0, new float[]{0.2F, 0.08F, 0.8F, 0.04F},
                3, 0.18F, 0.25F, 0.35F));
        return new WorkPattern(PATCH_ID, panels, List.of(), assembly, joins, 0F, 0.25F, 0.75F, PATCH_ID, false);
    }

    // ------------------------------------------------------------------ persistence and network

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Schema", SCHEMA_VERSION);
        tag.putString("Id", id.toString());
        tag.putString("Presentation", presentation.toString());
        tag.putBoolean("Legacy", legacy);
        tag.putFloat("WCut", cuttingWeight);
        tag.putFloat("WAsm", assemblyWeight);
        tag.putFloat("WStitch", stitchingWeight);
        ListTag panelList = new ListTag();
        for (Panel p : panels) {
            CompoundTag t = new CompoundTag();
            t.putString("Id", p.id());
            putFloats(t, "Outline", p.outline());
            putFloats(t, "Frame", new float[]{p.w(), p.h(), p.layoutX(), p.layoutY(), p.layoutRot()});
            panelList.add(t);
        }
        tag.put("Panels", panelList);
        ListTag cutList = new ListTag();
        for (Cut c : cuts) {
            CompoundTag t = new CompoundTag();
            t.putString("Id", c.id());
            t.putInt("Panel", c.panel());
            putFloats(t, "Path", c.path());
            t.putFloat("Tol", c.tolerance());
            cutList.add(t);
        }
        tag.put("Cuts", cutList);
        ListTag asm = new ListTag();
        for (Placement p : assembly) {
            CompoundTag t = new CompoundTag();
            t.putInt("Panel", p.panel());
            t.putString("Anchor", p.anchor());
            putFloats(t, "Target", new float[]{p.x(), p.y(), p.rot(), p.posTol(), p.rotTol()});
            asm.add(t);
        }
        tag.put("Assembly", asm);
        ListTag joinList = new ListTag();
        for (Join j : joins) {
            CompoundTag t = new CompoundTag();
            t.putString("Id", j.id());
            t.putInt("A", j.panelA());
            t.putInt("B", j.panelB());
            putFloats(t, "PathA", j.pathA());
            putFloats(t, "PathB", j.pathB());
            t.putInt("Actions", j.actions());
            putFloats(t, "Pull", new float[]{j.pullMin(), j.pullIdeal(), j.pullMax()});
            joinList.add(t);
        }
        tag.put("Joins", joinList);
        return tag;
    }

    /** Reads a saved snapshot; null when it is damaged (the job then offers recovery of its materials). */
    @Nullable
    public static WorkPattern load(CompoundTag tag) {
        try {
            if (tag.getInt("Schema") != SCHEMA_VERSION) return null;
            List<Panel> panels = new ArrayList<>();
            for (Tag t : tag.getList("Panels", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                float[] frame = getFloats(c, "Frame", 5);
                panels.add(new Panel(c.getString("Id"), checked(getFloats(c, "Outline", -1), 6), frame[0], frame[1], frame[2], frame[3], frame[4]));
            }
            if (panels.isEmpty() || panels.size() > MAX_PANELS) return null;
            List<Cut> cuts = new ArrayList<>();
            for (Tag t : tag.getList("Cuts", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                cuts.add(new Cut(c.getString("Id"), index(c.getInt("Panel"), panels.size()), checked(getFloats(c, "Path", -1), 4), c.getFloat("Tol")));
            }
            List<Placement> assembly = new ArrayList<>();
            for (Tag t : tag.getList("Assembly", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                float[] v = getFloats(c, "Target", 5);
                assembly.add(new Placement(index(c.getInt("Panel"), panels.size()), c.getString("Anchor"), v[0], v[1], v[2], v[3], v[4]));
            }
            List<Join> joins = new ArrayList<>();
            for (Tag t : tag.getList("Joins", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                float[] pull = getFloats(c, "Pull", 3);
                int actions = c.getInt("Actions");
                if (actions < 1 || actions > MAX_ACTIONS_PER_JOIN) return null;
                joins.add(new Join(c.getString("Id"), index(c.getInt("A"), panels.size()), checked(getFloats(c, "PathA", -1), 4),
                        index(c.getInt("B"), panels.size()), checked(getFloats(c, "PathB", -1), 4), actions, pull[0], pull[1], pull[2]));
            }
            if (joins.isEmpty() || cuts.size() > MAX_CUTS || joins.size() > MAX_JOINS) return null;
            WorkPattern pattern = new WorkPattern(new ResourceLocation(tag.getString("Id")), panels, cuts, assembly, joins,
                    tag.getFloat("WCut"), tag.getFloat("WAsm"), tag.getFloat("WStitch"), new ResourceLocation(tag.getString("Presentation")),
                    tag.getBoolean("Legacy"));
            return pattern.totalActions() <= MAX_ACTIONS ? pattern : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(save());
    }

    @Nullable
    public static WorkPattern read(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return tag == null ? null : load(tag);
    }

    private static int index(int i, int size) {
        if (i < 0 || i >= size) throw new IllegalArgumentException("panel index");
        return i;
    }

    private static float[] checked(float[] v, int minimum) {
        if (v.length < minimum || v.length % 2 != 0 || v.length > MAX_POINTS * 2) throw new IllegalArgumentException("points");
        for (float f : v) if (!Float.isFinite(f) || f < -0.01F || f > 1.01F) throw new IllegalArgumentException("point");
        return v;
    }

    private static void putFloats(CompoundTag tag, String key, float[] values) {
        int[] bits = new int[values.length];
        for (int i = 0; i < values.length; i++) bits[i] = Float.floatToIntBits(values[i]);
        tag.putIntArray(key, bits);
    }

    private static float[] getFloats(CompoundTag tag, String key, int exact) {
        int[] bits = tag.getIntArray(key);
        if (exact >= 0 && bits.length != exact) throw new IllegalArgumentException(key);
        float[] out = new float[bits.length];
        for (int i = 0; i < bits.length; i++) {
            out[i] = Float.intBitsToFloat(bits[i]);
            if (!Float.isFinite(out[i])) throw new IllegalArgumentException(key);
        }
        return out;
    }

    /** A stable fingerprint of the authoritative content, for logs and the job's snapshot record. */
    public int fingerprint() {
        return save().hashCode();
    }
}
