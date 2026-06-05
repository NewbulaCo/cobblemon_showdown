package com.newbulaco.showdown.mixin.client;

import com.cobblemon.mod.common.battles.Targetable;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.List;

// auto-picks a single-target move's PNX when the user has more than one valid target
// to choose from. MoveTile.targetPnx returns null in that case (its "TODO: multi-battles"
// branch), and dispatching with null target makes showdown reject the choice. we
// substitute the first foe with a live pokemon, falling back to the first valid target
// when no foe is present (e.g. an ally-only move with multiple allies in the list).
@Mixin(targets = "com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection$MoveTile", remap = false)
public abstract class BattleMoveSelectionMixin {
    @Unique
    private static final Logger cobblemonShowdown$LOGGER = LoggerFactory.getLogger("CobblemonShowdown");

    @Unique
    private static Method cobblemonShowdown$getTargetListMethod = null;
    @Unique
    private static Method cobblemonShowdown$getMoveSelectionMethod = null;
    @Unique
    private static Method cobblemonShowdown$getRequestMethod = null;
    @Unique
    private static Method cobblemonShowdown$getActivePokemonMethod = null;
    @Unique
    private static boolean cobblemonShowdown$reflectionFailed = false;

    @Inject(method = "getTargetPnx", at = @At("RETURN"), cancellable = true)
    private void cobblemonShowdown$autoPickFirstFoe(CallbackInfoReturnable<String> cir) {
        if (cir.getReturnValue() != null) return;
        if (cobblemonShowdown$reflectionFailed) return;

        List<Targetable> targets = cobblemonShowdown$readTargetList();
        if (targets == null || targets.size() <= 1) return;

        Targetable user = cobblemonShowdown$readUser();
        Targetable pick = null;

        for (Targetable t : targets) {
            if (!t.hasPokemon()) continue;
            if (!cobblemonShowdown$isAlive(t)) continue;
            if (user != null && !user.isAllied(t)) {
                pick = t;
                break;
            }
            if (pick == null) pick = t;
        }

        if (pick != null) {
            cir.setReturnValue(pick.getPNX());
        }
    }

    @Unique
    @SuppressWarnings("unchecked")
    private List<Targetable> cobblemonShowdown$readTargetList() {
        try {
            if (cobblemonShowdown$getTargetListMethod == null) {
                cobblemonShowdown$getTargetListMethod = this.getClass().getMethod("getTargetList");
            }
            return (List<Targetable>) cobblemonShowdown$getTargetListMethod.invoke(this);
        } catch (Exception e) {
            cobblemonShowdown$logReflectionFailure("getTargetList", e);
            return null;
        }
    }

    @Unique
    private Targetable cobblemonShowdown$readUser() {
        try {
            if (cobblemonShowdown$getMoveSelectionMethod == null) {
                cobblemonShowdown$getMoveSelectionMethod = this.getClass().getMethod("getMoveSelection");
            }
            Object moveSelection = cobblemonShowdown$getMoveSelectionMethod.invoke(this);
            if (moveSelection == null) return null;

            if (cobblemonShowdown$getRequestMethod == null) {
                cobblemonShowdown$getRequestMethod = moveSelection.getClass().getMethod("getRequest");
            }
            Object request = cobblemonShowdown$getRequestMethod.invoke(moveSelection);
            if (request == null) return null;

            if (cobblemonShowdown$getActivePokemonMethod == null) {
                cobblemonShowdown$getActivePokemonMethod = request.getClass().getMethod("getActivePokemon");
            }
            Object active = cobblemonShowdown$getActivePokemonMethod.invoke(request);
            return active instanceof Targetable ? (Targetable) active : null;
        } catch (Exception e) {
            cobblemonShowdown$logReflectionFailure("user pokemon chain", e);
            return null;
        }
    }

    // hasPokemon only checks battlePokemon != null; a fainted slot still holds its reference
    // until the switch resolves. mirror the hp gate the server uses in MoveActionResponse.isValid.
    @Unique
    private boolean cobblemonShowdown$isAlive(Targetable t) {
        if (!(t instanceof ActiveClientBattlePokemon)) return true;
        ClientBattlePokemon bp = ((ActiveClientBattlePokemon) t).getBattlePokemon();
        return bp != null && bp.getHpValue() > 0;
    }

    @Unique
    private void cobblemonShowdown$logReflectionFailure(String what, Exception e) {
        if (!cobblemonShowdown$reflectionFailed) {
            cobblemonShowdown$LOGGER.warn("[Showdown] Auto-target reflection failed at {}: {}", what, e.getMessage());
            cobblemonShowdown$reflectionFailed = true;
        }
    }
}
