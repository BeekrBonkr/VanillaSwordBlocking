package net.player005.vanillablocking.ocm;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Corrects item tooltips to match the damage OldCombatMechanics actually
 * deals. Purely cosmetic: OCM still applies the damage itself, we only stop
 * the tooltip claiming the modern value.
 */
public final class OcmDamageDisplay {

    private final OcmConfigReader ocm;
    private final NamespacedKey pdcKey;
    private final NamespacedKey modifierKey;

    private volatile boolean enabled = true;

    public OcmDamageDisplay(@NotNull Plugin plugin, @NotNull OcmConfigReader ocm) {
        this.ocm = ocm;
        this.pdcKey = new NamespacedKey(plugin, "ocm_damage_display");
        this.modifierKey = new NamespacedKey(plugin, "ocm_damage_display_delta");
    }

    /**
     * Turns the whole feature on or off (config option
     * {@code oldcombatmechanics.tooltip-compat}).
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Remove our tooltip modifier regardless of OCM state/world.
     *
     * @return whether the stack was modified, so the caller can write it back
     */
    public boolean forceRemove(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        // An untouched item cannot carry our modifier, and reading its meta
        // is the expensive part of this whole pass.
        if (!item.hasItemMeta()) return false;

        final ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        boolean changed = removeOurModifier(meta);
        final PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(pdcKey)) {
            pdc.remove(pdcKey);
            changed = true;
        }

        if (changed) item.setItemMeta(meta);
        return changed;
    }

    /**
     * Apply / remove the tooltip delta modifier depending on OCM config,
     * world and material.
     *
     * @return whether the stack was modified, so the caller can write it back
     */
    public boolean updateItemForPlayer(@NotNull Player player, @Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (!enabled || !ocm.isPresent()) return forceRemove(item);

        final boolean active = ocm.isActiveIn(player.getWorld());
        final Double desired = ocm.desiredDamage(item.getType());

        // Nothing to add and nothing that could need removing.
        if ((!active || desired == null) && !item.hasItemMeta()) return false;

        final ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        final PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (!active || desired == null) {
            boolean changed = removeOurModifier(meta);
            if (pdc.has(pdcKey)) {
                pdc.remove(pdcKey);
                changed = true;
            }
            if (changed) item.setItemMeta(meta);
            return changed;
        }

        // Always remove our modifier before computing the current value, so
        // we measure the item without us and cannot oscillate on and off.
        boolean removed = removeOurModifier(meta);

        final double currentWithoutUs = computeAttackDamageTooltip(meta);
        final double delta = desired - currentWithoutUs;

        if (Math.abs(delta) < 1.0E-6) {
            boolean changed = removed;
            if (pdc.has(pdcKey)) {
                pdc.remove(pdcKey);
                changed = true;
            }
            if (changed) item.setItemMeta(meta);
            return changed;
        }

        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                modifierKey,
                delta,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.MAINHAND
        ));
        pdc.set(pdcKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return true;
    }

    private boolean removeOurModifier(@NotNull ItemMeta meta) {
        final Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(Attribute.ATTACK_DAMAGE);
        if (modifiers == null) return false;

        boolean changed = false;
        for (AttributeModifier modifier : new ArrayList<>(modifiers)) {
            if (modifierKey.equals(modifier.getKey())) {
                meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE, modifier);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Approximate the tooltip value the client shows for attack damage.
     * Vanilla starts from base 1.0 and applies operations in order:
     * ADD_NUMBER, then ADD_SCALAR, then MULTIPLY_SCALAR_1.
     */
    private static double computeAttackDamageTooltip(@NotNull ItemMeta meta) {
        final double base = 1.0;

        final Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(Attribute.ATTACK_DAMAGE);
        if (modifiers == null) return base;

        double add = 0.0;
        double addScalar = 0.0;
        double multiplyScalar1 = 0.0;

        for (AttributeModifier modifier : modifiers) {
            final EquipmentSlotGroup group = modifier.getSlotGroup();
            if (group != null && !group.test(org.bukkit.inventory.EquipmentSlot.HAND)) continue;

            switch (modifier.getOperation()) {
                case ADD_NUMBER -> add += modifier.getAmount();
                case ADD_SCALAR -> addScalar += modifier.getAmount();
                case MULTIPLY_SCALAR_1 -> multiplyScalar1 += modifier.getAmount();
            }
        }

        double value = base + add;
        value *= (1.0 + addScalar);
        value *= (1.0 + multiplyScalar1);
        return value;
    }
}
