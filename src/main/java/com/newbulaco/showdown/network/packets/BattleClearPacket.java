package com.newbulaco.showdown.network.packets;

import com.newbulaco.showdown.client.battle.BattleStatusTracker;
import com.newbulaco.showdown.client.battle.ClientPartyStatusManager;
import com.newbulaco.showdown.client.battle.ClientSideConditionManager;
import com.newbulaco.showdown.client.battle.ClientStatChangeManager;
import com.newbulaco.showdown.client.battle.ClientVolatileEffectManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * sent to clients when a battle ends to clear all tracked battle state.
 * ensures rain, tailwind, stealth rock, etc. don't persist after battle.
 */
public class BattleClearPacket {
    private final UUID battleId;

    public BattleClearPacket(UUID battleId) {
        this.battleId = battleId;
    }

    public BattleClearPacket(FriendlyByteBuf buf) {
        this.battleId = buf.readUUID();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(battleId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient());
        });
        ctx.get().setPacketHandled(true);
    }

    private void handleClient() {
        ClientSideConditionManager.getInstance().clearBattle(battleId);
        ClientVolatileEffectManager.getInstance().clearBattle(battleId);
        ClientPartyStatusManager.getInstance().clearBattle(battleId);
        ClientStatChangeManager.getInstance().clearBattle(battleId);
        BattleStatusTracker.clearAll();
    }

    public UUID getBattleId() {
        return battleId;
    }
}
