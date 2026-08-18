package com.newbulaco.showdown.client;

import com.newbulaco.showdown.CobblemonShowdown;
import com.newbulaco.showdown.client.battle.ClientCommandingTracker;
import com.newbulaco.showdown.client.battle.ClientPartyStatusManager;
import com.newbulaco.showdown.client.battle.ClientSideConditionManager;
import com.newbulaco.showdown.client.battle.ClientStatChangeManager;
import com.newbulaco.showdown.client.battle.ClientVolatileEffectManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CobblemonShowdown.MODID, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientBattleStateManager.getInstance().clearAll();
        ClientSpectatorManager.getInstance().clear();
        ClientVolatileEffectManager.getInstance().clearAll();
        ClientStatChangeManager.getInstance().clearAll();
        ClientSideConditionManager.getInstance().clearAll();
        ClientPartyStatusManager.getInstance().clearAll();
        ClientCommandingTracker.getInstance().clear();
        PartyLearnersCache.getInstance().clear();
    }
}
