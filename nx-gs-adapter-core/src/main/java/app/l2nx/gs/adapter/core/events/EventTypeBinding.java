package app.l2nx.gs.adapter.core.events;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/** {@link #messageTypeBytes()} is pre-encoded once so the daemon thread does not re-encode per envelope. */
final class EventTypeBinding {

    private final String familyKey;
    private final String messageType;
    private final byte[] messageTypeBytes;
    private final Function<Object, byte[]> partitionKeyExtractor;
    private final UnaryOperator<Object> wireNormalizer;

    EventTypeBinding(
            String familyKey,
            String messageType,
            Function<Object, byte[]> partitionKeyExtractor,
            UnaryOperator<Object> wireNormalizer) {
        this.familyKey = familyKey;
        this.messageType = messageType;
        this.messageTypeBytes = messageType.getBytes(StandardCharsets.UTF_8);
        this.partitionKeyExtractor = partitionKeyExtractor;
        this.wireNormalizer = wireNormalizer;
    }

    String familyKey() {
        return familyKey;
    }

    String messageType() {
        return messageType;
    }

    byte[] messageTypeBytes() {
        return messageTypeBytes;
    }

    Function<Object, byte[]> partitionKeyExtractor() {
        return partitionKeyExtractor;
    }

    /** Applied before the event is queued; returns the same instance when nothing changes. */
    UnaryOperator<Object> wireNormalizer() {
        return wireNormalizer;
    }
}
