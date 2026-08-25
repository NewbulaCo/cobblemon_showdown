package com.newbulaco.showdown.mixin;

import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.InBattleGimmickMove;
import com.cobblemon.mod.common.battles.InBattleMove;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.MoveTarget;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.battles.Targetable;
import kotlin.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.List;

// don't reject a move whose chosen target has just fainted mid-turn.
@Mixin(value = MoveActionResponse.class, remap = false)
public abstract class MoveActionResponseMixin {

    @Overwrite
    public boolean isValid(ActiveBattlePokemon activeBattlePokemon, ShowdownMoveset showdownMoveSet, boolean forceSwitch) {
        if (forceSwitch || showdownMoveSet == null) return false;

        MoveActionResponse self = (MoveActionResponse) (Object) this;
        String moveName = self.getMoveName();
        InBattleMove move = null;
        for (InBattleMove m : showdownMoveSet.getMoves()) {
            if (m.getId().equals(moveName)) { move = m; break; }
        }
        if (move == null) return false;

        InBattleGimmickMove gimmickMove = move.getGimmickMove();
        boolean validGimmickMove = gimmickMove != null && !gimmickMove.getDisabled();
        if (!validGimmickMove && !move.canBeUsed()) return false;

        MoveTarget target = validGimmickMove ? gimmickMove.getTarget() : move.getTarget();
        List<Targetable> availableTargets = target.getTargetList().invoke(activeBattlePokemon);
        if (availableTargets == null || availableTargets.isEmpty()) return true;

        String pnx = self.getTargetPnx();
        if (pnx == null) return false;
        Pair<?, ActiveBattlePokemon> resolved = activeBattlePokemon.getActor().getBattle().getActorAndActiveSlotFromPNX(pnx);
        return availableTargets.contains(resolved.getSecond());
    }
}
