package net.player005.vanillablocking;

import net.player005.vanillablocking.BlockingFormula.Formula;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockingFormulaTest {

    private static final double EPSILON = 1.0E-9;

    @Test
    @DisplayName("the legacy formula matches 1.8.9: (damage + 1) / 2")
    void legacyFormula() {
        assertEquals(4.5, BlockingFormula.damageTaken(Formula.LEGACY, 8.0, 0.5), EPSILON);
        assertEquals(1.0, BlockingFormula.damageTaken(Formula.LEGACY, 1.0, 0.5), EPSILON);
        assertEquals(0.75, BlockingFormula.damageTaken(Formula.LEGACY, 0.5, 0.5), EPSILON);
    }

    @Test
    @DisplayName("1.8.9 only blocked strictly positive damage")
    void nonPositiveDamageIsUntouched() {
        assertEquals(0.0, BlockingFormula.damageTaken(Formula.LEGACY, 0.0, 0.5), EPSILON);
        assertEquals(-3.0, BlockingFormula.damageTaken(Formula.LEGACY, -3.0, 0.5), EPSILON);
        assertEquals(0.0, BlockingFormula.damageTaken(Formula.MULTIPLIER, 0.0, 0.5), EPSILON);
    }

    @Test
    void multiplierFormula() {
        assertEquals(4.0, BlockingFormula.damageTaken(Formula.MULTIPLIER, 8.0, 0.5), EPSILON);
        assertEquals(2.0, BlockingFormula.damageTaken(Formula.MULTIPLIER, 8.0, 0.25), EPSILON);
        assertEquals(8.0, BlockingFormula.damageTaken(Formula.MULTIPLIER, 8.0, 1.0), EPSILON);
    }

    @Test
    @DisplayName("max-reduction caps how much a block can remove")
    void maxReductionCap() {
        // Legacy would take 4.5 of 8; a 0.25 cap means at least 6 is taken.
        assertEquals(6.0, BlockingFormula.damageTakenCapped(Formula.LEGACY, 8.0, 0.5, 0.25), EPSILON);
        // A cap looser than the formula changes nothing.
        assertEquals(4.5, BlockingFormula.damageTakenCapped(Formula.LEGACY, 8.0, 0.5, 0.9), EPSILON);
        // -1 disables the cap.
        assertEquals(4.5, BlockingFormula.damageTakenCapped(Formula.LEGACY, 8.0, 0.5, -1), EPSILON);
        // A cap of 0 means blocking removes nothing.
        assertEquals(8.0, BlockingFormula.damageTakenCapped(Formula.LEGACY, 8.0, 0.5, 0.0), EPSILON);
    }

    @Test
    void maxReducibleRespectsTheCap() {
        assertEquals(2.0, BlockingFormula.maxReducible(8.0, 0.25), EPSILON);
        assertTrue(BlockingFormula.maxReducible(8.0, -1) > 1.0E30);
        // Fractions above 1 are clamped, so a block can never heal.
        assertEquals(8.0, BlockingFormula.maxReducible(8.0, 5.0), EPSILON);
    }

    @Test
    @DisplayName("the native component's base/factor reproduce the same numbers")
    void nativeMappingMatchesTheFormula() {
        for (double damage = 0.5; damage <= 40; damage += 0.5) {
            double expectedLegacy = BlockingFormula.damageTaken(Formula.LEGACY, damage, 0.5);
            double nativeLegacy = damage - (BlockingFormula.nativeBase(Formula.LEGACY, 0.5)
                    + BlockingFormula.nativeFactor(Formula.LEGACY, 0.5) * damage);
            assertEquals(expectedLegacy, nativeLegacy, 1.0E-6, "legacy at " + damage);

            for (double multiplier : new double[]{0.0, 0.25, 0.5, 0.8, 1.0}) {
                double expected = BlockingFormula.damageTaken(Formula.MULTIPLIER, damage, multiplier);
                double actual = damage - (BlockingFormula.nativeBase(Formula.MULTIPLIER, multiplier)
                        + BlockingFormula.nativeFactor(Formula.MULTIPLIER, multiplier) * damage);
                assertEquals(expected, actual, 1.0E-6, "multiplier " + multiplier + " at " + damage);
            }
        }
    }
}
