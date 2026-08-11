package com.newbulaco.showdown;

import com.mojang.logging.LogUtils;
import com.newbulaco.showdown.config.ShowdownConfig;
import com.newbulaco.showdown.battle.BattleManager;
import com.newbulaco.showdown.battle.BattleTimer;
import com.newbulaco.showdown.battle.CobblemonBattleListener;
import com.newbulaco.showdown.battle.ShowdownBattle;
import com.newbulaco.showdown.battle.VolatileEffectTracker;
import com.newbulaco.showdown.challenge.ChallengeManager;
import com.newbulaco.showdown.command.ShowdownCommand;
import com.newbulaco.showdown.data.HistoryStorage;
import com.newbulaco.showdown.format.FormatManager;
import com.newbulaco.showdown.gui.PartySelectionSession;
import com.newbulaco.showdown.network.ShowdownNetwork;
import com.newbulaco.showdown.network.packets.BattleStatePacket;
import com.newbulaco.showdown.util.TickScheduler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(CobblemonShowdown.MODID)
public class CobblemonShowdown {
    public static final String MODID = "cobblemon_showdown";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean cobblemonLoaded = false;
    private static FormatManager formatManager;
    private static ChallengeManager challengeManager;
    private static HistoryStorage historyStorage;
    private static BattleManager battleManager;

    private int tickCounter = 0;
    private static final int CLEANUP_INTERVAL = 1200; // 60s at 20 ticks/sec

    public CobblemonShowdown() {
        ShowdownConfig.register();

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Cobblemon Showdown initializing...");

        cobblemonLoaded = ModList.get().isLoaded("cobblemon");
        if (!cobblemonLoaded) {
            LOGGER.warn("Cobblemon not detected - mod functionality will be limited");
        }

        ShowdownNetwork.register();

        formatManager = new FormatManager();
        formatManager.initialize();

        challengeManager = new ChallengeManager();
        historyStorage = new HistoryStorage();
        battleManager = new BattleManager();

        if (cobblemonLoaded) {
            CobblemonBattleListener.register();
            VolatileEffectTracker.getInstance().initialize();
        }

        LOGGER.info("Cobblemon Showdown initialized");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        if (battleManager != null) {
            battleManager.initialize(event.getServer());
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ShowdownCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tickCounter++;

            TickScheduler.tick();

            PartySelectionSession.tickAll();

            if (battleManager != null) {
                boolean broadcastTimers = tickCounter % 20 == 0;
                for (ShowdownBattle battle : battleManager.getActiveBattles().values()) {
                    BattleTimer timer = battle.getTimer();
                    if (timer == null || !timer.isRunning()) continue;
                    timer.tick();
                    if (broadcastTimers) {
                        BattleStatePacket packet = BattleStatePacket.timerUpdate(
                                battle.getBattleId(),
                                battle.getPlayer1().getName().getString(),
                                battle.getPlayer2().getName().getString(),
                                timer.getPlayer1TotalTime(),
                                timer.getPlayer1TurnTime(),
                                timer.getPlayer2TotalTime(),
                                timer.getPlayer2TurnTime());
                        ShowdownNetwork.sendToPlayer(packet, battle.getPlayer1());
                        ShowdownNetwork.sendToPlayer(packet, battle.getPlayer2());
                    }
                }
            }

            if (cobblemonLoaded) {
                VolatileEffectTracker.getInstance().tick();
            }

            if (tickCounter >= CLEANUP_INTERVAL) {
                tickCounter = 0;
                if (challengeManager != null) {
                    challengeManager.cleanupExpired();
                }
            }
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        TickScheduler.cancelAll();
        PartySelectionSession.clearAll();

        if (battleManager != null) battleManager.shutdown();
        if (cobblemonLoaded) VolatileEffectTracker.getInstance().clear();
        if (formatManager != null) formatManager.shutdown();
        if (historyStorage != null) historyStorage.saveAll();

        LOGGER.info("Server stopped, data saved");
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PartySelectionSession.cancelSession(player.getUUID());
            if (battleManager != null) battleManager.onPlayerDisconnect(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (battleManager != null) battleManager.onPlayerJoin(player);
        }
    }

    public static boolean isCobblemonLoaded() {
        return cobblemonLoaded;
    }

    public static FormatManager getFormatManager() {
        return formatManager;
    }

    public static ChallengeManager getChallengeManager() {
        return challengeManager;
    }

    public static HistoryStorage getHistoryStorage() {
        return historyStorage;
    }

    public static BattleManager getBattleManager() {
        return battleManager;
    }
}
