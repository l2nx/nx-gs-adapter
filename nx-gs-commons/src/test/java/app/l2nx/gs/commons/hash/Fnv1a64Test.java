package app.l2nx.gs.commons.hash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class Fnv1a64Test {

    @Test
    void mix_shouldBeOrderSensitive() {
        long ab = Fnv1a64.mix(Fnv1a64.mix(Fnv1a64.start(), 1L), 2L);
        long ba = Fnv1a64.mix(Fnv1a64.mix(Fnv1a64.start(), 2L), 1L);
        assertNotEquals(ab, ba);
    }

    @Test
    void mixInt_shouldMatchMixLong_forSameUnsignedValue() {
        long viaInt = Fnv1a64.mix(Fnv1a64.start(), -1);
        long viaLong = Fnv1a64.mix(Fnv1a64.start(), 0xffffffffL);
        assertEquals(viaInt, viaLong);
    }

    @Test
    void mixCharSequence_shouldDistinguishStrings() {
        long a = Fnv1a64.mix(Fnv1a64.start(), "alice");
        long b = Fnv1a64.mix(Fnv1a64.start(), "clara");
        assertNotEquals(a, b);
    }

    @Test
    void mixBoolean_shouldDistinguishTrueFromFalse() {
        long t = Fnv1a64.mix(Fnv1a64.start(), true);
        long f = Fnv1a64.mix(Fnv1a64.start(), false);
        assertNotEquals(t, f);
    }
}
