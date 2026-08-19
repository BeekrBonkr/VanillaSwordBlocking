package net.player005.vanillablocking;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.UUID;

public final class OcmDamageDisplay {

    // Stable UUID so we can remove/replace our own modifier reliably
    private static final UUID OCM_DISPLAY_UUID = UUID.fromString("0f2b91c3-4f74-4a1a-8c7c-4d2c5d2c9e11");

    private final OcmConfigReader ocm;
    private final NamespacedKey pdcKey;

    public OcmDamageDisplay(Plugin plugin, OcmConfigReader ocm) {
        this.ocm = ocm;
        this.pdcKey = new NamespacedKey(plugin, "ocm_damage_display");
    }

    /**
     * Remove our tooltip modifier regardless of OCM state/world.
     *
     * @return whether the stack was modified, so the caller can write it back
     */
    public boolean forceRemove(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        final ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        final Attribute attackDamage = AttributeCompat.attackDamage();
        final PersistentDataContainer pdc = meta.getPersistentDataContainer();

        boolean changed = removeOurModifier(meta, attackDamage);
        if (pdc.has(pdcKey)) {
            pdc.remove(pdcKey);
            changed = true;
        }

        if (changed) item.setItemMeta(meta);
        return changed;
    }

    /**
     * Apply / remove the tooltip delta modifier depending on OCM config + world + material.
     *
     * @return whether the stack was modified, so the caller can write it back
     */
    public boolean updateItemForPlayer(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        final boolean active = ocm.isActiveIn(player.getWorld());
        final Double desired = ocm.desiredDamage(item.getType());

        final ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        final Attribute attackDamage = AttributeCompat.attackDamage();
        final PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // If not applicable: just remove ours if present
        if (!active || desired == null) {
            boolean changed = removeOurModifier(meta, attackDamage);
            if (pdc.has(pdcKey)) {
                pdc.remove(pdcKey);
                changed = true;
            }
            if (changed) item.setItemMeta(meta);
            return changed;
        }

        // KEY FIX:
        // Always remove our modifier BEFORE computing "current" so we don't oscillate on/off.
        boolean removed = removeOurModifier(meta, attackDamage);

        final double currentWithoutUs = computeAttackDamageTooltip(meta, attackDamage);
        final double delta = desired - currentWithoutUs;

        // If we don't need a delta, keep it removed (clean item)
        if (Math.abs(delta) < 1.0E-6) {
            boolean changed = removed;
            if (pdc.has(pdcKey)) {
                pdc.remove(pdcKey);
                changed = true;
            }
            if (changed) item.setItemMeta(meta);
            return changed;
        }

        // Add our delta modifier
        final AttributeModifier mod = new AttributeModifier(
                OCM_DISPLAY_UUID,
                "ocm_damage_display_delta",
                delta,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlot.HAND
        );

        meta.addAttributeModifier(attackDamage, mod);
        pdc.set(pdcKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return true;
    }

    private static boolean removeOurModifier(ItemMeta meta, Attribute attackDamage) {
        boolean changed = false;
        final var mods = meta.getAttributeModifiers(attackDamage);
        if (mods == null) return false;

        for (AttributeModifier m : new ArrayList<>(mods)) {
            // deprecated but still present in your compile target
            if (OCM_DISPLAY_UUID.equals(m.getUniqueId())) {
                meta.removeAttributeModifier(attackDamage, m);
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
    private static double computeAttackDamageTooltip(ItemMeta meta, Attribute attackDamage) {
        double base = 1.0;

        double add = 0.0;
        double addScalar = 0.0;
        double multScalar1 = 0.0;

        final var mods = meta.getAttributeModifiers(attackDamage);
        if (mods == null) return base;

        for (AttributeModifier m : mods) {
            final EquipmentSlot slot = m.getSlot();
            if (slot != null && slot != EquipmentSlot.HAND) continue;

            switch (m.getOperation()) {
                case ADD_NUMBER -> add += m.getAmount();
                case ADD_SCALAR -> addScalar += m.getAmount();
                case MULTIPLY_SCALAR_1 -> multScalar1 += m.getAmount();
            }
        }

        double v = base + add;
        v *= (1.0 + addScalar);
        v *= (1.0 + multScalar1);
        return v;
    }
}