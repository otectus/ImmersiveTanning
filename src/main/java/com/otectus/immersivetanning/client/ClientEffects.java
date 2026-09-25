package com.otectus.immersivetanning.client;

import com.otectus.immersivetanning.config.ClientConfig;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.util.StationEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** Spawns station effects on the client, scaled by the client's particle setting. */
public final class ClientEffects {

    public static void spawn(BlockPos pos, int type, float strength) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        RandomSource random = level.getRandom();
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        switch (type) {
            case StationEffects.SCRAPE -> {
                int count = ClientConfig.scaleParticles(2 + Math.round(strength * 40F));
                ItemParticleOption flesh = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(ModItems.rawHide(com.otectus.immersivetanning.hide.HideSize.SMALL)));
                for (int i = 0; i < Math.min(count, 8); i++) {
                    level.addParticle(flesh, x + (random.nextDouble() - 0.5) * 0.6, y + 0.6, z + (random.nextDouble() - 0.5) * 0.6,
                            (random.nextDouble() - 0.5) * 0.08, 0.05, (random.nextDouble() - 0.5) * 0.08);
                }
            }
            case StationEffects.STITCH -> {
                int count = ClientConfig.scaleParticles(strength > 0 ? 3 : 1);
                for (int i = 0; i < count; i++) {
                    level.addParticle(strength >= 0.9F ? ParticleTypes.CRIT : ParticleTypes.SMOKE, x + (random.nextDouble() - 0.5) * 0.4,
                            y + 1.05, z + (random.nextDouble() - 0.5) * 0.4, 0, 0.02, 0);
                }
            }
            case StationEffects.SPLASH -> {
                int count = ClientConfig.scaleParticles(4 + Math.round(strength * 8F));
                for (int i = 0; i < count; i++) {
                    level.addParticle(ParticleTypes.SPLASH, x + (random.nextDouble() - 0.5) * 0.6, y + 0.8, z + (random.nextDouble() - 0.5) * 0.6, 0, 0.1, 0);
                }
            }
            case StationEffects.FINISHED -> {
                int count = ClientConfig.scaleParticles(6 + Math.round(strength * 8F));
                ItemParticleOption leather = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(ModItems.leather(Finish.HARNESS)));
                for (int i = 0; i < count; i++) {
                    level.addParticle(leather, x + (random.nextDouble() - 0.5) * 0.5, y + 1.05, z + (random.nextDouble() - 0.5) * 0.5,
                            (random.nextDouble() - 0.5) * 0.1, 0.12, (random.nextDouble() - 0.5) * 0.1);
                }
                if (strength >= 0.9F) {
                    level.addParticle(ParticleTypes.HAPPY_VILLAGER, x, y + 1.2, z, 0, 0, 0);
                }
            }
            default -> {
            }
        }
    }

    private ClientEffects() {}
}
