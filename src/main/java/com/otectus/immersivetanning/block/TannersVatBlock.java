package com.otectus.immersivetanning.block;

import com.otectus.immersivetanning.blockentity.TannersVatBlockEntity;
import com.otectus.immersivetanning.config.ClientConfig;
import com.otectus.immersivetanning.registry.ModBlockEntities;
import com.otectus.immersivetanning.registry.ModSounds;
import com.otectus.immersivetanning.util.Feedback;
import net.minecraft.core.BlockPos;
import com.otectus.immersivetanning.station.StationInteraction;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Tanner's Vat: a hooped wooden tub. Pour in water, scatter bark into it, lower scraped hides and come back for
 * them once they are tanned. It has no screen: its state is in the liquor, each hide shows at the rim and says exactly
 * what it waits for, and a paddle on the rim may stir it once ({@link StationInteraction}).
 */
public class TannersVatBlock extends BaseEntityBlock {
    /** Since 1.3: which side is the vat's front (its hook, and the side the view leans in from). Older vats face north. */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Shapes.join(
            Block.box(1, 0, 1, 15, 13, 15),
            Block.box(2.5, 2, 2.5, 13.5, 13, 13.5), BooleanOp.ONLY_FIRST);

    public TannersVatBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
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
        return StationInteraction.use(level, pos, player, hand);
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
