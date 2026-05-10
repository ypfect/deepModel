package com.deepmodel.relation.service;

import com.deepmodel.relation.model.BaseappObjectField;
import com.deepmodel.relation.model.ObjectTypeMeta;
import com.deepmodel.relation.model.ResolveModels;
import com.deepmodel.relation.model.ResolveModels.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SkillsService resolve 相关方法的单元测试。
 * 命名规则: 方法名_场景_期望结果
 */
class SkillsServiceResolveTest {

    private ImpactAnalyzerService analyzerService;
    private SkillsService skillsService;

    @BeforeEach
    void setUp() {
        analyzerService = mock(ImpactAnalyzerService.class);
        skillsService = new SkillsService(analyzerService);

        // 基础 mock：对象类型集合
        Set<String> allTypes = new TreeSet<>(Arrays.asList(
                "ArContract", "ArContractSubjectMatterItem", "ApContract",
                "RevenueConfirmation", "RevenueConfirmationItem", "User", "Org", "Project"
        ));
        when(analyzerService.getAllObjectTypes()).thenReturn(allTypes);

        // 对象元信息
        Map<String, ObjectTypeMeta> metas = new HashMap<>();
        metas.put("ArContract", buildMeta("ArContract", "应收合同", "bill", "应收合同单据", false));
        metas.put("ArContractSubjectMatterItem", buildMeta("ArContractSubjectMatterItem", "应收合同标的", "bill", null, false));
        metas.put("ApContract", buildMeta("ApContract", "应付合同", "bill", null, false));
        metas.put("RevenueConfirmation", buildMeta("RevenueConfirmation", "收入确认单", "bill", null, false));
        metas.put("RevenueConfirmationItem", buildMeta("RevenueConfirmationItem", "收入确认单明细", "bill", null, false));
        metas.put("User", buildMeta("User", "人员", "setting", null, false));
        metas.put("Org", buildMeta("Org", "部门", "setting", null, false));
        metas.put("Project", buildMeta("Project", "项目", "setting", null, false));
        when(analyzerService.getObjectTypeMetas()).thenReturn(Collections.unmodifiableMap(metas));

        // 标题反向索引
        Map<String, List<String>> titleIndex = new HashMap<>();
        titleIndex.put("应收合同", Collections.singletonList("ArContract"));
        titleIndex.put("应收合同标的", Collections.singletonList("ArContractSubjectMatterItem"));
        titleIndex.put("应付合同", Collections.singletonList("ApContract"));
        titleIndex.put("收入确认单", Collections.singletonList("RevenueConfirmation"));
        titleIndex.put("收入确认单明细", Collections.singletonList("RevenueConfirmationItem"));
        titleIndex.put("人员", Collections.singletonList("User"));
        titleIndex.put("部门", Collections.singletonList("Org"));
        titleIndex.put("项目", Collections.singletonList("Project"));
        when(analyzerService.getTitleToObjectTypes()).thenReturn(Collections.unmodifiableMap(titleIndex));

        // 同义词
        Map<String, List<String>> synonyms = new HashMap<>();
        synonyms.put("User", Arrays.asList("人员", "员工", "操作人", "经办人"));
        synonyms.put("ArContract", Arrays.asList("合同", "应收合同", "销售合同"));
        synonyms.put("Org", Arrays.asList("部门", "组织", "机构"));
        synonyms.put("Project", Arrays.asList("项目", "工程"));
        when(analyzerService.getGlobalSynonyms()).thenReturn(Collections.unmodifiableMap(synonyms));

        // objectTitles（兼容接口）
        Map<String, String> objectTitles = new HashMap<>();
        for (Map.Entry<String, ObjectTypeMeta> e : metas.entrySet()) {
            objectTitles.put(e.getKey(), e.getValue().getTitle());
        }
        when(analyzerService.getObjectTitles()).thenReturn(Collections.unmodifiableMap(objectTitles));

        // 主子表关系
        Map<String, Set<String>> mainToDetails = new HashMap<>();
        mainToDetails.put("ArContract", new LinkedHashSet<>(Arrays.asList("ArContractSubjectMatterItem")));
        mainToDetails.put("RevenueConfirmation", new LinkedHashSet<>(Arrays.asList("RevenueConfirmationItem")));
        when(analyzerService.getMainToDetails()).thenReturn(Collections.unmodifiableMap(mainToDetails));

        Map<String, String> detailToMain = new HashMap<>();
        detailToMain.put("ArContractSubjectMatterItem", "ArContract");
        detailToMain.put("RevenueConfirmationItem", "RevenueConfirmation");
        when(analyzerService.getDetailToMain()).thenReturn(Collections.unmodifiableMap(detailToMain));

        // 子表查询
        when(analyzerService.getAllDetailEntities("ArContract"))
                .thenReturn(new LinkedHashSet<>(Arrays.asList("ArContractSubjectMatterItem")));

        // ArContract 字段
        List<BaseappObjectField> arFields = Arrays.asList(
                buildField("ArContract", "amount", "金额", "Amount", null, null, null),
                buildField("ArContract", "originAmount", "原始金额", "Amount", null, null, null),
                buildField("ArContract", "contractNo", "合同编号", "Text", null, null, null),
                buildField("ArContract", "customerId", "客户", "Refer", null, null, null)
        );
        when(analyzerService.getFieldDetailsForObject("ArContract")).thenReturn(arFields);

        // ArContractSubjectMatterItem 字段
        List<BaseappObjectField> smiFields = Arrays.asList(
                buildField("ArContractSubjectMatterItem", "originAmount", "原始金额", "Amount", null, null, null),
                buildField("ArContractSubjectMatterItem", "receiptAmount", "收款金额", "Amount",
                        "{\"srcObjectType\":\"PaymentItem\",\"expression\":\"sum(amount)\"}", null, null),
                buildField("ArContractSubjectMatterItem", "quantity", "数量", "Qty", null, null, null)
        );
        when(analyzerService.getFieldDetailsForObject("ArContractSubjectMatterItem")).thenReturn(smiFields);
    }

    // ======== calculateMatchScore 5 档评分 ========

    @Nested
    class CalculateMatchScoreTest {

        @Test
        void calculateMatchScore_精确匹配_返回最高分() {
            int score = SkillsService.calculateMatchScore("ArContract", "ArContract");
            assertEquals(1000, score);
        }

        @Test
        void calculateMatchScore_精确匹配忽略大小写_返回最高分() {
            int score = SkillsService.calculateMatchScore("arcontract", "ArContract");
            assertEquals(1000, score);
        }

        @Test
        void calculateMatchScore_后缀匹配_返回600档() {
            // target 以 query 结尾
            int score = SkillsService.calculateMatchScore("Contract", "ArContract");
            assertTrue(score >= 480 && score <= 600,
                    "后缀匹配分数应在 480~600 之间, 实际: " + score);
        }

        @Test
        void calculateMatchScore_前缀匹配_返回500档() {
            int score = SkillsService.calculateMatchScore("Ar", "ArContract");
            assertTrue(score >= 400 && score <= 500,
                    "前缀匹配分数应在 400~500 之间, 实际: " + score);
        }

        @Test
        void calculateMatchScore_包含匹配_返回400档() {
            // query 在 target 中间
            int score = SkillsService.calculateMatchScore("Contract", "ArContractSubjectMatterItem");
            assertTrue(score > 0 && score <= 600,
                    "包含匹配分数应大于 0, 实际: " + score);
        }

        @Test
        void calculateMatchScore_反向包含_返回200档() {
            // query 包含 target
            int score = SkillsService.calculateMatchScore("应收合同的金额", "金额");
            assertTrue(score >= 160 && score <= 200,
                    "反向包含分数应在 160~200 之间, 实际: " + score);
        }

        @Test
        void calculateMatchScore_无匹配_返回0() {
            int score = SkillsService.calculateMatchScore("采购", "ArContract");
            assertEquals(0, score);
        }

        @Test
        void calculateMatchScore_紧凑度修正_短查询比长查询分低() {
            // 相同的后缀匹配，但 query 占 target 比例不同
            int shortScore = SkillsService.calculateMatchScore("ct", "ArContract");
            int longScore = SkillsService.calculateMatchScore("Contract", "ArContract");
            assertTrue(longScore > shortScore,
                    "长查询应比短查询分高: long=" + longScore + " short=" + shortScore);
        }

        @Test
        void calculateMatchScore_空输入_返回0() {
            assertEquals(0, SkillsService.calculateMatchScore(null, "test"));
            assertEquals(0, SkillsService.calculateMatchScore("test", null));
            assertEquals(0, SkillsService.calculateMatchScore("", "test"));
            assertEquals(0, SkillsService.calculateMatchScore("test", ""));
        }
    }

    // ======== matchObjects 对象匹配 ========

    @Nested
    class MatchObjectsTest {

        @Test
        void resolve_精确英文名_返回score等于1() {
            ResolveResult result = skillsService.resolve("ArContract", 5, false);

            assertFalse(result.objectMatches.isEmpty());
            ObjectMatch top = result.objectMatches.get(0);
            assertEquals("ArContract", top.objectType);
            assertEquals(1.0, top.score, 0.01);
            assertEquals(ResolveModels.MatchSource.EXACT_NAME, top.matchSource);
        }

        @Test
        void resolve_中文标题精确匹配_返回高分() {
            ResolveResult result = skillsService.resolve("应收合同", 5, false);

            assertFalse(result.objectMatches.isEmpty());
            ObjectMatch top = result.objectMatches.get(0);
            assertEquals("ArContract", top.objectType);
            assertTrue(top.score >= 0.8, "中文标题精确匹配分数应 >= 0.8, 实际: " + top.score);
        }

        @Test
        void resolve_同义词匹配_返回结果() {
            ResolveResult result = skillsService.resolve("销售合同", 5, false);

            assertFalse(result.objectMatches.isEmpty());
            ObjectMatch top = result.objectMatches.get(0);
            assertEquals("ArContract", top.objectType);
            assertEquals(ResolveModels.MatchSource.SYNONYM, top.matchSource);
        }

        @Test
        void resolve_空输入_返回空结果() {
            ResolveResult result = skillsService.resolve("", 5, true);
            assertTrue(result.objectMatches.isEmpty());
        }

        @Test
        void resolve_null输入_返回空结果() {
            ResolveResult result = skillsService.resolve(null, 5, true);
            assertTrue(result.objectMatches.isEmpty());
        }

        @Test
        void resolve_纯标点输入_返回空结果() {
            ResolveResult result = skillsService.resolve("。。。", 5, true);
            assertTrue(result.objectMatches.isEmpty());
        }

        @Test
        void resolve_ObjectMatch包含description和type() {
            ResolveResult result = skillsService.resolve("ArContract", 5, false);

            ObjectMatch top = result.objectMatches.get(0);
            assertEquals("应收合同单据", top.description);
            assertEquals("bill", top.type);
            assertEquals(false, top.isDisabled);
        }
    }

    // ======== matchFields 字段匹配 ========

    @Nested
    class MatchFieldsTest {

        @Test
        void resolve_带字段查询_返回匹配字段() {
            ResolveResult result = skillsService.resolve("ArContract的金额", 5, true);

            assertFalse(result.objectMatches.isEmpty());
            ObjectMatch top = result.objectMatches.get(0);
            assertEquals("ArContract", top.objectType);
            assertFalse(top.fieldMatches.isEmpty(), "应返回字段匹配结果");

            // 应该有金额相关字段
            boolean hasAmount = top.fieldMatches.stream()
                    .anyMatch(fm -> "amount".equals(fm.field) || "originAmount".equals(fm.field));
            assertTrue(hasAmount, "应匹配到金额字段");
        }

        @Test
        void resolve_FieldMatch包含description和enumType() {
            // 构造一个有 description 和 enumType 的字段
            BaseappObjectField statusField = buildField("ArContract", "statusId", "状态", "Enum",
                    null, null, null);
            statusField.setDescription("合同审批状态");
            statusField.setEnumType("ContractStatus");
            statusField.setIsDisabled(false);

            List<BaseappObjectField> fieldsWithDesc = new ArrayList<>(
                    analyzerService.getFieldDetailsForObject("ArContract"));
            fieldsWithDesc.add(statusField);
            when(analyzerService.getFieldDetailsForObject("ArContract")).thenReturn(fieldsWithDesc);

            ResolveResult result = skillsService.resolve("ArContract的状态", 5, true);

            ObjectMatch top = result.objectMatches.get(0);
            Optional<FieldMatch> statusMatch = top.fieldMatches.stream()
                    .filter(fm -> "statusId".equals(fm.field))
                    .findFirst();
            assertTrue(statusMatch.isPresent(), "应匹配到 statusId 字段");
            assertEquals("合同审批状态", statusMatch.get().description);
            assertEquals("ContractStatus", statusMatch.get().enumType);
        }
    }

    // ======== US2: 子表级联搜索 ========

    @Nested
    class DetailQueryTest {

        @Test
        void resolve_子表查询不丢fieldPart_返回子表字段匹配() {
            // "应收合同子表的原始金额" → 子表关键词 "子表"，objectPart="应收合同", fieldPart="原始金额"
            ResolveResult result = skillsService.resolve("应收合同子表的原始金额", 5, true);

            assertFalse(result.objectMatches.isEmpty(), "应返回子表对象");
            ObjectMatch detail = result.objectMatches.get(0);
            assertEquals("ArContractSubjectMatterItem", detail.objectType);
            assertEquals("ArContract", detail.parentEntity);

            // 关键：fieldPart 不应丢失，应返回匹配字段
            assertFalse(detail.fieldMatches.isEmpty(), "子表查询应返回字段匹配结果");
            boolean hasOriginAmount = detail.fieldMatches.stream()
                    .anyMatch(fm -> "originAmount".equals(fm.field));
            assertTrue(hasOriginAmount, "应匹配到 originAmount 字段");
        }

        @Test
        void resolve_子表明细关键词_也能定位子表() {
            // "应收合同明细" 使用 "明细" 关键词
            ResolveResult result = skillsService.resolve("应收合同明细", 5, true);

            assertFalse(result.objectMatches.isEmpty());
            ObjectMatch detail = result.objectMatches.get(0);
            assertEquals("ArContractSubjectMatterItem", detail.objectType);
        }

        @Test
        void resolve_子表查询无fieldPart_不抛异常() {
            // "应收合同子表" 只有子表关键词，没有字段部分
            ResolveResult result = skillsService.resolve("应收合同子表", 5, true);

            assertFalse(result.objectMatches.isEmpty());
            ObjectMatch detail = result.objectMatches.get(0);
            assertEquals("ArContractSubjectMatterItem", detail.objectType);
        }

        @Test
        void resolve_级联搜索_当主表无匹配时搜索子表() {
            // "ArContract的收款金额" → ArContract 上没有 "收款金额"，但子表有
            ResolveResult result = skillsService.resolve("ArContract的收款金额", 5, true);

            assertFalse(result.objectMatches.isEmpty());
            ObjectMatch top = result.objectMatches.get(0);
            assertEquals("ArContract", top.objectType);

            // 级联搜索应在子表找到 receiptAmount
            boolean hasReceiptAmount = top.fieldMatches.stream()
                    .anyMatch(fm -> "receiptAmount".equals(fm.field));
            assertTrue(hasReceiptAmount, "级联搜索应在子表找到 receiptAmount");
        }

        @Test
        void resolve_级联搜索_深度惩罚() {
            // 级联搜索的字段 score 应被衰减
            ResolveResult result = skillsService.resolve("ArContract的收款金额", 5, true);

            ObjectMatch top = result.objectMatches.get(0);
            Optional<FieldMatch> fm = top.fieldMatches.stream()
                    .filter(f -> "receiptAmount".equals(f.field))
                    .findFirst();
            assertTrue(fm.isPresent());
            // 级联第1层 depth=0，penalty = 0.5^1 = 0.5，所以 score < 1.0
            assertTrue(fm.get().score < 0.6, "级联字段 score 应被深度惩罚: " + fm.get().score);
        }
    }

    // ======== US3: 数据源质量增强 ========

    @Nested
    class DataQualityTest {

        @Test
        void resolve_FieldMatch含writeBack标记() {
            // ArContractSubjectMatterItem.receiptAmount 有 writeBackExpr
            ResolveResult result = skillsService.resolve("应收合同子表的收款金额", 5, true);

            assertFalse(result.objectMatches.isEmpty());
            ObjectMatch detail = result.objectMatches.get(0);
            Optional<FieldMatch> fm = detail.fieldMatches.stream()
                    .filter(f -> "receiptAmount".equals(f.field))
                    .findFirst();
            assertTrue(fm.isPresent(), "应匹配到 receiptAmount");
            assertTrue(fm.get().hasWriteBack, "receiptAmount 应标记 hasWriteBack=true");
            assertEquals(ResolveModels.FieldCategory.WRITE_BACK, fm.get().category);
        }

        @Test
        void resolve_停用对象标记isDisabled() {
            // 构造一个停用对象
            Map<String, ObjectTypeMeta> metas = new HashMap<>(analyzerService.getObjectTypeMetas());
            metas.put("DisabledEntity", buildMeta("DisabledEntity", "停用实体", "bill", null, true));
            when(analyzerService.getObjectTypeMetas()).thenReturn(Collections.unmodifiableMap(metas));

            Set<String> allTypes = new TreeSet<>(analyzerService.getAllObjectTypes());
            allTypes.add("DisabledEntity");
            when(analyzerService.getAllObjectTypes()).thenReturn(allTypes);

            Map<String, List<String>> titleIndex = new HashMap<>(analyzerService.getTitleToObjectTypes());
            titleIndex.put("停用实体", Collections.singletonList("DisabledEntity"));
            when(analyzerService.getTitleToObjectTypes()).thenReturn(Collections.unmodifiableMap(titleIndex));

            Map<String, String> titles = new HashMap<>();
            for (Map.Entry<String, ObjectTypeMeta> e : metas.entrySet()) {
                if (e.getValue().getTitle() != null) titles.put(e.getKey(), e.getValue().getTitle());
            }
            when(analyzerService.getObjectTitles()).thenReturn(Collections.unmodifiableMap(titles));

            ResolveResult result = skillsService.resolve("停用实体", 5, false);

            assertFalse(result.objectMatches.isEmpty());
            ObjectMatch match = result.objectMatches.get(0);
            assertEquals("DisabledEntity", match.objectType);
            assertTrue(match.isDisabled, "停用对象应标记 isDisabled=true");
        }

        @Test
        void resolve_停用字段标记isDisabled() {
            // 构造一个停用字段
            BaseappObjectField disabledField = buildField("ArContract", "oldField", "旧字段", "Text",
                    null, null, null);
            disabledField.setIsDisabled(true);

            List<BaseappObjectField> fields = new ArrayList<>(analyzerService.getFieldDetailsForObject("ArContract"));
            fields.add(disabledField);
            when(analyzerService.getFieldDetailsForObject("ArContract")).thenReturn(fields);

            ResolveResult result = skillsService.resolve("ArContract的旧字段", 5, true);

            ObjectMatch top = result.objectMatches.get(0);
            Optional<FieldMatch> fm = top.fieldMatches.stream()
                    .filter(f -> "oldField".equals(f.field))
                    .findFirst();
            assertTrue(fm.isPresent());
            assertTrue(fm.get().isDisabled, "停用字段应标记 isDisabled=true");
        }
    }

    // ======== US4: 分词预处理 ========

    @Nested
    class ParseQueryTest {

        @Test
        void parseQuery_中文分隔符_正确分离对象和字段() {
            ParsedQuery pq = SkillsService.parseQuery("应收合同的金额");
            assertEquals("应收合同", pq.objectPart);
            assertEquals("金额", pq.fieldPart);
            assertFalse(pq.isDetailQuery);
        }

        @Test
        void parseQuery_英文点号分隔_正确分离() {
            ParsedQuery pq = SkillsService.parseQuery("ArContract.amount");
            assertEquals("ArContract", pq.objectPart);
            assertEquals("amount", pq.fieldPart);
        }

        @Test
        void parseQuery_子表关键词_标记isDetailQuery() {
            ParsedQuery pq = SkillsService.parseQuery("应收合同子表的原始金额");
            assertTrue(pq.isDetailQuery);
            assertEquals("子表", pq.detailNavWord);
            assertEquals("应收合同", pq.objectPart);
            assertEquals("原始金额", pq.fieldPart);
        }

        @Test
        void parseQuery_明细关键词_标记isDetailQuery() {
            ParsedQuery pq = SkillsService.parseQuery("应收合同明细");
            assertTrue(pq.isDetailQuery);
            assertEquals("明细", pq.detailNavWord);
            assertEquals("应收合同", pq.objectPart);
            assertNull(pq.fieldPart);
        }

        @Test
        void parseQuery_无分隔符_整体作为objectPart() {
            ParsedQuery pq = SkillsService.parseQuery("应收合同");
            assertEquals("应收合同", pq.objectPart);
            assertNull(pq.fieldPart);
            assertFalse(pq.isDetailQuery);
        }

        @Test
        void parseQuery_纯英文无分隔符_整体作为objectPart() {
            ParsedQuery pq = SkillsService.parseQuery("ArContract");
            assertEquals("ArContract", pq.objectPart);
            assertNull(pq.fieldPart);
        }

        @Test
        void parseQuery_子表关键词在中间_正确分离前后() {
            ParsedQuery pq = SkillsService.parseQuery("收入确认单明细的收款金额");
            assertTrue(pq.isDetailQuery);
            assertEquals("收入确认单", pq.objectPart);
            assertEquals("收款金额", pq.fieldPart);
        }
    }

    // ======== US5: 精细化评分 ========

    @Nested
    class ScoringTest {

        @Test
        void resolve_isMasterField加分_排在非主字段前面() {
            BaseappObjectField masterField = buildField("ArContract", "contractName", "合同名称", "Text",
                    null, null, null);
            masterField.setIsMasterField(true);

            BaseappObjectField normalField = buildField("ArContract", "contractNo", "合同编号", "Text",
                    null, null, null);
            normalField.setIsMasterField(false);

            when(analyzerService.getFieldDetailsForObject("ArContract"))
                    .thenReturn(Arrays.asList(normalField, masterField));

            ResolveResult result = skillsService.resolve("ArContract的合同", 5, true);

            ObjectMatch top = result.objectMatches.get(0);
            assertFalse(top.fieldMatches.isEmpty());

            // 两个字段都含"合同"，但 masterField 应有加分
            Optional<FieldMatch> masterFm = top.fieldMatches.stream()
                    .filter(fm -> "contractName".equals(fm.field))
                    .findFirst();
            Optional<FieldMatch> normalFm = top.fieldMatches.stream()
                    .filter(fm -> "contractNo".equals(fm.field))
                    .findFirst();
            assertTrue(masterFm.isPresent());
            assertTrue(normalFm.isPresent());
            assertTrue(masterFm.get().score >= normalFm.get().score,
                    "主字段分数应 >= 非主字段: master=" + masterFm.get().score + " normal=" + normalFm.get().score);
        }

        @Test
        void resolve_description匹配_低优先级返回结果() {
            BaseappObjectField field = buildField("ArContract", "bizField", "业务字段", "Text",
                    null, null, null);
            field.setDescription("这是一个跟踪合同执行的特殊字段");

            when(analyzerService.getFieldDetailsForObject("ArContract"))
                    .thenReturn(Arrays.asList(field));

            ResolveResult result = skillsService.resolve("ArContract的执行", 5, true);

            ObjectMatch top = result.objectMatches.get(0);
            // description 含"执行"应被匹配
            boolean hasField = top.fieldMatches.stream()
                    .anyMatch(fm -> "bizField".equals(fm.field));
            assertTrue(hasField, "description 匹配应返回结果");

            // 但 score 应较低（description 上限 100/1000 = 0.1）
            Optional<FieldMatch> fm = top.fieldMatches.stream()
                    .filter(f -> "bizField".equals(f.field))
                    .findFirst();
            assertTrue(fm.get().score <= 0.12, "description 匹配 score 应 <= 0.12: " + fm.get().score);
        }
    }

    // ======== 辅助方法 ========

    private ObjectTypeMeta buildMeta(String name, String title, String type, String description, boolean disabled) {
        ObjectTypeMeta meta = new ObjectTypeMeta();
        meta.setName(name);
        meta.setTitle(title);
        meta.setType(type);
        meta.setDescription(description);
        meta.setIsDisabled(disabled);
        return meta;
    }

    private BaseappObjectField buildField(String objectType, String apiName, String title,
                                          String bizType, String writeBackExpr,
                                          String triggerExpr, String expression) {
        BaseappObjectField f = new BaseappObjectField();
        f.setObjectType(objectType);
        f.setApiName(apiName);
        f.setTitle(title);
        f.setBizType(bizType);
        f.setWriteBackExpr(writeBackExpr);
        f.setTriggerExpr(triggerExpr);
        f.setExpression(expression);
        return f;
    }
}
