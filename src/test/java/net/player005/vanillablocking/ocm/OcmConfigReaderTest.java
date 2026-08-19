package net.player005.vanillablocking.ocm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OcmConfigReaderTest {

    @Test
    void translatesPreFlatteningNames() {
        assertEquals("WOODEN_SWORD", OcmConfigReader.normaliseMaterialName("WOOD_SWORD"));
        assertEquals("GOLDEN_AXE", OcmConfigReader.normaliseMaterialName("gold_axe"));
        assertEquals("WOODEN_SHOVEL", OcmConfigReader.normaliseMaterialName("WOOD_SPADE"));
        assertEquals("GOLDEN_SHOVEL", OcmConfigReader.normaliseMaterialName("GOLD_SPADE"));
        assertEquals("IRON_SHOVEL", OcmConfigReader.normaliseMaterialName("IRON_SPADE"));
    }

    @Test
    void leavesModernNamesAlone() {
        assertEquals("NETHERITE_SWORD", OcmConfigReader.normaliseMaterialName("netherite_sword"));
        assertEquals("DIAMOND_AXE", OcmConfigReader.normaliseMaterialName("  diamond_axe  "));
    }

    @Test
    void rejectsEmptyInput() {
        assertNull(OcmConfigReader.normaliseMaterialName(null));
        assertNull(OcmConfigReader.normaliseMaterialName("   "));
    }
}
