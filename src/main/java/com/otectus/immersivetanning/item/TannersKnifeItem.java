package com.otectus.immersivetanning.item;

import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModSounds;
import com.otectus.immersivetanning.registry.ModTags;
import net.minecraft.ChatFormatting;
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
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The tanner's one blade: it strips bark from logs (the tannin for the vat) and fleshes hides on the Tanner's Rack.
 * Stripping follows the axe's own strip mapping, so any log an axe can strip, including modded ones, yields bark.
 */
public class TannersKnifeItem extends Item {

    public TannersKnifeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModTags.BARK_SOURCES)) return InteractionResult.PASS;
        BlockState stripped = state.getToolModifiedState(context, ToolActions.AXE_STRIP, false);
        if (stripped == null || stripped == state) return InteractionResult.PASS;
        Player player = context.getPlayer();
        level.playSound(player, pos, ModSounds.KNIFE_STRIP.get(), SoundSource.BLOCKS, 1.0F, 1.1F);
        if (level.isClientSide) return InteractionResult.SUCCESS;

        level.setBlock(pos, stripped, Block.UPDATE_ALL_IMMEDIATE);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, stripped));
        ItemStack bark = new ItemStack(ModItems.BARK.get(), ServerConfig.get(ServerConfig.BARK_PER_LOG));
        if (player != null) {
            ItemHandlerHelper.giveItemToPlayer(player, bark);
            context.getItemInHand().hurtAndBreak(1, player, p -> p.broadcastBreakEvent(context.getHand()));
            if (player instanceof ServerPlayer sp) com.otectus.immersivetanning.advancement.ModAdvancements.award(sp, com.otectus.immersivetanning.advancement.ModAdvancements.BARK);
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
}
