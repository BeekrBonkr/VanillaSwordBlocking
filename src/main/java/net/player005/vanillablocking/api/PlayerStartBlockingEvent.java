package net.player005.vanillablocking.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player raises a blockable item. Cancelling it lowers the item
 * again immediately.
 */
public class PlayerStartBlockingEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final ItemStack item;
    private final EquipmentSlot hand;
    private boolean cancelled;

    public PlayerStartBlockingEvent(@NotNull Player player, @NotNull ItemStack item, @NotNull EquipmentSlot hand) {
        super(player);
        this.item = item;
        this.hand = hand;
    }

    /**
     * The item being raised.
     */
    public @NotNull ItemStack getItem() {
        return item;
    }

    /**
     * The hand the item is held in.
     */
    public @NotNull EquipmentSlot getHand() {
        return hand;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
