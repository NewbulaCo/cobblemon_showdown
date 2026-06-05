package com.newbulaco.showdown.mixin;

import com.mojang.logging.LogUtils;
import com.newbulaco.showdown.api.ShowdownScriptBuilder;
import com.newbulaco.showdown.api.registry.ContentRegistry;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.nio.file.Path;

// ensures custom content registered via ShowdownAPI is written to showdown/data/mods/cobblemon/
// on every server start, so users never need to manually delete the showdown folder after updating mods
@Mixin(targets = "com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService", remap = false)
public class GraalShowdownServiceMixin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean hasInjected = false;

    @Inject(method = "openConnection", at = @At("HEAD"))
    private void cobblemonShowdown$injectScripts(CallbackInfo ci) {
        if (hasInjected) {
            return;
        }

        try {
            triggerUnbundleEarly();
            injectCustomContent();
            hasInjected = true;
        } catch (Exception e) {
            LOGGER.error("[CobblemonShowdown] Failed to inject Showdown scripts: {}", e.getMessage());
            LOGGER.debug("[CobblemonShowdown] Injection error details:", e);
        } finally {
            // engine is about to start regardless of injection outcome; freeze the registry
            // so any further showdownJs registration is flagged loudly.
            ContentRegistry.markScriptsBuilt();
        }
    }

    // force the unbundler to run early so the showdown/ directory exists before we write scripts
    private void triggerUnbundleEarly() {
        try {
            var field = this.getClass().getDeclaredField("unbundler");
            field.setAccessible(true);
            var unbundler = field.get(this);

            if (unbundler != null) {
                var method = unbundler.getClass().getMethod("attemptUnbundle");
                method.invoke(unbundler);
                LOGGER.debug("[CobblemonShowdown] Early unbundle completed");
            }
        } catch (NoSuchFieldException e) {
            LOGGER.debug("[CobblemonShowdown] Could not find unbundler field, showdown may already be extracted");
        } catch (Exception e) {
            LOGGER.debug("[CobblemonShowdown] Early unbundle skipped: {}", e.getMessage());
        }
    }

    private void injectCustomContent() {
        File showdownDir = new File("showdown");
        if (!showdownDir.exists()) {
            LOGGER.warn("[CobblemonShowdown] Showdown directory not found after unbundle, script injection skipped");
            return;
        }

        if (!ContentRegistry.hasShowdownJs()) {
            LOGGER.debug("[CobblemonShowdown] No custom content with Showdown JS registered, skipping");
            return;
        }

        Path modDir = showdownDir.toPath().resolve("data").resolve("mods").resolve("cobblemon");
        LOGGER.info("[CobblemonShowdown] Injecting custom Showdown scripts ({} registrations)...",
                ContentRegistry.getTotalRegistrations());

        ShowdownScriptBuilder.buildAndWriteScripts(modDir);
    }
}
