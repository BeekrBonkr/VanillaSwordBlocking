package net.player005.vanillablocking;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemUseAnimation;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VanillaBlockingPaper extends JavaPlugin implements Listener {

    private static final int OFFHAND_SLOT = 40;

    private PluginConfig config;
    private OcmConfigReader ocmReader;
    private OcmDamageDisplay ocmDisplay;

    /**
     * Only used when block-hitting is disabled: players who recently
     * attacked while blocking, mapped to when they may block again
     * (epoch milliseconds).
     */
    private final Map<UUID, Long> blockHitCooldowns = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        config = new PluginConfig(this);
        config.load();

        // OldCombatMechanics tooltip compatibility (no-op when OCM is absent)
        ocmReader = new OcmConfigReader();
        ocmReader.reload();
        ocmDisplay = new OcmDamageDisplay(this, ocmReader);

        getServer().getPluginManager().registerEvents(this, this);

        // Handles being enabled on a running server (e.g. via a plugin
        // manager or /reload)
        for (Player player : getServer().getOnlinePlayers()) {
            player.getScheduler().run(this, task -> updateInventory(player), null);
        }
    }

    @Override
    public void onDisable() {
        // On a normal shutdown players quit before plugins disable, so this
        // only matters for runtime disables (e.g. plugin managers, /reload).
        for (Player player : getServer().getOnlinePlayers()) {
            try {
                stripInventory(player);
            } catch (Exception ignored) {
            }
        }
        blockHitCooldowns.clear();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            ocmReader.reload();
            if (config.load()) {
                for (Player player : getServer().getOnlinePlayers()) {
                    player.getScheduler().run(this, task -> updateInventory(player), null);
                }
                sender.sendMessage(Component.text("VanillaSwordBlocking config reloaded.", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("config.yml contains errors - keeping the previous settings. See the console for details.", NamedTextColor.RED));
            }
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
        if (!isBlocking(player)) return;

        event.setDamage(config.applyBlocking(event.getDamage()));
    }

    /**
     * Block-hitting: a player attacking while blocking. With block-hitting
     * enabled (1.8.9 behavior) the block is never interrupted and an
     * optional outgoing damage multiplier applies; with it disabled the
     * block is interrupted and re-blocking is prevented for a while.
     */
    @EventHandler(ignoreCancelled = true)
    public void onAttackWhileBlocking(@NotNull EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        DamageCause cause = event.getCause();
        if (cause != DamageCause.ENTITY_ATTACK && cause != DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (!config.isActiveIn(attacker.getWorld())) return;
        if (!isBlocking(attacker)) return;

        if (config.blockHittingEnabled()) {
            if (config.blockHitDamageMultiplier() != 1.0) {
                event.setDamage(event.getDamage() * config.blockHitDamageMultiplier());
            }
        } else {
            interruptBlocking(attacker);
        }
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
        if (!isBlocking(player)) return;

        event.setKnockback(event.getKnockback().multiply(config.knockbackMultiplier()));
    }

    /**
     * Fires when an item is inserted/replaced in a slot,
     * including when the slot is already selected.
     */
    @EventHandler
    public void onSlotChange(@NotNull PlayerInventorySlotChangeEvent event) {
        int slot = event.getSlot();
        if (slot == OFFHAND_SLOT) {
            // A shield (or offhand weapon) appearing or disappearing
            // affects whether the whole hotbar may block
            updateInventory(event.getPlayer());
            return;
        }
        if (slot >= 0 && slot <= 8) {
            normalizeSlot(event.getPlayer(), slot);
        }
        // The tooltip fix applies to any slot, not just the hotbar
        if (slot >= 0 && slot < event.getPlayer().getInventory().getSize()) {
            updateOcmDisplay(event.getPlayer(), slot);
        }
    }

    /**
     * Hotbar scroll fallback
     */
    @EventHandler
    public void onItemChangeEvent(@NotNull PlayerItemHeldEvent event) {
        normalizeSlot(event.getPlayer(), event.getNewSlot());
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
        blockHitCooldowns.remove(event.getPlayer().getUniqueId());
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

    /**
     * Whether the player is actively blocking right now, respecting all
     * config restrictions. Only items with our BLOCK-animation component
     * count, so eating food never triggers this.
     */
    private boolean isBlocking(@NotNull Player player) {
        if (isOnBlockHitCooldown(player.getUniqueId())) return false;

        var handle = ((CraftPlayer) player).getHandle();
        var useItem = handle.getUseItem();
        if (useItem.isEmpty() || useItem.getUseAnimation() != ItemUseAnimation.BLOCK) return false;
        if (!config.isBlockableItem(CraftMagicNumbers.getMaterial(useItem.getItem()))) return false;
        if (!config.allowOffhand() && handle.getUsedItemHand() != InteractionHand.MAIN_HAND) return false;
        return !hasShieldConflict(player);
    }

    private boolean hasShieldConflict(@NotNull Player player) {
        return !config.allowWithShield() && player.getInventory().getItemInOffHand().getType() == Material.SHIELD;
    }

    private boolean isOnBlockHitCooldown(@NotNull UUID uuid) {
        Long expiry = blockHitCooldowns.get(uuid);
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    /**
     * Interrupts a block (block-hitting disabled): stops the item use,
     * strips the blocking components so the client lowers the item too,
     * and restores them once the cooldown expires.
     */
    private void interruptBlocking(@NotNull Player player) {
        ((CraftPlayer) player).getHandle().stopUsingItem();
        blockHitCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + config.interruptTicks() * 50L);
        updateInventory(player);

        player.getScheduler().runDelayed(this, task -> {
            if (!isOnBlockHitCooldown(player.getUniqueId())) {
                blockHitCooldowns.remove(player.getUniqueId());
                updateInventory(player);
            }
        }, null, config.interruptTicks() + 1L);
    }

    private boolean isBlockingSlot(int slot) {
        return (slot >= 0 && slot <= 8) || (slot == OFFHAND_SLOT && config.allowOffhand());
    }

    /**
     * Ensures the item in the given slot has the blocking component exactly
     * when it should: a configured blockable item, in a blocking slot, in a
     * world where blocking is active, with no shield conflict or block-hit
     * cooldown. Everything else gets the component stripped.
     */
    private void normalizeSlot(@NotNull Player player, int slot) {
        PlayerInventory inventory = player.getInventory();
        ItemStack stack = inventory.getItem(slot);
        if (stack == null || stack.getType().isAir()) return;
        if (!config.isBlockableItem(stack.getType()) && !stack.hasItemMeta()) return;

        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(stack);
        if (!VanillaBlocking.canReceiveBlockingComponent(nms)) return;

        boolean shouldBlock = config.isActiveIn(player.getWorld())
                && config.isBlockableItem(stack.getType())
                && isBlockingSlot(slot)
                && !hasShieldConflict(player)
                && !isOnBlockHitCooldown(player.getUniqueId());
        if (VanillaBlocking.hasBlockingComponent(nms) == shouldBlock) return;

        if (shouldBlock) {
            VanillaBlocking.addBlockingComponent(nms);
        } else {
            VanillaBlocking.removeBlockingComponent(nms);
        }
        inventory.setItem(slot, CraftItemStack.asBukkitCopy(nms));
    }

    /**
     * Normalizes a whole inventory.
     */
    private void updateInventory(@NotNull Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            normalizeSlot(player, slot);
            updateOcmDisplay(player, slot);
        }
    }

    /**
     * Applies OCM's old-tool-damage value to an item's tooltip. Unlike
     * blocking this covers every item OCM configures (axes and other tools
     * included), not just blockable ones, and is a no-op when OCM is not
     * installed or not active in this world.
     */
    private void updateOcmDisplay(@NotNull Player player, int slot) {
        PlayerInventory inventory = player.getInventory();
        ItemStack stack = inventory.getItem(slot);
        if (stack == null || stack.getType().isAir()) return;

        if (ocmDisplay.updateItemForPlayer(player, stack)) {
            inventory.setItem(slot, stack);
        }
    }

    /**
     * Removes the blocking component from every item in the inventory.
     */
    private void stripInventory(@NotNull Player player) {
        PlayerInventory inventory = player.getInventory();

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) continue;

            net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(stack);
            if (VanillaBlocking.hasBlockingComponent(nms)) {
                VanillaBlocking.removeBlockingComponent(nms);
                stack = CraftItemStack.asBukkitCopy(nms);
                inventory.setItem(slot, stack);
            }

            // Our tooltip modifier must not be saved to disk either
            if (ocmDisplay.forceRemove(stack)) {
                inventory.setItem(slot, stack);
            }
        }
    }
}
