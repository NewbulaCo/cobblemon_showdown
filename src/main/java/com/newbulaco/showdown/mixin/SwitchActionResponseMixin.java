package com.newbulaco.showdown.mixin;

import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.ShowdownActionRequest;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.battles.ShowdownPokemon;
import com.cobblemon.mod.common.battles.ShowdownSide;
import com.cobblemon.mod.common.battles.SwitchActionResponse;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.UUID;

// fix NPE and wrong reviving lookup by matching on the active pokemon, not side.pokemon[0].
@Mixin(value = SwitchActionResponse.class, remap = false)
public abstract class SwitchActionResponseMixin {

    @Overwrite
    public boolean isValid(ActiveBattlePokemon activeBattlePokemon, ShowdownMoveset showdownMoveSet, boolean forceSwitch) {
        SwitchActionResponse self = (SwitchActionResponse) (Object) this;
        UUID newPokemonId = self.getNewPokemonId();

        BattlePokemon pokemon = null;
        for (BattlePokemon bp : activeBattlePokemon.getActor().getPokemonList()) {
            if (bp.getUuid().equals(newPokemonId)) { pokemon = bp; break; }
        }
        if (pokemon == null) return false;

        boolean reviving = false;
        ShowdownActionRequest request = activeBattlePokemon.getActor().getRequest();
        ShowdownSide side = request != null ? request.getSide() : null;
        BattlePokemon active = activeBattlePokemon.getBattlePokemon();
        if (side != null && active != null) {
            for (ShowdownPokemon sp : side.getPokemon()) {
                if (active.getUuid().equals(sp.getUuid()) && sp.getReviving()) { reviving = true; break; }
            }
        }
        if (!reviving && pokemon.getHealth() <= 0) return false;
        if (showdownMoveSet != null && showdownMoveSet.getTrapped()) return false;
        for (ActiveBattlePokemon a : activeBattlePokemon.getActor().getSide().getActivePokemon()) {
            BattlePokemon bp = a.getBattlePokemon();
            if (bp != null && newPokemonId.equals(bp.getUuid())) return false;
        }
        return true;
    }
}
