package com.newbulaco.showdown.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.newbulaco.showdown.CobblemonShowdown;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = CobblemonShowdown.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ShowdownKeybinds {

    public static final KeyMapping SHOW_INFO = new KeyMapping(
            "key.cobblemon_showdown.show_info",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_SHIFT,
            "key.categories.cobblemon_showdown"
    );

    public static final KeyMapping SHOW_PARTY_LEARNERS = new KeyMapping(
            "key.cobblemon_showdown.show_party_learners",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_CONTROL,
            "key.categories.cobblemon_showdown"
    );

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(SHOW_INFO);
        event.register(SHOW_PARTY_LEARNERS);
    }

    public static boolean isShowInfoDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(window, SHOW_INFO.getKey().getValue());
    }

    public static boolean isShowPartyLearnersDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(window, SHOW_PARTY_LEARNERS.getKey().getValue());
    }

    public static Component getShowInfoKeyName() {
        return SHOW_INFO.getTranslatedKeyMessage();
    }

    public static Component getShowPartyLearnersKeyName() {
        return SHOW_PARTY_LEARNERS.getTranslatedKeyMessage();
    }
}
