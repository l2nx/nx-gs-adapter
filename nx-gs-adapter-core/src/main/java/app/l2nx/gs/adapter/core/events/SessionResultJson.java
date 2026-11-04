package app.l2nx.gs.adapter.core.events;

import app.l2nx.gs.adapter.api.domain.Attribute;
import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import app.l2nx.gs.adapter.api.kafka.events.character.result.SessionResult;
import app.l2nx.gs.adapter.api.kafka.events.character.result.SessionResultDeath;
import app.l2nx.gs.adapter.api.kafka.events.character.result.SessionResultExperience;
import app.l2nx.gs.adapter.api.kafka.events.character.result.SessionResultItem;
import app.l2nx.gs.adapter.api.kafka.events.character.result.SessionResultKill;
import app.l2nx.gs.adapter.api.kafka.events.character.result.SessionResultUnique;
import app.l2nx.gs.adapter.api.kafka.events.character.result.SessionResultUntracked;
import app.l2nx.gs.adapter.core.kafka.gson.AdapterGson;
import app.l2nx.gs.log.NxLog;
import app.l2nx.gs.log.NxLogFactory;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The JSON of a {@link SessionResult}, written by the producer's own Gson: the size measured here is the size the
 * producer's limit checks, and the text the session table holds rebuilds the same block.
 */
public final class SessionResultJson {

    private static final NxLog log = NxLogFactory.getLogger(SessionResultJson.class);

    private static final Gson GSON = AdapterGson.create();

    private SessionResultJson() {}

    /**
     * The text of a block within {@link SessionResult#MAX_JSON_BYTES}, or {@code null} with one ERROR: a block a host
     * failed to fold is dropped so the end of the session still goes out.
     */
    public static @Nullable String toJson(SessionResult result, @Nullable UUID sessionId) {
        String json;
        try {
            json = GSON.toJson(result);
        } catch (RuntimeException e) {
            log.error("Session {}: its result cannot be serialized, dropped: {}", sessionId, e.getMessage(), e);
            return null;
        }
        int bytes = json.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > SessionResult.MAX_JSON_BYTES) {
            log.error(
                    "Session {}: its result takes {} bytes, over the budget of {}; dropped, the end goes out without"
                            + " it",
                    sessionId,
                    bytes,
                    SessionResult.MAX_JSON_BYTES);
            return null;
        }
        return json;
    }

    /**
     * Gson bypasses the constructors and reads an unknown enum constant as {@code null}, a map key included, so every
     * row and snapshot is rebuilt through its checks: a block a newer adapter wrote fails here instead of reaching the
     * wire with a {@code null} source.
     *
     * @throws RuntimeException when the text is not a block this adapter version can carry
     */
    public static SessionResult parse(String json) {
        SessionResult result = GSON.fromJson(json, SessionResult.class);
        if (result == null) {
            throw new IllegalArgumentException("a session result must be a JSON object");
        }
        for (SessionResultItem row : result.getItems()) {
            row.toBuilder().build();
        }
        for (SessionResultUnique row : result.getUniques()) {
            row.toBuilder().build();
            recheck(row.getItem());
        }
        for (SessionResultExperience row : result.getExperience()) {
            row.toBuilder().build();
        }
        for (SessionResultKill row : result.getKills()) {
            row.toBuilder().build();
        }
        for (SessionResultDeath row : result.getDeaths()) {
            row.toBuilder().build();
        }
        for (SessionResultUntracked row : result.getUntracked()) {
            row.toBuilder().build();
        }
        return result.toBuilder().build();
    }

    private static void recheck(ItemSnapshot item) {
        ItemSnapshot rebuilt = item.toBuilder().build();
        Map<Attribute, Integer> attributes = rebuilt.getAttributes();
        if (attributes != null && attributes.containsKey(null)) {
            throw new IllegalArgumentException("an item attribute is unknown to this adapter version");
        }
    }
}
