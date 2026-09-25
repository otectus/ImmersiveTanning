package com.otectus.immersivetanning.effect;

import com.google.common.collect.Multimap;
import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.api.TraitEffect;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.leather.LeatherworkData;
import com.otectus.immersivetanning.trait.TraitEffects;
import com.otectus.immersivetanning.trait.TraitRef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What finished leatherwork does when worn. Every effect is per piece, so each piece contributes and a full set only
 * stacks the contributions (no set threshold). Workmanship scales armor and the finish's own effect; Integrity scales
 * durability (see {@link #adjustDamage}).
 * <ul>
 *     <li>Supple - movement speed, a smaller detection range, and boots that keep footsteps from sculk.</li>
 *     <li>Harness - much more durability, and the best field repair (see FieldRepair).</li>
 *     <li>Hardened - extra armor and some toughness.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ImmersiveTanning.MOD_ID)
public final class LeatherworkEffects {
    private static final Map<EquipmentSlot, UUID> TOUGHNESS_IDS = ids("armor_toughness");
    private static final Map<EquipmentSlot, UUID> SPEED_IDS = ids("movement_speed");
    private static final String MODIFIER_NAME = "Leatherwork";
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static Map<EquipmentSlot, UUID> ids(String attribute) {
        Map<EquipmentSlot, UUID> map = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            map.put(slot, UUID.nameUUIDFromBytes(("immersive_tanning:" + attribute + ":" + slot.getName()).getBytes(StandardCharsets.UTF_8)));
        }
        return map;
    }

    /** Workmanship as a multiplier of armor and finish effects. */
    public static double efficacy(LeatherworkData data) {
        return lerp(ServerConfig.get(ServerConfig.MINIMUM_EFFICACY_MULTIPLIER), ServerConfig.get(ServerConfig.MAXIMUM_EFFICACY_MULTIPLIER), data.workmanship());
    }

    /** Integrity, finish and trait as one durability multiplier. */
    public static double durability(LeatherworkData data) {
        double multiplier = lerp(ServerConfig.get(ServerConfig.MINIMUM_DURABILITY_MULTIPLIER), ServerConfig.get(ServerConfig.MAXIMUM_DURABILITY_MULTIPLIER), data.integrity());
        if (data.finish() == Finish.HARNESS) multiplier *= ServerConfig.get(ServerConfig.HARNESS_DURABILITY_MULTIPLIER);
        TraitEffect effect = traitEffect(data.trait());
        if (effect != null) multiplier *= effect.durabilityMultiplier(data.trait());
        return multiplier;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onAttributeModifiers(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        Optional<LeatherworkData> found = LeatherworkData.get(stack);
        if (found.isEmpty()) return;
        EquipmentSlot slot = event.getSlotType();
        if (!slot.isArmor() || LivingEntity.getEquipmentSlotForItem(stack) != slot) return;
        LeatherworkData data = found.get();
        try {
            double efficacy = efficacy(data);
            Multimap<Attribute, AttributeModifier> original = event.getOriginalModifiers();
            double armorMultiplier = efficacy;
            if (data.finish() == Finish.HARDENED) armorMultiplier += ServerConfig.get(ServerConfig.HARDENED_ARMOR_FACTOR) * efficacy;
            if (additions(original, Attributes.ARMOR) > 0 && Math.abs(armorMultiplier - 1.0) > 1.0E-6) {
                // Rescale the piece's own armor modifiers in place (same ids), so the tooltip shows one armor line.
                for (AttributeModifier m : java.util.List.copyOf(event.getModifiers().get(Attributes.ARMOR))) {
                    if (m.getOperation() != AttributeModifier.Operation.ADDITION || !original.containsEntry(Attributes.ARMOR, m)) continue;
                    event.removeModifier(Attributes.ARMOR, m);
                    event.addModifier(Attributes.ARMOR, new AttributeModifier(m.getId(), m.getName(), m.getAmount() * armorMultiplier, m.getOperation()));
                }
            }
            if (data.finish() == Finish.HARDENED) {
                double toughness = ServerConfig.get(ServerConfig.HARDENED_TOUGHNESS_PER_PIECE) * efficacy;
                if (toughness > 0) {
                    event.addModifier(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(TOUGHNESS_IDS.get(slot), MODIFIER_NAME, toughness, AttributeModifier.Operation.ADDITION));
                }
            }
            if (data.finish() == Finish.SUPPLE) {
                double speed = ServerConfig.get(ServerConfig.SUPPLE_SPEED_PER_PIECE) * efficacy;
                if (speed > 0) {
                    event.addModifier(Attributes.MOVEMENT_SPEED, new AttributeModifier(SPEED_IDS.get(slot), MODIFIER_NAME, speed, AttributeModifier.Operation.MULTIPLY_BASE));
                }
            }
            TraitEffect effect = traitEffect(data.trait());
            if (effect != null) effect.addModifiers(event, data.trait());
        } catch (RuntimeException e) {
            ImmersiveTanning.LOGGER.debug("Could not apply leatherwork effects to {}: {}", stack, e.toString());
        }
    }

    /** Supple pieces shrink the range at which mobs notice the wearer. */
    @SubscribeEvent
    public static void onVisibility(LivingEvent.LivingVisibilityEvent event) {
        double per = ServerConfig.get(ServerConfig.SUPPLE_VISIBILITY_PER_PIECE);
        if (per <= 0) return;
        double multiplier = 1.0;
        for (EquipmentSlot slot : ARMOR) {
            Optional<LeatherworkData> data = LeatherworkData.get(event.getEntity().getItemBySlot(slot));
            if (data.isPresent() && data.get().finish() == Finish.SUPPLE) multiplier *= 1.0 - Mth.clamp(per * efficacy(data.get()), 0.0, 0.5);
        }
        if (multiplier < 1.0) event.modifyVisibility(multiplier);
    }

    /** Supple boots keep footsteps and landings from reaching sculk sensors and wardens, like wool. */
    @SubscribeEvent
    public static void onGameEvent(VanillaGameEvent event) {
        GameEvent type = event.getVanillaEvent();
        if (type != GameEvent.STEP && type != GameEvent.HIT_GROUND && type != GameEvent.SWIM) return;
        Entity cause = event.getCause();
        if (!(cause instanceof LivingEntity living) || !ServerConfig.get(ServerConfig.SUPPLE_BOOTS_MUFFLE_STEPS)) return;
        Optional<LeatherworkData> boots = LeatherworkData.get(living.getItemBySlot(EquipmentSlot.FEET));
        if (boots.isPresent() && boots.get().finish() == Finish.SUPPLE) event.setCanceled(true);
    }

    /** Innate hide traits reduce the damage types they resist, per piece. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        float multiplier = 1F;
        for (EquipmentSlot slot : ARMOR) {
            Optional<LeatherworkData> data = LeatherworkData.get(event.getEntity().getItemBySlot(slot));
            if (data.isEmpty() || data.get().trait() == null) continue;
            TraitEffect effect = traitEffect(data.get().trait());
            if (effect != null) multiplier *= effect.damageMultiplier(event.getSource(), data.get().trait());
        }
        if (multiplier < 1F) event.setAmount(event.getAmount() * Math.max(0F, multiplier));
    }

    /**
     * Durability conversion: rather than changing an item's maximum durability (which arbitrary items may read
     * directly), each incoming damage point is scaled by 1 / multiplier, keeping the fractional remainder on the stack
     * so the conversion is deterministic. Unbreaking and Mending run unchanged on top. Called from ItemStackMixin at
     * the head of {@code ItemStack#hurt}.
     */
    public static int adjustDamage(ItemStack stack, int amount) {
        if (amount <= 0) return amount;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(LeatherworkData.ROOT)) return amount;
        Optional<LeatherworkData> data = LeatherworkData.get(stack);
        if (data.isEmpty()) return amount;
        double multiplier = Math.max(0.01, durability(data.get()));
        if (Math.abs(multiplier - 1.0) < 1.0E-6) return amount;
        CompoundTag root = tag.getCompound(LeatherworkData.ROOT);
        double carry = root.getDouble(LeatherworkData.CARRY) + amount / multiplier;
        int applied = (int) Math.floor(carry);
        root.putDouble(LeatherworkData.CARRY, carry - applied);
        return applied;
    }

    private static TraitEffect traitEffect(TraitRef trait) {
        return trait == null ? null : TraitEffects.get(trait.effect());
    }

    private static double additions(Multimap<Attribute, AttributeModifier> modifiers, Attribute attribute) {
        double sum = 0;
        for (AttributeModifier m : modifiers.get(attribute)) {
            if (m.getOperation() == AttributeModifier.Operation.ADDITION) sum += m.getAmount();
        }
        return sum;
    }

    public static double lerp(double min, double max, int score) {
        return min + (max - min) * Mth.clamp(score, 0, 100) / 100.0;
    }

    private LeatherworkEffects() {}
}
