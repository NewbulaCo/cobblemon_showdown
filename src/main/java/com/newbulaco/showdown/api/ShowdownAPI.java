package com.newbulaco.showdown.api;

import com.newbulaco.showdown.api.content.*;
import com.newbulaco.showdown.api.registry.ContentRegistry;
import com.newbulaco.showdown.api.registry.RegistrationResult;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

// public registration surface for the Cobblemon Showdown content API.
//
// lifecycle: register from FMLCommonSetupEvent. content can still be registered later
// and will appear in /dt and battle UI, but its showdownJs cannot reach the Showdown
// engine after the first battle's openConnection runs (see ContentRegistry.areScriptsBuilt).
//
// id namespace: ids are global across consumers and lowercase-normalized. register()
// will overwrite a colliding entry and log a warning; tryRegister() returns REJECTED
// instead. nums (>= 9000 by convention) should also be unique per consumer; the
// registry logs a warning on collision.
//
// showdownJs namespace: every consumer's showdownJs is concatenated into a single
// exports.Moves / exports.Abilities / exports.Conditions object. malformed JS from one
// consumer can corrupt entries from another; the registry runs a brace-balance check
// at register time and logs a warning if it looks off.
public final class ShowdownAPI {

    private ShowdownAPI() {}

    public static RegistrationResult registerAbility(CustomAbility ability) {
        return ContentRegistry.registerAbility(ability);
    }

    public static RegistrationResult tryRegisterAbility(CustomAbility ability) {
        return ContentRegistry.tryRegisterAbility(ability);
    }

    @Nullable
    public static CustomAbility getAbility(String id) {
        return ContentRegistry.getAbility(id);
    }

    public static Collection<CustomAbility> getAllAbilities() {
        return ContentRegistry.getAllAbilities();
    }

    public static RegistrationResult registerMove(CustomMove move) {
        return ContentRegistry.registerMove(move);
    }

    public static RegistrationResult tryRegisterMove(CustomMove move) {
        return ContentRegistry.tryRegisterMove(move);
    }

    @Nullable
    public static CustomMove getMove(String id) {
        return ContentRegistry.getMove(id);
    }

    public static Collection<CustomMove> getAllMoves() {
        return ContentRegistry.getAllMoves();
    }

    public static RegistrationResult registerMoveModification(MoveModification mod) {
        return ContentRegistry.registerMoveModification(mod);
    }

    public static RegistrationResult tryRegisterMoveModification(MoveModification mod) {
        return ContentRegistry.tryRegisterMoveModification(mod);
    }

    @Nullable
    public static MoveModification getMoveModification(String moveId) {
        return ContentRegistry.getMoveModification(moveId);
    }

    public static Collection<MoveModification> getAllMoveModifications() {
        return ContentRegistry.getAllMoveModifications();
    }

    public static RegistrationResult registerAbilityModification(AbilityModification mod) {
        return ContentRegistry.registerAbilityModification(mod);
    }

    public static RegistrationResult tryRegisterAbilityModification(AbilityModification mod) {
        return ContentRegistry.tryRegisterAbilityModification(mod);
    }

    @Nullable
    public static AbilityModification getAbilityModification(String abilityId) {
        return ContentRegistry.getAbilityModification(abilityId);
    }

    public static Collection<AbilityModification> getAllAbilityModifications() {
        return ContentRegistry.getAllAbilityModifications();
    }

    // injected before abilities/moves load; use for shared constants like ALL_TYPES or utility functions
    public static void registerHelperJs(String js) {
        ContentRegistry.registerHelperJs(js);
    }

    public static List<String> getAllHelperJs() {
        return ContentRegistry.getAllHelperJs();
    }

    public static RegistrationResult registerFieldCondition(CustomFieldCondition condition) {
        return ContentRegistry.registerFieldCondition(condition);
    }

    public static RegistrationResult tryRegisterFieldCondition(CustomFieldCondition condition) {
        return ContentRegistry.tryRegisterFieldCondition(condition);
    }

    @Nullable
    public static CustomFieldCondition getFieldCondition(String id) {
        return ContentRegistry.getFieldCondition(id);
    }

    public static Collection<CustomFieldCondition> getAllFieldConditions() {
        return ContentRegistry.getAllFieldConditions();
    }

    public static RegistrationResult registerVolatileEffect(CustomVolatileEffect effect) {
        return ContentRegistry.registerVolatileEffect(effect);
    }

    public static RegistrationResult tryRegisterVolatileEffect(CustomVolatileEffect effect) {
        return ContentRegistry.tryRegisterVolatileEffect(effect);
    }

    @Nullable
    public static CustomVolatileEffect getVolatileEffect(String id) {
        return ContentRegistry.getVolatileEffect(id);
    }

    public static Collection<CustomVolatileEffect> getAllVolatileEffects() {
        return ContentRegistry.getAllVolatileEffects();
    }

    public static RegistrationResult registerSideCondition(CustomSideCondition condition) {
        return ContentRegistry.registerSideCondition(condition);
    }

    public static RegistrationResult tryRegisterSideCondition(CustomSideCondition condition) {
        return ContentRegistry.tryRegisterSideCondition(condition);
    }

    @Nullable
    public static CustomSideCondition getSideCondition(String id) {
        return ContentRegistry.getSideCondition(id);
    }

    public static Collection<CustomSideCondition> getAllSideConditions() {
        return ContentRegistry.getAllSideConditions();
    }

    public static boolean hasCustomAbility(String id) {
        return ContentRegistry.getAbility(id) != null;
    }

    public static boolean hasCustomMove(String id) {
        return ContentRegistry.getMove(id) != null;
    }

    public static boolean hasMoveModification(String moveId) {
        return ContentRegistry.getMoveModification(moveId) != null;
    }

    public static boolean hasAbilityModification(String abilityId) {
        return ContentRegistry.getAbilityModification(abilityId) != null;
    }

    public static boolean hasShowdownJs() {
        return ContentRegistry.hasShowdownJs();
    }

    // true once Showdown has started; after this point new showdownJs cannot reach the engine.
    public static boolean isScriptsBuilt() {
        return ContentRegistry.areScriptsBuilt();
    }

    public static String getAbilityDisplayName(String id) {
        CustomAbility ability = ContentRegistry.getAbility(id);
        return ability != null ? ability.getDisplayName() : formatId(id);
    }

    public static String getMoveDisplayName(String id) {
        CustomMove move = ContentRegistry.getMove(id);
        return move != null ? move.getDisplayName() : formatId(id);
    }

    public static String getFieldConditionDisplayName(String id) {
        CustomFieldCondition condition = ContentRegistry.getFieldCondition(id);
        return condition != null ? condition.getDisplayName() : formatId(id);
    }

    public static String getVolatileEffectDisplayName(String id) {
        CustomVolatileEffect effect = ContentRegistry.getVolatileEffect(id);
        return effect != null ? effect.getDisplayName() : formatId(id);
    }

    public static String getSideConditionDisplayName(String id) {
        CustomSideCondition condition = ContentRegistry.getSideCondition(id);
        return condition != null ? condition.getDisplayName() : formatId(id);
    }

    private static String formatId(String id) {
        if (id == null || id.isEmpty()) return "";

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : id.toCharArray()) {
            if (c == '_' || c == ' ') {
                result.append(' ');
                capitalizeNext = true;
            } else if (Character.isUpperCase(c)) {
                if (result.length() > 0 && !capitalizeNext) {
                    result.append(' ');
                }
                result.append(c);
                capitalizeNext = false;
            } else {
                result.append(capitalizeNext ? Character.toUpperCase(c) : c);
                capitalizeNext = false;
            }
        }

        return result.toString().trim();
    }
}
