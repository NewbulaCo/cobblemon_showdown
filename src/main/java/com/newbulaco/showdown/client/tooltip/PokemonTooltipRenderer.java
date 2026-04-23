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
            tooltipLines.add(Component.translatable(
                "tooltip.cobblemon_showdown.pokemon_info",
                ShowdownKeybinds.getShowInfoKeyName().copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC)
            ).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
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
        MutableComponent header = pokemon.getDisplayName().copy()
            .withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE);
        header.append(Component.translatable("tooltip.cobblemon_showdown.pokemon_info.level", pokemon.getLevel())
            .withStyle(ChatFormatting.GRAY));
        lines.add(header);

        MutableComponent typeStr = Component.empty();
        ElementalType primaryType = pokemon.getPrimaryType();
        typeStr.append(primaryType.getDisplayName());

        ElementalType secondaryType = pokemon.getSecondaryType();
        if (secondaryType != null) {
            typeStr.append(" / ").append(secondaryType.getDisplayName());
        }
        lines.add(Component.translatable(
            "tooltip.cobblemon_showdown.pokemon_info.type",
            typeStr.withStyle(ChatFormatting.AQUA)
        ).withStyle(ChatFormatting.GRAY));

        String abilityKey = pokemon.getAbility() != null ? pokemon.getAbility().getDisplayName() : "";
        lines.add(Component.translatable(
            "tooltip.cobblemon_showdown.pokemon_info.ability",
            Component.translatable(abilityKey).withStyle(ChatFormatting.GOLD)
        ).withStyle(ChatFormatting.GRAY));

        String natureKey = pokemon.getNature().getDisplayName();
        lines.add(Component.translatable(
            "tooltip.cobblemon_showdown.pokemon_info.nature",
            Component.translatable(natureKey).withStyle(ChatFormatting.GREEN)
        ).withStyle(ChatFormatting.GRAY));

        lines.add(Component.translatable(
            "tooltip.cobblemon_showdown.pokemon_info.stats",
            Component.literal(String.format("%d/%d/%d/%d/%d/%d",
                pokemon.getHp(),
                pokemon.getAttack(),
                pokemon.getDefence(),
                pokemon.getSpecialAttack(),
                pokemon.getSpecialDefence(),
                pokemon.getSpeed())
            ).withStyle(ChatFormatting.GREEN)
        ).withStyle(ChatFormatting.GRAY));

        lines.add(Component.translatable("tooltip.cobblemon_showdown.pokemon_info.moves")
            .withStyle(ChatFormatting.GRAY));
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
        lines.add(Component.translatable("tooltip.cobblemon_showdown.pokemon_info.detail").withStyle(ChatFormatting.YELLOW));

        int hpIV = pokemon.getIvs().getOrDefault(Stats.HP);
        int atkIV = pokemon.getIvs().getOrDefault(Stats.ATTACK);
        int defIV = pokemon.getIvs().getOrDefault(Stats.DEFENCE);
        int spAtkIV = pokemon.getIvs().getOrDefault(Stats.SPECIAL_ATTACK);
        int spDefIV = pokemon.getIvs().getOrDefault(Stats.SPECIAL_DEFENCE);
        int spdIV = pokemon.getIvs().getOrDefault(Stats.SPEED);

        lines.add(Component.translatable(
            "tooltip.cobblemon_showdown.pokemon_info.iv",
            Component.literal(String.format("%d/%d/%d/%d/%d/%d",
                hpIV, atkIV, defIV, spAtkIV, spDefIV, spdIV)
            ).withStyle(ChatFormatting.LIGHT_PURPLE)
        ).withStyle(ChatFormatting.GRAY));

        int hpEV = pokemon.getEvs().getOrDefault(Stats.HP);
        int atkEV = pokemon.getEvs().getOrDefault(Stats.ATTACK);
        int defEV = pokemon.getEvs().getOrDefault(Stats.DEFENCE);
        int spAtkEV = pokemon.getEvs().getOrDefault(Stats.SPECIAL_ATTACK);
        int spDefEV = pokemon.getEvs().getOrDefault(Stats.SPECIAL_DEFENCE);
        int spdEV = pokemon.getEvs().getOrDefault(Stats.SPEED);

        lines.add(Component.translatable(
            "tooltip.cobblemon_showdown.pokemon_info.ev",
            Component.literal(String.format("%d/%d/%d/%d/%d/%d",
                hpEV, atkEV, defEV, spAtkEV, spDefEV, spdEV)
            ).withStyle(ChatFormatting.DARK_AQUA)
        ).withStyle(ChatFormatting.GRAY));

        var nature = pokemon.getNature();
        if (nature.getIncreasedStat() != null && nature.getDecreasedStat() != null) {
            MutableComponent changedStats = Component.empty();
            Component inc = Component.empty().withStyle(ChatFormatting.GREEN)
                .append("+").append(getStatShortName(nature.getIncreasedStat()));
            Component dec = Component.empty().withStyle(ChatFormatting.RED)
                .append("-").append(getStatShortName(nature.getDecreasedStat()));
            changedStats.append(inc).append(" / ").append(dec);
            lines.add(Component.translatable(
                "tooltip.cobblemon_showdown.pokemon_info.nature",
                changedStats
            ).withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable(
                "tooltip.cobblemon_showdown.pokemon_info.nature",
                Component.translatable("tooltip.cobblemon_showdown.pokemon_info.nature.neutral")
                    .withStyle(ChatFormatting.YELLOW)
            ).withStyle(ChatFormatting.GRAY));
        }

        MutableComponent hpType = HiddenPowerCalculator.calculateHiddenPowerType(pokemon);
        lines.add(Component.translatable(
            "tooltip.cobblemon_showdown.pokemon_info.hidden_power",
            hpType.withStyle(ChatFormatting.RED)
        ).withStyle(ChatFormatting.GRAY));

        lines.add(Component.translatable(
            "tooltip.cobblemon_showdown.pokemon_info.friendship",
            Component.literal(String.valueOf(pokemon.getFriendship())).withStyle(ChatFormatting.LIGHT_PURPLE)
        ).withStyle(ChatFormatting.GRAY));

        String otName = pokemon.getOriginalTrainerName();
        if (otName != null && !otName.isEmpty()) {
            lines.add(Component.translatable(
                "tooltip.cobblemon_showdown.pokemon_info.original_trainer",
                Component.literal(otName).withStyle(ChatFormatting.WHITE)
            ).withStyle(ChatFormatting.GRAY));
        }

        // cobblemon doesn't expose catch date directly

        if (pokemon.getShiny()) {
            lines.add(Component.translatable("tooltip.cobblemon_showdown.pokemon_info.shiny")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
    }

    private static Component getStatShortName(com.cobblemon.mod.common.api.pokemon.stats.Stat stat) {
        if (stat == Stats.HP) return Component.translatable("cobblemon_showdown.stat.hp.short");
        if (stat == Stats.ATTACK) return Component.translatable("cobblemon_showdown.stat.attack.short");
        if (stat == Stats.DEFENCE) return Component.translatable("cobblemon_showdown.stat.defence.short");
        if (stat == Stats.SPECIAL_ATTACK) return Component.translatable("cobblemon_showdown.stat.special_attack.short");
        if (stat == Stats.SPECIAL_DEFENCE) return Component.translatable("cobblemon_showdown.stat.special_defence.short");
        if (stat == Stats.SPEED) return Component.translatable("cobblemon_showdown.stat.speed.short");
        return stat.getDisplayName();
    }
}
