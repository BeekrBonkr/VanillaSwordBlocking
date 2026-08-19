package net.player005.vanillablocking;

/**
 * The damage-reduction maths, kept free of Bukkit so it can be unit tested
 * and so both blocking strategies derive their numbers from the same place.
 */
public final class BlockingFormula {

    /**
     * How damage taken while blocking is calculated.
     */
    public enum Formula {
        /** The exact 1.8.9 formula: {@code (damage + 1) / 2}. */
        LEGACY,
        /** A flat percentage: {@code damage * multiplier}. */
        MULTIPLIER
    }

    private BlockingFormula() {
    }

    /**
     * Damage actually taken for a blocked hit, before any cap.
     *
     * @param damage     pre-armor incoming damage
     * @param multiplier fraction still taken, only used by {@link Formula#MULTIPLIER}
     */
    public static double damageTaken(Formula formula, double damage, double multiplier) {
        if (damage <= 0) return damage; // 1.8.9 only blocked strictly positive damage
        return switch (formula) {
            case LEGACY -> (1.0 + damage) * 0.5;
            case MULTIPLIER -> damage * multiplier;
        };
    }

    /**
     * Damage taken for a blocked hit, with the configured cap on how much of
     * the incoming damage blocking is allowed to remove.
     *
     * @param maxReduction largest fraction of the hit that may be removed, or
     *                     a negative value for no cap
     */
    public static double damageTakenCapped(Formula formula, double damage, double multiplier, double maxReduction) {
        double taken = damageTaken(formula, damage, multiplier);
        if (damage <= 0 || maxReduction < 0) return taken;
        double floor = damage * (1.0 - clampFraction(maxReduction));
        return Math.max(taken, floor);
    }

    /**
     * Largest amount of damage blocking may remove from a hit of this size.
     */
    public static double maxReducible(double damage, double maxReduction) {
        if (maxReduction < 0) return Double.MAX_VALUE;
        return damage * clampFraction(maxReduction);
    }

    /**
     * The {@code base} of a {@code minecraft:blocks_attacks} damage reduction
     * that reproduces this formula. Vanilla removes
     * {@code base + factor * damage} from the hit.
     */
    public static float nativeBase(Formula formula, double multiplier) {
        return switch (formula) {
            // damage - (damage + 1) / 2  ==  -0.5 + 0.5 * damage
            case LEGACY -> -0.5f;
            case MULTIPLIER -> 0f;
        };
    }

    /**
     * The {@code factor} of a {@code minecraft:blocks_attacks} damage
     * reduction that reproduces this formula.
     */
    public static float nativeFactor(Formula formula, double multiplier) {
        return switch (formula) {
            case LEGACY -> 0.5f;
            case MULTIPLIER -> (float) (1.0 - clampFraction(multiplier));
        };
    }

    private static double clampFraction(double value) {
        if (value < 0) return 0;
        return Math.min(value, 1.0);
    }
}
