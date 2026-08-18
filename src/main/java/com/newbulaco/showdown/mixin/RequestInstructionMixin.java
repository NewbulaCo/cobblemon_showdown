package com.newbulaco.showdown.mixin;

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.interpreter.instructions.RequestInstruction;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.newbulaco.showdown.network.ShowdownNetwork;
import com.newbulaco.showdown.network.packets.CommandingSyncPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// cobblemon 1.5.2 drops the request json's "commanding" field. re-parse it here, cache
// per-actor, and broadcast the battle-wide union so both sides can filter enemy commanders.
@Mixin(value = RequestInstruction.class, remap = false)
public abstract class RequestInstructionMixin {

    private static final Map<UUID, Map<UUID, Set<UUID>>> BATTLE_COMMANDING = new ConcurrentHashMap<>();

    @Shadow @Final private BattleActor battleActor;
    @Shadow @Final private BattleMessage message;

    @Inject(method = "invoke", at = @At("HEAD"))
    private void cobblemonShowdown$syncCommanding(PokemonBattle battle, CallbackInfo ci) {
        try {
            String raw = message.getRawMessage();
            int idx = raw.indexOf("|request|");
            if (idx < 0) return;
            String json = raw.substring(idx + "|request|".length());
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("side")) return;
            JsonObject side = root.getAsJsonObject("side");
            if (!side.has("pokemon")) return;
            JsonArray pokemon = side.getAsJsonArray("pokemon");

            Set<UUID> mine = new HashSet<>();
            for (int i = 0; i < pokemon.size(); i++) {
                JsonObject p = pokemon.get(i).getAsJsonObject();
                if (!p.has("commanding") || !p.get("commanding").getAsBoolean()) continue;
                UUID id = cobblemonShowdown$uuidFromDetails(p);
                if (id != null) mine.add(id);
            }

            BATTLE_COMMANDING
                    .computeIfAbsent(battle.getBattleId(), k -> new ConcurrentHashMap<>())
                    .put(battleActor.getUuid(), mine);

            Set<UUID> union = new HashSet<>();
            for (Set<UUID> perActor : BATTLE_COMMANDING.get(battle.getBattleId()).values()) {
                union.addAll(perActor);
            }

            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            CommandingSyncPacket packet = new CommandingSyncPacket(union);
            for (BattleActor actor : battle.getActors()) {
                for (UUID playerUuid : actor.getPlayerUUIDs()) {
                    ServerPlayer p = server.getPlayerList().getPlayer(playerUuid);
                    if (p != null) ShowdownNetwork.sendToPlayer(packet, p);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static UUID cobblemonShowdown$uuidFromDetails(JsonObject p) {
        if (!p.has("details")) return null;
        String details = p.get("details").getAsString();
        String[] parts = details.split(",");
        if (parts.length < 2) return null;
        try {
            return UUID.fromString(parts[1].trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
