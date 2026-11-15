package com.otectus.immersivetanning.station;

import com.otectus.immersivetanning.advancement.ModAdvancements;
import com.otectus.immersivetanning.api.ImmersiveTanningAPI;
import com.otectus.immersivetanning.api.event.HideFleshedEvent;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.hide.HideItems;
import com.otectus.immersivetanning.item.ProcessedHideItem;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.minigame.FleshingSurface;
import com.otectus.immersivetanning.network.packet.StationAckPacket;
import com.otectus.immersivetanning.network.packet.StationCommandPacket;
import com.otectus.immersivetanning.network.packet.StationStrokePacket;
import com.otectus.immersivetanning.network.packet.WorkResultPacket;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModSounds;
import com.otectus.immersivetanning.util.Feedback;
import com.otectus.immersivetanning.util.StationEffects;
import com.otectus.immersivetanning.work.FleshingWork;
import com.otectus.immersivetanning.work.StationLease;
import com.otectus.immersivetanning.work.WorkAction;
import com.otectus.immersivetanning.work.WorkCommandHandler.Reply;
import com.otectus.immersivetanning.work.WorkNet;
import com.otectus.immersivetanning.work.WorkPreferences;
import com.otectus.immersivetanning.work.WorkRules;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Tanner's Rack's rules for hands-on work. Every path that changes the rack - the focused view, a quick sneak-use,
 * a test - goes through these few operations, so a hide is mounted, scraped, picked up, framed and committed one way.
 */
public final class RackStationLogic {

    public static Reply handle(ServerPlayer player, TannersRackBlockEntity rack, StationLease lease, StationCommandPacket p) {
        return switch (p.action()) {
            case FLESH -> flesh(player, rack, lease, p);
            case FLESH_ROUGH -> finishRoughly(player, rack);
            case RACK_TAKE -> take(player, rack) ? Reply.OK : Reply.refused(StationAckPacket.REFUSED);
            case RACK_MOUNT -> mount(player, rack, player.getMainHandItem()) ? Reply.OK : Reply.refused(StationAckPacket.NO_MATERIAL);
            case NEXT_HIDE -> nextHide(player, rack) ? Reply.OK : Reply.refused(StationAckPacket.NO_MATERIAL);
            case DETENT -> detent(player, rack, Finish.byOrdinal(p.arg(0, 1)));
            case TREAT -> treat(player, rack, p);
            case SEAT -> seat(player, rack) ? Reply.OK : Reply.refused(StationAckPacket.WRONG_STAGE);
            case HANDOFF -> handoff(player, rack) ? Reply.OK : Reply.refused(StationAckPacket.NOT_OWNER);
            default -> Reply.refused(StationAckPacket.REFUSED);
        };
    }

    // ------------------------------------------------------------------ fleshing

    private static Reply flesh(ServerPlayer player, TannersRackBlockEntity rack, StationLease lease, StationCommandPacket p) {
        FleshingWork work = rack.work();
        if (rack.mode() != TannersRackBlockEntity.Mode.RAW || work == null || rack.hide() == null) return Reply.refused(StationAckPacket.WRONG_STAGE);
        if (!work.ownedBy(player.getUUID())) {
            Feedback.fail(player, "hide_belongs_to", work.ownerName());
            return Reply.refused(StationAckPacket.NOT_OWNER);
        }
        int knife = WorkRules.findTool(player, WorkRules::isKnife, work.knifeSlot());
        if (knife < 0) {
            Feedback.fail(player, "need_knife");
            return Reply.refused(StationAckPacket.NO_TOOL);
        }
        work.claim(player.getUUID(), player.getGameProfile().getName());
        work.reserveKnife(knife);
        FleshingSurface surface = work.surface();
        long now = Util.getMillis();
        float budget = lease.unthrottled() ? FleshingWork.MAX_BUDGET : work.refillBudget(now);
        float before = surface.coverage();
        int accepted = 0;
        for (int i = 0; i < p.xs().length; i++) {
            int x = p.xs()[i];
            int y = p.ys()[i];
            if (i == 0 && p.has(WorkAction.BEGIN)) {
                surface.begin(x, y);
                work.countStroke();
            } else {
                if (!surface.isStroking()) break;
                float dx = (x - work.lastQX()) / (float) FleshingSurface.COORD_MAX * FleshingSurface.W;
                float dy = (y - work.lastQY()) / (float) FleshingSurface.COORD_MAX * FleshingSurface.H;
                float length = (float) Math.sqrt(dx * dx + dy * dy);
                if (length > budget) break; // faster than any hand: the rest of the batch is refused
                budget -= length;
                surface.drag(x, y);
            }
            work.lastPoint(x, y);
            accepted++;
        }
        work.spendBudget(budget);
        boolean ends = p.has(WorkAction.END);
        if (ends) surface.end();
        work.accepted(p.sequence());
        ServerLevel level = player.serverLevel();
        if (accepted > 0) {
            short[] xs = java.util.Arrays.copyOf(p.xs(), accepted);
            short[] ys = java.util.Arrays.copyOf(p.ys(), accepted);
            WorkNet.toWatchers(level, rack.getBlockPos(), player, new StationStrokePacket(rack.getBlockPos(), StationStrokePacket.FLESH,
                    player.getId(), p.has(WorkAction.BEGIN), ends, xs, ys));
        }
        if (surface.coverage() > before) StationEffects.send(level, rack.getBlockPos(), StationEffects.SCRAPE, surface.coverage() - before);
        if (ServerConfig.get(ServerConfig.CHALLENGE_TIMING) && work.challengeMs() > allowedMs(player)) {
            finishFleshing(player, rack, true);
        } else if (surface.isComplete()) {
            finishFleshing(player, rack, false);
        } else if (ends) {
            rack.changed(); // the authoritative hide, for everyone, after each stroke
        } else {
            rack.setChanged();
        }
        return Reply.OK;
    }

    /** The challenge-mode allowance (the only place {@code ITanningAssistSource} still acts). */
    public static int allowedMs(ServerPlayer player) {
        return ImmersiveTanningAPI.assistedFleshingMs(player, ServerConfig.get(ServerConfig.FLESHING_TIME_SECONDS) * 1000);
    }

    private static Reply finishRoughly(ServerPlayer player, TannersRackBlockEntity rack) {
        FleshingWork work = rack.work();
        if (rack.mode() != TannersRackBlockEntity.Mode.RAW || work == null) return Reply.refused(StationAckPacket.WRONG_STAGE);
        if (!work.ownedBy(player.getUUID())) return Reply.refused(StationAckPacket.NOT_OWNER);
        if (!work.surface().canFinishRoughly()) {
            Feedback.fail(player, "finish_roughly_more");
            return Reply.refused(StationAckPacket.REFUSED);
        }
        finishFleshing(player, rack, false);
        return Reply.OK;
    }

    /**
     * The hide is done (clean, finished roughly, or the challenge time ran out): progression mods hear the score, the
     * Integrity is settled and the clean hide stays on the beam to be picked up. The knife wears once for the hide.
     */
    public static void finishFleshing(ServerPlayer player, TannersRackBlockEntity rack, boolean timedOut) {
        FleshingWork work = rack.work();
        HideData hide = rack.hide();
        if (work == null || hide == null || rack.mode() != TannersRackBlockEntity.Mode.RAW) return;
        FleshingSurface surface = work.surface();
        surface.end();
        HideFleshedEvent event = new HideFleshedEvent(player, hide, surface.score(), rack.getBlockPos(), timedOut);
        MinecraftForge.EVENT_BUS.post(event);
        rack.completeFleshing(event.getScore(), player.getUUID(), player.getGameProfile().getName());
        WorkRules.wear(player, work.knifeSlot(), WorkRules::isKnife);
        player.level().playSound(null, rack.getBlockPos(), ModSounds.RACK_MOUNT.get(), SoundSource.BLOCKS, 0.8F, 1.2F);
        ModAdvancements.award(player, ModAdvancements.FLESHED);
        List<String> why = new ArrayList<>();
        why.add(surface.isComplete() ? "clean_hide" : "flesh_left");
        if (surface.control() < 0.85F) why.add("slipped_off");
        if (surface.overworkPenalty() > 0F) why.add("overworked");
        if (timedOut) why.add("out_of_time");
        HideData done = rack.hide();
        WorkNet.to(player, new WorkResultPacket(rack.getBlockPos(), WorkResultPacket.FLESHING, done == null ? 0 : done.integrity(), -1, why));
    }

    // ------------------------------------------------------------------ handling the hide

    /** Picks up whatever the rack holds and the player may take: the clean hide, the leather, a hide in the frame. */
    public static boolean take(ServerPlayer player, TannersRackBlockEntity rack) {
        ItemStack stack;
        switch (rack.mode()) {
            case RAW -> {
                FleshingWork work = rack.work();
                if (work != null && work.started() && !work.ownedBy(player.getUUID()) && !player.hasPermissions(2)) {
                    Feedback.fail(player, "hide_belongs_to", work.ownerName());
                    return false;
                }
                if (!fits(player, rack.rawStack())) return false;
                stack = rack.takeRaw();
            }
            case SCRAPED -> {
                if (rack.scrapedBy() != null && !rack.scrapedBy().equals(player.getUUID()) && !player.hasPermissions(2)) {
                    Feedback.fail(player, "hide_belongs_to", rack.scrapedByName());
                    return false;
                }
                if (rack.hide() == null || !fits(player, ModItems.SCRAPED_HIDE.get().create(rack.hide()))) return false;
                stack = rack.takeScraped();
            }
            case FRAMING -> {
                if (rack.hide() == null || !fits(player, ModItems.WET_TANNED_HIDE.get().create(rack.hide()))) return false;
                stack = rack.takeWet();
            }
            case DRYING -> {
                if (rack.isDry()) {
                    HideData hide = rack.hide();
                    if (hide == null || !fits(player, ModItems.leather(rack.finish()).create(hide.integrity(), hide.trait(), hide.size().leather()))) return false;
                    stack = rack.collectLeather();
                    player.level().playSound(null, rack.getBlockPos(), ModSounds.BENCH_FINISH.get(), SoundSource.BLOCKS, 0.8F, 1.1F);
                    ModAdvancements.award(player, ModAdvancements.FINISHED);
                } else {
                    stack = rack.takeWet();
                }
            }
            default -> {
                return false;
            }
        }
        if (stack.isEmpty()) return false;
        player.getInventory().add(stack);
        if (!stack.isEmpty()) player.drop(stack, false);
        player.level().playSound(null, rack.getBlockPos(), ModSounds.WET_LEATHER.get(), SoundSource.BLOCKS, 0.6F, 1.0F);
        return true;
    }

    /** Whether the player has room: a pickup that would not fit leaves the hide on the rack rather than dropping it. */
    public static boolean fits(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return false;
        Inventory inv = player.getInventory();
        if (inv.getFreeSlot() >= 0 || inv.getSlotWithRemainingSpace(stack) >= 0) return true;
        Feedback.fail(player, "inventory_full");
        return false;
    }

    /**
     * Places one hide from a stack on the rack: a raw hide over the beam (with the work it carries), a wet hide into
     * the frame (resuming its drying when it was taken off unfinished). Returns whether one was placed.
     */
    public static boolean mount(ServerPlayer player, TannersRackBlockEntity rack, ItemStack held) {
        if (!rack.isEmpty() || held.isEmpty()) return false;
        Optional<HideData> raw = HideItems.resolve(player.level(), held);
        if (raw.isPresent()) {
            CompoundTag root = held.getTagElement(HideData.ROOT);
            CompoundTag carried = root != null && root.contains(TannersRackBlockEntity.FLESHING) ? root.getCompound(TannersRackBlockEntity.FLESHING) : null;
            rack.mountRaw(raw.get(), carried);
            if (!player.getAbilities().instabuild) held.shrink(1);
            player.level().playSound(null, rack.getBlockPos(), ModSounds.RACK_MOUNT.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
            return true;
        }
        if (held.is(ModItems.WET_TANNED_HIDE.get())) {
            rack.mountWet(held, ProcessedHideItem.data(held), WorkPreferences.lastFinish(player));
            if (!player.getAbilities().instabuild) held.shrink(1);
            player.level().playSound(null, rack.getBlockPos(), ModSounds.WET_LEATHER.get(), SoundSource.BLOCKS, 0.9F, 0.9F);
            return true;
        }
        return false;
    }

    /** Mounts the next raw hide from the player's inventory on an empty beam (repeat work keeps the view where it is). */
    public static boolean nextHide(ServerPlayer player, TannersRackBlockEntity rack) {
        if (!rack.isEmpty()) return false;
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.items.size(); slot++) {
            ItemStack stack = inv.items.get(slot);
            if (HideItems.resolve(player.level(), stack).isPresent()) return mount(player, rack, stack);
        }
        Feedback.info(player, "no_more_hides");
        return false;
    }

    // ------------------------------------------------------------------ finishing

    private static Reply detent(ServerPlayer player, TannersRackBlockEntity rack, Finish finish) {
        if (rack.mode() != TannersRackBlockEntity.Mode.FRAMING) return Reply.refused(StationAckPacket.WRONG_STAGE);
        if (finish != rack.finish()) {
            rack.setDetent(finish);
            player.level().playSound(null, rack.getBlockPos(), ModSounds.RACK_LACE.get(), SoundSource.BLOCKS, 0.6F,
                    0.8F + finish.ordinal() * 0.15F);
        }
        return Reply.OK;
    }

    /**
     * The finish's short treatment: smoothing (Supple) or burnishing (Hardened) across the hide, drawing the cords
     * (Harness). Broad and forgiving: about one and a half widths of the frame of contact. Nothing is scored.
     */
    private static Reply treat(ServerPlayer player, TannersRackBlockEntity rack, StationCommandPacket p) {
        if (rack.mode() != TannersRackBlockEntity.Mode.FRAMING) return Reply.refused(StationAckPacket.WRONG_STAGE);
        boolean wasTreated = rack.treated();
        float travel = 0F;
        float lx = Float.NaN;
        float ly = Float.NaN;
        for (int i = 0; i < p.xs().length; i++) {
            float x = StationCommandPacket.plane(p.xs()[i]);
            float y = StationCommandPacket.plane(p.ys()[i]);
            if (!Float.isNaN(lx)) {
                float dx = x - lx;
                float dy = y - ly;
                if (rack.finish() == Finish.HARNESS) travel += Math.max(0F, dy) / 0.45F * 1.6F;
                else if (x > -0.05F && x < 1.05F && y > -0.05F && y < 1.05F) travel += (float) Math.sqrt(dx * dx + dy * dy);
            }
            lx = x;
            ly = y;
        }
        rack.treat(Math.min(0.4F, travel / 1.6F));
        if (!p.has(WorkAction.BEGIN) || p.xs().length > 1) {
            WorkNet.toWatchers(player.serverLevel(), rack.getBlockPos(), player, new StationStrokePacket(rack.getBlockPos(),
                    StationStrokePacket.TREAT, player.getId(), p.has(WorkAction.BEGIN), p.has(WorkAction.END), p.xs(), p.ys()));
        }
        if (!wasTreated && rack.treated()) {
            player.level().playSound(null, rack.getBlockPos(), switch (rack.finish()) {
                case SUPPLE -> ModSounds.RACK_SMOOTH.get();
                case HARNESS -> ModSounds.RACK_LACE.get();
                case HARDENED -> ModSounds.RACK_BURNISH.get();
            }, SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        return Reply.OK;
    }

    /** Seats the mechanism: the finish is committed and drying starts. */
    public static boolean seat(ServerPlayer player, TannersRackBlockEntity rack) {
        if (rack.mode() != TannersRackBlockEntity.Mode.FRAMING) return false;
        if (!rack.treated()) {
            Feedback.fail(player, "treat_first");
            return false;
        }
        Finish finish = rack.finish();
        if (!rack.seat()) return false;
        WorkPreferences.setLastFinish(player, finish);
        player.level().playSound(null, rack.getBlockPos(), ModSounds.RACK_SEAT.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
        WorkNet.to(player, new WorkResultPacket(rack.getBlockPos(), WorkResultPacket.FINISHING, -1, finish.ordinal(), List.of()));
        return true;
    }

    /** The quick route: a wet hide is laced in with the player's last finish, treated and seated in one use. */
    public static boolean quickFinish(ServerPlayer player, TannersRackBlockEntity rack, ItemStack held) {
        if (!rack.isEmpty() || !held.is(ModItems.WET_TANNED_HIDE.get())) return false;
        Finish finish = WorkPreferences.lastFinish(player);
        rack.mountWet(held, ProcessedHideItem.data(held), finish);
        if (rack.mode() == TannersRackBlockEntity.Mode.FRAMING) rack.startDrying(finish);
        if (!player.getAbilities().instabuild) held.shrink(1);
        player.level().playSound(null, rack.getBlockPos(), ModSounds.RACK_SEAT.get(), SoundSource.BLOCKS, 0.9F, 0.9F);
        Feedback.info(player, "hide_drying", rack.finish().displayName());
        return true;
    }

    private static boolean handoff(ServerPlayer player, TannersRackBlockEntity rack) {
        FleshingWork work = rack.work();
        if (work != null && work.owner() != null && work.owner().equals(player.getUUID())) {
            work.release();
            rack.changed();
            Feedback.info(player, "handed_off");
            return true;
        }
        return false;
    }

    /** The lease's gesture timed out: the blade is lifted. */
    public static void lift(TannersRackBlockEntity rack) {
        if (rack.work() != null) rack.work().surface().end();
    }

    private RackStationLogic() {}
}
