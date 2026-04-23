package com.newbulaco.showdown.client.battle;

import com.newbulaco.showdown.api.ShowdownAPI;
import com.newbulaco.showdown.api.content.CustomSideCondition;
import com.newbulaco.showdown.network.packets.SideConditionPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public class ClientSideConditionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("CobblemonShowdown");

    private static ClientSideConditionManager instance;

    // battle ID -> isAllySide -> map of condition ID to turns remaining
    private final Map<UUID, Map<Boolean, Map<String, Integer>>> battleConditions = new ConcurrentHashMap<>();

    private UUID currentBattleId = null;

    private ClientSideConditionManager() {}

    public static ClientSideConditionManager getInstance() {
        if (instance == null) {
            instance = new ClientSideConditionManager();
        }
        return instance;
    }

    public void handlePacket(SideConditionPacket packet) {
        UUID battleId = packet.getBattleId();
        boolean isAllySide = packet.isAllySide();
        String conditionId = packet.getConditionId();

        LOGGER.debug("SideConditionPacket received: action={}, side={}, condition={}",
                packet.getAction(), isAllySide ? "ALLY" : "OPPONENT", conditionId);

        currentBattleId = battleId;

        battleConditions.computeIfAbsent(battleId, k -> new ConcurrentHashMap<>());
        Map<Boolean, Map<String, Integer>> sideConditions = battleConditions.get(battleId);
        sideConditions.computeIfAbsent(isAllySide, k -> new ConcurrentHashMap<>());

        Map<String, Integer> conditions = sideConditions.get(isAllySide);

        switch (packet.getAction()) {
            case ADD:
                conditions.put(conditionId, packet.getTurnsRemaining());
                LOGGER.debug("Added condition {} to {} side", conditionId, isAllySide ? "ALLY" : "OPPONENT");
                break;

            case REMOVE:
                conditions.remove(conditionId);
                LOGGER.debug("Removed condition {} from {} side", conditionId, isAllySide ? "ALLY" : "OPPONENT");
                break;

            case CLEAR_ALL:
                conditions.clear();
                LOGGER.debug("Cleared all conditions from {} side", isAllySide ? "ALLY" : "OPPONENT");
                break;
        }
    }

    public Map<String, Integer> getAllyConditions() {
        if (currentBattleId == null) return Collections.emptyMap();

        Map<Boolean, Map<String, Integer>> sideConditions = battleConditions.get(currentBattleId);
        if (sideConditions == null) return Collections.emptyMap();

        Map<String, Integer> conditions = sideConditions.get(true);
        return conditions != null ? new HashMap<>(conditions) : Collections.emptyMap();
    }

    public Map<String, Integer> getOpponentConditions() {
        if (currentBattleId == null) return Collections.emptyMap();

        Map<Boolean, Map<String, Integer>> sideConditions = battleConditions.get(currentBattleId);
        if (sideConditions == null) return Collections.emptyMap();

        Map<String, Integer> conditions = sideConditions.get(false);
        return conditions != null ? new HashMap<>(conditions) : Collections.emptyMap();
    }

    public boolean allyHasCondition(String conditionId) {
        return getAllyConditions().containsKey(conditionId);
    }

    public boolean opponentHasCondition(String conditionId) {
        return getOpponentConditions().containsKey(conditionId);
    }

    public void clearBattle(UUID battleId) {
        battleConditions.remove(battleId);
        if (battleId.equals(currentBattleId)) {
            currentBattleId = null;
        }
    }

    public void clearAll() {
        battleConditions.clear();
        currentBattleId = null;
    }

    public static MutableComponent getConditionDisplayName(String conditionId) {
        if (conditionId == null) return Component.empty();

        CustomSideCondition custom = ShowdownAPI.getSideCondition(conditionId);
        if (custom != null) {
            return Component.literal(custom.getDisplayName());
        }

        return switch (conditionId.toLowerCase()) {
            case "safeguard" -> Component.translatable("cobblemon_showdown.battle_overlay.condition.safeguard");
            case "lightscreen" -> Component.translatable("cobblemon_showdown.battle_overlay.condition.lightscreen");
            case "reflect" -> Component.translatable("cobblemon_showdown.battle_overlay.condition.reflect");
            case "auroraveil" -> Component.translatable("cobblemon_showdown.battle_overlay.condition.auroraveil");
            case "tailwind" -> Component.translatable("cobblemon_showdown.battle_overlay.condition.tailwind");
            case "mist" -> Component.translatable("cobblemon_showdown.battle_overlay.condition.mist");
            case "luckychant" -> Component.translatable("cobblemon_showdown.battle_overlay.condition.luckychant");
            case "stealthrock" -> Component.translatable("cobblemon_showdown.battle_overlay.condition.stealthrock");
            case "spikes" -> Component.translatable("cobblemon_showdown.battle_overlay.condition.spikes");
            case "toxicspikes" -> Component.translatable("cobblemon_showdown.battle_overlay.condition.toxicspikes");
            case "stickyweb" -> Component.translatable("cobblemon_showdown.battle_overlay.condition.stickyweb");
            case "gmaxsteelsurge" -> Component.translatable("cobblemon_showdown.battle_overlay.condition.gmaxsteelsurge");
            case "gmaxcannonade" -> Component.translatable("cobblemon_showdown.battle_overlay.condition.gmaxcannonade");
            case "gmaxvinelash" -> Component.translatable("cobblemon_showdown.battle_overlay.condition.gmaxvinelash");
            case "gmaxwildfire" -> Component.translatable("cobblemon_showdown.battle_overlay.condition.gmaxwildfire");
            case "gmaxvolcalith" -> Component.translatable("cobblemon_showdown.battle_overlay.condition.gmaxvolcalith");
            case "wideguard" -> Component.translatable("cobblemon_showdown.battle_overlay.condition.wideguard");
            case "quickguard" -> Component.translatable("cobblemon_showdown.battle_overlay.condition.quickguard");
            case "matblock" -> Component.translatable("cobblemon_showdown.battle_overlay.condition.matblock");
            case "craftyshield" -> Component.translatable("cobblemon_showdown.battle_overlay.condition.craftyshield");
            default -> Component.literal(formatConditionName(conditionId));
        };
    }

    public static int getConditionColor(String conditionId) {
        if (conditionId == null) return 0xFFFFFFFF;

        CustomSideCondition custom = ShowdownAPI.getSideCondition(conditionId);
        if (custom != null) {
            return custom.getColor();
        }

        return switch (conditionId.toLowerCase()) {
            case "lightscreen" -> 0xFFFFFF55;
            case "reflect" -> 0xFFFF8855;
            case "auroraveil" -> 0xFF55FFFF;
            case "safeguard" -> 0xFF55FF55;
            case "mist" -> 0xFFAADDFF;
            case "luckychant" -> 0xFFFFAAFF;
            case "tailwind" -> 0xFF88CCFF;
            case "stealthrock" -> 0xFFAA8866;
            case "spikes" -> 0xFF888888;
            case "toxicspikes" -> 0xFFAA55AA;
            case "stickyweb" -> 0xFFCCCC55;
            case "wideguard", "quickguard", "matblock", "craftyshield" -> 0xFF88FF88;
            default -> 0xFFFFFFFF;
        };
    }

    public static boolean isHazard(String conditionId) {
        if (conditionId == null) return false;
        return switch (conditionId.toLowerCase()) {
            case "stealthrock", "spikes", "toxicspikes", "stickyweb",
                 "gmaxsteelsurge", "gmaxcannonade", "gmaxvinelash",
                 "gmaxwildfire", "gmaxvolcalith" -> true;
            default -> false;
        };
    }

    // converts camelCase condition ID to spaced display name
    private static String formatConditionName(String conditionId) {
        if (conditionId == null || conditionId.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < conditionId.length(); i++) {
            char c = conditionId.charAt(i);
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
