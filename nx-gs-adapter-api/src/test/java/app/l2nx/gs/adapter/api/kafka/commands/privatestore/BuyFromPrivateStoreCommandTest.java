package app.l2nx.gs.adapter.api.kafka.commands.privatestore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import app.l2nx.gs.adapter.api.kafka.commands.privatestore.model.BuyLine;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
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

    private static BuyLine line(long itemId) {
        return BuyLine.builder()
                .item(ItemSnapshot.builder().itemId(itemId).itemTemplateId(57L).build())
                .count(1L)
                .unitPriceAdena(100L)
                .build();
    }

    private static BuyFromPrivateStoreCommand.Builder valid() {
        return BuyFromPrivateStoreCommand.builder()
                .buyerCharacterId(1)
                .sellerCharacterId(2)
                .lines(Collections.singletonList(line(1)))
                .buyerFeePercent(5)
                .deadline(DEADLINE)
                .mailSender(MAIL_SENDER)
                .mailSubject(MAIL_SUBJECT)
                .mailBody(MAIL_BODY)
                .initiator(TestInitiators.USER);
    }

    @ParameterizedTest(name = "buyerCharacterId={0}")
    @CsvSource({"0", "-1"})
    void constructor_shouldReject_whenBuyerCharacterIdNotPositive(int buyerCharacterId) {
        BuyFromPrivateStoreCommand.Builder builder = valid().buyerCharacterId(buyerCharacterId);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @ParameterizedTest(name = "sellerCharacterId={0}")
    @CsvSource({"0", "-1"})
    void constructor_shouldReject_whenSellerCharacterIdNotPositive(int sellerCharacterId) {
        BuyFromPrivateStoreCommand.Builder builder = valid().sellerCharacterId(sellerCharacterId);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    private static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("buyer equals seller", IllegalArgumentException.class, (Consumer<
                                BuyFromPrivateStoreCommand.Builder>)
                        b -> b.buyerCharacterId(5).sellerCharacterId(5)),
                Arguments.of(
                        "lines null", IllegalArgumentException.class, (Consumer<BuyFromPrivateStoreCommand.Builder>)
                                b -> b.lines(null)),
                Arguments.of(
                        "lines empty", IllegalArgumentException.class, (Consumer<BuyFromPrivateStoreCommand.Builder>)
                                b -> b.lines(Collections.emptyList())),
                Arguments.of("lines contain null", IllegalArgumentException.class, (Consumer<
                                BuyFromPrivateStoreCommand.Builder>)
                        b -> b.lines(Arrays.asList(line(1), null))),
                Arguments.of("itemId duplicated across lines", IllegalArgumentException.class, (Consumer<
                                BuyFromPrivateStoreCommand.Builder>)
                        b -> b.lines(Arrays.asList(line(1), line(1)))),
                Arguments.of("buyerFeePercent null", IllegalArgumentException.class, (Consumer<
                                BuyFromPrivateStoreCommand.Builder>)
                        b -> b.buyerFeePercent(null)),
                Arguments.of("deadline null", NullPointerException.class, (Consumer<BuyFromPrivateStoreCommand.Builder>)
                        b -> b.deadline(null)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidFields")
    void constructor_shouldReject_whenFieldInvalid(
            String reason, Class<? extends Throwable> expected, Consumer<BuyFromPrivateStoreCommand.Builder> change) {
        BuyFromPrivateStoreCommand.Builder builder = valid();
        change.accept(builder);
        assertThrows(expected, builder::build);
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
        BuyFromPrivateStoreCommand original = valid().ownerVerified(true).build();

        BuyFromPrivateStoreCommand copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertNotSame(original, copy);
        assertEquals(MAIL_SENDER, copy.getMailSender());
        assertEquals(MAIL_SUBJECT, copy.getMailSubject());
        assertEquals(MAIL_BODY, copy.getMailBody());
        assertTrue(copy.isOwnerVerified());
        assertEquals(1, original.getBuyerCharacterId());
        assertEquals(2, original.getSellerCharacterId());
        assertEquals(DEADLINE, original.getDeadline());
        assertEquals(TestInitiators.USER, original.getInitiator());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(BuyFromPrivateStoreCommand.class).verify();
    }
}
