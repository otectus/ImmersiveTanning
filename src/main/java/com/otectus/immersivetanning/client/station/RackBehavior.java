package com.otectus.immersivetanning.client.station;

import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.config.ClientConfig;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.hide.HideItems;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.minigame.FleshingSurface;
import com.otectus.immersivetanning.network.packet.OperatorPosePacket;
import com.otectus.immersivetanning.network.packet.WorkResultPacket;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.station.RackGeometry;
import com.otectus.immersivetanning.util.Scores;
import com.otectus.immersivetanning.work.FleshingWork;
import com.otectus.immersivetanning.work.WorkAction;
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
 * Working the Tanner's Rack. Over the beam: the knife is drawn through the flesh where the pointer meets the curved
 * hide (the same surface point is the blade's contact, the scored cell and the texel that clears), scraping is
 * predicted locally and settles on the server's hide after each stroke. In the frame: the tension lever is dragged
 * between its three labelled detents, the finish's short treatment is worked with the slicker or the cords, and the
 * lever's hub seats the choice. Picking things up is always a deliberate click on them, never the secondary button.
 */
public final class RackBehavior extends StationBehavior {
    private enum Tool { NONE, KNIFE, LEVER, SLICKER, CORDS }

    private Tool tool = Tool.NONE;
    @Nullable
    private Gesture gesture;
    @Nullable
    private FleshingSurface predicted;
    @Nullable
    private FleshingWork predictedFrom;
    private long predictedUntil;
    private float u = 0.5F;
    private float v = 0.5F;
    private boolean onHide;
    private float leverAngle = Float.NaN;
    /** Where the lever was taken (GUI); a release close by is a click, which seats a treated hide. */
    private double leverPressX;
    private double leverPressY;
    private double guiX;
    private double guiY;
    private float treatX = 0.5F;
    private float treatY = 0.5F;
    private boolean treatDown;
    private final List<Hud.Button> buttons = new ArrayList<>();
    @Nullable
    private WorkResultPacket result;
    private long resultMs;

    public RackBehavior(StationFocus.Session session) {
        super(session);
    }

    @Nullable
    private TannersRackBlockEntity rack() {
        return station() instanceof TannersRackBlockEntity rack ? rack : null;
    }

    @Override
    public StationShots.Task task() {
        TannersRackBlockEntity rack = rack();
        if (rack == null) return StationShots.Task.FLESHING;
        return switch (rack.mode()) {
            case FRAMING, DRYING -> StationShots.Task.FINISHING;
            default -> StationShots.Task.FLESHING;
        };
    }

    @Override
    public boolean gestureActive() {
        return (gesture != null && gesture.open()) || tool == Tool.LEVER;
    }

    // ------------------------------------------------------------------ targets

    @Override
    public List<Target> targets() {
        TannersRackBlockEntity rack = rack();
        List<Target> list = new ArrayList<>();
        if (rack == null) return list;
        LocalPlayer player = Minecraft.getInstance().player;
        boolean operator = session.operator;
        switch (rack.mode()) {
            case EMPTY -> {
                ItemStack held = player.getMainHandItem();
                boolean mountable = HideItems.isMountable(player.level(), held) || held.is(ModItems.WET_TANNED_HIDE.get());
                int carried = rawHides();
                list.add(new Target("beam", new AABB(2, 3.5, 0.2, 14, 7.2, 3.6),
                        mountable ? Component.translatable("verb.immersive_tanning.mount_hide")
                                : carried > 0 ? Component.translatable("verb.immersive_tanning.next_hide", carried)
                                : Component.translatable("verb.immersive_tanning.need_raw_hide"), operator && (mountable || carried > 0)));
            }
            case RAW -> list.add(new Target("hide", RackGeometry.fleshBounds(), hasKnife()
                    ? Component.translatable("verb.immersive_tanning.scrape") : Component.translatable("verb.immersive_tanning.need_knife"),
                    operator && hasKnife()));
            case SCRAPED -> list.add(new Target("take", RackGeometry.fleshBounds(), Component.translatable("verb.immersive_tanning.take_scraped"), operator));
            case FRAMING -> {
                Finish finish = rack.finish();
                list.add(new Target("lever", RackGeometry.leverBox(angle(rack)), Component.translatable(rack.treated()
                        ? "verb.immersive_tanning.lever_seat" : "verb.immersive_tanning.lever", finish.displayName()), operator));
                if (finish == Finish.HARNESS) {
                    list.add(new Target("cords", RackGeometry.cordBox(), Component.translatable("verb.immersive_tanning.cords"), operator));
                } else {
                    list.add(new Target("slicker", RackGeometry.slickerBox(), Component.translatable(finish == Finish.SUPPLE
                            ? "verb.immersive_tanning.slicker_supple" : "verb.immersive_tanning.slicker_hardened"), operator));
                }
                if (tool == Tool.SLICKER || tool == Tool.CORDS) {
                    list.add(new Target("frame", RackGeometry.frameBox().expandTowards(0, -3.5, 0), Component.translatable(tool == Tool.CORDS
                            ? "verb.immersive_tanning.draw_cords" : finish == Finish.SUPPLE ? "verb.immersive_tanning.smooth"
                            : "verb.immersive_tanning.burnish"), operator));
                }
                list.add(new Target("hub", RackGeometry.hubBox(), rack.treated() ? Component.translatable("verb.immersive_tanning.seat", finish.displayName())
                        : Component.translatable("verb.immersive_tanning.seat_untreated"), operator && rack.treated()));
                list.add(new Target("unhook", new AABB(3, 13.4, 6.4, 13, 16, 9.6), Component.translatable("verb.immersive_tanning.unhook"), operator));
            }
            case DRYING -> {
                if (rack.isDry()) {
                    list.add(new Target("leather", RackGeometry.frameBox(), Component.translatable("verb.immersive_tanning.take_leather"), operator));
                } else {
                    long time = Minecraft.getInstance().level.getGameTime();
                    int percent = Math.round(rack.dryProgress(time) * 100F);
                    list.add(new Target("inspect", RackGeometry.frameBox(), Component.translatable("verb.immersive_tanning.drying",
                            rack.finish().displayName(), percent), false));
                    list.add(new Target("unhook", new AABB(3, 13.4, 6.4, 13, 16, 9.6), Component.translatable("verb.immersive_tanning.unhook_drying"), operator));
                }
            }
        }
        return list;
    }

    private float angle(TannersRackBlockEntity rack) {
        return tool == Tool.LEVER && !Float.isNaN(leverAngle) ? leverAngle : RackGeometry.detentDegrees(rack.finish());
    }

    /** The lever's angle as drawn (dragged, or resting in the rack's detent). */
    public float leverAngle(TannersRackBlockEntity rack) {
        return angle(rack);
    }

    private boolean hasKnife() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModItems.TANNERS_KNIFE.get())) return true;
        }
        return false;
    }

    private int rawHides() {
        LocalPlayer player = Minecraft.getInstance().player;
        int n = 0;
        for (ItemStack s : player.getInventory().items) if (HideItems.isMountable(player.level(), s)) n += s.getCount();
        return n;
    }

    @Override
    public boolean drawerTakes(ItemStack stack) {
        LocalPlayer player = Minecraft.getInstance().player;
        return stack.is(ModItems.TANNERS_KNIFE.get()) || stack.is(ModItems.WET_TANNED_HIDE.get()) || HideItems.isMountable(player.level(), stack);
    }

    // ------------------------------------------------------------------ pointer

    @Override
    public void pointer(@Nullable PointerRay.Local ray, double guiX, double guiY) {
        super.pointer(ray, guiX, guiY);
        this.guiX = guiX;
        this.guiY = guiY;
        TannersRackBlockEntity rack = rack();
        if (ray == null || rack == null) return;
        if (rack.mode() == TannersRackBlockEntity.Mode.RAW || rack.mode() == TannersRackBlockEntity.Mode.SCRAPED) {
            boolean down = gesture != null && gesture.open();
            float[] hit = RackGeometry.intersectFlesh(ray.origin(), ray.dir(), down);
            onHide = hit != null;
            if (hit != null) {
                float sensitivity = (float) (double) ClientConfig.get(ClientConfig.POINTER_SENSITIVITY);
                if (down && sensitivity != 1F) {
                    u = Mth.clamp(u + (hit[0] - u) * sensitivity, 0F, 1F);
                    v = Mth.clamp(v + (hit[1] - v) * sensitivity, 0F, 1F);
                } else {
                    u = hit[0];
                    v = hit[1];
                }
                if (down) drag();
            }
        } else if (rack.mode() == TannersRackBlockEntity.Mode.FRAMING) {
            if (tool == Tool.LEVER) {
                leverAngle = RackGeometry.leverAngle(ray.origin(), ray.dir(), leverAngle);
            } else if (tool == Tool.SLICKER || tool == Tool.CORDS) {
                float[] hit = RackGeometry.intersectFrame(ray.origin(), ray.dir());
                if (hit != null) {
                    treatX = hit[0];
                    treatY = hit[1];
                    if (gesture != null && gesture.open()) gesture.move(treatX, treatY);
                }
            }
        }
    }

    private void drag() {
        if (gesture == null || !gesture.open()) return;
        gesture.move(u, v);
    }

    /** The prediction takes exactly the points sent, in the same quantisation, so it replays like the server. */
    private void predict(float x, float y, boolean first) {
        if (predicted == null) return;
        int qx = FleshingSurface.quantize(x);
        int qy = FleshingSurface.quantize(y);
        if (first) {
            predicted.begin(qx, qy);
        } else {
            predicted.drag(qx, qy);
            WorkSounds.localScrape(pos, predicted.lastRemovedCells() > 0);
            if (predicted.lastRemovedCells() > 0 && Minecraft.getInstance().level != null) {
                com.otectus.immersivetanning.client.ClientEffects.scrape(pos, x, y, predicted.lastRemovedCells() / 400F);
            }
        }
    }

    // ------------------------------------------------------------------ press and release

    @Override
    public boolean press(@Nullable PointerRay.Local ray) {
        TannersRackBlockEntity rack = rack();
        if (rack == null || !session.operator) return false;
        Target t = hovered;
        switch (rack.mode()) {
            case EMPTY -> {
                if (t == null || !t.enabled()) return false;
                ItemStack held = Minecraft.getInstance().player.getMainHandItem();
                boolean mountable = HideItems.isMountable(Minecraft.getInstance().player.level(), held) || held.is(ModItems.WET_TANNED_HIDE.get());
                StationFocus.send(mountable ? WorkAction.RACK_MOUNT : WorkAction.NEXT_HIDE);
                return false;
            }
            case RAW -> {
                if (!hasKnife() || ray == null) return false;
                float[] hit = RackGeometry.intersectFlesh(ray.origin(), ray.dir(), false);
                if (hit == null) return false;
                u = hit[0];
                v = hit[1];
                FleshingWork work = rack.work();
                if (work == null) return false;
                predictedFrom = work;
                predicted = (predicted != null && System.currentTimeMillis() < predictedUntil) ? predicted : work.surface().copy();
                gesture = new Gesture(WorkAction.FLESH, Gesture.Space.UNIT, 0.004F).sink(this::predict);
                gesture.begin(u, v);
                tool = Tool.KNIFE;
                return true;
            }
            case SCRAPED -> {
                if (t != null && t.enabled()) StationFocus.send(WorkAction.RACK_TAKE);
                return false;
            }
            case FRAMING -> {
                if (t == null || !t.enabled()) return false;
                switch (t.id()) {
                    case "lever" -> {
                        tool = Tool.LEVER;
                        leverAngle = RackGeometry.detentDegrees(rack.finish());
                        leverPressX = guiX;
                        leverPressY = guiY;
                        return true;
                    }
                    case "slicker" -> {
                        tool = Tool.SLICKER;
                        return false;
                    }
                    case "cords" -> {
                        tool = Tool.CORDS;
                        return false;
                    }
                    case "frame" -> {
                        if (tool != Tool.SLICKER && tool != Tool.CORDS) return false;
                        gesture = new Gesture(WorkAction.TREAT, Gesture.Space.PLANE, 0.01F);
                        gesture.begin(treatX, treatY);
                        treatDown = true;
                        return true;
                    }
                    case "hub" -> {
                        StationFocus.send(WorkAction.SEAT);
                        tool = Tool.NONE;
                        return false;
                    }
                    case "unhook" -> {
                        StationFocus.send(WorkAction.RACK_TAKE);
                        tool = Tool.NONE;
                        return false;
                    }
                    default -> {
                        return false;
                    }
                }
            }
            case DRYING -> {
                if (t != null && t.enabled() && (t.id().equals("leather") || t.id().equals("unhook"))) StationFocus.send(WorkAction.RACK_TAKE);
                return false;
            }
        }
        return false;
    }

    @Override
    public void release(@Nullable PointerRay.Local ray) {
        TannersRackBlockEntity rack = rack();
        if (tool == Tool.LEVER) {
            // The hub sits behind the handle at some detents: a click on the handle (no drag) presses the lever home,
            // seating a treated hide; only a drag moves it to another detent.
            boolean click = Math.hypot(guiX - leverPressX, guiY - leverPressY) < 4.0;
            if (rack != null && click) {
                if (rack.treated()) StationFocus.send(WorkAction.SEAT);
            } else if (rack != null && !Float.isNaN(leverAngle)) {
                Finish detent = RackGeometry.nearestDetent(leverAngle);
                StationFocus.send(WorkAction.DETENT, detent.ordinal());
            }
            tool = Tool.NONE;
            leverAngle = Float.NaN;
            return;
        }
        if (gesture != null && gesture.open()) {
            if (gesture == null) return;
            if (tool == Tool.KNIFE) {
                gesture.end(u, v);
                if (predicted != null) predicted.end();
                predictedUntil = System.currentTimeMillis() + 1500L;
                WorkSounds.fade(pos);
                tool = Tool.NONE;
            } else {
                gesture.end(treatX, treatY);
                treatDown = false;
            }
        }
    }

    @Override
    public void secondary() {
        // Put the slicker or cords back, or let go of the lever without moving it.
        tool = Tool.NONE;
        leverAngle = Float.NaN;
    }

    @Override
    public void cancelGesture() {
        if (gesture != null && gesture.open()) {
            if (tool == Tool.KNIFE) {
                gesture.end(u, v);
                if (predicted != null) predicted.end();
            } else {
                gesture.cancel();
            }
        }
        treatDown = false;
        if (tool == Tool.LEVER || tool == Tool.KNIFE) tool = Tool.NONE;
        leverAngle = Float.NaN;
        WorkSounds.fade(pos);
    }

    @Override
    public boolean key(int key, int scan, int modifiers) {
        TannersRackBlockEntity rack = rack();
        if (rack == null) return false;
        if (FocusKeys.is(FocusKeys.FINISH, key, scan) && rack.mode() == TannersRackBlockEntity.Mode.RAW) {
            StationFocus.send(WorkAction.FLESH_ROUGH);
            return true;
        }
        if (rack.mode() == TannersRackBlockEntity.Mode.FRAMING && (FocusKeys.is(FocusKeys.ROTATE_LEFT, key, scan) || FocusKeys.is(FocusKeys.ROTATE_RIGHT, key, scan))) {
            int step = FocusKeys.is(FocusKeys.ROTATE_LEFT, key, scan) ? -1 : 1;
            StationFocus.send(WorkAction.DETENT, Mth.clamp(rack.finish().ordinal() + step, 0, 2));
            return true;
        }
        return false;
    }

    /** Keyboard fleshing: with the blade down the arrows move along (right/left) and across (up/down) the grain. */
    @Override
    @Nullable
    public float[] follow(float dx, float dy, float seconds) {
        TannersRackBlockEntity rack = rack();
        if (rack == null || tool != Tool.KNIFE || rack.work() == null) return null;
        FleshingSurface surface = rack.work().surface();
        int patch = nearestPatch(surface);
        float g = patch < 0 ? 0F : surface.grain(patch);
        float speed = 0.32F * seconds * (float) (double) ClientConfig.get(ClientConfig.POINTER_SENSITIVITY);
        float gx = Mth.cos(g) / FleshingSurface.W;
        float gy = Mth.sin(g) / FleshingSurface.H;
        float len = Mth.sqrt(gx * gx + gy * gy);
        gx /= len;
        gy /= len;
        float nu = Mth.clamp(u + (dx * gx - dy * gy) * speed, 0F, 1F);
        float nv = Mth.clamp(v + (dx * gy + dy * gx) * speed, 0F, 1F);
        float[] p = RackGeometry.fleshPoint(nu, nv);
        return PointerRay.projectLocal(pos, facing, new Vec3(p[0], p[1], p[2]));
    }

    private int nearestPatch(FleshingSurface surface) {
        int best = -1;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < surface.patches(); i++) {
            if (surface.patchLeft(i) <= 0F) continue;
            float dx = surface.patchX(i) / FleshingSurface.W - u;
            float dy = surface.patchY(i) / FleshingSurface.H - v;
            float d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** Keyboard cycling over the hide jumps between the patches still carrying flesh. */
    @Override
    @Nullable
    public float[] cycle(int direction) {
        TannersRackBlockEntity rack = rack();
        if (rack != null && rack.mode() == TannersRackBlockEntity.Mode.RAW && rack.work() != null) {
            FleshingSurface surface = rack.work().surface();
            List<Integer> left = new ArrayList<>();
            for (int i = 0; i < surface.patches(); i++) if (surface.patchLeft(i) > 0F) left.add(i);
            if (!left.isEmpty()) {
                keyboardTarget = Math.floorMod(keyboardTarget + direction, left.size());
                int patch = left.get(keyboardTarget);
                float pu = surface.patchX(patch) / FleshingSurface.W;
                float pv = surface.patchY(patch) / FleshingSurface.H;
                float[] p = RackGeometry.fleshPoint(pu, pv);
                return PointerRay.projectLocal(pos, facing, new Vec3(p[0], p[1], p[2]));
            }
        }
        return super.cycle(direction);
    }

    @Override
    public void tick() {
        TannersRackBlockEntity rack = rack();
        if (rack == null) return;
        // The prediction settles on the server's hide once the stroke's authoritative copy has arrived.
        if (predicted != null && (gesture == null || !gesture.open())) {
            boolean synced = rack.work() != predictedFrom || rack.mode() != TannersRackBlockEntity.Mode.RAW;
            if (synced || System.currentTimeMillis() > predictedUntil) {
                predicted = null;
                predictedFrom = null;
            }
        }
        if (rack.mode() != TannersRackBlockEntity.Mode.FRAMING && (tool == Tool.SLICKER || tool == Tool.CORDS || tool == Tool.LEVER)) tool = Tool.NONE;
        if (result != null && System.currentTimeMillis() - resultMs > ClientConfig.get(ClientConfig.FEEDBACK_LABEL_MS) + 2500L) result = null;
    }

    /** The hide the renderer should draw: the local prediction while this client is scraping, else the station's. */
    @Nullable
    public FleshingSurface surface(TannersRackBlockEntity rack) {
        if (predicted != null && rack.mode() == TannersRackBlockEntity.Mode.RAW) return predicted;
        if (rack.mode() == TannersRackBlockEntity.Mode.SCRAPED) return rack.scrapedLook();
        return rack.work() == null ? null : rack.work().surface();
    }

    // ------------------------------------------------------------------ tool for the renderer and watchers

    public boolean knifeOut() {
        TannersRackBlockEntity rack = rack();
        return rack != null && session.operator && rack.mode() == TannersRackBlockEntity.Mode.RAW && hasKnife() && (onHide || tool == Tool.KNIFE);
    }

    public boolean bladeDown() {
        return tool == Tool.KNIFE && gesture != null && gesture.open();
    }

    public float u() { return u; }
    public float v() { return v; }
    public boolean treating() { return treatDown; }
    public float treatX() { return treatX; }
    public float treatY() { return treatY; }

    /** Which frame tool is in hand: 0 none, 1 slicker, 2 cords. */
    public int frameTool() {
        return tool == Tool.SLICKER ? 1 : tool == Tool.CORDS ? 2 : 0;
    }

    @Override
    @Nullable
    public float[] toolPose() {
        TannersRackBlockEntity rack = rack();
        if (rack == null) return null;
        if (knifeOut()) {
            float[] p = RackGeometry.fleshPoint(u, v);
            float[] n = RackGeometry.fleshNormal(v);
            float lift = bladeDown() ? 0.12F : 1.1F;
            return new float[]{OperatorPosePacket.TOOL_KNIFE, bladeDown() ? 1 : 0, p[0] + n[0] * lift, p[1] + n[1] * lift, p[2] + n[2] * lift};
        }
        if (frameTool() > 0) {
            float[] p = RackGeometry.framePoint(treatX, Math.min(treatY, 1.4F));
            return new float[]{frameTool() == 1 ? OperatorPosePacket.TOOL_SLICKER : OperatorPosePacket.TOOL_HAND, treatDown ? 1 : 0,
                    p[0], p[1], p[2] - (treatDown ? 0.3F : 1.2F)};
        }
        return null;
    }

    // ------------------------------------------------------------------ HUD

    @Override
    public void result(WorkResultPacket packet) {
        result = packet;
        resultMs = System.currentTimeMillis();
    }

    @Override
    public void renderHud(GuiGraphics g, Font font, int width, int height, int mouseX, int mouseY, float partialTick) {
        buttons.clear();
        TannersRackBlockEntity rack = rack();
        if (rack == null) return;
        List<Component> guide = new ArrayList<>();
        FleshingSurface surface = surface(rack);
        switch (rack.mode()) {
            case EMPTY -> guide.add(Component.translatable(rawHides() > 0 ? "hint.immersive_tanning.rack_next" : "hint.immersive_tanning.rack_empty"));
            case RAW -> {
                if (surface != null) {
                    int left = Math.round((1F - surface.coverage()) * 100F);
                    guide.add(Component.translatable(hasKnife() ? "hint.immersive_tanning.flesh" : "hint.immersive_tanning.flesh_knife"));
                    if (ClientConfig.get(ClientConfig.SHOW_NUMERIC_SCORES)) {
                        guide.add(Component.translatable("hint.immersive_tanning.flesh_progress", 100 - left, Scores.label(surface.score())));
                    }
                    if (ServerConfig.get(ServerConfig.CHALLENGE_TIMING) && rack.work() != null) {
                        guide.add(Component.translatable("hint.immersive_tanning.challenge"));
                    }
                    boolean rough = surface.canFinishRoughly() && !surface.isComplete();
                    // The hide's own actions sit at the top right, never over the hide where a stroke may start.
                    int bx = width - 6;
                    if (rough && session.operator) {
                        Component label = Component.translatable("button.immersive_tanning.finish_roughly", FocusKeys.FINISH.getTranslatedKeyMessage(),
                                Scores.label(surface.score()));
                        bx -= font.width(label) + 10;
                        buttons.add(Hud.button(g, font, "rough", label, bx, 6, true, mouseX >= bx && mouseX < bx + font.width(label) + 10
                                && mouseY >= 6 && mouseY < 20));
                        bx -= 4;
                    }
                    FleshingWork work = rack.work();
                    if (session.operator && work != null && work.started() && work.ownedBy(Minecraft.getInstance().player.getUUID())) {
                        Component label = Component.translatable("button.immersive_tanning.hand_off");
                        bx -= font.width(label) + 10;
                        buttons.add(Hud.button(g, font, "handoff", label, bx, 6, true, mouseX >= bx && mouseX < bx + font.width(label) + 10
                                && mouseY >= 6 && mouseY < 20));
                    }
                }
            }
            case SCRAPED -> guide.add(Component.translatable("hint.immersive_tanning.scraped"));
            case FRAMING -> {
                Finish finish = rack.finish();
                guide.add(Component.translatable("hint.immersive_tanning.frame_choose", finish.displayName(), finish.description()));
                guide.add(Component.translatable(rack.treated() ? "hint.immersive_tanning.frame_seat"
                        : finish == Finish.HARNESS ? "hint.immersive_tanning.frame_cords" : finish == Finish.SUPPLE
                        ? "hint.immersive_tanning.frame_smooth" : "hint.immersive_tanning.frame_burnish"));
            }
            case DRYING -> {
                long time = Minecraft.getInstance().level.getGameTime();
                guide.add(Component.translatable(rack.isDry() ? "hint.immersive_tanning.dry" : "hint.immersive_tanning.drying",
                        rack.finish().displayName(), Math.round(rack.dryProgress(time) * 100F)));
            }
        }
        if (!session.operator) guide.add(0, Component.translatable("hint.immersive_tanning.watching", rack.operatorName()));
        if (result != null) guide.add(0, resultLine(result));
        int guideTop = !guide.isEmpty() && (hints() || result != null) ? Hud.guide(g, font, guide, width, height) : height - 54;
        if (tool == Tool.LEVER && !Float.isNaN(leverAngle)) {
            Finish detent = RackGeometry.nearestDetent(leverAngle);
            Hud.verb(g, font, Component.translatable("verb.immersive_tanning.detent", detent.displayName(), detent.tagline()),
                    mouseX, mouseY, width, guideTop - 4, true);
        } else if (hovered != null && !gestureActive()) {
            Hud.verb(g, font, hovered.verb(), mouseX, mouseY, width, guideTop - 4, hovered.enabled());
        }
    }

    static Component resultLine(WorkResultPacket p) {
        List<Component> why = new ArrayList<>();
        for (String key : p.explanation()) why.add(Component.translatable("result.immersive_tanning." + key));
        Component reasons = why.isEmpty() ? Component.empty() : Component.translatable("result.immersive_tanning.because",
                net.minecraft.network.chat.ComponentUtils.formatList(why, Component.literal("; ")));
        return switch (p.kind()) {
            case WorkResultPacket.FLESHING -> Component.translatable("result.immersive_tanning.fleshed", Scores.label(p.score()), reasons);
            case WorkResultPacket.FINISHING -> Component.translatable("result.immersive_tanning.finished", Finish.byOrdinal(p.finish()).displayName());
            case WorkResultPacket.REPAIR -> Component.translatable("result.immersive_tanning.repaired", reasons);
            default -> Component.translatable("result.immersive_tanning.made", Scores.label(p.score()), reasons);
        };
    }

    @Override
    public List<Hud.Button> hudButtons() {
        return List.copyOf(buttons);
    }

    @Override
    public boolean hudClick(double mouseX, double mouseY, int button) {
        for (Hud.Button b : buttons) {
            if (!b.contains(mouseX, mouseY) || !b.enabled()) continue;
            if (b.id().equals("rough")) StationFocus.send(WorkAction.FLESH_ROUGH);
            else if (b.id().equals("handoff")) StationFocus.send(WorkAction.HANDOFF);
            return true;
        }
        return false;
    }

    @Override
    public boolean hudCovers(double mouseX, double mouseY) {
        for (Hud.Button b : buttons) if (b.contains(mouseX, mouseY)) return true;
        return false;
    }
}
