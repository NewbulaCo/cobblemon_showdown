package com.newbulaco.showdown.mixin.client;

import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleSwitchPokemonSelection;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.newbulaco.showdown.client.tooltip.PokemonTooltipRenderer;
import net.minecraft.client.gui.GuiGraphics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

@Mixin(BattleSwitchPokemonSelection.class)
public abstract class BattleSwitchPokemonSelectionMixin {
    @Unique
    private static final Logger cobblemonShowdown$LOGGER = LoggerFactory.getLogger("CobblemonShowdown");
    @Unique
    private static boolean cobblemonShowdown$loggedTileError = false;
    @Unique
    private static Method cobblemonShowdown$getTilesMethod = null;
    @Unique
    private static boolean cobblemonShowdown$tilesMethodLookupFailed = false;

    @Unique
    @SuppressWarnings("unchecked")
    private List<?> cobblemonShowdown$getTilesList() {
        if (cobblemonShowdown$tilesMethodLookupFailed) {
            return null;
        }

        try {
            if (cobblemonShowdown$getTilesMethod == null) {
                // kotlin 'val tiles' compiles to a public getTiles() method
                cobblemonShowdown$getTilesMethod = this.getClass().getMethod("getTiles");
            }
            return (List<?>) cobblemonShowdown$getTilesMethod.invoke(this);
        } catch (NoSuchMethodException e) {
            try {
                Field tilesField = this.getClass().getDeclaredField("tiles");
                tilesField.setAccessible(true);
                return (List<?>) tilesField.get(this);
            } catch (Exception ex) {
                cobblemonShowdown$LOGGER.warn("[Showdown] Could not access tiles. Listing available members:");
                for (Method m : this.getClass().getMethods()) {
                    if (m.getName().toLowerCase().contains("tile") || m.getName().startsWith("get")) {
                        cobblemonShowdown$LOGGER.warn("[Showdown]   method: {} -> {}", m.getName(), m.getReturnType().getSimpleName());
                    }
                }
                for (Field f : this.getClass().getDeclaredFields()) {
                    cobblemonShowdown$LOGGER.warn("[Showdown]   field: {} ({})", f.getName(), f.getType().getSimpleName());
                }
                cobblemonShowdown$tilesMethodLookupFailed = true;
                return null;
            }
        } catch (Exception e) {
            if (!cobblemonShowdown$tilesMethodLookupFailed) {
                cobblemonShowdown$LOGGER.error("[Showdown] Error invoking getTiles(): {}", e.getMessage());
                cobblemonShowdown$tilesMethodLookupFailed = true;
            }
            return null;
        }
    }

    // in MC 1.20+, renderButton was renamed to renderWidget
    @Inject(
        method = "renderWidget",
        at = @At("TAIL")
    )
    private void cobblemonShowdown$renderPokemonTooltip(
            GuiGraphics context,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo ci
    ) {
        List<?> tiles = cobblemonShowdown$getTilesList();
        if (tiles == null) {
            return;
        }

        if (tiles.isEmpty()) {
            return;
        }

        for (Object tileObj : tiles) {
            try {
                Method isHoveredMethod = tileObj.getClass().getMethod("isHovered", double.class, double.class);
                boolean isHovered = (boolean) isHoveredMethod.invoke(tileObj, (double) mouseX, (double) mouseY);

                if (isHovered) {
                    // kotlin val compiles to getter
                    Method getPokemonMethod = tileObj.getClass().getMethod("getPokemon");
                    Pokemon pokemon = (Pokemon) getPokemonMethod.invoke(tileObj);

                    if (pokemon != null) {
                        PokemonTooltipRenderer.renderTooltip(context, pokemon, mouseX, mouseY);
                    }
                    break;
                }
            } catch (Exception e) {
                if (!cobblemonShowdown$loggedTileError) {
                    cobblemonShowdown$LOGGER.warn("[Showdown] Error accessing SwitchTile: {}", e.getMessage());
                    cobblemonShowdown$LOGGER.info("[Showdown] Tile class: {}", tileObj.getClass().getName());
                    for (Method m : tileObj.getClass().getMethods()) {
                        if (!m.getDeclaringClass().equals(Object.class)) {
                            cobblemonShowdown$LOGGER.info("[Showdown]   method: {}({})", m.getName(), m.getParameterCount());
                        }
                    }
                    cobblemonShowdown$loggedTileError = true;
                }
            }
        }
    }
}
