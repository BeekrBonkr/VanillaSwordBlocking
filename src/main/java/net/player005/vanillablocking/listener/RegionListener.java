package net.player005.vanillablocking.listener;

import net.player005.vanillablocking.BlockingService;
import net.player005.vanillablocking.ItemNormalizer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Keeps the held item in sync while a player walks in and out of WorldGuard
 * regions that allow or deny blocking.
 * <p>
 * Only registered when WorldGuard is actually installed and the region flag
 * is being respected - a move handler that does nothing still costs an event
 * dispatch for every movement packet on the server.
 */
public final class RegionListener implements Listener {

    private final BlockingService service;
    private final ItemNormalizer normalizer;

    public RegionListener(@NotNull BlockingService service, @NotNull ItemNormalizer normalizer) {
        this.service = service;
        this.normalizer = normalizer;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(@NotNull PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) return;
        if (!service.config().isBlockableItem(held.getType())) return;

        // Only pay for a full pass when the held item disagrees with where
        // the player now stands.
        boolean shouldBlock = service.context(player).allows();
        if (service.strategy().isApplied(held) == shouldBlock) return;

        normalizer.normalizeHeldSlots(player);
    }
}
