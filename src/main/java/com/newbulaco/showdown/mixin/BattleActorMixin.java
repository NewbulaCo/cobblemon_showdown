package com.newbulaco.showdown.mixin;

import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.ShowdownActionRequest;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// don't prompt for a choice when every active on this side is already fainted.
@Mixin(value = BattleActor.class, remap = false)
public abstract class BattleActorMixin {

    @Shadow public abstract List<ActiveBattlePokemon> getActivePokemon();
    @Shadow public abstract ShowdownActionRequest getRequest();
    @Shadow public abstract void setRequest(ShowdownActionRequest request);
    @Shadow public abstract List<ShowdownActionResponse> getResponses();
    @Shadow public abstract List<ShowdownActionResponse> getExpectingPassActions();

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void cobblemonShowdown$skipTurnWhenAllFainted(CallbackInfo ci) {
        ShowdownActionRequest request = getRequest();
        if (request == null) return;

        for (ActiveBattlePokemon a : getActivePokemon()) {
            if (a.isAlive()) return;
        }

        getResponses().clear();
        List<ShowdownMoveset> active = request.getActive();
        if (active == null || active.isEmpty() || request.getWait()) {
            setRequest(null);
            getExpectingPassActions().clear();
        }
        ci.cancel();
    }
}
