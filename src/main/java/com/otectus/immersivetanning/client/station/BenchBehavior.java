package com.otectus.immersivetanning.client.station;

import com.otectus.immersivetanning.blockentity.LeatherworkersBenchBlockEntity;
import com.otectus.immersivetanning.config.ClientConfig;
import com.otectus.immersivetanning.item.UnfinishedLeatherworkItem;
import com.otectus.immersivetanning.leather.FieldRepair;
import com.otectus.immersivetanning.leather.LeatherData;
import com.otectus.immersivetanning.minigame.StitchPattern;
import com.otectus.immersivetanning.network.packet.BenchOffersPacket;
import com.otectus.immersivetanning.network.packet.OperatorPosePacket;
import com.otectus.immersivetanning.network.packet.StationAckPacket;
import com.otectus.immersivetanning.network.packet.StationCommandPacket;
import com.otectus.immersivetanning.network.packet.WorkResultPacket;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.station.BenchGeometry;
import com.otectus.immersivetanning.work.LeatherworkJob;
import com.otectus.immersivetanning.work.WorkAction;
import com.otectus.immersivetanning.work.WorkPattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Working the Leatherworker's Bench. The pattern book (on the bench) prepares a piece; then the knife traces each cut
 * line on the leather, freed panels are lifted from the far edge and seated on the jig (turned with the scroll wheel or
 * the rotate keys), and the awl is pressed through each hole pair and the thread drawn away from the seam until it
 * closes; the last knot ties it off and the piece waits on the output pad. Every step is judged on the server from
 * where the tools really went; the client draws its prediction until the bench's state confirms it.
 */
public final class BenchBehavior extends StationBehavior {
    private enum Tool { NONE, KNIFE, PANEL, AWL }

    private Tool tool = Tool.NONE;
    @Nullable
    private Gesture gesture;
    private float wx = 0.5F;
    private float wy = 0.5F;
    private boolean onPlane;
    private int carried = -1;
    private float carryX;
    private float carryY;
    private float carryRot;
    private float grabX;
    private float grabY;
    private int stitching = -1;
    /** The stitch whose thread was just let go: the synced job shows it done a tick or two later. */
    private int releasedStitch = -1;
    private long releasedMs;
    private boolean bookOpen;
    private final PatternBook book = new PatternBook();
    private final List<Hud.Button> buttons = new ArrayList<>();
    @Nullable
    private WorkResultPacket result;
    private long resultMs;
    private long rejectMs;

    public BenchBehavior(StationFocus.Session session) {
        super(session);
    }

    @Nullable
    private LeatherworkersBenchBlockEntity bench() {
        return station() instanceof LeatherworkersBenchBlockEntity bench ? bench : null;
    }

    @Nullable
    public LeatherworkJob job() {
        LeatherworkersBenchBlockEntity bench = bench();
        return bench == null ? null : bench.job();
    }

    @Override
    public StationShots.Task task() {
        LeatherworkJob job = job();
        if (job == null) {
            LeatherworkersBenchBlockEntity bench = bench();
            return bench != null && bench.hasOutput() && result != null && System.currentTimeMillis() - resultMs < 2500L
                    && ClientConfig.get(ClientConfig.CAMERA_MODE) == ClientConfig.CameraMode.CINEMATIC ? StationShots.Task.RESULT : StationShots.Task.BENCH;
        }
        return job.workStage() == LeatherworkJob.Stage.STITCHING || job.workStage() == LeatherworkJob.Stage.TIE ? StationShots.Task.STITCHING : StationShots.Task.BENCH;
    }

    @Override
    public boolean gestureActive() {
        return (gesture != null && gesture.open()) || tool == Tool.PANEL;
    }

    // ------------------------------------------------------------------ geometry of the job as drawn

    /** Where a panel lies now (cutting layout, staging, carried, or seated): {x, y, rot}. */
    public float[] panelPlace(LeatherworkJob job, int panel) {
        WorkPattern pattern = job.pattern();
        WorkPattern.Panel p = pattern.panels().get(panel);
        LeatherworkJob.Stage stage = job.workStage();
        if (stage == LeatherworkJob.Stage.RESERVED || stage == LeatherworkJob.Stage.CUTTING) return new float[]{p.layoutX(), p.layoutY(), p.layoutRot()};
        if (job.seated(panel)) return new float[]{job.seatX(panel), job.seatY(panel), job.seatRot(panel)};
        if (panel == carried && tool == Tool.PANEL) return new float[]{carryX, carryY, carryRot};
        ClientWork.RemotePose remote = ClientWork.remote(pos);
        if (remote != null && remote.held == panel) {
            float[] w = toPlane(remote.x, remote.z);
            return new float[]{w[0], w[1], remote.rot};
        }
        return pattern.staging(panel);
    }

    private static float[] toPlane(float lx, float lz) {
        return new float[]{(BenchGeometry.LEFT - lx) / BenchGeometry.SIZE, (BenchGeometry.FAR - lz) / BenchGeometry.SIZE};
    }

    public int carried() {
        return tool == Tool.PANEL ? carried : -1;
    }

    private static boolean inside(float[] poly, float x, float y) {
        boolean in = false;
        int n = poly.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            float xi = poly[i * 2], yi = poly[i * 2 + 1], xj = poly[j * 2], yj = poly[j * 2 + 1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi + 1.0E-9F) + xi) in = !in;
        }
        return in;
    }

    private int panelAt(LeatherworkJob job, float x, float y) {
        WorkPattern pattern = job.pattern();
        for (int p = pattern.panels().size() - 1; p >= 0; p--) {
            if (job.seated(p)) continue;
            float[] at = panelPlace(job, p);
            WorkPattern.Panel panel = pattern.panels().get(p);
            if (inside(WorkPattern.placePath(panel, panel.outline(), at[0], at[1], at[2]), x, y)) return p;
        }
        return -1;
    }

    private static AABB planeBox(float x, float y, float r) {
        Vec3 c = BenchGeometry.point(x, y);
        double px = r * BenchGeometry.SIZE;
        return new AABB(c.x - px, c.y - 0.4, c.z - px, c.x + px, c.y + 0.9, c.z + px);
    }

    // ------------------------------------------------------------------ targets

    @Override
    public List<Target> targets() {
        LeatherworkersBenchBlockEntity bench = bench();
        List<Target> list = new ArrayList<>();
        if (bench == null) return list;
        boolean op = session.operator;
        LeatherworkJob job = bench.job();
        LocalPlayer player = Minecraft.getInstance().player;
        if (bench.hasOutput()) {
            list.add(new Target("output", BenchGeometry.OUTPUT_PAD, Component.translatable("verb.immersive_tanning.collect", bench.output().size()),
                    op && (bench.outputOwner() == null || bench.outputOwner().equals(player.getUUID()) || player.hasPermissions(2))));
        }
        if (job == null) {
            list.add(new Target("book", BenchGeometry.BOOK, Component.translatable("verb.immersive_tanning.book"), op));
            ItemStack held = player.getMainHandItem();
            if (held.getItem() instanceof UnfinishedLeatherworkItem) {
                list.add(new Target("mat", planeBox(0.5F, 0.5F, 0.45F), Component.translatable("verb.immersive_tanning.lay_workpiece"), op));
            } else if (FieldRepair.isRepairable(held) && held.isDamaged()) {
                boolean scraps = player.getInventory().countItem(ModItems.LEATHER_SCRAPS.get()) > 0;
                list.add(new Target("mat", planeBox(0.5F, 0.5F, 0.45F), Component.translatable(scraps ? "verb.immersive_tanning.lay_repair"
                        : "verb.immersive_tanning.need_scraps"), op && scraps));
            }
            return list;
        }
        WorkPattern pattern = job.pattern();
        switch (job.workStage()) {
            case RESERVED, CUTTING -> {
                int c = job.activeCut();
                if (c >= 0) {
                    float[] path = pattern.cutPath(c);
                    float[] at = StitchPattern.pointAt(path, job.cutProgress(c));
                    list.add(new Target("cut", planeBox(at[0], at[1], 0.06F), Component.translatable(hasKnife() ? "verb.immersive_tanning.cut"
                            : "verb.immersive_tanning.need_knife_cut", c + 1, pattern.cuts().size()), op && hasKnife()));
                }
            }
            case ASSEMBLY -> {
                for (int p = 0; p < pattern.panels().size(); p++) {
                    if (job.seated(p)) continue;
                    float[] at = panelPlace(job, p);
                    list.add(new Target("panel" + p, planeBox(at[0], at[1], 0.08F), Component.translatable("verb.immersive_tanning.lift_panel",
                            panelName(pattern.panels().get(p))), op));
                }
            }
            case STITCHING -> {
                int next = job.nextStitch();
                if (next >= 0 && job.pending() < 0) {
                    float[] f = job.stitchFrame(next);
                    list.add(new Target("hole", planeBox(f[4], f[5], 0.045F), Component.translatable(hasAwl() ? "verb.immersive_tanning.puncture"
                            : "verb.immersive_tanning.need_awl", next + 1, job.stitchCount()), op && hasAwl()));
                }
                int last = lastStitch(job);
                if (last >= 0 && job.pending() < 0 && job.redoCount(last) < LeatherworkJob.MAX_REDO) {
                    float[] f = job.stitchFrame(last);
                    list.add(new Target("loosen", planeBox(f[4], f[5], 0.035F), Component.translatable("verb.immersive_tanning.loosen"), op));
                }
            }
            case TIE -> {
                int last = lastStitch(job);
                float[] f = job.stitchFrame(Math.max(0, last));
                list.add(new Target("tie", planeBox(f[4] + 0.04F, f[5] + 0.04F, 0.05F), Component.translatable("verb.immersive_tanning.tie"), op));
                if (last >= 0 && job.redoCount(last) < LeatherworkJob.MAX_REDO) {
                    list.add(new Target("loosen", planeBox(f[4], f[5], 0.03F), Component.translatable("verb.immersive_tanning.loosen"), op));
                }
            }
            default -> {
            }
        }
        return list;
    }

    private static Component panelName(WorkPattern.Panel panel) {
        String key = "panel.immersive_tanning." + panel.id();
        return net.minecraft.client.resources.language.I18n.exists(key) ? Component.translatable(key) : Component.literal(panel.id().replace('_', ' '));
    }

    private static int lastStitch(LeatherworkJob job) {
        int last = -1;
        for (int a = 0; a < job.stitchCount(); a++) if (!Float.isNaN(job.stitchQuality(a))) last = a;
        return last;
    }

    private boolean hasKnife() {
        // By kind, not by stack: a worn or renamed knife (it has tags) is still a knife.
        return Minecraft.getInstance().player.getInventory().hasAnyMatching(com.otectus.immersivetanning.work.WorkRules::isKnife)
                || com.otectus.immersivetanning.work.WorkRules.isKnife(Minecraft.getInstance().player.getOffhandItem());
    }

    private boolean hasAwl() {
        return Minecraft.getInstance().player.getInventory().hasAnyMatching(com.otectus.immersivetanning.work.WorkRules::isAwl)
                || com.otectus.immersivetanning.work.WorkRules.isAwl(Minecraft.getInstance().player.getOffhandItem());
    }

    @Override
    public boolean drawerTakes(ItemStack stack) {
        return stack.is(ModItems.AWL.get()) || stack.is(ModItems.TANNERS_KNIFE.get()) || stack.is(ModItems.LEATHER_SCRAPS.get())
                || stack.getItem() instanceof UnfinishedLeatherworkItem || LeatherData.get(stack).isPresent()
                || (FieldRepair.isRepairable(stack) && stack.isDamaged());
    }

    // ------------------------------------------------------------------ pointer

    @Override
    public void pointer(@Nullable PointerRay.Local ray, double guiX, double guiY) {
        super.pointer(ray, guiX, guiY);
        if (ray == null) return;
        float[] hit = BenchGeometry.intersectPlane(ray.origin(), ray.dir());
        onPlane = hit != null && BenchGeometry.inPlane(hit[0], hit[1], 0.05F);
        if (hit == null) return;
        float sensitivity = (float) (double) ClientConfig.get(ClientConfig.POINTER_SENSITIVITY);
        boolean down = gesture != null && gesture.open();
        float nx = Mth.clamp(hit[0], -0.2F, 1.2F);
        float ny = Mth.clamp(hit[1], -0.2F, 1.2F);
        if (down && sensitivity != 1F) {
            wx += (nx - wx) * sensitivity;
            wy += (ny - wy) * sensitivity;
        } else {
            wx = nx;
            wy = ny;
        }
        if (down) gesture.move(wx, wy);
        if (tool == Tool.PANEL) {
            carryX = Mth.clamp(wx - grabX, 0F, 1F);
            carryY = Mth.clamp(wy - grabY, 0F, 1F);
        }
    }

    // ------------------------------------------------------------------ press and release

    @Override
    public boolean press(@Nullable PointerRay.Local ray) {
        LeatherworkersBenchBlockEntity bench = bench();
        if (bench == null || !session.operator) return false;
        Target t = hovered;
        if (t != null && t.id().equals("output")) {
            StationFocus.send(WorkAction.BENCH_COLLECT);
            return false;
        }
        LeatherworkJob job = bench.job();
        if (job == null) {
            if (t == null || !t.enabled()) return false;
            switch (t.id()) {
                case "book" -> openBook();
                case "mat" -> {
                    ItemStack held = Minecraft.getInstance().player.getMainHandItem();
                    if (held.getItem() instanceof UnfinishedLeatherworkItem) StationFocus.send(WorkAction.BENCH_MOUNT_WORKPIECE);
                    else StationFocus.send(WorkAction.BENCH_REPAIR, Minecraft.getInstance().player.getInventory().selected);
                }
                default -> {
                }
            }
            return false;
        }
        switch (job.workStage()) {
            case RESERVED, CUTTING -> {
                if (!onPlane || !hasKnife()) return false;
                tool = Tool.KNIFE;
                gesture = new Gesture(WorkAction.BENCH_CUT, Gesture.Space.PLANE, 0.006F);
                gesture.begin(wx, wy);
                return true;
            }
            case ASSEMBLY -> {
                int p = panelAt(job, wx, wy);
                if (p < 0 && t != null && t.id().startsWith("panel")) p = Integer.parseInt(t.id().substring(5));
                if (p < 0) return false;
                float[] at = panelPlace(job, p);
                carried = p;
                carryX = at[0];
                carryY = at[1];
                carryRot = at[2];
                grabX = wx - at[0];
                grabY = wy - at[1];
                tool = Tool.PANEL;
                return true;
            }
            case STITCHING -> {
                if (t != null && t.id().equals("loosen")) {
                    StationFocus.send(WorkAction.BENCH_LOOSEN, lastStitch(job));
                    return false;
                }
                int next = expectedNext(job);
                boolean lagging = job.pending() >= 0 && job.pending() == releasedStitch && releaseRecent();
                if (next < 0 || !hasAwl() || (job.pending() >= 0 && !lagging)) return false;
                float[] f = job.stitchFrame(next);
                float d = Mth.sqrt((wx - f[4]) * (wx - f[4]) + (wy - f[5]) * (wy - f[5]));
                if (d > LeatherworkJob.HOLE_TOLERANCE * (ClientConfig.get(ClientConfig.ASSISTED_PROFILE) ? 1.5F : 1F) * 1.4F) return false;
                tool = Tool.AWL;
                stitching = next;
                gesture = new Gesture(WorkAction.BENCH_STITCH, Gesture.Space.PLANE, 0.004F).args(next);
                gesture.begin(wx, wy);
                return true;
            }
            case TIE -> {
                if (t == null) return false;
                if (t.id().equals("tie")) StationFocus.send(WorkAction.BENCH_TIE);
                else if (t.id().equals("loosen")) StationFocus.send(WorkAction.BENCH_LOOSEN, lastStitch(job));
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public void release(@Nullable PointerRay.Local ray) {
        if (tool == Tool.PANEL && carried >= 0) {
            StationFocus.send(WorkAction.BENCH_PLACE, 0, new int[]{carried, Math.round(carryRot * 10F) + 3600},
                    new short[]{StationCommandPacket.quantizePlane(carryX)}, new short[]{StationCommandPacket.quantizePlane(carryY)}, null, null);
            tool = Tool.NONE;
            return;
        }
        if (gesture != null && gesture.open()) {
            if (tool == Tool.AWL && stitching >= 0) {
                releasedStitch = stitching;
                releasedMs = System.currentTimeMillis();
            }
            gesture.end(wx, wy);
            tool = Tool.NONE;
            stitching = -1;
        }
    }

    private boolean releaseRecent() {
        return releasedStitch >= 0 && System.currentTimeMillis() - releasedMs < 1500L;
    }

    /**
     * The next stitch as this client expects it. A thread let go of is a stitch on the server (slack, even or puckered),
     * but the synced job shows it only after the next update: until then, or a refusal, the one after it is next.
     */
    private int expectedNext(LeatherworkJob job) {
        int next = job.nextStitch();
        if (releaseRecent() && next >= 0 && next <= releasedStitch) {
            int predicted = releasedStitch + 1;
            return predicted < job.stitchCount() ? predicted : -1;
        }
        return next;
    }

    @Override
    public void secondary() {
        if (bookOpen) {
            bookOpen = false;
            return;
        }
        tool = Tool.NONE;
        carried = -1;
    }

    @Override
    public void cancelGesture() {
        if (gesture != null && gesture.open()) {
            if (tool == Tool.AWL) gesture.cancel();
            else gesture.end(wx, wy);
        }
        if (tool != Tool.NONE) tool = Tool.NONE;
        carried = -1;
        stitching = -1;
    }

    @Override
    public boolean scrolled(double delta) {
        if (tool != Tool.PANEL) return false;
        carryRot = Mth.wrapDegrees(carryRot + (float) Math.signum(delta) * 15F);
        return true;
    }

    @Override
    public boolean key(int key, int scan, int modifiers) {
        if (tool == Tool.PANEL && (FocusKeys.is(FocusKeys.ROTATE_LEFT, key, scan) || FocusKeys.is(FocusKeys.ROTATE_RIGHT, key, scan))) {
            carryRot = Mth.wrapDegrees(carryRot + (FocusKeys.is(FocusKeys.ROTATE_LEFT, key, scan) ? -15F : 15F));
            return true;
        }
        return false;
    }

    /** Keyboard assistance: along the cut line; away from the seam while pulling; moving a carried panel. */
    @Override
    @Nullable
    public float[] follow(float dx, float dy, float seconds) {
        LeatherworkJob job = job();
        if (job == null) return null;
        float speed = 0.28F * seconds * (float) (double) ClientConfig.get(ClientConfig.POINTER_SENSITIVITY);
        if (tool == Tool.KNIFE) {
            int c = job.activeCut();
            if (c < 0) return null;
            float[] path = job.pattern().cutPath(c);
            float length = StitchPattern.length(path);
            float s = LeatherworkJob.project(path, wx, wy)[0] + (dx + dy) * speed;
            float[] p = StitchPattern.pointAt(path, Mth.clamp(s / Math.max(1.0E-4F, length), 0F, 1F));
            return PointerRay.projectLocal(pos, facing, BenchGeometry.point(p[0], p[1]));
        }
        if (tool == Tool.AWL && stitching >= 0) {
            float[] f = job.stitchFrame(stitching);
            float along = (wx - f[4]) * f[6] + (wy - f[5]) * f[7];
            float pull = along + (dy != 0 ? dy : dx) * speed * 0.6F;
            return PointerRay.projectLocal(pos, facing, BenchGeometry.point(f[4] + f[6] * pull, f[5] + f[7] * pull));
        }
        return null;
    }

    @Override
    @Nullable
    public float[] cycle(int direction) {
        LeatherworkJob job = job();
        if (tool == Tool.PANEL && job != null && carried >= 0) {
            WorkPattern.Placement target = job.pattern().placement(carried);
            carryRot = target.rot();
            return PointerRay.projectLocal(pos, facing, BenchGeometry.point(target.x() + grabX, target.y() + grabY));
        }
        return super.cycle(direction);
    }

    /** Opens the pattern book (asks the server for this player's book). */
    public void openBook() {
        bookOpen = true;
        StationFocus.send(WorkAction.BENCH_OFFERS);
    }

    public boolean bookOpen() {
        return bookOpen;
    }

    /** Whether the server's book has arrived and offers this pattern as makeable. */
    public boolean bookOffers(net.minecraft.resources.ResourceLocation recipe) {
        return book.loaded() && book.makeable(recipe);
    }

    /** Chooses a pattern in the open book and prepares it, as its row and "Prepare" would. */
    public void prepareFromBook(net.minecraft.resources.ResourceLocation recipe, @Nullable com.otectus.immersivetanning.leather.Finish finish) {
        book.select(recipe, finish);
        book.prepareSelected();
    }

    /** Chooses a pattern in the open book without preparing it (its details show). */
    public void selectInBook(net.minecraft.resources.ResourceLocation recipe) {
        book.select(recipe, null);
    }

    /** The label the open book shows after a result's name when another result shares it; null for a name of its own. */
    @Nullable
    public net.minecraft.network.chat.Component bookVariantLabel(net.minecraft.world.item.ItemStack result) {
        return book.variantLabel(result);
    }

    // ------------------------------------------------------------------ server events

    @Override
    public void offers(BenchOffersPacket packet) {
        book.offers(packet);
    }

    @Override
    public void ack(StationAckPacket packet) {
        if (!packet.accepted() && packet.code() == StationAckPacket.OUT_OF_PLACE) rejectMs = System.currentTimeMillis();
        // A refused command means the server's job is not what this client predicted; trust the next sync.
        if (!packet.accepted()) releasedStitch = -1;
    }

    @Override
    public void result(WorkResultPacket packet) {
        result = packet;
        resultMs = System.currentTimeMillis();
    }

    @Override
    public void tick() {
        if (job() != null) bookOpen = false;
        if (result != null && System.currentTimeMillis() - resultMs > ClientConfig.get(ClientConfig.FEEDBACK_LABEL_MS) + 3000L) result = null;
        LeatherworkJob job = job();
        if (tool == Tool.PANEL && (job == null || job.workStage() != LeatherworkJob.Stage.ASSEMBLY || (carried >= 0 && job.seated(carried)))) {
            tool = Tool.NONE;
            carried = -1;
        }
    }

    // ------------------------------------------------------------------ tool and watchers

    public boolean knifeOut() {
        LeatherworkJob job = job();
        return job != null && session.operator && (job.workStage() == LeatherworkJob.Stage.CUTTING || job.workStage() == LeatherworkJob.Stage.RESERVED)
                && hasKnife() && onPlane;
    }

    public boolean awlOut() {
        LeatherworkJob job = job();
        return job != null && session.operator && (job.workStage() == LeatherworkJob.Stage.STITCHING || job.workStage() == LeatherworkJob.Stage.TIE)
                && hasAwl() && onPlane;
    }

    public boolean toolDown() {
        return gesture != null && gesture.open();
    }

    public float wx() { return wx; }
    public float wy() { return wy; }
    public int stitching() { return stitching; }

    @Override
    @Nullable
    public float[] toolPose() {
        int kind = knifeOut() ? OperatorPosePacket.TOOL_KNIFE : awlOut() ? OperatorPosePacket.TOOL_AWL
                : tool == Tool.PANEL ? OperatorPosePacket.TOOL_HAND : OperatorPosePacket.TOOL_NONE;
        if (kind == OperatorPosePacket.TOOL_NONE) return null;
        Vec3 p = tool == Tool.PANEL ? BenchGeometry.point(carryX, carryY, 1.0F) : BenchGeometry.point(wx, wy, toolDown() ? 0.1F : 1.0F);
        return new float[]{kind, toolDown() ? 1 : 0, (float) p.x, (float) p.y, (float) p.z, tool == Tool.PANEL ? carried : -1, carryRot};
    }

    // ------------------------------------------------------------------ HUD

    @Override
    public void renderHud(GuiGraphics g, Font font, int width, int height, int mouseX, int mouseY, float partialTick) {
        buttons.clear();
        LeatherworkersBenchBlockEntity bench = bench();
        if (bench == null) return;
        LeatherworkJob job = bench.job();
        if (bookOpen && job == null) book.render(g, font, width, height, mouseX, mouseY);
        List<Component> guide = new ArrayList<>();
        if (!session.operator) guide.add(Component.translatable("hint.immersive_tanning.watching", bench.operatorName()));
        if (result != null) guide.add(RackBehavior.resultLine(result));
        if (job == null) {
            if (!bookOpen) guide.add(Component.translatable(bench.hasOutput() ? "hint.immersive_tanning.bench_collect" : "hint.immersive_tanning.bench_idle"));
        } else {
            WorkPattern pattern = job.pattern();
            switch (job.workStage()) {
                case RESERVED -> guide.add(Component.translatable("hint.immersive_tanning.bench_reserved", job.result().getHoverName()));
                case CUTTING -> guide.add(Component.translatable("hint.immersive_tanning.bench_cut", Math.max(1, job.activeCut() + 1), pattern.cuts().size()));
                case ASSEMBLY -> guide.add(Component.translatable(tool == Tool.PANEL ? "hint.immersive_tanning.bench_seat"
                        : "hint.immersive_tanning.bench_assemble", FocusKeys.ROTATE_LEFT.getTranslatedKeyMessage(), FocusKeys.ROTATE_RIGHT.getTranslatedKeyMessage()));
                case STITCHING -> {
                    Component instruction = Component.translatable("hint.immersive_tanning.bench_stitch", Math.max(1, job.nextStitch() + 1), job.stitchCount());
                    String seam = "work.immersive_tanning.seam." + pattern.joinOf(Math.max(0, job.nextStitch())).id();
                    guide.add(net.minecraft.locale.Language.getInstance().has(seam)
                            ? Component.translatable("hint.immersive_tanning.bench_named_seam", Component.translatable(seam), instruction) : instruction);
                }
                case TIE -> guide.add(Component.translatable("hint.immersive_tanning.bench_tie"));
                default -> {
                }
            }
            if (System.currentTimeMillis() - rejectMs < 1800L) guide.add(Component.translatable("hint.immersive_tanning.bench_not_seated"));
            if (tool == Tool.AWL && stitching >= 0 && ClientConfig.get(ClientConfig.TENSION_INDICATOR)) tensionGauge(g, font, job, width, height);
        }
        int guideTop = guide.isEmpty() ? height - 54 : Hud.guide(g, font, guide, width, height);
        if (hovered != null && !gestureActive() && !(bookOpen && book.covers(mouseX, mouseY))) {
            Hud.verb(g, font, hovered.verb(), mouseX, mouseY, width, guideTop - 4, hovered.enabled());
        }
        // The bench's own actions sit at the top right, never over the work plane or under a stroke.
        controls(g, font, bench, job, width, 6, mouseX, mouseY);
    }

    private void tensionGauge(GuiGraphics g, Font font, LeatherworkJob job, int width, int height) {
        float[] f = job.stitchFrame(stitching);
        WorkPattern.Join join = job.pattern().joinOf(stitching);
        float vx = wx - f[4];
        float vy = wy - f[5];
        float across = Math.abs(vx * f[6] + vy * f[7]);
        float along = Mth.sqrt(Math.max(0F, vx * vx + vy * vy - across * across));
        float drawn = Math.max(0F, across - 0.5F * along);
        String state = drawn < join.pullMin() ? "slack" : drawn > join.pullMax() ? "puckered" : "even";
        Hud.tension(g, font, width - 140, 40, drawn, join.pullMin(), join.pullIdeal(), join.pullMax(),
                Component.translatable("tension.immersive_tanning." + state));
    }

    private void controls(GuiGraphics g, Font font, LeatherworkersBenchBlockEntity bench, @Nullable LeatherworkJob job, int width, int y,
                          int mouseX, int mouseY) {
        if (!session.operator) return;
        List<String> ids = new ArrayList<>();
        List<Component> labels = new ArrayList<>();
        LocalPlayer player = Minecraft.getInstance().player;
        boolean owner = job != null && (job.owner() == null || job.owner().equals(player.getUUID()));
        if (job == null) {
            if (bench.lastRecipe() != null) {
                ids.add("again");
                labels.add(Component.translatable("button.immersive_tanning.make_another"));
            }
            if (bench.hasOutput()) {
                ids.add("collect");
                labels.add(Component.translatable("button.immersive_tanning.collect"));
            }
            if (!bookOpen) {
                ids.add("book");
                labels.add(Component.translatable("button.immersive_tanning.book"));
            }
        } else if (owner) {
            if (!job.started()) {
                ids.add("cancel");
                labels.add(Component.translatable("button.immersive_tanning.cancel"));
            } else {
                ids.add("workpiece");
                labels.add(Component.translatable("button.immersive_tanning.take_workpiece"));
            }
            if (job.kind() == LeatherworkJob.Kind.CRAFT && Minecraft.getInstance().level.getRecipeManager().byKey(job.recipeId()).isEmpty()) {
                ids.add("recover");
                labels.add(Component.translatable("button.immersive_tanning.recover"));
            }
            if (job.owner() != null) {
                ids.add("handoff");
                labels.add(Component.translatable("button.immersive_tanning.hand_off"));
            }
        }
        int total = 0;
        for (Component c : labels) total += font.width(c) + 14;
        int x = width - 6 - total + 4;
        for (int i = 0; i < ids.size(); i++) {
            Component c = labels.get(i);
            int w = font.width(c) + 10;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 14;
            buttons.add(Hud.button(g, font, ids.get(i), c, x, y, true, hover));
            x += w + 4;
        }
    }

    @Override
    public List<Hud.Button> hudButtons() {
        List<Hud.Button> out = new ArrayList<>(buttons);
        if (bookOpen) out.addAll(book.buttons());
        return out;
    }

    @Override
    public boolean hudClick(double mouseX, double mouseY, int button) {
        if (bookOpen && job() == null && book.click(mouseX, mouseY)) return true;
        for (Hud.Button b : buttons) {
            if (!b.contains(mouseX, mouseY) || !b.enabled()) continue;
            switch (b.id()) {
                case "again" -> StationFocus.send(WorkAction.BENCH_AGAIN);
                case "collect" -> StationFocus.send(WorkAction.BENCH_COLLECT);
                case "book" -> openBook();
                case "cancel" -> StationFocus.send(WorkAction.BENCH_CANCEL);
                case "workpiece" -> StationFocus.send(WorkAction.BENCH_TAKE_WORKPIECE);
                case "recover" -> StationFocus.send(WorkAction.BENCH_RECOVER);
                case "handoff" -> StationFocus.send(WorkAction.HANDOFF);
                default -> {
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean hudCovers(double mouseX, double mouseY) {
        if (bookOpen && job() == null && book.covers(mouseX, mouseY)) return true;
        for (Hud.Button b : buttons) if (b.contains(mouseX, mouseY)) return true;
        return false;
    }

    @Override
    public boolean hudScroll(double mouseX, double mouseY, double delta) {
        return bookOpen && job() == null && book.scroll(mouseX, mouseY, delta);
    }

    @Override
    public boolean hudKey(int key, int scan, int modifiers) {
        return bookOpen && job() == null && book.key(key, scan, modifiers);
    }

    @Override
    public boolean hudChar(char c, int modifiers) {
        return bookOpen && job() == null && book.character(c, modifiers);
    }
}
