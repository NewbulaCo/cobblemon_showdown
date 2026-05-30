package com.newbulaco.showdown.compat;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.pokemon.Pokemon;

/**
 * Single source of truth for the "can this party Pokemon learn this TM/TR move" check used
 * by the /showdown dt command and the TM item tooltip. Delegates to SimpleTMs when present,
 * otherwise falls back to SimpleTMs' default rules so the answer still matches a vanilla
 * SimpleTMs install.
 */
public final class PartyLearnerCheck {

    private PartyLearnerCheck() {}

    public static boolean knowsMove(Pokemon pokemon, MoveTemplate move) {
        for (var slot : pokemon.getMoveSet()) {
            if (slot != null && slot.getTemplate().equals(move)) return true;
        }
        for (var benched : pokemon.getBenchedMoves()) {
            if (benched.getMoveTemplate().equals(move)) return true;
        }
        return false;
    }

    public static boolean canLearnMove(Pokemon pokemon, MoveTemplate move) {
        if (SimpleTMsCompat.isLoaded()) {
            return SimpleTMsCompat.canLearnMove(pokemon, move);
        }
        return defaultCanLearnMove(pokemon, move);
    }

    // matches SimpleTMs 1.1.2 defaults (TM always, tutor on, egg on, no level-up, no pre-evo)
    private static boolean defaultCanLearnMove(Pokemon pokemon, MoveTemplate move) {
        var moves = pokemon.getForm().getMoves();
        if (moves.getTmMoves().contains(move)) return true;
        if (moves.getTutorMoves().contains(move)) return true;
        if (moves.getEggMoves().contains(move)) return true;
        return false;
    }
}
