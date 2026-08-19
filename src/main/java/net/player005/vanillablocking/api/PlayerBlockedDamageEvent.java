package net.player005.vanillablocking.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player blocks a hit, before the reduction is applied.
 * Cancelling it makes the hit land in full.
 */
public class PlayerBlockedDamageEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final EntityDamageEvent cause;
    private final ItemStack item;
    private final double incomingDamage;
    private double blockedDamage;
    private boolean cancelled;

    public PlayerBlockedDamageEvent(@NotNull Player player,
                                    @NotNull EntityDamageEvent cause,
                                    @NotNull ItemStack item,
                                    double incomingDamage,
                                    double blockedDamage) {
        super(player);
        this.cause = cause;
        this.item = item;
        this.incomingDamage = incomingDamage;
        this.blockedDamage = blockedDamage;
    }

    /**
     * The damage event being blocked.
     */
    public @NotNull EntityDamageEvent getCause() {
        return cause;
    }

    /**
     * The item the hit is being blocked with.
     */
    public @NotNull ItemStack getItem() {
        return item;
    }

    /**
     * Pre-armor damage of the incoming hit.
     */
    public double getIncomingDamage() {
        return incomingDamage;
    }

    /**
     * How much damage blocking removes from the hit.
     */
    public double getBlockedDamage() {
        return blockedDamage;
    }

    /**
     * Overrides how much damage blocking removes. Clamped to the incoming
     * damage when applied.
     */
    public void setBlockedDamage(double blockedDamage) {
        this.blockedDamage = blockedDamage;
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
