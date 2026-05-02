package com.newbulaco.showdown.battle;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.interpreter.BattleContext;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.newbulaco.showdown.CobblemonShowdown;
import com.newbulaco.showdown.network.ShowdownNetwork;
import com.newbulaco.showdown.network.packets.BattleClearPacket;
import com.newbulaco.showdown.network.packets.FieldStatusPacket;
import com.newbulaco.showdown.network.packets.PartyStatusPacket;
import com.newbulaco.showdown.network.packets.SideConditionPacket;
import com.newbulaco.showdown.network.packets.StatChangePacket;
import com.newbulaco.showdown.network.packets.VolatileEffectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;
import kotlin.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VolatileEffectTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger(VolatileEffectTracker.class);

    private static VolatileEffectTracker instance;

    // battle ID -> pokemon UUID -> active effect IDs
    private final Map<UUID, Map<UUID, Set<String>>> trackedEffects = new ConcurrentHashMap<>();

    // battle ID -> pokemon UUID -> stat -> stages
    private final Map<UUID, Map<UUID, Map<String, Integer>>> trackedStatStages = new ConcurrentHashMap<>();

    // battle ID -> pokemon UUID -> isAlly (relative to player 1)
    private final Map<UUID, Map<UUID, Boolean>> pokemonSides = new ConcurrentHashMap<>();

    // battle ID -> player UUIDs in the battle
    private final Map<UUID, Set<UUID>> battlePlayers = new ConcurrentHashMap<>();

    // battle ID -> isPlayerOneSide -> active side condition IDs
    private final Map<UUID, Map<Boolean, Set<String>>> trackedSideConditions = new ConcurrentHashMap<>();

    // battle ID -> current weather (empty string = no weather)
    private final Map<UUID, String> trackedWeather = new ConcurrentHashMap<>();

    // battle ID -> current terrain (empty string = no terrain)
    private final Map<UUID, String> trackedTerrain = new ConcurrentHashMap<>();

    // battle ID -> active room effects (trickroom, wonderroom, magicroom)
    private final Map<UUID, Set<String>> trackedRooms = new ConcurrentHashMap<>();

    // battle ID -> isPlayerOneSide -> party slot status strings (for change detection)
    private final Map<UUID, Map<Boolean, List<String>>> trackedPartyStatus = new ConcurrentHashMap<>();

    // battle ID -> side -> previously active pokemon UUIDs (for detecting switches)
    private final Map<UUID, Map<Boolean, Set<UUID>>> previouslyActivePokemon = new ConcurrentHashMap<>();

    private VolatileEffectTracker() {}

    public static VolatileEffectTracker getInstance() {
        if (instance == null) {
            instance = new VolatileEffectTracker();
        }
        return instance;
    }

    public void initialize() {
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(
                Priority.NORMAL,
                event -> {
                    onBattleStarted(event.getBattle());
                    return Unit.INSTANCE;
                }
        );

        CobblemonEvents.BATTLE_VICTORY.subscribe(
                Priority.NORMAL,
                event -> {
                    onBattleEnded(event.getBattle().getBattleId());
                    return Unit.INSTANCE;
                }
        );

        CobblemonEvents.BATTLE_FLED.subscribe(
                Priority.NORMAL,
                event -> {
                    onBattleEnded(event.getBattle().getBattleId());
                    return Unit.INSTANCE;
                }
        );

        LOGGER.info("[Showdown] VolatileEffectTracker initialized");
    }

    private void onBattleStarted(PokemonBattle battle) {
        UUID battleId = battle.getBattleId();
        trackedEffects.put(battleId, new ConcurrentHashMap<>());
        trackedStatStages.put(battleId, new ConcurrentHashMap<>());
        pokemonSides.put(battleId, new ConcurrentHashMap<>());
        battlePlayers.put(battleId, ConcurrentHashMap.newKeySet());

        Map<Boolean, Set<String>> sideConditions = new ConcurrentHashMap<>();
        sideConditions.put(true, ConcurrentHashMap.newKeySet());
        sideConditions.put(false, ConcurrentHashMap.newKeySet());
        trackedSideConditions.put(battleId, sideConditions);

        trackedWeather.put(battleId, "");
        trackedTerrain.put(battleId, "");
        trackedRooms.put(battleId, ConcurrentHashMap.newKeySet());

        Map<Boolean, List<String>> partyStatus = new ConcurrentHashMap<>();
        partyStatus.put(true, new ArrayList<>());
        partyStatus.put(false, new ArrayList<>());
        trackedPartyStatus.put(battleId, partyStatus);

        Map<Boolean, Set<UUID>> activePokemon = new ConcurrentHashMap<>();
        activePokemon.put(true, ConcurrentHashMap.newKeySet());
        activePokemon.put(false, ConcurrentHashMap.newKeySet());
        previouslyActivePokemon.put(battleId, activePokemon);

        for (BattleActor actor : battle.getActors()) {
            if (actor instanceof PlayerBattleActor playerActor) {
                battlePlayers.get(battleId).add(playerActor.getUuid());
            }
        }
    }

    private void onBattleEnded(UUID battleId) {
        // wild and npc battles don't go through CobblemonBattleListener's clear-packet path,
        // so do it here to keep client side conditions, volatiles, etc. from leaking across battles
        sendClearPacketToTrackedPlayers(battleId);

        trackedEffects.remove(battleId);
        trackedStatStages.remove(battleId);
        pokemonSides.remove(battleId);
        battlePlayers.remove(battleId);
        trackedSideConditions.remove(battleId);
        trackedWeather.remove(battleId);
        trackedTerrain.remove(battleId);
        trackedRooms.remove(battleId);
        trackedPartyStatus.remove(battleId);
        previouslyActivePokemon.remove(battleId);
        weatherStartTurn.remove(battleId);
        weatherDuration.remove(battleId);
        terrainStartTurn.remove(battleId);
        terrainDuration.remove(battleId);
        roomStartTurns.remove(battleId);
    }

    private void sendClearPacketToTrackedPlayers(UUID battleId) {
        Set<UUID> players = battlePlayers.get(battleId);
        if (players == null || players.isEmpty()) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        BattleClearPacket packet = new BattleClearPacket(battleId);
        for (UUID playerUuid : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player != null) {
                ShowdownNetwork.sendToPlayer(packet, player);
            }
        }
    }

    public void tick() {
        var battleRegistry = Cobblemon.INSTANCE.getBattleRegistry();

        // copy to avoid ConcurrentModificationException
        Set<UUID> battleIds = new HashSet<>(trackedEffects.keySet());

        for (UUID battleId : battleIds) {
            PokemonBattle battle = battleRegistry.getBattle(battleId);
            if (battle == null) {
                onBattleEnded(battleId);
                continue;
            }

            checkSide(battle, battle.getSide1(), true);
            checkSide(battle, battle.getSide2(), false);

            checkSideConditions(battle, battle.getSide1(), true);
            checkSideConditions(battle, battle.getSide2(), false);

            checkFieldConditions(battle);

            checkPartyStatus(battle, battle.getSide1(), true);
            checkPartyStatus(battle, battle.getSide2(), false);
        }
    }

    // weather, terrain, and rooms are on battle.contextManager, not on individual Pokemon
    private void checkFieldConditions(PokemonBattle battle) {
        checkWeatherConditions(battle);
        checkTerrainConditions(battle);
        checkRoomConditions(battle);
    }

    private static final int DEFAULT_WEATHER_DURATION = 5;
    private static final int EXTENDED_WEATHER_DURATION = 8;
    private static final int DEFAULT_TERRAIN_DURATION = 5;
    private static final int EXTENDED_TERRAIN_DURATION = 8;
    private static final int DEFAULT_ROOM_DURATION = 5;

    // weather-extending items (Showdown IDs)
    private static final String HEAT_ROCK = "heatrock";
    private static final String DAMP_ROCK = "damprock";
    private static final String SMOOTH_ROCK = "smoothrock";
    private static final String ICY_ROCK = "icyrock";
    private static final String TERRAIN_EXTENDER = "terrainextender";

    private final Map<UUID, Integer> weatherStartTurn = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> weatherDuration = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> terrainStartTurn = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> terrainDuration = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> roomStartTurns = new ConcurrentHashMap<>();

    private void checkWeatherConditions(PokemonBattle battle) {
        UUID battleId = battle.getBattleId();
        String previousWeather = trackedWeather.getOrDefault(battleId, "");
        String currentWeather = "";
        int contextTurn = -1;
        BattlePokemon weatherOrigin = null;

        var weatherContexts = battle.getContextManager().get(BattleContext.Type.WEATHER);
        if (weatherContexts != null && !weatherContexts.isEmpty()) {
            // only one weather can be active at a time
            for (BattleContext context : weatherContexts) {
                currentWeather = context.getId().toLowerCase();
                contextTurn = context.getTurn();
                weatherOrigin = context.getOrigin();
                break;
            }
        }

        int currentBattleTurn = battle.getTurn();

        if (!currentWeather.equals(previousWeather)) {
            trackedWeather.put(battleId, currentWeather);

            if (currentWeather.isEmpty()) {
                weatherStartTurn.remove(battleId);
                weatherDuration.remove(battleId);
                sendFieldStatusPacket(battle, FieldStatusPacket.FieldType.WEATHER,
                        FieldStatusPacket.Action.CLEAR, previousWeather, -1);
                LOGGER.debug("Weather cleared in battle {}: was {}", battleId, previousWeather);
            } else {
                weatherStartTurn.put(battleId, contextTurn > 0 ? contextTurn : currentBattleTurn);
                int duration = getWeatherDuration(currentWeather, weatherOrigin);
                weatherDuration.put(battleId, duration);
                int remainingTurns = calculateRemainingTurns(contextTurn, currentBattleTurn, duration);
                sendFieldStatusPacket(battle, FieldStatusPacket.FieldType.WEATHER,
                        FieldStatusPacket.Action.SET, currentWeather, remainingTurns);
                LOGGER.debug("Weather changed in battle {}: {} -> {} (turns: {}, duration: {})",
                        battleId, previousWeather, currentWeather, remainingTurns, duration);
            }
        } else if (!currentWeather.isEmpty()) {
            int startTurn = weatherStartTurn.getOrDefault(battleId, currentBattleTurn);
            int duration = weatherDuration.getOrDefault(battleId, DEFAULT_WEATHER_DURATION);
            int remainingTurns = calculateRemainingTurns(startTurn, currentBattleTurn, duration);

            sendFieldStatusPacket(battle, FieldStatusPacket.FieldType.WEATHER,
                    FieldStatusPacket.Action.SET, currentWeather, remainingTurns);
        }
    }

    private int getWeatherDuration(String weather, BattlePokemon origin) {
        if (origin == null) return DEFAULT_WEATHER_DURATION;

        try {
            String heldItem = origin.getHeldItemManager().showdownId(origin);
            if (heldItem == null || heldItem.isEmpty()) return DEFAULT_WEATHER_DURATION;

            String itemLower = heldItem.toLowerCase();
            String weatherLower = weather.toLowerCase();

            if ((weatherLower.contains("sun") || weatherLower.equals("sunnyday") || weatherLower.equals("harshsunshine"))
                    && itemLower.equals(HEAT_ROCK)) {
                LOGGER.debug("Weather extended by Heat Rock");
                return EXTENDED_WEATHER_DURATION;
            }
            if ((weatherLower.contains("rain") || weatherLower.equals("raindance") || weatherLower.equals("heavyrain"))
                    && itemLower.equals(DAMP_ROCK)) {
                LOGGER.debug("Weather extended by Damp Rock");
                return EXTENDED_WEATHER_DURATION;
            }
            if (weatherLower.contains("sandstorm") && itemLower.equals(SMOOTH_ROCK)) {
                LOGGER.debug("Weather extended by Smooth Rock");
                return EXTENDED_WEATHER_DURATION;
            }
            if ((weatherLower.contains("hail") || weatherLower.contains("snow"))
                    && itemLower.equals(ICY_ROCK)) {
                LOGGER.debug("Weather extended by Icy Rock");
                return EXTENDED_WEATHER_DURATION;
            }
        } catch (Exception e) {
            LOGGER.debug("Error checking weather-extending item: {}", e.getMessage());
        }

        return DEFAULT_WEATHER_DURATION;
    }

    private int calculateRemainingTurns(int startTurn, int currentTurn, int duration) {
        if (startTurn <= 0) return duration;
        int elapsed = currentTurn - startTurn;
        int remaining = duration - elapsed;
        return Math.max(0, remaining);
    }

    private void checkTerrainConditions(PokemonBattle battle) {
        UUID battleId = battle.getBattleId();
        String previousTerrain = trackedTerrain.getOrDefault(battleId, "");
        String currentTerrain = "";
        int contextTurn = -1;
        BattlePokemon terrainOrigin = null;

        var terrainContexts = battle.getContextManager().get(BattleContext.Type.TERRAIN);
        if (terrainContexts != null && !terrainContexts.isEmpty()) {
            for (BattleContext context : terrainContexts) {
                currentTerrain = context.getId().toLowerCase();
                contextTurn = context.getTurn();
                terrainOrigin = context.getOrigin();
                break;
            }
        }

        int currentBattleTurn = battle.getTurn();

        if (!currentTerrain.equals(previousTerrain)) {
            trackedTerrain.put(battleId, currentTerrain);

            if (currentTerrain.isEmpty()) {
                terrainStartTurn.remove(battleId);
                terrainDuration.remove(battleId);
                sendFieldStatusPacket(battle, FieldStatusPacket.FieldType.TERRAIN,
                        FieldStatusPacket.Action.CLEAR, previousTerrain, -1);
                LOGGER.debug("Terrain cleared in battle {}: was {}", battleId, previousTerrain);
            } else {
                terrainStartTurn.put(battleId, contextTurn > 0 ? contextTurn : currentBattleTurn);
                int duration = getTerrainDuration(terrainOrigin);
                terrainDuration.put(battleId, duration);
                int remainingTurns = calculateRemainingTurns(contextTurn, currentBattleTurn, duration);
                sendFieldStatusPacket(battle, FieldStatusPacket.FieldType.TERRAIN,
                        FieldStatusPacket.Action.SET, currentTerrain, remainingTurns);
                LOGGER.debug("Terrain changed in battle {}: {} -> {} (turns: {}, duration: {})",
                        battleId, previousTerrain, currentTerrain, remainingTurns, duration);
            }
        } else if (!currentTerrain.isEmpty()) {
            int startTurn = terrainStartTurn.getOrDefault(battleId, currentBattleTurn);
            int duration = terrainDuration.getOrDefault(battleId, DEFAULT_TERRAIN_DURATION);
            int remainingTurns = calculateRemainingTurns(startTurn, currentBattleTurn, duration);

            sendFieldStatusPacket(battle, FieldStatusPacket.FieldType.TERRAIN,
                    FieldStatusPacket.Action.SET, currentTerrain, remainingTurns);
        }
    }

    private int getTerrainDuration(BattlePokemon origin) {
        if (origin == null) return DEFAULT_TERRAIN_DURATION;

        try {
            String heldItem = origin.getHeldItemManager().showdownId(origin);
            if (heldItem != null && heldItem.toLowerCase().equals(TERRAIN_EXTENDER)) {
                LOGGER.debug("Terrain extended by Terrain Extender");
                return EXTENDED_TERRAIN_DURATION;
            }
        } catch (Exception e) {
            LOGGER.debug("Error checking terrain-extending item: {}", e.getMessage());
        }

        return DEFAULT_TERRAIN_DURATION;
    }

    private void checkRoomConditions(PokemonBattle battle) {
        UUID battleId = battle.getBattleId();
        Set<String> previousRooms = trackedRooms.getOrDefault(battleId, ConcurrentHashMap.newKeySet());
        Set<String> currentRooms = new HashSet<>();
        Map<String, Integer> roomContextTurns = new HashMap<>();

        var roomContexts = battle.getContextManager().get(BattleContext.Type.ROOM);
        if (roomContexts != null) {
            for (BattleContext context : roomContexts) {
                String roomId = context.getId().toLowerCase();
                currentRooms.add(roomId);
                roomContextTurns.put(roomId, context.getTurn());
            }
        }

        int currentBattleTurn = battle.getTurn();
        Map<String, Integer> battleRoomStarts = roomStartTurns.computeIfAbsent(battleId, k -> new ConcurrentHashMap<>());

        for (String room : currentRooms) {
            if (!previousRooms.contains(room)) {
                previousRooms.add(room);
                int contextTurn = roomContextTurns.getOrDefault(room, currentBattleTurn);
                battleRoomStarts.put(room, contextTurn);
                int remainingTurns = calculateRemainingTurns(contextTurn, currentBattleTurn, DEFAULT_ROOM_DURATION);
                sendFieldStatusPacket(battle, FieldStatusPacket.FieldType.ROOM,
                        FieldStatusPacket.Action.SET, room, remainingTurns);
                LOGGER.debug("Room added in battle {}: {} (turns: {})", battleId, room, remainingTurns);
            } else {
                int startTurn = battleRoomStarts.getOrDefault(room, currentBattleTurn);
                int remainingTurns = calculateRemainingTurns(startTurn, currentBattleTurn, DEFAULT_ROOM_DURATION);
                sendFieldStatusPacket(battle, FieldStatusPacket.FieldType.ROOM,
                        FieldStatusPacket.Action.SET, room, remainingTurns);
            }
        }

        Iterator<String> iterator = previousRooms.iterator();
        while (iterator.hasNext()) {
            String room = iterator.next();
            if (!currentRooms.contains(room)) {
                iterator.remove();
                battleRoomStarts.remove(room);
                sendFieldStatusPacket(battle, FieldStatusPacket.FieldType.ROOM,
                        FieldStatusPacket.Action.CLEAR, room, -1);
                LOGGER.debug("Room removed in battle {}: {}", battleId, room);
            }
        }

        trackedRooms.put(battleId, previousRooms);
    }

    private void sendFieldStatusPacket(PokemonBattle battle, FieldStatusPacket.FieldType fieldType,
                                        FieldStatusPacket.Action action, String conditionId, int turns) {
        UUID battleId = battle.getBattleId();
        Set<UUID> players = battlePlayers.get(battleId);
        if (players == null) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        FieldStatusPacket packet = new FieldStatusPacket(battleId, fieldType, action, conditionId, turns);

        for (UUID playerUuid : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player == null) continue;

            ShowdownNetwork.sendToPlayer(packet, player);
        }

        sendPacketToSpectators(battleId, packet, server);
    }

    private void sendPacketToSpectators(UUID cobblemonBattleId, Object packet, MinecraftServer server) {
        BattleManager battleManager = CobblemonShowdown.getBattleManager();
        if (battleManager == null) return;

        for (ShowdownBattle showdownBattle : battleManager.getActiveBattles().values()) {
            if (showdownBattle.getCobblemonBattle() != null &&
                    showdownBattle.getCobblemonBattle().getBattleId().equals(cobblemonBattleId)) {

                Set<UUID> spectators = showdownBattle.getSpectatorManager().getSpectators();
                for (UUID spectatorUuid : spectators) {
                    ServerPlayer spectator = server.getPlayerList().getPlayer(spectatorUuid);
                    if (spectator != null) {
                        if (packet instanceof FieldStatusPacket fieldPacket) {
                            ShowdownNetwork.sendToPlayer(fieldPacket, spectator);
                        } else if (packet instanceof SideConditionPacket sidePacket) {
                            ShowdownNetwork.sendToPlayer(sidePacket, spectator);
                        } else if (packet instanceof PartyStatusPacket partyPacket) {
                            ShowdownNetwork.sendToPlayer(partyPacket, spectator);
                        }
                    }
                }
                break;
            }
        }
    }

    // side conditions are in side.contextManager: HAZARD, SCREEN, and TAILWIND types
    private void checkSideConditions(PokemonBattle battle, BattleSide side, boolean isPlayerOneSide) {
        UUID battleId = battle.getBattleId();
        Map<Boolean, Set<String>> sideConditions = trackedSideConditions.get(battleId);
        if (sideConditions == null) return;

        Set<String> trackedConditions = sideConditions.get(isPlayerOneSide);
        if (trackedConditions == null) return;

        Set<String> currentConditions = new HashSet<>();

        var hazardContexts = side.getContextManager().get(BattleContext.Type.HAZARD);
        if (hazardContexts != null) {
            for (BattleContext context : hazardContexts) {
                currentConditions.add(context.getId().toLowerCase());
            }
        }

        var screenContexts = side.getContextManager().get(BattleContext.Type.SCREEN);
        if (screenContexts != null) {
            for (BattleContext context : screenContexts) {
                currentConditions.add(context.getId().toLowerCase());
            }
        }

        var tailwindContexts = side.getContextManager().get(BattleContext.Type.TAILWIND);
        if (tailwindContexts != null) {
            for (BattleContext context : tailwindContexts) {
                currentConditions.add(context.getId().toLowerCase());
            }
        }

        for (String conditionId : currentConditions) {
            if (!trackedConditions.contains(conditionId)) {
                sendSideConditionPacket(battle, isPlayerOneSide, SideConditionPacket.Action.ADD, conditionId);
                trackedConditions.add(conditionId);
                LOGGER.debug("Side condition ADDED for side {}: {}", isPlayerOneSide ? "1" : "2", conditionId);
            }
        }

        Iterator<String> iterator = trackedConditions.iterator();
        while (iterator.hasNext()) {
            String conditionId = iterator.next();
            if (!currentConditions.contains(conditionId)) {
                sendSideConditionPacket(battle, isPlayerOneSide, SideConditionPacket.Action.REMOVE, conditionId);
                iterator.remove();
                LOGGER.debug("Side condition REMOVED for side {}: {}", isPlayerOneSide ? "1" : "2", conditionId);
            }
        }
    }

    private void sendSideConditionPacket(PokemonBattle battle, boolean isPlayerOneSide,
                                          SideConditionPacket.Action action, String conditionId) {
        UUID battleId = battle.getBattleId();
        Set<UUID> players = battlePlayers.get(battleId);
        if (players == null) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (UUID playerUuid : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player == null) continue;

            boolean isAllySide = isAllyForPlayer(battle, playerUuid, isPlayerOneSide);

            SideConditionPacket packet = new SideConditionPacket(
                    battleId, isAllySide, action, conditionId, -1);
            ShowdownNetwork.sendToPlayer(packet, player);
        }

        // spectators use player one's perspective
        SideConditionPacket spectatorPacket = new SideConditionPacket(
                battleId, isPlayerOneSide, action, conditionId, -1);
        sendPacketToSpectators(battleId, spectatorPacket, server);
    }

    private void checkSide(PokemonBattle battle, BattleSide side, boolean isPlayerOneSide) {
        UUID battleId = battle.getBattleId();
        Map<UUID, Set<String>> battleEffects = trackedEffects.get(battleId);
        Map<UUID, Map<String, Integer>> battleStats = trackedStatStages.get(battleId);
        Map<UUID, Boolean> sides = pokemonSides.get(battleId);

        if (battleEffects == null || battleStats == null || sides == null) return;

        // detect switches: clear volatile effects and stats for Pokemon no longer active
        Map<Boolean, Set<UUID>> activePokemonMap = previouslyActivePokemon.get(battleId);
        if (activePokemonMap != null) {
            Set<UUID> previousActive = activePokemonMap.get(isPlayerOneSide);
            Set<UUID> currentActive = new HashSet<>();
            for (var ap : side.getActivePokemon()) {
                BattlePokemon bp = ap.getBattlePokemon();
                if (bp != null) currentActive.add(bp.getUuid());
            }

            // any Pokemon that was active last tick but isn't now has switched out
            for (UUID prevId : previousActive) {
                if (!currentActive.contains(prevId)) {
                    Set<String> oldEffects = battleEffects.remove(prevId);
                    if (oldEffects != null && !oldEffects.isEmpty()) {
                        sendEffectPacket(battle, prevId, isPlayerOneSide, VolatileEffectPacket.Action.CLEAR_ALL, "");
                        LOGGER.debug("Cleared volatile effects for switched-out Pokemon {} in battle {}", prevId, battleId);
                    }
                    Map<String, Integer> oldStats = battleStats.remove(prevId);
                    if (oldStats != null && !oldStats.isEmpty()) {
                        sendStatPacket(battle, prevId, isPlayerOneSide, new HashMap<>());
                        LOGGER.debug("Cleared stat stages for switched-out Pokemon {} in battle {}", prevId, battleId);
                    }
                }
            }

            previousActive.clear();
            previousActive.addAll(currentActive);
        }

        for (var activePokemon : side.getActivePokemon()) {
            BattlePokemon pokemon = activePokemon.getBattlePokemon();
            if (pokemon == null) continue;

            UUID pokemonId = pokemon.getUuid();

            sides.put(pokemonId, isPlayerOneSide);

            Set<String> currentEffects = new HashSet<>();
            var volatileContexts = pokemon.getContextManager().get(BattleContext.Type.VOLATILE);
            if (volatileContexts != null) {
                for (BattleContext context : volatileContexts) {
                    String effectId = context.getId();
                    // Wheel of Dharma embeds the type in the volatile ID (e.g., wheelofdharmaadaptedfire)
                    currentEffects.add(effectId);
                }
            }

            Set<String> previousEffects = battleEffects.computeIfAbsent(pokemonId, k -> ConcurrentHashMap.newKeySet());

            for (String effectId : currentEffects) {
                if (!previousEffects.contains(effectId)) {
                    sendEffectPacket(battle, pokemonId, isPlayerOneSide, VolatileEffectPacket.Action.ADD, effectId);
                    previousEffects.add(effectId);
                }
            }

            Iterator<String> iterator = previousEffects.iterator();
            while (iterator.hasNext()) {
                String effectId = iterator.next();
                if (!currentEffects.contains(effectId)) {
                    sendEffectPacket(battle, pokemonId, isPlayerOneSide, VolatileEffectPacket.Action.REMOVE, effectId);
                    iterator.remove();
                }
            }

            Map<String, Integer> currentStatStages = new HashMap<>();

            var boostContexts = pokemon.getContextManager().get(BattleContext.Type.BOOST);
            if (boostContexts != null) {
                for (BattleContext context : boostContexts) {
                    String statId = context.getId().toLowerCase();
                    currentStatStages.merge(statId, 1, Integer::sum);
                }
            }

            var unboostContexts = pokemon.getContextManager().get(BattleContext.Type.UNBOOST);
            if (unboostContexts != null) {
                for (BattleContext context : unboostContexts) {
                    String statId = context.getId().toLowerCase();
                    currentStatStages.merge(statId, -1, Integer::sum);
                }
            }

            currentStatStages.entrySet().removeIf(e -> e.getValue() == 0);

            Map<String, Integer> previousStats = battleStats.computeIfAbsent(pokemonId, k -> new ConcurrentHashMap<>());

            if (!currentStatStages.equals(previousStats)) {
                previousStats.clear();
                previousStats.putAll(currentStatStages);

                sendStatPacket(battle, pokemonId, isPlayerOneSide, currentStatStages);
            }
        }
    }

    private void sendEffectPacket(PokemonBattle battle, UUID pokemonId, boolean isPlayerOneSide,
                                   VolatileEffectPacket.Action action, String effectId) {
        UUID battleId = battle.getBattleId();
        Set<UUID> players = battlePlayers.get(battleId);
        if (players == null) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (UUID playerUuid : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player == null) continue;

            boolean isAlly = isAllyForPlayer(battle, playerUuid, isPlayerOneSide);

            VolatileEffectPacket packet = new VolatileEffectPacket(battleId, pokemonId, isAlly, action, effectId);
            ShowdownNetwork.sendToPlayer(packet, player);
        }
    }

    private void sendStatPacket(PokemonBattle battle, UUID pokemonId, boolean isPlayerOneSide,
                                 Map<String, Integer> statStages) {
        UUID battleId = battle.getBattleId();
        Set<UUID> players = battlePlayers.get(battleId);
        if (players == null) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (UUID playerUuid : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player == null) continue;

            boolean isAlly = isAllyForPlayer(battle, playerUuid, isPlayerOneSide);

            StatChangePacket packet = new StatChangePacket(battleId, pokemonId, isAlly, statStages);
            ShowdownNetwork.sendToPlayer(packet, player);
        }
    }

    private boolean isAllyForPlayer(PokemonBattle battle, UUID playerUuid, boolean isPlayerOneSide) {
        for (BattleActor actor : battle.getSide1().getActors()) {
            if (actor instanceof PlayerBattleActor playerActor) {
                if (playerActor.getUuid().equals(playerUuid)) {
                    return isPlayerOneSide;
                }
            }
        }

        // player must be on side 2
        return !isPlayerOneSide;
    }

    private void checkPartyStatus(PokemonBattle battle, BattleSide side, boolean isPlayerOneSide) {
        UUID battleId = battle.getBattleId();
        Map<Boolean, List<String>> partyStatusMap = trackedPartyStatus.get(battleId);
        if (partyStatusMap == null) return;

        List<PartyStatusPacket.SlotInfo> currentSlots = new ArrayList<>();
        List<String> currentStatusStrings = new ArrayList<>();

        for (BattleActor actor : side.getActors()) {
            List<BattlePokemon> pokemonList = actor.getPokemonList();
            for (BattlePokemon pokemon : pokemonList) {
                if (pokemon != null) {
                    int currentHp = pokemon.getHealth();
                    int maxHp = pokemon.getMaxHealth();

                    PartyStatusPacket.SlotInfo slotInfo = PartyStatusPacket.SlotInfo.fromHealth(currentHp, maxHp);
                    currentSlots.add(slotInfo);

                    currentStatusStrings.add(slotInfo.getStatus().name() + ":" + slotInfo.getHealthPercent());
                }
            }
        }

        List<String> previousStatusStrings = partyStatusMap.get(isPlayerOneSide);
        if (previousStatusStrings == null) {
            previousStatusStrings = new ArrayList<>();
        }

        if (!currentStatusStrings.equals(previousStatusStrings)) {
            partyStatusMap.put(isPlayerOneSide, new ArrayList<>(currentStatusStrings));

            sendPartyStatusPacket(battle, isPlayerOneSide, currentSlots);

            LOGGER.debug("Party status changed for side {}: {} Pokemon",
                    isPlayerOneSide ? "1" : "2", currentSlots.size());
        }
    }

    private void sendPartyStatusPacket(PokemonBattle battle, boolean isPlayerOneSide,
                                        List<PartyStatusPacket.SlotInfo> slots) {
        UUID battleId = battle.getBattleId();
        Set<UUID> players = battlePlayers.get(battleId);
        if (players == null) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (UUID playerUuid : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player == null) continue;

            boolean isAllySide = isAllyForPlayer(battle, playerUuid, isPlayerOneSide);

            PartyStatusPacket packet = new PartyStatusPacket(battleId, isAllySide, slots);
            ShowdownNetwork.sendToPlayer(packet, player);
        }

        // spectators use player one's perspective
        PartyStatusPacket spectatorPacket = new PartyStatusPacket(battleId, isPlayerOneSide, slots);
        sendPacketToSpectators(battleId, spectatorPacket, server);
    }

    // forces immediate sync when a Pokemon faints, bypassing normal change detection
    public void forcePartyStatusUpdate(PokemonBattle battle) {
        if (battle == null) return;

        UUID battleId = battle.getBattleId();
        if (!battlePlayers.containsKey(battleId)) return;

        Map<Boolean, List<String>> partyStatusMap = trackedPartyStatus.get(battleId);
        if (partyStatusMap != null) {
            partyStatusMap.clear();
        }

        checkPartyStatus(battle, battle.getSide1(), true);
        checkPartyStatus(battle, battle.getSide2(), false);

        LOGGER.debug("Forced party status update for battle {}", battleId);
    }

    public void clear() {
        trackedEffects.clear();
        trackedStatStages.clear();
        pokemonSides.clear();
        battlePlayers.clear();
        trackedSideConditions.clear();
        trackedWeather.clear();
        trackedTerrain.clear();
        trackedRooms.clear();
        trackedPartyStatus.clear();
        weatherStartTurn.clear();
        weatherDuration.clear();
        terrainStartTurn.clear();
        terrainDuration.clear();
        roomStartTurns.clear();
    }

}
