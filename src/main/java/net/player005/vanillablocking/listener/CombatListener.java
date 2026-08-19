package net.player005.vanillablocking.listener;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.player005.vanillablocking.BlockingFormula;
import net.player005.vanillablocking.BlockingService;
import net.player005.vanillablocking.BlockingTracker;
import net.player005.vanillablocking.ItemNormalizer;
import net.player005.vanillablocking.Messages;
import net.player005.vanillablocking.PluginConfig;
import net.player005.vanillablocking.api.PlayerBlockedDamageEvent;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Everything that happens while a player is blocking: the damage reduction
 * itself, block-hitting, knockback and the optional hit feedback.
 */
public final class CombatListener implements Listener {

    private final Plugin plugin;
    private final BlockingService service;
    private final ItemNormalizer normalizer;
    private final BlockingTracker tracker;
    private final Messages messages;

    public CombatListener(@NotNull Plugin plugin,
                          @NotNull BlockingService service,
                          @NotNull ItemNormalizer normalizer,
                          @NotNull BlockingTracker tracker,
                          @NotNull Messages messages) {
        this.plugin = plugin;
        this.service = service;
        this.normalizer = normalizer;
        this.tracker = tracker;
        this.messages = messages;
    }

    /**
     * Damage reduction while blocking. Bukkit's base damage is the
     * pre-armor value, which is also where 1.8.9 applied blocking.
     */
    @EventHandler(ignoreCancelled = true)
    public void onDamagePlayer(@NotNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        PluginConfig config = service.config();
        if (!config.isActiveIn(player.getWorld())) return;

        if (service.strategy().nativeDamageReduction()) {
            correctNativeBlock(event, player);
            return;
        }

        if (!isBlockableCause(event)) return;

        ItemStack item = service.blockingItem(player);
        if (item == null) return;
        if (!isAllowedAttacker(event)) return;

        double incoming = event.getDamage();
        double taken = config.applyBlocking(incoming, item.getType());
        double blocked = incoming - taken;
        if (blocked <= 0) return;

        blocked = fireBlockedEvent(player, event, item, incoming, blocked);
        if (blocked <= 0) return;

        event.setDamage(incoming - blocked);
        onBlocked(player, item, blocked, false);
    }

    /**
     * On 1.21.5+ the server blocks the hit itself before the event is fired.
     * All that is left for us is to undo it where our restrictions say the
     * player should not have been blocking, and to apply the damage cap and
     * the API event.
     * <p>
     * DamageModifier is deprecated in favour of the DamageSource API, but it
     * is still the only way to read and adjust the reduction vanilla already
     * applied.
     */
    @SuppressWarnings("deprecation")
    private void correctNativeBlock(@NotNull EntityDamageEvent event, @NotNull Player player) {
        if (!event.isApplicable(DamageModifier.BLOCKING)) return;

        double blocked = -event.getDamage(DamageModifier.BLOCKING);
        if (blocked <= 0) return;

        // A hit blocked with a real shield is none of our business. Only
        // items we made blockable are ours to correct.
        ItemStack item = service.managedActiveItem(player);
        if (item == null) return;

        // The component enforces block-delay-ticks itself, so the delay is
        // deliberately not re-checked here.
        if (service.blockingItemIgnoringDelay(player) == null
                || !isBlockableCause(event)
                || !isAllowedAttacker(event)) {
            event.setDamage(DamageModifier.BLOCKING, 0);
            return;
        }

        double incoming = event.getDamage(DamageModifier.BASE);
        double capped = Math.min(blocked, BlockingFormula.maxReducible(incoming, service.config().maxReduction()));
        capped = fireBlockedEvent(player, event, item, incoming, capped);

        if (Math.abs(capped - blocked) > 1.0E-6) {
            event.setDamage(DamageModifier.BLOCKING, -Math.max(0, capped));
        }
        if (capped > 0) onBlocked(player, item, capped, true);
    }

    /**
     * @return how much damage should be blocked after listeners had their
     * say, or 0 when the block was cancelled
     */
    private double fireBlockedEvent(@NotNull Player player,
                                    @NotNull EntityDamageEvent cause,
                                    @NotNull ItemStack item,
                                    double incoming,
                                    double blocked) {
        PlayerBlockedDamageEvent event = new PlayerBlockedDamageEvent(player, cause, item, incoming, blocked);
        event.callEvent();
        if (event.isCancelled()) return 0;
        return Math.max(0, Math.min(event.getBlockedDamage(), incoming));
    }

    /**
     * Feedback and durability cost for a hit that was actually blocked.
     *
     * @param nativeBlock whether the server already blocked the hit itself,
     *                    in which case the block sound and the durability
     *                    cost are part of the item component and must not be
     *                    applied a second time here
     */
    private void onBlocked(@NotNull Player player, @NotNull ItemStack item, double blocked, boolean nativeBlock) {
        PluginConfig config = service.config();

        String sound = config.feedbackSound();
        if (!nativeBlock && !sound.isBlank()) {
            try {
                player.playSound(Sound.sound(Key.key(sound), Sound.Source.PLAYER,
                        config.feedbackVolume(), config.feedbackPitch()), Sound.Emitter.self());
            } catch (Exception exception) {
                // An invalid sound key should not break combat.
            }
        }

        if (config.feedbackParticles()) {
            player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 6, 0.2, 0.2, 0.2, 0.02);
        }

        if (config.feedbackActionBar() && messages.has("feedback.blocked")) {
            player.sendActionBar(messages.get("feedback.blocked",
                    Messages.placeholder("blocked", String.format("%.1f", blocked)),
                    Messages.placeholder("hearts", String.format("%.1f", blocked / 2.0))));
        }

        int cost = config.durabilityCost();
        if (!nativeBlock && cost > 0) {
            EquipmentSlot hand = player.getActiveItemHand();
            if (hand != null) player.damageItemStack(hand, cost);
        }
    }

    /**
     * Whether blocking protects against this kind of damage. The 1.8.9 rule
     * was "anything armour reduces", which the server can tell us per hit -
     * that also covers damage types added by datapacks and mods.
     */
    @SuppressWarnings("deprecation")
    private boolean isBlockableCause(@NotNull EntityDamageEvent event) {
        if (service.config().causeRule() == PluginConfig.CauseRule.LIST) {
            return service.config().isBlockable(event.getCause());
        }
        return event.isApplicable(DamageModifier.ARMOR);
    }

    /**
     * Honours {@code restrictions.pvp-only}.
     */
    private boolean isAllowedAttacker(@NotNull EntityDamageEvent event) {
        if (!service.config().pvpOnly()) return true;
        return attacker(event) instanceof Player;
    }

    private @Nullable Object attacker(@NotNull EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) return null;
        if (byEntity.getDamager() instanceof Projectile projectile) return projectile.getShooter();
        return byEntity.getDamager();
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
        if (!service.config().isActiveIn(attacker.getWorld())) return;
        if (service.blockingItemIgnoringDelay(attacker) == null) return;

        if (!service.config().blockHittingEnabled()) {
            interruptBlocking(attacker);
            return;
        }

        if (service.config().blockHitDamageMultiplier() != 1.0) {
            event.setDamage(event.getDamage() * service.config().blockHitDamageMultiplier());
        }
        if (service.config().blockHitFlicker()) {
            attacker.clearActiveItem();
        }
    }

    /**
     * Optional knockback reduction while blocking (1.8.9 default is no
     * reduction, so this is skipped entirely at the authentic setting).
     */
    @EventHandler(ignoreCancelled = true)
    public void onKnockback(@NotNull io.papermc.paper.event.entity.EntityKnockbackEvent event) {
        if (service.config().knockbackMultiplier() == 1.0) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!service.config().isActiveIn(player.getWorld())) return;
        if (service.blockingItem(player) == null) return;

        event.setKnockback(event.getKnockback().multiply(service.config().knockbackMultiplier()));
    }

    /**
     * Interrupts a block (block-hitting disabled): stops the item use,
     * strips the blocking components so the client lowers the item too,
     * and restores them once the cooldown expires.
     */
    private void interruptBlocking(@NotNull Player player) {
        player.clearActiveItem();
        service.startBlockHitCooldown(player.getUniqueId());
        tracker.stop(player);
        normalizer.normalizeHeldSlots(player);

        player.getScheduler().runDelayed(plugin, task -> {
            if (!service.isOnBlockHitCooldown(player.getUniqueId())) {
                normalizer.normalizeHeldSlots(player);
            }
        }, null, service.config().interruptTicks() + 1L);
    }
}
