package com.newbulaco.showdown.format;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class Format {
    private String name;

    @SerializedName("party_size")
    private int partySize = 3;

    @SerializedName("battle_type")
    private String battleType = "singles";

    private String description = "";

    @SerializedName("best_of")
    private int bestOf = 1;

    @SerializedName("set_level")
    private Integer setLevel = null;

    @SerializedName("battle_timer")
    private boolean battleTimer = false;

    @SerializedName("team_preview")
    private boolean teamPreview = false;

    @SerializedName("species_clause")
    private boolean speciesClause = false;

    private FormatBans bans = new FormatBans();

    @SerializedName("ban_all_pokemon_except_whitelist")
    private boolean banAllExceptWhitelist = false;

    @SerializedName("whitelist_species")
    private List<String> whitelistSpecies = new ArrayList<>();

    public Format() {}

    public Format(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPartySize() {
        return partySize;
    }

    public void setPartySize(int partySize) {
        this.partySize = Math.max(1, Math.min(6, partySize));
    }

    public String getBattleType() {
        return battleType != null ? battleType : "singles";
    }

    public void setBattleType(String battleType) {
        if (battleType == null) {
            this.battleType = "singles";
            return;
        }
        String normalized = battleType.toLowerCase();
        if (!normalized.equals("singles") && !normalized.equals("doubles")) {
            throw new IllegalArgumentException("battle_type must be \"singles\" or \"doubles\"");
        }
        this.battleType = normalized;
    }

    public boolean isDoubles() {
        return "doubles".equalsIgnoreCase(battleType);
    }

    public int getSlotsPerActor() {
        return isDoubles() ? 2 : 1;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description != null ? description : "";
    }

    public int getBestOf() {
        return bestOf;
    }

    public void setBestOf(int bestOf) {
        if (bestOf == 1 || bestOf == 3 || bestOf == 5) {
            this.bestOf = bestOf;
        } else {
            throw new IllegalArgumentException("best_of must be 1, 3, or 5");
        }
    }

    public Integer getSetLevel() {
        return setLevel;
    }

    public void setSetLevel(Integer setLevel) {
        if (setLevel != null) {
            this.setLevel = Math.max(1, Math.min(100, setLevel));
        } else {
            this.setLevel = null;
        }
    }

    public boolean hasBattleTimer() {
        return battleTimer;
    }

    public void setBattleTimer(boolean battleTimer) {
        this.battleTimer = battleTimer;
    }

    public boolean hasTeamPreview() {
        return teamPreview;
    }

    public void setTeamPreview(boolean teamPreview) {
        this.teamPreview = teamPreview;
    }

    public boolean hasSpeciesClause() {
        return speciesClause;
    }

    public void setSpeciesClause(boolean speciesClause) {
        this.speciesClause = speciesClause;
    }

    public FormatBans getBans() {
        return bans;
    }

    public void setBans(FormatBans bans) {
        this.bans = bans != null ? bans : new FormatBans();
    }

    public boolean isBanAllExceptWhitelist() {
        return banAllExceptWhitelist;
    }

    public void setBanAllExceptWhitelist(boolean banAllExceptWhitelist) {
        this.banAllExceptWhitelist = banAllExceptWhitelist;
    }

    public List<String> getWhitelistSpecies() {
        return whitelistSpecies;
    }

    public void setWhitelistSpecies(List<String> whitelistSpecies) {
        this.whitelistSpecies = whitelistSpecies != null ? whitelistSpecies : new ArrayList<>();
    }

    public static class FormatBans {
        private List<String> pokemon = new ArrayList<>();
        private List<String> moves = new ArrayList<>();
        private List<String> abilities = new ArrayList<>();
        private List<String> items = new ArrayList<>();

        public List<String> getPokemon() {
            return pokemon;
        }

        public void setPokemon(List<String> pokemon) {
            this.pokemon = pokemon != null ? pokemon : new ArrayList<>();
        }

        public List<String> getMoves() {
            return moves;
        }

        public void setMoves(List<String> moves) {
            this.moves = moves != null ? moves : new ArrayList<>();
        }

        public List<String> getAbilities() {
            return abilities;
        }

        public void setAbilities(List<String> abilities) {
            this.abilities = abilities != null ? abilities : new ArrayList<>();
        }

        public List<String> getItems() {
            return items;
        }

        public void setItems(List<String> items) {
            this.items = items != null ? items : new ArrayList<>();
        }
    }
}
