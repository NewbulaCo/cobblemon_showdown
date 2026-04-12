package com.newbulaco.showdown.gui;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.newbulaco.showdown.format.Format;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * layout: player's Pokemon on left (col 0), opponent's on right (col 8),
 * selected Pokemon in battle order in center arena, manual ready button
 */
public class PartySelectionMenuProvider implements MenuProvider {
    private final ServerPlayer selector;
    private final ServerPlayer opponent;
    private final PartySelectionSession session;
    private final Format format;
    private final int maxSelections;

    private PlayerPartyStore party;
    private PartySelectionMenu openedMenu;
    // LinkedHashSet preserves insertion order -- first clicked = lead Pokemon
    private final Set<Integer> selectedSlots = new LinkedHashSet<>();
    private int opponentSelectedCount = 0;
    private boolean opponentReady = false;
    private boolean playerReady = false;
    private boolean guiModifierFlag = false;

    // center arena slots where selected Pokemon appear in battle order
    // row 1: 11, 12, 13, 14 / row 2: 20, 21 (up to 6 Pokemon)
    private static final int[] BATTLE_ORDER_SLOTS = {11, 12, 13, 14, 20, 21};

    private static final int READY_BUTTON_SLOT = 31;
    private static final int FORMAT_INFO_SLOT = 22;
    private static final int TIMER_SLOT = 40;
    private static final int PLAYER_STATUS_SLOT = 29;
    private static final int OPPONENT_STATUS_SLOT = 33;

    public PartySelectionMenuProvider(PartySelectionSession session, ServerPlayer selector,
                                       ServerPlayer opponent, Format format) {
        this.session = session;
        this.selector = selector;
        this.opponent = opponent;
        this.format = format;
        this.maxSelections = format.getPartySize();
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("cobblemon_showdown.battle_gui.title", maxSelections);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        PartySelectionMenu menu = new PartySelectionMenu(this, containerId, playerInventory);
        setupMenuContents(menu);
        this.openedMenu = menu;
        return menu;
    }

    private void setupMenuContents(PartySelectionMenu menu) {
        party = Cobblemon.INSTANCE.getStorage().getParty(selector);
        PlayerPartyStore opponentParty = Cobblemon.INSTANCE.getStorage().getParty(opponent);

        setupBackground(menu);

        // player's Pokemon on the left column (col 0)
        for (int i = 0; i < 6; i++) {
            int itemSlot = i * 9;
            Pokemon pokemon = party.get(i);
            if (pokemon != null) {
                ItemStack pokemonItem = createPokemonDisplayItem(pokemon, i);
                menu.setItem(itemSlot, menu.getStateId(), pokemonItem);
            } else {
                ItemStack empty = new ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE);
                empty.setHoverName(Component.translatable("cobblemon_showdown.battle_gui.empty_slot")
                    .withStyle(ChatFormatting.GRAY));
                menu.setItem(itemSlot, menu.getStateId(), empty);
            }
        }

        // opponent's Pokemon on the right column (col 8)
        for (int i = 0; i < 6; i++) {
            int itemSlot = (i * 9) + 8;
            Pokemon pokemon = opponentParty.get(i);
            if (pokemon != null) {
                if (format.hasTeamPreview()) {
                    ItemStack pokemonItem = createOpponentPokemonDisplayItem(pokemon, opponent.getName());
                    menu.setItem(itemSlot, menu.getStateId(), pokemonItem);
                } else {
                    ItemStack pokeballItem = new ItemStack(CobblemonItems.POKE_BALL.asItem());
                    pokeballItem.setHoverName(Component.translatable(
                        "cobblemon_showdown.battle_gui.pokemon_owner",
                        opponent.getName().getString()
                    ).withStyle(ChatFormatting.GOLD));
                    menu.setItem(itemSlot, menu.getStateId(), pokeballItem);
                }
            } else {
                ItemStack empty = new ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE);
                empty.setHoverName(Component.translatable("cobblemon_showdown.battle_gui.empty_slot")
                    .withStyle(ChatFormatting.GRAY));
                menu.setItem(itemSlot, menu.getStateId(), empty);
            }
        }

        setupBattleOrderDisplay(menu);
        setupFormatInfo(menu);
        setupReadyButton(menu);
        setupStatusDisplays(menu);
    }

    private void setupBackground(PartySelectionMenu menu) {
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = row * 9 + col;
                ItemStack filler;

                if (col == 1) {
                    filler = guiModifierFlag ?
                        new ItemStack(Items.LIGHT_BLUE_STAINED_GLASS_PANE) :
                        new ItemStack(Items.CYAN_STAINED_GLASS_PANE);
                    filler.setHoverName(Component.translatable("cobblemon_showdown.battle_gui.side.ally")
                        .withStyle(ChatFormatting.AQUA));
                } else if (col == 7) {
                    filler = guiModifierFlag ?
                        new ItemStack(Items.YELLOW_STAINED_GLASS_PANE) :
                        new ItemStack(Items.ORANGE_STAINED_GLASS_PANE);
                    filler.setHoverName(Component.translatable(
                        "cobblemon_showdown.battle_gui.side.opponent",
                        opponent.getName().getString()
                    ).withStyle(ChatFormatting.GOLD));
                } else if (col >= 2 && col <= 6) {
                    filler = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
                    filler.setHoverName(Component.translatable("cobblemon_showdown.battle_gui")
                        .withStyle(ChatFormatting.DARK_GRAY));
                } else {
                    continue;
                }

                menu.setItem(slot, menu.getStateId(), filler);
            }
        }
    }

    private void setupBattleOrderDisplay(PartySelectionMenu menu) {
        ItemStack arenaTitle = new ItemStack(Items.NETHER_STAR);
        arenaTitle.setHoverName(Component.translatable("cobblemon_showdown.battle_gui.order"));
        ListTag titleLore = new ListTag();
        titleLore.add(StringTag.valueOf(Component.Serializer.toJson(
            Component.translatable("cobblemon_showdown.battle_gui.order.tip1").withStyle(ChatFormatting.GRAY))));
        titleLore.add(StringTag.valueOf(Component.Serializer.toJson(
            Component.translatable("cobblemon_showdown.battle_gui.order.tip2").withStyle(ChatFormatting.GRAY))));
        arenaTitle.getOrCreateTagElement("display").put("Lore", titleLore);
        menu.setItem(4, menu.getStateId(), arenaTitle);

        List<Integer> selectedList = new ArrayList<>(selectedSlots);
        for (int i = 0; i < BATTLE_ORDER_SLOTS.length && i < maxSelections; i++) {
            int displaySlot = BATTLE_ORDER_SLOTS[i];

            if (i < selectedList.size()) {
                int partySlot = selectedList.get(i);
                Pokemon pokemon = party.get(partySlot);
                if (pokemon != null) {
                    ItemStack pokemonItem = createBattleOrderItem(pokemon, i + 1, partySlot);
                    menu.setItem(displaySlot, menu.getStateId(), pokemonItem);
                }
            } else {
                ItemStack emptySlot = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
                Component slotLabel = (i == 0)
                    ? Component.translatable("cobblemon_showdown.battle_gui.order.leader")
                    : Component.translatable("cobblemon_showdown.battle_gui.order.number", (i + 1));
                emptySlot.setHoverName(Component.translatable("cobblemon_showdown.battle_gui.order.tag",
                        slotLabel,
                        Component.translatable("cobblemon_showdown.battle_gui.order.empty"))
                    .withStyle(ChatFormatting.DARK_GRAY));
                ListTag lore = new ListTag();
                lore.add(StringTag.valueOf(Component.Serializer.toJson(
                    Component.translatable("cobblemon_showdown.battle_gui.order.select_pokemon")
                        .withStyle(ChatFormatting.GRAY))));
                emptySlot.getOrCreateTagElement("display").put("Lore", lore);
                menu.setItem(displaySlot, menu.getStateId(), emptySlot);
            }
        }
    }

    private ItemStack createBattleOrderItem(Pokemon pokemon, int order, int partySlot) {
        Pokemon displayPokemon = pokemon;
        if (format.getSetLevel() > 0) {
            BattlePokemon copy = BattlePokemon.Companion.safeCopyOf(pokemon);
            copy.getEffectedPokemon().setLevel(format.getSetLevel());
            displayPokemon = copy.getEffectedPokemon();
        }

        ItemStack item = PokemonItem.from(displayPokemon, 1);

        Component orderLabel = (order == 1)
            ? Component.literal("★ ").append(Component.translatable("cobblemon_showdown.battle_gui.order.leader"))
            : Component.translatable("cobblemon_showdown.battle_gui.order.number", order);
        ChatFormatting color = (order == 1) ? ChatFormatting.GOLD : ChatFormatting.GREEN;
        item.setHoverName(Component.translatable(
            "cobblemon_showdown.battle_gui.order.tag",
            orderLabel,
            displayPokemon.getDisplayName().copy().withStyle(ChatFormatting.WHITE)
        ).withStyle(color));

        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(
            Component.translatable("cobblemon_showdown.battle_gui.order.remove_pokemon")
                .withStyle(ChatFormatting.YELLOW))));
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(""))));

        MutableComponent typeStr = Component.empty();
        typeStr.append(displayPokemon.getPrimaryType().getDisplayName());
        if (displayPokemon.getSecondaryType() != null) {
            typeStr.append("/").append(displayPokemon.getSecondaryType().getDisplayName());
        }
        lore.add(StringTag.valueOf(Component.Serializer.toJson(
            Component.translatable(
                "tooltip.cobblemon_showdown.pokemon_info.type",
                typeStr
            ).withStyle(ChatFormatting.GRAY))));

        item.getOrCreateTag().putInt("partySlot", partySlot);
        item.getOrCreateTagElement("display").put("Lore", lore);
        return item;
    }

    private void setupFormatInfo(PartySelectionMenu menu) {
        ItemStack formatItem = new ItemStack(Items.BOOK);
        formatItem.setHoverName(Component.translatable(
            "cobblemon_showdown.battle_gui.setup.format",
            Component.literal(format.getName()).withStyle(ChatFormatting.WHITE)
        ).withStyle(ChatFormatting.AQUA));

        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(
            Component.translatable("cobblemon_showdown.battle_gui.setup.party_size",
                Component.translatable("" + format.getPartySize()).withStyle(ChatFormatting.WHITE)
            ).withStyle(ChatFormatting.GRAY))));

        if (format.getSetLevel() > 0) {
            lore.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.translatable("cobblemon_showdown.battle_gui.setup.level_cap",
                    Component.translatable("" + format.getSetLevel()).withStyle(ChatFormatting.WHITE)
                ).withStyle(ChatFormatting.GRAY))));
        }

        if (format.getBestOf() > 1) {
            lore.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.translatable("cobblemon_showdown.battle_gui.setup.best",
                    Component.translatable("" + format.getBestOf()).withStyle(ChatFormatting.WHITE)
                ).withStyle(ChatFormatting.GRAY))));
        }

        if (format.hasSpeciesClause()) {
            lore.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.translatable("cobblemon_showdown.battle_gui.setup.clause")
                    .withStyle(ChatFormatting.YELLOW))));
        }

        if (format.hasBattleTimer()) {
            lore.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.translatable("cobblemon_showdown.battle_gui.setup.timer")
                    .withStyle(ChatFormatting.YELLOW))));
        }

        if (format.hasTeamPreview()) {
            lore.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.translatable("cobblemon_showdown.battle_gui.setup.preview")
                    .withStyle(ChatFormatting.GREEN))));
        }

        if (format.getBans() != null) {
            var bans = format.getBans();
            int totalBans = 0;
            if (bans.getPokemon() != null) totalBans += bans.getPokemon().size();
            if (bans.getMoves() != null) totalBans += bans.getMoves().size();
            if (bans.getAbilities() != null) totalBans += bans.getAbilities().size();
            if (bans.getItems() != null) totalBans += bans.getItems().size();

            if (totalBans > 0) {
                lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(""))));
                lore.add(StringTag.valueOf(Component.Serializer.toJson(
                    Component.translatable("cobblemon_showdown.battle_gui.setup.ban", totalBans)
                        .withStyle(ChatFormatting.RED))));
            }
        }

        formatItem.getOrCreateTagElement("display").put("Lore", lore);
        menu.setItem(FORMAT_INFO_SLOT, menu.getStateId(), formatItem);
    }

    private void setupReadyButton(PartySelectionMenu menu) {
        ItemStack readyButton;
        boolean canReady = selectedSlots.size() == maxSelections;

        if (playerReady) {
            readyButton = new ItemStack(Items.LIME_CONCRETE);
            readyButton.setHoverName(Component.translatable("cobblemon_showdown.battle_gui.ready")
                .withStyle(ChatFormatting.GREEN));
            ListTag lore = new ListTag();
            lore.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.translatable("cobblemon_showdown.battle_gui.ready.tip1")
                    .withStyle(ChatFormatting.GRAY))));
            lore.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.translatable("cobblemon_showdown.battle_gui.ready.tip2")
                    .withStyle(ChatFormatting.YELLOW))));
            readyButton.getOrCreateTagElement("display").put("Lore", lore);
        } else if (canReady) {
            readyButton = new ItemStack(Items.EMERALD_BLOCK);
            readyButton.setHoverName(Component.translatable("cobblemon_showdown.battle_gui.ready_click")
                .withStyle(ChatFormatting.GREEN));
            ListTag lore = new ListTag();
            lore.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.translatable("cobblemon_showdown.battle_gui.ready_click.tip1", maxSelections)
                    .withStyle(ChatFormatting.WHITE))));
            lore.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.translatable("cobblemon_showdown.battle_gui.ready_click.tip2")
                    .withStyle(ChatFormatting.YELLOW))));
            readyButton.getOrCreateTagElement("display").put("Lore", lore);
        } else {
            readyButton = new ItemStack(Items.BARRIER);
            readyButton.setHoverName(Component.translatable("cobblemon_showdown.battle_gui.ready_not")
                .withStyle(ChatFormatting.RED));
            ListTag lore = new ListTag();
            lore.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.translatable("cobblemon_showdown.battle_gui.ready_not.tip2", selectedSlots.size(), maxSelections)
                    .withStyle(ChatFormatting.WHITE))));
            lore.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.translatable("cobblemon_showdown.battle_gui.ready_not.tip1")
                    .withStyle(ChatFormatting.GRAY))));
            readyButton.getOrCreateTagElement("display").put("Lore", lore);
        }

        menu.setItem(READY_BUTTON_SLOT, menu.getStateId(), readyButton);
    }

    private void setupStatusDisplays(PartySelectionMenu menu) {
        int timeLeft = (int) Math.ceil(
            ((session.getCreationTime() + PartySelectionSession.SELECTION_TIMEOUT_MILLIS) - System.currentTimeMillis()) / 1000f);

        ItemStack timerItem = new ItemStack(Items.CLOCK);
        ChatFormatting timerColor = timeLeft > 30 ? ChatFormatting.GREEN : (timeLeft > 10 ? ChatFormatting.YELLOW : ChatFormatting.RED);
        timerItem.setHoverName(
            Component.translatable("cobblemon_showdown.battle_gui.ready_status.timer", timeLeft)
                .withStyle(timerColor));
        menu.setItem(TIMER_SLOT, menu.getStateId(), timerItem);

        ItemStack playerStatus = new ItemStack(playerReady ? Items.LIME_DYE : Items.LIGHT_BLUE_DYE);
        Component playerStatusText = playerReady
            ? Component.translatable("cobblemon_showdown.battle_gui.ready_status.ready").withStyle(ChatFormatting.GREEN)
            : Component.literal(selectedSlots.size() + "/" + maxSelections).withStyle(ChatFormatting.WHITE);
        playerStatus.setHoverName(
            Component.translatable(
                "cobblemon_showdown.battle_gui.ready_status.ally",
                playerStatusText
            ).withStyle(ChatFormatting.AQUA));
        menu.setItem(PLAYER_STATUS_SLOT, menu.getStateId(), playerStatus);

        ItemStack opponentStatus = new ItemStack(opponentReady ? Items.LIME_DYE : Items.ORANGE_DYE);
        Component opponentStatusText = opponentReady
            ? Component.translatable("cobblemon_showdown.battle_gui.ready_status.ready").withStyle(ChatFormatting.GREEN)
            : Component.literal(opponentSelectedCount + "/" + maxSelections).withStyle(ChatFormatting.WHITE);
        opponentStatus.setHoverName(
            Component.translatable(
                "cobblemon_showdown.battle_gui.ready_status.opponent",
                opponent.getName(),
                opponentStatusText
            ).withStyle(ChatFormatting.GOLD));
        menu.setItem(OPPONENT_STATUS_SLOT, menu.getStateId(), opponentStatus);
    }

    private ItemStack createOpponentPokemonDisplayItem(Pokemon pokemon, Component ownerName) {
        Pokemon displayPokemon = pokemon;
        if (format.getSetLevel() > 0) {
            BattlePokemon copy = BattlePokemon.Companion.safeCopyOf(pokemon);
            copy.getEffectedPokemon().setLevel(format.getSetLevel());
            displayPokemon = copy.getEffectedPokemon();
        }

        ItemStack item = PokemonItem.from(displayPokemon, 1);

        int levelStr = format.getSetLevel() > 0 ?
            format.getSetLevel() : pokemon.getLevel();
        item.setHoverName(Component.translatable(
            "cobblemon_showdown.battle_gui.opponent_pokemon.name",
            ownerName,
            displayPokemon.getDisplayName(),
            levelStr
        ).withStyle(ChatFormatting.GOLD));

        ListTag loreTag = new ListTag();
        loreTag.add(StringTag.valueOf(Component.Serializer.toJson(
            Component.translatable("cobblemon_showdown.battle_gui.opponent_pokemon")
                .withStyle(ChatFormatting.DARK_PURPLE))));
        loreTag.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(""))));

        MutableComponent typeStr = Component.empty().withStyle(ChatFormatting.WHITE);
        typeStr.append(displayPokemon.getPrimaryType().getDisplayName());
        if (displayPokemon.getSecondaryType() != null) {
            typeStr.append("/").append(displayPokemon.getSecondaryType().getDisplayName());
        }
        loreTag.add(StringTag.valueOf(Component.Serializer.toJson(
            Component.translatable("tooltip.cobblemon_showdown.pokemon_info.type", typeStr)
                .withStyle(ChatFormatting.GRAY))));

        if (displayPokemon.getAbility() != null) {
            Component abilityName = Component.literal(displayPokemon.getAbility().getDisplayName())
                .withStyle(ChatFormatting.GOLD);
            if (!abilityName.getString().isEmpty()) {
                loreTag.add(StringTag.valueOf(Component.Serializer.toJson(
                    Component.translatable("tooltip.cobblemon_showdown.pokemon_info.ability", abilityName)
                        .withStyle(ChatFormatting.GRAY))));
            }
        }

        if (!displayPokemon.heldItem().isEmpty()) {
            loreTag.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.translatable(
                    "tooltip.cobblemon_showdown.pokemon_info.item",
                    displayPokemon.heldItem().getHoverName().copy().withStyle(ChatFormatting.AQUA)
                ).withStyle(ChatFormatting.GRAY))));
        }

        loreTag.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(""))));
        loreTag.add(StringTag.valueOf(Component.Serializer.toJson(
            Component.translatable("tooltip.cobblemon_showdown.pokemon_info.moves")
                .withStyle(ChatFormatting.GRAY))));
        displayPokemon.getMoveSet().getMoves().forEach(move -> {
            loreTag.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.empty().withStyle(ChatFormatting.WHITE)
                    .append("  - ").append(move.getDisplayName()))));
        });

        item.getOrCreateTagElement("display").put("Lore", loreTag);
        return item;
    }

    private ItemStack createPokemonDisplayItem(Pokemon pokemon, int slot) {
        Pokemon displayPokemon = pokemon;
        if (format.getSetLevel() > 0) {
            BattlePokemon copy = BattlePokemon.Companion.safeCopyOf(pokemon);
            copy.getEffectedPokemon().setLevel(format.getSetLevel());
            displayPokemon = copy.getEffectedPokemon();
        }

        ItemStack item = PokemonItem.from(displayPokemon, 1);

        int selectionOrder = getSelectionOrder(slot);
        boolean isSelected = selectionOrder > 0;

        ChatFormatting color = isSelected ? ChatFormatting.GREEN : ChatFormatting.AQUA;
        int levelStr = format.getSetLevel() > 0 ?
           format.getSetLevel() : pokemon.getLevel();
        Component orderStr = isSelected
            ? Component.translatable("cobblemon_showdown.battle_gui.ally_pokemon.name.selected", selectionOrder)
            : Component.empty();
        Component leadStr = (selectionOrder == 1)
            ? Component.translatable("cobblemon_showdown.battle_gui.ally_pokemon.name.leader")
            : Component.empty();
        item.setHoverName(
            Component.translatable(
                "cobblemon_showdown.battle_gui.ally_pokemon.name",
                displayPokemon.getDisplayName(),
                levelStr,
                orderStr,
                leadStr
            ).withStyle(color));

        ListTag loreTag = new ListTag();

        if (isSelected) {
            if (selectionOrder == 1) {
                loreTag.add(StringTag.valueOf(Component.Serializer.toJson(
                    Component.translatable("cobblemon_showdown.battle_gui.ally_pokemon.leader")
                        .withStyle(ChatFormatting.GOLD))));
            } else {
                loreTag.add(StringTag.valueOf(Component.Serializer.toJson(
                    Component.translatable("cobblemon_showdown.battle_gui.ally_pokemon.number", selectionOrder)
                        .withStyle(ChatFormatting.GREEN))));
            }
            loreTag.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.translatable("cobblemon_showdown.battle_gui.ally_pokemon.deselect")
                    .withStyle(ChatFormatting.YELLOW))));
        } else if (selectedSlots.size() >= maxSelections) {
            loreTag.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.translatable("cobblemon_showdown.battle_gui.ally_pokemon.max_select")
                    .withStyle(ChatFormatting.RED))));
            loreTag.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.translatable("cobblemon_showdown.battle_gui.ally_pokemon.max_select.tip")
                    .withStyle(ChatFormatting.GRAY))));
        } else {
            Component hint = selectedSlots.isEmpty()
                ? Component.translatable("cobblemon_showdown.battle_gui.ally_pokemon.hint1").withStyle(ChatFormatting.YELLOW)
                : Component.translatable("cobblemon_showdown.battle_gui.ally_pokemon.hint2").withStyle(ChatFormatting.YELLOW);
            loreTag.add(StringTag.valueOf(Component.Serializer.toJson(hint)));
        }

        loreTag.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(""))));

        MutableComponent typeStr = Component.empty().withStyle(ChatFormatting.WHITE);
        typeStr.append(displayPokemon.getPrimaryType().getDisplayName());
        if (displayPokemon.getSecondaryType() != null) {
            typeStr.append("/").append(displayPokemon.getSecondaryType().getDisplayName());
        }
        loreTag.add(StringTag.valueOf(Component.Serializer.toJson(
            Component.translatable("tooltip.cobblemon_showdown.pokemon_info.type", typeStr)
                .withStyle(ChatFormatting.GRAY))));

        int healthPercent = (int) ((displayPokemon.getCurrentHealth() / (float) displayPokemon.getHp()) * 100);
        ChatFormatting hpColor = healthPercent > 50 ? ChatFormatting.GREEN : (healthPercent > 25 ? ChatFormatting.YELLOW : ChatFormatting.RED);
        Component hpValue = Component.literal(
            displayPokemon.getCurrentHealth() + "/" + displayPokemon.getHp() + " (" + healthPercent + "%)"
        ).withStyle(hpColor);
        loreTag.add(StringTag.valueOf(Component.Serializer.toJson(
            Component.translatable("tooltip.cobblemon_showdown.pokemon_info.hp", hpValue).withStyle(ChatFormatting.GRAY))));

        if (displayPokemon.getAbility() != null) {
            Component abilityName = Component.literal(displayPokemon.getAbility().getDisplayName())
                .withStyle(ChatFormatting.GOLD);
            if (!abilityName.getString().isEmpty()) {
                loreTag.add(StringTag.valueOf(Component.Serializer.toJson(
                    Component.translatable("tooltip.cobblemon_showdown.pokemon_info.ability", abilityName)
                        .withStyle(ChatFormatting.GRAY))));
            }
        }

        if (displayPokemon.getNature() != null) {
            loreTag.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.translatable("tooltip.cobblemon_showdown.pokemon_info.nature",
                    Component.literal(displayPokemon.getNature().getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE)
                ).withStyle(ChatFormatting.GRAY))));
        }

        if (!displayPokemon.heldItem().isEmpty()) {
            loreTag.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.translatable("tooltip.cobblemon_showdown.pokemon_info.item",
                    displayPokemon.heldItem().getHoverName().copy().withStyle(ChatFormatting.AQUA)
                ).withStyle(ChatFormatting.GRAY))));
        }

        loreTag.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(""))));
        loreTag.add(StringTag.valueOf(Component.Serializer.toJson(
            Component.translatable("tooltip.cobblemon_showdown.pokemon_info.moves").withStyle(ChatFormatting.GRAY))));
        displayPokemon.getMoveSet().getMoves().forEach(move -> {
            loreTag.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.empty().withStyle(ChatFormatting.WHITE)
                    .append("  - ").append(move.getDisplayName()))));
        });

        item.getOrCreateTagElement("display").put("Lore", loreTag);
        return item;
    }

    // returns 1-based selection order, or 0 if not selected
    private int getSelectionOrder(int slot) {
        int order = 1;
        for (Integer selected : selectedSlots) {
            if (selected.equals(slot)) {
                return order;
            }
            order++;
        }
        return 0;
    }

    public void onSlotClick(PartySelectionMenu menu, int slot) {
        // left column = player Pokemon slot
        if (slot % 9 == 0 && slot < 54) {
            int partyIndex = slot / 9;
            onSelectPokemonSlot(menu, partyIndex);
            return;
        }

        for (int i = 0; i < BATTLE_ORDER_SLOTS.length; i++) {
            if (slot == BATTLE_ORDER_SLOTS[i]) {
                onBattleOrderSlotClick(menu, slot);
                return;
            }
        }

        if (slot == READY_BUTTON_SLOT) {
            onReadyButtonClick();
            return;
        }
    }

    public void onSelectPokemonSlot(PartySelectionMenu menu, int partySlot) {
        Pokemon pokemon = party.get(partySlot);
        if (pokemon == null) return;

        if (playerReady) {
            selector.sendSystemMessage(Component.translatable("cobblemon_showdown.battle_gui.message.is_ready")
                .withStyle(ChatFormatting.RED));
            return;
        }

        if (pokemon.getCurrentHealth() <= 0) {
            selector.sendSystemMessage(Component.translatable("cobblemon_showdown.battle_gui.message.is_faint")
                .withStyle(ChatFormatting.RED));
            return;
        }

        if (selectedSlots.contains(partySlot)) {
            selectedSlots.remove(partySlot);
        } else if (selectedSlots.size() < maxSelections) {
            selectedSlots.add(partySlot);
        } else {
            selector.sendSystemMessage(Component.translatable("cobblemon_showdown.battle_gui.message.reach_max")
                .withStyle(ChatFormatting.YELLOW));
            return;
        }

        setupMenuContents(menu);

        session.onPokemonSelected(this);
    }

    private void onBattleOrderSlotClick(PartySelectionMenu menu, int slot) {
        if (playerReady) {
            selector.sendSystemMessage(Component.translatable("cobblemon_showdown.battle_gui.message.is_ready")
                .withStyle(ChatFormatting.RED));
            return;
        }

        int orderIndex = -1;
        for (int i = 0; i < BATTLE_ORDER_SLOTS.length; i++) {
            if (BATTLE_ORDER_SLOTS[i] == slot) {
                orderIndex = i;
                break;
            }
        }

        if (orderIndex >= 0) {
            List<Integer> selectedList = new ArrayList<>(selectedSlots);
            if (orderIndex < selectedList.size()) {
                int partySlot = selectedList.get(orderIndex);
                selectedSlots.remove(partySlot);
                setupMenuContents(menu);
                session.onPokemonSelected(this);
            }
        }
    }

    private void onReadyButtonClick() {
        if (selectedSlots.size() != maxSelections) {
            selector.sendSystemMessage(Component.translatable(
                "cobblemon_showdown.battle_gui.message.insufficient",
                    maxSelections
            ).withStyle(ChatFormatting.RED));
            return;
        }

        playerReady = !playerReady;

        if (playerReady) {
            selector.sendSystemMessage(Component.translatable("cobblemon_showdown.battle_gui.message.wait_opponent")
                .withStyle(ChatFormatting.GREEN));
        } else {
            selector.sendSystemMessage(Component.translatable("cobblemon_showdown.battle_gui.message.ready_canceled")
                .withStyle(ChatFormatting.YELLOW));
        }

        if (openedMenu != null) {
            setupMenuContents(openedMenu);
        }

        session.onPlayerReadyChanged(this, playerReady);
    }

    public void updateOpponentCount(int count) {
        this.opponentSelectedCount = count;
        if (openedMenu != null) {
            setupStatusDisplays(openedMenu);
        }
    }

    public void updateOpponentReady(boolean ready) {
        this.opponentReady = ready;
        if (openedMenu != null) {
            setupStatusDisplays(openedMenu);
        }
    }

    // called each tick to animate the border colors and refresh timer
    public void timedGuiUpdate() {
        guiModifierFlag = !guiModifierFlag;
        if (openedMenu != null) {
            setupBackground(openedMenu);
            setupBattleOrderDisplay(openedMenu);
            setupFormatInfo(openedMenu);
            setupReadyButton(openedMenu);
            setupStatusDisplays(openedMenu);
        }
    }

    public void forceCloseMenu() {
        if (openedMenu != null) {
            openedMenu.invalidateMenu();
        }
    }

    public void onPlayerCloseContainer() {
        session.onPlayerCloseMenu(selector);
    }

    public List<Integer> getSelectedSlots() {
        return new ArrayList<>(selectedSlots);
    }

    public boolean isSelectionComplete() {
        return selectedSlots.size() == maxSelections && playerReady;
    }

    public boolean isPlayerReady() {
        return playerReady;
    }

    public ServerPlayer getSelector() {
        return selector;
    }
}
