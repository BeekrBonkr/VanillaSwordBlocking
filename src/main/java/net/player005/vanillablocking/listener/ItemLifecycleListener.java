package net.player005.vanillablocking.listener;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import net.player005.vanillablocking.BlockingService;
import net.player005.vanillablocking.BlockingTracker;
import net.player005.vanillablocking.ItemNormalizer;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Keeps the blocking component in sync with what a player is holding, and -
 * just as importantly - makes sure it never leaves the player's inventory.
 * <p>
 * The component is only ever meant to exist on an item a player is currently
 * holding. If one escapes into a chest or onto the ground it gets written to
 * the world file, and uninstalling the plugin would leave a permanently
 * right-clickable sword behind.
 */
public final class ItemLifecycleListener implements Listener {

    private final Plugin plugin;
    private final BlockingService service;
    private final ItemNormalizer normalizer;
    private final BlockingTracker tracker;

    public ItemLifecycleListener(@NotNull Plugin plugin,
                                 @NotNull BlockingService service,
                                 @NotNull ItemNormalizer normalizer,
                                 @NotNull BlockingTracker tracker) {
        this.plugin = plugin;
        this.service = service;
        this.normalizer = normalizer;
        this.tracker = tracker;
    }

    /**
     * Fires when an item is inserted/replaced in a slot,
     * including when the slot is already selected.
     */
    @EventHandler
    public void onSlotChange(@NotNull PlayerInventorySlotChangeEvent event) {
        int slot = event.getSlot();
        if (slot == ItemNormalizer.OFFHAND_SLOT) {
            // A shield (or offhand weapon) appearing or disappearing
            // affects whether the whole hotbar may block
            normalizer.normalizeInventory(event.getPlayer());
            return;
        }
        normalizer.normalizeSlot(event.getPlayer(), slot);
    }

    /**
     * Hotbar scroll fallback
     */
    @EventHandler
    public void onItemHeld(@NotNull PlayerItemHeldEvent event) {
        normalizer.normalizeSlot(event.getPlayer(), event.getNewSlot());
    }

    /**
     * Right-clicking is the only hint we get that a player may be raising an
     * item, so a tick later we check whether they actually did.
     */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(@NotNull PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        player.getScheduler().runDelayed(plugin, task -> tracker.onPossibleStart(player), null, 1L);
    }

    /**
     * Normalize inventory on join
     */
    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent event) {
        // Clears a modifier left behind by a crash mid-block.
        tracker.clearSlowdown(event.getPlayer());
        normalizer.normalizeInventory(event.getPlayer());
    }

    /**
     * Re-normalize when moving between worlds (blocking may be disabled
     * in the destination world).
     */
    @EventHandler
    public void onWorldChange(@NotNull PlayerChangedWorldEvent event) {
        tracker.stop(event.getPlayer());
        normalizer.normalizeInventory(event.getPlayer());
    }

    /**
     * Creative and spectator can be excluded from blocking, so the component
     * has to follow game mode changes.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(@NotNull PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        player.getScheduler().runDelayed(plugin, task -> normalizer.normalizeInventory(player), null, 1L);
    }

    /**
     * Respawning hands the player a fresh inventory.
     */
    @EventHandler
    public void onRespawn(@NotNull PlayerPostRespawnEvent event) {
        normalizer.normalizeInventory(event.getPlayer());
    }

    /**
     * Cleanup on leave, so no modified components end up saved to disk
     */
    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        cleanUpPlayer(event.getPlayer());
    }

    @EventHandler
    public void onKick(@NotNull PlayerKickEvent event) {
        cleanUpPlayer(event.getPlayer());
    }

    private void cleanUpPlayer(@NotNull Player player) {
        service.forget(player.getUniqueId());
        // stop(), not forget(): the blocking speed modifier lives on the
        // player's attributes and would be written to their player data.
        tracker.stop(player);
        try {
            normalizer.stripPlayer(player);
        } catch (Exception ignored) {
        }
    }

    /**
     * Death drops are spawned from this list. With keep-inventory the list is
     * empty and the player's own inventory is normalised on respawn instead.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(@NotNull PlayerDeathEvent event) {
        tracker.stop(event.getEntity());
        for (ItemStack drop : event.getDrops()) {
            normalizer.strip(drop);
        }
    }

    /**
     * The catch-all: anything that becomes a ground item gets cleaned,
     * whatever spawned it.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemSpawn(@NotNull ItemSpawnEvent event) {
        ItemStack stack = event.getEntity().getItemStack();
        if (normalizer.strip(stack)) {
            event.getEntity().setItemStack(stack);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(@NotNull PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        if (normalizer.strip(stack)) {
            event.getItemDrop().setItemStack(stack);
        }
    }

    /**
     * Moving an item into a chest, a shulker box or an ender chest writes it
     * to disk, so it must be clean by the time it lands there.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        scheduleContainerClean(event.getView().getTopInventory(), event.getWhoClicked());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        scheduleContainerClean(event.getView().getTopInventory(), event.getWhoClicked());
    }

    /**
     * The click has not been applied yet when the event fires, so the actual
     * cleaning happens a tick later, once the items have landed.
     */
    private void scheduleContainerClean(@NotNull Inventory top, @NotNull org.bukkit.entity.HumanEntity clicker) {
        if (!(clicker instanceof Player player)) return;

        // Only the player's own 2x2 crafting grid is exempt. Checking the
        // holder instead would wrongly exempt ender chests, whose holder is
        // the player even though their contents are saved to disk.
        InventoryType type = top.getType();
        if (type == InventoryType.CRAFTING || type == InventoryType.PLAYER) return;

        player.getScheduler().runDelayed(plugin, task -> normalizer.stripInventory(top), null, 1L);
    }

    /**
     * Putting an item into an item frame moves it out of the inventory and
     * onto an entity that is saved with the chunk.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemFramePlace(@NotNull PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame frame)) return;

        Player player = event.getPlayer();
        player.getScheduler().runDelayed(plugin, task -> {
            ItemStack stack = frame.getItem();
            if (normalizer.strip(stack)) frame.setItem(stack, false);
        }, null, 1L);
    }

    /**
     * Same for armor stands, which can be handed a sword.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmorStand(@NotNull PlayerArmorStandManipulateEvent event) {
        var stand = event.getRightClicked();
        var slot = event.getSlot();

        event.getPlayer().getScheduler().runDelayed(plugin, task -> {
            var equipment = stand.getEquipment();
            ItemStack stack = equipment.getItem(slot);
            if (normalizer.strip(stack)) equipment.setItem(slot, stack);
        }, null, 1L);
    }

    /**
     * Hoppers and droppers shuffling items between containers.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryMoveItem(@NotNull InventoryMoveItemEvent event) {
        // getItem returns a copy, so the change only counts once it is set back.
        ItemStack stack = event.getItem();
        if (normalizer.strip(stack)) {
            event.setItem(stack);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDispense(@NotNull BlockDispenseEvent event) {
        ItemStack stack = event.getItem();
        if (normalizer.strip(stack)) {
            event.setItem(stack);
        }
    }
}
