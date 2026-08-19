package net.player005.vanillablocking.compat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockPlayersTest {

    @Test
    void recognisesFloodgateUuids() {
        assertTrue(BedrockPlayers.isFloodgateUuid(new UUID(0L, 1234L)));
        assertFalse(BedrockPlayers.isFloodgateUuid(UUID.randomUUID()));
        assertFalse(BedrockPlayers.isFloodgateUuid(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")));
    }
}
