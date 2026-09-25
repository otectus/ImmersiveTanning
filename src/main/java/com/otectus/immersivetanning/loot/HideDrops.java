package com.otectus.immersivetanning.loot;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.hide.HideSourceLoader;
import com.otectus.immersivetanning.leather.BindingLeather;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The hide an entity drops, shared by the loot modifier (loot tables) and the drops fallback below (mods such as
 * Lycanites that hand out drops without a loot table and fire only {@link LivingDropsEvent}).
 *
 * <p>A carcass kill - a {@code #immersive_tanning:carcass_tools} weapon, or one enchanted with a configured carcass
 * enchantment, on a {@code #immersive_tanning:carcass_animals} adult - drops no hide: the mod that butchers the carcass
 * supplies the skin, and the rack accepts that skin through a hide mounting recipe.</p>
 */
@Mod.EventBusSubscriber(modid = ImmersiveTanning.MOD_ID)
public final class HideDrops {

    /** Replaces the leather in {@code loot} with the source's hides, or only removes it on a carcass kill. */
    public static void apply(List<ItemStack> loot, LivingEntity victim, HideSourceLoader.HideSource source, @Nullable Entity killer,
                             int looting, RandomSource random) {
        boolean carcass = butcheredAsCarcass(victim, killer);
        if (carcass || ServerConfig.get(ServerConfig.REPLACE_LEATHER_DROPS)) loot.removeIf(stack -> stack.is(ModTags.REPLACED_BY_HIDES));
        if (!carcass) loot.addAll(hides(source, looting, random));
    }

    /** The hide stacks one death yields. */
    public static List<ItemStack> hides(HideSourceLoader.HideSource source, int looting, RandomSource random) {
        int count = source.count();
        if (ServerConfig.get(ServerConfig.LOOTING_ADDS_HIDES) && looting > 0) count += random.nextInt(looting + 1);
        ItemStack hide = ModItems.rawHide(source.size()).create(source.traitRef());
        List<ItemStack> out = new ArrayList<>();
        while (count > 0) {
            int n = Math.min(count, hide.getMaxStackSize());
            out.add(hide.copyWithCount(n));
            count -= n;
        }
        return out;
    }

    public static boolean butcheredAsCarcass(LivingEntity victim, @Nullable Entity killer) {
        if (victim.isBaby() || !victim.getType().is(ModTags.CARCASS_ANIMALS) || !(killer instanceof LivingEntity living)) return false;
        ItemStack weapon = living.getMainHandItem();
        if (weapon.isEmpty()) return false;
        if (weapon.is(ModTags.CARCASS_TOOLS)) return true;
        for (String id : ServerConfig.list(ServerConfig.CARCASS_ENCHANTMENTS)) {
            ResourceLocation key = ResourceLocation.tryParse(id);
            Enchantment enchantment = key == null ? null : ForgeRegistries.ENCHANTMENTS.getValue(key);
            if (enchantment != null && weapon.getEnchantmentLevel(enchantment) > 0) return true;
        }
        return false;
    }

    /**
     * Drops that never went through a loot table: leather an entity with a hide source drops becomes its hide (or, when
     * its loot table already gave the hide, simply goes: Livestock Overhaul drops leather from code on top of its loot
     * table), and vanilla leather in any mob's drops becomes Binding Leather. Runs late so other mods' drops are in.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player || victim.level().isClientSide) return;
        HideSourceLoader.HideSource source = HideSourceLoader.forEntity(victim.getType());
        if (source != null && contains(event, true)) {
            List<ItemStack> stacks = new ArrayList<>();
            event.getDrops().forEach(e -> stacks.add(e.getItem()));
            Entity killer = event.getSource().getEntity();
            if (!contains(event, false)) {
                apply(stacks, victim, source, killer, event.getLootingLevel(), victim.getRandom());
            } else if (ServerConfig.get(ServerConfig.REPLACE_LEATHER_DROPS) || butcheredAsCarcass(victim, killer)) {
                stacks.removeIf(stack -> stack.is(ModTags.REPLACED_BY_HIDES));
            }
            // Keep the entities of stacks that survived; spawn entities for the added hides.
            for (Iterator<ItemEntity> it = event.getDrops().iterator(); it.hasNext(); ) {
                ItemEntity drop = it.next();
                if (!stacks.removeIf(s -> s == drop.getItem())) it.remove();
            }
            for (ItemStack hide : stacks) {
                ItemEntity drop = new ItemEntity(victim.level(), victim.getX(), victim.getY() + 0.5, victim.getZ(), hide);
                drop.setDefaultPickUpDelay();
                event.getDrops().add(drop);
            }
        }
        if (ServerConfig.get(ServerConfig.REPLACE_LEATHER_LOOT)) {
            for (ItemEntity drop : event.getDrops()) {
                ItemStack replaced = BindingLeather.replace(drop.getItem());
                if (replaced != drop.getItem()) drop.setItem(replaced);
            }
        }
    }

    /** Whether the drops hold leather replaced by hides ({@code replaced}) or a raw hide already ({@code !replaced}). */
    private static boolean contains(LivingDropsEvent event, boolean replaced) {
        for (ItemEntity drop : event.getDrops()) {
            if (drop.getItem().is(replaced ? ModTags.REPLACED_BY_HIDES : ModTags.RAW_HIDES)) return true;
        }
        return false;
    }

    private HideDrops() {}
}
