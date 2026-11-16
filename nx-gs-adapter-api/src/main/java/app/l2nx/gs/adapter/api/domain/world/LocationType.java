package app.l2nx.gs.adapter.api.domain.world;

import app.l2nx.gs.adapter.api.Vocabularies;
import app.l2nx.gs.adapter.api.localization.LocalizedText;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Kind of a named place of the game world: a land, a town, a hunting ground, a dungeon floor, a residence, an instance.
 * Places nest (a floor inside a dungeon, a hunting ground inside a land), so the kind says what the place is, not where
 * it sits. The wire value is the constant name.
 *
 * <p>Unlike the other vocabularies, each constant carries its display name in ru, en, uk and es and a sort order:
 * the platform shows the kind as {@code {code, name}} from several services, and this enum is the one source of
 * those texts.</p>
 */
public enum LocationType {
    /** Top-level region of the world map that other places nest in (Dion Land, Giran Land). */
    LAND("Земля", "Land", "Земля", "Tierra", 1),

    TOWN("Город", "Town", "Місто", "Ciudad", 2),

    HUNTING_GROUND("Поле охоты", "Hunting ground", "Мисливські угіддя", "Zona de caza", 3),

    /** A dungeon or a tower, including each of its floors. */
    DUNGEON("Подземелье", "Dungeon", "Підземелля", "Mazmorra", 4),

    CASTLE("Замок", "Castle", "Замок", "Castillo", 5),

    FORTRESS("Крепость", "Fortress", "Фортеця", "Fortaleza", 6),

    CLAN_HALL("Холл клана", "Clan hall", "Хол клану", "Salón del clan", 7),

    /** An instanced zone entered through a reflection template. */
    REFLECTION("Инстанс", "Instance", "Інстанс", "Instancia", 8),

    /** A named place that is none of the above: a ruin, a lake, a village outskirt. */
    LANDMARK("Место", "Landmark", "Місце", "Lugar", 9),

    /** A place with its own rules: an arena, a battlefield, a peace zone outside a town. */
    SPECIAL_ZONE("Особая зона", "Special zone", "Особлива зона", "Zona especial", 10);

    private final LocalizedText displayName;
    private final int sortOrder;

    LocationType(String ru, String en, String uk, String es, int sortOrder) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("ru", ru);
        values.put("en", en);
        values.put("uk", uk);
        values.put("es", es);
        this.displayName = new LocalizedText(values);
        this.sortOrder = sortOrder;
    }

    public LocalizedText displayName() {
        return displayName;
    }

    /** Position of the kind in a list of kinds, ascending; unique across constants. */
    public int sortOrder() {
        return sortOrder;
    }

    public static @Nullable LocationType fromToken(@Nullable String token) {
        return Vocabularies.fromToken(LocationType.class, token);
    }
}
