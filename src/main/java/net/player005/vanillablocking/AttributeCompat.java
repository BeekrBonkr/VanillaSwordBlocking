package net.player005.vanillablocking;

import org.bukkit.attribute.Attribute;

public final class AttributeCompat {
    private AttributeCompat() {}

    public static Attribute attackDamage() {
        // Avoid compile-time reference to missing enum constants.
        // Your server jar provides ATTACK_DAMAGE.
        return Attribute.valueOf("ATTACK_DAMAGE");
    }
}