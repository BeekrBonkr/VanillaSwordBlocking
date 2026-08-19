package net.player005.vanillablocking.api;

import net.player005.vanillablocking.BlockingService;
import net.player005.vanillablocking.BlockingTracker;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The bit of VanillaSwordBlocking other plugins are meant to touch.
 * <p>
 * Everything here is safe to call when the plugin is disabled: queries then
 * simply report that nobody is blocking.
 *
 * @see PlayerStartBlockingEvent
 * @see PlayerStopBlockingEvent
 * @see PlayerBlockedDamageEvent
 */
public final class VanillaBlockingApi {

    private static volatile @Nullable BlockingService service;
    private static volatile @Nullable BlockingTracker tracker;

    private VanillaBlockingApi() {
    }

    /**
     * Whether the plugin is loaded and ready to answer queries.
     */
    public static boolean isAvailable() {
        return service != null;
    }

    /**
     * Whether this player is blocking right now, with every restriction
     * (world, permission, region, shield, cooldown) taken into account.
     */
    public static boolean isBlocking(@NotNull Player player) {
        BlockingService current = service;
        return current != null && current.isBlocking(player);
    }

    /**
     * The item the player is blocking with, or null when they are not
     * blocking.
     */
    public static @Nullable ItemStack blockingItem(@NotNull Player player) {
        BlockingService current = service;
        return current == null ? null : current.blockingItem(player);
    }

    /**
     * Whether this player would be allowed to block where they are standing,
     * regardless of what they are holding.
     */
    public static boolean mayBlock(@NotNull Player player) {
        BlockingService current = service;
        return current != null && current.mayBlock(player);
    }

    /**
     * Whether the player turned their own sword blocking off with
     * {@code /vsb toggle}.
     */
    public static boolean isToggledOff(@NotNull Player player) {
        BlockingService current = service;
        return current != null && current.isToggledOff(player);
    }

    /**
     * Lowers a blocking player's item, as a block-hit interrupt would.
     */
    public static void stopBlocking(@NotNull Player player) {
        BlockingTracker current = tracker;
        player.clearActiveItem();
        if (current != null) current.stop(player);
    }

    /**
     * Wired up by the plugin on enable. Not part of the public API.
     */
    public static void install(@NotNull BlockingService service, @NotNull BlockingTracker tracker) {
        VanillaBlockingApi.service = service;
        VanillaBlockingApi.tracker = tracker;
    }

    /**
     * Cleared by the plugin on disable. Not part of the public API.
     */
    public static void uninstall() {
        service = null;
        tracker = null;
    }
}
