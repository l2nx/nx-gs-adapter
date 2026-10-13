package app.l2nx.gs.db.sync;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.spi.model.ChildSource;
import app.l2nx.gs.adapter.api.spi.model.EntityMapping;
import app.l2nx.gs.adapter.api.spi.model.ParentRef;
import app.l2nx.gs.adapter.api.spi.model.PrimarySource;
import app.l2nx.gs.adapter.api.spi.model.RowFilter;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DbSyncModuleIdentValidationTest {

    @Test
    void validateIdentifiers_shouldAcceptCleanMapping() {
        DbSyncModule.validateIdentifiers(Collections.singletonList(
                mapping("clan_data", "clan_id", Arrays.asList("clan_name", "clan_level"), Collections.emptyList())));
    }

    @Test
    void validateIdentifiers_shouldRejectSqlInjectionInTableName() {
        EntityMapping<?> bad = mapping(
                "clan_data; DROP TABLE x", "clan_id", Collections.singletonList("clan_name"), Collections.emptyList());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> DbSyncModule.validateIdentifiers(Collections.singletonList(bad)));
        assertTrue(ex.getMessage().contains("primary.tableName"));
    }

    @Test
    void validateIdentifiers_shouldRejectSqlInjectionInPkColumn() {
        EntityMapping<?> bad = mapping(
                "clan_data", "clan_id; DROP TABLE x", Collections.singletonList("clan_name"), Collections.emptyList());

        assertThrows(
                IllegalStateException.class, () -> DbSyncModule.validateIdentifiers(Collections.singletonList(bad)));
    }

    @Test
    void validateIdentifiers_shouldRejectSqlInjectionInHashedColumn() {
        EntityMapping<?> bad = mapping(
                "clan_data",
                "clan_id",
                Arrays.asList("clan_name", "x; DROP TABLE clan_data; --"),
                Collections.emptyList());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> DbSyncModule.validateIdentifiers(Collections.singletonList(bad)));
        assertTrue(ex.getMessage().contains("hashedColumns"));
    }

    @Test
    void validateIdentifiers_shouldRejectSqlInjectionInChildSource() {
        ChildSource<Object> hostileChild = new ChildSource<Object>() {
            @Override
            public String tableName() {
                return "clan_skills";
            }

            @Override
            public String fkColumn() {
                return "clan_id; DROP TABLE x";
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
        EntityMapping<?> bad = mapping(
                "clan_data",
                "clan_id",
                Collections.singletonList("clan_name"),
                Collections.singletonList(hostileChild));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> DbSyncModule.validateIdentifiers(Collections.singletonList(bad)));
        assertTrue(ex.getMessage().contains("child.fkColumn"));
    }

    @Test
    void validateIdentifiers_shouldAcceptChildWithRowFilter() {
        DbSyncModule.validateIdentifiers(Collections.singletonList(mapping(
                "clan_data",
                "clan_id",
                Collections.singletonList("clan_name"),
                Collections.singletonList(childWithRowFilter(RowFilter.notStartsWith("kind", "tmp_"))))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"kind = 0; DROP TABLE x", "kind --", "  ", "t.kind", "kind'"})
    void validateIdentifiers_shouldRejectNonIdentifierRowFilterColumn(String column) {
        EntityMapping<?> bad = mapping(
                "clan_data",
                "clan_id",
                Collections.singletonList("clan_name"),
                Collections.singletonList(childWithRowFilter(RowFilter.eq(column, 0))));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> DbSyncModule.validateIdentifiers(Collections.singletonList(bad)));
        assertTrue(ex.getMessage().contains("rowFilter.column"));
    }

    @Test
    void validateIdentifiers_shouldRejectDuplicateChildTableWithinOneEntity() {
        EntityMapping<?> bad = mapping(
                "clan_data",
                "clan_id",
                Collections.singletonList("clan_name"),
                Arrays.<ChildSource<?>>asList(
                        childWithRowFilter(RowFilter.eq("kind", 0)), childWithRowFilter(RowFilter.eq("kind", 1))));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> DbSyncModule.validateIdentifiers(Collections.singletonList(bad)));
        assertTrue(ex.getMessage().contains("clan_skills"));
        assertTrue(ex.getMessage().contains("more than once"));
    }

    @Test
    void validateIdentifiers_shouldAcceptParentRefReferencingDeclaredEntity() {
        EntityMapping<?> clan =
                mapping("clan_data", "clan_id", Collections.singletonList("clan_name"), Collections.emptyList());
        EntityMapping<?> item = withParentRefs(
                mapping("items", "object_id", Collections.singletonList("count"), Collections.emptyList()),
                "item",
                Collections.singletonList(ParentRef.of("clan", "owner_id")));

        DbSyncModule.validateIdentifiers(Arrays.asList(clan, item));
    }

    @Test
    void validateIdentifiers_shouldRejectParentRefWithUnknownParentEntity() {
        EntityMapping<?> item = withParentRefs(
                mapping("items", "object_id", Collections.singletonList("count"), Collections.emptyList()),
                "item",
                Collections.singletonList(ParentRef.of("ghost", "owner_id")));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> DbSyncModule.validateIdentifiers(Collections.singletonList(item)));
        assertTrue(ex.getMessage().contains("parentRef.parentEntityName"));
    }

    @Test
    void validateIdentifiers_shouldRejectSqlInjectionInParentRefFkColumn() {
        EntityMapping<?> clan =
                mapping("clan_data", "clan_id", Collections.singletonList("clan_name"), Collections.emptyList());
        EntityMapping<?> item = withParentRefs(
                mapping("items", "object_id", Collections.singletonList("count"), Collections.emptyList()),
                "item",
                Collections.singletonList(ParentRef.of("clan", "owner_id; DROP TABLE x")));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> DbSyncModule.validateIdentifiers(Arrays.asList(clan, item)));
        assertTrue(ex.getMessage().contains("parentRef.fkColumn"));
    }

    private static ChildSource<Object> childWithRowFilter(RowFilter rowFilter) {
        return new ChildSource<Object>() {
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
            public RowFilter rowFilter() {
                return rowFilter;
            }

            @Override
            public Object mapRow(ResultSet rs) {
                return null;
            }
        };
    }

    private static EntityMapping<Object> withParentRefs(
            final EntityMapping<Object> delegate, final String entityName, final List<ParentRef> refs) {
        return new EntityMapping<Object>() {
            @Override
            public String entityName() {
                return entityName;
            }

            @Override
            public Class<Object> dtoType() {
                return delegate.dtoType();
            }

            @Override
            public PrimarySource<?> primary() {
                return delegate.primary();
            }

            @Override
            public List<ChildSource<?>> children() {
                return delegate.children();
            }

            @Override
            public List<ParentRef> parentRefs() {
                return refs;
            }

            @Override
            public Object mapEntity(Object primaryRow, Map<String, List<Object>> childRowsByTable) {
                return delegate.mapEntity(primaryRow, childRowsByTable);
            }
        };
    }

    private static EntityMapping<Object> mapping(
            final String table, final String pk, final List<String> hashed, final List<ChildSource<?>> children) {
        final PrimarySource<Object> primary = new PrimarySource<Object>() {
            @Override
            public String tableName() {
                return table;
            }

            @Override
            public String pkColumn() {
                return pk;
            }

            @Override
            public List<String> hashedColumns() {
                return hashed;
            }

            @Override
            public Object mapRow(ResultSet rs) {
                return null;
            }
        };
        return new EntityMapping<Object>() {
            @Override
            public String entityName() {
                return "clan";
            }

            @Override
            public Class<Object> dtoType() {
                return Object.class;
            }

            @Override
            public PrimarySource<?> primary() {
                return primary;
            }

            @Override
            public List<ChildSource<?>> children() {
                return children;
            }

            @Override
            public Object mapEntity(Object primaryRow, Map<String, List<Object>> childRowsByTable) {
                return primaryRow;
            }
        };
    }
}
