package com.otectus.immersivetanning.work;

import com.otectus.immersivetanning.blockentity.StationBlockEntity;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.item.AwlItem;
import com.otectus.immersivetanning.item.TannersKnifeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/**
 * Who may work a station and with what. The camera may hang anywhere near the work; the rules are about the player's
 * body: alive, not a spectator, in the station's dimension, within reach of it, allowed to interact there, and (when a
 * lease is granted) able to see it. Tools are the real items: a Tanner's Knife cuts and scrapes, an Awl stitches,
 * found in the hands first, then the hotbar, then the rest of the inventory, and remembered by slot for the job.
 */
public final class WorkRules {

    public static boolean canWork(ServerPlayer player, StationBlockEntity station) {
        if (station.getLevel() == null || station.isRemoved()) return false;
        if (!player.isAlive() || player.isSpectator() || player.level() != station.getLevel()) return false;
        BlockPos pos = station.getBlockPos();
        if (!inReach(player, pos)) return false;
        return station.getLevel().mayInteract(player, pos);
    }

    public static boolean inReach(Player player, BlockPos pos) {
        double max = ServerConfig.get(ServerConfig.MAX_STATION_DISTANCE);
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= max * max;
    }

    /** Whether nothing solid stands between the player's eyes and the station (a lease is never granted through a wall). */
    public static boolean canSee(ServerPlayer player, BlockPos pos) {
        Vec3 eye = player.getEyePosition();
        Vec3 target = Vec3.atCenterOf(pos);
        BlockHitResult hit = player.level().clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    // ------------------------------------------------------------------ tools

    public static boolean isKnife(ItemStack stack) {
        return stack.getItem() instanceof TannersKnifeItem;
    }

    public static boolean isAwl(ItemStack stack) {
        return stack.getItem() instanceof AwlItem;
    }

    /**
     * The inventory slot of a tool: the reserved slot while it still holds that kind of tool, else the main hand, the
     * offhand, the hotbar and the rest of the inventory. -1 when the player has none.
     */
    public static int findTool(Player player, Predicate<ItemStack> kind, int reserved) {
        Inventory inv = player.getInventory();
        if (reserved >= 0 && reserved < inv.getContainerSize() && kind.test(inv.getItem(reserved))) return reserved;
        if (kind.test(inv.getSelected())) return inv.selected;
        if (kind.test(player.getOffhandItem())) return Inventory.SLOT_OFFHAND;
        for (int slot = 0; slot < inv.items.size(); slot++) {
            if (kind.test(inv.getItem(slot))) return slot;
        }
        return -1;
    }

    /** Wears the tool in its slot by one, once per job; a creative player's tools never wear. */
    public static void wear(ServerPlayer player, int slot, Predicate<ItemStack> kind) {
        if (player.getAbilities().instabuild) return;
        int found = findTool(player, kind, slot);
        if (found < 0) return;
        ItemStack tool = player.getInventory().getItem(found);
        tool.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(found == Inventory.SLOT_OFFHAND
                ? net.minecraft.world.entity.EquipmentSlot.OFFHAND : net.minecraft.world.entity.EquipmentSlot.MAINHAND));
    }

    private WorkRules() {}
}
