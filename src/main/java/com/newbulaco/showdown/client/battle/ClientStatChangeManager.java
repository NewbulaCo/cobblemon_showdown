package com.newbulaco.showdown.client.battle;

import com.newbulaco.showdown.network.packets.StatChangePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public class ClientStatChangeManager {

    private static ClientStatChangeManager instance;

    // battle ID -> pokemon ID -> stat -> stages
    private final Map<UUID, Map<UUID, Map<String, Integer>>> battleStats = new ConcurrentHashMap<>();

    // battle ID -> pokemon ID -> isAlly flag
    private final Map<UUID, Map<UUID, Boolean>> pokemonSides = new ConcurrentHashMap<>();

    private UUID currentBattleId = null;

    private ClientStatChangeManager() {}

    public static ClientStatChangeManager getInstance() {
        if (instance == null) {
            instance = new ClientStatChangeManager();
        }
        return instance;
    }

    public void handlePacket(StatChangePacket packet) {
        UUID battleId = packet.getBattleId();
        UUID pokemonId = packet.getPokemonId();

        currentBattleId = battleId;

        battleStats.computeIfAbsent(battleId, k -> new ConcurrentHashMap<>());
        pokemonSides.computeIfAbsent(battleId, k -> new ConcurrentHashMap<>());

        Map<UUID, Map<String, Integer>> pokemonStats = battleStats.get(battleId);
        Map<UUID, Boolean> sides = pokemonSides.get(battleId);

        sides.put(pokemonId, packet.isAlly());
        pokemonStats.put(pokemonId, new ConcurrentHashMap<>(packet.getStatStages()));
    }

    public Map<String, Integer> getAllyStatStages() {
        if (currentBattleId == null) return Collections.emptyMap();

        Map<UUID, Map<String, Integer>> pokemonStats = battleStats.get(currentBattleId);
        Map<UUID, Boolean> sides = pokemonSides.get(currentBattleId);

        if (pokemonStats == null || sides == null) return Collections.emptyMap();

        for (Map.Entry<UUID, Boolean> entry : sides.entrySet()) {
            if (entry.getValue()) {
                Map<String, Integer> stats = pokemonStats.get(entry.getKey());
                if (stats != null) {
                    return new HashMap<>(stats);
                }
            }
        }
        return Collections.emptyMap();
    }

    public Map<String, Integer> getOpponentStatStages() {
        if (currentBattleId == null) return Collections.emptyMap();

        Map<UUID, Map<String, Integer>> pokemonStats = battleStats.get(currentBattleId);
        Map<UUID, Boolean> sides = pokemonSides.get(currentBattleId);

        if (pokemonStats == null || sides == null) return Collections.emptyMap();

        for (Map.Entry<UUID, Boolean> entry : sides.entrySet()) {
            if (!entry.getValue()) {
                Map<String, Integer> stats = pokemonStats.get(entry.getKey());
                if (stats != null) {
                    return new HashMap<>(stats);
                }
            }
        }
        return Collections.emptyMap();
    }

    // per-pokemon accessor for doubles, where each active slot has its own stages.
    // the singles getters above pick first-on-side, which is fine when there's only one.
    public Map<String, Integer> getStatStagesByPokemon(UUID pokemonId) {
        if (currentBattleId == null) return Collections.emptyMap();

        Map<UUID, Map<String, Integer>> pokemonStats = battleStats.get(currentBattleId);
        if (pokemonStats == null) return Collections.emptyMap();

        Map<String, Integer> stats = pokemonStats.get(pokemonId);
        return stats != null ? new HashMap<>(stats) : Collections.emptyMap();
    }

    public List<UUID> getTrackedPokemonOnSide(boolean ally) {
        if (currentBattleId == null) return Collections.emptyList();

        Map<UUID, Boolean> sides = pokemonSides.get(currentBattleId);
        if (sides == null) return Collections.emptyList();

        List<UUID> result = new ArrayList<>();
        for (Map.Entry<UUID, Boolean> entry : sides.entrySet()) {
            if (entry.getValue() == ally) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public void clearBattle(UUID battleId) {
        battleStats.remove(battleId);
        pokemonSides.remove(battleId);
        if (battleId.equals(currentBattleId)) {
            currentBattleId = null;
        }
    }

    public void clearAll() {
        battleStats.clear();
        pokemonSides.clear();
        currentBattleId = null;
    }

    public static MutableComponent getStatDisplayName(String statAbbr) {
        if (statAbbr == null) return Component.empty();
        return switch (statAbbr.toLowerCase()) {
            case "atk" -> Component.translatable("cobblemon_showdown.stat.attack.short");
            case "def" -> Component.translatable("cobblemon_showdown.stat.defence.short");
            case "spa" -> Component.translatable("cobblemon_showdown.stat.special_attack.short");
            case "spd" -> Component.translatable("cobblemon_showdown.stat.special_defence.short");
            case "spe" -> Component.translatable("cobblemon_showdown.stat.speed.short");
            case "accuracy" -> Component.translatable("cobblemon_showdown.stat.accuracy.short");
            case "evasion" -> Component.translatable("cobblemon_showdown.stat.evasion.short");
            default -> Component.literal(statAbbr);
        };
    }
}
