package com.otectus.immersivetanning.block;

import com.otectus.immersivetanning.advancement.ModAdvancements;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.config.ClientConfig;
import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.hide.HideItems;
import com.otectus.immersivetanning.item.TannersKnifeItem;
import com.otectus.immersivetanning.station.StationInteraction;
import com.otectus.immersivetanning.registry.ModBlockEntities;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModSounds;
import com.otectus.immersivetanning.util.Feedback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The Tanner's Rack: a timber frame with a fleshing beam in front. A raw hide drapes over the beam to be scraped with
 * the Tanner's Knife; a wet tanned hide is laced into the frame, its finish chosen by working the tension lever, and
 * dries by itself. Using it leans in to the work ({@link StationInteraction}); sneak-use is the quick route.
 */
public class TannersRackBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private static final StationShape SHAPE = new StationShape(Shapes.or(
            Block.box(0, 0, 5, 4, 1, 11),
            Block.box(12, 0, 5, 16, 1, 11),
            Block.box(1, 0, 7, 3, 16, 9),
            Block.box(13, 0, 7, 15, 16, 9),
            Block.box(3, 14, 7, 13, 16, 9),
            Block.box(3, 2, 7, 13, 4, 9),
            Block.box(1, 4, 3, 3, 6, 7),
            Block.box(13, 4, 3, 15, 6, 7),
            Block.box(2, 4, 1, 14, 6, 3)));

    public TannersRackBlock(Properties properties) {
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
        return SHAPE.facing(state.getValue(FACING));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TannersRackBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.TANNERS_RACK.get(), TannersRackBlockEntity::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof TannersRackBlockEntity rack) {
            rack.releaseAll();
            rack.dropContents();
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return StationInteraction.use(level, pos, player, hand);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof TannersRackBlockEntity rack)) return;
        if (rack.mode() != TannersRackBlockEntity.Mode.DRYING && rack.mode() != TannersRackBlockEntity.Mode.FRAMING) return;
        float progress = rack.dryProgress(level.getGameTime());
        if (progress >= 0.6F || random.nextFloat() > 0.35F * (1F - progress) * ClientConfig.scaleParticles(10) / 10F) return;
        Direction facing = state.getValue(FACING);
        double along = 0.3 + random.nextDouble() * 0.4;
        double x = pos.getX() + 0.5 + (facing.getAxis() == Direction.Axis.Z ? along - 0.5 : 0);
        double z = pos.getZ() + 0.5 + (facing.getAxis() == Direction.Axis.X ? along - 0.5 : 0);
        level.addParticle(ParticleTypes.DRIPPING_WATER, x, pos.getY() + 0.3, z, 0, 0, 0);
    }
}
