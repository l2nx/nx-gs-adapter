package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.kafka.commands.privatestore.model.BuyLine;
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

class BuyFromPrivateStoreCommandTest {

    private static final Instant DEADLINE = Instant.parse("2026-08-11T12:00:00Z");
    private static final String MAIL_SENDER = "Courier";
    private static final String MAIL_SUBJECT = "You've got a delivery!";
    private static final String MAIL_BODY = "Your purchase from the private store has arrived.";

    private static BuyLine line(int itemId) {
        return BuyLine.builder()
                .itemId(itemId)
                .itemTemplateId(57L)
                .count(1L)
                .unitPriceAdena(100L)
                .build();
    }

    private static BuyFromPrivateStoreCommand.Builder valid() {
        return BuyFromPrivateStoreCommand.builder()
                .buyerCharId(1)
                .sellerCharId(2)
                .lines(Collections.singletonList(line(1)))
                .buyerFeePercent(5)
                .deadline(DEADLINE)
                .mailSender(MAIL_SENDER)
                .mailSubject(MAIL_SUBJECT)
                .mailBody(MAIL_BODY);
    }

    @ParameterizedTest(name = "buyerCharId={0}")
    @CsvSource({"0", "-1"})
    void constructor_shouldReject_whenBuyerCharIdNotPositive(int buyerCharId) {
        BuyFromPrivateStoreCommand.Builder builder = valid().buyerCharId(buyerCharId);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @ParameterizedTest(name = "sellerCharId={0}")
    @CsvSource({"0", "-1"})
    void constructor_shouldReject_whenSellerCharIdNotPositive(int sellerCharId) {
        BuyFromPrivateStoreCommand.Builder builder = valid().sellerCharId(sellerCharId);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldReject_whenBuyerEqualsSeller() {
        BuyFromPrivateStoreCommand.Builder builder = valid().buyerCharId(5).sellerCharId(5);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldReject_whenLinesNull() {
        BuyFromPrivateStoreCommand.Builder builder = valid().lines(null);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldReject_whenLinesEmpty() {
        BuyFromPrivateStoreCommand.Builder builder = valid().lines(Collections.emptyList());
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldReject_whenLinesContainNull() {
        List<BuyLine> lines = new ArrayList<>();
        lines.add(line(1));
        lines.add(null);
        BuyFromPrivateStoreCommand.Builder builder = valid().lines(lines);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldAccept_whenLinesManyDistinct() {
        List<BuyLine> lines = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            lines.add(line(i));
        }
        BuyFromPrivateStoreCommand command = valid().lines(lines).build();
        assertEquals(100, command.getLines().size());
    }

    @Test
    void constructor_shouldReject_whenItemIdDuplicatedAcrossLines() {
        List<BuyLine> lines = Arrays.asList(line(1), line(1));
        BuyFromPrivateStoreCommand.Builder builder = valid().lines(lines);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @ParameterizedTest(name = "buyerFeePercent={0}")
    @CsvSource({"-1", "-2147483648"})
    void constructor_shouldReject_whenBuyerFeePercentNegative(int buyerFeePercent) {
        BuyFromPrivateStoreCommand.Builder builder = valid().buyerFeePercent(buyerFeePercent);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @ParameterizedTest(name = "buyerFeePercent={0}")
    @CsvSource({"0", "75"})
    void constructor_shouldAccept_whenBuyerFeePercentNotNegative(int buyerFeePercent) {
        BuyFromPrivateStoreCommand command =
                valid().buyerFeePercent(buyerFeePercent).build();
        assertEquals(buyerFeePercent, command.getBuyerFeePercent().intValue());
    }

    @Test
    void constructor_shouldReject_whenBuyerFeePercentNull() {
        BuyFromPrivateStoreCommand.Builder builder = valid().buyerFeePercent(null);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldReject_whenDeadlineNull() {
        BuyFromPrivateStoreCommand.Builder builder = valid().deadline(null);
        assertThrows(NullPointerException.class, builder::build);
    }

    static Stream<Arguments> blankMailText() {
        Stream.Builder<Arguments> args = Stream.builder();
        for (String blank : new String[] {null, "", " ", "	"}) {
            args.add(
                    Arguments.of("mailSender", (MailTextSetter) BuyFromPrivateStoreCommand.Builder::mailSender, blank));
            args.add(Arguments.of(
                    "mailSubject", (MailTextSetter) BuyFromPrivateStoreCommand.Builder::mailSubject, blank));
            args.add(Arguments.of("mailBody", (MailTextSetter) BuyFromPrivateStoreCommand.Builder::mailBody, blank));
        }
        return args.build();
    }

    @ParameterizedTest(name = "{0}=[{2}]")
    @MethodSource("blankMailText")
    void constructor_shouldReject_whenMailTextBlank(String field, MailTextSetter setter, String blank) {
        BuyFromPrivateStoreCommand.Builder builder = setter.apply(valid(), blank);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(exception.getMessage().contains(field));
    }

    interface MailTextSetter {
        BuyFromPrivateStoreCommand.Builder apply(BuyFromPrivateStoreCommand.Builder builder, String value);
    }

    @Test
    void getLines_shouldBeUnmodifiable() {
        BuyFromPrivateStoreCommand command = valid().build();
        assertThrows(
                UnsupportedOperationException.class, () -> command.getLines().add(line(2)));
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        BuyFromPrivateStoreCommand original = valid().build();

        BuyFromPrivateStoreCommand copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertNotSame(original, copy);
        assertEquals(MAIL_SENDER, copy.getMailSender());
        assertEquals(MAIL_SUBJECT, copy.getMailSubject());
        assertEquals(MAIL_BODY, copy.getMailBody());
    }

    @Test
    void equals_shouldDistinguishDeadline() {
        BuyFromPrivateStoreCommand a = valid().deadline(DEADLINE).build();
        BuyFromPrivateStoreCommand b = valid().deadline(DEADLINE.plusSeconds(1)).build();
        assertTrue(!a.equals(b));
    }

    @Test
    void equals_shouldDistinguishMailBody() {
        BuyFromPrivateStoreCommand a = valid().mailBody(MAIL_BODY).build();
        BuyFromPrivateStoreCommand b = valid().mailBody(MAIL_BODY + " ...").build();
        assertTrue(!a.equals(b));
        assertTrue(a.hashCode() != b.hashCode());
    }
}
