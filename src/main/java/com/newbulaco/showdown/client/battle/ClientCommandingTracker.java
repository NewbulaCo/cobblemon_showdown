package com.newbulaco.showdown.client.battle;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class ClientCommandingTracker {
    private static final ClientCommandingTracker INSTANCE = new ClientCommandingTracker();
    private final Set<UUID> commanding = new HashSet<>();

    private ClientCommandingTracker() {}

    public static ClientCommandingTracker getInstance() { return INSTANCE; }

    public synchronized void set(Set<UUID> uuids) {
        commanding.clear();
        commanding.addAll(uuids);
    }

    public synchronized boolean isCommanding(UUID pokemonUuid) {
        return pokemonUuid != null && commanding.contains(pokemonUuid);
    }

    public synchronized void clear() {
        commanding.clear();
    }
}
