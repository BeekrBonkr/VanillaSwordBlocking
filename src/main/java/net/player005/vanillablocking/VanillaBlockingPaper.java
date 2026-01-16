package net.player005.vanillablocking;

import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack; // NMS ItemStack
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VanillaBlockingPaper extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    /**
     * Applies or removes blocking components to all swords in an inventory.
     * Used only on join/quit to normalize state.
     */
    private static void updateAllItems(@NotNull Inventory inventory, boolean add) {
        for (@Nullable org.bukkit.inventory.ItemStack stack : inventory.getContents()) {
            if (stack == null) continue;
            if (!stack.getType().name().endsWith("_SWORD")) continue;

            ItemStack nms = CraftItemStack.asNMSCopy(stack);

            if (add) {
                if (!nms.getComponents().has(DataComponents.CONSUMABLE)) {
                    VanillaBlocking.addSwordComponents(nms);
                    inventory.setItem(
                        inventory.first(stack),
                        CraftItemStack.asBukkitCopy(nms)
                    );
                }
            } else {
                VanillaBlocking.removeSwordComponents(nms);
            }
        }
    }

    /**
     * Damage reduction while blocking
     */
    @EventHandler
    public void onDamagePlayer(@NotNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        event.setDamage(
            event.getDamage() *
            VanillaBlocking.damageMultiplier(((CraftPlayer) player).getHandle())
        );
    }

    /**
     * 🔑 CRITICAL FIX
     * Fires when an item is inserted/replaced in a slot,
     * including when the slot is already selected.
     */
    @EventHandler(ignoreCancelled = true)
    public void onSlotChange(PlayerInventorySlotChangeEvent event) {
        int slot = event.getSlot();

        // Hotbar only
        if (slot < 0 || slot > 8) return;

        org.bukkit.inventory.ItemStack newItem = event.getNewItemStack();
        if (newItem == null) return;
        if (!newItem.getType().name().endsWith("_SWORD")) return;

        ItemStack nms = CraftItemStack.asNMSCopy(newItem);

        // Apply only if missing
        if (!nms.getComponents().has(DataComponents.CONSUMABLE)) {
            VanillaBlocking.addSwordComponents(nms);
            event.getPlayer().getInventory().setItem(
                slot,
                CraftItemStack.asBukkitCopy(nms)
            );
        }
    }

    /**
     * Hotbar scroll fallback
     */
    @EventHandler
    public void onItemChangeEvent(@NotNull PlayerItemHeldEvent event) {
        org.bukkit.inventory.ItemStack stack =
            event.getPlayer().getInventory().getItem(event.getNewSlot());

        if (stack == null) return;
        if (!stack.getType().name().endsWith("_SWORD")) return;

        ItemStack nms = CraftItemStack.asNMSCopy(stack);

        if (!nms.getComponents().has(DataComponents.CONSUMABLE)) {
            VanillaBlocking.addSwordComponents(nms);
            event.getPlayer().getInventory().setItem(
                event.getNewSlot(),
                CraftItemStack.asBukkitCopy(nms)
            );
        }
    }

    /**
     * Normalize inventory on join
     */
    @EventHandler(ignoreCancelled = true)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        updateAllItems(event.getPlayer().getInventory(), true);
    }

    /**
     * Cleanup on leave
     */
    @EventHandler
    public void onDisconnect(@NotNull PlayerQuitEvent event) {
        try {
            updateAllItems(event.getPlayer().getInventory(), false);
        } catch (Exception ignored) {}
    }

    @EventHandler
    public void onKick(@NotNull PlayerKickEvent event) {
        try {
            updateAllItems(event.getPlayer().getInventory(), false);
        } catch (Exception ignored) {}
    }
}
