package app.l2nx.gs.adapter.api.kafka.sync.gd.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.domain.Attribute;
import app.l2nx.gs.adapter.api.domain.character.CharacterRace;
import app.l2nx.gs.adapter.api.domain.character.clazz.CharacterClass;
import app.l2nx.gs.adapter.api.domain.clan.SocialClass;
import app.l2nx.gs.adapter.api.domain.skill.SkillRewardKind;
import app.l2nx.gs.adapter.api.domain.skill.SkillTreeKind;
import app.l2nx.gs.adapter.api.domain.skill.condition.SkillConditionLogic;
import app.l2nx.gs.adapter.api.domain.skill.condition.SkillConditionType;
import app.l2nx.gs.adapter.api.domain.skill.effect.SkillEffectCategory;
import app.l2nx.gs.adapter.api.domain.skill.effect.SkillEffectScope;
import app.l2nx.gs.adapter.api.domain.skill.effect.SkillEffectType;
import app.l2nx.gs.adapter.api.domain.skill.kind.SkillBasicProperty;
import app.l2nx.gs.adapter.api.domain.skill.kind.SkillMagicType;
import app.l2nx.gs.adapter.api.domain.skill.kind.SkillOperateType;
import app.l2nx.gs.adapter.api.domain.skill.kind.SkillTrait;
import app.l2nx.gs.adapter.api.domain.skill.target.SkillAffectObject;
import app.l2nx.gs.adapter.api.domain.skill.target.SkillAffectScope;
import app.l2nx.gs.adapter.api.domain.skill.target.SkillTargetType;
import app.l2nx.gs.adapter.api.domain.stat.Stat;
import app.l2nx.gs.adapter.api.domain.stat.StatOperation;
import app.l2nx.gs.adapter.api.kafka.sync.gd.StatModifier;
import app.l2nx.gs.adapter.api.kafka.sync.gd.skill.condition.SkillConditionNode;
import app.l2nx.gs.adapter.api.kafka.sync.gd.skill.effect.SkillEffect;
import app.l2nx.gs.adapter.api.kafka.sync.gd.skill.learn.SkillLearn;
import app.l2nx.gs.adapter.api.kafka.sync.gd.skill.learn.SkillLearnItem;
import app.l2nx.gs.adapter.api.kafka.sync.gd.skill.learn.SkillLearnItemSet;
import app.l2nx.gs.adapter.api.kafka.sync.gd.skill.learn.SkillLearnSubClass;
import app.l2nx.gs.adapter.api.kafka.sync.gd.skill.learn.SkillLevelRef;
import app.l2nx.gs.adapter.api.kafka.sync.gd.skill.produced.SkillProducedItem;
import app.l2nx.gs.adapter.api.kafka.sync.gd.skill.produced.SkillProducedItemGroup;
import app.l2nx.gs.adapter.api.localization.LocalizedText;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SkillTest {

    private static Map<String, String> map(String key, String value) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put(key, value);
        return map;
    }

    private static SkillConditionNode leafCondition(String typeRaw) {
        return SkillConditionNode.builder()
                .type(SkillConditionType.CASTER_LEVEL)
                .typeRaw(typeRaw)
                .negated(true)
                .params(map("min", "40"))
                .paramsRaw(map("level", "40"))
                .build();
    }

    private static SkillConditionNode conditionTree() {
        return SkillConditionNode.builder()
                .logic(SkillConditionLogic.OR)
                .negated(true)
                .children(Collections.singletonList(leafCondition("engineLevelToken")))
                .build();
    }

    private static StatModifier statModifier() {
        return StatModifier.builder()
                .stat(Stat.MAX_HP)
                .statRaw("engineStat")
                .operation(StatOperation.SUBTRACT)
                .operationRaw("engineOperation")
                .value(100.5)
                .valueRaw("100.5")
                .order(7)
                .conditionTree(leafCondition("engineStatGate"))
                .build();
    }

    private static SkillEffect effect() {
        return SkillEffect.builder()
                .type(SkillEffectType.STUN)
                .typeRaw("engineEffectToken")
                .scope(SkillEffectScope.SELF)
                .scopeRaw("engineScope")
                .params(map("durationSec", "9"))
                .paramsRaw(map("time", "9"))
                .value(3.5)
                .valueRaw("3.5")
                .activateRatePercent(60)
                .abnormalTypeRaw("ENGINE_ABNORMAL")
                .abnormalLevel(2)
                .abnormalTimeSec(15)
                .tickIntervalMs(2500)
                .conditionTree(leafCondition("engineEffectGate"))
                .derived(true)
                .statModifiers(Collections.singletonList(statModifier()))
                .build();
    }

    private static SkillLevel level(int level) {
        Map<String, Integer> negate = new LinkedHashMap<String, Integer>();
        negate.put("ENGINE_ABNORMAL", 4);
        return SkillLevel.builder()
                .level(level)
                .icon("icon.level")
                .mpConsume(11)
                .mpInitialConsume(12)
                .hpConsume(13)
                .itemTemplateId(14)
                .itemTemplateCount(15)
                .soulMaxConsume(16)
                .energyConsume(17)
                .chargeConsume(18)
                .castRange(19)
                .effectRange(20)
                .affectRange(21)
                .affectLimit(22)
                .fanStartAngle(23)
                .fanRadius(24)
                .fanAngle(25)
                .magicLevel(26)
                .abnormalLevel(27)
                .abnormalTimeSec(28)
                .hitTimeMs(29)
                .coolTimeMs(30)
                .reuseDelayMs(31)
                .channelingMpConsume(32)
                .channelingSkillId(33)
                .channelingTickIntervalMs(34)
                .channelingStartDelayMs(35)
                .baseCritRate(36)
                .power(37.5)
                .pvpPower(38.5)
                .pvePower(39.5)
                .minChancePercent(40)
                .maxChancePercent(41)
                .activateRatePercent(42)
                .levelModifier(43)
                .lethalStrikeRatePercent(44)
                .halfKillRatePercent(45)
                .blowChancePercent(46)
                .negateRatePercent(47)
                .negateAbnormalTypesRaw(negate)
                .aggroPoints(48)
                .attribute(Attribute.WATER)
                .attributeRaw("engineAttribute")
                .attributePower(49)
                .name(LocalizedText.of(map("en", "Skill name")))
                .description(LocalizedText.of(map("en", "Skill description")))
                .conditionTree(conditionTree())
                .effects(Collections.singletonList(effect()))
                .statModifiers(Collections.singletonList(statModifier()))
                .producedItems(Collections.singletonList(SkillProducedItemGroup.builder()
                        .chancePercent(12.5)
                        .enchantLevel(3)
                        .items(Collections.singletonList(SkillProducedItem.builder()
                                .itemTemplateId(5001)
                                .rewardKind(SkillRewardKind.PC_CAFE_POINTS)
                                .minCount(2L)
                                .maxCount(5L)
                                .build()))
                        .build()))
                .build();
    }

    private static SkillLearn learn() {
        return SkillLearn.builder()
                .treeKind(SkillTreeKind.TRANSFER)
                .treeKindRaw("engineTree")
                .treeName("engineTreeName")
                .clazz(CharacterClass.ELVEN_MAGE)
                .skillLevel(2)
                .requiredLevel(20)
                .requiredClanLevel(5)
                .learnSp(1_000_000L)
                .learnClanReputation(2_000L)
                .autoLearn(true)
                .learnedByNpc(true)
                .learnedByForgottenScroll(true)
                .requiredItemSets(Collections.singletonList(SkillLearnItemSet.builder()
                        .items(Collections.singletonList(SkillLearnItem.builder()
                                .itemTemplateId(1512)
                                .itemTemplateCount(3L)
                                .build()))
                        .build()))
                .prerequisites(Collections.singletonList(
                        SkillLevelRef.builder().skillId(77).skillLevel(4).build()))
                .races(Collections.singletonList(CharacterRace.ELF))
                .residenceIds(Collections.singletonList(61))
                .socialClass(SocialClass.VASSAL)
                .subClassRequirements(Collections.singletonList(
                        SkillLearnSubClass.builder().slot(2).level(75).build()))
                .removedSkillIds(Collections.singletonList(88))
                .requiredDualClassLevel(85)
                .abilityTreeId(1)
                .abilityRow(2)
                .abilityColumn(3)
                .abilityPointsRequired(4)
                .build();
    }

    private static SkillFlags flags() {
        return SkillFlags.builder()
                .debuff(true)
                .offensive(true)
                .staticReuse(true)
                .blockedInOlympiad(true)
                .overHit(true)
                .ignoreShield(true)
                .nextActionAttack(true)
                .dispellable(true)
                .reflectable(true)
                .stayAfterDeath(true)
                .triggered(true)
                .build();
    }

    private static SkillEnchantRoute enchantRoute() {
        return SkillEnchantRoute.builder()
                .baseLevel(3)
                .route(2)
                .enchantLevel(5)
                .enchantAdena(100L)
                .enchantExp(200L)
                .enchantSp(300L)
                .enchantChanceByCharLevelPercent(Collections.singletonMap(76, 82))
                .node(level(3))
                .build();
    }

    private static GearScoreContribution gearScoreContribution() {
        return GearScoreContribution.builder()
                .kind("engineKind")
                .value(8)
                .classIds(Collections.singletonList(12))
                .build();
    }

    private static Skill fullyPopulated() {
        return Skill.builder()
                .id(100)
                .operateType(SkillOperateType.A3)
                .operateTypeRaw("engineOperate")
                .skillTypeRaw("engineSkillType")
                .targetType(SkillTargetType.SELF)
                .targetTypeRaw("engineTarget")
                .affectScope(SkillAffectScope.RANGE)
                .affectScopeRaw("engineAffectScope")
                .affectObject(SkillAffectObject.FRIEND)
                .affectObjectRaw("engineAffectObject")
                .trait(SkillTrait.BLEED)
                .traitRaw("engineTrait")
                .magicType(SkillMagicType.MAGIC)
                .magicTypeRaw("engineMagic")
                .basicProperty(SkillBasicProperty.PHYSICAL)
                .basicPropertyRaw("engineProperty")
                .abnormalTypeRaw("ENGINE_ABNORMAL")
                .abnormalVisualEffectsRaw(Collections.singletonList("ENGINE_VISUAL"))
                .saveVs(Stat.MAX_HP)
                .saveVsRaw("engineSave")
                .effectCategory(SkillEffectCategory.DEBUFF)
                .sharedReuseGroup(9)
                .icon("icon.skill")
                .maxLevel(3)
                .flags(flags())
                .levels(Collections.singletonList(level(2)))
                .enchantRoutes(Collections.singletonList(enchantRoute()))
                .learns(Collections.singletonList(learn()))
                .gearScoreContributions(Collections.singletonList(gearScoreContribution()))
                .build();
    }

    private static ObjectMapper mapper() {
        SimpleModule localizedText = new SimpleModule();
        localizedText.addSerializer(LocalizedText.class, new JsonSerializer<LocalizedText>() {
            @Override
            public void serialize(LocalizedText value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                gen.writeObject(value.values());
            }
        });
        localizedText.addDeserializer(LocalizedText.class, new JsonDeserializer<LocalizedText>() {
            @Override
            public LocalizedText deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                return new LocalizedText(parser.readValueAs(new TypeReference<Map<String, String>>() {}));
            }
        });
        return new ObjectMapper().registerModule(new ParameterNamesModule()).registerModule(localizedText);
    }

    @Nested
    class Json {

        @Test
        void toJson_shouldCarryTheContractFieldNames() {
            JsonObject actual = new Gson().toJsonTree(fullyPopulated()).getAsJsonObject();

            assertEquals("A3", actual.get("operateType").getAsString());
            assertEquals("engineOperate", actual.get("operateTypeRaw").getAsString());
            assertEquals("ENGINE_ABNORMAL", actual.get("abnormalTypeRaw").getAsString());
            assertFalse(actual.has("classes"));
            assertFalse(actual.has("conditions"));

            JsonObject learn = actual.getAsJsonArray("learns").get(0).getAsJsonObject();
            assertEquals("TRANSFER", learn.get("treeKind").getAsString());
            assertEquals("engineTree", learn.get("treeKindRaw").getAsString());

            JsonObject level = actual.getAsJsonArray("levels").get(0).getAsJsonObject();
            JsonObject conditionTree = level.getAsJsonObject("conditionTree");
            assertEquals("OR", conditionTree.get("logic").getAsString());
            JsonObject leaf = conditionTree.getAsJsonArray("children").get(0).getAsJsonObject();
            assertEquals("CASTER_LEVEL", leaf.get("type").getAsString());
            assertEquals("engineLevelToken", leaf.get("typeRaw").getAsString());
            assertTrue(leaf.get("negated").getAsBoolean());

            JsonObject effect = level.getAsJsonArray("effects").get(0).getAsJsonObject();
            assertEquals("STUN", effect.get("type").getAsString());
            assertEquals("engineEffectToken", effect.get("typeRaw").getAsString());
            assertEquals(
                    "9", effect.getAsJsonObject("params").get("durationSec").getAsString());
            assertEquals("9", effect.getAsJsonObject("paramsRaw").get("time").getAsString());
            assertEquals("ENGINE_ABNORMAL", effect.get("abnormalTypeRaw").getAsString());
            JsonObject modifier = effect.getAsJsonArray("statModifiers").get(0).getAsJsonObject();
            assertEquals("MAX_HP", modifier.get("stat").getAsString());
            assertEquals("engineOperation", modifier.get("operationRaw").getAsString());
        }

        @Test
        void fromJson_shouldRoundTripEveryField() {
            Skill expected = fullyPopulated();
            Gson gson = new Gson();

            Skill actual = gson.fromJson(gson.toJson(expected), Skill.class);

            assertEquals(expected, actual);
        }
    }

    @Nested
    class JacksonBinding {

        @ParameterizedTest
        @ValueSource(
                strings = {"{\"logic\":\"AND\",\"negated\":false}", "{\"type\":\"CASTER_LEVEL\",\"negated\":false}"})
        void readValue_shouldReject_whenContainerHasNoChildrenOrLeafNoTypeRaw(String json) {
            JsonMappingException actual =
                    assertThrows(JsonMappingException.class, () -> mapper().readValue(json, SkillConditionNode.class));

            assertInstanceOf(IllegalArgumentException.class, actual.getCause());
        }

        @Test
        void readValue_shouldKeepEffectParams_whenTypeUnknownToConsumer() throws IOException {
            String json = "{\"type\":\"FUTURE_EFFECT\",\"typeRaw\":\"future\",\"params\":{\"power\":\"10\"}}";

            SkillEffect actual = mapper().enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                    .readValue(json, SkillEffect.class);

            assertNull(actual.getType());
            assertEquals("future", actual.getTypeRaw());
            assertEquals("10", actual.getParams().get("power"));
        }

        @Test
        void readValue_shouldKeepContainer_whenLogicUnknownToConsumer() throws IOException {
            String json = "{\"logic\":\"XOR\",\"negated\":false,"
                    + "\"children\":[{\"typeRaw\":\"PlayerLevel\",\"negated\":false}]}";

            SkillConditionNode actual = mapper().enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                    .readValue(json, SkillConditionNode.class);

            assertNull(actual.getLogic());
            assertEquals(1, actual.getChildren().size());
        }
    }

    @Test
    void toBuilderAndJackson_shouldRoundTripEveryField() throws IOException {
        Skill expected = fullyPopulated();

        Skill actual = expected.toBuilder().build();

        assertEquals(expected, actual);
        assertEquals(expected.hashCode(), actual.hashCode());
        assertEquals(100, actual.getId());
        assertEquals(SkillOperateType.A3, actual.getOperateType());
        assertEquals("engineOperate", actual.getOperateTypeRaw());
        assertEquals("engineSkillType", actual.getSkillTypeRaw());
        assertEquals(SkillTargetType.SELF, actual.getTargetType());
        assertEquals("engineTarget", actual.getTargetTypeRaw());
        assertEquals(SkillAffectScope.RANGE, actual.getAffectScope());
        assertEquals("engineAffectScope", actual.getAffectScopeRaw());
        assertEquals(SkillAffectObject.FRIEND, actual.getAffectObject());
        assertEquals("engineAffectObject", actual.getAffectObjectRaw());
        assertEquals(SkillTrait.BLEED, actual.getTrait());
        assertEquals("engineTrait", actual.getTraitRaw());
        assertEquals(SkillMagicType.MAGIC, actual.getMagicType());
        assertEquals("engineMagic", actual.getMagicTypeRaw());
        assertEquals(SkillBasicProperty.PHYSICAL, actual.getBasicProperty());
        assertEquals("engineProperty", actual.getBasicPropertyRaw());
        assertEquals("ENGINE_ABNORMAL", actual.getAbnormalTypeRaw());
        assertEquals(Collections.singletonList("ENGINE_VISUAL"), actual.getAbnormalVisualEffectsRaw());
        assertEquals(Stat.MAX_HP, actual.getSaveVs());
        assertEquals("engineSave", actual.getSaveVsRaw());
        assertEquals(SkillEffectCategory.DEBUFF, actual.getEffectCategory());
        assertEquals(Integer.valueOf(9), actual.getSharedReuseGroup());
        assertEquals("icon.skill", actual.getIcon());
        assertEquals(Integer.valueOf(3), actual.getMaxLevel());
        assertEquals(flags(), actual.getFlags());
        assertEquals(Collections.singletonList(level(2)), actual.getLevels());
        assertEquals(Collections.singletonList(enchantRoute()), actual.getEnchantRoutes());
        assertEquals(Collections.singletonList(learn()), actual.getLearns());
        assertEquals(Collections.singletonList(gearScoreContribution()), actual.getGearScoreContributions());

        ObjectMapper mapper = mapper();
        assertEquals(expected, mapper.readValue(mapper.writeValueAsString(expected), Skill.class));
    }

    @Nested
    class NestedTypes {

        @Test
        void toBuilder_shouldRoundtripEveryField_forStatModifier() {
            StatModifier actual = statModifier().toBuilder().build();

            assertEquals(statModifier(), actual);
            assertEquals(Stat.MAX_HP, actual.getStat());
            assertEquals("engineStat", actual.getStatRaw());
            assertEquals(StatOperation.SUBTRACT, actual.getOperation());
            assertEquals("engineOperation", actual.getOperationRaw());
            assertEquals(100.5, actual.getValue());
            assertEquals("100.5", actual.getValueRaw());
            assertEquals(7, actual.getOrder());
            assertEquals(leafCondition("engineStatGate"), actual.getConditionTree());
        }

        @Test
        void toBuilder_shouldRoundtripEveryField_forSkillEffect() {
            SkillEffect actual = effect().toBuilder().build();

            assertEquals(effect(), actual);
            assertEquals(SkillEffectType.STUN, actual.getType());
            assertEquals("engineEffectToken", actual.getTypeRaw());
            assertEquals(SkillEffectScope.SELF, actual.getScope());
            assertEquals("engineScope", actual.getScopeRaw());
            assertEquals(map("durationSec", "9"), actual.getParams());
            assertEquals(map("time", "9"), actual.getParamsRaw());
            assertEquals(3.5, actual.getValue());
            assertEquals("3.5", actual.getValueRaw());
            assertEquals(60, actual.getActivateRatePercent());
            assertEquals("ENGINE_ABNORMAL", actual.getAbnormalTypeRaw());
            assertEquals(2, actual.getAbnormalLevel());
            assertEquals(15, actual.getAbnormalTimeSec());
            assertEquals(2500, actual.getTickIntervalMs());
            assertEquals(leafCondition("engineEffectGate"), actual.getConditionTree());
            assertTrue(actual.getDerived());
            assertEquals(Collections.singletonList(statModifier()), actual.getStatModifiers());
        }

        @Test
        void toBuilder_shouldRoundtripEveryField_forSkillLevel() {
            SkillLevel actual = level(2).toBuilder().build();

            assertEquals(level(2), actual);
            assertEquals(2, actual.getLevel());
            assertEquals("icon.level", actual.getIcon());
            assertEquals(11, actual.getMpConsume());
            assertEquals(12, actual.getMpInitialConsume());
            assertEquals(13, actual.getHpConsume());
            assertEquals(14, actual.getItemTemplateId());
            assertEquals(15, actual.getItemTemplateCount());
            assertEquals(16, actual.getSoulMaxConsume());
            assertEquals(17, actual.getEnergyConsume());
            assertEquals(18, actual.getChargeConsume());
            assertEquals(19, actual.getCastRange());
            assertEquals(20, actual.getEffectRange());
            assertEquals(21, actual.getAffectRange());
            assertEquals(22, actual.getAffectLimit());
            assertEquals(23, actual.getFanStartAngle());
            assertEquals(24, actual.getFanRadius());
            assertEquals(25, actual.getFanAngle());
            assertEquals(26, actual.getMagicLevel());
            assertEquals(27, actual.getAbnormalLevel());
            assertEquals(28, actual.getAbnormalTimeSec());
            assertEquals(29, actual.getHitTimeMs());
            assertEquals(30, actual.getCoolTimeMs());
            assertEquals(31, actual.getReuseDelayMs());
            assertEquals(32, actual.getChannelingMpConsume());
            assertEquals(33, actual.getChannelingSkillId());
            assertEquals(34, actual.getChannelingTickIntervalMs());
            assertEquals(35, actual.getChannelingStartDelayMs());
            assertEquals(36, actual.getBaseCritRate());
            assertEquals(37.5, actual.getPower());
            assertEquals(38.5, actual.getPvpPower());
            assertEquals(39.5, actual.getPvePower());
            assertEquals(40, actual.getMinChancePercent());
            assertEquals(41, actual.getMaxChancePercent());
            assertEquals(42, actual.getActivateRatePercent());
            assertEquals(43, actual.getLevelModifier());
            assertEquals(44, actual.getLethalStrikeRatePercent());
            assertEquals(45, actual.getHalfKillRatePercent());
            assertEquals(46, actual.getBlowChancePercent());
            assertEquals(47, actual.getNegateRatePercent());
            assertEquals(Collections.singletonMap("ENGINE_ABNORMAL", 4), actual.getNegateAbnormalTypesRaw());
            assertEquals(48, actual.getAggroPoints());
            assertEquals(Attribute.WATER, actual.getAttribute());
            assertEquals("engineAttribute", actual.getAttributeRaw());
            assertEquals(49, actual.getAttributePower());
            assertEquals(LocalizedText.of(map("en", "Skill name")), actual.getName());
            assertEquals(LocalizedText.of(map("en", "Skill description")), actual.getDescription());
            assertEquals(conditionTree(), actual.getConditionTree());
            assertEquals(Collections.singletonList(effect()), actual.getEffects());
            assertEquals(Collections.singletonList(statModifier()), actual.getStatModifiers());
        }

        @Test
        void toBuilder_shouldRoundtripEveryField_forSkillLearn() {
            SkillLearn actual = learn().toBuilder().build();

            assertEquals(learn(), actual);
            assertEquals(SkillTreeKind.TRANSFER, actual.getTreeKind());
            assertEquals("engineTree", actual.getTreeKindRaw());
            assertEquals("engineTreeName", actual.getTreeName());
            assertEquals(CharacterClass.ELVEN_MAGE, actual.getClazz());
            assertEquals(2, actual.getSkillLevel());
            assertEquals(20, actual.getRequiredLevel());
            assertEquals(5, actual.getRequiredClanLevel());
            assertEquals(1_000_000L, actual.getLearnSp());
            assertEquals(2_000L, actual.getLearnClanReputation());
            assertTrue(actual.getAutoLearn());
            assertTrue(actual.getLearnedByNpc());
            assertTrue(actual.getLearnedByForgottenScroll());
            assertEquals(Collections.singletonList(CharacterRace.ELF), actual.getRaces());
            assertEquals(Collections.singletonList(61), actual.getResidenceIds());
            assertEquals(SocialClass.VASSAL, actual.getSocialClass());
            assertEquals(Collections.singletonList(88), actual.getRemovedSkillIds());
            assertEquals(85, actual.getRequiredDualClassLevel());
            assertEquals(1, actual.getAbilityTreeId());
            assertEquals(2, actual.getAbilityRow());
            assertEquals(3, actual.getAbilityColumn());
            assertEquals(4, actual.getAbilityPointsRequired());
        }

        @Test
        void toBuilder_shouldRoundtripEveryField_forSkillFlags() {
            SkillFlags actual = flags().toBuilder().build();

            assertEquals(flags(), actual);
            assertTrue(actual.getDebuff());
            assertTrue(actual.getOffensive());
            assertTrue(actual.getStaticReuse());
            assertTrue(actual.getBlockedInOlympiad());
            assertTrue(actual.getOverHit());
            assertTrue(actual.getIgnoreShield());
            assertTrue(actual.getNextActionAttack());
            assertTrue(actual.getDispellable());
            assertTrue(actual.getReflectable());
            assertTrue(actual.getStayAfterDeath());
            assertTrue(actual.getTriggered());
        }

        @Test
        void toBuilder_shouldRoundtripEveryField_forSkillEnchantRoute() {
            SkillEnchantRoute actual = enchantRoute().toBuilder().build();

            assertEquals(enchantRoute(), actual);
            assertEquals(3, actual.getBaseLevel());
            assertEquals(2, actual.getRoute());
            assertEquals(5, actual.getEnchantLevel());
            assertEquals(100L, actual.getEnchantAdena());
            assertEquals(200L, actual.getEnchantExp());
            assertEquals(300L, actual.getEnchantSp());
            assertEquals(Collections.singletonMap(76, 82), actual.getEnchantChanceByCharLevelPercent());
            assertEquals(level(3), actual.getNode());
        }

        @Test
        void toBuilder_shouldRoundtripEveryField_forGearScoreContribution() {
            GearScoreContribution actual = gearScoreContribution().toBuilder().build();

            assertEquals(gearScoreContribution(), actual);
            assertEquals("engineKind", actual.getKind());
            assertEquals(8, actual.getValue());
            assertEquals(Collections.singletonList(12), actual.getClassIds());
        }

        @Test
        void toBuilder_shouldRoundtripEveryField_forSkillConditionNode() {
            SkillConditionNode actual =
                    leafCondition("engineLevelToken").toBuilder().build();

            assertEquals(leafCondition("engineLevelToken"), actual);
            assertEquals(SkillConditionType.CASTER_LEVEL, actual.getType());
            assertEquals("engineLevelToken", actual.getTypeRaw());
            assertTrue(actual.isNegated());
            assertEquals(map("min", "40"), actual.getParams());
            assertEquals(map("level", "40"), actual.getParamsRaw());
        }

        @Test
        void toBuilder_shouldRoundtripEveryField_forProducedItems() {
            SkillProducedItemGroup group = level(2).getProducedItems().get(0);
            SkillProducedItem item = group.getItems().get(0);

            assertEquals(12.5, group.getChancePercent());
            assertEquals(3, group.getEnchantLevel());
            assertEquals(1, group.getItems().size());
            assertEquals(5001, item.getItemTemplateId());
            assertEquals(SkillRewardKind.PC_CAFE_POINTS, item.getRewardKind());
            assertEquals(2L, item.getMinCount());
            assertEquals(5L, item.getMaxCount());
            assertEquals(group, group.toBuilder().build());
            assertEquals(item, item.toBuilder().build());
        }

        @Test
        void toBuilder_shouldRoundtripEveryField_forLearnRequirements() {
            SkillLearnItemSet itemSet = learn().getRequiredItemSets().get(0);
            SkillLearnItem item = itemSet.getItems().get(0);
            SkillLevelRef prerequisite = learn().getPrerequisites().get(0);
            SkillLearnSubClass subClass = learn().getSubClassRequirements().get(0);

            assertEquals(1512, item.getItemTemplateId());
            assertEquals(3L, item.getItemTemplateCount());
            assertEquals(77, prerequisite.getSkillId());
            assertEquals(4, prerequisite.getSkillLevel());
            assertEquals(2, subClass.getSlot());
            assertEquals(75, subClass.getLevel());
            assertEquals(itemSet, itemSet.toBuilder().build());
            assertEquals(item, item.toBuilder().build());
            assertEquals(prerequisite, prerequisite.toBuilder().build());
            assertEquals(subClass, subClass.toBuilder().build());
        }
    }
}
