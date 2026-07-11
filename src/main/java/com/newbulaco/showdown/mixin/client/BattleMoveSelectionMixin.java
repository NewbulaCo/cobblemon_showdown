package com.newbulaco.showdown.mixin.client;

import com.cobblemon.mod.common.battles.InBattleMove;
import com.cobblemon.mod.common.client.battle.SingleActionRequest;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection;
import com.newbulaco.showdown.client.gui.battle.BattleTargetSelection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// routes the move click to our target picker subscreen in 2v2+ battles.
// without this, dispatching a move with multiple valid targets sends a null pnx to
// showdown which rejects it. singles is byte-for-byte untouched: we only act when
// pokemonperside > 1.
@Mixin(targets = "com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection$MoveTile", remap = false)
public abstract class BattleMoveSelectionMixin {

    @Shadow public abstract InBattleMove getMove();
    @Shadow public abstract BattleMoveSelection getMoveSelection();
    @Shadow public abstract boolean getSelectable();

    @Inject(method = "onClick", at = @At("HEAD"), cancellable = true)
    private void cobblemonShowdown$routeToTargetScreen(CallbackInfo ci) {
        if (!getSelectable()) return;

        BattleMoveSelection ms = getMoveSelection();
        if (ms == null) return;
        SingleActionRequest req = ms.getRequest();
        if (req.getActivePokemon().getFormat().getBattleType().getPokemonPerSide() <= 1) return;

        ms.getBattleGUI().changeActionSelection(
                new BattleTargetSelection(ms.getBattleGUI(), req, getMove()));
        ci.cancel();
    }
}
