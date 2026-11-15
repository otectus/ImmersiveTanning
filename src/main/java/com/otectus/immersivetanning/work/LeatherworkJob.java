package com.otectus.immersivetanning.work;

import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.minigame.StitchPattern;
import com.otectus.immersivetanning.trait.TraitRef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * One piece in the making at a Leatherworker's Bench: the materials it holds ({@link MaterialLedger}), the pattern it
 * follows (a {@link WorkPattern} copied when the job was prepared), and every piece of work accepted so far. It lives
 * in the bench (saved with it), survives a player leaving, a restart and a reload, and can travel as an unfinished
 * workpiece item. Every rule that judges the work is here and runs on the server; clients receive a copy to draw.
 *
 * <p>Stages: {@link Stage#RESERVED} (materials held, nothing cut: cancelling returns them whole), {@link Stage#CUTTING},
 * {@link Stage#ASSEMBLY}, {@link Stage#STITCHING}, {@link Stage#TIE} (the last knot), {@link Stage#DONE}.</p>
 *
 * <p>Scoring counts distinct accepted work, never frames, distance or packets: a cut advances only along its own path
 * and only once; a panel counts when it is seated; a stitch counts when its thread is released. Workmanship is
 * {@code round(100 x (cutting x wc + assembly x wa + stitching x ws))} with the pattern's weights, every component
 * averaged over all of its required items.</p>
 */
public final class LeatherworkJob {
    public static final int VERSION = 1;
    /** How near the pressed point must be to the hole pair (work-plane units). */
    public static final float HOLE_TOLERANCE = 0.065F;
    public static final int MAX_REDO = 3;
    public static final float REDONE_CAP = 0.95F;
    private static final int MAX_STRAY_MARKS = 24;

    public enum Kind { CRAFT, REPAIR }

    public enum Stage { RESERVED, CUTTING, ASSEMBLY, STITCHING, TIE, DONE }

    public enum Tension { NONE, SLACK, CLEAN, PUCKERED }

    public enum Outcome { ACCEPTED, IGNORED, WRONG_STAGE, OUT_OF_PLACE, OUT_OF_ORDER, DONE }

    private final int id;
    private final Kind kind;
    @Nullable
    private UUID owner;
    private String ownerName;
    private final ResourceLocation recipeId;
    private final ItemStack result;
    private final Finish finish;
    @Nullable
    private final TraitRef trait;
    private final int integrity;
    private final int leatherUsed;
    private final MaterialLedger ledger;
    private final WorkPattern pattern;
    private Stage stage = Stage.RESERVED;
    private int revision;

    private final float[] frontier;
    private final float[] errSum;
    private final float[] errWeight;
    private final float[] stray;
    private final List<float[]> strayMarks = new ArrayList<>();

    private final boolean[] seated;
    private final float[] seatX;
    private final float[] seatY;
    private final float[] seatRot;
    private final float[] seatQuality;

    private final float[] stitchQuality;
    private final byte[] tension;
    private final byte[] redo;
    private final float[] releaseX;
    private final float[] releaseY;
    /** The stitch being pulled (punctured, thread in hand), or -1. Kept only while the operator holds it. */
    private int pending = -1;
    private float pullX;
    private float pullY;

    private int knifeSlot = -1;
    private int awlSlot = -1;
    private boolean knifeUsed;
    private boolean awlUsed;
    private int repairPoints;

    // transient cut stroke state
    private boolean cutting;
    private float lastX;
    private float lastY;

    private LeatherworkJob(int id, Kind kind, @Nullable UUID owner, String ownerName, ResourceLocation recipeId, ItemStack result,
                           Finish finish, @Nullable TraitRef trait, int integrity, int leatherUsed, MaterialLedger ledger, WorkPattern pattern) {
        this.id = id;
        this.kind = kind;
        this.owner = owner;
        this.ownerName = ownerName;
        this.recipeId = recipeId;
        this.result = result;
        this.finish = finish;
        this.trait = trait;
        this.integrity = integrity;
        this.leatherUsed = leatherUsed;
        this.ledger = ledger;
        this.pattern = pattern;
        int cuts = pattern.cuts().size();
        frontier = new float[cuts];
        errSum = new float[cuts];
        errWeight = new float[cuts];
        stray = new float[cuts];
        int panels = pattern.panels().size();
        seated = new boolean[panels];
        seatX = new float[panels];
        seatY = new float[panels];
        seatRot = new float[panels];
        seatQuality = new float[panels];
        for (int p = 0; p < panels; p++) {
            WorkPattern.Placement target = pattern.placement(p);
            seatX[p] = target.x();
            seatY[p] = target.y();
            seatRot[p] = target.rot();
        }
        int actions = pattern.totalActions();
        stitchQuality = new float[actions];
        Arrays.fill(stitchQuality, Float.NaN);
        tension = new byte[actions];
        redo = new byte[actions];
        releaseX = new float[actions];
        releaseY = new float[actions];
    }

    public static LeatherworkJob craft(UUID owner, String ownerName, ResourceLocation recipeId, ItemStack result, Finish finish,
                                       @Nullable TraitRef trait, int integrity, int leatherUsed, MaterialLedger ledger, WorkPattern pattern) {
        return new LeatherworkJob(newId(), Kind.CRAFT, owner, ownerName, recipeId, result.copy(), finish, trait, integrity, leatherUsed, ledger, pattern);
    }

    public static LeatherworkJob repair(UUID owner, String ownerName, ItemStack item, MaterialLedger ledger, int repairPoints, WorkPattern pattern) {
        Finish finish = com.otectus.immersivetanning.leather.LeatherworkData.get(item).map(d -> d.finish()).orElse(Finish.HARNESS);
        LeatherworkJob job = new LeatherworkJob(newId(), Kind.REPAIR, owner, ownerName, WorkPattern.PATCH_ID, item.copy(), finish, null, 0, 0, ledger, pattern);
        job.repairPoints = repairPoints;
        return job;
    }

    private static int newId() {
        return new Random().nextInt(Integer.MAX_VALUE - 1) + 1;
    }

    // ------------------------------------------------------------------ identity

    public int id() { return id; }
    public Kind kind() { return kind; }
    @Nullable public UUID owner() { return owner; }
    public String ownerName() { return ownerName; }
    public ResourceLocation recipeId() { return recipeId; }
    /** The piece as it will be (crafted) or the item being mended (repair). A copy. */
    public ItemStack result() { return result.copy(); }
    public Finish finish() { return finish; }
    @Nullable public TraitRef trait() { return trait; }
    public int integrity() { return integrity; }
    public int leatherUsed() { return leatherUsed; }
    public MaterialLedger ledger() { return ledger; }
    public WorkPattern pattern() { return pattern; }
    public Stage stage() { return stage; }

    /** The stage as worked: a prepared piece without cuts (a repair patch) goes straight to seating. */
    public Stage workStage() {
        return stage == Stage.RESERVED && frontier.length == 0 ? Stage.ASSEMBLY : stage;
    }
    public int revision() { return revision; }
    public int repairPoints() { return repairPoints; }
    public boolean started() { return stage != Stage.RESERVED; }

    public boolean ownedBy(UUID player) {
        return owner == null || owner.equals(player);
    }

    /** A deliberate handoff: the next player who works the piece becomes its owner. */
    public void release() {
        owner = null;
        ownerName = "";
        revision++;
    }

    public void claim(UUID player, String name) {
        owner = player;
        ownerName = name;
        revision++;
    }

    public int knifeSlot() { return knifeSlot; }
    public int awlSlot() { return awlSlot; }
    public void reserveKnife(int slot) { if (knifeSlot < 0) knifeSlot = slot; knifeUsed = true; }
    public void reserveAwl(int slot) { if (awlSlot < 0) awlSlot = slot; awlUsed = true; }
    public boolean knifeUsed() { return knifeUsed; }
    public boolean awlUsed() { return awlUsed; }

    // ------------------------------------------------------------------ cutting

    /** The cut being worked, or -1 once every cut is done. */
    public int activeCut() {
        for (int c = 0; c < frontier.length; c++) {
            if (!cutDone(c)) return c;
        }
        return -1;
    }

    public boolean cutDone(int c) {
        return frontier[c] >= StitchPattern.length(pattern.cutPath(c)) - 1.0E-4F;
    }

    public float cutFrontier(int c) { return frontier[c]; }
    public float cutProgress(int c) {
        float length = StitchPattern.length(pattern.cutPath(c));
        return length <= 0F ? 1F : Mth.clamp(frontier[c] / length, 0F, 1F);
    }
    public List<float[]> strayMarks() { return List.copyOf(strayMarks); }

    /**
     * One batch of blade samples on the work plane (already bounded and trimmed by the handler). The blade only
     * advances its cut along the path, near the line and from where the cut stopped; it may lift and resume. Blade
     * work off the line near it is a rough edge.
     */
    public Outcome cut(boolean begins, boolean ends, float[] xs, float[] ys, float toleranceScale) {
        if (stage != Stage.RESERVED && stage != Stage.CUTTING) return Outcome.WRONG_STAGE;
        boolean changed = false;
        for (int i = 0; i < xs.length; i++) {
            float x = xs[i];
            float y = ys[i];
            if (i == 0 && begins) {
                cutting = true;
                lastX = x;
                lastY = y;
                continue;
            }
            if (!cutting) break;
            int c = activeCut();
            if (c < 0) break;
            float[] path = pattern.cutPath(c);
            float length = StitchPattern.length(path);
            float tol = pattern.cuts().get(c).tolerance() * toleranceScale;
            float seg = Mth.sqrt((x - lastX) * (x - lastX) + (y - lastY) * (y - lastY));
            float[] proj = project(path, x, y);
            float s = proj[0];
            float d = proj[1];
            if (d <= tol) {
                if (s > frontier[c] && s <= frontier[c] + tol * 2.5F && project(path, lastX, lastY)[0] >= frontier[c] - tol * 1.5F) {
                    float advance = s - frontier[c];
                    frontier[c] = s;
                    errSum[c] += (d / tol) * advance;
                    errWeight[c] += advance;
                    if (stage == Stage.RESERVED) stage = Stage.CUTTING;
                    changed = true;
                }
            } else if (d <= tol * 4F && seg > 0F) {
                stray[c] += seg;
                if (strayMarks.size() < MAX_STRAY_MARKS
                        && (strayMarks.isEmpty() || dist(strayMarks.get(strayMarks.size() - 1), x, y) > 0.03F)) {
                    strayMarks.add(new float[]{x, y});
                }
                if (stage == Stage.RESERVED) stage = Stage.CUTTING;
                changed = true;
            }
            if (frontier[c] >= length - tol * 0.35F) {
                frontier[c] = length;
                changed = true;
            }
            lastX = x;
            lastY = y;
        }
        if (ends) cutting = false;
        if (activeCut() < 0 && (stage == Stage.CUTTING || stage == Stage.RESERVED)) {
            stage = Stage.ASSEMBLY;
            changed = true;
        }
        if (changed) revision++;
        return changed ? Outcome.ACCEPTED : Outcome.IGNORED;
    }

    /** Arc position along a polyline of the nearest point and the distance to it: {s, d}. */
    public static float[] project(float[] path, float x, float y) {
        float bestD = Float.MAX_VALUE;
        float bestS = 0F;
        float arc = 0F;
        for (int i = 2; i + 1 < path.length; i += 2) {
            float ax = path[i - 2];
            float ay = path[i - 1];
            float dx = path[i] - ax;
            float dy = path[i + 1] - ay;
            float len2 = dx * dx + dy * dy;
            float len = Mth.sqrt(len2);
            float t = len2 <= 0F ? 0F : Mth.clamp(((x - ax) * dx + (y - ay) * dy) / len2, 0F, 1F);
            float qx = ax + dx * t - x;
            float qy = ay + dy * t - y;
            float d = Mth.sqrt(qx * qx + qy * qy);
            if (d < bestD) {
                bestD = d;
                bestS = arc + t * len;
            }
            arc += len;
        }
        return new float[]{bestS, bestD};
    }

    private static float dist(float[] p, float x, float y) {
        return Mth.sqrt((p[0] - x) * (p[0] - x) + (p[1] - y) * (p[1] - y));
    }

    // ------------------------------------------------------------------ assembly

    public boolean seated(int panel) { return seated[panel]; }
    public float seatX(int panel) { return seatX[panel]; }
    public float seatY(int panel) { return seatY[panel]; }
    public float seatRot(int panel) { return seatRot[panel]; }

    /** Seats a panel when it was let go near its place, turned about right. The panel settles exactly into place. */
    public Outcome place(int panel, float x, float y, float rot, float toleranceScale) {
        if (workStage() != Stage.ASSEMBLY) return Outcome.WRONG_STAGE;
        if (panel < 0 || panel >= seated.length || seated[panel]) return Outcome.IGNORED;
        WorkPattern.Placement target = pattern.placement(panel);
        float posTol = target.posTol() * toleranceScale;
        float rotTol = target.rotTol() * toleranceScale;
        float dPos = Mth.sqrt((x - target.x()) * (x - target.x()) + (y - target.y()) * (y - target.y()));
        float dRot = Math.abs(Mth.wrapDegrees(rot - target.rot()));
        if (dPos > posTol || dRot > rotTol) return Outcome.OUT_OF_PLACE;
        if (stage == Stage.RESERVED) stage = Stage.ASSEMBLY;
        seated[panel] = true;
        seatX[panel] = target.x();
        seatY[panel] = target.y();
        seatRot[panel] = target.rot();
        seatQuality[panel] = 1F - 0.5F * Math.max(dPos / posTol, dRot / rotTol);
        boolean all = true;
        for (boolean s : seated) all &= s;
        if (all) stage = Stage.STITCHING;
        revision++;
        return Outcome.ACCEPTED;
    }

    // ------------------------------------------------------------------ stitching

    /** The next stitch to make, or -1 once every stitch is made. */
    public int nextStitch() {
        for (int a = 0; a < stitchQuality.length; a++) if (Float.isNaN(stitchQuality[a])) return a;
        return -1;
    }

    public int pending() { return pending; }
    public float pullX() { return pullX; }
    public float pullY() { return pullY; }
    public float stitchQuality(int action) { return stitchQuality[action]; }
    public Tension tension(int action) { return Tension.values()[tension[action]]; }
    public int redoCount(int action) { return redo[action]; }
    public float releaseX(int action) { return releaseX[action]; }
    public float releaseY(int action) { return releaseY[action]; }
    public int stitchCount() { return stitchQuality.length; }

    /** The hole pair and pull frame of a stitch (see {@link WorkPattern#stitch}). */
    public float[] stitchFrame(int action) {
        return pattern.stitch(action, seatX, seatY, seatRot);
    }

    /** The awl presses through the next hole pair. */
    public Outcome puncture(int action, float x, float y, float toleranceScale) {
        if (stage != Stage.STITCHING) return Outcome.WRONG_STAGE;
        if (pending >= 0) return Outcome.IGNORED;
        if (action != nextStitch()) return Outcome.OUT_OF_ORDER;
        float[] frame = stitchFrame(action);
        float d = Mth.sqrt((x - frame[4]) * (x - frame[4]) + (y - frame[5]) * (y - frame[5]));
        if (d > HOLE_TOLERANCE * toleranceScale) return Outcome.OUT_OF_PLACE;
        pending = action;
        pullX = x;
        pullY = y;
        revision++;
        return Outcome.ACCEPTED;
    }

    /** The thread is drawn to a point (presentation for watchers; the release decides). */
    public Outcome pull(float x, float y) {
        if (pending < 0) return Outcome.IGNORED;
        pullX = x;
        pullY = y;
        return Outcome.ACCEPTED;
    }

    /** The thread is let go: how far it was drawn away from the seam decides the stitch. */
    public Outcome releaseThread(float x, float y, boolean assisted) {
        if (stage != Stage.STITCHING || pending < 0) return Outcome.IGNORED;
        int action = pending;
        float[] frame = stitchFrame(action);
        WorkPattern.Join join = pattern.joinOf(action);
        float vx = x - frame[4];
        float vy = y - frame[5];
        float across = Math.abs(vx * frame[6] + vy * frame[7]);
        float along = Mth.sqrt(Math.max(0F, vx * vx + vy * vy - across * across));
        float drawn = Math.max(0F, across - 0.5F * along);
        float widen = assisted ? 0.25F : 0F;
        float min = join.pullMin() * (1F - widen);
        float max = join.pullMax() * (1F + widen);
        Tension t;
        float q;
        if (drawn < min) {
            t = Tension.SLACK;
            q = 0.35F + 0.4F * drawn / min;
        } else if (drawn <= join.pullIdeal()) {
            t = Tension.CLEAN;
            q = 0.8F + 0.2F * (drawn - min) / Math.max(1.0E-4F, join.pullIdeal() - min);
        } else if (drawn <= max) {
            t = Tension.CLEAN;
            q = 1F - 0.2F * (drawn - join.pullIdeal()) / Math.max(1.0E-4F, max - join.pullIdeal());
        } else {
            t = Tension.PUCKERED;
            q = Math.max(0.3F, 0.8F - 0.5F * (drawn - max) / max);
        }
        if (redo[action] > 0) q = Math.min(q, REDONE_CAP);
        stitchQuality[action] = Mth.clamp(q, 0F, 1F);
        tension[action] = (byte) t.ordinal();
        releaseX[action] = x;
        releaseY[action] = y;
        pending = -1;
        if (nextStitch() < 0) stage = Stage.TIE;
        revision++;
        return Outcome.ACCEPTED;
    }

    /** Lets go of a thread without deciding the stitch (the operator left): the stitch is simply not made yet. */
    public void dropThread() {
        if (pending >= 0) {
            pending = -1;
            revision++;
        }
    }

    /** The last stitch made, while the next is untouched, may be loosened and made again (a few times at most). */
    public Outcome loosen(int action) {
        if (pending >= 0) return Outcome.IGNORED;
        int last = -1;
        for (int a = 0; a < stitchQuality.length; a++) if (!Float.isNaN(stitchQuality[a])) last = a;
        if (action != last || action < 0 || redo[action] >= MAX_REDO) return Outcome.OUT_OF_ORDER;
        if (stage != Stage.STITCHING && stage != Stage.TIE) return Outcome.WRONG_STAGE;
        stitchQuality[action] = Float.NaN;
        tension[action] = (byte) Tension.NONE.ordinal();
        redo[action]++;
        stage = Stage.STITCHING;
        revision++;
        return Outcome.ACCEPTED;
    }

    /** The final knot. */
    public Outcome tie() {
        if (stage != Stage.TIE) return Outcome.WRONG_STAGE;
        stage = Stage.DONE;
        revision++;
        return Outcome.DONE;
    }

    // ------------------------------------------------------------------ scores

    /** Cutting quality 0..1: closeness to the lines and few rough edges, an unfinished cut counting only its share. */
    public float cuttingScore() {
        if (frontier.length == 0) return 1F;
        float sum = 0F;
        for (int c = 0; c < frontier.length; c++) {
            float length = Math.max(1.0E-4F, StitchPattern.length(pattern.cutPath(c)));
            float accuracy = errWeight[c] <= 0F ? 0.75F : 1F - 0.45F * Mth.clamp(errSum[c] / errWeight[c], 0F, 1F);
            float rough = Math.min(0.4F, 0.5F * stray[c] / length);
            sum += Mth.clamp(accuracy - rough, 0F, 1F) * cutProgress(c);
        }
        return sum / frontier.length;
    }

    public float assemblyScore() {
        if (seated.length == 0) return 1F;
        float sum = 0F;
        for (int p = 0; p < seated.length; p++) if (seated[p]) sum += seatQuality[p];
        return sum / seated.length;
    }

    public float stitchingScore() {
        if (stitchQuality.length == 0) return 1F;
        float sum = 0F;
        for (float q : stitchQuality) if (!Float.isNaN(q)) sum += q;
        return sum / stitchQuality.length;
    }

    /** Workmanship 0..100 from the three components and the pattern's weights (components without work drop out). */
    public int workmanship() {
        float wc = frontier.length == 0 ? 0F : pattern.cuttingWeight();
        float wa = seated.length == 0 ? 0F : pattern.assemblyWeight();
        float ws = stitchQuality.length == 0 ? 0F : pattern.stitchingWeight();
        float sum = wc + wa + ws;
        if (sum <= 0F) return 50;
        float raw = (wc * cuttingScore() + wa * assemblyScore() + ws * stitchingScore()) / sum;
        return Mth.clamp(Math.round(raw * 100F), 0, 100);
    }

    /** Translation keys of a short, concrete account of the work: "Even seams; rough cut edge." */
    public List<String> explanation() {
        List<String> out = new ArrayList<>();
        int slack = 0;
        int puckered = 0;
        for (byte t : tension) {
            if (t == Tension.SLACK.ordinal()) slack++;
            if (t == Tension.PUCKERED.ordinal()) puckered++;
        }
        int n = Math.max(1, stitchQuality.length);
        if (slack * 3 >= n) out.add("loose_seams");
        else if (puckered * 3 >= n) out.add("puckered_seams");
        else if (stitchingScore() >= 0.85F) out.add("even_seams");
        else out.add("uneven_seams");
        if (frontier.length > 0) {
            float cut = cuttingScore();
            if (cut < 0.7F) out.add("rough_cut");
            else if (cut >= 0.9F) out.add("clean_cut");
        }
        if (seated.length > 0 && assemblyScore() < 0.75F) out.add("panels_off");
        return out;
    }

    // ------------------------------------------------------------------ persistence

    public CompoundTag save() {
        CompoundTag tag = saveProgress();
        tag.put("Ledger", ledger.save());
        tag.put("Result", result.save(new CompoundTag()));
        tag.putInt("KnifeSlot", knifeSlot);
        tag.putInt("AwlSlot", awlSlot);
        tag.putBoolean("KnifeUsed", knifeUsed);
        tag.putBoolean("AwlUsed", awlUsed);
        if (owner != null) tag.putUUID("Owner", owner);
        return tag;
    }

    /**
     * What a client needs to draw and explain the job: progress and pattern, the piece (a display copy), the owner's
     * name; never the materials held or other players' data.
     */
    public CompoundTag saveClient() {
        CompoundTag tag = saveProgress();
        tag.put("Result", displayCopy(result).save(new CompoundTag()));
        tag.putInt("LedgerCount", ledger.count(MaterialLedger.Role.LEATHER) + ledger.count(MaterialLedger.Role.SCRAP));
        if (owner != null) tag.putUUID("Owner", owner);
        return tag;
    }

    private CompoundTag saveProgress() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", VERSION);
        tag.putInt("Id", id);
        tag.putString("Kind", kind.name());
        tag.putString("OwnerName", ownerName);
        tag.putString("Recipe", recipeId.toString());
        tag.putString("Finish", finish.getSerializedName());
        if (trait != null) trait.save(tag);
        tag.putInt("Integrity", integrity);
        tag.putInt("LeatherUsed", leatherUsed);
        tag.put("Pattern", pattern.save());
        tag.putString("Stage", stage.name());
        tag.putInt("Revision", revision);
        tag.putInt("RepairPoints", repairPoints);
        putFloats(tag, "Frontier", frontier);
        putFloats(tag, "ErrSum", errSum);
        putFloats(tag, "ErrWeight", errWeight);
        putFloats(tag, "Stray", stray);
        float[] marks = new float[strayMarks.size() * 2];
        for (int i = 0; i < strayMarks.size(); i++) {
            marks[i * 2] = strayMarks.get(i)[0];
            marks[i * 2 + 1] = strayMarks.get(i)[1];
        }
        putFloats(tag, "StrayMarks", marks);
        byte[] seats = new byte[seated.length];
        for (int p = 0; p < seated.length; p++) seats[p] = (byte) (seated[p] ? 1 : 0);
        tag.putByteArray("Seated", seats);
        putFloats(tag, "SeatX", seatX);
        putFloats(tag, "SeatY", seatY);
        putFloats(tag, "SeatRot", seatRot);
        putFloats(tag, "SeatQ", seatQuality);
        putFloats(tag, "StitchQ", stitchQuality);
        tag.putByteArray("Tension", tension);
        tag.putByteArray("Redo", redo);
        putFloats(tag, "ReleaseX", releaseX);
        putFloats(tag, "ReleaseY", releaseY);
        tag.putInt("Pending", pending);
        tag.putFloat("PullX", pullX);
        tag.putFloat("PullY", pullY);
        return tag;
    }

    /** Reads a saved job; null when it cannot be read at all (the caller then recovers the materials it can find). */
    @Nullable
    public static LeatherworkJob load(CompoundTag tag) {
        if (tag.getInt("Version") != VERSION) return null;
        WorkPattern pattern = WorkPattern.load(tag.getCompound("Pattern"));
        Finish finish = Finish.byName(tag.getString("Finish"));
        if (pattern == null || finish == null) return null;
        Kind kind;
        Stage stage;
        try {
            kind = Kind.valueOf(tag.getString("Kind"));
            stage = Stage.valueOf(tag.getString("Stage"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        UUID owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ResourceLocation recipe = ResourceLocation.tryParse(tag.getString("Recipe"));
        LeatherworkJob job = new LeatherworkJob(tag.getInt("Id"), kind, owner, tag.getString("OwnerName"),
                recipe != null ? recipe : WorkPattern.GENERIC, ItemStack.of(tag.getCompound("Result")), finish, TraitRef.load(tag),
                tag.getInt("Integrity"), tag.getInt("LeatherUsed"), MaterialLedger.load(tag.getList("Ledger", Tag.TAG_COMPOUND)), pattern);
        job.stage = stage;
        job.revision = tag.getInt("Revision");
        job.repairPoints = tag.getInt("RepairPoints");
        copy(tag, "Frontier", job.frontier);
        copy(tag, "ErrSum", job.errSum);
        copy(tag, "ErrWeight", job.errWeight);
        copy(tag, "Stray", job.stray);
        float[] marks = getFloats(tag, "StrayMarks");
        for (int i = 0; i + 1 < marks.length && job.strayMarks.size() < MAX_STRAY_MARKS; i += 2) job.strayMarks.add(new float[]{marks[i], marks[i + 1]});
        byte[] seats = tag.getByteArray("Seated");
        for (int p = 0; p < Math.min(seats.length, job.seated.length); p++) job.seated[p] = seats[p] != 0;
        copy(tag, "SeatX", job.seatX);
        copy(tag, "SeatY", job.seatY);
        copy(tag, "SeatRot", job.seatRot);
        copy(tag, "SeatQ", job.seatQuality);
        float[] q = getFloats(tag, "StitchQ");
        for (int a = 0; a < Math.min(q.length, job.stitchQuality.length); a++) {
            job.stitchQuality[a] = Float.isNaN(q[a]) ? Float.NaN : Mth.clamp(q[a], 0F, 1F);
        }
        byte[] t = tag.getByteArray("Tension");
        for (int a = 0; a < Math.min(t.length, job.tension.length); a++) job.tension[a] = (byte) Mth.clamp(t[a], 0, Tension.values().length - 1);
        byte[] r = tag.getByteArray("Redo");
        System.arraycopy(r, 0, job.redo, 0, Math.min(r.length, job.redo.length));
        copy(tag, "ReleaseX", job.releaseX);
        copy(tag, "ReleaseY", job.releaseY);
        job.pending = Mth.clamp(tag.getInt("Pending"), -1, job.stitchQuality.length - 1);
        job.pullX = tag.getFloat("PullX");
        job.pullY = tag.getFloat("PullY");
        job.knifeSlot = tag.contains("KnifeSlot") ? tag.getInt("KnifeSlot") : -1;
        job.awlSlot = tag.contains("AwlSlot") ? tag.getInt("AwlSlot") : -1;
        job.knifeUsed = tag.getBoolean("KnifeUsed");
        job.awlUsed = tag.getBoolean("AwlUsed");
        return job;
    }

    /** Leaves the operator-only state (a thread in hand) when the job is saved for later or carried away. */
    public void settle() {
        dropThread();
        cutting = false;
    }

    /** The piece as clients may see it: the item, its damage, name, colour and glint; nothing else of its data. */
    public static ItemStack displayCopy(ItemStack stack) {
        ItemStack copy = new ItemStack(stack.getItem(), Math.max(1, stack.getCount()));
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            CompoundTag safe = new CompoundTag();
            for (String key : new String[]{"Damage", "display", "Enchantments", "CustomModelData", "Trim"}) {
                if (tag.contains(key)) safe.put(key, tag.get(key).copy());
            }
            if (!safe.isEmpty()) copy.setTag(safe);
        }
        return copy;
    }

    private static void putFloats(CompoundTag tag, String key, float[] values) {
        int[] bits = new int[values.length];
        for (int i = 0; i < values.length; i++) bits[i] = Float.floatToIntBits(values[i]);
        tag.putIntArray(key, bits);
    }

    private static float[] getFloats(CompoundTag tag, String key) {
        int[] bits = tag.getIntArray(key);
        float[] out = new float[bits.length];
        for (int i = 0; i < bits.length; i++) out[i] = Float.intBitsToFloat(bits[i]);
        return out;
    }

    private static void copy(CompoundTag tag, String key, float[] into) {
        float[] values = getFloats(tag, key);
        for (int i = 0; i < Math.min(values.length, into.length); i++) into[i] = Float.isFinite(values[i]) ? values[i] : 0F;
    }

    /** All saved stacks a damaged job record can still name, for recovery. */
    public static List<net.minecraft.world.item.ItemStack> salvage(CompoundTag tag) {
        List<ItemStack> out = new ArrayList<>();
        ListTag list = tag.getList("Ledger", Tag.TAG_COMPOUND);
        for (ItemStack s : MaterialLedger.load(list).drain()) if (!s.isEmpty()) out.add(s);
        return out;
    }
}
