package com.otectus.immersivetanning.client.station;

import com.otectus.immersivetanning.network.packet.StationCommandPacket;
import com.otectus.immersivetanning.work.WorkAction;
import net.minecraft.Util;

import java.util.Arrays;

/**
 * One press-drag-release of a tool, sampled for the server: points are taken by distance travelled (not per frame or
 * per mouse event), sharp turns are kept, and batches go out about ten times a second with the press and the release
 * marked. Two clients drawing the same path at 30 or 240 frames per second send equivalent paths.
 */
public final class Gesture {
    public enum Space { UNIT, PLANE }

    private static final long FLUSH_MS = 100L;
    private static final int MAX_BATCH = 40;

    private final WorkAction action;
    private final Space space;
    private final float step;
    private int[] ints = new int[0];
    private final short[] xs = new short[StationCommandPacket.MAX_POINTS];
    private final short[] ys = new short[StationCommandPacket.MAX_POINTS];
    private int count;
    private boolean open;
    private boolean beginPending;
    private long lastFlushMs;
    private float lastX = Float.NaN;
    private float lastY;
    private float dirX;
    private float dirY;

    public Gesture(WorkAction action, Space space, float step) {
        this.action = action;
        this.space = space;
        this.step = step;
    }

    public boolean open() {
        return open;
    }

    /** Called with every point as it is taken for sending (the client's prediction applies exactly these). */
    public interface Sink {
        void point(float x, float y, boolean first);
    }

    @org.jetbrains.annotations.Nullable
    private Sink sink;

    public Gesture sink(Sink sink) {
        this.sink = sink;
        return this;
    }

    public Gesture args(int... values) {
        this.ints = values;
        return this;
    }

    public void begin(float x, float y) {
        open = true;
        beginPending = true;
        count = 0;
        lastX = Float.NaN;
        add(x, y);
        lastFlushMs = Util.getMillis();
        flush(false, false);
    }

    public void move(float x, float y) {
        if (!open) return;
        float dx = x - lastX;
        float dy = y - lastY;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d >= step) {
            // A sharp turn keeps its corner: the previous point already stands where the direction changed.
            float nx = dx / d;
            float ny = dy / d;
            dirX = nx;
            dirY = ny;
            add(x, y);
        }
        if (count >= MAX_BATCH || (count > 0 && Util.getMillis() - lastFlushMs >= FLUSH_MS)) flush(false, false);
    }

    public void end(float x, float y) {
        if (!open) return;
        // The release always carries a point (the last one again when the tool did not move since the last batch),
        // so the server knows where the tool was let go.
        if (count == 0 || Float.isNaN(lastX) || Math.abs(x - lastX) + Math.abs(y - lastY) > 1.0E-4F) add(x, y);
        flush(true, false);
        open = false;
    }

    /** Lets go without completing (a thread dropped, a sweep broken off). */
    public void cancel() {
        if (!open) return;
        flush(true, true);
        open = false;
    }

    private void add(float x, float y) {
        if (count >= xs.length) flush(false, false);
        if (sink != null) sink.point(x, y, Float.isNaN(lastX));
        xs[count] = space == Space.PLANE ? StationCommandPacket.quantizePlane(x) : StationCommandPacket.quantize(x);
        ys[count] = space == Space.PLANE ? StationCommandPacket.quantizePlane(y) : StationCommandPacket.quantize(y);
        count++;
        lastX = x;
        lastY = y;
    }

    private void flush(boolean end, boolean cancel) {
        if (count == 0 && !end && !beginPending) return;
        int flags = (beginPending ? WorkAction.BEGIN : 0) | (end ? WorkAction.END : 0) | (cancel ? WorkAction.CANCEL : 0);
        StationFocus.send(action, flags, ints, Arrays.copyOf(xs, count), Arrays.copyOf(ys, count), null, null);
        beginPending = false;
        count = 0;
        lastFlushMs = Util.getMillis();
    }

    public float lastX() {
        return lastX;
    }

    public float lastY() {
        return lastY;
    }

    public float dirX() {
        return dirX;
    }

    public float dirY() {
        return dirY;
    }
}
