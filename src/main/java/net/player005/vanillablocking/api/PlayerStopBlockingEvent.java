package net.player005.vanillablocking.api;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player lowers a blockable item, for any reason: releasing the
 * button, swapping slots, being interrupted, dying or disconnecting.
 */
public class PlayerStopBlockingEvent extends PlayerEvent {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final int blockedTicks;

    public PlayerStopBlockingEvent(@NotNull Player player, int blockedTicks) {
        super(player);
        this.blockedTicks = blockedTicks;
    }

    /**
     * How long the player held the block, in ticks.
     */
    public int getBlockedTicks() {
        return blockedTicks;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
