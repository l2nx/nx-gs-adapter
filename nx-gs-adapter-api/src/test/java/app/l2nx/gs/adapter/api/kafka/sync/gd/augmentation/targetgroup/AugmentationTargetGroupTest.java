package app.l2nx.gs.adapter.api.kafka.sync.gd.augmentation.targetgroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AugmentationTargetGroupTest {

    private static AugmentationTargetGroup fullyPopulated() {
        return AugmentationTargetGroup.builder()
                .id(7)
                .itemTemplateIds(Arrays.asList(100, 200, 300))
                .build();
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new ParameterNamesModule());
    }

    @Nested
    class Json {

        @Test
        void toJson_shouldCarryTheContractFieldNames() {
            JsonObject actual = new Gson().toJsonTree(fullyPopulated()).getAsJsonObject();

            assertEquals(7, actual.get("id").getAsInt());
            assertFalse(actual.has("kind"));
            assertEquals(3, actual.getAsJsonArray("itemTemplateIds").size());
            assertEquals(200, actual.getAsJsonArray("itemTemplateIds").get(1).getAsInt());
        }
    }

    @Nested
    class Validate {

        @Test
        void build_shouldAcceptEmptyMembers() {
            AugmentationTargetGroup actual =
                    AugmentationTargetGroup.builder().id(1).build();

            assertEquals(0, actual.getItemTemplateIds().size());
        }

        @Test
        void constructor_shouldRejectNullItemTemplateIds() {
            assertThrows(NullPointerException.class, () -> new AugmentationTargetGroup(1, null));
        }

        @Test
        void constructor_shouldCopyAndFreezeMembers() {
            List<Integer> source = new ArrayList<Integer>(Arrays.asList(1, 2));
            AugmentationTargetGroup actual = new AugmentationTargetGroup(1, source);

            source.add(3);

            assertEquals(Arrays.asList(1, 2), actual.getItemTemplateIds());
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> actual.getItemTemplateIds().add(1));
        }
    }

    @Test
    void toBuilderAndJackson_shouldRoundTripEveryField() throws IOException {
        AugmentationTargetGroup expected = fullyPopulated();

        AugmentationTargetGroup actual = expected.toBuilder().build();

        assertEquals(expected, actual);
        assertEquals(expected.hashCode(), actual.hashCode());
        assertEquals(7, actual.getId());
        assertEquals(Arrays.asList(100, 200, 300), actual.getItemTemplateIds());

        assertEquals(
                expected, mapper().readValue(mapper().writeValueAsString(expected), AugmentationTargetGroup.class));
    }
}
