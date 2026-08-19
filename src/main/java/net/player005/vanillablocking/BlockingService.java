package net.player005.vanillablocking;

import net.player005.vanillablocking.compat.BedrockPlayers;
import net.player005.vanillablocking.compat.WorldGuardHook;
import net.player005.vanillablocking.item.BlockingStrategy;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Answers "may this player block, and are they blocking right now" for every
 * other part of the plugin. All the restriction options funnel through here
 * so the component the client sees and the damage reduction the server
 * applies can never disagree.
 */
public final class BlockingService {

    /**
     * Set on a player who turned their own sword blocking off with
     * {@code /vsb toggle}.
     */
    private final NamespacedKey toggleKey;

    private final PluginConfig config;
    private final WorldGuardHook worldGuard;

    /**
     * Only used when block-hitting is disabled: players who recently
     * attacked while blocking, mapped to the tick their block-hit cooldown
     * expires on.
     */
    private final Map<UUID, Integer> blockHitCooldowns = new ConcurrentHashMap<>();

    private volatile BlockingStrategy strategy;

    public BlockingService(@NotNull Plugin plugin,
                           @NotNull PluginConfig config,
                           @NotNull BlockingStrategy strategy,
                           @NotNull WorldGuardHook worldGuard) {
        this.toggleKey = new NamespacedKey(plugin, "blocking_disabled");
        this.config = config;
        this.strategy = strategy;
        this.worldGuard = worldGuard;
    }

    public @NotNull BlockingStrategy strategy() {
        return strategy;
    }

    public void strategy(@NotNull BlockingStrategy strategy) {
        this.strategy = strategy;
    }

    public @NotNull PluginConfig config() {
        return config;
    }

    /**
     * Whether this player is allowed to block at all, right here and now.
     * Does not look at what they are holding.
     */
    public boolean mayBlock(@NotNull Player player) {
        if (!config.isActiveIn(player.getWorld())) return false;
        if (config.isGameModeDisabled(player.getGameMode())) return false;
        if (config.requirePermission() && !player.hasPermission("vanillablocking.block")) return false;
        if (isToggledOff(player)) return false;
        if (config.disableForBedrock() && BedrockPlayers.isBedrock(player)) return false;
        if (config.respectWorldGuard() && !worldGuard.allowsBlocking(player)) return false;
        return true;
    }

    /**
     * The parts of "may this player block" that are the same for every slot
     * in their inventory. Walking a whole inventory would otherwise ask
     * WorldGuard the same question forty-one times.
     *
     * @param mayBlock       context: world, game mode, permission, region, toggle
     * @param shieldConflict an offhand shield is stopping the main hand
     * @param onCooldown     the player is in a block-hit interrupt
     */
    public record Context(boolean mayBlock, boolean shieldConflict, boolean onCooldown) {

        /**
         * Whether blocking is allowed at all right now, before looking at
         * any particular item.
         */
        public boolean allows() {
            return mayBlock && !shieldConflict && !onCooldown;
        }
    }

    /**
     * Works out the per-player half of the blocking rules once.
     */
    public @NotNull Context context(@NotNull Player player) {
        return new Context(mayBlock(player), hasShieldConflict(player), isOnBlockHitCooldown(player.getUniqueId()));
    }

    /**
     * Whether the item in the given slot of this player's inventory should
     * currently carry the blocking component.
     */
    public boolean shouldBlockingItemBeActive(@NotNull Context context, @NotNull ItemStack stack, int slot) {
        return context.allows()
                && config.isBlockableItem(stack.getType())
                && isBlockingSlot(slot);
    }

    /**
     * Whether the player is actively blocking right now, respecting every
     * config restriction. Only items carrying our blocking marker count, so
     * eating food or drawing a bow never triggers this.
     */
    public boolean isBlocking(@NotNull Player player) {
        return blockingItem(player) != null;
    }

    /**
     * The item the player is currently blocking with, or null when they are
     * not blocking.
     */
    public @Nullable ItemStack blockingItem(@NotNull Player player) {
        return blockingItem(player, true);
    }

    /**
     * The item the player has raised if - and only if - it is one this
     * plugin made blockable. Applies no restrictions at all.
     * <p>
     * This is how the native strategy tells our blocking apart from a
     * vanilla shield: zeroing the reduction on a hit somebody blocked with
     * an actual shield would break shields for the whole server.
     */
    public @Nullable ItemStack managedActiveItem(@NotNull Player player) {
        if (!player.hasActiveItem()) return null;

        ItemStack active = player.getActiveItem();
        if (active.getType().isAir()) return null;
        if (!config.isBlockableItem(active.getType())) return null;
        if (!strategy.isApplied(active)) return null;
        return active;
    }

    /**
     * The raised item, ignoring {@code block-delay-ticks}. The delay only
     * gates the damage reduction - the player is visibly blocking from the
     * moment they raise the item.
     */
    public @Nullable ItemStack blockingItemIgnoringDelay(@NotNull Player player) {
        return blockingItem(player, false);
    }

    private @Nullable ItemStack blockingItem(@NotNull Player player, boolean respectDelay) {
        if (!player.hasActiveItem()) return null;
        if (isOnBlockHitCooldown(player.getUniqueId())) return null;
        if (!mayBlock(player)) return null;

        ItemStack active = player.getActiveItem();
        if (active.getType().isAir()) return null;
        if (!config.isBlockableItem(active.getType())) return null;
        if (!strategy.isApplied(active)) return null;

        if (!config.allowOffhand() && player.getActiveItemHand() != EquipmentSlot.HAND) return null;
        if (hasShieldConflict(player)) return null;
        if (respectDelay && player.getActiveItemUsedTime() < config.blockDelayTicks()) return null;

        return active;
    }

    /**
     * Whether an offhand item stops the main hand from blocking. Shields are
     * the obvious case; on 1.21.5+ anything else carrying the native
     * blocks_attacks component counts too.
     */
    public boolean hasShieldConflict(@NotNull Player player) {
        if (config.allowWithShield()) return false;

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand.getType().isAir()) return false;

        // With allow-offhand on, the offhand item is one of ours and carries
        // the blocking component itself - it is not a shield competing with
        // the main hand.
        if (strategy.isApplied(offhand)) return false;

        return strategy.isNativeBlocker(offhand);
    }

    /**
     * Hotbar slots always, the offhand slot only when offhand blocking is on.
     */
    public boolean isBlockingSlot(int slot) {
        return (slot >= 0 && slot <= 8) || (slot == ItemNormalizer.OFFHAND_SLOT && config.allowOffhand());
    }

    public boolean isOnBlockHitCooldown(@NotNull UUID uuid) {
        Integer expiry = blockHitCooldowns.get(uuid);
        if (expiry == null) return false;
        if (currentTick() >= expiry) {
            blockHitCooldowns.remove(uuid);
            return false;
        }
        return true;
    }

    public void startBlockHitCooldown(@NotNull UUID uuid) {
        blockHitCooldowns.put(uuid, currentTick() + config.interruptTicks());
    }

    public void clearBlockHitCooldown(@NotNull UUID uuid) {
        blockHitCooldowns.remove(uuid);
    }

    public void forget(@NotNull UUID uuid) {
        blockHitCooldowns.remove(uuid);
    }

    public void clearAllCooldowns() {
        blockHitCooldowns.clear();
    }

    /**
     * Whether the player turned their own sword blocking off.
     */
    public boolean isToggledOff(@NotNull Player player) {
        Byte value = player.getPersistentDataContainer().get(toggleKey, PersistentDataType.BYTE);
        return value != null && value != 0;
    }

    /**
     * Turns this player's sword blocking on or off, persisted across
     * sessions in their persistent data container.
     *
     * @return the new state: true when blocking is enabled again
     */
    public boolean toggle(@NotNull Player player) {
        boolean nowEnabled = isToggledOff(player);
        if (nowEnabled) {
            player.getPersistentDataContainer().remove(toggleKey);
        } else {
            player.getPersistentDataContainer().set(toggleKey, PersistentDataType.BYTE, (byte) 1);
        }
        return nowEnabled;
    }

    /**
     * The server tick, used for cooldowns so they behave the same whether or
     * not the server is keeping up.
     */
    public static int currentTick() {
        return org.bukkit.Bukkit.getCurrentTick();
    }
}
