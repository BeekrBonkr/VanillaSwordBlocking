package net.player005.vanillablocking;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Tag;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public class VanillaBlockingPaper extends JavaPlugin implements Listener {

    private static final int OFFHAND_SLOT = 40;

    private PluginConfig config;

    @Override
    public void onEnable() {
        config = new PluginConfig(this);
        config.load();
        getServer().getPluginManager().registerEvents(this, this);

        // Handles being enabled on a running server (e.g. via a plugin manager)
        for (Player player : getServer().getOnlinePlayers()) {
            player.getScheduler().run(this, task -> updateInventory(player), null);
        }
    }

    @Override
    public void onDisable() {
        // On a normal shutdown players quit before plugins disable, so this
        // only matters for runtime disables (e.g. via a plugin manager).
        for (Player player : getServer().getOnlinePlayers()) {
            try {
                stripInventory(player);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            config.load();
            for (Player player : getServer().getOnlinePlayers()) {
                player.getScheduler().run(this, task -> updateInventory(player), null);
            }
            sender.sendMessage(Component.text("VanillaSwordBlocking config reloaded.", NamedTextColor.GREEN));
            return true;
        }
        sender.sendMessage(Component.text("Usage: /" + label + " reload", NamedTextColor.RED));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 1 && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))) return List.of("reload");
        return List.of();
    }

    /**
     * Damage reduction while blocking. Bukkit's base damage is the
     * pre-armor value, which is also where 1.8.9 applied blocking.
     */
    @EventHandler(ignoreCancelled = true)
    public void onDamagePlayer(@NotNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!config.isActiveIn(player.getWorld())) return;
        if (!config.isBlockable(event.getCause())) return;
        if (!VanillaBlocking.isBlockingSword(((CraftPlayer) player).getHandle(), config.allowOffhand())) return;

        event.setDamage(config.applyBlocking(event.getDamage()));
    }

    /**
     * Optional knockback reduction while blocking (1.8.9 default is no
     * reduction, so this is skipped entirely at the authentic setting).
     */
    @EventHandler(ignoreCancelled = true)
    public void onKnockback(@NotNull EntityKnockbackEvent event) {
        if (config.knockbackMultiplier() == 1.0) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!config.isActiveIn(player.getWorld())) return;
        if (!VanillaBlocking.isBlockingSword(((CraftPlayer) player).getHandle(), config.allowOffhand())) return;

        event.setKnockback(event.getKnockback().multiply(config.knockbackMultiplier()));
    }

    /**
     * Fires when an item is inserted/replaced in a slot,
     * including when the slot is already selected.
     */
    @EventHandler
    public void onSlotChange(@NotNull PlayerInventorySlotChangeEvent event) {
        Player player = event.getPlayer();
        if (!config.isActiveIn(player.getWorld())) return;
        if (!isBlockingSlot(event.getSlot())) return;

        applyToSlot(player.getInventory(), event.getSlot());
    }

    /**
     * Hotbar scroll fallback
     */
    @EventHandler
    public void onItemChangeEvent(@NotNull PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!config.isActiveIn(player.getWorld())) return;

        applyToSlot(player.getInventory(), event.getNewSlot());
    }

    /**
     * Normalize inventory on join
     */
    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent event) {
        updateInventory(event.getPlayer());
    }

    /**
     * Re-normalize when moving between worlds (blocking may be disabled
     * in the destination world).
     */
    @EventHandler
    public void onWorldChange(@NotNull PlayerChangedWorldEvent event) {
        updateInventory(event.getPlayer());
    }

    /**
     * Cleanup on leave, so no modified components end up saved to disk
     */
    @EventHandler
    public void onDisconnect(@NotNull PlayerQuitEvent event) {
        try {
            stripInventory(event.getPlayer());
        } catch (Exception ignored) {
        }
    }

    @EventHandler
    public void onKick(@NotNull PlayerKickEvent event) {
        try {
            stripInventory(event.getPlayer());
        } catch (Exception ignored) {
        }
    }

    private boolean isBlockingSlot(int slot) {
        return (slot >= 0 && slot <= 8) || (slot == OFFHAND_SLOT && config.allowOffhand());
    }

    private static boolean isSword(@Nullable ItemStack stack) {
        return stack != null && Tag.ITEMS_SWORDS.isTagged(stack.getType());
    }

    /**
     * Adds the blocking component to the sword in the given slot, if missing.
     */
    private void applyToSlot(@NotNull PlayerInventory inventory, int slot) {
        ItemStack stack = inventory.getItem(slot);
        if (!isSword(stack)) return;

        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(stack);
        if (VanillaBlocking.hasSwordComponents(nms)) return;

        VanillaBlocking.addSwordComponents(nms);
        inventory.setItem(slot, CraftItemStack.asBukkitCopy(nms));
    }

    /**
     * Normalizes a whole inventory: swords in blocking slots gain the
     * component, all other swords lose it.
     */
    private void updateInventory(@NotNull Player player) {
        boolean active = config.isActiveIn(player.getWorld());
        PlayerInventory inventory = player.getInventory();

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isSword(stack)) continue;

            boolean shouldBlock = active && isBlockingSlot(slot);
            net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(stack);
            if (VanillaBlocking.hasSwordComponents(nms) == shouldBlock) continue;

            if (shouldBlock) {
                VanillaBlocking.addSwordComponents(nms);
            } else {
                VanillaBlocking.removeSwordComponents(nms);
            }
            inventory.setItem(slot, CraftItemStack.asBukkitCopy(nms));
        }
    }

    /**
     * Removes the blocking component from every sword in the inventory.
     */
    private void stripInventory(@NotNull Player player) {
        PlayerInventory inventory = player.getInventory();

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isSword(stack)) continue;

            net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(stack);
            if (!VanillaBlocking.hasSwordComponents(nms)) continue;

            VanillaBlocking.removeSwordComponents(nms);
            inventory.setItem(slot, CraftItemStack.asBukkitCopy(nms));
        }
    }
}
