package com.otectus.immersivetanning.mixin;

import com.otectus.immersivetanning.compat.geneticanimals.GeneticAnimalsHooks;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Adds the Genetic Animals hooks ({@code compat.eanimod.*}) only when that mod is in the loading mod list, before any
 * of its classes can load, and checks each hook once its class is transformed: a hook that did not take is recorded as
 * failed ({@link GeneticAnimalsHooks}) and logged, and that path keeps Genetic Animals' own behaviour. The hooks are
 * written for 0.11.12; on another release they apply wherever its methods are unchanged.
 */
public final class TanningMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("immersive_tanning");
    private static final String COMPAT = ".compat.eanimod.";
    private static final List<String> GENETIC_ANIMALS = List.of("compat.eanimod.EnhancedAnimalAbstractMixin",
            "compat.eanimod.EnhancedAnimalRideableAbstractMixin", "compat.eanimod.EanimodVillagerTradesMixin");

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !mixinClassName.contains(COMPAT) || GeneticAnimalsHooks.present();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return GeneticAnimalsHooks.present() ? GENETIC_ANIMALS : null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (!mixinClassName.contains(COMPAT) && !mixinClassName.endsWith(".LivingEntityMixin")) return;
        for (GeneticAnimalsHooks.Hook hook : GeneticAnimalsHooks.Hook.values()) {
            if (!hook.targetClass.equals(targetClassName)) continue;
            boolean applied = true;
            for (String handler : hook.handlers) applied &= calls(targetClass, hook.targetMethod, handler);
            GeneticAnimalsHooks.record(hook, applied);
            if (applied) {
                LOGGER.debug("Immersive Tanning: hook {} applied to {}", hook, targetClassName);
            } else if (hook == GeneticAnimalsHooks.Hook.DEATH_DROPS) {
                LOGGER.error("Immersive Tanning: another mod has rewritten LivingEntity#dropAllDeathLoot, so the death drops of Genetic"
                        + " Animals' animals cannot be told from their cargo; Immersive Tanning leaves those drops untouched.");
            } else {
                LOGGER.error("Genetic Animals {}: hook {} did not apply to {}#{}; that path keeps Genetic Animals' own behaviour."
                        + " Immersive Tanning's integration is verified against 0.11.12.", GeneticAnimalsHooks.version(), hook, targetClassName, hook.targetMethod);
            }
        }
    }

    /** Whether the target method (any, if null) now calls the handler (Mixin merges handlers under a prefixed name). */
    private static boolean calls(ClassNode target, String method, String handler) {
        for (MethodNode m : target.methods) {
            if (method != null && !m.name.equals(method)) continue;
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof MethodInsnNode call && call.name.endsWith(handler)) return true;
            }
        }
        return false;
    }
}
