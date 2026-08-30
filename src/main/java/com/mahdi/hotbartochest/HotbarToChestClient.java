package com.mahdi.hotbartochest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.GenericContainerScreenHandler;

/**
 * Continuously moves complete stacks from the player's hotbar into an open
 * chest / double chest / ender chest. It keeps running for as long as the
 * container screen remains open.
 */
public class HotbarToChestClient implements ClientModInitializer {
    // 5 ticks = 250 ms between moves. Slow enough for AFK use, not instant.
    private static final int ACTION_DELAY_TICKS = 5;
    private static final int OPEN_DELAY_TICKS = 6;

    private static HandledScreen<?> lastScreen;
    private static int scanSlot;
    private static int delay;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(HotbarToChestClient::tick);
    }

    private static void tick(MinecraftClient client) {
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

        // In a GenericContainerScreenHandler the player's inventory starts
        // immediately after the container: 27 main-inventory slots + 9 hotbar.
        int hotbarSlotId = containerSlots + 27 + scanSlot;
        if (hotbarSlotId >= handler.slots.size()) {
            scanSlot = 0;
            delay = ACTION_DELAY_TICKS;
            return;
        }

        // QUICK_MOVE transfers the complete stack (subject to normal Minecraft
        // inventory rules), rather than taking only one item.
        if (!handler.getSlot(hotbarSlotId).getStack().isEmpty()) {
            client.interactionManager.clickSlot(
                    handler.syncId,
                    hotbarSlotId,
                    0,
                    SlotActionType.QUICK_MOVE,
                    client.player
            );
        }

        // Keep cycling forever while the chest GUI is open. This means items
        // that arrive in the hotbar later are also picked up automatically.
        scanSlot = (scanSlot + 1) % 9;
        delay = ACTION_DELAY_TICKS;
    }

    private static void reset() {
        lastScreen = null;
        scanSlot = 0;
        delay = 0;
    }
}
