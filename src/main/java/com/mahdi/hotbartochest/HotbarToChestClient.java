package com.mahdi.hotbartochest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Hotbar To Chest
 *
 * Automatically moves complete item stacks from the player's hotbar
 * into an open chest, double chest, or ender chest.
 *
 * The feature can be enabled/disabled with a configurable keybind.
 */
public class HotbarToChestClient implements ClientModInitializer {

    /*
     * Delay between checking hotbar slots.
     *
     * 5 ticks = 250 milliseconds.
     *
     * This intentionally isn't instant so the transfers aren't too fast.
     */
    private static final int ACTION_DELAY_TICKS = 5;

    /*
     * Small delay after opening a chest before the first transfer.
     */
    private static final int OPEN_DELAY_TICKS = 6;

    /*
     * Minecraft 1.21.11 uses KeyBinding.Category.
     *
     * Identifier.of() is required by the 1.21.11 API.
     */
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(
                    Identifier.of("hotbartochest", "keybinds")
            );

    private static KeyBinding toggleKey;

    /*
     * Starts enabled.
     */
    private static boolean enabled = true;

    /*
     * The currently open container screen.
     */
    private static GenericContainerScreen lastScreen;

    /*
     * Which hotbar slot we are currently checking.
     *
     * 0 = first hotbar slot
     * 8 = last hotbar slot
     */
    private static int scanSlot = 0;

    /*
     * Tick countdown used to control transfer speed.
     */
    private static int delay = 0;

    @Override
    public void onInitializeClient() {

        /*
         * Register configurable keybind.
         *
         * Default = Unbound.
         *
         * The player can choose any key from:
         *
         * Options
         * → Controls
         * → Key Binds
         */
        toggleKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "key.hotbar_to_chest.toggle",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_UNKNOWN,
                        CATEGORY
                )
        );

        /*
         * Run our logic every client tick.
         */
        ClientTickEvents.END_CLIENT_TICK.register(
                HotbarToChestClient::tick
        );
    }

    private static void tick(MinecraftClient client) {

        /*
         * Check whether the player pressed the configured toggle key.
         */
        while (toggleKey.wasPressed()) {

            enabled = !enabled;

            /*
             * Reset transfer state when switching ON/OFF.
             */
            reset();

            /*
             * Display ON/OFF message above the hotbar.
             *
             * The "true" argument makes it use the action-bar area.
             */
            if (client.player != null) {

                client.player.sendMessage(
                        Text.literal(
                                "Hotbar To Chest: "
                                        + (enabled ? "ON" : "OFF")
                        ),
                        true
                );
            }
        }

        /*
         * If the feature is OFF, do nothing.
         */
        if (!enabled) {
            return;
        }

        /*
         * We only work while a container screen is open.
         *
         * GenericContainerScreen covers normal chests,
         * double chests and similar container screens.
         */
        if (!(client.currentScreen instanceof GenericContainerScreen screen)
                || client.player == null
                || client.interactionManager == null) {

            reset();
            return;
        }

        /*
         * Detect when a new chest/container has been opened.
         */
        if (screen != lastScreen) {

            lastScreen = screen;

            /*
             * Start checking from the first hotbar slot.
             */
            scanSlot = 0;

            /*
             * Wait a little before the first transfer.
             */
            delay = OPEN_DELAY_TICKS;

            return;
        }

        /*
         * Wait until the current delay finishes.
         */
        if (delay > 0) {
            delay--;
            return;
        }

        /*
         * Get the chest/container handler.
         */
        GenericContainerScreenHandler handler =
                screen.getScreenHandler();

        /*
         * Calculate how many slots belong to the container.
         *
         * Normal chest:
         * 3 rows × 9 = 27
         *
         * Double chest:
         * 6 rows × 9 = 54
         */
        int containerSlots = handler.getRows() * 9;

        /*
         * After the container slots come:
         *
         * 27 main inventory slots
         * 9 hotbar slots
         *
         * Therefore:
         *
         * first hotbar slot =
         * container slots + 27
         */
        int hotbarSlotId =
                containerSlots + 27 + scanSlot;

        /*
         * Safety check.
         */
        if (hotbarSlotId >= handler.slots.size()) {

            scanSlot = 0;
            delay = ACTION_DELAY_TICKS;

            return;
        }

        /*
         * Check whether this hotbar slot contains an item.
         */
        if (!handler.getSlot(hotbarSlotId)
                .getStack()
                .isEmpty()) {

            /*
             * QUICK_MOVE tells Minecraft to move the whole stack
             * according to its normal inventory rules.
             */
            client.interactionManager.clickSlot(
                    handler.syncId,
                    hotbarSlotId,
                    0,
                    SlotActionType.QUICK_MOVE,
                    client.player
            );
        }

        /*
         * Move to the next hotbar slot.
         *
         * After slot 8, return to slot 0.
         */
        scanSlot = (scanSlot + 1) % 9;

        /*
         * Wait before checking the next slot.
         */
        delay = ACTION_DELAY_TICKS;
    }

    /*
     * Reset transfer state.
     */
    private static void reset() {

        lastScreen = null;
        scanSlot = 0;
        delay = 0;
    }
}
