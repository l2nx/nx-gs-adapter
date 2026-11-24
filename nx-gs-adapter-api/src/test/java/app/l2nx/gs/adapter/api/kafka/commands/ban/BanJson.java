package app.l2nx.gs.adapter.api.kafka.commands.ban;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import java.io.IOException;
import java.time.Instant;

/** The two readers of the ban wire: Gson on the host (no constructor call), Jackson on the platform (constructor). */
public final class BanJson {

    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(
                    Instant.class, (JsonSerializer<Instant>) (src, type, ctx) -> new JsonPrimitive(src.toString()))
            .registerTypeAdapter(
                    Instant.class, (JsonDeserializer<Instant>) (json, type, ctx) -> Instant.parse(json.getAsString()))
            .create();

    public static final ObjectMapper JACKSON = JsonMapper.builder()
            .addModule(new ParameterNamesModule())
            .addModule(new SimpleModule()
                    .addSerializer(Instant.class, new InstantSerializer())
                    .addDeserializer(Instant.class, new InstantDeserializer()))
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private BanJson() {}

    public static String json(String singleQuoted) {
        return singleQuoted.replace('\'', '"');
    }

    private static final class InstantSerializer extends StdSerializer<Instant> {

        private static final long serialVersionUID = 1L;

        InstantSerializer() {
            super(Instant.class);
        }

        @Override
        public void serialize(Instant value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value.toString());
        }
    }

    private static final class InstantDeserializer extends StdDeserializer<Instant> {

        private static final long serialVersionUID = 1L;

        InstantDeserializer() {
            super(Instant.class);
        }

        @Override
        public Instant deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
            return Instant.parse(parser.getText());
        }
    }
}
