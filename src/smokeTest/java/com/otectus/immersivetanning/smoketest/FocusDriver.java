package com.otectus.immersivetanning.smoketest;

import com.otectus.immersivetanning.client.station.FocusScreen;
import com.otectus.immersivetanning.client.station.PointerRay;
import com.otectus.immersivetanning.client.station.StationFocus;
import com.otectus.immersivetanning.station.BenchGeometry;
import com.otectus.immersivetanning.station.RackGeometry;
import com.otectus.immersivetanning.station.StationFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Drives station focus the way a player's hand does: real mouse and key events delivered to the focus screen, at the
 * screen points where the station's parts are drawn this frame (projected through the same matrices the pointer ray
 * uses). Each queued step runs on its own client tick, so presses, drags and releases arrive spread over time like a
 * person's - the server's rate and path budgets apply as for anyone.
 */
final class FocusDriver {
    /** A queued step; one runs per tick. Steps of a stroke whose guard failed are dropped without using a tick. */
    private record Step(Runnable run, boolean[] live) {}

    private static final Deque<Step> QUEUE = new ArrayDeque<>();
    private static final boolean[] ALWAYS = {true};
    private static double lastX;
    private static double lastY;

    static boolean idle() {
        return QUEUE.isEmpty();
    }

    static void clear() {
        QUEUE.clear();
    }

    /** Runs the next queued step; call once per client tick. */
    static void tick() {
        while (!QUEUE.isEmpty()) {
            Step next = QUEUE.poll();
            if (!next.live()[0]) continue;
            next.run().run();
            return;
        }
    }

    private static void add(Runnable step) {
        QUEUE.add(new Step(step, ALWAYS));
    }

    private static void add(Runnable step, boolean[] live) {
        QUEUE.add(new Step(step, live));
    }

    @Nullable
    static FocusScreen screen() {
        return Minecraft.getInstance().screen instanceof FocusScreen screen ? screen : null;
    }

    static boolean focusedAt(BlockPos pos) {
        StationFocus.Session s = StationFocus.session();
        return s != null && s.pos.equals(pos) && screen() != null && PointerRay.valid();
    }

    static Direction facing(BlockPos pos) {
        return StationFrame.facing(Minecraft.getInstance().level.getBlockState(pos));
    }

    /** A station-local pixel point on the GUI now (null when off screen). */
    @Nullable
    static float[] gui(BlockPos pos, Vec3 localPx) {
        return PointerRay.projectLocal(pos, facing(pos), localPx);
    }

    static Vec3 hide(float u, float v) {
        float[] p = RackGeometry.fleshPoint(u, v);
        return new Vec3(p[0], p[1], p[2]);
    }

    static Vec3 frame(float fu, float fv) {
        float[] p = RackGeometry.framePoint(fu, fv);
        return new Vec3(p[0], p[1], p[2]);
    }

    static Vec3 plane(float wx, float wy) {
        return BenchGeometry.point(wx, wy);
    }

    // ------------------------------------------------------------------ steps

    static void wait(int ticks) {
        for (int i = 0; i < ticks; i++) add(() -> {});
    }

    static void run(Runnable step) {
        add(step);
    }

    /** Uses the same 15-degree wheel detents as a player while a panel is held. */
    static void rotatePanel(float degrees) {
        FocusScreen screen = screen();
        if (screen == null) return;
        int detents = Math.round(degrees / 15F);
        for (int i = 0; i < Math.abs(detents); i++) screen.mouseScrolled(lastX, lastY, Math.signum(detents));
    }

    /** Moves to a part and clicks it (press on one tick, release on the next, then a pause past the duplicate guard). */
    static void click(BlockPos pos, Vec3 localPx) {
        add(() -> {
            FocusScreen s = screen();
            float[] at = gui(pos, localPx);
            if (s == null || at == null) return;
            lastX = at[0];
            lastY = at[1];
            s.mouseMoved(at[0], at[1]);
            s.mouseClicked(at[0], at[1], 0);
        });
        add(() -> {
            FocusScreen s = screen();
            if (s != null) s.mouseReleased(lastX, lastY, 0);
        });
        wait(2);
    }

    /**
     * Presses at the first point and drags through the rest over {@code ticks} ticks, then releases: a stroke, a cut, a
     * carried panel or a drawn thread. Points are station-local pixels, re-projected each tick.
     */
    static void drag(BlockPos pos, List<Vec3> path, int ticks) {
        drag(pos, path, ticks, null, null);
    }

    static void drag(BlockPos pos, List<Vec3> path, int ticks, @Nullable Runnable midway) {
        drag(pos, path, ticks, midway, null);
    }

    /**
     * As {@link #drag(BlockPos, List, int)}, running {@code midway} (a screenshot) halfway, while still pressed. A
     * {@code guard} that is false when the press is due skips the whole stroke (the work it was for is done).
     */
    static void drag(BlockPos pos, List<Vec3> path, int ticks, @Nullable Runnable midway, @Nullable java.util.function.BooleanSupplier guard) {
        boolean[] live = {true};
        add(() -> {
            FocusScreen s = screen();
            float[] at = gui(pos, path.get(0));
            if ((guard != null && !guard.getAsBoolean()) || s == null || at == null) {
                live[0] = false; // the rest of this stroke is dropped without using ticks
                return;
            }
            lastX = at[0];
            lastY = at[1];
            s.mouseMoved(at[0], at[1]);
            s.mouseClicked(at[0], at[1], 0);
        });
        int per = Math.max(1, (int) Math.ceil((path.size() - 1) / (double) Math.max(1, ticks)));
        boolean ran = midway == null;
        for (int start = 1; start < path.size(); start += per) {
            final int from = start;
            final int to = Math.min(path.size(), start + per);
            add(() -> {
                FocusScreen s = screen();
                if (s == null) return;
                for (int i = from; i < to; i++) {
                    float[] at = gui(pos, path.get(i));
                    if (at == null) continue;
                    s.mouseDragged(at[0], at[1], 0, at[0] - lastX, at[1] - lastY);
                    lastX = at[0];
                    lastY = at[1];
                }
            }, live);
            if (!ran && to >= path.size() / 2) {
                add(() -> {}, live); // a frame drawn with the tool down before the shot
                add(midway, live);
                ran = true;
            }
        }
        add(() -> {
            FocusScreen s = screen();
            if (s != null) s.mouseReleased(lastX, lastY, 0);
        }, live);
        add(() -> {}, live);
        add(() -> {}, live);
    }

    /** Moves the pointer onto a part without pressing (for a hover label). */
    static void move(BlockPos pos, Vec3 localPx) {
        add(() -> {
            FocusScreen s = screen();
            float[] at = gui(pos, localPx);
            if (s == null || at == null) return;
            lastX = at[0];
            lastY = at[1];
            s.mouseMoved(at[0], at[1]);
        });
    }

    /** Moves the pointer through points over the given ticks without pressing (a key-held gesture follows it). */
    static void glide(BlockPos pos, List<Vec3> path, int ticks) {
        int per = Math.max(1, (int) Math.ceil(path.size() / (double) Math.max(1, ticks)));
        for (int start = 0; start < path.size(); start += per) {
            final int from = start;
            final int to = Math.min(path.size(), start + per);
            add(() -> {
                FocusScreen s = screen();
                if (s == null) return;
                for (int i = from; i < to; i++) {
                    float[] at = gui(pos, path.get(i));
                    if (at == null) continue;
                    lastX = at[0];
                    lastY = at[1];
                    s.mouseMoved(at[0], at[1]);
                }
            });
        }
    }

    /** A key going down (and, with {@code repeats}, auto-repeating as a held key does), without the release. */
    static void keyDown(int glfwKey, int repeats) {
        add(() -> {
            FocusScreen s = screen();
            if (s != null) for (int i = 0; i <= repeats; i++) s.keyPressed(glfwKey, 0, 0);
        });
    }

    static void keyUp(int glfwKey) {
        add(() -> {
            FocusScreen s = screen();
            if (s != null) s.keyReleased(glfwKey, 0, 0);
        });
    }

    static void key(int glfwKey) {
        add(() -> {
            FocusScreen s = screen();
            if (s != null) {
                s.keyPressed(glfwKey, 0, 0);
                s.keyReleased(glfwKey, 0, 0);
            }
        });
        wait(1);
    }

    static List<Vec3> line(Vec3 a, Vec3 b, int points) {
        List<Vec3> out = new ArrayList<>();
        for (int i = 0; i <= points; i++) out.add(a.lerp(b, i / (double) points));
        return out;
    }

    private FocusDriver() {}
}
