package com.newbulaco.showdown.client;

import com.newbulaco.showdown.network.ShowdownNetwork;
import com.newbulaco.showdown.network.packets.PartyLearnersRequestPacket;
import com.newbulaco.showdown.network.packets.PartyLearnersResponsePacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// caches server-computed party-learnability answers. lookups return empty while a
// request is in flight; the tooltip mixin renders a "loading" line and re-renders
// on the next frame once the response has populated the cache.
@OnlyIn(Dist.CLIENT)
public final class PartyLearnersCache {

    private static final PartyLearnersCache INSTANCE = new PartyLearnersCache();

    public static PartyLearnersCache getInstance() { return INSTANCE; }

    private final Map<String, Map<UUID, Boolean>> results = new HashMap<>();
    private final Set<String> pending = new HashSet<>();

    private PartyLearnersCache() {}

    public Optional<Boolean> canLearn(String moveName, UUID pokemonId) {
        Map<UUID, Boolean> byPokemon = results.get(moveName.toLowerCase());
        if (byPokemon == null) return Optional.empty();
        Boolean value = byPokemon.get(pokemonId);
        return value == null ? Optional.empty() : Optional.of(value);
    }

    public void requestIfMissing(String moveName) {
        String key = moveName.toLowerCase();
        if (results.containsKey(key) || pending.contains(key)) return;
        pending.add(key);
        ShowdownNetwork.CHANNEL.sendToServer(new PartyLearnersRequestPacket(moveName));
    }

    public void receive(String moveName, List<PartyLearnersResponsePacket.Entry> entries) {
        String key = moveName.toLowerCase();
        Map<UUID, Boolean> byPokemon = new HashMap<>();
        for (PartyLearnersResponsePacket.Entry entry : entries) {
            byPokemon.put(entry.pokemonId(), entry.canLearn());
        }
        results.put(key, byPokemon);
        pending.remove(key);
    }

    public boolean isPending(String moveName) {
        return pending.contains(moveName.toLowerCase());
    }

    public void clear() {
        results.clear();
        pending.clear();
    }
}
