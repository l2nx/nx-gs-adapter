package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import app.l2nx.gs.adapter.api.kafka.commands.privatestore.model.SellLine;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class SellToPrivateStoreCommandTest {

    private static final Instant DEADLINE = Instant.parse("2026-08-11T12:00:00Z");
    private static final String MAIL_SENDER = "Courier";
    private static final String MAIL_SUBJECT = "Sale to a private store";
    private static final String MAIL_BODY = "The proceeds of your sale have arrived.";

    private static SellLine line(int itemId) {
        return new SellLine(itemId, 1L, 100L);
    }

    private static SellToPrivateStoreCommand.Builder valid() {
        return SellToPrivateStoreCommand.builder()
                .sellerCharId(1)
                .buyerCharId(2)
                .lines(Collections.singletonList(line(1)))
                .sellerFeePercent(5)
                .deadline(DEADLINE)
                .mailSender(MAIL_SENDER)
                .mailSubject(MAIL_SUBJECT)
                .mailBody(MAIL_BODY)
                .initiator(TestInitiators.USER);
    }

    @ParameterizedTest(name = "sellerCharId={0}")
    @CsvSource({"0", "-1"})
    void constructor_shouldReject_whenSellerCharIdNotPositive(int sellerCharId) {
        SellToPrivateStoreCommand.Builder builder = valid().sellerCharId(sellerCharId);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @ParameterizedTest(name = "buyerCharId={0}")
    @CsvSource({"0", "-1"})
    void constructor_shouldReject_whenBuyerCharIdNotPositive(int buyerCharId) {
        SellToPrivateStoreCommand.Builder builder = valid().buyerCharId(buyerCharId);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldReject_whenSellerEqualsBuyer() {
        SellToPrivateStoreCommand.Builder builder = valid().sellerCharId(5).buyerCharId(5);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldReject_whenLinesNull() {
        SellToPrivateStoreCommand.Builder builder = valid().lines(null);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldReject_whenLinesEmpty() {
        SellToPrivateStoreCommand.Builder builder = valid().lines(Collections.emptyList());
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldReject_whenLinesContainNull() {
        List<SellLine> lines = new ArrayList<>();
        lines.add(line(1));
        lines.add(null);
        SellToPrivateStoreCommand.Builder builder = valid().lines(lines);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldAccept_whenLinesManyDistinct() {
        List<SellLine> lines = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            lines.add(line(i));
        }
        SellToPrivateStoreCommand command = valid().lines(lines).build();
        assertEquals(100, command.getLines().size());
    }

    @Test
    void constructor_shouldReject_whenItemIdDuplicatedAcrossLines() {
        List<SellLine> lines = Arrays.asList(line(1), line(1));
        SellToPrivateStoreCommand.Builder builder = valid().lines(lines);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @ParameterizedTest(name = "sellerFeePercent={0}")
    @CsvSource({"-1", "-2147483648", "101", "2147483647"})
    void constructor_shouldReject_whenSellerFeePercentOutOfRange(int sellerFeePercent) {
        SellToPrivateStoreCommand.Builder builder = valid().sellerFeePercent(sellerFeePercent);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @ParameterizedTest(name = "sellerFeePercent={0}")
    @CsvSource({"0", "75", "100"})
    void constructor_shouldAccept_whenSellerFeePercentInRange(int sellerFeePercent) {
        SellToPrivateStoreCommand command =
                valid().sellerFeePercent(sellerFeePercent).build();
        assertEquals(sellerFeePercent, command.getSellerFeePercent().intValue());
    }

    @Test
    void constructor_shouldReject_whenSellerFeePercentNull() {
        SellToPrivateStoreCommand.Builder builder = valid().sellerFeePercent(null);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldReject_whenDeadlineNull() {
        SellToPrivateStoreCommand.Builder builder = valid().deadline(null);
        assertThrows(NullPointerException.class, builder::build);
    }

    static Stream<Arguments> blankMailText() {
        Stream.Builder<Arguments> args = Stream.builder();
        for (String blank : new String[] {null, "", " ", "	"}) {
            args.add(Arguments.of("mailSender", (MailTextSetter) SellToPrivateStoreCommand.Builder::mailSender, blank));
            args.add(Arguments.of(
                    "mailSubject", (MailTextSetter) SellToPrivateStoreCommand.Builder::mailSubject, blank));
            args.add(Arguments.of("mailBody", (MailTextSetter) SellToPrivateStoreCommand.Builder::mailBody, blank));
        }
        return args.build();
    }

    @ParameterizedTest(name = "{0}=[{2}]")
    @MethodSource("blankMailText")
    void constructor_shouldReject_whenMailTextBlank(String field, MailTextSetter setter, String blank) {
        SellToPrivateStoreCommand.Builder builder = setter.apply(valid(), blank);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(exception.getMessage().contains(field));
    }

    interface MailTextSetter {
        SellToPrivateStoreCommand.Builder apply(SellToPrivateStoreCommand.Builder builder, String value);
    }

    @Test
    void getLines_shouldBeUnmodifiable() {
        SellToPrivateStoreCommand command = valid().build();
        assertThrows(
                UnsupportedOperationException.class, () -> command.getLines().add(line(2)));
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        SellToPrivateStoreCommand original = valid().ownerVerified(true).build();

        SellToPrivateStoreCommand copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertNotSame(original, copy);
        assertTrue(copy.isOwnerVerified());
        assertEquals(MAIL_SENDER, copy.getMailSender());
        assertEquals(MAIL_SUBJECT, copy.getMailSubject());
        assertEquals(MAIL_BODY, copy.getMailBody());
    }

    @Test
    void equals_shouldDistinguishDeadline() {
        SellToPrivateStoreCommand a = valid().deadline(DEADLINE).build();
        SellToPrivateStoreCommand b = valid().deadline(DEADLINE.plusSeconds(1)).build();
        assertTrue(!a.equals(b));
    }

    @Test
    void equals_shouldDistinguishMailBody() {
        SellToPrivateStoreCommand a = valid().mailBody(MAIL_BODY).build();
        SellToPrivateStoreCommand b = valid().mailBody(MAIL_BODY + " ...").build();
        assertTrue(!a.equals(b));
        assertTrue(a.hashCode() != b.hashCode());
    }
}
