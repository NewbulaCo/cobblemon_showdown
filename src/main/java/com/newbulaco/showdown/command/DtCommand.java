package com.newbulaco.showdown.command;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.pokemon.FormData;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.newbulaco.showdown.api.ShowdownAPI;
import com.newbulaco.showdown.api.content.AbilityModification;
import com.newbulaco.showdown.api.content.CustomAbility;
import com.newbulaco.showdown.api.content.CustomMove;
import com.newbulaco.showdown.api.content.MoveModification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

// similar to Pokemon Showdown's /data command
public class DtCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(DtCommand.class);

    // attacking type -> defending type -> multiplier
    private static final Map<String, Map<String, Double>> TYPE_CHART = new HashMap<>();

    static {
        initTypeChart();
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> parent) {
        parent.then(Commands.literal("dt")
                .then(Commands.argument("query", StringArgumentType.greedyString())
                        .suggests(DtCommand::suggestQuery)
                        .executes(DtCommand::handleQuery)));
    }

    private static CompletableFuture<Suggestions> suggestQuery(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String input = builder.getRemainingLowerCase();

        List<String> suggestions = new ArrayList<>();

        for (Species species : PokemonSpecies.INSTANCE.getSpecies()) {
            String name = species.getName().toLowerCase();
            if (name.startsWith(input) || fuzzyMatch(input, name)) {
                suggestions.add(species.getName());
            }
            for (FormData form : species.getForms()) {
                String formName = form.getName().toLowerCase();
                if (!formName.equals("normal") && !formName.isEmpty()) {
                    String fullFormName = species.getName() + "-" + form.getName();
                    if (fullFormName.toLowerCase().startsWith(input) || fuzzyMatch(input, fullFormName.toLowerCase())) {
                        suggestions.add(fullFormName);
                    }
                }
            }
        }

        for (MoveTemplate move : Moves.INSTANCE.all()) {
            String name = move.getName().toLowerCase();
            if (name.startsWith(input) || fuzzyMatch(input, name)) {
                suggestions.add(formatDisplayName(move.getName()));
            }
        }

        for (AbilityTemplate ability : Abilities.INSTANCE.all()) {
            String name = ability.getName().toLowerCase();
            if (name.startsWith(input) || fuzzyMatch(input, name)) {
                suggestions.add(formatDisplayName(ability.getName()));
            }
        }

        for (ElementalType type : ElementalTypes.INSTANCE.all()) {
            String name = type.getName().toLowerCase();
            if (name.startsWith(input) || fuzzyMatch(input, name)) {
                suggestions.add(formatDisplayName(type.getName()));
            }
        }

        suggestions.stream()
                .distinct()
                .sorted()
                .limit(15)
                .forEach(builder::suggest);

        return builder.buildFuture();
    }

    private static int handleQuery(CommandContext<CommandSourceStack> ctx) {
        String query = StringArgumentType.getString(ctx, "query").toLowerCase().trim();
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();

        if (player == null) return 0;

        // SimpleTMs items need special handling because they encode the move in the item id
        if (tryShowSimpleTMsItem(player, query)) return 1;

        // custom API content from other mods takes priority over vanilla lookups
        if (tryShowCustomAbility(player, query)) return 1;
        if (tryShowCustomMove(player, query)) return 1;

        // exact matches first, then fuzzy
        if (tryShowPokemonExact(player, query)) return 1;
        if (tryShowMoveExact(player, query)) return 1;
        if (tryShowAbilityExact(player, query)) return 1;
        if (tryShowTypeExact(player, query)) return 1;
        if (tryShowItemExact(player, query)) return 1;

        if (tryShowPokemon(player, query)) return 1;
        if (tryShowMove(player, query)) return 1;
        if (tryShowAbility(player, query)) return 1;
        if (tryShowType(player, query)) return 1;
        if (tryShowItem(player, query)) return 1;

        showNoMatchFound(player, query);
        return 0;
    }

    private static boolean tryShowPokemonExact(ServerPlayer player, String query) {
        Species species = findSpeciesExact(query);
        if (species == null) return false;
        showPokemonData(player, species, species.getStandardForm());
        return true;
    }

    private static boolean tryShowMoveExact(ServerPlayer player, String query) {
        MoveTemplate move = findMoveExact(query);
        if (move == null) return false;
        showMoveData(player, move);
        return true;
    }

    private static boolean tryShowAbilityExact(ServerPlayer player, String query) {
        AbilityTemplate ability = findAbilityExact(query);
        if (ability == null) return false;
        showAbilityData(player, ability);
        return true;
    }

    private static boolean tryShowTypeExact(ServerPlayer player, String query) {
        ElementalType type = findTypeExact(query);
        if (type == null) return false;
        showTypeData(player, type);
        return true;
    }

    private static boolean tryShowItemExact(ServerPlayer player, String query) {
        Item item = findCobblemonItemExact(query);
        if (item == null) return false;
        showItemData(player, item);
        return true;
    }

    private static Species findSpeciesExact(String query) {
        String normalized = query.toLowerCase().replace(" ", "_").replace("-", "_");
        for (Species species : PokemonSpecies.INSTANCE.getSpecies()) {
            String speciesName = species.getName().toLowerCase();
            if (speciesName.equals(normalized) ||
                speciesName.replace("_", "").equals(normalized.replace("_", ""))) {
                return species;
            }
        }
        return null;
    }

    private static MoveTemplate findMoveExact(String query) {
        String normalized = query.toLowerCase().replace(" ", "_").replace("-", "_");
        MoveTemplate move = Moves.INSTANCE.getByName(normalized);
        if (move != null) return move;
        return Moves.INSTANCE.getByName(normalized.replace("_", ""));
    }

    private static AbilityTemplate findAbilityExact(String query) {
        String normalized = query.toLowerCase().replace(" ", "_").replace("-", "_");
        AbilityTemplate ability = Abilities.INSTANCE.get(normalized);
        if (ability != null) return ability;
        for (AbilityTemplate a : Abilities.INSTANCE.all()) {
            String abilityName = a.getName().toLowerCase();
            if (abilityName.equals(normalized) ||
                abilityName.replace("_", "").equals(normalized.replace("_", ""))) {
                return a;
            }
        }
        return null;
    }

    private static ElementalType findTypeExact(String query) {
        String normalized = query.toLowerCase().replace(" ", "_");
        return ElementalTypes.INSTANCE.get(normalized);
    }

    private static Item findCobblemonItemExact(String query) {
        String normalized = query.toLowerCase().replace(" ", "_").replace("-", "_");
        ResourceLocation location = new ResourceLocation(Cobblemon.MODID, normalized);
        Item item = ForgeRegistries.ITEMS.getValue(location);
        if (item != null && item != net.minecraft.world.item.Items.AIR) {
            return item;
        }
        return null;
    }

    private static boolean tryShowPokemon(ServerPlayer player, String query) {
        String speciesName = query;
        String formName = null;

        if (query.contains("-")) {
            String[] parts = query.split("-", 2);
            speciesName = parts[0];
            formName = parts[1];
        }

        Species species = findSpecies(speciesName);
        if (species == null) return false;

        FormData form = species.getStandardForm();
        if (formName != null && !formName.isEmpty()) {
            FormData matchedForm = findForm(species, formName);
            if (matchedForm != null) {
                form = matchedForm;
            }
        }

        showPokemonData(player, species, form);
        return true;
    }

    private static void showPokemonData(ServerPlayer player, Species species, FormData form) {
        player.sendSystemMessage(Component.literal("=== ")
                .withStyle(ChatFormatting.GOLD)
                .append(species.getTranslatedName().copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(form.getName().equals("Normal") || form.getName().isEmpty() ?
                        Component.empty() :
                        Component.literal(" (" + form.getName() + ")").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" ===").withStyle(ChatFormatting.GOLD)));

        MutableComponent typesComp = Component.empty();
        List<ElementalType> types = new ArrayList<>();
        form.getTypes().forEach(types::add);
        for (int i = 0; i < types.size(); i++) {
            typesComp.append(createClickableType(types.get(i)));
            if (i < types.size() - 1) {
                typesComp.append(Component.literal("/").withStyle(ChatFormatting.GRAY));
            }
        }
        MutableComponent typeComp = Component.translatable(
            "tooltip.cobblemon_showdown.pokemon_info.type",
            typesComp
        ).withStyle(ChatFormatting.GRAY);
        player.sendSystemMessage(typeComp);

        Map<Stat, Integer> stats = form.getBaseStats();
        player.sendSystemMessage(Component.translatable("tooltip.cobblemon_showdown.pokemon_info.base_stats").withStyle(ChatFormatting.GRAY));

        int hp = stats.getOrDefault(Stats.HP, 0);
        int atk = stats.getOrDefault(Stats.ATTACK, 0);
        int def = stats.getOrDefault(Stats.DEFENCE, 0);
        int spa = stats.getOrDefault(Stats.SPECIAL_ATTACK, 0);
        int spd = stats.getOrDefault(Stats.SPECIAL_DEFENCE, 0);
        int spe = stats.getOrDefault(Stats.SPEED, 0);
        int bst = hp + atk + def + spa + spd + spe;

        player.sendSystemMessage(Component.translatable(
            "command.cobblemon_showdown.dt.pokemon_info.stats",
            Component.literal(String.valueOf(hp)).withStyle(getStatColor(hp)),
            Component.literal(String.valueOf(atk)).withStyle(getStatColor(atk)),
            Component.literal(String.valueOf(def)).withStyle(getStatColor(def)),
            Component.literal(String.valueOf(spa)).withStyle(getStatColor(spa)),
            Component.literal(String.valueOf(spd)).withStyle(getStatColor(spd)),
            Component.literal(String.valueOf(spe)).withStyle(getStatColor(spe)),
            Component.literal(String.valueOf(bst)).withStyle(ChatFormatting.AQUA)
        ).withStyle(ChatFormatting.GRAY));

        MutableComponent abilitiesComp = Component.empty();
        var abilities = form.getAbilities();
        List<AbilityTemplate> abilityTemplates = new ArrayList<>();
        abilities.forEach(pa -> {
            if (pa.getTemplate() != null) {
                abilityTemplates.add(pa.getTemplate());
            }
        });
        for (int i = 0; i < abilityTemplates.size(); i++) {
            AbilityTemplate ability = abilityTemplates.get(i);
            abilitiesComp.append(createClickableDt(
                    Component.translatable(ability.getDisplayName()),
                    ability.getName(),
                    ChatFormatting.WHITE));
            if (i < abilityTemplates.size() - 1) {
                abilitiesComp.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
        }
        MutableComponent abilityComp = Component.translatable(
            "tooltip.cobblemon_showdown.pokemon_info.ability",
            abilitiesComp
        ).withStyle(ChatFormatting.GRAY);
        player.sendSystemMessage(abilityComp);
    }

    private static boolean tryShowMove(ServerPlayer player, String query) {
        MoveTemplate move = findMove(query);
        if (move == null) return false;

        showMoveData(player, move);
        return true;
    }

    private static void showMoveData(ServerPlayer player, MoveTemplate move) {
        player.sendSystemMessage(Component.translatable(
            "command.cobblemon_showdown.dt.move",
            move.getDisplayName().copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
        ).withStyle(ChatFormatting.GOLD));

        MutableComponent typeCat = Component.empty().withStyle(ChatFormatting.GRAY);
        typeCat.append(Component.translatable("tooltip.cobblemon_showdown.move_info.type",
            createClickableType(move.getElementalType())));
        typeCat.append(Component.literal(" | "));
        String category = move.getDamageCategory().getName();
        ChatFormatting catColor = switch (category.toLowerCase()) {
            case "physical" -> ChatFormatting.RED;
            case "special" -> ChatFormatting.BLUE;
            default -> ChatFormatting.GRAY;
        };
        typeCat.append(Component.translatable("tooltip.cobblemon_showdown.move_info.category",
            move.getDamageCategory().getDisplayName().copy().withStyle(catColor)));
        player.sendSystemMessage(typeCat);

        MutableComponent stats = Component.empty().withStyle(ChatFormatting.GRAY);
        double power = move.getPower();
        stats.append(Component.translatable(
            "tooltip.cobblemon_showdown.move_info.power",
            Component.literal(power > 0 ? String.valueOf((int)power) : "-").withStyle(ChatFormatting.WHITE)));
        stats.append(Component.literal(" | "));
        double accuracy = move.getAccuracy();
        stats.append(Component.translatable(
            "tooltip.cobblemon_showdown.move_info.accuracy",
            Component.literal(accuracy > 0 ? String.valueOf((int)accuracy) : "-").withStyle(ChatFormatting.WHITE)));
        stats.append(Component.literal(" | "));
        stats.append(Component.translatable(
            "tooltip.cobblemon_showdown.move_info.pp",
            Component.literal(String.valueOf(move.getPp())).withStyle(ChatFormatting.WHITE)));
        player.sendSystemMessage(stats);

        Component desc = getMoveDescription(move);
        if (desc != null && !desc.getString().isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                "command.cobblemon_showdown.dt.description",
                desc.copy().withStyle(ChatFormatting.ITALIC, ChatFormatting.WHITE)
            ).withStyle(ChatFormatting.GRAY));
        } else {
            player.sendSystemMessage(Component.translatable(
                "command.cobblemon_showdown.dt.description",
                Component.translatable("command.cobblemon_showdown.dt.description.no_desc")
                    .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY)
            ).withStyle(ChatFormatting.GRAY));
        }
    }

    private static boolean tryShowAbility(ServerPlayer player, String query) {
        AbilityTemplate ability = findAbility(query);
        if (ability == null) return false;

        showAbilityData(player, ability);
        return true;
    }

    private static void showAbilityData(ServerPlayer player, AbilityTemplate ability) {
        player.sendSystemMessage(Component.translatable(
            "command.cobblemon_showdown.dt.ability",
            Component.translatable(ability.getDisplayName()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
        ).withStyle(ChatFormatting.GOLD));

        String desc = getAbilityDescription(ability);
        if (desc != null && !desc.isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                "command.cobblemon_showdown.dt.description",
                    Component.literal(desc).withStyle(ChatFormatting.ITALIC, ChatFormatting.WHITE)
                ).withStyle(ChatFormatting.GRAY));
        } else {
            player.sendSystemMessage(Component.translatable(
                "command.cobblemon_showdown.dt.description",
                Component.translatable("command.cobblemon_showdown.dt.description.no_desc")
                    .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY)
            ).withStyle(ChatFormatting.GRAY));
        }
    }

    private static boolean tryShowType(ServerPlayer player, String query) {
        ElementalType type = findType(query);
        if (type == null) return false;

        showTypeData(player, type);
        return true;
    }

    private static void showTypeData(ServerPlayer player, ElementalType type) {
        player.sendSystemMessage(Component.translatable(
            "command.cobblemon_showdown.dt.type",
                createClickableType(type)
        ).withStyle(ChatFormatting.GOLD));

        String typeName = type.getName().toLowerCase();

        List<String> superEffective = new ArrayList<>();
        List<String> notVeryEffective = new ArrayList<>();
        List<String> noEffect = new ArrayList<>();

        Map<String, Double> offensiveChart = TYPE_CHART.get(typeName);
        if (offensiveChart != null) {
            for (Map.Entry<String, Double> entry : offensiveChart.entrySet()) {
                if (entry.getValue() == 2.0) superEffective.add(entry.getKey());
                else if (entry.getValue() == 0.5) notVeryEffective.add(entry.getKey());
                else if (entry.getValue() == 0.0) noEffect.add(entry.getKey());
            }
        }

        player.sendSystemMessage(Component.translatable("command.cobblemon_showdown.dt.type.attack")
            .withStyle(ChatFormatting.GRAY, ChatFormatting.UNDERLINE));
        if (!superEffective.isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                "command.cobblemon_showdown.dt.type.attack.super",
                createClickableTypeList(superEffective)
            ).withStyle(ChatFormatting.GREEN));
        }
        if (!notVeryEffective.isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                "command.cobblemon_showdown.dt.type.attack.half",
                createClickableTypeList(notVeryEffective)
            ).withStyle(ChatFormatting.RED));
        }
        if (!noEffect.isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                "command.cobblemon_showdown.dt.type.attack.immune",
                createClickableTypeList(noEffect)
            ).withStyle(ChatFormatting.DARK_GRAY));
        }

        List<String> weakTo = new ArrayList<>();
        List<String> resistantTo = new ArrayList<>();
        List<String> immuneTo = new ArrayList<>();

        for (Map.Entry<String, Map<String, Double>> entry : TYPE_CHART.entrySet()) {
            Double mult = entry.getValue().get(typeName);
            if (mult != null) {
                if (mult == 2.0) weakTo.add(entry.getKey());
                else if (mult == 0.5) resistantTo.add(entry.getKey());
                else if (mult == 0.0) immuneTo.add(entry.getKey());
            }
        }

        player.sendSystemMessage(Component.translatable("command.cobblemon_showdown.dt.type.defend")
            .withStyle(ChatFormatting.GRAY, ChatFormatting.UNDERLINE));
        if (!weakTo.isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                "command.cobblemon_showdown.dt.type.defend.super",
                createClickableTypeList(weakTo)
            ).withStyle(ChatFormatting.RED));
        }
        if (!resistantTo.isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                "command.cobblemon_showdown.dt.type.defend.half",
                createClickableTypeList(resistantTo)
            ).withStyle(ChatFormatting.GREEN));
        }
        if (!immuneTo.isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                "command.cobblemon_showdown.dt.type.defend.immune",
                createClickableTypeList(immuneTo)
            ).withStyle(ChatFormatting.AQUA));
        }
    }

    private static boolean tryShowSimpleTMsItem(ServerPlayer player, String query) {
        String normalized = query.toLowerCase().replace(" ", "_").replace("-", "_");

        boolean isTM = normalized.startsWith("tm_");
        boolean isTR = normalized.startsWith("tr_");

        if (!isTM && !isTR) {
            return false;
        }

        String moveName = normalized.substring(3);

        ResourceLocation location = new ResourceLocation("simpletms", normalized);
        Item item = ForgeRegistries.ITEMS.getValue(location);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            return false;
        }

        MoveTemplate move = findMove(moveName);
        if (move == null) {
            showItemData(player, item);
            return true;
        }

        showSimpleTMsData(player, item, move, isTR);
        return true;
    }

    private static void showSimpleTMsData(ServerPlayer player, Item item, MoveTemplate move, boolean isTR) {
        ItemStack stack = new ItemStack(item);
        Component displayName = stack.getHoverName();
        Component itemType = isTR
            ? Component.translatable("command.cobblemon_showdown.dt.simple_tms.tr")
            : Component.translatable("command.cobblemon_showdown.dt.simple_tms.tm");

        player.sendSystemMessage(Component.translatable(
            "command.cobblemon_showdown.dt.simple_tms",
                displayName.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
                itemType
            ).withStyle(ChatFormatting.GOLD));

        MutableComponent teachesComp = Component.translatable(
            "command.cobblemon_showdown.dt.simple_tms.teach",
            createClickableDt(
                move.getDisplayName(),
                move.getName(),
                ChatFormatting.AQUA, ChatFormatting.BOLD)
        ).withStyle(ChatFormatting.GRAY);
        player.sendSystemMessage(teachesComp);

        MutableComponent typeCat = Component.empty().withStyle(ChatFormatting.GRAY);
        typeCat.append(Component.translatable(
            "tooltip.cobblemon_showdown.move_info.type",
            createClickableType(move.getElementalType())));
        typeCat.append(Component.literal(" | "));
        String category = move.getDamageCategory().getName();
        ChatFormatting catColor = switch (category.toLowerCase()) {
            case "physical" -> ChatFormatting.RED;
            case "special" -> ChatFormatting.BLUE;
            default -> ChatFormatting.GRAY;
        };
        typeCat.append(Component.translatable(
            "tooltip.cobblemon_showdown.move_info.category",
            move.getDamageCategory().getDisplayName().copy().withStyle(catColor)
        ));
        player.sendSystemMessage(typeCat);

        MutableComponent stats = Component.empty().withStyle(ChatFormatting.GRAY);
        double power = move.getPower();
        stats.append(Component.translatable("tooltip.cobblemon_showdown.move_info.power",
            Component.literal(power > 0 ? String.valueOf((int) power) : "-").withStyle(ChatFormatting.WHITE)));
        stats.append(Component.literal(" | "));
        double accuracy = move.getAccuracy();
        stats.append(Component.translatable("tooltip.cobblemon_showdown.move_info.accuracy",
            Component.literal(accuracy > 0 ? String.valueOf((int) accuracy) : "-").withStyle(ChatFormatting.WHITE)));
        stats.append(Component.literal(" | "));
        stats.append(Component.translatable("tooltip.cobblemon_showdown.move_info.pp",
            Component.literal(String.valueOf(move.getPp())).withStyle(ChatFormatting.WHITE)));
        player.sendSystemMessage(stats);

        Component desc = getMoveDescription(move);
        if (desc != null && !desc.getString().isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                "command.cobblemon_showdown.dt.description",
                desc.copy().withStyle(ChatFormatting.ITALIC, ChatFormatting.WHITE)
            ).withStyle(ChatFormatting.GRAY));
        }

        player.sendSystemMessage(Component.empty());
        showPartyLearners(player, move);
    }

    private static void showPartyLearners(ServerPlayer player, MoveTemplate move) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);

        List<Pokemon> canLearn = new ArrayList<>();
        List<Pokemon> alreadyKnows = new ArrayList<>();
        List<Pokemon> cannotLearn = new ArrayList<>();

        for (Pokemon pokemon : party) {
            if (pokemon == null) continue;

            boolean knows = false;
            for (var moveSlot : pokemon.getMoveSet()) {
                if (moveSlot != null && moveSlot.getTemplate().equals(move)) {
                    knows = true;
                    break;
                }
            }
            for (var benchedMove : pokemon.getBenchedMoves()) {
                if (benchedMove.getMoveTemplate().equals(move)) {
                    knows = true;
                    break;
                }
            }

            if (knows) {
                alreadyKnows.add(pokemon);
                continue;
            }

            if (canPokemonLearnMove(pokemon, move)) {
                canLearn.add(pokemon);
            } else {
                cannotLearn.add(pokemon);
            }
        }

        player.sendSystemMessage(Component.translatable("tooltip.cobblemon_showdown.party_learner")
            .withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE));

        if (!canLearn.isEmpty()) {
            MutableComponent learnComp = Component.empty();
            for (int i = 0; i < canLearn.size(); i++) {
                Pokemon pokemon = canLearn.get(i);
                learnComp.append(createClickableDt(
                        pokemon.getSpecies().getTranslatedName(),
                        pokemon.getSpecies().getName(),
                        ChatFormatting.WHITE));
                if (i < canLearn.size() - 1) {
                    learnComp.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
                }
            }
            player.sendSystemMessage(Component.literal("  ").withStyle(ChatFormatting.GREEN)
                .append(Component.translatable("tooltip.cobblemon_showdown.party_learner.can_learn", learnComp)));
        }

        if (!alreadyKnows.isEmpty()) {
            MutableComponent knowsComp = Component.empty();
            for (int i = 0; i < alreadyKnows.size(); i++) {
                Pokemon pokemon = alreadyKnows.get(i);
                knowsComp.append(createClickableDt(
                        pokemon.getSpecies().getTranslatedName(),
                        pokemon.getSpecies().getName(),
                        ChatFormatting.WHITE));
                if (i < alreadyKnows.size() - 1) {
                    knowsComp.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
                }
            }
            player.sendSystemMessage(Component.literal("  ").withStyle(ChatFormatting.YELLOW)
                .append(Component.translatable("tooltip.cobblemon_showdown.party_learner.already_knows", knowsComp)));
        }

        if (!cannotLearn.isEmpty()) {
            MutableComponent cantComp = Component.empty();
            for (int i = 0; i < cannotLearn.size(); i++) {
                Pokemon pokemon = cannotLearn.get(i);
                cantComp.append(createClickableDt(
                        pokemon.getSpecies().getTranslatedName(),
                        pokemon.getSpecies().getName(),
                        ChatFormatting.DARK_GRAY));
                if (i < cannotLearn.size() - 1) {
                    cantComp.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
                }
            }
            player.sendSystemMessage(Component.literal("  ").withStyle(ChatFormatting.RED)
                .append(Component.translatable("tooltip.cobblemon_showdown.party_learner.cannot_learn", cantComp)));
        }

        if (canLearn.isEmpty() && alreadyKnows.isEmpty() && cannotLearn.isEmpty()) {
            player.sendSystemMessage(Component.literal("  ").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
                .append(Component.translatable("tooltip.cobblemon_showdown.party_learner.nobody")));
        }
    }

    // checks TM, tutor, egg, and level-up learnsets
    private static boolean canPokemonLearnMove(Pokemon pokemon, MoveTemplate move) {
        var moves = pokemon.getForm().getMoves();

        if (moves.getTmMoves().contains(move)) return true;
        if (moves.getTutorMoves().contains(move)) return true;
        if (moves.getEggMoves().contains(move)) return true;

        int maxLevel = Cobblemon.config.getMaxPokemonLevel();
        return moves.getLevelUpMovesUpTo(maxLevel).contains(move);
    }

    private static boolean tryShowItem(ServerPlayer player, String query) {
        Item item = findCobblemonItem(query);
        if (item == null) return false;

        showItemData(player, item);
        return true;
    }

    private static void showItemData(ServerPlayer player, Item item) {
        ItemStack stack = new ItemStack(item);
        Component displayName = stack.getHoverName();

        player.sendSystemMessage(Component.translatable(
            "command.cobblemon_showdown.dt.item",
                displayName.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
            .withStyle(ChatFormatting.GOLD));

        List<net.minecraft.network.chat.Component> tooltips = new ArrayList<>();
        stack.getItem().appendHoverText(stack, null, tooltips, net.minecraft.world.item.TooltipFlag.NORMAL);

        if (!tooltips.isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                "command.cobblemon_showdown.dt.description",
                tooltips.get(0).copy().withStyle(ChatFormatting.ITALIC, ChatFormatting.WHITE)
            ).withStyle(ChatFormatting.GRAY));
        } else {
            player.sendSystemMessage(Component.translatable("command.cobblemon_showdown.dt.description.no_desc")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    private static void showNoMatchFound(ServerPlayer player, String query) {
        player.sendSystemMessage(Component.translatable(
            "command.cobblemon_showdown.dt.cannot_find",
                Component.literal(query).withStyle(ChatFormatting.WHITE)
        ).withStyle(ChatFormatting.RED));

        List<String> similar = findSimilarMatches(query, 5);
        if (!similar.isEmpty()) {
            MutableComponent suggestions = Component.empty();
            for (int i = 0; i < similar.size(); i++) {
                String suggestion = similar.get(i);
                suggestions.append(createClickableDt(Component.literal(suggestion), suggestion, ChatFormatting.YELLOW));
                if (i < similar.size() - 1) {
                    suggestions.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
                }
            }
            player.sendSystemMessage(Component.translatable("command.cobblemon_showdown.dt.cannot_find.msg", suggestions)
                .withStyle(ChatFormatting.GRAY));
        }
    }

    private static Species findSpecies(String query) {
        String normalized = query.toLowerCase().replace(" ", "_").replace("-", "_");

        for (Species species : PokemonSpecies.INSTANCE.getSpecies()) {
            if (species.getName().toLowerCase().equals(normalized) ||
                species.getName().toLowerCase().replace("_", "").equals(normalized.replace("_", ""))) {
                return species;
            }
        }

        for (Species species : PokemonSpecies.INSTANCE.getSpecies()) {
            if (fuzzyMatch(normalized, species.getName().toLowerCase())) {
                return species;
            }
        }
        return null;
    }

    private static FormData findForm(Species species, String formQuery) {
        String normalized = formQuery.toLowerCase();
        for (FormData form : species.getForms()) {
            String formName = form.getName().toLowerCase();
            if (formName.equals(normalized) || formName.startsWith(normalized) ||
                formName.replace("_", "").startsWith(normalized.replace("_", ""))) {
                return form;
            }
        }
        return null;
    }

    private static MoveTemplate findMove(String query) {
        String normalized = query.toLowerCase().replace(" ", "_").replace("-", "_");

        MoveTemplate move = Moves.INSTANCE.getByName(normalized);
        if (move != null) return move;

        move = Moves.INSTANCE.getByName(normalized.replace("_", ""));
        if (move != null) return move;

        for (MoveTemplate m : Moves.INSTANCE.all()) {
            if (fuzzyMatch(normalized, m.getName().toLowerCase())) {
                return m;
            }
        }
        return null;
    }

    private static AbilityTemplate findAbility(String query) {
        String normalized = query.toLowerCase().replace(" ", "_").replace("-", "_");

        AbilityTemplate ability = Abilities.INSTANCE.get(normalized);
        if (ability != null) return ability;

        for (AbilityTemplate a : Abilities.INSTANCE.all()) {
            if (fuzzyMatch(normalized, a.getName().toLowerCase())) {
                return a;
            }
        }
        return null;
    }

    private static ElementalType findType(String query) {
        String normalized = query.toLowerCase().replace(" ", "_");
        ElementalType type = ElementalTypes.INSTANCE.get(normalized);
        if (type != null) return type;

        for (ElementalType t : ElementalTypes.INSTANCE.all()) {
            if (fuzzyMatch(normalized, t.getName().toLowerCase())) {
                return t;
            }
        }
        return null;
    }

    private static Item findCobblemonItem(String query) {
        String normalized = query.toLowerCase().replace(" ", "_").replace("-", "_");

        ResourceLocation location = new ResourceLocation(Cobblemon.MODID, normalized);
        Item item = ForgeRegistries.ITEMS.getValue(location);
        if (item != null && item != net.minecraft.world.item.Items.AIR) {
            return item;
        }

        for (var entry : ForgeRegistries.ITEMS.getEntries()) {
            ResourceLocation key = entry.getKey().location();
            if (key.getNamespace().equals(Cobblemon.MODID)) {
                String itemName = key.getPath().toLowerCase();
                if (fuzzyMatch(normalized, itemName)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static List<String> findSimilarMatches(String query, int limit) {
        String normalized = query.toLowerCase();
        List<String> matches = new ArrayList<>();

        for (Species species : PokemonSpecies.INSTANCE.getSpecies()) {
            if (species.getName().toLowerCase().contains(normalized) ||
                normalized.contains(species.getName().toLowerCase().substring(0, Math.min(3, species.getName().length())))) {
                matches.add(species.getName());
            }
        }

        for (MoveTemplate move : Moves.INSTANCE.all()) {
            if (move.getName().toLowerCase().contains(normalized)) {
                matches.add(formatDisplayName(move.getName()));
            }
        }

        for (AbilityTemplate ability : Abilities.INSTANCE.all()) {
            if (ability.getName().toLowerCase().contains(normalized)) {
                matches.add(formatDisplayName(ability.getName()));
            }
        }

        return matches.stream().distinct().limit(limit).collect(Collectors.toList());
    }

    // matches prefix, substring, word-prefix, and first-letter abbreviation
    private static boolean fuzzyMatch(String query, String target) {
        if (target.startsWith(query)) return true;
        if (target.contains(query)) return true;

        String[] targetWords = target.split("_");
        String[] queryWords = query.split("_");

        if (queryWords.length <= targetWords.length) {
            boolean allMatch = true;
            for (int i = 0; i < queryWords.length && i < targetWords.length; i++) {
                if (!targetWords[i].startsWith(queryWords[i])) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) return true;
        }

        if (query.length() <= targetWords.length) {
            StringBuilder abbrev = new StringBuilder();
            for (String word : targetWords) {
                if (!word.isEmpty()) abbrev.append(word.charAt(0));
            }
            if (abbrev.toString().startsWith(query)) return true;
        }

        return false;
    }

    private static String formatDisplayName(String name) {
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

    private static MutableComponent createClickableDt(Component displayText, String query, ChatFormatting... styles) {
        MutableComponent component = displayText.copy();
        for (ChatFormatting style : styles) {
            component = component.withStyle(style);
        }
        component = component.withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/showdown dt " + query))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("command.cobblemon_showdown.dt.clickable", displayText).withStyle(ChatFormatting.YELLOW))));
        return component;
    }

    private static MutableComponent createClickableType(ElementalType type) {
        int color = type.getHue();
        Component displayName = type.getDisplayName();
        return displayName.copy()
                .setStyle(Style.EMPTY.withBold(true).withColor(color))
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/showdown dt " + type.getName()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("command.cobblemon_showdown.dt.clickable.type_info").withStyle(ChatFormatting.YELLOW))));
    }

    private static MutableComponent createClickableTypeByName(String typeName) {
        ElementalType type = ElementalTypes.INSTANCE.get(typeName);
        int color = type != null ? type.getHue() : getTypeColor(typeName).getColor();
        Component displayName = type != null ? type.getDisplayName() : Component.literal(formatDisplayName(typeName));
        return displayName.copy()
                .setStyle(Style.EMPTY.withColor(color))
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/showdown dt " + typeName))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("command.cobblemon_showdown.dt.clickable.type_info").withStyle(ChatFormatting.YELLOW))));
    }

    private static MutableComponent createClickableTypeList(List<String> typeNames) {
        MutableComponent result = Component.empty();
        for (int i = 0; i < typeNames.size(); i++) {
            result.append(createClickableTypeByName(typeNames.get(i)));
            if (i < typeNames.size() - 1) {
                result.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
        }
        return result;
    }

    private static ChatFormatting getTypeColor(String typeName) {
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

    private static ChatFormatting getStatColor(int stat) {
        if (stat >= 130) return ChatFormatting.GREEN;
        if (stat >= 100) return ChatFormatting.AQUA;
        if (stat >= 70) return ChatFormatting.YELLOW;
        if (stat >= 50) return ChatFormatting.GOLD;
        return ChatFormatting.RED;
    }

    private static Component getMoveDescription(MoveTemplate move) {
        // modifications from other mods override the default description
        MoveModification mod = ShowdownAPI.getMoveModification(move.getName());
        if (mod != null && mod.getDescription() != null) {
            return Component.literal(mod.getDescription());
        }

        CustomMove custom = ShowdownAPI.getMove(move.getName());
        if (custom != null && !custom.getDescription().isEmpty()) {
            return Component.literal(custom.getDescription());
        }

        try {
            var desc = move.getDescription();
            if (desc != null) {
                return desc;
            }
        } catch (Exception e) {
            // description not available
        }
        return null;
    }

    private static String getAbilityDescription(AbilityTemplate ability) {
        // modifications from other mods override the default description
        AbilityModification mod = ShowdownAPI.getAbilityModification(ability.getName());
        if (mod != null && mod.getDescription() != null) {
            return mod.getDescription();
        }

        CustomAbility custom = ShowdownAPI.getAbility(ability.getName());
        if (custom != null && !custom.getDescription().isEmpty()) {
            return custom.getDescription();
        }

        try {
            String descKey = ability.getDescription();
            if (descKey != null && !descKey.isEmpty()) {
                Component translated = Component.translatable(descKey);
                String text = translated.getString();
                if (!text.equals(descKey)) {
                    return text;
                }
            }
        } catch (Exception e) {
            // description not available
        }
        return null;
    }

    private static void initTypeChart() {
        addTypeMatchup("normal", "rock", 0.5);
        addTypeMatchup("normal", "ghost", 0.0);
        addTypeMatchup("normal", "steel", 0.5);

        addTypeMatchup("fire", "fire", 0.5);
        addTypeMatchup("fire", "water", 0.5);
        addTypeMatchup("fire", "grass", 2.0);
        addTypeMatchup("fire", "ice", 2.0);
        addTypeMatchup("fire", "bug", 2.0);
        addTypeMatchup("fire", "rock", 0.5);
        addTypeMatchup("fire", "dragon", 0.5);
        addTypeMatchup("fire", "steel", 2.0);

        addTypeMatchup("water", "fire", 2.0);
        addTypeMatchup("water", "water", 0.5);
        addTypeMatchup("water", "grass", 0.5);
        addTypeMatchup("water", "ground", 2.0);
        addTypeMatchup("water", "rock", 2.0);
        addTypeMatchup("water", "dragon", 0.5);

        addTypeMatchup("grass", "fire", 0.5);
        addTypeMatchup("grass", "water", 2.0);
        addTypeMatchup("grass", "grass", 0.5);
        addTypeMatchup("grass", "poison", 0.5);
        addTypeMatchup("grass", "ground", 2.0);
        addTypeMatchup("grass", "flying", 0.5);
        addTypeMatchup("grass", "bug", 0.5);
        addTypeMatchup("grass", "rock", 2.0);
        addTypeMatchup("grass", "dragon", 0.5);
        addTypeMatchup("grass", "steel", 0.5);

        addTypeMatchup("electric", "water", 2.0);
        addTypeMatchup("electric", "grass", 0.5);
        addTypeMatchup("electric", "electric", 0.5);
        addTypeMatchup("electric", "ground", 0.0);
        addTypeMatchup("electric", "flying", 2.0);
        addTypeMatchup("electric", "dragon", 0.5);

        addTypeMatchup("ice", "fire", 0.5);
        addTypeMatchup("ice", "water", 0.5);
        addTypeMatchup("ice", "grass", 2.0);
        addTypeMatchup("ice", "ice", 0.5);
        addTypeMatchup("ice", "ground", 2.0);
        addTypeMatchup("ice", "flying", 2.0);
        addTypeMatchup("ice", "dragon", 2.0);
        addTypeMatchup("ice", "steel", 0.5);

        addTypeMatchup("fighting", "normal", 2.0);
        addTypeMatchup("fighting", "ice", 2.0);
        addTypeMatchup("fighting", "poison", 0.5);
        addTypeMatchup("fighting", "flying", 0.5);
        addTypeMatchup("fighting", "psychic", 0.5);
        addTypeMatchup("fighting", "bug", 0.5);
        addTypeMatchup("fighting", "rock", 2.0);
        addTypeMatchup("fighting", "ghost", 0.0);
        addTypeMatchup("fighting", "dark", 2.0);
        addTypeMatchup("fighting", "steel", 2.0);
        addTypeMatchup("fighting", "fairy", 0.5);

        addTypeMatchup("poison", "grass", 2.0);
        addTypeMatchup("poison", "poison", 0.5);
        addTypeMatchup("poison", "ground", 0.5);
        addTypeMatchup("poison", "rock", 0.5);
        addTypeMatchup("poison", "ghost", 0.5);
        addTypeMatchup("poison", "steel", 0.0);
        addTypeMatchup("poison", "fairy", 2.0);

        addTypeMatchup("ground", "fire", 2.0);
        addTypeMatchup("ground", "grass", 0.5);
        addTypeMatchup("ground", "electric", 2.0);
        addTypeMatchup("ground", "poison", 2.0);
        addTypeMatchup("ground", "flying", 0.0);
        addTypeMatchup("ground", "bug", 0.5);
        addTypeMatchup("ground", "rock", 2.0);
        addTypeMatchup("ground", "steel", 2.0);

        addTypeMatchup("flying", "grass", 2.0);
        addTypeMatchup("flying", "electric", 0.5);
        addTypeMatchup("flying", "fighting", 2.0);
        addTypeMatchup("flying", "bug", 2.0);
        addTypeMatchup("flying", "rock", 0.5);
        addTypeMatchup("flying", "steel", 0.5);

        addTypeMatchup("psychic", "fighting", 2.0);
        addTypeMatchup("psychic", "poison", 2.0);
        addTypeMatchup("psychic", "psychic", 0.5);
        addTypeMatchup("psychic", "dark", 0.0);
        addTypeMatchup("psychic", "steel", 0.5);

        addTypeMatchup("bug", "fire", 0.5);
        addTypeMatchup("bug", "grass", 2.0);
        addTypeMatchup("bug", "fighting", 0.5);
        addTypeMatchup("bug", "poison", 0.5);
        addTypeMatchup("bug", "flying", 0.5);
        addTypeMatchup("bug", "psychic", 2.0);
        addTypeMatchup("bug", "ghost", 0.5);
        addTypeMatchup("bug", "dark", 2.0);
        addTypeMatchup("bug", "steel", 0.5);
        addTypeMatchup("bug", "fairy", 0.5);

        addTypeMatchup("rock", "fire", 2.0);
        addTypeMatchup("rock", "ice", 2.0);
        addTypeMatchup("rock", "fighting", 0.5);
        addTypeMatchup("rock", "ground", 0.5);
        addTypeMatchup("rock", "flying", 2.0);
        addTypeMatchup("rock", "bug", 2.0);
        addTypeMatchup("rock", "steel", 0.5);

        addTypeMatchup("ghost", "normal", 0.0);
        addTypeMatchup("ghost", "psychic", 2.0);
        addTypeMatchup("ghost", "ghost", 2.0);
        addTypeMatchup("ghost", "dark", 0.5);

        addTypeMatchup("dragon", "dragon", 2.0);
        addTypeMatchup("dragon", "steel", 0.5);
        addTypeMatchup("dragon", "fairy", 0.0);

        addTypeMatchup("dark", "fighting", 0.5);
        addTypeMatchup("dark", "psychic", 2.0);
        addTypeMatchup("dark", "ghost", 2.0);
        addTypeMatchup("dark", "dark", 0.5);
        addTypeMatchup("dark", "fairy", 0.5);

        addTypeMatchup("steel", "fire", 0.5);
        addTypeMatchup("steel", "water", 0.5);
        addTypeMatchup("steel", "electric", 0.5);
        addTypeMatchup("steel", "ice", 2.0);
        addTypeMatchup("steel", "rock", 2.0);
        addTypeMatchup("steel", "steel", 0.5);
        addTypeMatchup("steel", "fairy", 2.0);

        addTypeMatchup("fairy", "fire", 0.5);
        addTypeMatchup("fairy", "fighting", 2.0);
        addTypeMatchup("fairy", "poison", 0.5);
        addTypeMatchup("fairy", "dragon", 2.0);
        addTypeMatchup("fairy", "dark", 2.0);
        addTypeMatchup("fairy", "steel", 0.5);
    }

    private static void addTypeMatchup(String attacker, String defender, double multiplier) {
        TYPE_CHART.computeIfAbsent(attacker, k -> new HashMap<>()).put(defender, multiplier);
    }

    private static boolean tryShowCustomAbility(ServerPlayer player, String query) {
        CustomAbility ability = ShowdownAPI.getAbility(query);
        if (ability == null) return false;
        showCustomAbilityData(player, ability);
        return true;
    }

    private static boolean tryShowCustomMove(ServerPlayer player, String query) {
        CustomMove move = ShowdownAPI.getMove(query);
        if (move == null) return false;
        showCustomMoveData(player, move);
        return true;
    }

    private static void showCustomAbilityData(ServerPlayer player, CustomAbility ability) {
        player.sendSystemMessage(Component.translatable(
            "command.cobblemon_showdown.dt.ability",
                Component.literal(ability.getDisplayName()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
            ).withStyle(ChatFormatting.GOLD));

        player.sendSystemMessage(Component.translatable(
            "command.cobblemon_showdown.dt.mod",
                Component.literal(ability.getModId()).withStyle(ChatFormatting.AQUA)
            ).withStyle(ChatFormatting.GRAY));

        if (!ability.getDescription().isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                "command.cobblemon_showdown.dt.description",
                Component.literal(ability.getDescription()).withStyle(ChatFormatting.WHITE)
            ).withStyle(ChatFormatting.GRAY));
        }
    }

    private static void showCustomMoveData(ServerPlayer player, CustomMove move) {
        player.sendSystemMessage(Component.translatable(
            "command.cobblemon_showdown.dt.move",
                Component.literal(move.getDisplayName()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
        ).withStyle(ChatFormatting.GOLD));

        ChatFormatting typeColor = getTypeColor(move.getType());
        player.sendSystemMessage(Component.empty().withStyle(ChatFormatting.GRAY)
                .append(Component.translatable(
                    "tooltip.cobblemon_showdown.move_info.type",
                    Component.literal(move.getType()).withStyle(typeColor, ChatFormatting.BOLD)))
                .append(Component.literal(" | "))
                .append(Component.translatable(
                    "tooltip.cobblemon_showdown.move_info.category",
                    Component.literal(move.getCategory()).withStyle(ChatFormatting.WHITE))));

        String powerStr = move.getPower() > 0 ? String.valueOf(move.getPower()) : "—";
        String accStr = move.getAccuracy() > 0 ? move.getAccuracy() + "%" : "—";
        player.sendSystemMessage(Component.empty().withStyle(ChatFormatting.GRAY)
                .append(Component.translatable(
                    "tooltip.cobblemon_showdown.move_info.power",
                    Component.literal(powerStr).withStyle(ChatFormatting.WHITE)))
                .append(Component.literal(" | "))
                .append(Component.translatable(
                    "tooltip.cobblemon_showdown.move_info.accuracy",
                    Component.literal(accStr).withStyle(ChatFormatting.WHITE)))
                .append(Component.literal(" | "))
                .append(Component.translatable(
                    "tooltip.cobblemon_showdown.move_info.pp",
                    Component.literal(String.valueOf(move.getPp())).withStyle(ChatFormatting.WHITE))));

        if (move.getPriority() != 0) {
            String priorityStr = move.getPriority() > 0 ? "+" + move.getPriority() : String.valueOf(move.getPriority());
            player.sendSystemMessage(Component.translatable(
                "tooltip.cobblemon_showdown.move_info.priority",
                    Component.literal(priorityStr).withStyle(ChatFormatting.YELLOW)
            ).withStyle(ChatFormatting.GRAY));
        }

        player.sendSystemMessage(Component.translatable(
            "command.cobblemon_showdown.dt.mod",
            Component.literal(move.getModId()).withStyle(ChatFormatting.AQUA)
        ).withStyle(ChatFormatting.GRAY));

        if (!move.getDescription().isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                "command.cobblemon_showdown.dt.description",
                Component.literal(move.getDescription()).withStyle(ChatFormatting.WHITE)
            ).withStyle(ChatFormatting.GRAY));
        }
    }
}
