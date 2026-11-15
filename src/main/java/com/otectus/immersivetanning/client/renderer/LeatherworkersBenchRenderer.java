package com.otectus.immersivetanning.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.otectus.immersivetanning.blockentity.LeatherworkersBenchBlockEntity;
import com.otectus.immersivetanning.client.render.WorkDraw;
import com.otectus.immersivetanning.client.render.WorkHands;
import com.otectus.immersivetanning.client.station.BenchBehavior;
import com.otectus.immersivetanning.client.station.ClientWork;
import com.otectus.immersivetanning.client.station.StationFocus;
import com.otectus.immersivetanning.config.ClientConfig;
import com.otectus.immersivetanning.minigame.StitchPattern;
import com.otectus.immersivetanning.network.packet.OperatorPosePacket;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.station.BenchGeometry;
import com.otectus.immersivetanning.station.StationFrame;
import com.otectus.immersivetanning.util.StationEffects;
import com.otectus.immersivetanning.work.LeatherworkJob;
import com.otectus.immersivetanning.work.WorkPattern;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The piece taking shape on the bench, from the job's synchronised state: the chosen leather with the pattern paper
 * laid over it, the cut lines (the active one dashed from its start, cut lengths solid, rough edges where the blade
 * strayed), freed panels waiting at the far edge with the offcuts in the scrap pile, the jig and the soft outline of
 * where each panel seats, the holes, the thread of every stitch (slack, even or puckered as it was drawn) and the
 * thread in hand, and the finished piece on the output pad. Tools and hands follow the operator; watchers see the
 * operator's tool and carried panel where their pose puts them.
 */
public class LeatherworkersBenchRenderer implements BlockEntityRenderer<LeatherworkersBenchBlockEntity> {
    private static final float PX = 1F / 16F;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private final ItemRenderer itemRenderer;

    public LeatherworkersBenchRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(LeatherworkersBenchBlockEntity bench, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = StationFrame.facing(bench.getBlockState());
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(StationFrame.modelYRot(facing)));
        pose.translate(-0.5, 0, -0.5);
        VertexConsumer props = WorkDraw.props(buffers);
        BenchBehavior local = StationFocus.behaviorAt(bench.getBlockPos()) instanceof BenchBehavior b ? b : null;
        book(bench, pose, buffers, light);
        output(bench, pose, buffers, props, light);
        LeatherworkJob job = bench.job();
        if (job != null) {
            int color = 0xFF000000 | job.finish().leatherColor();
            switch (job.workStage()) {
                case RESERVED, CUTTING -> cutting(job, pose, props, light, color);
                case ASSEMBLY -> assembly(job, local, bench.getBlockPos(), pose, props, light, color);
                default -> stitching(job, local, pose, props, light, color);
            }
            if (job.kind() == LeatherworkJob.Kind.REPAIR) {
                pose.pushPose();
                Vec3 at = BenchGeometry.point(0.5F, 0.58F, -0.05F);
                pose.translate(at.x * PX, at.y * PX, at.z * PX);
                pose.mulPose(Axis.XP.rotationDegrees(90F));
                pose.scale(0.42F, 0.42F, 0.42F);
                itemRenderer.renderStatic(job.result(), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, bench.getLevel(), 1);
                pose.popPose();
            }
            tools(bench, job, local, pose, buffers, light, facing);
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------ props

    private void book(LeatherworkersBenchBlockEntity bench, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 c = BenchGeometry.BOOK.getCenter();
        pose.pushPose();
        pose.translate(c.x * PX, (BenchGeometry.BOOK.minY + 0.15) * PX, c.z * PX);
        pose.mulPose(Axis.XP.rotationDegrees(90F));
        pose.mulPose(Axis.ZP.rotationDegrees(-18F));
        pose.scale(0.3F, 0.3F, 0.3F);
        itemRenderer.renderStatic(new ItemStack(Items.WRITABLE_BOOK), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                bench.getLevel(), 2);
        pose.popPose();
    }

    private void output(LeatherworkersBenchBlockEntity bench, PoseStack pose, MultiBufferSource buffers, VertexConsumer props, int light) {
        var pad = BenchGeometry.OUTPUT_PAD;
        WorkDraw.box(pose, props, pad.minX, 15.0, pad.minZ, pad.maxX, 15.3, pad.maxZ, 0xFF6A5038, light, WorkDraw.WOOD);
        List<ItemStack> out = bench.output();
        if (out.isEmpty()) return;
        float rise = 1F;
        for (ClientWork.Animation a : ClientWork.animations(bench.getBlockPos(), 2F)) {
            if (a.type() == StationEffects.FINISHED && ClientConfig.get(ClientConfig.CAMERA_MODE) != ClientConfig.CameraMode.REDUCED_MOTION) {
                float t = Mth.clamp(a.age() / 0.6F, 0F, 1F);
                rise = Math.min(rise, t * t * (3F - 2F * t));
            }
        }
        Vec3 c = pad.getCenter();
        for (int i = 0; i < Math.min(3, out.size()); i++) {
            pose.pushPose();
            double y = 15.35 + i * 0.5 + (1F - rise) * 2.0;
            pose.translate((c.x + (i - 1) * 0.6) * PX, y * PX, (c.z - i * 0.8) * PX);
            pose.mulPose(Axis.XP.rotationDegrees(90F));
            pose.mulPose(Axis.ZP.rotationDegrees(25F + i * 40F));
            float s = i == 0 ? 0.36F : 0.24F;
            pose.scale(s, s, s);
            itemRenderer.renderStatic(out.get(i), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, bench.getLevel(), 3 + i);
            pose.popPose();
        }
        // Offcuts in the scrap pile when scraps came with the piece.
        boolean scraps = out.stream().anyMatch(s -> s.is(ModItems.LEATHER_SCRAPS.get()));
        if (scraps) {
            var pile = BenchGeometry.SCRAP_PILE;
            for (int k = 0; k < 4; k++) {
                double x = pile.minX + 0.4 + (k * 0.55) % 2.0;
                double z = pile.minZ + 0.6 + k * 0.6;
                WorkDraw.box(pose, props, x, 15.0, z, x + 0.9, 15.2 + k * 0.05, z + 0.5, 0xFFA07040, light, WorkDraw.LEATHER);
            }
        }
    }

    // ------------------------------------------------------------------ stages

    private static float[] toLocal(float[] w) {
        float[] out = new float[w.length];
        for (int i = 0; i + 1 < w.length; i += 2) {
            Vec3 p = BenchGeometry.point(w[i], w[i + 1]);
            out[i] = (float) p.x;
            out[i + 1] = (float) p.z;
        }
        return out;
    }

    private static List<Vec3> polyline(float[] w, float lift) {
        List<Vec3> out = new ArrayList<>();
        for (int i = 0; i + 1 < w.length; i += 2) out.add(BenchGeometry.point(w[i], w[i + 1], lift));
        return out;
    }

    private static float[] closed(float[] w) {
        float[] out = java.util.Arrays.copyOf(w, w.length + 2);
        out[w.length] = w[0];
        out[w.length + 1] = w[1];
        return out;
    }

    private void cutting(LeatherworkJob job, PoseStack pose, VertexConsumer props, int light, int color) {
        WorkPattern pattern = job.pattern();
        // The leather sheet under the paper: a rounded rectangle around the panels' layout.
        float minX = 1, minY = 1, maxX = 0, maxY = 0;
        for (WorkPattern.Panel p : pattern.panels()) {
            float[] outline = WorkPattern.placePath(p, p.outline(), p.layoutX(), p.layoutY(), p.layoutRot());
            for (int i = 0; i < outline.length; i += 2) {
                minX = Math.min(minX, outline[i] - 0.04F);
                maxX = Math.max(maxX, outline[i] + 0.04F);
                minY = Math.min(minY, outline[i + 1] - 0.04F);
                maxY = Math.max(maxY, outline[i + 1] + 0.04F);
            }
        }
        minX = Math.max(0.02F, minX);
        minY = Math.max(0.02F, minY);
        maxX = Math.min(0.98F, maxX);
        maxY = Math.min(0.98F, maxY);
        float[] sheet = {minX, minY, maxX, minY, maxX, maxY, minX, maxY};
        WorkDraw.leather(pose, props, toLocal(sheet), BenchGeometry.PLANE_Y, 0.10, color, light);
        for (WorkPattern.Panel p : pattern.panels()) {
            float[] outline = WorkPattern.placePath(p, p.outline(), p.layoutX(), p.layoutY(), p.layoutRot());
            WorkDraw.polyline(pose, props, polyline(closed(outline), 0.05F), UP, 0.22, 0, 0xFFF2EAD8, light, WorkDraw.PAPER);
            grainMark(p, p.layoutX(), p.layoutY(), p.layoutRot(), 0.08F, pose, props, light, 0xFFCFB892);
        }
        int active = job.activeCut();
        for (int c = 0; c < pattern.cuts().size(); c++) {
            float[] path = pattern.cutPath(c);
            float progress = job.cutProgress(c);
            List<Vec3> line = polyline(path, 0.09F);
            if (c == active) {
                WorkDraw.polyline(pose, props, line, UP, 0.24, 0.45, 0xFFFFE9A0, light, WorkDraw.WHITE);
                // The bright lead follows the uncut end, including a cut resumed after leaving the station.
                float[] start = StitchPattern.pointAt(path, progress);
                float[] ahead = StitchPattern.pointAt(path, Math.min(1F, progress + 0.08F));
                Vec3 a = BenchGeometry.point(start[0], start[1], 0.1F);
                Vec3 b = BenchGeometry.point(ahead[0], ahead[1], 0.1F);
                WorkDraw.ribbon(pose, props, a, b, UP, 0.55, 0xFFFFD050, light, WorkDraw.WHITE);
            } else if (progress < 1F) {
                WorkDraw.polyline(pose, props, line, UP, 0.16, 0.35, 0xA0E8E0D0, light, WorkDraw.WHITE);
            }
            if (progress > 0F) {
                // The cut itself: a dark slit as far as the blade really went.
                List<Vec3> cut = new ArrayList<>();
                int steps = 12;
                for (int i = 0; i <= steps; i++) {
                    float[] q = StitchPattern.pointAt(path, progress * i / steps);
                    cut.add(BenchGeometry.point(q[0], q[1], 0.11F));
                }
                WorkDraw.polyline(pose, props, cut, UP, 0.2, 0, 0xFF1E140C, light, WorkDraw.DARK);
            }
        }
        for (float[] mark : job.strayMarks()) {
            Vec3 m = BenchGeometry.point(mark[0], mark[1], 0.1F);
            WorkDraw.ribbon(pose, props, m.add(-0.25, 0, -0.1), m.add(0.25, 0, 0.12), UP, 0.14, 0xFF2A1C10, light, WorkDraw.DARK);
        }
    }

    /** Chalk grain arrow: it turns with the pattern and helps read the direction of narrow bands and mirrored pieces. */
    private void grainMark(WorkPattern.Panel panel, float x, float y, float rot, float lift, PoseStack pose,
                           VertexConsumer props, int light, int color) {
        float[] arrow = panel.w() > panel.h()
                ? new float[]{0.30F, 0.5F, 0.70F, 0.5F, 0.61F, 0.32F, 0.70F, 0.5F, 0.61F, 0.68F}
                : new float[]{0.5F, 0.70F, 0.5F, 0.30F, 0.38F, 0.39F, 0.5F, 0.30F, 0.62F, 0.39F};
        // Find a quiet interior spot even on a concave boot quarter. Never put chalk in a neckline or off the leather.
        float cx = 0, cy = 0;
        for (int i = 0; i < panel.outline().length; i += 2) {
            cx += panel.outline()[i];
            cy += panel.outline()[i + 1];
        }
        cx /= panel.outline().length / 2;
        cy /= panel.outline().length / 2;
        for (int i = 0; i < arrow.length; i += 2) {
            arrow[i] = cx + (arrow[i] - 0.5F) * 0.65F;
            arrow[i + 1] = cy + (arrow[i + 1] - 0.5F) * 0.65F;
            if (!inside(panel.outline(), arrow[i], arrow[i + 1])) return;
        }
        WorkDraw.polyline(pose, props, polyline(WorkPattern.placePath(panel, arrow, x, y, rot), lift), UP,
                0.055, 0, color, light, WorkDraw.WHITE);
    }

    private static boolean inside(float[] polygon, float x, float y) {
        boolean inside = false;
        for (int i = 0, j = polygon.length - 2; i < polygon.length; j = i, i += 2) {
            if ((polygon[i + 1] > y) != (polygon[j + 1] > y)
                    && x < (polygon[j] - polygon[i]) * (y - polygon[i + 1]) / (polygon[j + 1] - polygon[i + 1]) + polygon[i]) inside = !inside;
        }
        return inside;
    }

    private void panel(WorkPattern pattern, int index, float[] at, float lift, PoseStack pose, VertexConsumer props, int light, int color) {
        WorkPattern.Panel p = pattern.panels().get(index);
        float[] outline = WorkPattern.placePath(p, p.outline(), at[0], at[1], at[2]);
        WorkDraw.leather(pose, props, toLocal(outline), BenchGeometry.PLANE_Y + lift + 0.12, 0.12, color, light);
        WorkDraw.polyline(pose, props, polyline(closed(outline), lift + 0.14F), UP, 0.10, 0, WorkDraw.shade(color, 0.72F), light, WorkDraw.LEATHER);
    }

    private void jig(PoseStack pose, VertexConsumer props, int light) {
        Vec3 a = BenchGeometry.point(0.26F, 0.4F);
        Vec3 b = BenchGeometry.point(0.74F, 0.78F);
        WorkDraw.box(pose, props, Math.min(a.x, b.x), BenchGeometry.PLANE_Y - 0.05, Math.min(a.z, b.z), Math.max(a.x, b.x), BenchGeometry.PLANE_Y + 0.06,
                Math.max(a.z, b.z), 0xFF8A6E50, light, WorkDraw.WOOD);
    }

    private void assembly(LeatherworkJob job, BenchBehavior local, net.minecraft.core.BlockPos pos, PoseStack pose, VertexConsumer props, int light, int color) {
        WorkPattern pattern = job.pattern();
        jig(pose, props, light);
        for (int p = 0; p < pattern.panels().size(); p++) {
            if (job.seated(p)) continue;
            WorkPattern.Placement target = pattern.placement(p);
            WorkPattern.Panel panel = pattern.panels().get(p);
            float[] ghost = WorkPattern.placePath(panel, panel.outline(), target.x(), target.y(), target.rot());
            boolean near = false;
            if (local != null && local.carried() == p) {
                float[] at = local.panelPlace(job, p);
                near = Mth.sqrt((at[0] - target.x()) * (at[0] - target.x()) + (at[1] - target.y()) * (at[1] - target.y())) <= target.posTol()
                        && Math.abs(Mth.wrapDegrees(at[2] - target.rot())) <= target.rotTol();
            }
            WorkDraw.polyline(pose, props, polyline(closed(ghost), 0.13F), UP, near ? 0.3 : 0.18, near ? 0 : 0.4,
                    near ? (ClientConfig.get(ClientConfig.COLORBLIND_SAFE_COLORS) ? 0xFF9CD9FF : 0xFFB0D8A0) : 0xFFE6D5AE, light, WorkDraw.WHITE);
            grainMark(panel, target.x(), target.y(), target.rot(), 0.14F, pose, props, light, 0xFFD6C9AD);
        }
        for (int p = 0; p < pattern.panels().size(); p++) {
            float[] at = local != null ? local.panelPlace(job, p) : placeRemote(job, p, pos);
            boolean lifted = local != null && local.carried() == p;
            panel(pattern, p, at, lifted ? 0.9F : job.seated(p) ? 0.12F : 0F, pose, props, light, color);
        }
        // Offcuts of the cut wait in the scrap pile.
        var pile = BenchGeometry.SCRAP_PILE;
        WorkDraw.box(pose, props, pile.minX + 0.3, 15.0, pile.minZ + 0.5, pile.maxX - 0.3, 15.25, pile.maxZ - 0.5, WorkDraw.shade(color, 0.9F), light, WorkDraw.LEATHER);
    }

    /** Where a watcher sees a panel: seated, carried by the operator (their relayed pose), or waiting at the far edge. */
    private static float[] placeRemote(LeatherworkJob job, int p, net.minecraft.core.BlockPos pos) {
        if (job.seated(p)) return new float[]{job.seatX(p), job.seatY(p), job.seatRot(p)};
        ClientWork.RemotePose remote = ClientWork.remote(pos);
        if (remote != null && remote.held == p) {
            return new float[]{(BenchGeometry.LEFT - remote.x) / BenchGeometry.SIZE, (BenchGeometry.FAR - remote.z) / BenchGeometry.SIZE, remote.rot};
        }
        return job.pattern().staging(p);
    }

    private void stitching(LeatherworkJob job, BenchBehavior local, PoseStack pose, VertexConsumer props, int light, int color) {
        WorkPattern pattern = job.pattern();
        jig(pose, props, light);
        for (int p = 0; p < pattern.panels().size(); p++) {
            panel(pattern, p, new float[]{job.seatX(p), job.seatY(p), job.seatRot(p)}, 0.12F, pose, props, light, color);
        }
        float lift = 0.3F;
        int next = job.nextStitch();
        WorkPattern.Join activeJoin = next >= 0 ? pattern.joinOf(next) : null;
        // Stitching grooves describe the whole seam; the next hole alone carries the bright interaction marker.
        for (WorkPattern.Join join : pattern.joins()) {
            int tint = join == activeJoin ? 0xFFC5AA7F : WorkDraw.shade(color, 0.72F);
            int[] panels = {join.panelA(), join.panelB()};
            float[][] paths = {join.pathA(), join.pathB()};
            for (int side = 0; side < 2; side++) {
                int p = panels[side];
                float[] path = WorkPattern.placePath(pattern.panels().get(p), paths[side], job.seatX(p), job.seatY(p), job.seatRot(p));
                WorkDraw.polyline(pose, props, polyline(path, lift - 0.01F), UP, 0.055, 0.28, tint, light, WorkDraw.WHITE);
            }
        }
        for (int a = 0; a < job.stitchCount(); a++) {
            float[] f = job.stitchFrame(a);
            Vec3 ha = BenchGeometry.point(f[0], f[1], lift);
            Vec3 hb = BenchGeometry.point(f[2], f[3], lift);
            WorkDraw.box(pose, props, ha.x - 0.075, ha.y - 0.05, ha.z - 0.075, ha.x + 0.075, ha.y + 0.02, ha.z + 0.075, 0xFF120C08, light, WorkDraw.DARK);
            WorkDraw.box(pose, props, hb.x - 0.075, hb.y - 0.05, hb.z - 0.075, hb.x + 0.075, hb.y + 0.02, hb.z + 0.075, 0xFF120C08, light, WorkDraw.DARK);
            float q = job.stitchQuality(a);
            if (!Float.isNaN(q)) {
                LeatherworkJob.Tension t = job.tension(a);
                int thread = t == LeatherworkJob.Tension.CLEAN ? 0xFFF0E2C0 : t == LeatherworkJob.Tension.SLACK ? 0xFFD8C8A8 : 0xFFE8D2B0;
                if (t == LeatherworkJob.Tension.SLACK) {
                    // A loose stitch bows away from the seam.
                    Vec3 mid = ha.add(hb).scale(0.5).add(f[6] * -0.5, 0.08, f[7] * 0.5);
                    WorkDraw.thread(pose, props, ha, mid, hb, UP, 0.12, thread, light);
                } else {
                    WorkDraw.ribbon(pose, props, ha.add(0, 0.03, 0), hb.add(0, 0.03, 0), UP, 0.12, thread, light, WorkDraw.THREAD);
                }
                if (t == LeatherworkJob.Tension.PUCKERED) {
                    // A puckered edge: small gathers either side of the stitch.
                    Vec3 along = new Vec3(f[7], 0, -f[6]).scale(0.35);
                    WorkDraw.ribbon(pose, props, ha.add(along), ha.subtract(along), UP, 0.1, WorkDraw.shade(color, 0.7F), light, WorkDraw.LEATHER);
                    WorkDraw.ribbon(pose, props, hb.add(along), hb.subtract(along), UP, 0.1, WorkDraw.shade(color, 0.7F), light, WorkDraw.LEATHER);
                }
            } else if (a == next && job.pending() < 0) {
                Vec3 c = BenchGeometry.point(f[4], f[5], lift);
                WorkDraw.ring(pose, props, c.x, c.y + 0.02, c.z, 0.45, 0.14, 0xD0FFE9A0, light);
            }
        }
        // The thread in hand: from the hole pair to where it is drawn; slack droops, taut is straight.
        int pending = local != null && local.stitching() >= 0 ? local.stitching() : job.pending();
        if (pending >= 0) {
            float[] f = job.stitchFrame(pending);
            float px = local != null && local.stitching() >= 0 ? local.wx() : job.pullX();
            float py = local != null && local.stitching() >= 0 ? local.wy() : job.pullY();
            WorkPattern.Join join = pattern.joinOf(pending);
            float across = Math.abs((px - f[4]) * f[6] + (py - f[5]) * f[7]);
            float slackness = Mth.clamp(1F - across / join.pullMin(), 0F, 1F);
            Vec3 from = BenchGeometry.point(f[4], f[5], lift + 0.05F);
            Vec3 to = BenchGeometry.point(px, py, 0.8F);
            Vec3 mid = from.add(to).scale(0.5).add(f[7] * slackness * 0.6, -0.3 * slackness, -f[6] * slackness * 0.6);
            WorkDraw.thread(pose, props, from, mid, to, new Vec3(0, 0, 1), 0.12, 0xFFF0E2C0, light);
        }
        if (job.workStage() == LeatherworkJob.Stage.TIE || job.workStage() == LeatherworkJob.Stage.DONE) {
            int last = Math.max(0, job.stitchCount() - 1);
            float[] f = job.stitchFrame(last);
            Vec3 knot = BenchGeometry.point(f[4] + 0.04F, f[5] + 0.04F, lift + 0.1F);
            WorkDraw.box(pose, props, knot.x - 0.25, knot.y, knot.z - 0.25, knot.x + 0.25, knot.y + 0.3, knot.z + 0.25, 0xFFF0E2C0, light, WorkDraw.THREAD);
        }
    }

    // ------------------------------------------------------------------ tools

    private void tools(LeatherworkersBenchBlockEntity bench, LeatherworkJob job, BenchBehavior local, PoseStack pose, MultiBufferSource buffers,
                       int light, Direction facing) {
        Vec3 point;
        boolean down;
        int kind;
        if (local != null && (local.knifeOut() || local.awlOut())) {
            down = local.toolDown();
            kind = local.knifeOut() ? OperatorPosePacket.TOOL_KNIFE : OperatorPosePacket.TOOL_AWL;
            point = BenchGeometry.point(local.wx(), local.wy(), down ? 0.06F : 1.0F);
        } else {
            ClientWork.RemotePose remote = ClientWork.remote(bench.getBlockPos());
            if (remote == null || (remote.tool != OperatorPosePacket.TOOL_KNIFE && remote.tool != OperatorPosePacket.TOOL_AWL)) return;
            float t = Mth.clamp((Util.getMillis() - remote.updatedMs) / 100F, 0F, 1F);
            point = new Vec3(Mth.lerp(t, remote.px, remote.x), Mth.lerp(t, remote.py, remote.y), Mth.lerp(t, remote.pz, remote.z));
            down = remote.pressed;
            kind = remote.tool;
        }
        boolean knife = kind == OperatorPosePacket.TOOL_KNIFE;
        ItemStack stack = held(knife);
        // Drawn from the viewer's side: the knife's edge on the leather, the awl's tip upright into the hole.
        double side = local == null || WorkHands.rightHanded() ? 1 : -1;
        Vec3 shaft = knife ? new Vec3(side * 0.25, -0.75, 0.6).normalize() : new Vec3(side * 0.15, -0.95, 0.25).normalize();
        Vec3 grip = WorkHands.tool(pose, buffers, stack, knife ? WorkHands.Tool.KNIFE : WorkHands.Tool.AWL, point, shaft, new Vec3(0, 0.2, -1),
                light, bench.getLevel(), 7);
        if (local != null && (local.knifeOut() || local.awlOut())) {
            WorkHands.arm(pose, bench.getBlockPos(), facing, WorkHands.rightHanded(), grip, light);
            if (com.otectus.immersivetanning.config.ClientConfig.get(com.otectus.immersivetanning.config.ClientConfig.SUPPORTING_HAND)) {
                Vec3 hold = supportingPalm(job, knife);
                WorkHands.arm(pose, bench.getBlockPos(), facing, !WorkHands.rightHanded(), hold, light);
            }
        }
    }

    /** Steady the actual piece, including small keepers and rotated cutting layouts, with the palm above its surface. */
    private static Vec3 supportingPalm(LeatherworkJob job, boolean cutting) {
        WorkPattern pattern = job.pattern();
        int index = cutting && job.activeCut() >= 0 ? pattern.cuts().get(job.activeCut()).panel()
                : pattern.joinOf(Math.max(0, job.nextStitch())).panelA();
        WorkPattern.Panel panel = pattern.panels().get(index);
        float x = cutting ? panel.layoutX() : job.seatX(index);
        float y = cutting ? panel.layoutY() : job.seatY(index);
        float rot = cutting ? panel.layoutRot() : job.seatRot(index);
        float desiredX = x + (WorkHands.rightHanded() ? -0.12F : 0.12F);
        float[] best = {x, y};
        float score = Float.MAX_VALUE;
        for (int u = 1; u < 10; u++) for (int v = 1; v < 10; v++) {
            if (!inside(panel.outline(), u / 10F, v / 10F)) continue;
            float[] point = WorkPattern.place(panel, u / 10F, v / 10F, x, y, rot);
            float distance = Math.abs(point[0] - desiredX) + Math.abs(point[1] - (y + 0.06F));
            if (distance < score) {
                score = distance;
                best = point;
            }
        }
        return BenchGeometry.point(best[0], best[1], 1.6F);
    }

    private static ItemStack held(boolean knife) {
        var player = Minecraft.getInstance().player;
        var item = knife ? ModItems.TANNERS_KNIFE.get() : ModItems.AWL.get();
        if (player != null) {
            if (player.getMainHandItem().is(item)) return player.getMainHandItem();
            for (ItemStack s : player.getInventory().items) if (s.is(item)) return s;
        }
        return new ItemStack(item);
    }

}
