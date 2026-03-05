package com.deepmodel.relation.service;

import com.deepmodel.relation.model.BaseappObjectField;
import com.deepmodel.relation.model.WriteBackExpr;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * AI 模型辅助 Skills Service。
 *
 * 提供面向 AI 模型改动场景的语义化查询接口，全部基于 ImpactAnalyzerService 的内存索引，
 * 无需额外 DB 查询。
 *
 * 缓存策略：各接口结果以入参为 key 缓存在 Guava Cache 中（最多 500 条）。
 * ImpactAnalyzerService.clearAnalysisCache() 调用 SkillsService.clearCache() 联动清除。
 *
 * 覆盖场景：
 *  1. 理解当前对象配置（objectProfile / threadChain / patternCheck）
 *  2. 规划改动范围（changeScope）
 *  3. 跨对象字段搜索（searchFields）
 */
@Service
public class SkillsService {

    private static final Logger log = LoggerFactory.getLogger(SkillsService.class);

    // ===================== 标准金额/数量字段名集合 =====================

    /** 标准金额字段后缀（字段名 endsWith 其中之一即视为金额类） */
    private static final List<String> AMOUNT_SUFFIXES = Arrays.asList(
            "OriginAmount", "Amount", "AmountWithoutTax", "OriginAmountWithoutTax"
    );

    /** 标准金额字段精确名（camelCase，含前缀变体） */
    private static final Set<String> AMOUNT_EXACT_NAMES = new HashSet<>(Arrays.asList(
            "originAmount", "amount", "amountWithoutTax", "originAmountWithoutTax"
    ));

    /** 标准数量字段精确名（camelCase） */
    private static final Set<String> QTY_EXACT_NAMES = new LinkedHashSet<>(Arrays.asList(
            "quantity", "transAuxQty", "baseQty", "auxQty"
    ));

    /** 标准数量字段后缀 */
    private static final List<String> QTY_SUFFIXES = Arrays.asList(
            "Quantity", "TransAuxQty", "BaseQty", "AuxQty"
    );

    // ===================== 业务类型关键词 =====================
    private static final List<String> AMOUNT_BIZTYPES = Arrays.asList("Amount", "Currency", "Money");
    private static final List<String> QTY_BIZTYPES    = Arrays.asList("Qty", "Quantity");

    private final ImpactAnalyzerService analyzerService;

    // 各接口独立 Cache，key = 接口参数拼接字符串
    private final Cache<String, ObjectProfile>       profileCache  = CacheBuilder.newBuilder().maximumSize(500).recordStats().build();
    private final Cache<String, ThreadChainResult>   threadCache   = CacheBuilder.newBuilder().maximumSize(500).recordStats().build();
    private final Cache<String, PatternCheckResult>  patternCache  = CacheBuilder.newBuilder().maximumSize(500).recordStats().build();
    private final Cache<String, ChangeScopeResult>   scopeCache    = CacheBuilder.newBuilder().maximumSize(500).recordStats().build();
    private final Cache<String, List<FieldSearchResult>> searchCache = CacheBuilder.newBuilder().maximumSize(500).recordStats().build();

    public SkillsService(ImpactAnalyzerService analyzerService) {
        this.analyzerService = analyzerService;
    }

    /** 由 ImpactAnalyzerService.clearAnalysisCache() 联动调用，清除全部 Skills 缓存 */
    public void clearCache() {
        profileCache.invalidateAll();
        threadCache.invalidateAll();
        patternCache.invalidateAll();
        scopeCache.invalidateAll();
        searchCache.invalidateAll();
        log.info("[SkillsService] 已清除所有 Skills 缓存");
    }

    // =========================================================
    // 1. 对象画像
    // =========================================================

    /**
     * 返回对象的完整业务语义画像，将字段按业务类型分组，
     * 并附带对象级别的入站/出站回写关系摘要。
     */
    public ObjectProfile objectProfile(String objectType) {
        try {
            return profileCache.get(objectType, () -> doObjectProfile(objectType));
        } catch (ExecutionException e) {
            log.warn("[SkillsService] objectProfile cache load failed for {}: {}", objectType, e.getMessage());
            return doObjectProfile(objectType);
        }
    }

    private ObjectProfile doObjectProfile(String objectType) {
        List<BaseappObjectField> fields = analyzerService.getFieldDetailsForObject(objectType);

        ObjectProfile profile = new ObjectProfile();
        profile.objectType = objectType;

        // 从 objectDetails 取 title
        analyzerService.getObjectDetails().stream()
                .filter(m -> objectType.equals(m.get("value")))
                .findFirst()
                .ifPresent(m -> profile.title = m.get("title"));

        for (BaseappObjectField f : fields) {
            String fieldName = canonicalName(f);

            if (isWriteBackField(f)) {
                WriteBackExpr wb = analyzerService.parseWriteBack(f.getWriteBackExpr());
                WriteBackFieldInfo info = new WriteBackFieldInfo();
                info.field = fieldName;
                info.title = f.getTitle();
                info.bizType = f.getBizType();
                info.srcObjectType = wb != null ? wb.getSrcObjectType() : null;
                info.expression = wb != null ? wb.getExpression() : null;
                info.idField = wb != null ? wb.getIdField() : null;
                profile.writeBackFields.add(info);
                continue;
            }

            if (isVirtualField(f)) {
                FieldInfo info = new FieldInfo();
                info.field = fieldName;
                info.title = f.getTitle();
                info.bizType = f.getBizType();
                info.expr = f.getVirtualExpr();
                profile.virtualFields.add(info);
                continue;
            }

            if (isTriggerField(f)) {
                FieldInfo info = new FieldInfo();
                info.field = fieldName;
                info.title = f.getTitle();
                info.bizType = f.getBizType();
                info.expr = firstNonEmpty(f.getTriggerExpr(), f.getExpression());
                profile.triggerFields.add(info);
                if (isAmountField(f)) profile.amountFields.add(toBasicInfo(f, info.expr));
                else if (isQtyField(f)) profile.qtyFields.add(toBasicInfo(f, info.expr));
                continue;
            }

            // 基础字段
            if (isAmountField(f)) {
                profile.amountFields.add(toBasicInfo(f, null));
            } else if (isQtyField(f)) {
                profile.qtyFields.add(toBasicInfo(f, null));
            } else {
                FieldInfo info = new FieldInfo();
                info.field = fieldName;
                info.title = f.getTitle();
                info.bizType = f.getBizType();
                profile.baseFields.add(info);
            }
        }

        // 入站回写来源（哪些对象回写到本对象）
        analyzerService.listSourcesForTarget(objectType).forEach(s -> profile.inboundSources.add(s.sourceObject));

        // 出站回写目标（本对象回写到哪些对象）
        analyzerService.listTargetsBySource(objectType).forEach(t -> profile.outboundTargets.add(t.targetObject));

        return profile;
    }

    // =========================================================
    // 2. 线索字段对象链
    // =========================================================

    /**
     * 以线索字段（如 ArContractSubjectMatterItemId）为轴，
     * 找出所有持有该字段的对象，以及它们通过 writeBack 形成的执行链。
     */
    public ThreadChainResult threadChain(String threadField) {
        try {
            return threadCache.get(threadField, () -> doThreadChain(threadField));
        } catch (ExecutionException e) {
            log.warn("[SkillsService] threadChain cache load failed for {}: {}", threadField, e.getMessage());
            return doThreadChain(threadField);
        }
    }

    private ThreadChainResult doThreadChain(String threadField) {
        ThreadChainResult result = new ThreadChainResult();
        result.threadField = threadField;

        // 遍历所有字段，找持有 threadField 的对象
        Set<String> objectsWithThread = new LinkedHashSet<>();
        for (BaseappObjectField f : analyzerService.getAllFields()) {
            String name = canonicalName(f);
            if (name.equalsIgnoreCase(threadField)) {
                objectsWithThread.add(f.getObjectType());
            }
        }

        // 为每个持有 threadField 的对象，补充回写关系
        for (String obj : objectsWithThread) {
            ThreadObjectInfo info = new ThreadObjectInfo();
            info.objectType = obj;
            analyzerService.listTargetsBySource(obj).forEach(t -> info.hasWriteBackTo.add(t.targetObject));
            result.objects.add(info);
        }

        // 构建执行链（srcObj → targetObj，仅涉及 threadField 对象集合的边）
        for (ThreadObjectInfo info : result.objects) {
            for (String target : info.hasWriteBackTo) {
                result.executionChain.add(info.objectType + "→" + target);
            }
        }

        return result;
    }

    // =========================================================
    // 3. 金额/数量完整性检查
    // =========================================================

    /**
     * 检查一个对象当前有哪些标准金额/数量字段，缺少哪些；
     * 并检查回写字段的覆盖情况（哪些金额字段有 writeBack 来源，哪些没有）。
     */
    public PatternCheckResult patternCheck(String objectType) {
        try {
            return patternCache.get(objectType, () -> doPatternCheck(objectType));
        } catch (ExecutionException e) {
            log.warn("[SkillsService] patternCheck cache load failed for {}: {}", objectType, e.getMessage());
            return doPatternCheck(objectType);
        }
    }

    private PatternCheckResult doPatternCheck(String objectType) {
        List<BaseappObjectField> fields = analyzerService.getFieldDetailsForObject(objectType);
        Set<String> fieldNameSet = fields.stream()
                .map(this::canonicalName)
                .collect(Collectors.toSet());

        PatternCheckResult result = new PatternCheckResult();
        result.objectType = objectType;

        // 金额检查：同时检查精确名与后缀名
        Set<String> amountFieldsInObj = fields.stream()
                .filter(this::isAmountField)
                .map(this::canonicalName)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (String std : AMOUNT_EXACT_NAMES) {
            if (fieldNameSet.contains(std) || amountFieldsInObj.stream().anyMatch(n -> n.endsWith(capitalize(std)))) {
                result.amountPattern.present.add(std);
            } else {
                result.amountPattern.missing.add(std);
            }
        }

        // 数量检查
        Set<String> qtyFieldsInObj = fields.stream()
                .filter(this::isQtyField)
                .map(this::canonicalName)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (String std : QTY_EXACT_NAMES) {
            if (fieldNameSet.contains(std) || qtyFieldsInObj.stream().anyMatch(n -> n.endsWith(capitalize(std)))) {
                result.qtyPattern.present.add(std);
            } else {
                result.qtyPattern.missing.add(std);
            }
        }

        // writeBack 覆盖率：找出哪些金额/数量字段有 writeBack 定义
        Set<String> writeBackCovered = fields.stream()
                .filter(this::isWriteBackField)
                .map(this::canonicalName)
                .collect(Collectors.toSet());

        Set<String> allExecFields = new LinkedHashSet<>();
        allExecFields.addAll(amountFieldsInObj);
        allExecFields.addAll(qtyFieldsInObj);

        for (String execField : allExecFields) {
            if (writeBackCovered.contains(execField)) {
                result.writeBackCoverage.present.add(execField);
            } else {
                // 若该字段是 trigger，而非 writeBack，则认为是内部聚合，不视为缺失
                boolean isTrigger = fields.stream()
                        .filter(f -> canonicalName(f).equals(execField))
                        .anyMatch(this::isTriggerField);
                if (!isTrigger) {
                    result.writeBackCoverage.missingReferenceFields.add(execField);
                }
            }
        }

        return result;
    }

    // =========================================================
    // 4. 改动影响范围预估（changeScope）
    // =========================================================

    /**
     * 根据改动意图推导需要调整的对象/字段范围。
     *
     * scenario:
     *  - addExecution:   新增执行对象，通过 writeBack 回写到目标对象
     *  - adjustWriteBack: 调整现有 writeBack 表达式（如新增分支）
     *  - addBranch:      在现有执行链中插入分支对象
     */
    public ChangeScopeResult changeScope(ChangeScopeRequest request) {
        String cacheKey = request.scenario + "|" + request.newSourceObject + "|" + request.targetObject + "|"
                + (request.fields != null ? String.join(",", request.fields) : "");
        try {
            return scopeCache.get(cacheKey, () -> doChangeScope(request));
        } catch (ExecutionException e) {
            log.warn("[SkillsService] changeScope cache load failed for {}: {}", cacheKey, e.getMessage());
            return doChangeScope(request);
        }
    }

    private ChangeScopeResult doChangeScope(ChangeScopeRequest request) {
        ChangeScopeResult result = new ChangeScopeResult();
        String targetObject = request.targetObject;
        List<String> requestedFields = request.fields != null ? request.fields : Collections.emptyList();

        // 找目标对象上已有的同类字段（金额/数量）writeBack 来源，用于参照现有配置
        List<BaseappObjectField> targetFields = analyzerService.getFieldDetailsForObject(targetObject);

        for (String requestedField : requestedFields) {
            // 找目标对象里对应字段的现有定义
            BaseappObjectField targetFieldDef = targetFields.stream()
                    .filter(f -> canonicalName(f).equalsIgnoreCase(requestedField)
                              || (f.getApiName() != null && f.getApiName().equalsIgnoreCase(requestedField)))
                    .findFirst().orElse(null);

            ChangeScopeResult.ObjectChanges targetChanges = result.getOrCreate(targetObject);

            if (targetFieldDef == null) {
                // 字段在目标对象上不存在，需要新增字段
                ChangeScopeResult.FieldChange fc = new ChangeScopeResult.FieldChange();
                fc.field = requestedField;
                fc.action = "ADD_FIELD";
                fc.reason = "目标对象上不存在该字段，需新增";
                targetChanges.changes.add(fc);
            } else if (isWriteBackField(targetFieldDef)) {
                // 已有 writeBack 字段，新增来源
                ChangeScopeResult.FieldChange fc = new ChangeScopeResult.FieldChange();
                fc.field = requestedField;
                fc.action = "ADD_WRITE_BACK_SOURCE";
                fc.reason = request.newSourceObject != null
                        ? "已有 writeBack 来源，需将 " + request.newSourceObject + " 纳入聚合表达式"
                        : "已有 writeBack 来源，需扩展聚合表达式";
                targetChanges.changes.add(fc);
            } else {
                // 非 writeBack 字段，新增 writeBack 关联
                ChangeScopeResult.FieldChange fc = new ChangeScopeResult.FieldChange();
                fc.field = requestedField;
                fc.action = "ADD_WRITE_BACK_DEFINITION";
                fc.reason = "字段当前没有 writeBack 定义，需新增 writeBackExpr 指向 " +
                        (request.newSourceObject != null ? request.newSourceObject : "新来源对象");
                targetChanges.changes.add(fc);
            }

            // 检查目标对象内，依赖该字段的 trigger 字段（需要 check 是否仍然正确）
            List<BaseappObjectField> triggerDeps = analyzerService.getTriggerFieldsForTarget(targetObject, requestedField);
            for (BaseappObjectField td : triggerDeps) {
                ChangeScopeResult.FieldChange fc = new ChangeScopeResult.FieldChange();
                fc.field = canonicalName(td);
                fc.action = "CHECK_TRIGGER";
                fc.reason = "依赖 " + requestedField + " 的 trigger 字段，writeBack 变更后需确认聚合结果仍正确";
                targetChanges.changes.add(fc);
            }
        }

        // 向下游追踪：目标对象的 outbound writeBack 目标也可能受影响
        analyzerService.listTargetsBySource(targetObject).forEach(target -> {
            // 只关注与请求字段相关的回写字段
            List<BaseappObjectField> downstreamAffected =
                    analyzerService.getFieldsImpactedBySourceObject(target.targetObject, targetObject);
            if (!downstreamAffected.isEmpty()) {
                ChangeScopeResult.ObjectChanges downChanges = result.getOrCreate(target.targetObject);
                for (BaseappObjectField df : downstreamAffected) {
                    // 只关注与目标字段同类型的字段
                    boolean related = requestedFields.isEmpty() ||
                            requestedFields.stream().anyMatch(rf ->
                                    canonicalName(df).toLowerCase().contains(rf.toLowerCase().replace("amount", "").replace("qty", ""))
                            );
                    if (related) {
                        ChangeScopeResult.FieldChange fc = new ChangeScopeResult.FieldChange();
                        fc.field = canonicalName(df);
                        fc.action = "CHECK_TRIGGER";
                        fc.reason = "上游 " + targetObject + " 的 writeBack 变更可能影响此字段";
                        downChanges.changes.add(fc);
                    }
                }
            }
        });

        result.upgradeScriptNeeded = !result.affectedObjects.isEmpty();
        return result;
    }

    // =========================================================
    // 5. 字段模糊搜索
    // =========================================================

    /**
     * 跨所有对象或在指定对象内，按字段名/标题/bizType 模糊搜索字段。
     *
     * @param namePattern  字段名或标题中包含的关键字（大小写不敏感），null 表示不限
     * @param objectType   限定对象，null 表示搜索所有对象
     * @param bizType      精确匹配 bizType，null 表示不限
     * @param limit        最多返回条数，默认 200
     */
    public List<FieldSearchResult> searchFields(String namePattern, String objectType, String bizType, int limit) {
        String cacheKey = (namePattern != null ? namePattern : "") + "|"
                + (objectType != null ? objectType : "") + "|"
                + (bizType != null ? bizType : "") + "|" + limit;
        try {
            return searchCache.get(cacheKey, () -> doSearchFields(namePattern, objectType, bizType, limit));
        } catch (ExecutionException e) {
            log.warn("[SkillsService] searchFields cache load failed for {}: {}", cacheKey, e.getMessage());
            return doSearchFields(namePattern, objectType, bizType, limit);
        }
    }

    private List<FieldSearchResult> doSearchFields(String namePattern, String objectType, String bizType, int limit) {
        List<BaseappObjectField> source = objectType != null
                ? analyzerService.getFieldDetailsForObject(objectType)
                : analyzerService.getAllFields();

        String patternLower = namePattern != null ? namePattern.toLowerCase() : null;
        String bizTypeLower = bizType != null ? bizType.toLowerCase() : null;

        List<FieldSearchResult> results = new ArrayList<>();
        for (BaseappObjectField f : source) {
            if (results.size() >= limit) break;

            // bizType 过滤
            if (bizTypeLower != null) {
                String bt = f.getBizType();
                if (bt == null || !bt.toLowerCase().contains(bizTypeLower)) continue;
            }

            // namePattern 过滤（字段名 / apiName / title）
            if (patternLower != null) {
                String fname = canonicalName(f).toLowerCase();
                String ftitle = f.getTitle() != null ? f.getTitle().toLowerCase() : "";
                if (!fname.contains(patternLower) && !ftitle.contains(patternLower)) continue;
            }

            FieldSearchResult r = new FieldSearchResult();
            r.objectType = f.getObjectType();
            r.field = canonicalName(f);
            r.title = f.getTitle();
            r.type = f.getType();
            r.bizType = f.getBizType();
            r.hasWriteBack = isWriteBackField(f);
            r.hasTrigger = isTriggerField(f);
            r.isVirtual = isVirtualField(f);
            results.add(r);
        }
        return results;
    }

    // =========================================================
    // 内部工具方法
    // =========================================================

    private String canonicalName(BaseappObjectField f) {
        if (f.getApiName() != null && !f.getApiName().trim().isEmpty()) return f.getApiName().trim();
        if (f.getName() != null && !f.getName().trim().isEmpty()) {
            String n = f.getName().trim();
            return n.contains("_") ? snakeToCamel(n) : n;
        }
        return "";
    }

    private boolean isWriteBackField(BaseappObjectField f) {
        return f.getWriteBackExpr() != null && !f.getWriteBackExpr().trim().isEmpty();
    }

    private boolean isVirtualField(BaseappObjectField f) {
        String te = f.getTriggerExpr();
        String ex = f.getExpression();
        String ve = f.getVirtualExpr();
        boolean hasTriggerOrExpr = (te != null && !te.trim().isEmpty()) || (ex != null && !ex.trim().isEmpty());
        return !hasTriggerOrExpr && ve != null && !ve.trim().isEmpty();
    }

    private boolean isTriggerField(BaseappObjectField f) {
        String te = f.getTriggerExpr();
        String ex = f.getExpression();
        return (te != null && !te.trim().isEmpty()) || (ex != null && !ex.trim().isEmpty());
    }

    private boolean isAmountField(BaseappObjectField f) {
        String bt = f.getBizType();
        if (bt != null) {
            for (String ab : AMOUNT_BIZTYPES) {
                if (bt.toLowerCase().contains(ab.toLowerCase())) return true;
            }
        }
        String name = canonicalName(f);
        for (String suf : AMOUNT_SUFFIXES) {
            if (name.endsWith(suf)) return true;
        }
        return AMOUNT_EXACT_NAMES.contains(name);
    }

    private boolean isQtyField(BaseappObjectField f) {
        String bt = f.getBizType();
        if (bt != null) {
            for (String qb : QTY_BIZTYPES) {
                if (bt.toLowerCase().contains(qb.toLowerCase())) return true;
            }
        }
        String name = canonicalName(f);
        for (String suf : QTY_SUFFIXES) {
            if (name.endsWith(suf)) return true;
        }
        return QTY_EXACT_NAMES.contains(name);
    }

    private FieldInfo toBasicInfo(BaseappObjectField f, String expr) {
        FieldInfo info = new FieldInfo();
        info.field = canonicalName(f);
        info.title = f.getTitle();
        info.bizType = f.getBizType();
        info.expr = expr;
        return info;
    }

    private String firstNonEmpty(String... arr) {
        for (String s : arr) {
            if (s != null && !s.trim().isEmpty()) return s.trim();
        }
        return null;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String snakeToCamel(String snake) {
        if (snake == null) return null;
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') { nextUpper = true; continue; }
            sb.append(nextUpper ? Character.toUpperCase(c) : c);
            nextUpper = false;
        }
        return sb.toString();
    }

    // =========================================================
    // 返回值模型
    // =========================================================

    public static class ObjectProfile {
        public String objectType;
        public String title;
        public List<FieldInfo> amountFields       = new ArrayList<>();
        public List<FieldInfo> qtyFields           = new ArrayList<>();
        public List<WriteBackFieldInfo> writeBackFields = new ArrayList<>();
        public List<FieldInfo> triggerFields       = new ArrayList<>();
        public List<FieldInfo> virtualFields       = new ArrayList<>();
        public List<FieldInfo> baseFields          = new ArrayList<>();
        public List<String>    inboundSources      = new ArrayList<>();
        public List<String>    outboundTargets     = new ArrayList<>();
    }

    public static class FieldInfo {
        public String field;
        public String title;
        public String bizType;
        public String expr;
    }

    public static class WriteBackFieldInfo {
        public String field;
        public String title;
        public String bizType;
        public String srcObjectType;
        public String expression;
        public String idField;
    }

    public static class ThreadChainResult {
        public String threadField;
        public List<ThreadObjectInfo> objects     = new ArrayList<>();
        public List<String>           executionChain = new ArrayList<>();
    }

    public static class ThreadObjectInfo {
        public String objectType;
        public List<String> hasWriteBackTo = new ArrayList<>();
    }

    public static class PatternCheckResult {
        public String objectType;
        public PatternGroup amountPattern       = new PatternGroup();
        public PatternGroup qtyPattern          = new PatternGroup();
        public WriteBackCoverage writeBackCoverage = new WriteBackCoverage();
    }

    public static class PatternGroup {
        public List<String> present = new ArrayList<>();
        public List<String> missing = new ArrayList<>();
    }

    public static class WriteBackCoverage {
        public List<String> present               = new ArrayList<>();
        public List<String> missingReferenceFields = new ArrayList<>();
    }

    public static class ChangeScopeRequest {
        public String scenario;          // addExecution | adjustWriteBack | addBranch
        public String newSourceObject;   // 新增来源对象（addExecution 场景）
        public String targetObject;      // 目标对象
        public List<String> fields;      // 要新增/调整 writeBack 的字段
    }

    public static class ChangeScopeResult {
        public List<ObjectChanges> affectedObjects = new ArrayList<>();
        public boolean upgradeScriptNeeded;

        public ObjectChanges getOrCreate(String objectType) {
            for (ObjectChanges oc : affectedObjects) {
                if (oc.objectType.equals(objectType)) return oc;
            }
            ObjectChanges oc = new ObjectChanges();
            oc.objectType = objectType;
            affectedObjects.add(oc);
            return oc;
        }

        public static class ObjectChanges {
            public String objectType;
            public List<FieldChange> changes = new ArrayList<>();
        }

        public static class FieldChange {
            public String field;
            public String action;   // ADD_FIELD | ADD_WRITE_BACK_SOURCE | ADD_WRITE_BACK_DEFINITION | CHECK_TRIGGER
            public String reason;
        }
    }

    public static class FieldSearchResult {
        public String objectType;
        public String field;
        public String title;
        public String type;
        public String bizType;
        public boolean hasWriteBack;
        public boolean hasTrigger;
        public boolean isVirtual;
    }
}
