package com.otectus.immersivetanning.effect;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.api.TraitEffect;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.leather.LeatherworkData;
import com.otectus.immersivetanning.leather.LeatherworkProfile;
import com.otectus.immersivetanning.trait.TraitEffects;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * What a piece's finish and quality are worth, by its {@link LeatherworkProfile}. Every effect, the durability
 * conversion, field repair and the tooltip read the same policy, so the client shows what the server applies.
 * <ul>
 *     <li>Ordinary leather: the {@code quality} and {@code finishes} config sections. Hardened adds a share of the
 *     piece's own armor.</li>
 *     <li>Scale-backed: the {@code scaleBacked} section. Narrower quality bands, a quarter of Supple's speed, half its
 *     stealth, a smaller Harness multiplier, Hardened as a flat bonus per piece, and no scrap repair by default.</li>
 *     <li>Neutral: no finish or quality effect at all, for an unrecognised profile record or an inverted
 *     {@code scaleBacked} range. Hide traits still act, as on every piece.</li>
 * </ul>
 *
 * @param hardenedArmorShare Hardened armor as a share of the piece's own armor (ordinary leather)
 * @param hardenedArmor      Hardened armor added per piece (scale-backed)
 */
public record LeatherworkPolicy(LeatherworkProfile profile, double minEfficacy, double maxEfficacy, double minDurability,
                                double maxDurability, double suppleSpeed, double suppleVisibility, boolean suppleMuffle,
                                double harnessDurability, double harnessRepair, double hardenedArmorShare, double hardenedArmor,
                                double hardenedToughness, boolean scrapRepair) {

    private static volatile String warnedConfig = "";

    public static LeatherworkPolicy of(ItemStack stack) {
        return switch (LeatherworkProfile.of(stack)) {
            case LEATHER -> leather();
            case SCALE_BACKED -> scaleBacked();
            case UNRECOGNISED -> neutral(LeatherworkProfile.UNRECOGNISED, false);
        };
    }

    public static LeatherworkPolicy leather() {
        return new LeatherworkPolicy(LeatherworkProfile.LEATHER,
                ServerConfig.get(ServerConfig.MINIMUM_EFFICACY_MULTIPLIER), ServerConfig.get(ServerConfig.MAXIMUM_EFFICACY_MULTIPLIER),
                ServerConfig.get(ServerConfig.MINIMUM_DURABILITY_MULTIPLIER), ServerConfig.get(ServerConfig.MAXIMUM_DURABILITY_MULTIPLIER),
                ServerConfig.get(ServerConfig.SUPPLE_SPEED_PER_PIECE), ServerConfig.get(ServerConfig.SUPPLE_VISIBILITY_PER_PIECE),
                ServerConfig.get(ServerConfig.SUPPLE_BOOTS_MUFFLE_STEPS), ServerConfig.get(ServerConfig.HARNESS_DURABILITY_MULTIPLIER),
                ServerConfig.get(ServerConfig.HARNESS_REPAIR_MULTIPLIER), ServerConfig.get(ServerConfig.HARDENED_ARMOR_FACTOR), 0.0,
                ServerConfig.get(ServerConfig.HARDENED_TOUGHNESS_PER_PIECE), true);
    }

    /** The scale-backed policy, or the neutral one (with a warning) when a configured range is inverted. */
    public static LeatherworkPolicy scaleBacked() {
        double minEfficacy = ServerConfig.get(ServerConfig.SCALE_MINIMUM_EFFICACY_MULTIPLIER);
        double maxEfficacy = ServerConfig.get(ServerConfig.SCALE_MAXIMUM_EFFICACY_MULTIPLIER);
        double minDurability = ServerConfig.get(ServerConfig.SCALE_MINIMUM_DURABILITY_MULTIPLIER);
        double maxDurability = ServerConfig.get(ServerConfig.SCALE_MAXIMUM_DURABILITY_MULTIPLIER);
        boolean scraps = ServerConfig.get(ServerConfig.SCALE_ALLOW_SCRAP_REPAIR);
        if (minEfficacy > maxEfficacy || minDurability > maxDurability) {
            String config = minEfficacy + "/" + maxEfficacy + "/" + minDurability + "/" + maxDurability;
            if (!config.equals(warnedConfig)) {
                warnedConfig = config;
                ImmersiveTanning.LOGGER.warn("scaleBacked multiplier ranges are inverted (efficacy {}..{}, durability {}..{}); scale-backed "
                        + "pieces get no finish or quality effects until a minimum is at most its maximum", minEfficacy, maxEfficacy, minDurability, maxDurability);
            }
            return neutral(LeatherworkProfile.SCALE_BACKED, scraps);
        }
        return new LeatherworkPolicy(LeatherworkProfile.SCALE_BACKED, minEfficacy, maxEfficacy, minDurability, maxDurability,
                ServerConfig.get(ServerConfig.SCALE_SUPPLE_SPEED_PER_PIECE), ServerConfig.get(ServerConfig.SCALE_SUPPLE_VISIBILITY_PER_PIECE),
                ServerConfig.get(ServerConfig.SUPPLE_BOOTS_MUFFLE_STEPS), ServerConfig.get(ServerConfig.SCALE_HARNESS_DURABILITY_MULTIPLIER),
                ServerConfig.get(ServerConfig.HARNESS_REPAIR_MULTIPLIER), 0.0, ServerConfig.get(ServerConfig.SCALE_HARDENED_ARMOR_PER_PIECE),
                ServerConfig.get(ServerConfig.SCALE_HARDENED_TOUGHNESS_PER_PIECE), scraps);
    }

    static LeatherworkPolicy neutral(LeatherworkProfile profile, boolean scrapRepair) {
        return new LeatherworkPolicy(profile, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0, false, 1.0, 1.0, 0.0, 0.0, 0.0, scrapRepair);
    }

    public boolean isScaleBacked() {
        return profile == LeatherworkProfile.SCALE_BACKED;
    }

    /** Workmanship as a multiplier of armor and finish effects. */
    public double efficacy(LeatherworkData data) {
        return lerp(minEfficacy, maxEfficacy, data.workmanship());
    }

    /** Integrity, finish and trait as one durability multiplier (see {@link LeatherworkEffects#adjustDamage}). */
    public double durability(LeatherworkData data) {
        double multiplier = lerp(minDurability, maxDurability, data.integrity());
        if (data.finish() == Finish.HARNESS) multiplier *= harnessDurability;
        TraitEffect effect = data.trait() == null ? null : TraitEffects.get(data.trait().effect());
        if (effect != null) multiplier *= effect.durabilityMultiplier(data.trait());
        return multiplier;
    }

    public static double lerp(double min, double max, int score) {
        return min + (max - min) * Mth.clamp(score, 0, 100) / 100.0;
    }
}
