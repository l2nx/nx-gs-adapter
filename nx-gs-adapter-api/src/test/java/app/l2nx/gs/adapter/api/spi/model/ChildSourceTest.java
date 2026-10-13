package app.l2nx.gs.adapter.api.spi.model;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.ResultSet;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChildSourceTest {

    @Test
    void rowFilter_shouldDefaultToNull_soAllRowsAreVisible() {
        ChildSource<Object> child = new ChildSource<Object>() {
            @Override
            public String tableName() {
                return "clan_skills";
            }

            @Override
            public String fkColumn() {
                return "clan_id";
            }

            @Override
            public List<String> hashedColumns() {
                return Collections.singletonList("skill_id");
            }

            @Override
            public Object mapRow(ResultSet rs) {
                return null;
            }
        };

        assertNull(child.rowFilter());
    }
}
