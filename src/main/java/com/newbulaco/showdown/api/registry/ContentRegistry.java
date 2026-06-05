package com.newbulaco.showdown.api.registry;

import com.newbulaco.showdown.api.content.*;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// thread-safe for registration during mod loading. consumers are expected to register
// from FMLCommonSetupEvent; once the Showdown engine starts (see markScriptsBuilt) any
// further showdownJs additions cannot reach the battle engine without a JVM restart.
public final class ContentRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("CobblemonShowdown");

    private static final Map<String, CustomAbility> abilities = new ConcurrentHashMap<>();
    private static final Map<String, CustomMove> moves = new ConcurrentHashMap<>();
    private static final Map<String, MoveModification> moveModifications = new ConcurrentHashMap<>();
    private static final Map<String, AbilityModification> abilityModifications = new ConcurrentHashMap<>();
    private static final Map<String, CustomFieldCondition> fieldConditions = new ConcurrentHashMap<>();
    private static final Map<String, CustomVolatileEffect> volatileEffects = new ConcurrentHashMap<>();
    private static final Map<String, CustomSideCondition> sideConditions = new ConcurrentHashMap<>();
    private static final List<String> helperJs = Collections.synchronizedList(new ArrayList<>());

    // num -> id, so a collision warning can name the prior registrant
    private static final Map<Integer, String> usedAbilityNums = new ConcurrentHashMap<>();
    private static final Map<Integer, String> usedMoveNums = new ConcurrentHashMap<>();

    // flipped by the script-builder mixin once the Showdown engine starts. content
    // registered after this point still goes into the registry (for /dt and UI), but
    // its showdownJs is too late to be picked up by Showdown for this JVM.
    private static volatile boolean scriptsBuilt;

    private ContentRegistry() {}

    public static RegistrationResult registerAbility(CustomAbility ability) {
        return doRegisterAbility(ability, true);
    }

    public static RegistrationResult tryRegisterAbility(CustomAbility ability) {
        return doRegisterAbility(ability, false);
    }

    private static RegistrationResult doRegisterAbility(CustomAbility ability, boolean allowOverwrite) {
        String id = ability.getId();
        CustomAbility prior = abilities.get(id);
        if (prior != null && !allowOverwrite) {
            LOGGER.warn("Rejected custom ability '{}' from '{}': already registered by '{}'",
                    id, ability.getModId(), prior.getModId());
            return RegistrationResult.REJECTED;
        }
        if (prior != null) {
            LOGGER.warn("Overwriting custom ability '{}' from '{}' (was from '{}')",
                    id, ability.getModId(), prior.getModId());
        }
        warnIfNumCollision(ability.getNum(), id, ability.getModId(), usedAbilityNums, "ability");
        warnIfMalformedJs(ability.getShowdownJs(), id, ability.getModId(), "ability");
        warnIfFrozen(ability.hasShowdownJs(), id, ability.getModId(), "ability");

        abilities.put(id, ability);
        if (ability.getNum() != 0) usedAbilityNums.putIfAbsent(ability.getNum(), id);

        LOGGER.info("Registered custom ability: {} ({}) from {}", ability.getDisplayName(), id, ability.getModId());
        return prior != null ? RegistrationResult.REPLACED : RegistrationResult.REGISTERED;
    }

    @Nullable
    public static CustomAbility getAbility(String id) {
        if (id == null) return null;
        return abilities.get(normalize(id));
    }

    public static Collection<CustomAbility> getAllAbilities() {
        return Collections.unmodifiableCollection(abilities.values());
    }

    public static RegistrationResult registerMove(CustomMove move) {
        return doRegisterMove(move, true);
    }

    public static RegistrationResult tryRegisterMove(CustomMove move) {
        return doRegisterMove(move, false);
    }

    private static RegistrationResult doRegisterMove(CustomMove move, boolean allowOverwrite) {
        String id = move.getId();
        CustomMove prior = moves.get(id);
        if (prior != null && !allowOverwrite) {
            LOGGER.warn("Rejected custom move '{}' from '{}': already registered by '{}'",
                    id, move.getModId(), prior.getModId());
            return RegistrationResult.REJECTED;
        }
        if (prior != null) {
            LOGGER.warn("Overwriting custom move '{}' from '{}' (was from '{}')",
                    id, move.getModId(), prior.getModId());
        }
        warnIfNumCollision(move.getNum(), id, move.getModId(), usedMoveNums, "move");
        warnIfMalformedJs(move.getShowdownJs(), id, move.getModId(), "move");
        warnIfFrozen(move.hasShowdownJs(), id, move.getModId(), "move");

        moves.put(id, move);
        if (move.getNum() != 0) usedMoveNums.putIfAbsent(move.getNum(), id);

        LOGGER.info("Registered custom move: {} ({}) from {}", move.getDisplayName(), id, move.getModId());
        return prior != null ? RegistrationResult.REPLACED : RegistrationResult.REGISTERED;
    }

    @Nullable
    public static CustomMove getMove(String id) {
        if (id == null) return null;
        return moves.get(normalize(id));
    }

    public static Collection<CustomMove> getAllMoves() {
        return Collections.unmodifiableCollection(moves.values());
    }

    public static RegistrationResult registerMoveModification(MoveModification mod) {
        return doRegisterMoveModification(mod, true);
    }

    public static RegistrationResult tryRegisterMoveModification(MoveModification mod) {
        return doRegisterMoveModification(mod, false);
    }

    private static RegistrationResult doRegisterMoveModification(MoveModification mod, boolean allowOverwrite) {
        String id = mod.getMoveId();
        MoveModification prior = moveModifications.get(id);
        if (prior != null && !allowOverwrite) {
            LOGGER.warn("Rejected move modification '{}' from '{}': already registered by '{}'",
                    id, mod.getModId(), prior.getModId());
            return RegistrationResult.REJECTED;
        }
        if (prior != null) {
            LOGGER.warn("Overwriting move modification '{}' from '{}' (was from '{}')",
                    id, mod.getModId(), prior.getModId());
        }
        warnIfMalformedJs(mod.getShowdownJs(), id, mod.getModId(), "move modification");
        warnIfFrozen(mod.hasShowdownJs(), id, mod.getModId(), "move modification");

        moveModifications.put(id, mod);
        LOGGER.info("Registered move modification: {} (from {})", id, mod.getModId());
        return prior != null ? RegistrationResult.REPLACED : RegistrationResult.REGISTERED;
    }

    @Nullable
    public static MoveModification getMoveModification(String moveId) {
        if (moveId == null) return null;
        return moveModifications.get(normalize(moveId));
    }

    public static Collection<MoveModification> getAllMoveModifications() {
        return Collections.unmodifiableCollection(moveModifications.values());
    }

    public static RegistrationResult registerAbilityModification(AbilityModification mod) {
        return doRegisterAbilityModification(mod, true);
    }

    public static RegistrationResult tryRegisterAbilityModification(AbilityModification mod) {
        return doRegisterAbilityModification(mod, false);
    }

    private static RegistrationResult doRegisterAbilityModification(AbilityModification mod, boolean allowOverwrite) {
        String id = mod.getAbilityId();
        AbilityModification prior = abilityModifications.get(id);
        if (prior != null && !allowOverwrite) {
            LOGGER.warn("Rejected ability modification '{}' from '{}': already registered by '{}'",
                    id, mod.getModId(), prior.getModId());
            return RegistrationResult.REJECTED;
        }
        if (prior != null) {
            LOGGER.warn("Overwriting ability modification '{}' from '{}' (was from '{}')",
                    id, mod.getModId(), prior.getModId());
        }
        warnIfMalformedJs(mod.getShowdownJs(), id, mod.getModId(), "ability modification");
        warnIfFrozen(mod.hasShowdownJs(), id, mod.getModId(), "ability modification");

        abilityModifications.put(id, mod);
        LOGGER.info("Registered ability modification: {} (from {})", id, mod.getModId());
        return prior != null ? RegistrationResult.REPLACED : RegistrationResult.REGISTERED;
    }

    @Nullable
    public static AbilityModification getAbilityModification(String abilityId) {
        if (abilityId == null) return null;
        return abilityModifications.get(normalize(abilityId));
    }

    public static Collection<AbilityModification> getAllAbilityModifications() {
        return Collections.unmodifiableCollection(abilityModifications.values());
    }

    // injected before abilities/moves load; use for shared constants or utility functions
    public static void registerHelperJs(String js) {
        if (js == null || js.isEmpty()) return;
        if (scriptsBuilt) {
            LOGGER.warn("Helper JS registered after Showdown engine started. It will not reach the battle engine until next JVM restart.");
        }
        if (!isJsBalanced(js)) {
            LOGGER.warn("Helper JS has unbalanced braces/parens/brackets, may corrupt generated Showdown scripts");
        }
        helperJs.add(js);
        LOGGER.info("Registered helper JavaScript ({} chars)", js.length());
    }

    public static List<String> getAllHelperJs() {
        return Collections.unmodifiableList(new ArrayList<>(helperJs));
    }

    public static RegistrationResult registerFieldCondition(CustomFieldCondition condition) {
        return doRegisterFieldCondition(condition, true);
    }

    public static RegistrationResult tryRegisterFieldCondition(CustomFieldCondition condition) {
        return doRegisterFieldCondition(condition, false);
    }

    private static RegistrationResult doRegisterFieldCondition(CustomFieldCondition condition, boolean allowOverwrite) {
        String id = condition.getId();
        CustomFieldCondition prior = fieldConditions.get(id);
        if (prior != null && !allowOverwrite) {
            LOGGER.warn("Rejected field condition '{}' from '{}': already registered by '{}'",
                    id, condition.getModId(), prior.getModId());
            return RegistrationResult.REJECTED;
        }
        if (prior != null) {
            LOGGER.warn("Overwriting field condition '{}' from '{}' (was from '{}')",
                    id, condition.getModId(), prior.getModId());
        }
        warnIfMalformedJs(condition.getShowdownJs(), id, condition.getModId(), "field condition");
        warnIfFrozen(condition.hasShowdownJs(), id, condition.getModId(), "field condition");

        fieldConditions.put(id, condition);
        LOGGER.info("Registered custom field condition: {} ({}) from {}",
                condition.getDisplayName(), id, condition.getModId());
        return prior != null ? RegistrationResult.REPLACED : RegistrationResult.REGISTERED;
    }

    @Nullable
    public static CustomFieldCondition getFieldCondition(String id) {
        if (id == null) return null;
        return fieldConditions.get(normalize(id));
    }

    public static Collection<CustomFieldCondition> getAllFieldConditions() {
        return Collections.unmodifiableCollection(fieldConditions.values());
    }

    public static RegistrationResult registerVolatileEffect(CustomVolatileEffect effect) {
        return doRegisterVolatileEffect(effect, true);
    }

    public static RegistrationResult tryRegisterVolatileEffect(CustomVolatileEffect effect) {
        return doRegisterVolatileEffect(effect, false);
    }

    private static RegistrationResult doRegisterVolatileEffect(CustomVolatileEffect effect, boolean allowOverwrite) {
        String id = effect.getId();
        CustomVolatileEffect prior = volatileEffects.get(id);
        if (prior != null && !allowOverwrite) {
            LOGGER.warn("Rejected volatile effect '{}' from '{}': already registered by '{}'",
                    id, effect.getModId(), prior.getModId());
            return RegistrationResult.REJECTED;
        }
        if (prior != null) {
            LOGGER.warn("Overwriting volatile effect '{}' from '{}' (was from '{}')",
                    id, effect.getModId(), prior.getModId());
        }
        warnIfMalformedJs(effect.getShowdownJs(), id, effect.getModId(), "volatile effect");
        warnIfFrozen(effect.hasShowdownJs(), id, effect.getModId(), "volatile effect");

        volatileEffects.put(id, effect);
        LOGGER.info("Registered custom volatile effect: {} ({}) from {}",
                effect.getDisplayName(), id, effect.getModId());
        return prior != null ? RegistrationResult.REPLACED : RegistrationResult.REGISTERED;
    }

    @Nullable
    public static CustomVolatileEffect getVolatileEffect(String id) {
        if (id == null) return null;
        return volatileEffects.get(normalize(id));
    }

    public static Collection<CustomVolatileEffect> getAllVolatileEffects() {
        return Collections.unmodifiableCollection(volatileEffects.values());
    }

    public static RegistrationResult registerSideCondition(CustomSideCondition condition) {
        return doRegisterSideCondition(condition, true);
    }

    public static RegistrationResult tryRegisterSideCondition(CustomSideCondition condition) {
        return doRegisterSideCondition(condition, false);
    }

    private static RegistrationResult doRegisterSideCondition(CustomSideCondition condition, boolean allowOverwrite) {
        String id = condition.getId();
        CustomSideCondition prior = sideConditions.get(id);
        if (prior != null && !allowOverwrite) {
            LOGGER.warn("Rejected side condition '{}' from '{}': already registered by '{}'",
                    id, condition.getModId(), prior.getModId());
            return RegistrationResult.REJECTED;
        }
        if (prior != null) {
            LOGGER.warn("Overwriting side condition '{}' from '{}' (was from '{}')",
                    id, condition.getModId(), prior.getModId());
        }
        warnIfMalformedJs(condition.getShowdownJs(), id, condition.getModId(), "side condition");
        warnIfFrozen(condition.hasShowdownJs(), id, condition.getModId(), "side condition");

        sideConditions.put(id, condition);
        LOGGER.info("Registered custom side condition: {} ({}) from {}",
                condition.getDisplayName(), id, condition.getModId());
        return prior != null ? RegistrationResult.REPLACED : RegistrationResult.REGISTERED;
    }

    @Nullable
    public static CustomSideCondition getSideCondition(String id) {
        if (id == null) return null;
        return sideConditions.get(normalize(id));
    }

    public static Collection<CustomSideCondition> getAllSideConditions() {
        return Collections.unmodifiableCollection(sideConditions.values());
    }

    public static int getTotalRegistrations() {
        return abilities.size() + moves.size() + moveModifications.size() + abilityModifications.size()
             + fieldConditions.size() + volatileEffects.size() + sideConditions.size();
    }

    public static void logRegistrationSummary() {
        LOGGER.info("Custom content registered: {} abilities, {} moves, {} move mods, {} ability mods, {} field conditions, {} volatile effects, {} side conditions, {} helper scripts",
                abilities.size(), moves.size(), moveModifications.size(), abilityModifications.size(), fieldConditions.size(),
                volatileEffects.size(), sideConditions.size(), helperJs.size());
    }

    public static boolean hasShowdownJs() {
        for (CustomAbility ability : abilities.values()) if (ability.hasShowdownJs()) return true;
        for (CustomMove move : moves.values()) if (move.hasShowdownJs()) return true;
        for (MoveModification mod : moveModifications.values()) if (mod.hasShowdownJs()) return true;
        for (AbilityModification mod : abilityModifications.values()) if (mod.hasShowdownJs()) return true;
        for (CustomVolatileEffect effect : volatileEffects.values()) if (effect.hasShowdownJs()) return true;
        for (CustomSideCondition condition : sideConditions.values()) if (condition.hasShowdownJs()) return true;
        for (CustomFieldCondition fc : fieldConditions.values()) if (fc.hasShowdownJs()) return true;
        return !helperJs.isEmpty();
    }

    // called from GraalShowdownServiceMixin once the Showdown engine is about to start.
    // subsequent registrations still succeed but their showdownJs cannot reach Showdown.
    public static void markScriptsBuilt() {
        scriptsBuilt = true;
    }

    public static boolean areScriptsBuilt() {
        return scriptsBuilt;
    }

    private static String normalize(String id) {
        return id.toLowerCase().replace(" ", "").replace("_", "");
    }

    private static void warnIfNumCollision(int num, String newId, String modId,
                                           Map<Integer, String> usedNums, String kind) {
        if (num == 0) return;
        String prior = usedNums.get(num);
        if (prior != null && !prior.equals(newId)) {
            LOGGER.warn("Num {} for {} '{}' from '{}' collides with existing {} '{}'. Pick a distinct num (>= 9000) per consumer to avoid Showdown lookup ambiguity.",
                    num, kind, newId, modId, kind, prior);
        }
    }

    private static void warnIfMalformedJs(@Nullable String js, String id, String modId, String kind) {
        if (js == null || js.isEmpty()) return;
        if (!isJsBalanced(js)) {
            LOGGER.warn("{} '{}' from '{}' has unbalanced braces/parens/brackets in showdownJs; the generated Showdown script may not parse",
                    kind, id, modId);
        }
        if (js.trim().endsWith(",")) {
            LOGGER.warn("{} '{}' from '{}' has a trailing comma in showdownJs", kind, id, modId);
        }
    }

    private static void warnIfFrozen(boolean hasJs, String id, String modId, String kind) {
        if (!hasJs || !scriptsBuilt) return;
        LOGGER.warn("{} '{}' from '{}' registered after the Showdown engine started. Its showdownJs will not reach the battle engine until next JVM restart; register from FMLCommonSetupEvent instead.",
                kind, id, modId);
    }

    // cheap balance check that skips quoted strings (single, double, and template literals).
    // catches the bulk of accidental syntax errors without trying to be a real JS parser.
    private static boolean isJsBalanced(String js) {
        int braces = 0, parens = 0, brackets = 0;
        boolean inString = false;
        char stringChar = 0;
        for (int i = 0; i < js.length(); i++) {
            char c = js.charAt(i);
            if (inString) {
                if (c == '\\' && i + 1 < js.length()) { i++; continue; }
                if (c == stringChar) inString = false;
                continue;
            }
            switch (c) {
                case '"', '\'', '`' -> { inString = true; stringChar = c; }
                case '{' -> braces++;
                case '}' -> braces--;
                case '(' -> parens++;
                case ')' -> parens--;
                case '[' -> brackets++;
                case ']' -> brackets--;
                default -> {}
            }
            if (braces < 0 || parens < 0 || brackets < 0) return false;
        }
        return braces == 0 && parens == 0 && brackets == 0;
    }
}
