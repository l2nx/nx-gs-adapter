package app.l2nx.gs.adapter.api.kafka.sync.gd.skill.learn;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class SkillLearnItemSetTest {

    @Test
    void build_shouldRejectMissingItems() {
        assertThrows(
                NullPointerException.class, () -> SkillLearnItemSet.builder().build());
    }

    @Test
    void build_shouldRejectEmptyItems() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SkillLearnItemSet.builder()
                        .items(new ArrayList<SkillLearnItem>())
                        .build());
    }
}
