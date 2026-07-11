package com.newbulaco.showdown.mixin.client;

import com.cobblemon.mod.common.battles.PassActionResponse;
import com.cobblemon.mod.common.battles.ShowdownPokemon;
import com.cobblemon.mod.common.battles.ShowdownSide;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.SingleActionRequest;
import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleActionSelection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

// auto-pass slots that have no action for the player to take.
//
// showdown emits a SingleActionRequest per slot every turn, including slots it doesn't
// need a real choice for (e.g. slot A fainted needs a switch, slot B is healthy and
// unaddressed). without auto-pass the GUI walks the player through both, the choice
// packet over-fills, and showdown rejects with "more switches than Pokemon need to
// switch". pass conditions: no moveset, or side data says our active is missing/fainted.
@Mixin(targets = "com.cobblemon.mod.common.client.gui.battle.BattleGUI", remap = false)
public abstract class BattleGUIMixin {

    @Inject(method = "deriveRootActionSelection", at = @At("HEAD"), cancellable = true)
    private void cobblemonShowdown$autoPassNoActionSlots(
            ClientBattleActor actor,
            SingleActionRequest request,
            CallbackInfoReturnable<BattleActionSelection> cir) {

        if (request.getForceSwitch()) return;

        if (request.getMoveSet() != null && !cobblemonShowdown$sidePokemonMissingOrFainted(request)) return;

        BattleGUI self = (BattleGUI) (Object) this;
        self.selectAction(request, PassActionResponse.INSTANCE);
        cir.setReturnValue(null);
    }

    private boolean cobblemonShowdown$sidePokemonMissingOrFainted(SingleActionRequest request) {
        ShowdownSide side = request.getSide();
        if (side == null) return false;

        ActiveClientBattlePokemon active = request.getActivePokemon();
        ClientBattlePokemon battlePokemon = active.getBattlePokemon();
        if (battlePokemon == null) return true;

        UUID target = battlePokemon.getUuid();
        ShowdownPokemon match = null;
        for (ShowdownPokemon p : side.getPokemon()) {
            if (target.equals(p.getUuid())) {
                match = p;
                break;
            }
        }
        if (match == null) return true;

        String condition = match.getCondition();
        return condition != null && condition.contains("fnt");
    }
}
