package com.newbulaco.showdown.client.battle;

import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TypeEffectiveness {

    public static final double IMMUNE = 0.0;
    public static final double NOT_VERY_EFFECTIVE = 0.5;
    public static final double NEUTRAL = 1.0;
    public static final double SUPER_EFFECTIVE = 2.0;

    private static final Map<String, Map<String, Double>> TYPE_CHART = new HashMap<>();

    static {
        initializeTypeChart();
    }

    private static void initializeTypeChart() {
        Map<String, Double> normal = new HashMap<>();
        normal.put("rock", NOT_VERY_EFFECTIVE);
        normal.put("steel", NOT_VERY_EFFECTIVE);
        normal.put("ghost", IMMUNE);
        TYPE_CHART.put("normal", normal);

        Map<String, Double> fire = new HashMap<>();
        fire.put("fire", NOT_VERY_EFFECTIVE);
        fire.put("water", NOT_VERY_EFFECTIVE);
        fire.put("rock", NOT_VERY_EFFECTIVE);
        fire.put("dragon", NOT_VERY_EFFECTIVE);
        fire.put("grass", SUPER_EFFECTIVE);
        fire.put("ice", SUPER_EFFECTIVE);
        fire.put("bug", SUPER_EFFECTIVE);
        fire.put("steel", SUPER_EFFECTIVE);
        TYPE_CHART.put("fire", fire);

        Map<String, Double> water = new HashMap<>();
        water.put("water", NOT_VERY_EFFECTIVE);
        water.put("grass", NOT_VERY_EFFECTIVE);
        water.put("dragon", NOT_VERY_EFFECTIVE);
        water.put("fire", SUPER_EFFECTIVE);
        water.put("ground", SUPER_EFFECTIVE);
        water.put("rock", SUPER_EFFECTIVE);
        TYPE_CHART.put("water", water);

        Map<String, Double> grass = new HashMap<>();
        grass.put("fire", NOT_VERY_EFFECTIVE);
        grass.put("grass", NOT_VERY_EFFECTIVE);
        grass.put("poison", NOT_VERY_EFFECTIVE);
        grass.put("flying", NOT_VERY_EFFECTIVE);
        grass.put("bug", NOT_VERY_EFFECTIVE);
        grass.put("dragon", NOT_VERY_EFFECTIVE);
        grass.put("steel", NOT_VERY_EFFECTIVE);
        grass.put("water", SUPER_EFFECTIVE);
        grass.put("ground", SUPER_EFFECTIVE);
        grass.put("rock", SUPER_EFFECTIVE);
        TYPE_CHART.put("grass", grass);

        Map<String, Double> electric = new HashMap<>();
        electric.put("electric", NOT_VERY_EFFECTIVE);
        electric.put("grass", NOT_VERY_EFFECTIVE);
        electric.put("dragon", NOT_VERY_EFFECTIVE);
        electric.put("ground", IMMUNE);
        electric.put("water", SUPER_EFFECTIVE);
        electric.put("flying", SUPER_EFFECTIVE);
        TYPE_CHART.put("electric", electric);

        Map<String, Double> ice = new HashMap<>();
        ice.put("fire", NOT_VERY_EFFECTIVE);
        ice.put("water", NOT_VERY_EFFECTIVE);
        ice.put("ice", NOT_VERY_EFFECTIVE);
        ice.put("steel", NOT_VERY_EFFECTIVE);
        ice.put("grass", SUPER_EFFECTIVE);
        ice.put("ground", SUPER_EFFECTIVE);
        ice.put("flying", SUPER_EFFECTIVE);
        ice.put("dragon", SUPER_EFFECTIVE);
        TYPE_CHART.put("ice", ice);

        Map<String, Double> fighting = new HashMap<>();
        fighting.put("poison", NOT_VERY_EFFECTIVE);
        fighting.put("flying", NOT_VERY_EFFECTIVE);
        fighting.put("psychic", NOT_VERY_EFFECTIVE);
        fighting.put("bug", NOT_VERY_EFFECTIVE);
        fighting.put("fairy", NOT_VERY_EFFECTIVE);
        fighting.put("ghost", IMMUNE);
        fighting.put("normal", SUPER_EFFECTIVE);
        fighting.put("ice", SUPER_EFFECTIVE);
        fighting.put("rock", SUPER_EFFECTIVE);
        fighting.put("dark", SUPER_EFFECTIVE);
        fighting.put("steel", SUPER_EFFECTIVE);
        TYPE_CHART.put("fighting", fighting);

        Map<String, Double> poison = new HashMap<>();
        poison.put("poison", NOT_VERY_EFFECTIVE);
        poison.put("ground", NOT_VERY_EFFECTIVE);
        poison.put("rock", NOT_VERY_EFFECTIVE);
        poison.put("ghost", NOT_VERY_EFFECTIVE);
        poison.put("steel", IMMUNE);
        poison.put("grass", SUPER_EFFECTIVE);
        poison.put("fairy", SUPER_EFFECTIVE);
        TYPE_CHART.put("poison", poison);

        Map<String, Double> ground = new HashMap<>();
        ground.put("grass", NOT_VERY_EFFECTIVE);
        ground.put("bug", NOT_VERY_EFFECTIVE);
        ground.put("flying", IMMUNE);
        ground.put("fire", SUPER_EFFECTIVE);
        ground.put("electric", SUPER_EFFECTIVE);
        ground.put("poison", SUPER_EFFECTIVE);
        ground.put("rock", SUPER_EFFECTIVE);
        ground.put("steel", SUPER_EFFECTIVE);
        TYPE_CHART.put("ground", ground);

        Map<String, Double> flying = new HashMap<>();
        flying.put("electric", NOT_VERY_EFFECTIVE);
        flying.put("rock", NOT_VERY_EFFECTIVE);
        flying.put("steel", NOT_VERY_EFFECTIVE);
        flying.put("grass", SUPER_EFFECTIVE);
        flying.put("fighting", SUPER_EFFECTIVE);
        flying.put("bug", SUPER_EFFECTIVE);
        TYPE_CHART.put("flying", flying);

        Map<String, Double> psychic = new HashMap<>();
        psychic.put("psychic", NOT_VERY_EFFECTIVE);
        psychic.put("steel", NOT_VERY_EFFECTIVE);
        psychic.put("dark", IMMUNE);
        psychic.put("fighting", SUPER_EFFECTIVE);
        psychic.put("poison", SUPER_EFFECTIVE);
        TYPE_CHART.put("psychic", psychic);

        Map<String, Double> bug = new HashMap<>();
        bug.put("fire", NOT_VERY_EFFECTIVE);
        bug.put("fighting", NOT_VERY_EFFECTIVE);
        bug.put("poison", NOT_VERY_EFFECTIVE);
        bug.put("flying", NOT_VERY_EFFECTIVE);
        bug.put("ghost", NOT_VERY_EFFECTIVE);
        bug.put("steel", NOT_VERY_EFFECTIVE);
        bug.put("fairy", NOT_VERY_EFFECTIVE);
        bug.put("grass", SUPER_EFFECTIVE);
        bug.put("psychic", SUPER_EFFECTIVE);
        bug.put("dark", SUPER_EFFECTIVE);
        TYPE_CHART.put("bug", bug);

        Map<String, Double> rock = new HashMap<>();
        rock.put("fighting", NOT_VERY_EFFECTIVE);
        rock.put("ground", NOT_VERY_EFFECTIVE);
        rock.put("steel", NOT_VERY_EFFECTIVE);
        rock.put("fire", SUPER_EFFECTIVE);
        rock.put("ice", SUPER_EFFECTIVE);
        rock.put("flying", SUPER_EFFECTIVE);
        rock.put("bug", SUPER_EFFECTIVE);
        TYPE_CHART.put("rock", rock);

        Map<String, Double> ghost = new HashMap<>();
        ghost.put("dark", NOT_VERY_EFFECTIVE);
        ghost.put("normal", IMMUNE);
        ghost.put("psychic", SUPER_EFFECTIVE);
        ghost.put("ghost", SUPER_EFFECTIVE);
        TYPE_CHART.put("ghost", ghost);

        Map<String, Double> dragon = new HashMap<>();
        dragon.put("steel", NOT_VERY_EFFECTIVE);
        dragon.put("fairy", IMMUNE);
        dragon.put("dragon", SUPER_EFFECTIVE);
        TYPE_CHART.put("dragon", dragon);

        Map<String, Double> dark = new HashMap<>();
        dark.put("fighting", NOT_VERY_EFFECTIVE);
        dark.put("dark", NOT_VERY_EFFECTIVE);
        dark.put("fairy", NOT_VERY_EFFECTIVE);
        dark.put("psychic", SUPER_EFFECTIVE);
        dark.put("ghost", SUPER_EFFECTIVE);
        TYPE_CHART.put("dark", dark);

        Map<String, Double> steel = new HashMap<>();
        steel.put("fire", NOT_VERY_EFFECTIVE);
        steel.put("water", NOT_VERY_EFFECTIVE);
        steel.put("electric", NOT_VERY_EFFECTIVE);
        steel.put("steel", NOT_VERY_EFFECTIVE);
        steel.put("ice", SUPER_EFFECTIVE);
        steel.put("rock", SUPER_EFFECTIVE);
        steel.put("fairy", SUPER_EFFECTIVE);
        TYPE_CHART.put("steel", steel);

        Map<String, Double> fairy = new HashMap<>();
        fairy.put("fire", NOT_VERY_EFFECTIVE);
        fairy.put("poison", NOT_VERY_EFFECTIVE);
        fairy.put("steel", NOT_VERY_EFFECTIVE);
        fairy.put("fighting", SUPER_EFFECTIVE);
        fairy.put("dragon", SUPER_EFFECTIVE);
        fairy.put("dark", SUPER_EFFECTIVE);
        TYPE_CHART.put("fairy", fairy);
    }

    public static double getEffectiveness(String attackingType, String defendingType1, String defendingType2) {
        if (attackingType == null || attackingType.isEmpty()) return NEUTRAL;
        if (defendingType1 == null || defendingType1.isEmpty()) return NEUTRAL;

        String attacker = attackingType.toLowerCase();
        String defender1 = defendingType1.toLowerCase();

        double multiplier = getTypeMultiplier(attacker, defender1);

        if (defendingType2 != null && !defendingType2.isEmpty()) {
            String defender2 = defendingType2.toLowerCase();
            multiplier *= getTypeMultiplier(attacker, defender2);
        }

        return multiplier;
    }

    public static double getEffectiveness(String attackingType, Set<String> defendingTypes) {
        if (attackingType == null || attackingType.isEmpty()) return NEUTRAL;
        if (defendingTypes == null || defendingTypes.isEmpty()) return NEUTRAL;

        String attacker = attackingType.toLowerCase();
        double multiplier = NEUTRAL;

        for (String defender : defendingTypes) {
            if (defender != null && !defender.isEmpty()) {
                multiplier *= getTypeMultiplier(attacker, defender.toLowerCase());
            }
        }

        return multiplier;
    }

    private static double getTypeMultiplier(String attacker, String defender) {
        Map<String, Double> attackerChart = TYPE_CHART.get(attacker);
        if (attackerChart == null) return NEUTRAL;

        Double multiplier = attackerChart.get(defender);
        return multiplier != null ? multiplier : NEUTRAL;
    }

    public static String getEffectivenessText(double multiplier) {
        if (multiplier == 0) return "Immune";
        if (multiplier == 0.25) return "x0.25";
        if (multiplier == 0.5) return "x0.5";
        if (multiplier == 1) return "";
        if (multiplier == 2) return "x2";
        if (multiplier == 4) return "x4";
        return String.format("x%.2f", multiplier);
    }

    public static int getEffectivenessColor(double multiplier) {
        if (multiplier == 0) return 0xFF888888;
        if (multiplier < 1) return 0xFFFF6666;
        if (multiplier == 1) return 0xFFFFFFFF;
        if (multiplier > 1) return 0xFF66FF66;
        return 0xFFFFFFFF;
    }

    public enum EffectivenessCategory {
        IMMUNE(0xFF888888, "Immune", Component.translatable("cobblemon_showdown.battle_overlay.effectiveness.immune")),
        QUARTER(0xFFFF4444, "x0.25", Component.translatable("cobblemon_showdown.battle_overlay.effectiveness", 0.25)),
        HALF(0xFFFF8866, "x0.5", Component.translatable("cobblemon_showdown.battle_overlay.effectiveness", 0.5)),
        NEUTRAL(0xFFFFFFFF, "", Component.empty()),
        DOUBLE(0xFF88FF88, "x2", Component.translatable("cobblemon_showdown.battle_overlay.effectiveness", 2)),
        QUADRUPLE(0xFF44FF44, "x4", Component.translatable("cobblemon_showdown.battle_overlay.effectiveness", 4));

        public final int color;
        public final String text;
        public final Component translatedText;

        EffectivenessCategory(int color, String text, Component translatedText) {
            this.color = color;
            this.text = text;
            this.translatedText = translatedText;
        }

        public static EffectivenessCategory fromMultiplier(double multiplier) {
            if (multiplier == 0) return IMMUNE;
            if (multiplier == 0.25) return QUARTER;
            if (multiplier == 0.5) return HALF;
            if (multiplier == 1) return NEUTRAL;
            if (multiplier == 2) return DOUBLE;
            if (multiplier >= 4) return QUADRUPLE;
            if (multiplier < 1) return HALF;
            return DOUBLE;
        }
    }
}
