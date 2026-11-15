package com.otectus.immersivetanning.station;

import com.otectus.immersivetanning.advancement.ModAdvancements;
import com.otectus.immersivetanning.block.TannersVatBlock;
import com.otectus.immersivetanning.blockentity.TannersVatBlockEntity;
import com.otectus.immersivetanning.hide.HideItems;
import com.otectus.immersivetanning.item.ProcessedHideItem;
import com.otectus.immersivetanning.network.packet.StationAckPacket;
import com.otectus.immersivetanning.network.packet.StationCommandPacket;
import com.otectus.immersivetanning.network.packet.StationStrokePacket;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModSounds;
import com.otectus.immersivetanning.registry.ModTags;
import com.otectus.immersivetanning.util.Feedback;
import com.otectus.immersivetanning.util.StationEffects;
import com.otectus.immersivetanning.work.StationLease;
import com.otectus.immersivetanning.work.WorkAction;
import com.otectus.immersivetanning.work.WorkCommandHandler.Reply;
import com.otectus.immersivetanning.work.WorkNet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The Tanner's Vat's hands-on actions. Each is one transaction through the vat's own operations (which all end in its
 * single start transition), so water, bark and hides work in any order, each hide pays its tannin once, and the visual
 * pour or splash is only the picture of a transfer that already happened. One bucket is one bucket.
 */
public final class VatStationLogic {
    /** Path (surface widths) one paddle sweep must cover to count. */
    public static final float SWEEP_LENGTH = 0.9F;

    public static Reply handle(ServerPlayer player, TannersVatBlockEntity vat, StationLease lease, StationCommandPacket p) {
        ItemStack held = player.getMainHandItem();
        boolean stack = p.has(WorkAction.STACK);
        return switch (p.action()) {
            case VAT_POUR -> pour(player, vat, InteractionHand.MAIN_HAND) ? Reply.OK : Reply.refused(StationAckPacket.NO_MATERIAL);
            case VAT_DRAIN -> drain(player, vat, InteractionHand.MAIN_HAND) ? Reply.OK : Reply.refused(StationAckPacket.NO_MATERIAL);
            case VAT_ADD -> addBark(player, vat, held, stack ? held.getCount() : 1) ? Reply.OK : Reply.refused(StationAckPacket.NO_MATERIAL);
            case VAT_LOWER -> lower(player, vat, held, stack ? held.getCount() : 1) ? Reply.OK : Reply.refused(StationAckPacket.NO_MATERIAL);
            case VAT_LIFT -> lift(player, vat) ? Reply.OK : Reply.refused(StationAckPacket.REFUSED);
            case VAT_TAKE_BACK -> takeBack(player, vat, p.arg(0, -1)) ? Reply.OK : Reply.refused(StationAckPacket.REFUSED);
            case VAT_SWEEP -> sweep(player, vat, lease, p);
            default -> Reply.refused(StationAckPacket.REFUSED);
        };
    }

    public static boolean pour(ServerPlayer player, TannersVatBlockEntity vat, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(Items.WATER_BUCKET)) return false;
        if (!vat.fillWater()) {
            Feedback.info(player, "vat_already_full");
            return false;
        }
        if (!player.getAbilities().instabuild) player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.BUCKET)));
        Level level = player.level();
        level.playSound(null, vat.getBlockPos(), ModSounds.VAT_POUR.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        StationEffects.send((ServerLevel) level, vat.getBlockPos(), StationEffects.POUR, 1F);
        return true;
    }

    public static boolean drain(ServerPlayer player, TannersVatBlockEntity vat, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(Items.BUCKET) || !vat.hasWater()) return false;
        vat.drainWater();
        if (!player.getAbilities().instabuild) player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.WATER_BUCKET)));
        player.level().playSound(null, vat.getBlockPos(), ModSounds.VAT_FILL.get(), SoundSource.BLOCKS, 1.0F, 0.8F);
        int paused = vat.pausedCount();
        if (paused > 0) Feedback.warn(player, Feedback.message("vat_drained_paused", paused));
        else Feedback.info(player, "vat_drained");
        return true;
    }

    public static boolean addBark(ServerPlayer player, TannersVatBlockEntity vat, ItemStack held, int count) {
        if (!held.is(ModTags.TANNIN_SOURCES) || count <= 0) return false;
        int taken = vat.addTannin(Math.min(count, held.getCount()));
        if (taken == 0) {
            Feedback.info(player, "vat_tannin_full", vat.maxTannin());
            return false;
        }
        if (!player.getAbilities().instabuild) held.shrink(taken);
        player.level().playSound(null, vat.getBlockPos(), ModSounds.VAT_BARK.get(), SoundSource.BLOCKS, 0.8F, 0.9F);
        StationEffects.send(player.serverLevel(), vat.getBlockPos(), StationEffects.BARK, taken);
        return true;
    }

    public static boolean lower(ServerPlayer player, TannersVatBlockEntity vat, ItemStack held, int count) {
        if (!held.is(ModItems.SCRAPED_HIDE.get())) {
            if (HideItems.isMountable(player.level(), held)) Feedback.info(player, "flesh_first");
            return false;
        }
        int offered = Math.min(count, held.getCount());
        int added = 0;
        while (added < offered && vat.insertHide(ProcessedHideItem.data(held))) added++;
        if (added == 0) {
            Feedback.fail(player, "vat_full");
            return false;
        }
        if (!player.getAbilities().instabuild) held.shrink(added);
        player.level().playSound(null, vat.getBlockPos(), ModSounds.VAT_HIDE.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
        StationEffects.send(player.serverLevel(), vat.getBlockPos(), StationEffects.SPLASH, Math.max(0.25F, added / 4F));
        if (added < offered) Feedback.info(player, "vat_full");
        return true;
    }

    /** Lifts every tanned hide out at once (one batch). */
    public static boolean lift(ServerPlayer player, TannersVatBlockEntity vat) {
        if (vat.finishedCount() == 0) return false;
        List<ItemStack> finished = vat.takeFinished();
        for (ItemStack s : finished) {
            player.getInventory().add(s);
            if (!s.isEmpty()) player.drop(s, false);
        }
        player.level().playSound(null, vat.getBlockPos(), ModSounds.VAT_LIFT.get(), SoundSource.BLOCKS, 0.9F, 0.8F);
        StationEffects.send(player.serverLevel(), vat.getBlockPos(), StationEffects.LIFT, finished.size());
        ModAdvancements.award(player, ModAdvancements.TANNED);
        return true;
    }

    public static boolean takeBack(ServerPlayer player, TannersVatBlockEntity vat, int index) {
        ItemStack back = vat.takeBack(index);
        if (back.isEmpty()) return false;
        player.getInventory().add(back);
        if (!back.isEmpty()) player.drop(back, false);
        player.level().playSound(null, vat.getBlockPos(), ModSounds.VAT_LIFT.get(), SoundSource.BLOCKS, 0.7F, 1.0F);
        return true;
    }

    /**
     * A paddle sweep: the points (liquor surface, 0..1 each way) build up one sweep between press and release; a
     * sweep long enough credits each steeping hide once ({@link TannersVatBlockEntity#tend}).
     */
    private static Reply sweep(ServerPlayer player, TannersVatBlockEntity vat, StationLease lease, StationCommandPacket p) {
        if (!vat.hasWater()) return Reply.refused(StationAckPacket.WRONG_STAGE);
        if (p.has(WorkAction.BEGIN)) {
            lease.sweep = 0F;
            lease.lastX = Float.NaN;
        }
        long now = net.minecraft.Util.getMillis();
        float budget = lease.budget(now, 2.5F, 1.5F);
        for (int i = 0; i < p.xs().length; i++) {
            float x = StationCommandPacket.unit(p.xs()[i]);
            float y = StationCommandPacket.unit(p.ys()[i]);
            if (!Float.isNaN(lease.lastX)) {
                float d = (float) Math.sqrt((x - lease.lastX) * (x - lease.lastX) + (y - lease.lastY) * (y - lease.lastY));
                if (d > budget) break;
                budget -= d;
                lease.sweep += d;
            }
            lease.lastX = x;
            lease.lastY = y;
        }
        lease.spend(lease.budget(now, 2.5F, 1.5F) - budget);
        WorkNet.toWatchers(player.serverLevel(), vat.getBlockPos(), player, new StationStrokePacket(vat.getBlockPos(), StationStrokePacket.SWEEP,
                player.getId(), p.has(WorkAction.BEGIN), p.has(WorkAction.END), p.xs(), p.ys()));
        if (p.has(WorkAction.END)) {
            float swept = lease.sweep;
            lease.sweep = 0F;
            lease.lastX = Float.NaN;
            if (swept >= SWEEP_LENGTH && !p.has(WorkAction.CANCEL)) {
                player.level().playSound(null, vat.getBlockPos(), ModSounds.VAT_SWEEP.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
                StationEffects.send(player.serverLevel(), vat.getBlockPos(), StationEffects.SWEEP, 1F);
                int credited = vat.tend();
                if (credited > 0) Feedback.info(player, "vat_tended", credited);
                else Feedback.info(player, "vat_nothing_to_tend");
            }
        }
        return Reply.OK;
    }

    /**
     * Sneak-use and automation-style use without the focused view: water, drain, bark, hides, then collecting. The
     * same transactions as the focused actions; an empty hand lifts the tanned hides out, or, sneaking, the last
     * unfinished one back.
     */
    public static void quick(ServerPlayer player, TannersVatBlockEntity vat, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.WATER_BUCKET)) {
            if (pour(player, vat, hand)) status(player, vat);
            return;
        }
        if (held.is(Items.BUCKET) && vat.hasWater()) {
            drain(player, vat, hand);
            return;
        }
        if (held.is(ModTags.TANNIN_SOURCES)) {
            if (addBark(player, vat, held, held.getCount())) status(player, vat);
            return;
        }
        if (held.is(ModItems.SCRAPED_HIDE.get())) {
            if (lower(player, vat, held, held.getCount())) status(player, vat);
            return;
        }
        if (HideItems.isMountable(player.level(), held)) {
            Feedback.info(player, "flesh_first");
            return;
        }
        if (held.isEmpty()) {
            if (lift(player, vat)) return;
            if (player.isSecondaryUseActive()) {
                ItemStack back = vat.takeBackUnfinished();
                if (!back.isEmpty()) {
                    player.getInventory().add(back);
                    if (!back.isEmpty()) player.drop(back, false);
                    return;
                }
            }
        }
        status(player, vat);
    }

    /** The full state and, as its own highlighted notice, what anything waits for. */
    public static void status(ServerPlayer player, TannersVatBlockEntity vat) {
        Feedback.info(player, TannersVatBlock.statusLine(vat));
        var reason = TannersVatBlock.waitReason(vat);
        if (reason != null) Feedback.warn(player, reason);
    }

    public static BlockPos pos(TannersVatBlockEntity vat) {
        return vat.getBlockPos();
    }

    private VatStationLogic() {}
}
