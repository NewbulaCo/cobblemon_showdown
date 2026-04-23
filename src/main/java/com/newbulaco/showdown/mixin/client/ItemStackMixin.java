package com.newbulaco.showdown.mixin.client;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.categories.DamageCategory;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.client.storage.ClientParty;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.newbulaco.showdown.util.ComponentUtil;
import net.minecraft.ChatFormatting;
import com.newbulaco.showdown.client.ShowdownKeybinds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

// adds move info and party learner info to SimpleTMs TM/TR item tooltips.
// shift shows move details, ctrl shows which party pokemon can learn the move.
@Mixin(ItemStack.class)
public class ItemStackMixin {

    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    private void cobblemonShowdown$addSimpleTMsInfo(Player player, TooltipFlag flag, CallbackInfoReturnable<List<Component>> cir) {
        if (player == null) return;

        ItemStack stack = (ItemStack) (Object) this;
        ResourceLocation itemId = stack.getItem().builtInRegistryHolder().key().location();

        if (!itemId.getNamespace().equals("simpletms")) return;

        String path = itemId.getPath();
        boolean isTM = path.startsWith("tm_");
        boolean isTR = path.startsWith("tr_");

        if (!isTM && !isTR) return;

        if (path.equals("tm_blank") || path.equals("tr_blank")) return;

        String moveName = path.substring(3);

        MoveTemplate move = Moves.INSTANCE.getByName(moveName);
        if (move == null) return;

        List<Component> tooltip = cir.getReturnValue();

        boolean infoHeld = ShowdownKeybinds.isShowInfoDown();
        boolean learnersHeld = ShowdownKeybinds.isShowPartyLearnersDown();

        if (!infoHeld && !learnersHeld) {
            tooltip.add(Component.translatable(
                "tooltip.cobblemon_showdown.tm_info.move",
                ShowdownKeybinds.getShowInfoKeyName().copy().withStyle(ChatFormatting.YELLOW)
            ).withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable(
                "tooltip.cobblemon_showdown.tm_info.learner",
                ShowdownKeybinds.getShowPartyLearnersKeyName().copy().withStyle(ChatFormatting.AQUA)
            ).withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        if (infoHeld) {
            cobblemonShowdown$addMoveInfo(tooltip, move);
        }

        if (learnersHeld) {
            cobblemonShowdown$addPartyLearners(tooltip, move, player);
        }
    }

    @Unique
    private void cobblemonShowdown$addMoveInfo(List<Component> tooltip, MoveTemplate move) {
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.cobblemon_showdown.move_info")
            .withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE));

        ElementalType type = move.getElementalType();
        // ChatFormatting typeColor = cobblemonShowdown$getTypeColor(type.getName());
        // Use Cobblemon type color
        tooltip.add(Component.literal("  ").append(Component.translatable(
            "tooltip.cobblemon_showdown.move_info.type",
            type.getDisplayName().setStyle(Style.EMPTY.withBold(true).withColor(type.getHue()))
        ).withStyle(ChatFormatting.GRAY)));

        DamageCategory category = move.getDamageCategory();
        ChatFormatting catColor = switch (category.getName().toLowerCase()) {
            case "physical" -> ChatFormatting.RED;
            case "special" -> ChatFormatting.BLUE;
            default -> ChatFormatting.GRAY;
        };
        tooltip.add(Component.literal("  ").append(Component.translatable(
            "tooltip.cobblemon_showdown.move_info.category",
            category.getDisplayName().copy().withStyle(catColor)
        ).withStyle(ChatFormatting.GRAY)));

        double power = move.getPower();
        tooltip.add(Component.literal("  ").append(Component.translatable(
            "tooltip.cobblemon_showdown.move_info.power",
            Component.literal(power > 0 ? String.valueOf((int) power) : "-")
                .withStyle(ChatFormatting.WHITE)
        ).withStyle(ChatFormatting.GRAY)));

        double accuracy = move.getAccuracy();
        tooltip.add(Component.literal("  ").append(Component.translatable(
            "tooltip.cobblemon_showdown.move_info.accuracy",
            Component.literal(accuracy > 0 ? String.valueOf((int) accuracy) : "-")
                .withStyle(ChatFormatting.WHITE)
        ).withStyle(ChatFormatting.GRAY)));

        tooltip.add(Component.literal("  ").append(Component.translatable(
            "tooltip.cobblemon_showdown.move_info.pp",
            Component.literal(String.valueOf(move.getPp()))
                .withStyle(ChatFormatting.WHITE)
        ).withStyle(ChatFormatting.GRAY)));

        try {
            var desc = move.getDescription();
            if (desc != null && !desc.getString().isEmpty()) {
                tooltip.add(Component.empty());
                // Average width of Mojangles is 4
                List<Component> lines = ComponentUtil.wrapText(desc, 160);
                for (Component line : lines) {
                    tooltip.add(Component.empty().withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY)
                        .append("  ").append(line));
                }
            }
        } catch (Exception e) {
            // description may not be available for all moves
        }
    }

    @Unique
    private void cobblemonShowdown$addPartyLearners(List<Component> tooltip, MoveTemplate move, Player player) {
        try {
            ClientParty party = CobblemonClient.INSTANCE.getStorage().getMyParty();
            if (party == null) return;

            List<Pokemon> canLearn = new ArrayList<>();
            List<Pokemon> alreadyKnows = new ArrayList<>();
            List<Pokemon> cannotLearn = new ArrayList<>();

            for (Pokemon pokemon : party.getSlots()) {
                if (pokemon == null) continue;

                boolean knows = cobblemonShowdown$pokemonKnowsMove(pokemon, move);

                if (knows) {
                    alreadyKnows.add(pokemon);
                    continue;
                }

                if (cobblemonShowdown$canPokemonLearnMove(pokemon, move)) {
                    canLearn.add(pokemon);
                } else {
                    cannotLearn.add(pokemon);
                }
            }

            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.cobblemon_showdown.party_learner")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE));

            if (!canLearn.isEmpty()) {
                MutableComponent names = Component.empty();
                for (int i = 0; i < canLearn.size(); i++) {
                    if (i > 0) names.append(", ");
                    names.append(canLearn.get(i).getSpecies().getTranslatedName());
                }
                tooltip.add(Component.literal("  ").append(Component.translatable(
                    "tooltip.cobblemon_showdown.party_learner.can_learn",
                    names.withStyle(ChatFormatting.WHITE)
                ).withStyle(ChatFormatting.GREEN)));
            }

            if (!alreadyKnows.isEmpty()) {
                MutableComponent names = Component.empty();
                for (int i = 0; i < alreadyKnows.size(); i++) {
                    if (i > 0) names.append(", ");
                    names.append(alreadyKnows.get(i).getSpecies().getTranslatedName());
                }
                tooltip.add(Component.literal("  ").append(Component.translatable(
                    "tooltip.cobblemon_showdown.party_learner.already_knows",
                    names.withStyle(ChatFormatting.WHITE)
                ).withStyle(ChatFormatting.YELLOW)));
            }

            if (!cannotLearn.isEmpty()) {
                MutableComponent names = Component.empty();
                for (int i = 0; i < cannotLearn.size(); i++) {
                    if (i > 0) names.append(", ");
                    names.append(cannotLearn.get(i).getSpecies().getTranslatedName());
                }
                tooltip.add(Component.literal("  ").append(Component.translatable(
                    "tooltip.cobblemon_showdown.party_learner.cannot_learn",
                    names.withStyle(ChatFormatting.DARK_GRAY)
                ).withStyle(ChatFormatting.RED)));
            }

            if (canLearn.isEmpty() && alreadyKnows.isEmpty() && cannotLearn.isEmpty()) {
                tooltip.add(Component.literal("  ").append(Component.translatable("tooltip.cobblemon_showdown.party_learner.nobody")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
            }

        } catch (Exception e) {
            // cobblemon storage may not be available yet
        }
    }

    @Unique
    private boolean cobblemonShowdown$pokemonKnowsMove(Pokemon pokemon, MoveTemplate move) {
        for (var moveSlot : pokemon.getMoveSet()) {
            if (moveSlot != null && moveSlot.getTemplate().equals(move)) {
                return true;
            }
        }
        for (var benchedMove : pokemon.getBenchedMoves()) {
            if (benchedMove.getMoveTemplate().equals(move)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private boolean cobblemonShowdown$canPokemonLearnMove(Pokemon pokemon, MoveTemplate move) {
        var form = pokemon.getForm();
        var moves = form.getMoves();
        String moveName = move.getName();

        for (MoveTemplate tm : moves.getTmMoves()) {
            if (tm.getName().equalsIgnoreCase(moveName)) return true;
        }

        for (MoveTemplate tutor : moves.getTutorMoves()) {
            if (tutor.getName().equalsIgnoreCase(moveName)) return true;
        }

        // check egg moves including pre-evolutions (matching SimpleTMs behavior)
        for (MoveTemplate egg : moves.getEggMoves()) {
            if (egg.getName().equalsIgnoreCase(moveName)) return true;
        }
        // walk up to 4 pre-evolution generations for egg moves
        var preEvolution = form.getPreEvolution();
        for (int i = 0; i < 4 && preEvolution != null && preEvolution.getSpecies() != null; i++) {
            var preForm = preEvolution.getForm();
            for (MoveTemplate egg : preForm.getMoves().getEggMoves()) {
                if (egg.getName().equalsIgnoreCase(moveName)) return true;
            }
            preEvolution = preForm.getPreEvolution();
        }

        int maxLevel = 100;
        try {
            maxLevel = Cobblemon.config.getMaxPokemonLevel();
        } catch (Exception e) {
            // fallback to 100
        }
        for (MoveTemplate levelUp : moves.getLevelUpMovesUpTo(maxLevel)) {
            if (levelUp.getName().equalsIgnoreCase(moveName)) return true;
        }

        return false;
    }

    @Unique
    private ChatFormatting cobblemonShowdown$getTypeColor(String typeName) {
        return switch (typeName.toLowerCase()) {
            case "normal" -> ChatFormatting.WHITE;
            case "fire" -> ChatFormatting.RED;
            case "water" -> ChatFormatting.BLUE;
            case "grass" -> ChatFormatting.GREEN;
            case "electric" -> ChatFormatting.YELLOW;
            case "ice" -> ChatFormatting.AQUA;
            case "fighting" -> ChatFormatting.DARK_RED;
            case "poison" -> ChatFormatting.DARK_PURPLE;
            case "ground" -> ChatFormatting.GOLD;
            case "flying" -> ChatFormatting.LIGHT_PURPLE;
            case "psychic" -> ChatFormatting.LIGHT_PURPLE;
            case "bug" -> ChatFormatting.DARK_GREEN;
            case "rock" -> ChatFormatting.GOLD;
            case "ghost" -> ChatFormatting.DARK_PURPLE;
            case "dragon" -> ChatFormatting.DARK_BLUE;
            case "dark" -> ChatFormatting.DARK_GRAY;
            case "steel" -> ChatFormatting.GRAY;
            case "fairy" -> ChatFormatting.LIGHT_PURPLE;
            default -> ChatFormatting.WHITE;
        };
    }

    @Unique
    private String cobblemonShowdown$formatName(String name) {
        if (name == null || name.isEmpty()) return "";
        String[] words = name.split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase())
                    .append(" ");
            }
        }
        return result.toString().trim();
    }
}
