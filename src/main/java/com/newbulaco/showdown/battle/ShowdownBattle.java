package com.newbulaco.showdown.battle;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import com.newbulaco.showdown.data.PrizeHandler;
import com.newbulaco.showdown.format.Format;
import kotlin.Unit;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ShowdownBattle {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShowdownBattle.class);

    private final UUID battleId;
    private final ServerPlayer player1;
    private final ServerPlayer player2;
    private final Format format;
    private PokemonBattle cobblemonBattle;

    private final BattleTimer timer;
    private final SpectatorManager spectatorManager;
    private final String context;

    @Nullable
    private PrizeHandler.ItemBet itemBet;

    private final Map<UUID, List<Integer>> selectedSlots = new HashMap<>();

    private boolean completed = false;
    private UUID winner;

    public ShowdownBattle(ServerPlayer player1, ServerPlayer player2, Format format, String context) {
        this.battleId = UUID.randomUUID();
        this.player1 = player1;
        this.player2 = player2;
        this.format = format;
        this.context = context;

        this.timer = format.hasBattleTimer() ? new BattleTimer(this) : null;
        this.spectatorManager = new SpectatorManager(this);

        LOGGER.info("Created Showdown battle: {} vs {} (format: {})",
                player1.getName().getString(),
                player2.getName().getString(),
                format.getName());
    }

    public boolean start() {
        return start(null, null);
    }

    public boolean start(@Nullable UUID leadPokemon1Uuid, @Nullable UUID leadPokemon2Uuid) {
        try {
            BattleFormat cobblemonFormat = format.isDoubles()
                    ? BattleFormat.Companion.getGEN_9_DOUBLES()
                    : BattleFormat.Companion.getGEN_9_SINGLES();

            PlayerPartyStore p1Party = Cobblemon.INSTANCE.getStorage().getParty(player1);
            PlayerPartyStore p2Party = Cobblemon.INSTANCE.getStorage().getParty(player2);

            Integer targetLevel = format.getSetLevel();

            List<Integer> p1Slots = selectedSlots.get(player1.getUUID());
            List<Integer> p2Slots = selectedSlots.get(player2.getUUID());

            if (p1Slots == null || p1Slots.isEmpty()) {
                p1Slots = createDefaultSlotList(p1Party, leadPokemon1Uuid);
            }
            if (p2Slots == null || p2Slots.isEmpty()) {
                p2Slots = createDefaultSlotList(p2Party, leadPokemon2Uuid);
            }

            // cloned Pokemon in SELECTED ORDER (originals are NOT modified)
            List<BattlePokemon> player1Team = createBattleTeam(p1Party, p1Slots, targetLevel);
            List<BattlePokemon> player2Team = createBattleTeam(p2Party, p2Slots, targetLevel);

            if (player1Team.isEmpty()) {
                LOGGER.error("Player 1 has no valid Pokemon for battle");
                return false;
            }
            if (player2Team.isEmpty()) {
                LOGGER.error("Player 2 has no valid Pokemon for battle");
                return false;
            }

            LOGGER.info("Created battle teams - Player1: {} Pokemon, Player2: {} Pokemon",
                    player1Team.size(), player2Team.size());

            PlayerBattleActor player1Actor = new PlayerBattleActor(player1.getUUID(), player1Team);
            PlayerBattleActor player2Actor = new PlayerBattleActor(player2.getUUID(), player2Team);

            BattleRegistry battleRegistry = Cobblemon.INSTANCE.getBattleRegistry();

            // array workaround: Java lambda can't capture mutable locals
            final boolean[] battleStarted = {false};

            battleRegistry.startBattle(
                    cobblemonFormat,
                    new BattleSide(player1Actor),
                    new BattleSide(player2Actor),
                    false
            ).ifSuccessful(battle -> {
                this.cobblemonBattle = battle;
                battleStarted[0] = true;

                try {
                    player1Actor.setBattleTheme(PlayerExtensionsKt.getBattleTheme(player2));
                    player2Actor.setBattleTheme(PlayerExtensionsKt.getBattleTheme(player1));
                } catch (Exception e) {
                    LOGGER.warn("Error setting battle themes: {}", e.getMessage());
                }

                // strip held items from clones to prevent dupe drops on faint;
                // showdown JS already has the item data from initial team serialization
                stripCloneHeldItems(player1Team);
                stripCloneHeldItems(player2Team);

                LOGGER.info("Battle started successfully via BattleRegistry: {}", battle.getBattleId());
                return Unit.INSTANCE;
            });

            if (!battleStarted[0] || this.cobblemonBattle == null) {
                LOGGER.error("Failed to start battle - battle did not start successfully");
                return false;
            }

            if (timer != null) {
                timer.start();
            }

            LOGGER.info("Battle started successfully: {} (leads: slots {}, slots {})", battleId, p1Slots, p2Slots);
            return true;

        } catch (Exception e) {
            LOGGER.error("Error starting battle", e);
            return false;
        }
    }

    private int findLeadSlot(PlayerPartyStore party, @Nullable UUID leadPokemonUuid) {
        if (leadPokemonUuid == null) {
            for (int i = 0; i < 6; i++) {
                if (party.get(i) != null) {
                    return i;
                }
            }
            return 0;
        }

        for (int i = 0; i < 6; i++) {
            Pokemon pokemon = party.get(i);
            if (pokemon != null && pokemon.getUuid().equals(leadPokemonUuid)) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Uses BattlePokemon.safeCopyOf() so original Pokemon are NOT modified.
     * Pokemon are added in the exact order of selectedSlots (first = lead).
     */
    private List<BattlePokemon> createBattleTeam(PlayerPartyStore party, List<Integer> selectedSlots, @Nullable Integer targetLevel) {
        List<BattlePokemon> battleTeam = new ArrayList<>();
        int maxPartySize = format.getPartySize();

        for (int i = 0; i < selectedSlots.size() && battleTeam.size() < maxPartySize; i++) {
            int slot = selectedSlots.get(i);
            Pokemon pokemon = party.get(slot);
            if (pokemon != null) {
                BattlePokemon battlePokemon = BattlePokemon.Companion.safeCopyOf(pokemon);
                applyFormatTransformations(battlePokemon, targetLevel);
                battleTeam.add(battlePokemon);
                LOGGER.debug("Added Pokemon: {} (slot {}, position {}{})",
                    pokemon.getSpecies().getName(), slot, battleTeam.size(),
                    battleTeam.size() == 1 ? " - LEAD" : "");
            } else {
                LOGGER.warn("Selected slot {} is null, skipping", slot);
            }
        }

        if (battleTeam.isEmpty()) {
            LOGGER.warn("No Pokemon from selected slots, using fallback");
            for (int i = 0; i < 6 && battleTeam.size() < maxPartySize; i++) {
                Pokemon pokemon = party.get(i);
                if (pokemon != null) {
                    BattlePokemon battlePokemon = BattlePokemon.Companion.safeCopyOf(pokemon);
                    applyFormatTransformations(battlePokemon, targetLevel);
                    battleTeam.add(battlePokemon);
                }
            }
        }

        return battleTeam;
    }

    private List<Integer> createDefaultSlotList(PlayerPartyStore party, @Nullable UUID leadPokemonUuid) {
        List<Integer> slots = new ArrayList<>();
        int leadSlot = findLeadSlot(party, leadPokemonUuid);

        if (party.get(leadSlot) != null) {
            slots.add(leadSlot);
        }

        for (int i = 0; i < 6; i++) {
            if (i != leadSlot && party.get(i) != null) {
                slots.add(i);
            }
        }

        return slots;
    }

    // prevents clones from dropping held items on faint (dupe prevention)
    private void stripCloneHeldItems(List<BattlePokemon> team) {
        for (BattlePokemon bp : team) {
            try {
                Pokemon clone = bp.getEffectedPokemon();
                if (!clone.heldItem().isEmpty()) {
                    clone.swapHeldItem(ItemStack.EMPTY, false);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to strip held item from clone: {}", e.getMessage());
            }
        }
    }

    // modifies the CLONE, not the original Pokemon
    private void applyFormatTransformations(BattlePokemon battlePokemon, @Nullable Integer targetLevel) {
        if (targetLevel != null) {
            battlePokemon.getEffectedPokemon().setLevel(targetLevel);
            battlePokemon.getEffectedPokemon().heal();
        }
    }

    public void onBattleEnd(UUID winnerId) {
        this.completed = true;
        this.winner = winnerId;

        if (timer != null) {
            timer.stop();
        }

        spectatorManager.clearSpectators();

        LOGGER.info("Battle ended: {} - Winner: {}", battleId, winnerId);
    }

    public void onTurnStart(int turnNumber) {
        if (timer != null) {
            timer.onTurnStart();
        }

        LOGGER.debug("Battle {} - Turn {}", battleId, turnNumber);
    }

    public UUID getBattleId() {
        return battleId;
    }

    public ServerPlayer getPlayer1() {
        return player1;
    }

    public ServerPlayer getPlayer2() {
        return player2;
    }

    public Format getFormat() {
        return format;
    }

    public PokemonBattle getCobblemonBattle() {
        return cobblemonBattle;
    }

    public BattleTimer getTimer() {
        return timer;
    }

    public SpectatorManager getSpectatorManager() {
        return spectatorManager;
    }

    public String getContext() {
        return context;
    }

    public boolean isCompleted() {
        return completed;
    }

    public UUID getWinner() {
        return winner;
    }

    public boolean hasTimer() {
        return timer != null;
    }

    public boolean isParticipant(UUID playerUuid) {
        return player1.getUUID().equals(playerUuid) || player2.getUUID().equals(playerUuid);
    }

    public ServerPlayer getOpponent(ServerPlayer player) {
        if (player.getUUID().equals(player1.getUUID())) {
            return player2;
        } else if (player.getUUID().equals(player2.getUUID())) {
            return player1;
        }
        return null;
    }

    @Nullable
    public PrizeHandler.ItemBet getItemBet() {
        return itemBet;
    }

    public void setItemBet(@Nullable PrizeHandler.ItemBet itemBet) {
        this.itemBet = itemBet;
    }

    public boolean hasBet() {
        return itemBet != null;
    }

    public void setSelectedSlots(UUID playerUuid, List<Integer> slots) {
        selectedSlots.put(playerUuid, new ArrayList<>(slots));
    }

    @Nullable
    public List<Integer> getSelectedSlots(UUID playerUuid) {
        return selectedSlots.get(playerUuid);
    }

    public boolean hasSelectedSlots() {
        return !selectedSlots.isEmpty();
    }
}
