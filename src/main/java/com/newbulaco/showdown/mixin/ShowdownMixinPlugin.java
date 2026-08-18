package com.newbulaco.showdown.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;

public class ShowdownMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("CobblemonShowdown");
    private boolean cobblemonPresent = false;

    @Override
    public void onLoad(String mixinPackage) {
        // use MixinService's bytecode provider to check class existence without loading it,
        // preventing conflicts with mods like ModernFix that need to mixin to core classes
        try {
            var bytecodeProvider = MixinService.getService().getBytecodeProvider();
            bytecodeProvider.getClassNode("com.cobblemon.mod.common.pokemon.Pokemon");
            cobblemonPresent = true;
        } catch (Exception e) {
            cobblemonPresent = false;
            LOGGER.warn("[Showdown] Cobblemon not found - client mixins will be disabled");
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!cobblemonPresent) {
            if (mixinClassName.contains("PCGUIMixin")
                    || mixinClassName.contains("BattleSwitchPokemonSelectionMixin")
                    || mixinClassName.contains("BattleGeneralActionSelectionMixin")
                    || mixinClassName.contains("BattleMadeInvalidChoiceHandlerMixin")) {
                return false;
            }
            // safe to always apply since it checks namespace, but needs cobblemon for party access
            if (mixinClassName.contains("ItemStackMixin")) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
