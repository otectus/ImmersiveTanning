package com.otectus.immersivetanning.item;

import com.otectus.immersivetanning.advancement.ModAdvancements;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModSounds;
import com.otectus.immersivetanning.registry.ModTags;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The tanner's one blade: it strips bark from logs (the tannin for the vat) and fleshes hides on the Tanner's Rack.
 * Stripping follows the axe's own strip mapping, so any log an axe can strip, including modded ones, yields bark.
 *
 * <p>Forge only hands out a stripped state to a stack that can perform {@link ToolActions#AXE_STRIP}, so the knife
 * claims that one action and nothing else (no scraping, waxing or other axe work). The client only simulates the strip
 * for its prediction; the server alone changes the block and then pays the bark, the wear and the advancements, and
 * pays nothing when the strip is refused or the block does not change.</p>
 */
public class TannersKnifeItem extends Item {

    public TannersKnifeItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction action) {
        return action == ToolActions.AXE_STRIP;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModTags.BARK_SOURCES)) return InteractionResult.PASS;
        BlockState stripped = state.getToolModifiedState(context, ToolActions.AXE_STRIP, level.isClientSide);
        if (stripped == null || stripped == state) return InteractionResult.PASS;
        Player player = context.getPlayer();
        if (level.isClientSide) {
            level.playSound(player, pos, ModSounds.KNIFE_STRIP.get(), SoundSource.BLOCKS, 1.0F, 1.1F);
            return InteractionResult.SUCCESS;
        }

        if (!level.setBlock(pos, stripped, Block.UPDATE_ALL_IMMEDIATE)) return InteractionResult.PASS;
        level.playSound(player, pos, ModSounds.KNIFE_STRIP.get(), SoundSource.BLOCKS, 1.0F, 1.1F);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, stripped));
        ItemStack knife = context.getItemInHand();
        ItemStack bark = new ItemStack(ModItems.BARK.get(), ServerConfig.get(ServerConfig.BARK_PER_LOG));
        if (player != null) {
            if (player instanceof ServerPlayer sp) CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(sp, pos, knife);
            ItemHandlerHelper.giveItemToPlayer(player, bark);
            knife.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(context.getHand()));
            if (player instanceof ServerPlayer sp) ModAdvancements.award(sp, ModAdvancements.BARK);
        } else {
            Block.popResourceFromFace(level, pos, context.getClickedFace(), bark);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(net.minecraftforge.common.Tags.Items.INGOTS_IRON) || super.isValidRepairItem(stack, repair);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.immersive_tanning.tanners_knife.hint").withStyle(ChatFormatting.GRAY));
    }

    /** Watchers see the arm reach to the work while its holder works a station (client presentation only). */
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        net.minecraftforge.client.extensions.common.IClientItemExtensions extensions = net.minecraftforge.fml.DistExecutor.unsafeCallWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> com.otectus.immersivetanning.client.station.WorkArmPose.TOOL);
        if (extensions != null) consumer.accept(extensions);
    }
}
