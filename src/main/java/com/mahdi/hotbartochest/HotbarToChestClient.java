package com.mahdi.hotbartochest;

import com.mojang.blaze3d.platform.InputUtil;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil.Type;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Continuously moves complete stacks from the player's hotbar into an open
 * chest / double chest / ender chest while the feature is enabled.
 *
 * The feature can be toggled with a configurable Minecraft keybind.
 */
public class HotbarToChestClient implements ClientModInitializer {
    // 5 ticks = 250 ms between moves. Slow enough for AFK use, not instant.
    private static final int ACTION_DELAY_TICKS = 5;
    private static final int OPEN_DELAY_TICKS = 6;

    private static KeyBinding toggleKey;
    private static boolean enabled = true;
    private static GenericContainerScreen lastScreen;
    private static int scanSlot;
    private static int delay;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.hotbar_to_chest.toggle",
                Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "category.hotbar_to_chest"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(HotbarToChestClient::tick);
    }

    private static void tick(MinecraftClient client) {
        while (toggleKey.wasPressed()) {
            enabled = !enabled;
            reset();

            if (client.player != null) {
                client.player.sendMessage(
                        Text.literal("Hotbar To Chest: " + (enabled ? "ON" : "OFF")),
                        true
                );
            }
        }

        if (!enabled) {
            return;
        }

        if (!(client.currentScreen instanceof GenericContainerScreen screen)
                || client.player == null
                || client.interactionManager == null) {
            reset();
            return;
        }

        if (screen != lastScreen) {
            lastScreen = screen;
            scanSlot = 0;
            delay = OPEN_DELAY_TICKS;
            return;
        }

        if (delay > 0) {
            delay--;
            return;
        }

        GenericContainerScreenHandler handler = screen.getScreenHandler();
        int containerSlots = handler.getRows() * 9;

        // Player inventory starts immediately after the container:
        // 27 main-inventory slots + 9 hotbar slots.
        int hotbarSlotId = containerSlots + 27 + scanSlot;
        if (hotbarSlotId >= handler.slots.size()) {
            scanSlot = 0;
            delay = ACTION_DELAY_TICKS;
            return;
        }

        // QUICK_MOVE transfers the complete stack, subject to normal inventory rules.
        if (!handler.getSlot(hotbarSlotId).getStack().isEmpty()) {
            client.interactionManager.clickSlot(
                    handler.syncId,
                    hotbarSlotId,
                    0,
                    SlotActionType.QUICK_MOVE,
                    client.player
            );
        }

        // Keep cycling forever while the chest GUI is open so newly-arriving
        // items in the hotbar are also picked up.
        scanSlot = (scanSlot + 1) % 9;
        delay = ACTION_DELAY_TICKS;
    }

    private static void reset() {
        lastScreen = null;
        scanSlot = 0;
        delay = 0;
    }
}
