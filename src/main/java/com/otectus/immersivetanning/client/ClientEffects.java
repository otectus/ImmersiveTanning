package com.otectus.immersivetanning.client;

import com.otectus.immersivetanning.client.station.ClientWork;
import com.otectus.immersivetanning.config.ClientConfig;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.network.packet.StationCommandPacket;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.station.BenchGeometry;
import com.otectus.immersivetanning.station.RackGeometry;
import com.otectus.immersivetanning.station.StationFrame;
import com.otectus.immersivetanning.station.VatGeometry;
import com.otectus.immersivetanning.util.StationEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Contact particles: a few flecks where the blade cleared flesh, drops at the rim, bark pieces, scrap flecks from a
 * cut, a fibre from a stitch. Few and small, scaled by the client's particle setting; never clouds, flashes or one
 * entity per piece. Positions follow the station's facing and, where known, the real contact point.
 */
public final class ClientEffects {

    public static void spawn(BlockPos pos, int type, float strength) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        RandomSource random = level.getRandom();
        Direction facing = StationFrame.facing(level.getBlockState(pos));
        switch (type) {
            case StationEffects.SCRAPE -> {
                // The operator's own client already put flecks at its blade (see scrape); watchers use the relayed
                // stroke. Without a known contact there is nothing to show.
                var behavior = com.otectus.immersivetanning.client.station.StationFocus.behaviorAt(pos);
                var session = com.otectus.immersivetanning.client.station.StationFocus.session();
                if (behavior != null && session != null && session.operator) return;
                float[] trail = ClientWork.trail(pos);
                if (trail == null) return;
                scrape(pos, StationCommandPacket.unit((short) trail[1]), StationCommandPacket.unit((short) trail[2]), strength);
            }
            case StationEffects.STITCH -> {
                Vec3 at = StationFrame.pxToWorld(pos, facing, 8, BenchGeometry.PLANE_Y + 0.5, 7.5);
                burst(level, random, THREAD, at, ClientConfig.scaleParticles(strength > 0.75F ? 1 : 2), 0.02, 0.02);
            }
            case StationEffects.SPLASH, StationEffects.LIFT -> {
                Vec3 at = StationFrame.pxToWorld(pos, facing, 8, VatGeometry.SURFACE_FULL + 0.3, 8);
                int count = Math.min(8, ClientConfig.scaleParticles(3 + Math.round(strength * 4F)));
                for (int i = 0; i < count; i++) {
                    level.addParticle(type == StationEffects.LIFT ? ParticleTypes.DRIPPING_WATER : ParticleTypes.SPLASH,
                            at.x + (random.nextDouble() - 0.5) * 0.5, at.y, at.z + (random.nextDouble() - 0.5) * 0.5, 0, 0.06, 0);
                }
            }
            case StationEffects.POUR -> {
                Vec3 at = StationFrame.pxToWorld(pos, facing, 8, VatGeometry.RIM + 0.2, 3.2);
                for (int i = 0; i < ClientConfig.scaleParticles(4); i++) {
                    level.addParticle(ParticleTypes.FALLING_WATER, at.x + (random.nextDouble() - 0.5) * 0.3, at.y, at.z + random.nextDouble() * 0.2, 0, 0, 0);
                }
            }
            case StationEffects.BARK -> {
                Vec3 at = StationFrame.pxToWorld(pos, facing, 8, VatGeometry.SURFACE_FULL + 0.8, 8);
                burst(level, random, new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(ModItems.BARK.get())), at,
                        Math.min(4, ClientConfig.scaleParticles(1 + Math.round(strength))), 0.05, 0.05);
            }
            case StationEffects.SWEEP -> {
                Vec3 at = StationFrame.pxToWorld(pos, facing, 8, VatGeometry.SURFACE_FULL + 0.1, 8);
                for (int i = 0; i < ClientConfig.scaleParticles(4); i++) {
                    level.addParticle(ParticleTypes.BUBBLE_POP, at.x + (random.nextDouble() - 0.5) * 0.55, at.y, at.z + (random.nextDouble() - 0.5) * 0.55,
                            0, 0.01, 0);
                }
            }
            case StationEffects.CUT -> {
                Vec3 at = StationFrame.pxToWorld(pos, facing, 8, BenchGeometry.PLANE_Y + 0.3, 7.5);
                burst(level, random, LEATHER_FLECK, at, ClientConfig.scaleParticles(3), 0.04, 0.02);
            }
            case StationEffects.FINISHED -> {
                Vec3 at = StationFrame.pxToWorld(pos, facing, BenchGeometry.OUTPUT_PAD.getCenter().x, 16.6, BenchGeometry.OUTPUT_PAD.getCenter().z);
                burst(level, random, new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(ModItems.leather(Finish.HARNESS))), at,
                        Math.min(6, ClientConfig.scaleParticles(3 + Math.round(strength * 3F))), 0.05, 0.08);
            }
            default -> {
            }
        }
    }

    /** Flesh flecks: tiny, dark red, a few per stroke - small enough to read as flecks with the camera close. */
    private static final DustParticleOptions FLESH_FLECK = new DustParticleOptions(new Vector3f(0.64F, 0.35F, 0.29F), 0.35F);
    private static final DustParticleOptions LEATHER_FLECK = new DustParticleOptions(new Vector3f(0.58F, 0.38F, 0.22F), 0.5F);
    private static final DustParticleOptions THREAD = new DustParticleOptions(new Vector3f(0.93F, 0.88F, 0.76F), 0.35F);

    /** Flecks where the blade cleared flesh, at (u, v) on the hide, lifted off the surface a little. */
    public static void scrape(BlockPos pos, float u, float v, float strength) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !ClientWork.allowFlecks(pos)) return;
        Direction facing = StationFrame.facing(level.getBlockState(pos));
        float[] p = RackGeometry.fleshPoint(u, v);
        float[] n = RackGeometry.fleshNormal(v);
        Vec3 at = StationFrame.pxToWorld(pos, facing, p[0] + n[0] * 0.4, p[1] + n[1] * 0.4, p[2] + n[2] * 0.4);
        int count = Math.min(2, ClientConfig.scaleParticles(1 + Math.round(strength * 20F)));
        burst(level, level.getRandom(), FLESH_FLECK, at, count, 0.012, -0.005);
    }

    private static void burst(ClientLevel level, RandomSource random, ParticleOptions particle, Vec3 at, int count, double spread, double up) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(at) > 256) return;
        ParticleStatus setting = mc.options.particles().get();
        if (setting == ParticleStatus.MINIMAL) return;
        if (setting == ParticleStatus.DECREASED) count = Math.min(1, count);
        for (int i = 0; i < count; i++) {
            // Vanilla dust is sized for distant blocks and lives for seconds. At the focused camera it becomes a
            // cloud across the work; these are short-lived, millimetre-sized scraps at the actual contact.
            var fleck = mc.particleEngine.createParticle(particle,
                    at.x + (random.nextDouble() - 0.5) * spread * 2, at.y, at.z + (random.nextDouble() - 0.5) * spread * 2,
                    0, 0, 0);
            if (fleck != null) {
                fleck.scale(0.22F);
                fleck.setLifetime(6 + random.nextInt(4));
                fleck.setParticleSpeed((random.nextDouble() - 0.5) * spread, up, (random.nextDouble() - 0.5) * spread);
            }
        }
    }

    private ClientEffects() {}
}
