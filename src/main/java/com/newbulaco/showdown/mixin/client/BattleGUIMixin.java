package com.newbulaco.showdown.mixin.client;

import com.cobblemon.mod.common.battles.PassActionResponse;
import com.cobblemon.mod.common.battles.ShowdownPokemon;
import com.cobblemon.mod.common.battles.ShowdownSide;
import com.cobblemon.mod.common.battles.SwitchActionResponse;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.SingleActionRequest;
import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleActionSelection;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.newbulaco.showdown.client.battle.ClientCommandingTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

        if (request.getForceSwitch()) {
            if (cobblemonShowdown$forceSwitchHasNoCandidates(actor, request)) {
                BattleGUI self = (BattleGUI) (Object) this;
                self.selectAction(request, PassActionResponse.INSTANCE);
                cir.setReturnValue(null);
            }
            return;
        }

        boolean commanding = cobblemonShowdown$activeIsCommanding(request);
        if (!commanding && request.getMoveSet() != null && !cobblemonShowdown$sidePokemonMissingOrFainted(request)) return;

        BattleGUI self = (BattleGUI) (Object) this;
        self.selectAction(request, PassActionResponse.INSTANCE);
        cir.setReturnValue(null);
    }

    // pass surplus forceSwitch slots that have no bench candidate left.
    private boolean cobblemonShowdown$forceSwitchHasNoCandidates(ClientBattleActor actor, SingleActionRequest request) {
        ShowdownSide side = request.getSide();
        if (side == null || side.getPokemon().isEmpty()) return false;

        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null) return false;

        Set<UUID> switchingIn = new HashSet<>();
        for (SingleActionRequest req : battle.getPendingActionRequests()) {
            Object resp = req.getResponse();
            if (resp instanceof SwitchActionResponse) {
                switchingIn.add(((SwitchActionResponse) resp).getNewPokemonId());
            }
        }

        Set<UUID> activeUuids = new HashSet<>();
        for (ActiveClientBattlePokemon a : actor.getActivePokemon()) {
            ClientBattlePokemon bp = a.getBattlePokemon();
            if (bp != null) activeUuids.add(bp.getUuid());
        }

        List<ShowdownPokemon> sidePokemon = side.getPokemon();
        boolean reviving = sidePokemon.get(0).getReviving();

        for (ShowdownPokemon sp : sidePokemon) {
            Pokemon match = null;
            for (Pokemon actorMon : actor.getPokemon()) {
                if (actorMon.getUuid().equals(sp.getUuid())) {
                    match = actorMon;
                    break;
                }
            }
            if (match == null) continue;
            String condition = sp.getCondition();
            boolean isFnt = condition != null && condition.contains("fnt");
            if (isFnt != reviving) continue;
            if (activeUuids.contains(match.getUuid())) continue;
            if (switchingIn.contains(match.getUuid())) continue;
            return false;
        }
        return true;
    }

    private boolean cobblemonShowdown$activeIsCommanding(SingleActionRequest request) {
        ActiveClientBattlePokemon active = request.getActivePokemon();
        if (active == null) return false;
        ClientBattlePokemon bp = active.getBattlePokemon();
        if (bp == null) return false;
        return ClientCommandingTracker.getInstance().isCommanding(bp.getUuid());
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
