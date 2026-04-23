package com.newbulaco.showdown.client.gui.pc;

import com.cobblemon.mod.common.api.storage.pc.PCPosition;
import com.cobblemon.mod.common.client.storage.ClientPC;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.net.messages.server.storage.pc.MovePCPokemonPacket;
import com.cobblemon.mod.common.net.messages.server.storage.pc.SwapPCPokemonPacket;
import com.cobblemon.mod.common.CobblemonNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PCSortManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("CobblemonShowdown");

    public enum SortCriteria {
        IV_TOTAL("IV Total", "cobblemon_showdown.pc_sort.sort_by.iv_total"),
        EV_TOTAL("EV Total", "cobblemon_showdown.pc_sort.sort_by.ev_total"),
        BST("Base Stat Total", "cobblemon_showdown.pc_sort.sort_by.base_stat"),
        DEX_NUMBER("Dex Number", "cobblemon_showdown.pc_sort.sort_by.dex_number"),
        NAME("Name", "cobblemon_showdown.pc_sort.sort_by.name"),
        LEVEL("Level", "cobblemon_showdown.pc_sort.sort_by.level"),
        GENDER("Gender", "cobblemon_showdown.pc_sort.sort_by.gender"),
        SHINY("Shiny", "cobblemon_showdown.pc_sort.sort_by.shiny"),
        CATCH_DATE("Catch Date", "cobblemon_showdown.pc_sort.sort_by.catch_date"),
        FRIENDSHIP("Friendship", "cobblemon_showdown.pc_sort.sort_by.friendship"),
        NATURE("Nature", "cobblemon_showdown.pc_sort.sort_by.nature");

        private final String displayName;
        private final String translationKey;

        SortCriteria(String displayName, String translationKey) {
            this.displayName = displayName;
            this.translationKey = translationKey;
        }

        public String getDisplayName() {
            return displayName;
        }

        public MutableComponent getTranslatedName() {
            return Component.translatable(translationKey);
        }

        public MutableComponent getTranslatedNameShort() {
            return Component.translatable(translationKey + ".short");
        }
    }

    private final ClientPC pc;
    private final int boxIndex;
    private boolean ascending = true;
    private SortCriteria currentCriteria = SortCriteria.DEX_NUMBER;
    private boolean isSorting = false;

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public PCSortManager(ClientPC pc, int boxIndex) {
        this.pc = pc;
        this.boxIndex = boxIndex;
    }

    public void setAscending(boolean ascending) {
        this.ascending = ascending;
    }

    public boolean isAscending() {
        return ascending;
    }

    public void toggleAscending() {
        this.ascending = !this.ascending;
    }

    public void setCriteria(SortCriteria criteria) {
        this.currentCriteria = criteria;
    }

    public SortCriteria getCriteria() {
        return currentCriteria;
    }

    public boolean isSorting() {
        return isSorting;
    }

    public CompletableFuture<Void> sortCurrentBox() {
        if (isSorting) {
            LOGGER.info("[Showdown] Sort already in progress, ignoring request");
            return CompletableFuture.completedFuture(null);
        }

        isSorting = true;
        LOGGER.info("[Showdown] Starting sort of box {} by {} ({})",
            boxIndex, currentCriteria.getDisplayName(), ascending ? "ascending" : "descending");

        return CompletableFuture.runAsync(() -> {
            try {
                performSort();
                // isSorting is set to false by executeMovesBatched after all packets are sent
            } catch (Exception e) {
                LOGGER.error("[Showdown] Error during sort", e);
                isSorting = false;
            }
        });
    }

    public CompletableFuture<Void> sortAllBoxes() {
        if (isSorting) {
            LOGGER.info("[Showdown] Sort already in progress, ignoring request");
            return CompletableFuture.completedFuture(null);
        }

        isSorting = true;
        LOGGER.info("[Showdown] Starting sort of all boxes by {} ({})",
            currentCriteria.getDisplayName(), ascending ? "ascending" : "descending");

        return CompletableFuture.runAsync(() -> {
            try {
                performSortAllBoxes();
                // isSorting is set to false by executeMovesBatched after all packets are sent
            } catch (Exception e) {
                LOGGER.error("[Showdown] Error during sort all", e);
                isSorting = false;
            }
        });
    }

    private void performSort() {
        List<PokemonSlot> pokemonSlots = new ArrayList<>();

        var box = pc.getBoxes().get(boxIndex);
        if (box == null) {
            LOGGER.warn("[Showdown] Box {} is null!", boxIndex);
            return;
        }

        LOGGER.info("[Showdown] Reading Pokemon from box {}:", boxIndex);
        for (int slot = 0; slot < 30; slot++) {
            Pokemon pokemon = box.getSlots().get(slot);
            if (pokemon != null) {
                LOGGER.info("[Showdown]   Slot {}: {} ({})", slot, pokemon.getSpecies().getName(), pokemon.getUuid());
                pokemonSlots.add(new PokemonSlot(pokemon, new PCPosition(boxIndex, slot)));
            }
        }

        if (pokemonSlots.isEmpty()) {
            LOGGER.warn("[Showdown] No Pokemon found in box!");
            return;
        }

        LOGGER.info("[Showdown] Found {} Pokemon, sorting by {} ({})",
            pokemonSlots.size(), currentCriteria.getDisplayName(), ascending ? "asc" : "desc");

        sortPokemonList(pokemonSlots);

        LOGGER.info("[Showdown] Sorted order:");
        for (int i = 0; i < pokemonSlots.size(); i++) {
            PokemonSlot ps = pokemonSlots.get(i);
            LOGGER.info("[Showdown]   Target slot {}: {} (was in slot {})",
                i, ps.pokemon.getSpecies().getName(), ps.position.getSlot());
        }

        executeMoves(pokemonSlots, boxIndex);
    }

    private void performSortAllBoxes() {
        List<PokemonSlot> allPokemon = new ArrayList<>();

        LOGGER.info("[Showdown] Reading all Pokemon from PC...");
        for (int box = 0; box < pc.getBoxes().size(); box++) {
            var pcBox = pc.getBoxes().get(box);
            if (pcBox == null) continue;

            for (int slot = 0; slot < 30; slot++) {
                Pokemon pokemon = pcBox.getSlots().get(slot);
                if (pokemon != null) {
                    LOGGER.info("[Showdown]   Box {} Slot {}: {} ({})",
                        box, slot, pokemon.getSpecies().getName(), pokemon.getUuid().toString().substring(0, 8));
                    allPokemon.add(new PokemonSlot(pokemon, new PCPosition(box, slot)));
                }
            }
        }

        if (allPokemon.isEmpty()) {
            LOGGER.warn("[Showdown] No Pokemon found in PC!");
            return;
        }

        LOGGER.info("[Showdown] Found {} Pokemon total, sorting by {} ({})",
            allPokemon.size(), currentCriteria.getDisplayName(), ascending ? "asc" : "desc");

        sortPokemonList(allPokemon);

        LOGGER.info("[Showdown] Sorted order:");
        for (int i = 0; i < allPokemon.size(); i++) {
            PokemonSlot ps = allPokemon.get(i);
            LOGGER.info("[Showdown]   [{}] {} - was at box {} slot {}",
                i, ps.pokemon.getSpecies().getName(), ps.position.getBox(), ps.position.getSlot());
        }

        // cycle-based sorting with swap tracking
        Map<String, UUID> posToUuid = new HashMap<>();
        Map<UUID, String> uuidToPos = new HashMap<>();

        for (PokemonSlot ps : allPokemon) {
            String posKey = ps.position.getBox() + "," + ps.position.getSlot();
            posToUuid.put(posKey, ps.pokemon.getUuid());
            uuidToPos.put(ps.pokemon.getUuid(), posKey);
        }

        int targetBoxNum = 0;
        int targetSlotNum = 0;
        List<MoveOperation> moves = new ArrayList<>();

        for (PokemonSlot ps : allPokemon) {
            String targetKey = targetBoxNum + "," + targetSlotNum;
            UUID desiredUuid = ps.pokemon.getUuid();
            UUID currentUuid = posToUuid.get(targetKey);

            String currentPosOfDesired = uuidToPos.get(desiredUuid);

            LOGGER.info("[Showdown] Target slot ({},{}): want {} currently at {}, slot has {}",
                targetBoxNum, targetSlotNum,
                ps.pokemon.getSpecies().getName(),
                currentPosOfDesired,
                currentUuid != null ? currentUuid.toString().substring(0, 8) : "empty");

            if (currentPosOfDesired.equals(targetKey)) {
                LOGGER.info("[Showdown]   Already in correct position");
            } else {
                String[] parts = currentPosOfDesired.split(",");
                int fromBox = Integer.parseInt(parts[0]);
                int fromSlot = Integer.parseInt(parts[1]);

                PCPosition from = new PCPosition(fromBox, fromSlot);
                PCPosition to = new PCPosition(targetBoxNum, targetSlotNum);

                if (currentUuid != null) {
                    moves.add(new MoveOperation(desiredUuid, from, to, currentUuid));
                    LOGGER.info("[Showdown]   -> SWAP from ({},{}) with ({},{}) [swap with {}]",
                        fromBox, fromSlot, targetBoxNum, targetSlotNum, currentUuid.toString().substring(0, 8));
                } else {
                    moves.add(new MoveOperation(desiredUuid, from, to));
                    LOGGER.info("[Showdown]   -> MOVE from ({},{}) to ({},{})",
                        fromBox, fromSlot, targetBoxNum, targetSlotNum);
                }

                // update tracking to reflect the swap/move
                if (currentUuid != null) {
                    posToUuid.put(currentPosOfDesired, currentUuid);
                    uuidToPos.put(currentUuid, currentPosOfDesired);
                } else {
                    posToUuid.remove(currentPosOfDesired);
                }
                posToUuid.put(targetKey, desiredUuid);
                uuidToPos.put(desiredUuid, targetKey);
            }

            targetSlotNum++;
            if (targetSlotNum >= 30) {
                targetSlotNum = 0;
                targetBoxNum++;
            }
        }

        LOGGER.info("[Showdown] Generated {} moves for all-box sort", moves.size());
        executeMovesBatched(moves);
    }

    private void sortPokemonList(List<PokemonSlot> pokemonSlots) {
        Comparator<PokemonSlot> comparator = getComparator();
        if (!ascending) {
            comparator = comparator.reversed();
        }
        pokemonSlots.sort(comparator);
    }

    private Comparator<PokemonSlot> getComparator() {
        switch (currentCriteria) {
            case IV_TOTAL:
                return Comparator.comparingInt(ps -> getIVTotal(ps.pokemon));
            case EV_TOTAL:
                return Comparator.comparingInt(ps -> getEVTotal(ps.pokemon));
            case BST:
                return Comparator.comparingInt(ps -> getBST(ps.pokemon));
            case DEX_NUMBER:
                return Comparator.comparingInt(ps -> ps.pokemon.getSpecies().getNationalPokedexNumber());
            case NAME:
                return Comparator.comparing(ps -> ps.pokemon.getSpecies().getTranslatedName().getString());
            case LEVEL:
                return Comparator.comparingInt(ps -> ps.pokemon.getLevel());
            case GENDER:
                return Comparator.comparing(ps -> ps.pokemon.getGender().name());
            case SHINY:
                return Comparator.comparing(ps -> ps.pokemon.getShiny());
            case CATCH_DATE:
                return Comparator.comparingLong(ps -> getCatchTime(ps.pokemon));
            case FRIENDSHIP:
                return Comparator.comparingInt(ps -> ps.pokemon.getFriendship());
            case NATURE:
                return Comparator.comparing(ps -> ps.pokemon.getNature().getDisplayName());
            default:
                return Comparator.comparingInt(ps -> ps.pokemon.getSpecies().getNationalPokedexNumber());
        }
    }

    private int getIVTotal(Pokemon pokemon) {
        return pokemon.getIvs().getOrDefault(Stats.HP) +
               pokemon.getIvs().getOrDefault(Stats.ATTACK) +
               pokemon.getIvs().getOrDefault(Stats.DEFENCE) +
               pokemon.getIvs().getOrDefault(Stats.SPECIAL_ATTACK) +
               pokemon.getIvs().getOrDefault(Stats.SPECIAL_DEFENCE) +
               pokemon.getIvs().getOrDefault(Stats.SPEED);
    }

    private int getEVTotal(Pokemon pokemon) {
        return pokemon.getEvs().getOrDefault(Stats.HP) +
               pokemon.getEvs().getOrDefault(Stats.ATTACK) +
               pokemon.getEvs().getOrDefault(Stats.DEFENCE) +
               pokemon.getEvs().getOrDefault(Stats.SPECIAL_ATTACK) +
               pokemon.getEvs().getOrDefault(Stats.SPECIAL_DEFENCE) +
               pokemon.getEvs().getOrDefault(Stats.SPEED);
    }

    private int getBST(Pokemon pokemon) {
        var baseStats = pokemon.getForm().getBaseStats();
        return baseStats.get(Stats.HP) +
               baseStats.get(Stats.ATTACK) +
               baseStats.get(Stats.DEFENCE) +
               baseStats.get(Stats.SPECIAL_ATTACK) +
               baseStats.get(Stats.SPECIAL_DEFENCE) +
               baseStats.get(Stats.SPEED);
    }

    private long getCatchTime(Pokemon pokemon) {
        // cobblemon doesn't expose catch date directly, so use UUID as catch order proxy
        try {
            var caughtBall = pokemon.getCaughtBall();
            return pokemon.getUuid().getMostSignificantBits();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * uses cycle sort algorithm to properly handle swaps.
     * when we move pokemon A to slot B, if slot B has pokemon C, they swap —
     * we need to track these swaps to generate correct move operations.
     */
    private void executeMoves(List<PokemonSlot> sortedSlots, int targetBox) {
        if (sortedSlots.isEmpty()) return;

        Map<Integer, UUID> currentState = new HashMap<>();
        Map<UUID, Integer> uuidToSlot = new HashMap<>();
        for (PokemonSlot ps : sortedSlots) {
            int slot = ps.position.getSlot();
            currentState.put(slot, ps.pokemon.getUuid());
            uuidToSlot.put(ps.pokemon.getUuid(), slot);
        }

        LOGGER.info("[Showdown] Current state (slot -> UUID):");
        for (Map.Entry<Integer, UUID> entry : currentState.entrySet()) {
            LOGGER.info("[Showdown]   Slot {}: {}", entry.getKey(), entry.getValue());
        }

        Map<Integer, UUID> desiredState = new HashMap<>();
        for (int i = 0; i < sortedSlots.size(); i++) {
            desiredState.put(i, sortedSlots.get(i).pokemon.getUuid());
        }

        LOGGER.info("[Showdown] Desired state (slot -> UUID):");
        for (Map.Entry<Integer, UUID> entry : desiredState.entrySet()) {
            LOGGER.info("[Showdown]   Slot {}: {}", entry.getKey(), entry.getValue());
        }

        List<MoveOperation> moves = new ArrayList<>();

        for (int targetSlot = 0; targetSlot < sortedSlots.size(); targetSlot++) {
            UUID desiredUuid = desiredState.get(targetSlot);
            UUID currentUuid = currentState.get(targetSlot);

            LOGGER.info("[Showdown] Processing slot {}: desired={}, current={}",
                targetSlot,
                desiredUuid != null ? desiredUuid.toString().substring(0, 8) : "null",
                currentUuid != null ? currentUuid.toString().substring(0, 8) : "null");

            if (desiredUuid != null && desiredUuid.equals(currentUuid)) {
                LOGGER.info("[Showdown]   Already correct, skipping");
                continue;
            }

            Integer sourceSlot = uuidToSlot.get(desiredUuid);
            if (sourceSlot == null) {
                LOGGER.warn("[Showdown]   Could not find source slot for desired UUID!");
                continue;
            }

            PCPosition from = new PCPosition(targetBox, sourceSlot);
            PCPosition to = new PCPosition(targetBox, targetSlot);

            if (currentUuid != null) {
                moves.add(new MoveOperation(desiredUuid, from, to, currentUuid));
                LOGGER.info("[Showdown]   -> SWAP slot {} with slot {} [swap with {}]",
                    sourceSlot, targetSlot, currentUuid.toString().substring(0, 8));
            } else {
                moves.add(new MoveOperation(desiredUuid, from, to));
                LOGGER.info("[Showdown]   -> MOVE from slot {} to slot {}", sourceSlot, targetSlot);
            }

            // update tracking state to reflect the swap/move
            if (currentUuid != null) {
                currentState.put(sourceSlot, currentUuid);
                uuidToSlot.put(currentUuid, sourceSlot);
            } else {
                currentState.remove(sourceSlot);
            }
            currentState.put(targetSlot, desiredUuid);
            uuidToSlot.put(desiredUuid, targetSlot);
        }

        LOGGER.info("[Showdown] Generated {} move operations", moves.size());
        executeMovesBatched(moves);
    }

    private void executeMovesBatched(List<MoveOperation> moves) {
        if (moves.isEmpty()) {
            LOGGER.info("[Showdown] No moves to execute, Pokemon already in order");
            isSorting = false;
            return;
        }

        LOGGER.info("[Showdown] Executing {} move operations", moves.size());

        int delayPerMove = 50;

        for (int i = 0; i < moves.size(); i++) {
            final MoveOperation move = moves.get(i);
            final int currentDelay = i * delayPerMove;
            final int moveIndex = i;

            scheduler.schedule(() -> {
                try {
                    if (move.isSwap) {
                        LOGGER.info("[Showdown] Sending SWAP {}: {} at ({},{}) <-> {} at ({},{})",
                            moveIndex, move.pokemonId.toString().substring(0, 8),
                            move.from.getBox(), move.from.getSlot(),
                            move.swapWithId.toString().substring(0, 8),
                            move.to.getBox(), move.to.getSlot());
                        SwapPCPokemonPacket packet = new SwapPCPokemonPacket(
                            move.pokemonId,
                            move.from,
                            move.swapWithId,
                            move.to
                        );
                        CobblemonNetwork.INSTANCE.sendToServer(packet);
                    } else {
                        LOGGER.info("[Showdown] Sending MOVE {}: {} from ({},{}) to ({},{})",
                            moveIndex, move.pokemonId.toString().substring(0, 8),
                            move.from.getBox(), move.from.getSlot(),
                            move.to.getBox(), move.to.getSlot());
                        MovePCPokemonPacket packet = new MovePCPokemonPacket(
                            move.pokemonId,
                            move.from,
                            move.to
                        );
                        CobblemonNetwork.INSTANCE.sendToServer(packet);
                    }
                } catch (Exception e) {
                    LOGGER.error("[Showdown] Failed to send packet", e);
                }
            }, currentDelay, TimeUnit.MILLISECONDS);
        }

        // extra 500ms for server to sync
        int totalTime = moves.size() * delayPerMove + 500;
        scheduler.schedule(() -> {
            isSorting = false;
            LOGGER.info("[Showdown] Sort complete, ready for next sort");
        }, totalTime, TimeUnit.MILLISECONDS);
    }

    private static class PokemonSlot {
        final Pokemon pokemon;
        PCPosition position;

        PokemonSlot(Pokemon pokemon, PCPosition position) {
            this.pokemon = pokemon;
            this.position = position;
        }
    }

    private static class MoveOperation {
        final UUID pokemonId;
        final PCPosition from;
        final PCPosition to;
        final UUID swapWithId;
        final boolean isSwap;

        MoveOperation(UUID pokemonId, PCPosition from, PCPosition to) {
            this.pokemonId = pokemonId;
            this.from = from;
            this.to = to;
            this.swapWithId = null;
            this.isSwap = false;
        }

        MoveOperation(UUID pokemonId, PCPosition from, PCPosition to, UUID swapWithId) {
            this.pokemonId = pokemonId;
            this.from = from;
            this.to = to;
            this.swapWithId = swapWithId;
            this.isSwap = true;
        }
    }
}
