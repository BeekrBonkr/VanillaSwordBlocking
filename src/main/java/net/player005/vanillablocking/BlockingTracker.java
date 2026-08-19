package net.player005.vanillablocking;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.player005.vanillablocking.api.PlayerStartBlockingEvent;
import net.player005.vanillablocking.api.PlayerStopBlockingEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Follows players while they block: fires the start/stop API events and
 * applies the configurable movement speed while a block is held.
 * <p>
 * A per-player task only exists for as long as that player is actually
 * blocking, so idle players cost nothing.
 */
public final class BlockingTracker {

    /**
     * Vanilla slows a player using an item to a fifth of their walking
     * speed. The configured multiplier is relative to normal walking speed,
     * so the modifier has to undo that factor first.
     */
    private static final double VANILLA_ITEM_USE_SLOWDOWN = 0.2;

    private final Plugin plugin;
    private final BlockingService service;
    private final NamespacedKey speedKey;

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public BlockingTracker(@NotNull Plugin plugin, @NotNull BlockingService service) {
        this.plugin = plugin;
        this.service = service;
        this.speedKey = new NamespacedKey(plugin, "blocking_slowdown");
    }

    /**
     * Called shortly after a right-click: starts following the player if
     * they actually raised a blocking item.
     */
    public void onPossibleStart(@NotNull Player player) {
        if (sessions.containsKey(player.getUniqueId())) return;

        ItemStack item = service.blockingItemIgnoringDelay(player);
        if (item == null) return;

        PlayerStartBlockingEvent event = new PlayerStartBlockingEvent(player, item, player.getActiveItemHand());
        event.callEvent();
        if (event.isCancelled()) {
            player.clearActiveItem();
            return;
        }

        Session session = new Session(BlockingService.currentTick());
        sessions.put(player.getUniqueId(), session);
        applySlowdown(player);

        // Polled every tick, not every other tick: the modifier is a real
        // speed boost that only makes sense while vanilla is slowing the
        // player down, so it must never outlive the block by a tick.
        session.task = player.getScheduler().runAtFixedRate(plugin, task -> tick(player), null, 1L, 1L);
    }

    private void tick(@NotNull Player player) {
        if (service.blockingItemIgnoringDelay(player) != null) return;
        stop(player);
    }

    /**
     * Stops following a player, removing the speed modifier and firing the
     * stop event. Safe to call for a player who is not blocking.
     */
    public void stop(@NotNull Player player) {
        Session session = sessions.remove(player.getUniqueId());
        removeSlowdown(player);
        if (session == null) return;

        if (session.task != null) session.task.cancel();
        new PlayerStopBlockingEvent(player, BlockingService.currentTick() - session.startTick).callEvent();
    }

    /**
     * Forgets a player without touching their attributes - for players who
     * already left.
     */
    public void forget(@NotNull UUID uuid) {
        Session session = sessions.remove(uuid);
        if (session != null && session.task != null) session.task.cancel();
    }

    public boolean isTracked(@NotNull Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /**
     * Re-applies the speed modifier for everyone currently blocking, after a
     * config reload changed the multiplier.
     */
    public void refreshSlowdowns() {
        for (UUID uuid : sessions.keySet()) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null) continue;
            // Attributes belong to the player's own region thread.
            player.getScheduler().run(plugin, task -> applySlowdown(player), null);
        }
    }

    private void applySlowdown(@NotNull Player player) {
        removeSlowdown(player);

        double multiplier = service.config().movementSpeedMultiplier();
        if (multiplier < 0) return; // vanilla item-use slowdown, nothing to do

        AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute == null) return;

        double factor = (multiplier / VANILLA_ITEM_USE_SLOWDOWN) - 1.0;
        attribute.addModifier(new AttributeModifier(
                speedKey, factor, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
    }

    /**
     * Removes our movement modifier, whether or not the player is tracked -
     * used on join to clear one left behind by a crash mid-block.
     */
    public void clearSlowdown(@NotNull Player player) {
        removeSlowdown(player);
    }

    private void removeSlowdown(@NotNull Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute == null) return;

        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (speedKey.equals(modifier.getKey())) attribute.removeModifier(modifier);
        }
    }

    private static final class Session {
        private final int startTick;
        private ScheduledTask task;

        private Session(int startTick) {
            this.startTick = startTick;
        }
    }
}
