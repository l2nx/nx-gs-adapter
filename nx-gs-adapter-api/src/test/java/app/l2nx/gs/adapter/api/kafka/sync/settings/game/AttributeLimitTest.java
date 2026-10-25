package app.l2nx.gs.adapter.api.kafka.sync.settings.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.l2nx.gs.adapter.api.domain.item.AttributeItemType;
import app.l2nx.gs.adapter.api.domain.item.AttributeTarget;
import app.l2nx.gs.adapter.api.domain.item.ItemGrade;
import org.junit.jupiter.api.Test;

class AttributeLimitTest {

    private static AttributeLimit.Builder valid() {
        return AttributeLimit.builder()
                .grade(ItemGrade.S)
                .type(AttributeItemType.STONE)
                .target(AttributeTarget.WEAPON)
                .maxPower(150);
    }

    @Test
    void build_shouldRejectNegativePower() {
        assertThrows(IllegalArgumentException.class, () -> valid().maxPower(-1).build());
    }

    @Test
    void build_shouldAcceptZeroPower() {
        assertEquals(0, valid().maxPower(0).build().getMaxPower());
    }

    @Test
    void build_shouldRejectNullKeyParts() {
        assertThrows(NullPointerException.class, () -> valid().grade(null).build());
        assertThrows(NullPointerException.class, () -> valid().type(null).build());
        assertThrows(NullPointerException.class, () -> valid().target(null).build());
    }
}
