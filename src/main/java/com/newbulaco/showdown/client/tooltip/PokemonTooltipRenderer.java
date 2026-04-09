package com.newbulaco.showdown.client.tooltip;

import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.newbulaco.showdown.client.ShowdownKeybinds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

public class PokemonTooltipRenderer {

    public static void renderTooltip(GuiGraphics graphics, Pokemon pokemon, int mouseX, int mouseY) {
        renderTooltip(graphics, pokemon, mouseX, mouseY, false);
    }

    /**
     * @param requireShift if true, only show tooltip when shift is held (for PC to reduce clutter)
     */
    public static void renderTooltip(GuiGraphics graphics, Pokemon pokemon, int mouseX, int mouseY, boolean requireShift) {
        if (pokemon == null) return;

        if (requireShift && !ShowdownKeybinds.isShowInfoDown()) {
            return;
        }

        List<Component> tooltipLines = new ArrayList<>();

        addBasicInfo(tooltipLines, pokemon);

        if (ShowdownKeybinds.isShowInfoDown()) {
            addDetailedInfo(tooltipLines, pokemon);
        } else {
            tooltipLines.add(Component.literal("Hold ")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
                .append(ShowdownKeybinds.getShowInfoKeyName().copy()
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC))
                .append(Component.literal(" for more info")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
        }

        graphics.renderTooltip(
            Minecraft.getInstance().font,
            tooltipLines,
            java.util.Optional.empty(),
            mouseX,
            mouseY
        );
    }

    private static void addBasicInfo(List<Component> lines, Pokemon pokemon) {
        MutableComponent header = Component.literal(pokemon.getDisplayName().getString())
            .withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE);
        header.append(Component.literal(" Lv." + pokemon.getLevel())
            .withStyle(ChatFormatting.GRAY));
        lines.add(header);

        StringBuilder typeStr = new StringBuilder();
        ElementalType primaryType = pokemon.getPrimaryType();
        typeStr.append(primaryType.getDisplayName().getString());

        ElementalType secondaryType = pokemon.getSecondaryType();
        if (secondaryType != null) {
            typeStr.append(" / ").append(secondaryType.getDisplayName().getString());
        }
        lines.add(Component.literal("Type: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(typeStr.toString()).withStyle(ChatFormatting.AQUA)));

        String abilityKey = pokemon.getAbility() != null ? pokemon.getAbility().getDisplayName() : "";
        lines.add(Component.literal("Ability: ").withStyle(ChatFormatting.GRAY)
            .append(Component.translatable(abilityKey).withStyle(ChatFormatting.GOLD)));

        String natureKey = pokemon.getNature().getDisplayName();
        lines.add(Component.literal("Nature: ").withStyle(ChatFormatting.GRAY)
            .append(Component.translatable(natureKey).withStyle(ChatFormatting.GREEN)));

        lines.add(Component.literal("Stats: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(String.format("%d/%d/%d/%d/%d/%d",
                pokemon.getHp(),
                pokemon.getAttack(),
                pokemon.getDefence(),
                pokemon.getSpecialAttack(),
                pokemon.getSpecialDefence(),
                pokemon.getSpeed()))
                .withStyle(ChatFormatting.GREEN)));

        lines.add(Component.literal("Moves:").withStyle(ChatFormatting.GRAY));
        pokemon.getMoveSet().getMoves().forEach(move -> {
            String moveName = move.getDisplayName().getString();
            int currentPp = move.getCurrentPp();
            int maxPp = move.getMaxPp();

            ChatFormatting ppColor = ChatFormatting.GREEN;
            if (currentPp == 0) {
                ppColor = ChatFormatting.RED;
            } else if (currentPp <= maxPp / 4) {
                ppColor = ChatFormatting.RED;
            } else if (currentPp <= maxPp / 2) {
                ppColor = ChatFormatting.YELLOW;
            }

            lines.add(Component.literal("  - " + moveName + " ")
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal("[" + currentPp + "/" + maxPp + "]")
                    .withStyle(ppColor)));
        });
    }

    private static void addDetailedInfo(List<Component> lines, Pokemon pokemon) {
        lines.add(Component.literal(""));
        lines.add(Component.literal("--- Detailed Info ---").withStyle(ChatFormatting.YELLOW));

        int hpIV = pokemon.getIvs().getOrDefault(Stats.HP);
        int atkIV = pokemon.getIvs().getOrDefault(Stats.ATTACK);
        int defIV = pokemon.getIvs().getOrDefault(Stats.DEFENCE);
        int spAtkIV = pokemon.getIvs().getOrDefault(Stats.SPECIAL_ATTACK);
        int spDefIV = pokemon.getIvs().getOrDefault(Stats.SPECIAL_DEFENCE);
        int spdIV = pokemon.getIvs().getOrDefault(Stats.SPEED);

        lines.add(Component.literal("IVs: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(String.format("%d/%d/%d/%d/%d/%d",
                hpIV, atkIV, defIV, spAtkIV, spDefIV, spdIV))
                .withStyle(ChatFormatting.LIGHT_PURPLE)));

        int hpEV = pokemon.getEvs().getOrDefault(Stats.HP);
        int atkEV = pokemon.getEvs().getOrDefault(Stats.ATTACK);
        int defEV = pokemon.getEvs().getOrDefault(Stats.DEFENCE);
        int spAtkEV = pokemon.getEvs().getOrDefault(Stats.SPECIAL_ATTACK);
        int spDefEV = pokemon.getEvs().getOrDefault(Stats.SPECIAL_DEFENCE);
        int spdEV = pokemon.getEvs().getOrDefault(Stats.SPEED);

        lines.add(Component.literal("EVs: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(String.format("%d/%d/%d/%d/%d/%d",
                hpEV, atkEV, defEV, spAtkEV, spDefEV, spdEV))
                .withStyle(ChatFormatting.DARK_AQUA)));

        var nature = pokemon.getNature();
        if (nature.getIncreasedStat() != null && nature.getDecreasedStat() != null) {
            lines.add(Component.literal("Nature: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("+" + getStatShortName(nature.getIncreasedStat()))
                    .withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" / ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("-" + getStatShortName(nature.getDecreasedStat()))
                    .withStyle(ChatFormatting.RED)));
        } else {
            lines.add(Component.literal("Nature: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("Neutral").withStyle(ChatFormatting.YELLOW)));
        }

        String hpType = HiddenPowerCalculator.calculateHiddenPowerType(pokemon);
        lines.add(Component.literal("Hidden Power: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(hpType).withStyle(ChatFormatting.RED)));

        lines.add(Component.literal("Friendship: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(String.valueOf(pokemon.getFriendship()))
                .withStyle(ChatFormatting.LIGHT_PURPLE)));

        String otName = pokemon.getOriginalTrainerName();
        if (otName != null && !otName.isEmpty()) {
            lines.add(Component.literal("OT: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(otName).withStyle(ChatFormatting.WHITE)));
        }

        // cobblemon doesn't expose catch date directly

        if (pokemon.getShiny()) {
            lines.add(Component.literal("*** SHINY ***")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
    }

    private static String getStatShortName(com.cobblemon.mod.common.api.pokemon.stats.Stat stat) {
        if (stat == Stats.HP) return "HP";
        if (stat == Stats.ATTACK) return "Atk";
        if (stat == Stats.DEFENCE) return "Def";
        if (stat == Stats.SPECIAL_ATTACK) return "SpA";
        if (stat == Stats.SPECIAL_DEFENCE) return "SpD";
        if (stat == Stats.SPEED) return "Spe";
        return stat.toString();
    }
}
