package app.l2nx.gs.adapter.api.kafka.events.premiumpurchase;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.events.premiumpurchase.model.Payment;
import app.l2nx.gs.adapter.api.kafka.events.premiumpurchase.model.PurchaseItem;
import app.l2nx.gs.adapter.api.kafka.events.premiumpurchase.model.PurchaseService;
import app.l2nx.gs.adapter.api.kafka.events.premiumpurchase.model.WellKnownServices;
import java.util.*;
import org.junit.jupiter.api.Test;

class PremiumPurchaseEventTest {

    @Test
    void getItemsAndServices_shouldReturnEmptyList_whenBuilderOmits() {
        PremiumPurchaseEvent event = PremiumPurchaseEvent.builder()
                .eventId(UUID.randomUUID())
                .characterId(42L)
                .build();

        assertTrue(event.getItems().isEmpty());
        assertTrue(event.getServices().isEmpty());
    }

    @Test
    void getCharacterName_shouldBeNullable() {
        PremiumPurchaseEvent event = PremiumPurchaseEvent.builder()
                .eventId(UUID.randomUUID())
                .characterId(42L)
                .build();

        assertNull(event.getCharacterName());
        assertNull(event.getAccountName());
    }

    @Test
    void constructor_shouldDefensivelyCopyAndFreezeItemsAndServices() {
        List<PurchaseItem> items = new ArrayList<PurchaseItem>();
        items.add(item(1));
        List<PurchaseService> services = new ArrayList<PurchaseService>();
        services.add(service(WellKnownServices.NOBLESSE));

        PremiumPurchaseEvent event = PremiumPurchaseEvent.builder()
                .eventId(UUID.randomUUID())
                .characterId(42L)
                .items(items)
                .services(services)
                .build();

        items.add(item(2));
        services.add(service(WellKnownServices.SUBCLASS));

        assertEquals(1, event.getItems().size());
        assertEquals(1, event.getServices().size());
        assertThrows(UnsupportedOperationException.class, () -> event.getItems().add(null));
        assertThrows(
                UnsupportedOperationException.class, () -> event.getServices().add(null));
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        Map<String, String> serviceParams = new HashMap<String, String>();
        serviceParams.put("rgb", "0xFFCC00");
        UUID eventId = UUID.randomUUID();
        PurchaseItem item = PurchaseItem.builder()
                .itemId(9627)
                .qty(1)
                .params(Collections.singletonMap("enchant", "3"))
                .payments(Collections.singletonList(
                        Payment.builder().currencyItemId(4037).qty(20).build()))
                .build();
        PurchaseService service = PurchaseService.builder()
                .code(WellKnownServices.NAME_COLOR_CHANGE)
                .params(serviceParams)
                .payments(Collections.singletonList(
                        Payment.builder().currencyItemId(4037).qty(50).build()))
                .build();

        PremiumPurchaseEvent original = PremiumPurchaseEvent.builder()
                .eventId(eventId)
                .characterId(268437521L)
                .characterName("Hisho")
                .accountName("shanon")
                .items(Collections.singletonList(item))
                .services(Collections.singletonList(service))
                .metadata(Collections.singletonMap("k", "v"))
                .build();

        PremiumPurchaseEvent copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(eventId, copy.getEventId());
        assertEquals(268437521L, copy.getCharacterId());
        assertEquals("Hisho", copy.getCharacterName());
        assertEquals("shanon", copy.getAccountName());
        assertEquals(Collections.singletonList(item), copy.getItems());
        assertEquals(Collections.singletonList(service), copy.getServices());
        assertEquals(Collections.singletonMap("k", "v"), copy.getMetadata());
        assertEquals(9627, item.getItemId());
        assertEquals(1, item.getQty());
        assertEquals(WellKnownServices.NAME_COLOR_CHANGE, service.getCode());
        assertEquals(Collections.singletonMap("enchant", "3"), item.getParams());
        assertEquals(
                Collections.singletonList(
                        Payment.builder().currencyItemId(4037).qty(20).build()),
                item.getPayments());
        assertEquals(Collections.singletonMap("rgb", "0xFFCC00"), service.getParams());
        assertEquals(
                Collections.singletonList(
                        Payment.builder().currencyItemId(4037).qty(50).build()),
                service.getPayments());
    }

    @Test
    void purchaseItem_getParams_shouldBeEmpty_whenNull() {
        PurchaseItem item = PurchaseItem.builder()
                .itemId(1)
                .qty(1)
                .payments(Collections.singletonList(
                        Payment.builder().currencyItemId(57).qty(100).build()))
                .build();

        assertTrue(item.getParams().isEmpty());
    }

    @Test
    void purchaseService_getParams_shouldBeEmpty_whenNull() {
        PurchaseService service = PurchaseService.builder()
                .code(WellKnownServices.NOBLESSE)
                .payments(Collections.singletonList(
                        Payment.builder().currencyItemId(4037).qty(50).build()))
                .build();

        assertTrue(service.getParams().isEmpty());
    }

    @Test
    void payment_equals_shouldDistinguishCurrencyItemId() {
        Payment a = Payment.builder().currencyItemId(57).qty(100).build();
        Payment b = Payment.builder().currencyItemId(4037).qty(100).build();

        assertEquals(a, a.toBuilder().build());
        assertThrows(AssertionError.class, () -> assertEquals(a, b));
        assertEquals(57, a.getCurrencyItemId());
        assertEquals(100, a.getQty());
    }

    @Test
    void wellKnownServices_shouldMatchWireCodes() {
        assertEquals("noblesse", WellKnownServices.NOBLESSE);
        assertEquals("subclass", WellKnownServices.SUBCLASS);
        assertEquals("sex_change", WellKnownServices.SEX_CHANGE);
        assertEquals("name_change", WellKnownServices.NAME_CHANGE);
        assertEquals("name_color_change", WellKnownServices.NAME_COLOR_CHANGE);
        assertEquals("title_color_change", WellKnownServices.TITLE_COLOR_CHANGE);
        assertEquals("hero_temporary", WellKnownServices.HERO_TEMPORARY);
        assertEquals("clan_lvl_up", WellKnownServices.CLAN_LVL_UP);
        assertEquals("clan_skill_buy", WellKnownServices.CLAN_SKILL_BUY);
        assertEquals("clan_rep_buy", WellKnownServices.CLAN_REP_BUY);
        assertEquals("clan_fame_buy", WellKnownServices.CLAN_FAME_BUY);
        assertEquals("clan_create_penalty_remove", WellKnownServices.CLAN_CREATE_PENALTY_REMOVE);
        assertEquals("clan_join_penalty_remove", WellKnownServices.CLAN_JOIN_PENALTY_REMOVE);
        assertEquals("clan_invite_penalty_remove", WellKnownServices.CLAN_INVITE_PENALTY_REMOVE);
        assertEquals("ally_penalty_remove", WellKnownServices.ALLY_PENALTY_REMOVE);
        assertEquals("level_up", WellKnownServices.LEVEL_UP);
        assertEquals("level_down", WellKnownServices.LEVEL_DOWN);
        assertEquals("karma_recover", WellKnownServices.KARMA_RECOVER);
        assertEquals("pk_recover", WellKnownServices.PK_RECOVER);
        assertEquals("vitality_recover", WellKnownServices.VITALITY_RECOVER);
        assertEquals("augmentation", WellKnownServices.AUGMENTATION);
        assertEquals("olympiad_pts_buy", WellKnownServices.OLYMPIAD_PTS_BUY);
        assertEquals("soul_cloak_transfer", WellKnownServices.SOUL_CLOAK_TRANSFER);
        assertEquals("premium_account_1h", WellKnownServices.PREMIUM_ACCOUNT_1H);
        assertEquals("premium_account_4h", WellKnownServices.PREMIUM_ACCOUNT_4H);
        assertEquals("premium_account_1d", WellKnownServices.PREMIUM_ACCOUNT_1D);
        assertEquals("premium_account_7d", WellKnownServices.PREMIUM_ACCOUNT_7D);
        assertEquals("premium_account_30d", WellKnownServices.PREMIUM_ACCOUNT_30D);
    }

    private static PurchaseItem item(int itemId) {
        return PurchaseItem.builder()
                .itemId(itemId)
                .qty(1)
                .payments(Collections.singletonList(
                        Payment.builder().currencyItemId(57).qty(100).build()))
                .build();
    }

    private static PurchaseService service(String code) {
        return PurchaseService.builder()
                .code(code)
                .payments(Collections.singletonList(
                        Payment.builder().currencyItemId(4037).qty(50).build()))
                .build();
    }
}
