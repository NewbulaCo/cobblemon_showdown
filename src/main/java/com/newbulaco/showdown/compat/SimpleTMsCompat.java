package com.newbulaco.showdown.compat;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridge to SimpleTMs' real learnability rules so the party-learner display matches what
 * actually happens when the player uses a TM/TR. SimpleTMs is a compileOnly optional
 * dependency; every reference to its classes lives in the {@link Bridge} inner class so the
 * JVM only links them when {@link #isLoaded()} has already returned true.
 */
public final class SimpleTMsCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleTMsCompat.class);
    private static final String MOD_ID = "simpletms";

    private static Boolean loaded;
    private static boolean warned;

    private SimpleTMsCompat() {}

    public static boolean isLoaded() {
        Boolean cached = loaded;
        if (cached == null) {
            cached = ModList.get() != null && ModList.get().isLoaded(MOD_ID);
            loaded = cached;
        }
        return cached;
    }

    // mirrors MoveTutorItem#canLearnMove from SimpleTMs Forge 1.1.2: anyMoveAnyPokemon
    // short-circuits to true, then TMs are always allowed and tutor/egg moves are gated by
    // their respective config flags. level-up moves and pre-evolution chains are NOT
    // considered, matching SimpleTMs' actual behavior.
    public static boolean canLearnMove(Pokemon pokemon, MoveTemplate move) {
        if (!isLoaded()) return false;
        try {
            return Bridge.canLearnMove(pokemon, move);
        } catch (Throwable t) {
            warnOnce(t);
            return false;
        }
    }

    private static void warnOnce(Throwable t) {
        if (warned) return;
        warned = true;
        LOGGER.warn("SimpleTMs is loaded but its config could not be read; the party-learner display will fall back to defaults: {}", t.toString());
    }

    // isolated so SimpleTMs class symbols are only resolved after isLoaded() passes
    private static final class Bridge {
        static boolean canLearnMove(Pokemon pokemon, MoveTemplate move) {
            var config = git.dragomordor.simpletms.forge.SimpleTMsMod.config;
            if (config == null) return false;

            if (config.anyMoveAnyPokemon) return true;

            var moves = pokemon.getForm().getMoves();
            if (moves.getTmMoves().contains(move)) return true;
            if (config.tutorMovesLearnable && moves.getTutorMoves().contains(move)) return true;
            if (config.eggMovesLearnable && moves.getEggMoves().contains(move)) return true;
            return false;
        }
    }
}
