package com.newbulaco.showdown.client.battle;

import com.newbulaco.showdown.api.ShowdownAPI;
import com.newbulaco.showdown.api.content.CustomVolatileEffect;
import com.newbulaco.showdown.network.packets.VolatileEffectPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public class ClientVolatileEffectManager {

    private static ClientVolatileEffectManager instance;

    // battle ID -> pokemon ID -> set of active effects
    private final Map<UUID, Map<UUID, Set<String>>> battleEffects = new ConcurrentHashMap<>();

    // battle ID -> pokemon ID -> isAlly flag
    private final Map<UUID, Map<UUID, Boolean>> pokemonSides = new ConcurrentHashMap<>();

    private UUID currentBattleId = null;

    private ClientVolatileEffectManager() {}

    public static ClientVolatileEffectManager getInstance() {
        if (instance == null) {
            instance = new ClientVolatileEffectManager();
        }
        return instance;
    }

    public void handlePacket(VolatileEffectPacket packet) {
        UUID battleId = packet.getBattleId();
        UUID pokemonId = packet.getPokemonId();
        String effectId = packet.getEffectId();

        currentBattleId = battleId;

        battleEffects.computeIfAbsent(battleId, k -> new ConcurrentHashMap<>());
        pokemonSides.computeIfAbsent(battleId, k -> new ConcurrentHashMap<>());

        Map<UUID, Set<String>> pokemonEffects = battleEffects.get(battleId);
        Map<UUID, Boolean> sides = pokemonSides.get(battleId);

        sides.put(pokemonId, packet.isAlly());

        switch (packet.getAction()) {
            case ADD:
                pokemonEffects.computeIfAbsent(pokemonId, k -> ConcurrentHashMap.newKeySet());
                pokemonEffects.get(pokemonId).add(effectId);
                break;

            case REMOVE:
                if (pokemonEffects.containsKey(pokemonId)) {
                    pokemonEffects.get(pokemonId).remove(effectId);
                }
                break;

            case CLEAR_ALL:
                pokemonEffects.remove(pokemonId);
                break;
        }
    }

    public Set<String> getAllyEffects() {
        if (currentBattleId == null) return Collections.emptySet();

        Map<UUID, Set<String>> pokemonEffects = battleEffects.get(currentBattleId);
        Map<UUID, Boolean> sides = pokemonSides.get(currentBattleId);

        if (pokemonEffects == null || sides == null) return Collections.emptySet();

        Set<String> allyEffects = new HashSet<>();
        for (Map.Entry<UUID, Boolean> entry : sides.entrySet()) {
            if (entry.getValue()) {
                Set<String> effects = pokemonEffects.get(entry.getKey());
                if (effects != null) {
                    allyEffects.addAll(effects);
                }
            }
        }
        return allyEffects;
    }

    public Set<String> getOpponentEffects() {
        if (currentBattleId == null) return Collections.emptySet();

        Map<UUID, Set<String>> pokemonEffects = battleEffects.get(currentBattleId);
        Map<UUID, Boolean> sides = pokemonSides.get(currentBattleId);

        if (pokemonEffects == null || sides == null) return Collections.emptySet();

        Set<String> opponentEffects = new HashSet<>();
        for (Map.Entry<UUID, Boolean> entry : sides.entrySet()) {
            if (!entry.getValue()) {
                Set<String> effects = pokemonEffects.get(entry.getKey());
                if (effects != null) {
                    opponentEffects.addAll(effects);
                }
            }
        }
        return opponentEffects;
    }

    public Set<String> getEffects(UUID battleId, UUID pokemonId) {
        Map<UUID, Set<String>> pokemonEffects = battleEffects.get(battleId);
        if (pokemonEffects == null) return Collections.emptySet();

        Set<String> effects = pokemonEffects.get(pokemonId);
        return effects != null ? new HashSet<>(effects) : Collections.emptySet();
    }

    public boolean hasEffect(UUID battleId, UUID pokemonId, String effectId) {
        Set<String> effects = getEffects(battleId, pokemonId);
        return effects.contains(effectId);
    }

    public void clearBattle(UUID battleId) {
        battleEffects.remove(battleId);
        pokemonSides.remove(battleId);
        if (battleId.equals(currentBattleId)) {
            currentBattleId = null;
        }
    }

    public void clearAll() {
        battleEffects.clear();
        pokemonSides.clear();
        currentBattleId = null;
    }

    /**
     * checks API registry for custom effects first, then falls back to built-in names.
     */
    public static MutableComponent getEffectDisplayName(String effectId) {
        if (effectId == null) return Component.empty();

        CustomVolatileEffect custom = ShowdownAPI.getVolatileEffect(effectId);
        if (custom != null) {
            return Component.literal(custom.getDisplayName());
        }

        // Wheel of Dharma adapted type formats:
        // wheelofdharmaadaptedfire, wheelofdharmaadapted:fire, wheelofdharmaadapted
        String lower = effectId.toLowerCase();
        if (lower.startsWith("wheelofdharmaadapted")) {
            String remainder = lower.substring("wheelofdharmaadapted".length());
            if (remainder.startsWith(":")) {
                String type = remainder.substring(1);
                return Component.literal("Adapted: " + formatTypeName(type));
            } else if (!remainder.isEmpty()) {
                return Component.literal("Adapted: " + formatTypeName(remainder));
            }
            return Component.literal("Adapted");
        }

        return switch (lower) {
            case "leechseed" -> Component.translatable("cobblemon_showdown.battle_overlay.effect.leechseed");
            case "confusion" -> Component.translatable("cobblemon_showdown.battle_overlay.effect.confusion");
            case "substitute" -> Component.translatable("cobblemon_showdown.battle_overlay.effect.substitute");
            case "focusenergy" -> Component.translatable("cobblemon_showdown.battle_overlay.effect.focusenergy");
            case "taunt" -> Component.translatable("cobblemon_showdown.battle_overlay.effect.taunt");
            case "encore" -> Component.translatable("cobblemon_showdown.battle_overlay.effect.encore");
            case "disable" -> Component.translatable("cobblemon_showdown.battle_overlay.effect.disable");
            case "torment" -> Component.translatable("cobblemon_showdown.battle_overlay.effect.torment");
            case "yawn" -> Component.translatable("cobblemon_showdown.battle_overlay.effect.yawn");
            case "curse" -> Component.translatable("cobblemon_showdown.battle_overlay.effect.curse");
            case "nightmare" -> Component.translatable("cobblemon_showdown.battle_overlay.effect.nightmare");
            case "attract" -> Component.translatable("cobblemon_showdown.battle_overlay.effect.attract");
            case "embargo" -> Component.translatable("cobblemon_showdown.battle_overlay.effect.embargo");
            case "healblock" -> Component.translatable("cobblemon_showdown.battle_overlay.effect.healblock");
            case "ingrain" -> Component.translatable("cobblemon_showdown.battle_overlay.effect.ingrain");
            case "aquaring" -> Component.translatable("cobblemon_showdown.battle_overlay.effect.aquaring");
            case "magnetrise" -> Component.translatable("cobblemon_showdown.battle_overlay.effect.magnetrise");
            case "perishsong" -> Component.translatable("cobblemon_showdown.battle_overlay.effect.perishsong");
            case "destinybond" -> Component.translatable("cobblemon_showdown.battle_overlay.effect.destinybond");
            case "protect" -> Component.translatable("cobblemon_showdown.battle_overlay.effect.protect");
            case "endure" -> Component.translatable("cobblemon_showdown.battle_overlay.effect.endure");
            case "flinch" -> Component.translatable("cobblemon_showdown.battle_overlay.effect.flinch");
            case "trapped" -> Component.translatable("cobblemon_showdown.battle_overlay.effect.trapped");
            case "partiallytrapped" -> Component.translatable("cobblemon_showdown.battle_overlay.effect.partiallytrapped");
            default -> Component.literal(formatEffectName(effectId));
        };
    }

    private static String formatTypeName(String type) {
        if (type == null || type.isEmpty()) return "Unknown";
        return Character.toUpperCase(type.charAt(0)) + type.substring(1).toLowerCase();
    }

    // converts camelCase or lowercase to Title Case
    private static String formatEffectName(String effectId) {
        if (effectId == null || effectId.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < effectId.length(); i++) {
            char c = effectId.charAt(i);
            if (i == 0) {
                result.append(Character.toUpperCase(c));
            } else if (Character.isUpperCase(c)) {
                result.append(' ').append(c);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
