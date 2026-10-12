package com.otectus.immersivetanning.block;

import com.otectus.immersivetanning.advancement.ModAdvancements;
import com.otectus.immersivetanning.blockentity.TannersVatBlockEntity;
import com.otectus.immersivetanning.config.ClientConfig;
import com.otectus.immersivetanning.item.ProcessedHideItem;
import com.otectus.immersivetanning.hide.HideItems;
import com.otectus.immersivetanning.registry.ModBlockEntities;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModSounds;
import com.otectus.immersivetanning.registry.ModTags;
import com.otectus.immersivetanning.util.Feedback;
import com.otectus.immersivetanning.util.StationEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Tanner's Vat: a hooped wooden tub. Fill it with a water bucket, charge it with bark, sink a stack of scraped
 * hides into it, and come back for them once they are tanned. It has no screen; its state is visible in the liquor,
 * and every interaction (or an empty hand) reports what is tanning, what waits and exactly what it waits for.
 */
public class TannersVatBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Shapes.join(
            Block.box(1, 0, 1, 15, 13, 15),
            Block.box(2.5, 2, 2.5, 13.5, 13, 13.5), BooleanOp.ONLY_FIRST);

    public TannersVatBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TannersVatBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.TANNERS_VAT.get(), TannersVatBlockEntity::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof TannersVatBlockEntity vat) {
            vat.dropContents();
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** Comparators read how many hides are ready, scaled to the vat's capacity. */
    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof TannersVatBlockEntity vat) || vat.finishedCount() == 0) return 0;
        return Math.max(1, Math.round(15F * vat.finishedCount() / Math.max(1, vat.capacity())));
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (player.isSecondaryUseActive() && held.getItem() instanceof BlockItem) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof TannersVatBlockEntity vat) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.PASS;
        }
        interact(sp, vat, held, hand);
        return InteractionResult.CONSUME;
    }

    private void interact(ServerPlayer player, TannersVatBlockEntity vat, ItemStack held, InteractionHand hand) {
        Level level = player.level();
        BlockPos pos = vat.getBlockPos();
        if (held.is(Items.WATER_BUCKET)) {
            if (vat.fillWater()) {
                if (!player.getAbilities().instabuild) player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.BUCKET)));
                level.playSound(null, pos, ModSounds.VAT_FILL.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                status(player, vat);
            } else {
                Feedback.info(player, "vat_already_full");
            }
            return;
        }
        if (held.is(Items.BUCKET) && vat.hasWater()) {
            vat.drainWater();
            if (!player.getAbilities().instabuild) player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.WATER_BUCKET)));
            level.playSound(null, pos, ModSounds.VAT_FILL.get(), SoundSource.BLOCKS, 1.0F, 0.8F);
            int paused = vat.pausedCount();
            if (paused > 0) Feedback.warn(player, Feedback.message("vat_drained_paused", paused));
            else Feedback.info(player, "vat_drained");
            return;
        }
        if (held.is(ModTags.TANNIN_SOURCES)) {
            int taken = vat.addTannin(held.getCount());
            if (taken == 0) {
                Feedback.info(player, "vat_tannin_full", vat.maxTannin());
                return;
            }
            if (!player.getAbilities().instabuild) held.shrink(taken);
            level.playSound(null, pos, ModSounds.VAT_BARK.get(), SoundSource.BLOCKS, 0.8F, 0.9F);
            status(player, vat);
            return;
        }
        if (held.is(ModItems.SCRAPED_HIDE.get())) {
            int offered = held.getCount();
            int added = 0;
            while (added < offered && vat.insertHide(ProcessedHideItem.data(held))) added++;
            if (added == 0) {
                Feedback.fail(player, "vat_full");
                return;
            }
            if (!player.getAbilities().instabuild) held.shrink(added);
            level.playSound(null, pos, ModSounds.VAT_HIDE.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
            StationEffects.send((ServerLevel) level, pos, StationEffects.SPLASH, added / 4F);
            status(player, vat);
            if (added < offered) Feedback.info(player, "vat_full");
            return;
        }
        if (HideItems.isMountable(level, held)) {
            Feedback.info(player, "flesh_first");
            return;
        }
        if (held.isEmpty()) {
            List<ItemStack> finished = vat.takeFinished();
            if (!finished.isEmpty()) {
                finished.forEach(s -> ItemHandlerHelper.giveItemToPlayer(player, s));
                level.playSound(null, pos, ModSounds.VAT_HIDE.get(), SoundSource.BLOCKS, 0.9F, 0.8F);
                StationEffects.send((ServerLevel) level, pos, StationEffects.SPLASH, 0.5F);
                ModAdvancements.award(player, ModAdvancements.TANNED);
                return;
            }
            if (player.isSecondaryUseActive()) {
                ItemStack back = vat.takeBackUnfinished();
                if (!back.isEmpty()) {
                    ItemHandlerHelper.giveItemToPlayer(player, back);
                    return;
                }
            }
        }
        status(player, vat);
    }


    /** The full state (water, tannin, hides by stage), and the reason anything waits, as its own highlighted notice. */
    private static void status(ServerPlayer player, TannersVatBlockEntity vat) {
        Feedback.info(player, statusLine(vat));
        Component reason = waitReason(vat);
        if (reason != null) Feedback.warn(player, reason);
    }

    public static Component statusLine(TannersVatBlockEntity vat) {
        Component water = Feedback.message(vat.hasWater() ? "vat_water_yes" : "vat_water_no");
        Component head = Feedback.message("vat_status", water, vat.tannin(), vat.maxTannin(), vat.slots().size(), vat.capacity());
        List<Component> parts = new ArrayList<>();
        int steeping = vat.steepingCount();
        if (steeping > 0) parts.add(Feedback.message("vat_status_tanning", steeping, StringUtil.formatTickDuration((int) vat.nextReadyTicks())));
        if (vat.pausedCount() > 0) parts.add(Feedback.message("vat_status_paused", vat.pausedCount()));
        if (vat.waitingCount() > 0) parts.add(Feedback.message("vat_status_waiting", vat.waitingCount()));
        if (vat.finishedCount() > 0) parts.add(Feedback.message("vat_status_ready", vat.finishedCount()));
        if (parts.isEmpty()) return head;
        return Feedback.message("vat_status_line", head, ComponentUtils.formatList(parts, Component.literal(", ")));
    }

    /** Why hides are not tanning, with the next step, or null when nothing waits. */
    @Nullable
    public static Component waitReason(TannersVatBlockEntity vat) {
        int waiting = vat.waitingCount();
        int paused = vat.pausedCount();
        if (!vat.hasWater()) {
            if (paused > 0) return Feedback.message("vat_needs_water_paused");
            return waiting > 0 ? Feedback.message("vat_needs_water") : null;
        }
        if (waiting > 0) {
            int missing = vat.missingTannin();
            return missing > 0 ? Feedback.message("vat_needs_tannin", missing) : null;
        }
        return null;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof TannersVatBlockEntity vat) || !vat.hasWater()) return;
        long now = level.getGameTime();
        boolean working = false;
        for (TannersVatBlockEntity.Slot s : vat.slots()) {
            if (s.started() && s.progress(now) < 1F) working = true;
        }
        if (!working) return;
        if (random.nextFloat() < 0.5F * ClientConfig.scaleParticles(10) / 10F) {
            level.addParticle(ParticleTypes.BUBBLE_POP, pos.getX() + 0.25 + random.nextDouble() * 0.5, pos.getY() + 0.76,
                    pos.getZ() + 0.25 + random.nextDouble() * 0.5, 0, 0.01, 0);
        }
        if (random.nextInt(120) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, ModSounds.VAT_BUBBLE.get(), SoundSource.BLOCKS,
                    0.3F, 0.8F + random.nextFloat() * 0.3F, false);
        }
    }
}
