package com.newbulaco.showdown.mixin.client;

import com.cobblemon.mod.common.client.gui.pc.PCGUI;
import com.cobblemon.mod.common.client.storage.ClientPC;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.newbulaco.showdown.client.gui.pc.PCSortPanel;
import com.newbulaco.showdown.client.tooltip.PokemonTooltipRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mixin(PCGUI.class)
public abstract class PCGUIMixin extends Screen {
    @Unique
    private static final Logger cobblemonShowdown$LOGGER = LoggerFactory.getLogger("CobblemonShowdown");
    @Unique
    private static Method cobblemonShowdown$getPreviewPokemonMethod = null;
    @Unique
    private static Field cobblemonShowdown$previewPokemonField = null;
    @Unique
    private static boolean cobblemonShowdown$lookupFailed = false;

    @Unique
    private PCSortPanel cobblemonShowdown$sortPanel = null;

    // can't inject into mouseClicked, so we poll GLFW directly during render
    @Unique
    private boolean cobblemonShowdown$wasMouseDown = false;

    // persists across PC sessions within the same game session
    @Unique
    private static int cobblemonShowdown$lastBox = 0;

    @Unique
    private static Field cobblemonShowdown$pcField = null;
    @Unique
    private static Field cobblemonShowdown$storageWidgetField = null;
    @Unique
    private static Method cobblemonShowdown$getBoxMethod = null;
    @Unique
    private static Method cobblemonShowdown$setBoxMethod = null;

    protected PCGUIMixin(Component title) {
        super(title);
    }

    @Unique
    private ClientPC cobblemonShowdown$getPC() {
        try {
            if (cobblemonShowdown$pcField == null) {
                cobblemonShowdown$pcField = this.getClass().getDeclaredField("pc");
                cobblemonShowdown$pcField.setAccessible(true);
            }
            return (ClientPC) cobblemonShowdown$pcField.get(this);
        } catch (Exception e) {
            cobblemonShowdown$LOGGER.error("[Showdown] Failed to get PC field", e);
            return null;
        }
    }

    // StorageWidget is 'private lateinit var storageWidget: StorageWidget' in PCGUI.kt
    @Unique
    private Object cobblemonShowdown$getStorageWidget() {
        try {
            if (cobblemonShowdown$storageWidgetField == null) {
                Class<?> clazz = this.getClass();
                while (clazz != null && cobblemonShowdown$storageWidgetField == null) {
                    for (Field f : clazz.getDeclaredFields()) {
                        String fieldName = f.getName();
                        if (fieldName.equals("storageWidget") ||
                            fieldName.contains("storageWidget") ||
                            f.getType().getSimpleName().equals("StorageWidget")) {
                            cobblemonShowdown$storageWidgetField = f;
                            cobblemonShowdown$storageWidgetField.setAccessible(true);
                            break;
                        }
                    }
                    clazz = clazz.getSuperclass();
                }

                if (cobblemonShowdown$storageWidgetField == null) {
                    cobblemonShowdown$LOGGER.warn("[Showdown] Could not find storageWidget field. Available fields:");
                    clazz = this.getClass();
                    while (clazz != null && clazz != Object.class) {
                        cobblemonShowdown$LOGGER.warn("[Showdown]   Class: {}", clazz.getSimpleName());
                        for (Field f : clazz.getDeclaredFields()) {
                            cobblemonShowdown$LOGGER.warn("[Showdown]     field: {} ({})", f.getName(), f.getType().getSimpleName());
                        }
                        clazz = clazz.getSuperclass();
                    }
                    return null;
                }
            }
            return cobblemonShowdown$storageWidgetField.get(this);
        } catch (Exception e) {
            return null;
        }
    }

    @Unique
    private static Field cobblemonShowdown$boxField = null;
    @Unique
    private static boolean cobblemonShowdown$boxFieldLookupDone = false;
    @Unique
    private static Method cobblemonShowdown$setupStorageSlotsMethod = null;

    @Unique
    private int cobblemonShowdown$getCurrentBox() {
        try {
            Object storageWidget = cobblemonShowdown$getStorageWidget();
            if (storageWidget == null) return 0;

            if (!cobblemonShowdown$boxFieldLookupDone) {
                cobblemonShowdown$boxFieldLookupDone = true;
                Class<?> clazz = storageWidget.getClass();
                while (clazz != null && cobblemonShowdown$boxField == null) {
                    for (Field f : clazz.getDeclaredFields()) {
                        String name = f.getName();
                        // kotlin might name the backing field differently
                        if (name.equals("box") || name.equals("_box") || name.equals("box$delegate")) {
                            f.setAccessible(true);
                            cobblemonShowdown$boxField = f;
                            break;
                        }
                    }
                    clazz = clazz.getSuperclass();
                }

                try {
                    cobblemonShowdown$setupStorageSlotsMethod = storageWidget.getClass().getDeclaredMethod("setupStorageSlots");
                    cobblemonShowdown$setupStorageSlotsMethod.setAccessible(true);
                } catch (NoSuchMethodException e) {
                    cobblemonShowdown$LOGGER.warn("[Showdown] Could not find setupStorageSlots method");
                }

                if (cobblemonShowdown$boxField == null) {
                    cobblemonShowdown$LOGGER.warn("[Showdown] Could not find box field. Trying methods...");
                    for (String methodName : new String[]{"getBox", "box", "getBox$common", "component1"}) {
                        try {
                            cobblemonShowdown$getBoxMethod = storageWidget.getClass().getMethod(methodName);
                            break;
                        } catch (NoSuchMethodException ignored) {}
                    }
                }
            }

            if (cobblemonShowdown$boxField != null) {
                Object value = cobblemonShowdown$boxField.get(storageWidget);
                if (value instanceof Integer) {
                    return (Integer) value;
                } else if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
            }

            if (cobblemonShowdown$getBoxMethod != null) {
                Object result = cobblemonShowdown$getBoxMethod.invoke(storageWidget);
                if (result instanceof Integer) {
                    return (Integer) result;
                } else if (result instanceof Number) {
                    return ((Number) result).intValue();
                }
            }

            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    // kotlin's custom setter calls setupStorageSlots() internally, but when we set the field
    // directly we must call it ourselves to refresh the UI
    @Unique
    private void cobblemonShowdown$setCurrentBox(int box) {
        try {
            Object storageWidget = cobblemonShowdown$getStorageWidget();
            if (storageWidget == null) return;

            if (cobblemonShowdown$setBoxMethod == null) {
                for (String methodName : new String[]{"setBox", "setBox$common"}) {
                    try {
                        cobblemonShowdown$setBoxMethod = storageWidget.getClass().getMethod(methodName, int.class);
                        break;
                    } catch (NoSuchMethodException ignored) {}
                }

                if (cobblemonShowdown$setBoxMethod == null) {
                    try {
                        cobblemonShowdown$setBoxMethod = storageWidget.getClass().getMethod("setBox", Integer.class);
                    } catch (NoSuchMethodException ignored) {}
                }
            }

            // setter is preferred; it calls setupStorageSlots internally
            if (cobblemonShowdown$setBoxMethod != null) {
                cobblemonShowdown$setBoxMethod.invoke(storageWidget, box);
                return;
            }

            // fallback: direct field access + manual UI refresh
            if (cobblemonShowdown$boxField != null) {
                cobblemonShowdown$boxField.set(storageWidget, box);

                // must call setupStorageSlots() since we bypassed kotlin's custom setter
                if (cobblemonShowdown$setupStorageSlotsMethod != null) {
                    cobblemonShowdown$setupStorageSlotsMethod.invoke(storageWidget);
                } else {
                    cobblemonShowdown$LOGGER.warn("[Showdown] Cannot refresh UI - setupStorageSlots method not found");
                }
                return;
            }

            cobblemonShowdown$LOGGER.warn("[Showdown] Could not set box - no method or field found");
        } catch (Exception e) {
        }
    }

    @Unique
    private Pokemon cobblemonShowdown$getPreviewPokemon() {
        if (cobblemonShowdown$lookupFailed) {
            return null;
        }

        try {
            if (cobblemonShowdown$getPreviewPokemonMethod == null && cobblemonShowdown$previewPokemonField == null) {
                // kotlin val compiles to a public getter
                for (String methodName : new String[]{"getPreviewPokemon", "getPreviewPokemon$common"}) {
                    try {
                        cobblemonShowdown$getPreviewPokemonMethod = this.getClass().getMethod(methodName);
                        break;
                    } catch (NoSuchMethodException ignored) {}
                }

                if (cobblemonShowdown$getPreviewPokemonMethod == null) {
                    for (String fieldName : new String[]{"previewPokemon", "previewPokemon$delegate"}) {
                        try {
                            cobblemonShowdown$previewPokemonField = this.getClass().getDeclaredField(fieldName);
                            cobblemonShowdown$previewPokemonField.setAccessible(true);
                            break;
                        } catch (NoSuchFieldException ignored) {}
                    }
                }

                if (cobblemonShowdown$getPreviewPokemonMethod == null && cobblemonShowdown$previewPokemonField == null) {
                    cobblemonShowdown$LOGGER.warn("[Showdown] Could not find previewPokemon. Available methods with 'preview':");
                    for (Method m : this.getClass().getMethods()) {
                        if (m.getName().toLowerCase().contains("preview")) {
                            cobblemonShowdown$LOGGER.warn("[Showdown]   method: {}", m.getName());
                        }
                    }
                    cobblemonShowdown$LOGGER.warn("[Showdown] Available fields:");
                    for (Field f : this.getClass().getDeclaredFields()) {
                        cobblemonShowdown$LOGGER.warn("[Showdown]   field: {} ({})", f.getName(), f.getType().getSimpleName());
                    }
                    cobblemonShowdown$lookupFailed = true;
                    return null;
                }
            }

            if (cobblemonShowdown$getPreviewPokemonMethod != null) {
                return (Pokemon) cobblemonShowdown$getPreviewPokemonMethod.invoke(this);
            } else if (cobblemonShowdown$previewPokemonField != null) {
                return (Pokemon) cobblemonShowdown$previewPokemonField.get(this);
            }
        } catch (Exception e) {
            if (!cobblemonShowdown$lookupFailed) {
                cobblemonShowdown$LOGGER.error("[Showdown] Error accessing previewPokemon: {}", e.getMessage());
                cobblemonShowdown$lookupFailed = true;
            }
        }
        return null;
    }

    // previewPokemon stays set to the last hovered pokemon even after the cursor moves away,
    // so we check actual slot bounds to avoid stale tooltips
    @Unique
    private boolean cobblemonShowdown$isMouseOverPokemonSlot(int mouseX, int mouseY) {
        // PCGUI.kt: BASE_WIDTH = 349, BASE_HEIGHT = 205
        int guiX = (this.width - 349) / 2;
        int guiY = (this.height - 205) / 2;

        // StorageWidget is at x+85, y+27 with WIDTH=263, HEIGHT=155
        int storageWidgetX = guiX + 85;
        int storageWidgetY = guiY + 27;

        // box slots: offset x+7, y+11, 6 columns x 5 rows, ~24x24 with 2px padding
        int boxX = storageWidgetX + 7;
        int boxY = storageWidgetY + 11;
        int boxWidth = 180;
        int boxHeight = 140;

        boolean inBoxArea = mouseX >= boxX && mouseX <= boxX + boxWidth
                         && mouseY >= boxY && mouseY <= boxY + boxHeight;

        // party slots: offset x+193, y+8, 1 column x 6 rows
        int partyX = storageWidgetX + 193;
        int partyY = storageWidgetY + 8;
        int partyWidth = 64;
        int partyHeight = 145;

        boolean inPartyArea = mouseX >= partyX && mouseX <= partyX + partyWidth
                           && mouseY >= partyY && mouseY <= partyY + partyHeight;

        return inBoxArea || inPartyArea;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void cobblemonShowdown$onInit(CallbackInfo ci) {
        int guiX = (this.width - 349) / 2;
        int guiY = (this.height - 205) / 2;

        int sortX = guiX - 36;
        int sortY = guiY + 4;

        ClientPC pc = cobblemonShowdown$getPC();
        if (pc != null) {
            cobblemonShowdown$sortPanel = new PCSortPanel(sortX, sortY, pc, this::cobblemonShowdown$getCurrentBox);

            if (cobblemonShowdown$lastBox > 0 && cobblemonShowdown$lastBox < pc.getBoxes().size()) {
                cobblemonShowdown$setCurrentBox(cobblemonShowdown$lastBox);
            }
        }
    }

    // only the 6x5 grid of box slots, not the party area or pasture
    @Unique
    private boolean cobblemonShowdown$isMouseOverBoxArea(double mouseX, double mouseY) {
        int guiX = (this.width - 349) / 2;
        int guiY = (this.height - 205) / 2;

        int storageWidgetX = guiX + 85;
        int storageWidgetY = guiY + 27;

        int boxX = storageWidgetX + 7;
        int boxY = storageWidgetY + 11;
        int boxWidth = 180;
        int boxHeight = 140;

        return mouseX >= boxX && mouseX <= boxX + boxWidth
            && mouseY >= boxY && mouseY <= boxY + boxHeight;
    }

    @Unique
    private int cobblemonShowdown$getBoxCount() {
        ClientPC pc = cobblemonShowdown$getPC();
        if (pc != null) {
            return pc.getBoxes().size();
        }
        return 1;
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, require = 0)
    private void cobblemonShowdown$onMouseScrolled(double mouseX, double mouseY, double delta,
            CallbackInfoReturnable<Boolean> cir) {
        if (!cobblemonShowdown$isMouseOverBoxArea(mouseX, mouseY)) {
            return;
        }

        int currentBox = cobblemonShowdown$getCurrentBox();
        int boxCount = cobblemonShowdown$getBoxCount();

        if (boxCount <= 1) {
            return;
        }

        int newBox;
        if (delta > 0) {
            newBox = (currentBox + 1) % boxCount;
        } else if (delta < 0) {
            newBox = (currentBox - 1 + boxCount) % boxCount;
        } else {
            return;
        }

        cobblemonShowdown$setCurrentBox(newBox);

        cir.setReturnValue(true);
    }

    @Inject(method = "onClose", at = @At("HEAD"), require = 0)
    private void cobblemonShowdown$onClose(CallbackInfo ci) {
        cobblemonShowdown$lastBox = cobblemonShowdown$getCurrentBox();
    }

    @Inject(method = "removed", at = @At("HEAD"), require = 0)
    private void cobblemonShowdown$onRemoved(CallbackInfo ci) {
        cobblemonShowdown$lastBox = cobblemonShowdown$getCurrentBox();
    }

    @Inject(
        method = "render",
        at = @At("TAIL")
    )
    private void cobblemonShowdown$renderPokemonTooltip(
            GuiGraphics context,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo ci
    ) {
        // save continuously, more reliable than hooking close methods
        int currentBox = cobblemonShowdown$getCurrentBox();
        if (currentBox >= 0) {
            cobblemonShowdown$lastBox = currentBox;
        }

        // poll GLFW directly because PCGUI doesn't override mouseClicked
        boolean isMouseDown = GLFW.glfwGetMouseButton(
            net.minecraft.client.Minecraft.getInstance().getWindow().getWindow(),
            GLFW.GLFW_MOUSE_BUTTON_LEFT
        ) == GLFW.GLFW_PRESS;

        if (isMouseDown && !cobblemonShowdown$wasMouseDown) {
            if (cobblemonShowdown$sortPanel != null) {
                cobblemonShowdown$sortPanel.mouseClicked(mouseX, mouseY, 0);
            }
        }
        cobblemonShowdown$wasMouseDown = isMouseDown;

        if (cobblemonShowdown$sortPanel != null) {
            cobblemonShowdown$sortPanel.render(context, mouseX, mouseY, delta);
        }

        if (cobblemonShowdown$sortPanel != null && cobblemonShowdown$sortPanel.isMouseOver(mouseX, mouseY)) {
            return;
        }

        Pokemon pokemon = cobblemonShowdown$getPreviewPokemon();

        if (pokemon == null) {
            return;
        }

        if (!cobblemonShowdown$isMouseOverPokemonSlot(mouseX, mouseY)) {
            return;
        }

        // held item is rendered at (guiX + 3, guiY + 98) with 16x16 size;
        // avoid overlapping cobblemon's own item tooltip
        int guiX = (this.width - 349) / 2;
        int guiY = (this.height - 205) / 2;
        int itemX = guiX + 3;
        int itemY = guiY + 98;

        boolean itemHovered = mouseX >= itemX && mouseX <= itemX + 16
                           && mouseY >= itemY && mouseY <= itemY + 16;

        if (itemHovered && pokemon.heldItem() != null && !pokemon.heldItem().isEmpty()) {
            return;
        }

        PokemonTooltipRenderer.renderTooltip(context, pokemon, mouseX, mouseY, true);
    }
}
