package com.deepmodel.relation.service;

import com.deepmodel.relation.model.*;
import com.deepmodel.relation.model.ResolveModels.*;
import com.deepmodel.relation.util.JiebaUtils;
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

    // ===================== 对象特性关键词映射 (US1) =====================
    private static final Map<String, String> TRAIT_KEYWORDS = new LinkedHashMap<>();
    static {
        TRAIT_KEYWORDS.put("树型", "isTree"); TRAIT_KEYWORDS.put("树状", "isTree"); TRAIT_KEYWORDS.put("树形", "isTree");
        TRAIT_KEYWORDS.put("子表", "isDetail"); TRAIT_KEYWORDS.put("明细", "isDetail");
        TRAIT_KEYWORDS.put("变更单", "isSupportChangeLog");
        TRAIT_KEYWORDS.put("自定义", "isCustomizedEntity");
        TRAIT_KEYWORDS.put("多版本", "isMultiDataVersion");
    }

    // ===================== bizType 中文关键词映射 (US2) =====================
    private static final Map<String, String> BIZTYPE_KEYWORDS = new LinkedHashMap<>();
    static {
        BIZTYPE_KEYWORDS.put("金额", "amount"); BIZTYPE_KEYWORDS.put("数量", "quantity");
        BIZTYPE_KEYWORDS.put("价格", "price"); BIZTYPE_KEYWORDS.put("比率", "ratio");
        BIZTYPE_KEYWORDS.put("百分比", "percent"); BIZTYPE_KEYWORDS.put("邮箱", "email");
        BIZTYPE_KEYWORDS.put("手机", "mobile"); BIZTYPE_KEYWORDS.put("电话", "phone");
    }

    private final ImpactAnalyzerService analyzerService;

    // 各接口独立 Cache，key = 接口参数拼接字符串
    private final Cache<String, ObjectProfile>       profileCache  = CacheBuilder.newBuilder().maximumSize(500).recordStats().build();
    private final Cache<String, ThreadChainResult>   threadCache   = CacheBuilder.newBuilder().maximumSize(500).recordStats().build();
    private final Cache<String, PatternCheckResult>  patternCache  = CacheBuilder.newBuilder().maximumSize(500).recordStats().build();
    private final Cache<String, ChangeScopeResult>   scopeCache    = CacheBuilder.newBuilder().maximumSize(500).recordStats().build();
    private final Cache<String, List<FieldSearchResult>> searchCache = CacheBuilder.newBuilder().maximumSize(500).recordStats().build();
    private final Cache<String, ResolveResult> resolveCache = CacheBuilder.newBuilder().maximumSize(500).recordStats().build();

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
        resolveCache.invalidateAll();
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
    // 6. 自然语言元数据匹配（resolve）
    // =========================================================

    /** 子表语义关键词 */
    private static final List<String> DETAIL_KEYWORDS = Arrays.asList("子表", "明细", "行项目", "明细表", "子项");

    /** 对象+字段分隔符关键词 */
    private static final List<String> FIELD_SEPARATORS = Arrays.asList("的", ".", "。");

    // ===================== 反向引用查询关键词 (US5) =====================
    private static final List<String> REVERSE_REF_PREFIXES = Arrays.asList(
            "哪些对象引用了", "引用了", "被哪些对象使用", "谁引用了", "谁使用了",
            "被谁引用", "被谁使用", "关联了"
    );

    /** 公共子表后缀黑名单：每个单据都有的通用子表（评论/附件/日志等），搜索时排除 */
    private static final List<String> PUBLIC_DETAIL_SUFFIXES = Arrays.asList(
            "Comment", "CommentReceiver", "Attachment", "AttachmentDetail",
            "OperateLog", "OperationLog", "ChangeLog",
            "Follow", "Tag", "TagRelation",
            "Remark", "BpmRecord", "ProcessInstance", "ApprovalRecord",
            "FieldSyncExtend", "FieldSync",
            "BillParticipant", "FreeProcNode"
    );

    /**
     * 用 Jieba 分词 + 规则解析查询字符串，识别对象部分、子表关键词、字段部分。
     * 优先使用 Jieba 分词结果识别业务术语边界，回退到分隔符切割。
     */
    static ParsedQuery parseQuery(String query) {
        ParsedQuery pq = new ParsedQuery();
        pq.objectPart = query;

        // 1. 检查是否包含子表关键词
        for (String kw : DETAIL_KEYWORDS) {
            int kwIdx = query.indexOf(kw);
            if (kwIdx >= 0) {
                String beforeKw = query.substring(0, kwIdx).trim();
                String afterKw = query.substring(kwIdx + kw.length()).trim();
                // 去掉分隔符
                for (String sep : FIELD_SEPARATORS) {
                    if (beforeKw.endsWith(sep))
                        beforeKw = beforeKw.substring(0, beforeKw.length() - sep.length()).trim();
                    if (afterKw.startsWith(sep))
                        afterKw = afterKw.substring(sep.length()).trim();
                }
                // 仅当有明确的主表部分时才视为子表查询（如"应收合同子表"）
                // 纯"子表"输入不走子表展开，由 detectTraitFilter 处理为特性筛选
                if (!beforeKw.isEmpty()) {
                    pq.isDetailQuery = true;
                    pq.detailNavWord = kw;
                    pq.objectPart = beforeKw;
                    pq.fieldPart = afterKw.isEmpty() ? null : afterKw;
                    return pq;
                }
            }
        }

        // 2. 尝试 Jieba 分词识别对象和字段边界
        try {
            List<String> tokens = JiebaUtils.getSegmenter().sentenceProcess(query);
            // 从分词结果中识别分隔符位置
            if (tokens.size() >= 2) {
                int sepIdx = -1;
                for (int i = 0; i < tokens.size(); i++) {
                    String tok = tokens.get(i).trim();
                    if (FIELD_SEPARATORS.contains(tok)) {
                        sepIdx = i;
                        break;
                    }
                }
                if (sepIdx > 0 && sepIdx < tokens.size() - 1) {
                    StringBuilder objSb = new StringBuilder();
                    for (int i = 0; i < sepIdx; i++) objSb.append(tokens.get(i));
                    StringBuilder fldSb = new StringBuilder();
                    for (int i = sepIdx + 1; i < tokens.size(); i++) fldSb.append(tokens.get(i));
                    pq.objectPart = objSb.toString().trim();
                    pq.fieldPart = fldSb.toString().trim();
                    if (pq.fieldPart.isEmpty()) pq.fieldPart = null;
                    return pq;
                }
            }
        } catch (Exception e) {
            // Jieba 分词失败，回退到分隔符切割
        }

        // 3. 回退：按分隔符硬切
        for (String sep : FIELD_SEPARATORS) {
            int idx = query.indexOf(sep);
            if (idx > 0 && idx < query.length() - sep.length()) {
                pq.objectPart = query.substring(0, idx).trim();
                pq.fieldPart = query.substring(idx + sep.length()).trim();
                if (pq.fieldPart.isEmpty()) pq.fieldPart = null;
                return pq;
            }
        }

        return pq;
    }

    /**
     * 自然语言元数据匹配：接收用户输入文本，返回分层匹配结果。
     *
     * @param query         用户输入的自然语言文本
     * @param maxResults    最多返回对象匹配数，默认 5
     * @param includeFields 是否同时匹配字段，默认 true
     */
    public ResolveResult resolve(String query, int maxResults, boolean includeFields) {
        String cacheKey = query + "|" + maxResults + "|" + includeFields;
        try {
            return resolveCache.get(cacheKey, () -> doResolve(query, maxResults, includeFields));
        } catch (ExecutionException e) {
            log.warn("[SkillsService] resolve cache load failed for {}: {}", query, e.getMessage());
            return doResolve(query, maxResults, includeFields);
        }
    }

    private ResolveResult doResolve(String query, int maxResults, boolean includeFields) {
        ResolveResult result = new ResolveResult();
        result.query = query;

        if (query == null || query.trim().isEmpty()) {
            return result;
        }
        query = query.trim();

        // 分词解析查询
        ParsedQuery pq = parseQuery(query);

        // 当 parseQuery 未拆出 fieldPart 时，用已知对象标题做前缀拆分
        // 例如 "应收合同收款状态" → objectPart="应收合同", fieldPart="收款状态"
        if (pq.fieldPart == null && !pq.isDetailQuery && pq.objectPart != null && pq.objectPart.length() > 1) {
            splitByKnownTitle(pq);
        }

        boolean isDetailQuery = pq.isDetailQuery;
        String objectPart = pq.objectPart;
        String fieldPart = pq.fieldPart;

        // 特性关键词检测（US1）
        detectTraitFilter(pq, query);
        // bizType 关键词检测（US2）
        detectBizTypeFilter(pq);
        // 反向引用查询意图检测（US5）
        detectReverseRefQuery(pq, query);

        // 反向引用查询：直接走 EntityReferenceService
        if (pq.reverseRefTarget != null) {
            result.objectMatches = matchReverseReferences(pq.reverseRefTarget);
            result.objectMatches.sort((a, b) -> Double.compare(b.score, a.score));
            return result;
        }

        // 枚举搜索（US3）
        result.enumMatches = matchEnums(query);

        // 对象匹配（如果有特性过滤或 bizType 过滤，不限制 bill only）
        boolean filterBill = !isDetailQuery && pq.traitFilter == null && pq.bizTypeFilter == null;
        List<ObjectMatch> objectMatches = matchObjects(objectPart, maxResults, filterBill);

        // 纯 bizType 搜索（无对象名）：从全量对象中采样有该 bizType 字段的对象
        if (objectMatches.isEmpty() && pq.bizTypeFilter != null) {
            objectMatches = sampleObjectsByBizType(pq.bizTypeFilter, maxResults);
        }

        // 特性过滤（US1）
        if (pq.traitFilter != null) {
            objectMatches = filterByTrait(objectMatches, pq.traitFilter);
        }

        // 子表查询：展开为子表对象，并在子表范围内匹配字段
        if (isDetailQuery) {
            List<ObjectMatch> detailMatches = new ArrayList<>();
            for (ObjectMatch om : objectMatches) {
                Set<String> details = analyzerService.getAllDetailEntities(om.objectType);
                // 兜底：当 detail relations 未加载时，用命名约定推导子表（XXXItem）
                if (details.isEmpty()) {
                    details = inferDetailsByNaming(om.objectType);
                }
                // 如果 fieldPart 中包含子表名称片段（导航词），优先匹配该子表
                String targetDetail = findDetailByNavWord(details, fieldPart);

                for (String detail : details) {
                    // 跳过公共子表（评论/附件/日志等）
                    if (isPublicDetail(detail)) continue;
                    ObjectMatch dm = new ObjectMatch();
                    dm.objectType = detail;
                    dm.title = analyzerService.getObjectTitles().getOrDefault(detail, detail);
                    // 如果该子表是导航词命中的，给更高分
                    dm.score = detail.equals(targetDetail) ? om.score * 0.98 : om.score * 0.95;
                    dm.matchSource = om.matchSource;
                    dm.parentEntity = om.objectType;
                    fillObjectContext(dm);

                    // 在子表范围内匹配字段（三路搜索）
                    if (includeFields && fieldPart != null && !fieldPart.isEmpty()) {
                        String cleanFieldPart = removeDetailNavFromField(fieldPart, detail);
                        if (cleanFieldPart != null && !cleanFieldPart.isEmpty()) {
                            List<FieldMatch> directMatches = matchFields(detail, cleanFieldPart, pq.bizTypeFilter);
                            for (FieldMatch fm : directMatches) fm.matchType = "DIRECT";

                            List<FieldMatch> chainResults = resolveFieldChain(detail, cleanFieldPart);
                            for (FieldMatch fm : chainResults) fm.matchType = "CHAIN";

                            List<FieldMatch> cascadeResults = cascadeFieldSearch(detail, cleanFieldPart, 0, new HashSet<>());
                            for (FieldMatch fm : cascadeResults) fm.matchType = "CASCADE";

                            dm.fieldMatches = mergeAndDedup(5, directMatches, chainResults, cascadeResults);
                            enrichFieldSummaries(dm.objectType, dm.fieldMatches);
                        }
                    }
                    detailMatches.add(dm);
                }
            }
            sortByScoreWithFieldTiebreak(detailMatches);
            result.objectMatches = deduplicateAndFilterPublic(detailMatches);
            return result;
        }

        // 非子表查询：填充上下文并匹配字段
        for (ObjectMatch om : objectMatches) {
            fillObjectContext(om);
            if (includeFields) {
                if (pq.customizedFieldFilter) {
                    // "XX的自定义字段" → 列出所有 isCustomizedField=true 的字段
                    List<FieldMatch> customFields = listCustomizedFields(om.objectType);
                    if (fieldPart != null && !fieldPart.isEmpty()) {
                        // "XX的自定义XX字段" → 在自定义字段中再按名称匹配
                        List<FieldMatch> filtered = new ArrayList<>();
                        for (FieldMatch fm : customFields) {
                            if (fm.title != null && fm.title.contains(fieldPart)) {
                                filtered.add(fm);
                            } else if (fm.field != null && fm.field.toLowerCase().contains(fieldPart.toLowerCase())) {
                                filtered.add(fm);
                            }
                        }
                        om.fieldMatches = filtered.isEmpty() ? customFields : filtered;
                    } else {
                        om.fieldMatches = customFields;
                    }
                } else if (fieldPart != null && !fieldPart.isEmpty()) {
                    List<FieldMatch> directMatches = matchFields(om.objectType, fieldPart, pq.bizTypeFilter);
                    for (FieldMatch fm : directMatches) fm.matchType = "DIRECT";

                    List<FieldMatch> chainResults = resolveFieldChain(om.objectType, fieldPart);
                    for (FieldMatch fm : chainResults) fm.matchType = "CHAIN";

                    List<FieldMatch> cascadeResults = cascadeFieldSearch(om.objectType, fieldPart, 0, new HashSet<>());
                    for (FieldMatch fm : cascadeResults) fm.matchType = "CASCADE";

                    om.fieldMatches = mergeAndDedup(5, directMatches, chainResults, cascadeResults);
                } else {
                    om.fieldMatches = matchFieldsWithFallback(om.objectType, query);
                }
                // 填充依赖/回写/枚举摘要（US3/US4）
                enrichFieldSummaries(om.objectType, om.fieldMatches);
            }
        }

        // 按匹配度降序排序（score 相同时用最佳字段匹配分 tiebreak）
        sortByScoreWithFieldTiebreak(objectMatches);
        // 过滤公共子表 + objectType 去重
        result.objectMatches = deduplicateAndFilterPublic(objectMatches);
        return result;
    }

    /** 过滤公共子表 + objectType 去重（已排序，保留首次出现即最高分的） */
    private List<ObjectMatch> deduplicateAndFilterPublic(List<ObjectMatch> sorted) {
        Set<String> seen = new LinkedHashSet<>();
        List<ObjectMatch> result = new ArrayList<>();
        for (ObjectMatch om : sorted) {
            if (isPublicDetail(om.objectType)) continue;
            if (seen.add(om.objectType)) {
                result.add(om);
            }
        }
        return result;
    }

    /** 按有效排序分降序排序（对象 score + 字段匹配直接度加成） */
    private void sortByScoreWithFieldTiebreak(List<ObjectMatch> matches) {
        matches.sort((a, b) -> Double.compare(effectiveSortScore(b), effectiveSortScore(a)));
    }

    /**
     * 计算用于排序的有效分：对象 score + 匹配直接度加成。
     * DIRECT 匹配加成 0.1，CHAIN 加成 0.05，CASCADE 无加成。
     * 确保字段匹配层级越直接的对象排名越靠前。
     */
    private double effectiveSortScore(ObjectMatch om) {
        if (om.fieldMatches == null || om.fieldMatches.isEmpty()) return om.score;
        int bestLevel = 3; // 0=DIRECT, 1=CHAIN, 2=CASCADE
        double bestFieldScore = 0;
        for (FieldMatch fm : om.fieldMatches) {
            int level = "DIRECT".equals(fm.matchType) ? 0
                      : "CHAIN".equals(fm.matchType) ? 1 : 2;
            if (level < bestLevel || (level == bestLevel && fm.score > bestFieldScore)) {
                bestLevel = level;
                bestFieldScore = fm.score;
            }
        }
        double bonus = bestLevel == 0 ? 0.1 : bestLevel == 1 ? 0.05 : 0;
        return om.score + bonus;
    }

    /**
     * 在子表集合中查找与 fieldPart 中子表名称片段匹配的子表。
     * 例如 fieldPart="标的的收款金额"，子表 title 含"标的" → 返回该子表。
     */
    private String findDetailByNavWord(Set<String> details, String fieldPart) {
        if (fieldPart == null || details == null) return null;
        Map<String, ObjectTypeMeta> metas = analyzerService.getObjectTypeMetas();
        for (String detail : details) {
            ObjectTypeMeta meta = metas.get(detail);
            if (meta == null || meta.getTitle() == null) continue;
            // 子表标题中的关键词出现在 fieldPart 中
            String detailTitle = meta.getTitle();
            if (fieldPart.contains(detailTitle) || detailTitle.contains(fieldPart)) {
                return detail;
            }
            // 子表标题的简短部分（去掉主表名前缀）
            for (Map.Entry<String, ObjectTypeMeta> e : metas.entrySet()) {
                String mainTitle = e.getValue().getTitle();
                if (mainTitle != null && detailTitle.startsWith(mainTitle)) {
                    String suffix = detailTitle.substring(mainTitle.length());
                    if (!suffix.isEmpty() && fieldPart.contains(suffix)) {
                        return detail;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 命名约定推导子表：在所有已知对象中查找以主表名开头+Item 结尾的对象。
     * 如 RevenueConfirmation → RevenueConfirmationItem
     */
    private Set<String> inferDetailsByNaming(String mainObjectType) {
        Set<String> inferred = new LinkedHashSet<>();
        Map<String, ObjectTypeMeta> metas = analyzerService.getObjectTypeMetas();
        for (String name : metas.keySet()) {
            if (name.startsWith(mainObjectType) && name.length() > mainObjectType.length()
                    && !name.equals(mainObjectType) && !name.endsWith("View")) {
                inferred.add(name);
            }
        }
        return inferred;
    }

    /** 判断是否为公共子表（评论/附件/操作日志等每个单据都有的通用子表） */
    private static boolean isPublicDetail(String objectType) {
        for (String name : PUBLIC_DETAIL_SUFFIXES) {
            if (objectType.equals(name) || objectType.endsWith(name)) return true;
        }
        return false;
    }

    /**
     * 从 fieldPart 中去掉子表导航词部分，保留纯字段查询。
     * 例如 fieldPart="标的的收款金额"，子表标题含"标的" → 返回 "收款金额"。
     */
    private String removeDetailNavFromField(String fieldPart, String detailType) {
        if (fieldPart == null) return null;
        ObjectTypeMeta meta = analyzerService.getObjectTypeMetas().get(detailType);
        if (meta == null || meta.getTitle() == null) return fieldPart;
        String title = meta.getTitle();
        // 尝试去掉子表标题
        String result = fieldPart.replace(title, "").trim();
        // 去掉分隔符
        for (String sep : FIELD_SEPARATORS) {
            if (result.startsWith(sep)) result = result.substring(sep.length()).trim();
            if (result.endsWith(sep)) result = result.substring(0, result.length() - sep.length()).trim();
        }
        return result.isEmpty() ? fieldPart : result;
    }

    /**
     * 合并多路字段匹配结果并去重（按 field+refPath 去重，保留最高分），限制返回数量
     */
    @SafeVarargs
    private final List<FieldMatch> mergeAndDedup(int maxResults, List<FieldMatch>... sources) {
        // key = field + "|" + refPath
        Map<String, FieldMatch> best = new LinkedHashMap<>();
        for (List<FieldMatch> source : sources) {
            for (FieldMatch fm : source) {
                String key = fm.field + "|" + (fm.refPath != null ? fm.refPath : "");
                FieldMatch existing = best.get(key);
                if (existing == null || fm.score > existing.score) {
                    best.put(key, fm);
                }
            }
        }
        List<FieldMatch> result = new ArrayList<>(best.values());
        result.sort((a, b) -> Double.compare(b.score, a.score));
        if (result.size() > maxResults) {
            result = new ArrayList<>(result.subList(0, maxResults));
        }
        return result;
    }

    /**
     * 级联字段搜索：沿 mainToDetails 和 referInfo.referEntityName 递归搜索字段。
     * 深度上限 2 层，每层 score × 0.5，共享 visited 防环。
     */
    private List<FieldMatch> cascadeFieldSearch(String objectType, String fieldQuery,
                                                int depth, Set<String> visited) {
        if (depth >= 2 || visited.contains(objectType)) return Collections.emptyList();
        visited.add(objectType);
        List<FieldMatch> results = new ArrayList<>();
        double depthPenalty = Math.pow(0.5, depth + 1);

        // 搜索子表
        Set<String> details = analyzerService.getMainToDetails().get(objectType);
        if (details != null) {
            for (String detail : details) {
                if (visited.contains(detail)) continue;
                String detailTitle = analyzerService.getObjectTitles().getOrDefault(detail, detail);
                List<FieldMatch> detailMatches = matchFields(detail, fieldQuery);
                for (FieldMatch fm : detailMatches) {
                    fm.score *= depthPenalty;
                    fm.refPath = "子表 → " + detailTitle + "(" + detail + ")";
                    results.add(fm);
                }
                results.addAll(cascadeFieldSearch(detail, fieldQuery, depth + 1, visited));
            }
        }

        // 搜索引用对象（referInfo.referEntityName）
        List<BaseappObjectField> fields = analyzerService.getFieldDetailsForObject(objectType);
        for (BaseappObjectField f : fields) {
            String refObj = f.getRefObjectType();
            if (refObj == null || refObj.isEmpty() || visited.contains(refObj)) continue;
            String fieldTitle = f.getTitle() != null ? f.getTitle() : canonicalName(f);
            List<FieldMatch> refMatches = matchFields(refObj, fieldQuery);
            for (FieldMatch fm : refMatches) {
                fm.score *= depthPenalty;
                fm.refPath = fieldTitle + "(" + canonicalName(f) + ") → " + refObj;
                results.add(fm);
            }
            results.addAll(cascadeFieldSearch(refObj, fieldQuery, depth + 1, visited));
        }

        results.sort((a, b) -> Double.compare(b.score, a.score));
        return results;
    }

    /**
     * 链式引用字段解析：
     * 1. 显式：fieldPart 含"的"时（如"项目的分类"），按"的"拆分逐级跳转
     * 2. 隐式：fieldPart 不含"的"时（如"项目分类"），用引用字段 title 做前缀匹配拆分
     */
    private List<FieldMatch> resolveFieldChain(String objectType, String fieldPart) {
        if (fieldPart == null || fieldPart.isEmpty()) return Collections.emptyList();

        // 显式链式（含"的"）
        if (fieldPart.contains("的")) {
            return resolveExplicitChain(objectType, fieldPart);
        }

        // 隐式链式：用引用字段 title 做前缀匹配拆分
        return resolveImplicitChain(objectType, fieldPart);
    }

    /** 显式链式：按"的"拆分逐级跳转 */
    private List<FieldMatch> resolveExplicitChain(String objectType, String fieldPart) {
        String[] segments = fieldPart.split("的");
        if (segments.length < 2) return Collections.emptyList();

        String currentObject = objectType;
        double chainScore = 1.0;
        StringBuilder pathBuilder = new StringBuilder();

        for (int i = 0; i < segments.length - 1; i++) {
            String seg = segments[i].trim();
            if (seg.isEmpty()) continue;

            BaseappObjectField refField = findReferField(currentObject, seg);
            if (refField == null) return Collections.emptyList();

            String refObj = refField.getRefObjectType();
            if (refObj == null || refObj.isEmpty()) return Collections.emptyList();

            String title = refField.getTitle();
            int score = Math.max(
                    calculateMatchScore(seg, canonicalName(refField)),
                    title != null ? calculateMatchScore(seg, title) : 0
            );
            chainScore *= (score / 1000.0);

            if (pathBuilder.length() > 0) pathBuilder.append(" → ");
            String fieldLabel = title != null ? title + "(" + canonicalName(refField) + ")" : canonicalName(refField);
            pathBuilder.append(fieldLabel).append(" → ").append(refObj);
            currentObject = refObj;
        }

        String lastSeg = segments[segments.length - 1].trim();
        if (lastSeg.isEmpty()) return Collections.emptyList();

        String refPath = pathBuilder.toString();
        List<FieldMatch> results = matchFields(currentObject, lastSeg);
        for (FieldMatch fm : results) {
            fm.score *= chainScore;
            fm.refPath = refPath;
        }
        return results;
    }

    /** 隐式链式：遍历引用字段，用 title 做前缀拆分（如"项目分类" → "项目" + "分类"） */
    private List<FieldMatch> resolveImplicitChain(String objectType, String fieldPart) {
        List<BaseappObjectField> fields = analyzerService.getFieldDetailsForObject(objectType);
        List<FieldMatch> bestResults = Collections.emptyList();
        double bestScore = 0;

        for (BaseappObjectField f : fields) {
            String refObj = f.getRefObjectType();
            if (refObj == null || refObj.isEmpty()) continue;

            String title = f.getTitle();
            if (title == null || title.isEmpty()) continue;

            // 检查引用字段 title 是否是 fieldPart 的前缀
            if (!fieldPart.startsWith(title) || fieldPart.equals(title)) continue;
            String remainder = fieldPart.substring(title.length());
            if (remainder.isEmpty()) continue;

            // 在被引用对象中匹配剩余部分
            List<FieldMatch> refResults = matchFields(refObj, remainder);
            if (refResults.isEmpty()) continue;

            // 计算链式分数
            int titleScore = calculateMatchScore(title, title); // 精确前缀
            double chainScore = titleScore / 1000.0;
            String refPath = title + "(" + canonicalName(f) + ") → " + refObj;

            for (FieldMatch fm : refResults) {
                fm.score *= chainScore;
                fm.refPath = refPath;
            }

            // 保留分数最高的一组
            if (!refResults.isEmpty() && refResults.get(0).score > bestScore) {
                bestScore = refResults.get(0).score;
                bestResults = refResults;
            }
        }
        return bestResults;
    }

    /**
     * 在指定对象中查找标题或名字匹配的引用字段（有 referInfo 的字段）
     */
    private BaseappObjectField findReferField(String objectType, String fieldQuery) {
        List<BaseappObjectField> fields = analyzerService.getFieldDetailsForObject(objectType);
        BaseappObjectField bestMatch = null;
        int bestScore = 0;

        for (BaseappObjectField f : fields) {
            // 只看有 referInfo 的字段
            if (f.getRefObjectType() == null || f.getRefObjectType().isEmpty()) continue;

            String name = canonicalName(f);
            int nameScore = calculateMatchScore(fieldQuery, name);
            String title = f.getTitle();
            int titleScore = title != null ? calculateMatchScore(fieldQuery, title) : 0;
            int score = Math.max(nameScore, titleScore);

            if (score > bestScore) {
                bestScore = score;
                bestMatch = f;
            }
        }
        return bestMatch;
    }

    /**
     * 多路对象匹配：精确英文名 → 反向索引标题 → 同义词 → 中文标题包含
     */
    private List<ObjectMatch> matchObjects(String input, int maxResults, boolean filterBillOnly) {
        List<ObjectMatch> matches = new ArrayList<>();
        if (input == null || input.isEmpty()) return matches;


        Map<String, ObjectTypeMeta> metas = analyzerService.getObjectTypeMetas();
        Map<String, List<String>> titleIndex = analyzerService.getTitleToObjectTypes();
        Map<String, List<String>> synonyms = analyzerService.getGlobalSynonyms();
        Set<String> allTypes = analyzerService.getAllObjectTypes();
        Set<String> matched = new LinkedHashSet<>();

        // 1. 精确英文名匹配（PascalCase）
        for (String type : allTypes) {
            if (type.equalsIgnoreCase(input)) {
                addObjectMatch(matches, matched, type, metas, 1.0, ResolveModels.MatchSource.EXACT_NAME, filterBillOnly);
            }
        }

        // 2. 反向索引标题精确匹配（替代硬编码同义词）
        List<String> titleMatched = titleIndex.get(input);

        if (titleMatched != null) {
            for (String type : titleMatched) {
                addObjectMatch(matches, matched, type, metas,
                        calculateMatchScore(input, input) / 1000.0, ResolveModels.MatchSource.TITLE_EXACT, filterBillOnly);
            }
        }

        // 3. 同义词精确匹配（GLOBAL_SYNONYMS 作为补充）
        for (Map.Entry<String, List<String>> entry : synonyms.entrySet()) {
            for (String syn : entry.getValue()) {
                if (syn.equals(input)) {
                    addObjectMatch(matches, matched, entry.getKey(), metas, 0.9, ResolveModels.MatchSource.SYNONYM, filterBillOnly);
                }
            }
        }

        // 4. 英文名子串匹配
        String inputLower = input.toLowerCase();
        for (String type : allTypes) {
            if (matched.contains(type)) continue;
            if (type.toLowerCase().contains(inputLower)) {
                int rawScore = calculateMatchScore(inputLower, type.toLowerCase());
                addObjectMatch(matches, matched, type, metas, rawScore / 1000.0, ResolveModels.MatchSource.EXACT_NAME, filterBillOnly);
            }
        }

        // 5. 中文标题包含匹配
        for (Map.Entry<String, ObjectTypeMeta> entry : metas.entrySet()) {
            if (matched.contains(entry.getKey())) continue;
            String titleVal = entry.getValue().getTitle();
            if (titleVal != null && (titleVal.contains(input) || input.contains(titleVal))) {
                int rawScore = calculateMatchScore(input, titleVal);
                addObjectMatch(matches, matched, entry.getKey(), metas, rawScore / 1000.0, ResolveModels.MatchSource.TITLE_CONTAINS, filterBillOnly);
            }
        }

        matches.sort((a, b) -> Double.compare(b.score, a.score));
        if (matches.size() > maxResults) {
            matches = new ArrayList<>(matches.subList(0, maxResults));
        }
        return matches;
    }

    /**
     * 5 档评分算法 + 紧凑度修正。
     * 返回原始整数分（0~1000），调用方除以 1000 归一化。
     */
    static int calculateMatchScore(String query, String target) {
        if (query == null || target == null || query.isEmpty() || target.isEmpty()) return 0;
        String q = query.toLowerCase();
        String t = target.toLowerCase();

        int baseScore;
        if (t.equals(q)) {
            baseScore = 1000; // 精确匹配
        } else if (t.endsWith(q)) {
            baseScore = 600;  // 后缀匹配
        } else if (t.startsWith(q)) {
            baseScore = 500;  // 前缀匹配
        } else if (t.contains(q)) {
            baseScore = 400;  // 包含匹配
        } else if (q.contains(t)) {
            baseScore = 200;  // 模糊匹配（查询包含目标）
        } else {
            // 公共字符匹配（如 "原始金额" vs "原币金额"）
            int commonChars = 0;
            boolean[] used = new boolean[t.length()];
            for (int i = 0; i < q.length(); i++) {
                for (int j = 0; j < t.length(); j++) {
                    if (!used[j] && q.charAt(i) == t.charAt(j)) {
                        commonChars++;
                        used[j] = true;
                        break;
                    }
                }
            }
            int maxLen = Math.max(q.length(), t.length());
            double ratio = (double) commonChars / maxLen;
            if (ratio >= 0.5 && commonChars >= 2) {
                baseScore = (int) (150 * ratio); // 最多 150 分
            } else {
                return 0;
            }
        }

        // 紧凑度修正：匹配长度占比越高分数越高
        int matchLen = Math.min(q.length(), t.length());
        int maxLen = Math.max(q.length(), t.length());
        double compactness = 0.8 + 0.2 * ((double) matchLen / maxLen);
        return (int) Math.round(baseScore * compactness);
    }

    private void addObjectMatch(List<ObjectMatch> matches, Set<String> matched,
                                String objectType, Map<String, ObjectTypeMeta> metas,
                                double score, ResolveModels.MatchSource source, boolean filterBillOnly) {
        if (matched.contains(objectType)) return;
        // 排除视图对象（名字以 View 结尾）
        if (objectType.endsWith("View")) return;
        // filterBillOnly=true 时只匹配单据(bill)对象
        ObjectTypeMeta meta = metas.get(objectType);
        if (filterBillOnly && meta != null) {
            String type = meta.getType();
            if (type != null && !type.isEmpty() && !"bill".equals(type)) {
                return;
            }
        }
        matched.add(objectType);
        ObjectMatch om = new ObjectMatch();
        om.objectType = objectType;
        if (meta != null) {
            om.title = meta.getTitle() != null ? meta.getTitle() : objectType;
            om.description = meta.getDescription();
            om.type = meta.getType();
            om.isDisabled = meta.getIsDisabled();
        } else {
            om.title = analyzerService.getObjectTitles().getOrDefault(objectType, objectType);
        }
        om.score = score;
        om.matchSource = source;
        matches.add(om);
    }

    /**
     * 填充对象上下文：子表列表、主表关系、入站/出站回写
     */
    private void fillObjectContext(ObjectMatch om) {
        // 子表列表
        Set<String> details = analyzerService.getMainToDetails().get(om.objectType);
        if (details != null) {
            om.detailEntities = new ArrayList<>(details);
        }
        // 主表关系
        if (om.parentEntity == null) {
            String parent = analyzerService.getDetailToMain().get(om.objectType);
            if (parent != null) {
                om.parentEntity = parent;
            }
        }
    }

    /**
     * 在指定对象内匹配字段：精确英文名 → 标题精确 → 标题包含，使用 calculateMatchScore 评分
     */
    private List<FieldMatch> matchFields(String objectType, String fieldInput) {
        return matchFields(objectType, fieldInput, null);
    }

    private List<FieldMatch> matchFields(String objectType, String fieldInput, String bizTypeFilter) {
        List<FieldMatch> matches = new ArrayList<>();
        if (fieldInput == null || fieldInput.isEmpty()) return matches;

        List<BaseappObjectField> fields = analyzerService.getFieldDetailsForObject(objectType);
        // key=fieldName → best FieldMatch（取最高分）
        Map<String, FieldMatch> bestByField = new LinkedHashMap<>();

        // bizType 精确匹配路径（US2）：score = 0.8
        if (bizTypeFilter != null) {
            for (BaseappObjectField f : fields) {
                String name = canonicalName(f);
                String bt = f.getBizType();
                if (bt != null && bt.toLowerCase().contains(bizTypeFilter.toLowerCase())) {
                    FieldMatch fm = buildFieldMatchObj(f, 0.8, ResolveModels.MatchSource.EXACT_NAME);
                    bestByField.put(name, fm);
                }
            }
        }

        // 标题/英文名/description 匹配：独立评分，与 bizType 互不阻断
        for (BaseappObjectField f : fields) {
            String name = canonicalName(f);

            int nameScore = calculateMatchScore(fieldInput, name);
            String title = f.getTitle();
            int titleScore = title != null ? calculateMatchScore(fieldInput, title) : 0;
            String desc = f.getDescription();
            int descScore = desc != null ? calculateMatchScore(fieldInput, desc) : 0;
            if (descScore > 100) descScore = 100;

            int bestScore = Math.max(nameScore, Math.max(titleScore, descScore));
            if (bestScore > 0) {
                ResolveModels.MatchSource source;
                if (nameScore >= titleScore && nameScore >= 1000) {
                    source = ResolveModels.MatchSource.EXACT_NAME;
                } else if (titleScore >= 1000) {
                    source = ResolveModels.MatchSource.TITLE_EXACT;
                } else if (titleScore > 0) {
                    source = ResolveModels.MatchSource.TITLE_CONTAINS;
                } else {
                    source = ResolveModels.MatchSource.EXACT_NAME;
                }
                double score = bestScore / 1000.0;
                if (Boolean.TRUE.equals(f.getIsMasterField())) {
                    score = Math.min(score * 1.2, 1.0);
                }
                FieldMatch fm = buildFieldMatchObj(f, score, source);
                // 取较高分
                FieldMatch existing = bestByField.get(name);
                if (existing == null || fm.score > existing.score) {
                    bestByField.put(name, fm);
                }
            }
        }

        matches.addAll(bestByField.values());
        matches.sort((a, b) -> Double.compare(b.score, a.score));
        return matches;
    }
    /**
     * 用原始 query 对字段做模糊匹配，再补充高价值字段至上限 50 个。
     * 策略：先用 query 匹配（标题/英文名），匹配到的 score 高；
     * 再按分类优先级补充未匹配的高价值字段（回写 > 触发 > 金额 > 数量）。
     */
    private List<FieldMatch> matchFieldsWithFallback(String objectType, String query) {
        // 第一步：用 query 做字段匹配
        List<FieldMatch> matched = matchFields(objectType, query);
        Set<String> matchedNames = new LinkedHashSet<>();
        for (FieldMatch fm : matched) matchedNames.add(fm.field);

        // 第二步：补充高价值字段（不重复已匹配的）
        List<BaseappObjectField> fields = analyzerService.getFieldDetailsForObject(objectType);
        List<FieldMatch> supplements = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>(matchedNames);

        // 按优先级分组补充
        for (BaseappObjectField f : fields) {
            if (isWriteBackField(f))    addFieldMatch(supplements, seen, f, 0.4, ResolveModels.MatchSource.TITLE_CONTAINS);
        }
        for (BaseappObjectField f : fields) {
            if (isTriggerField(f))      addFieldMatch(supplements, seen, f, 0.35, ResolveModels.MatchSource.TITLE_CONTAINS);
        }
        for (BaseappObjectField f : fields) {
            if (isAmountField(f))       addFieldMatch(supplements, seen, f, 0.3, ResolveModels.MatchSource.TITLE_CONTAINS);
        }
        for (BaseappObjectField f : fields) {
            if (isQtyField(f))          addFieldMatch(supplements, seen, f, 0.3, ResolveModels.MatchSource.TITLE_CONTAINS);
        }

        // 合并：匹配结果在前，补充在后
        List<FieldMatch> result = new ArrayList<>(matched);
        result.addAll(supplements);

        if (result.size() > 50) {
            result = new ArrayList<>(result.subList(0, 50));
        }
        return result;
    }

    /** 列出指定对象的所有自定义字段（isCustomizedField=true） */
    private List<FieldMatch> listCustomizedFields(String objectType) {
        List<FieldMatch> result = new ArrayList<>();
        List<BaseappObjectField> fields = analyzerService.getFieldsByObject(objectType);
        if (fields == null) return result;
        for (BaseappObjectField f : fields) {
            if (Boolean.TRUE.equals(f.getIsCustomizedField())) {
                result.add(buildFieldMatchObj(f, 1.0, ResolveModels.MatchSource.EXACT_NAME));
            }
        }
        return result;
    }

    private void addFieldMatch(List<FieldMatch> matches, Set<String> matched,
                               BaseappObjectField f, double score, ResolveModels.MatchSource source) {
        String name = canonicalName(f);
        if (matched.contains(name)) return;
        matched.add(name);
        matches.add(buildFieldMatchObj(f, score, source));
    }

    /** 构建 FieldMatch 对象（不操作集合，纯构建） */
    private FieldMatch buildFieldMatchObj(BaseappObjectField f, double score, ResolveModels.MatchSource source) {
        String name = canonicalName(f);
        FieldMatch fm = new FieldMatch();
        fm.field = name;
        fm.title = f.getTitle();
        fm.score = score;
        fm.matchSource = source;
        fm.bizType = f.getBizType();
        fm.description = f.getDescription();
        fm.enumType = f.getEnumType();
        fm.isDisabled = f.getIsDisabled();
        fm.isCustomizedField = f.getIsCustomizedField();
        fm.hasWriteBack = isWriteBackField(f);
        fm.hasTrigger = isTriggerField(f);

        // 字段分类
        if (isWriteBackField(f)) {
            fm.category = ResolveModels.FieldCategory.WRITE_BACK;
        } else if (isVirtualField(f)) {
            fm.category = ResolveModels.FieldCategory.VIRTUAL;
        } else if (isTriggerField(f)) {
            fm.category = ResolveModels.FieldCategory.TRIGGER;
        } else if (isAmountField(f)) {
            fm.category = ResolveModels.FieldCategory.AMOUNT;
        } else if (isQtyField(f)) {
            fm.category = ResolveModels.FieldCategory.QTY;
        } else {
            fm.category = ResolveModels.FieldCategory.BASE;
        }
        // 填充枚举值（US3）
        if (fm.enumType != null && !fm.enumType.isEmpty()) {
            EnumTypeMeta enumMeta = analyzerService.getEnumTypeIndex().get(fm.enumType);
            if (enumMeta != null) {
                fm.enumValues = enumMeta.getValues();
            }
        }
        // 填充依赖摘要（US4）
        String objectType = f.getObjectType();
        if (objectType != null) {
            try {
                String qualifiedName = objectType + "." + f.getName();
                // 先直接查当前 objectType，若未命中（子表场景），尝试查主表
                ExpressionFieldInfo info = analyzerService.getExpressionFieldService()
                        .getExpressionFieldInfo(objectType);
                if (info == null) {
                    // 子表的依赖索引在主表下，通过 detailToMain 反查
                    String mainObj = analyzerService.getDetailToMain().get(objectType);
                    if (mainObj != null) {
                        info = analyzerService.getExpressionFieldService()
                                .getExpressionFieldInfo(mainObj);
                    }
                }
                if (info != null) {
                    Map<String, Set<String>> fieldToExpr = info.getFieldToExprFields();
                    if (fieldToExpr != null) {
                        Set<String> depBy = fieldToExpr.get(qualifiedName);
                        if (depBy != null && !depBy.isEmpty()) {
                            fm.dependedByCount = depBy.size();
                            fm.dependedByFields = new ArrayList<>(depBy);
                        }
                    }
                }
            } catch (Exception e) {
                // 依赖信息为非关键路径，出错时静默跳过
            }
        }
        return fm;
    }

    // =========================================================
    // 内部工具方法
    // =========================================================

    /**
     * 用已知对象标题做前缀拆分。
     * 遍历所有对象中文标题，找最长前缀匹配，在标题边界处切出 fieldPart。
     * 例如 "应收合同收款状态" → "应收合同"(标题) + "收款状态"(fieldPart)
     */
    private void splitByKnownTitle(ParsedQuery pq) {
        String input = pq.objectPart;
        Map<String, List<String>> titleIndex = analyzerService.getTitleToObjectTypes();

        String bestTitle = null;
        for (String title : titleIndex.keySet()) {
            if (title.length() >= input.length()) continue; // 标题必须比输入短才有剩余部分
            if (input.startsWith(title)) {
                if (bestTitle == null || title.length() > bestTitle.length()) {
                    bestTitle = title;
                }
            }
        }
        if (bestTitle != null) {
            pq.objectPart = bestTitle;
            pq.fieldPart = input.substring(bestTitle.length()).trim();
            if (pq.fieldPart.isEmpty()) pq.fieldPart = null;
        }
    }

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

    // =========================================================
    // US1: 对象特性过滤
    // =========================================================

    /** 检测查询中是否包含特性关键词，设置 traitFilter */
    private void detectTraitFilter(ParsedQuery pq, String query) {
        for (Map.Entry<String, String> e : TRAIT_KEYWORDS.entrySet()) {
            if (query.contains(e.getKey())) {
                // 如果已被识别为子表查询，不要将"子表"/"明细"当作特性过滤
                if (pq.isDetailQuery && "isDetail".equals(e.getValue())) {
                    continue;
                }
                // "自定义" 出现在 fieldPart 中（如 "收入确认单的自定义字段"）→ 字段级过滤
                if ("isCustomizedEntity".equals(e.getValue())
                        && pq.fieldPart != null && pq.fieldPart.contains(e.getKey())
                        && pq.objectPart != null && !pq.objectPart.contains(e.getKey())) {
                    pq.customizedFieldFilter = true;
                    // 清除 fieldPart 中的 "自定义字段"/"自定义" 关键词
                    pq.fieldPart = pq.fieldPart.replace("自定义字段", "").replace("自定义", "").trim();
                    if (pq.fieldPart.isEmpty()) pq.fieldPart = null;
                    return;
                }
                pq.traitFilter = e.getValue();
                // 从 objectPart 中移除特性关键词，避免干扰对象匹配
                if (pq.objectPart != null) {
                    pq.objectPart = pq.objectPart.replace(e.getKey(), "").trim();
                    if (pq.objectPart.isEmpty()) {
                        pq.objectPart = "";
                    }
                }
                return;
            }
        }
    }

    /** 按特性标记过滤对象列表；若过滤后为空则扫描所有对象 */
    private List<ObjectMatch> filterByTrait(List<ObjectMatch> matches, String traitName) {
        // 先从已匹配对象中过滤
        Map<String, ObjectTypeMeta> metas = analyzerService.getObjectTypeMetas();
        if (!matches.isEmpty()) {
            List<ObjectMatch> filtered = new ArrayList<>();
            for (ObjectMatch om : matches) {
                ObjectTypeMeta meta = metas.get(om.objectType);
                if (meta != null && matchTrait(meta, traitName)) {
                    fillObjectTraits(om, meta);
                    filtered.add(om);
                }
            }
            if (!filtered.isEmpty()) return filtered;
        }
        // fallback：全量扫描所有对象
        List<ObjectMatch> allMatches = new ArrayList<>();
        for (Map.Entry<String, ObjectTypeMeta> e : metas.entrySet()) {
            if (e.getKey().endsWith("View")) continue;
            if (!matchTrait(e.getValue(), traitName)) continue;
            ObjectMatch om = new ObjectMatch();
            om.objectType = e.getKey();
            om.title = e.getValue().getTitle() != null ? e.getValue().getTitle() : e.getKey();
            om.description = e.getValue().getDescription();
            om.type = e.getValue().getType();
            om.isDisabled = e.getValue().getIsDisabled();
            om.score = 0.5;
            om.matchSource = ResolveModels.MatchSource.TITLE_CONTAINS;
            fillObjectTraits(om, e.getValue());
            allMatches.add(om);
        }
        return allMatches;
    }

    /**
     * 纯 bizType 搜索：遍历所有 bill 对象，返回有该 bizType 字段的对象（限制数量）
     */
    private List<ObjectMatch> sampleObjectsByBizType(String bizType, int maxResults) {
        List<ObjectMatch> results = new ArrayList<>();
        Map<String, ObjectTypeMeta> metas = analyzerService.getObjectTypeMetas();
        for (Map.Entry<String, ObjectTypeMeta> e : metas.entrySet()) {
            if (e.getKey().endsWith("View")) continue;
            ObjectTypeMeta meta = e.getValue();
            if (meta.getType() != null && !meta.getType().isEmpty() && !"bill".equals(meta.getType())) continue;
            // 检查该对象是否有该 bizType 的字段
            List<BaseappObjectField> fields = analyzerService.getFieldDetailsForObject(e.getKey());
            boolean hasField = false;
            for (BaseappObjectField f : fields) {
                if (bizType.equalsIgnoreCase(f.getBizType())) { hasField = true; break; }
            }
            if (!hasField) continue;
            ObjectMatch om = new ObjectMatch();
            om.objectType = e.getKey();
            om.title = meta.getTitle() != null ? meta.getTitle() : e.getKey();
            om.description = meta.getDescription();
            om.type = meta.getType();
            om.isDisabled = meta.getIsDisabled();
            om.score = 0.5;
            om.matchSource = ResolveModels.MatchSource.TITLE_CONTAINS;
            results.add(om);
            if (results.size() >= maxResults) break;
        }
        return results;
    }

    private boolean matchTrait(ObjectTypeMeta meta, String traitName) {
        switch (traitName) {
            case "isTree": return Boolean.TRUE.equals(meta.getIsTree());
            case "isDetail": return Boolean.TRUE.equals(meta.getIsDetail());
            case "isSupportChangeLog": return Boolean.TRUE.equals(meta.getIsSupportChangeLog());
            case "isCustomizedEntity": return Boolean.TRUE.equals(meta.getIsCustomizedEntity());
            case "isMultiDataVersion": return Boolean.TRUE.equals(meta.getIsMultiDataVersion());
            default: return false;
        }
    }

    /** 将 ObjectTypeMeta 中的特性字段同步到 ObjectMatch */
    private void fillObjectTraits(ObjectMatch om, ObjectTypeMeta meta) {
        om.isTree = meta.getIsTree();
        om.isDetail = meta.getIsDetail();
        om.isSupportChangeLog = meta.getIsSupportChangeLog();
        om.isCustomizedEntity = meta.getIsCustomizedEntity();
        om.isMultiDataVersion = meta.getIsMultiDataVersion();
        om.appName = meta.getAppName();
    }

    // =========================================================
    // US2: bizType 关键词检测
    // =========================================================

    /**
     * 检测是否为 bizType 过滤意图。
     * 先检查 fieldPart，再检查 objectPart（兼容纯 "金额字段" 无分隔符的场景）。
     * 只在内容几乎等同于 bizType 关键词时触发（如 "金额"/"金额字段"），
     * 不对 "收款金额" 这类包含业务语义前缀的情况触发。
     */
    private void detectBizTypeFilter(ParsedQuery pq) {
        // 优先从 fieldPart 检测
        String target = pq.fieldPart;
        // 如果 fieldPart 为空，从 objectPart 检测（纯 "金额字段" 场景）
        if (target == null && pq.objectPart != null) {
            target = pq.objectPart;
        }
        if (target == null) return;
        for (Map.Entry<String, String> e : BIZTYPE_KEYWORDS.entrySet()) {
            String kw = e.getKey();
            if (target.contains(kw)) {
                // 去掉关键词和通用修饰词后，看是否还有实质性内容
                String remainder = target.replace(kw, "").replace("字段", "").replace("类型", "").trim();
                if (remainder.isEmpty()) {
                    pq.bizTypeFilter = e.getValue();
                    // 纯 bizType 查询时清空 objectPart，后续走全量对象采样
                    if (pq.fieldPart == null) {
                        pq.objectPart = "";
                    }
                    return;
                }
            }
        }
    }

    // =========================================================
    // US5: 反向引用查询
    // =========================================================

    /** 检测查询中是否包含反向引用查询关键词，提取目标对象名 */
    private void detectReverseRefQuery(ParsedQuery pq, String query) {
        for (String prefix : REVERSE_REF_PREFIXES) {
            if (query.contains(prefix)) {
                String remainder = query.substring(query.indexOf(prefix) + prefix.length()).trim();
                if (!remainder.isEmpty()) {
                    pq.reverseRefTarget = remainder;
                    return;
                }
            }
        }
    }

    /** 通过 EntityReferenceService 查询反向引用，构建 ObjectMatch 列表 */
    private List<ObjectMatch> matchReverseReferences(String targetInput) {
        // 先把用户输入解析为实际的 objectType
        List<ObjectMatch> targetMatches = matchObjects(targetInput, 1, false);
        String targetObjectType;
        if (!targetMatches.isEmpty()) {
            targetObjectType = targetMatches.get(0).objectType;
        } else {
            // 无法匹配到对象，直接用输入作为 objectType 尝试
            targetObjectType = targetInput;
        }

        Map<String, Map<String, Boolean>> referRelations =
                analyzerService.getEntityReferenceService().getReferRelations(targetObjectType);
        if (referRelations.isEmpty()) return Collections.emptyList();

        Map<String, ObjectTypeMeta> metas = analyzerService.getObjectTypeMetas();
        List<ObjectMatch> results = new ArrayList<>();
        for (Map.Entry<String, Map<String, Boolean>> entry : referRelations.entrySet()) {
            String refObjectType = entry.getKey();
            if (refObjectType.endsWith("View")) continue;
            ObjectMatch om = new ObjectMatch();
            om.objectType = refObjectType;
            ObjectTypeMeta meta = metas.get(refObjectType);
            if (meta != null) {
                om.title = meta.getTitle() != null ? meta.getTitle() : refObjectType;
                om.description = meta.getDescription();
                om.type = meta.getType();
                om.isDisabled = meta.getIsDisabled();
            } else {
                om.title = analyzerService.getObjectTitles().getOrDefault(refObjectType, refObjectType);
            }
            om.score = 0.8;
            om.matchSource = ResolveModels.MatchSource.EXACT_NAME;
            // 填充引用字段信息到 fieldMatches
            List<FieldMatch> refFields = new ArrayList<>();
            for (String fkField : entry.getValue().keySet()) {
                FieldMatch fm = new FieldMatch();
                fm.field = fkField;
                fm.score = 0.8;
                fm.refPath = fkField + " → " + targetObjectType;
                fm.matchType = "REVERSE_REF";
                refFields.add(fm);
            }
            om.fieldMatches = refFields;
            fillObjectContext(om);
            results.add(om);
        }
        results.sort((a, b) -> a.objectType.compareTo(b.objectType));
        return results;
    }

    // =========================================================
    // US3: 枚举搜索
    // =========================================================

    /** 匹配枚举类型：英文名精确 → 标题精确 → 标题包含 */
    private List<EnumMatch> matchEnums(String query) {
        List<EnumMatch> matches = new ArrayList<>();
        if (query == null || query.isEmpty()) return matches;

        Map<String, EnumTypeMeta> typeIndex = analyzerService.getEnumTypeIndex();
        Map<String, List<String>> titleIndex = analyzerService.getEnumTitleIndex();
        Map<String, List<String>> fieldIndex = analyzerService.getEnumFieldIndex();
        Set<String> matched = new LinkedHashSet<>();

        // 1. 英文名精确匹配
        EnumTypeMeta exact = typeIndex.get(query);
        if (exact != null) {
            addEnumMatch(matches, matched, exact, 1.0, ResolveModels.MatchSource.EXACT_NAME, fieldIndex);
        }

        // 2. 标题精确匹配
        List<String> titleExact = titleIndex.get(query);
        if (titleExact != null) {
            for (String enumName : titleExact) {
                EnumTypeMeta meta = typeIndex.get(enumName);
                if (meta != null) {
                    addEnumMatch(matches, matched, meta, 0.9, ResolveModels.MatchSource.TITLE_EXACT, fieldIndex);
                }
            }
        }

        // 3. 标题包含匹配
        for (Map.Entry<String, EnumTypeMeta> e : typeIndex.entrySet()) {
            if (matched.contains(e.getKey())) continue;
            EnumTypeMeta meta = e.getValue();
            String title = meta.getTitle();
            if (title != null && (title.contains(query) || query.contains(title))) {
                addEnumMatch(matches, matched, meta, 0.6, ResolveModels.MatchSource.TITLE_CONTAINS, fieldIndex);
            }
            // 英文名子串
            if (e.getKey().toLowerCase().contains(query.toLowerCase())) {
                addEnumMatch(matches, matched, meta, 0.5, ResolveModels.MatchSource.EXACT_NAME, fieldIndex);
            }
        }

        // 4. 标题模糊匹配（公共字符评分兜底）
        if (matches.size() < 3 && query.length() >= 2) {
            for (Map.Entry<String, EnumTypeMeta> e : typeIndex.entrySet()) {
                if (matched.contains(e.getKey())) continue;
                EnumTypeMeta meta = e.getValue();
                String title = meta.getTitle();
                if (title != null) {
                    int score = calculateMatchScore(query, title);
                    if (score >= 100) {
                        addEnumMatch(matches, matched, meta, score / 1000.0 * 0.5,
                                ResolveModels.MatchSource.TITLE_CONTAINS, fieldIndex);
                    }
                }
            }
        }

        matches.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (matches.size() > 10) matches = new ArrayList<>(matches.subList(0, 10));
        return matches;
    }

    private void addEnumMatch(List<EnumMatch> matches, Set<String> matched,
                              EnumTypeMeta meta, double score, ResolveModels.MatchSource source,
                              Map<String, List<String>> fieldIndex) {
        if (matched.contains(meta.getName())) return;
        matched.add(meta.getName());
        EnumMatch em = new EnumMatch();
        em.setEnumType(meta.getName());
        em.setTitle(meta.getTitle());
        em.setDescription(meta.getDescription());
        em.setScore(score);
        em.setMatchSource(source);
        em.setValues(meta.getValues());
        List<String> usedBy = fieldIndex.get(meta.getName());
        if (usedBy != null) em.setUsedByFields(usedBy);
        matches.add(em);
    }

    // =========================================================
    // US4: 表达式依赖/回写摘要填充
    // =========================================================

    /** 为字段匹配结果填充依赖摘要和回写来源 */
    private void enrichFieldSummaries(String objectType, List<FieldMatch> fieldMatches) {
        if (fieldMatches == null) return;
        for (FieldMatch fm : fieldMatches) {
            // 依赖摘要
            try {
                ExpressionFieldInfo info = analyzerService.getExpressionFieldService()
                        .getExpressionFieldInfo(objectType);
                if (info != null) {
                    Map<String, Set<String>> fieldToExpr = info.getFieldToExprFields();
                    if (fieldToExpr != null) {
                        Set<String> depFields = fieldToExpr.get(fm.field);
                        if (depFields != null && !depFields.isEmpty()) {
                            fm.dependedByCount = depFields.size();
                            fm.dependedByFields = new ArrayList<>(depFields);
                            if (fm.dependedByFields.size() > 5) {
                                fm.dependedByFields = fm.dependedByFields.subList(0, 5);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // 依赖图未就绪，跳过
            }
            // 回写来源摘要
            try {
                List<BaseappObjectField> objFields = analyzerService.getFieldDetailsForObject(objectType);
                for (BaseappObjectField bf : objFields) {
                    String name = canonicalName(bf);
                    if (name.equals(fm.field) && bf.getWriteBackExpr() != null && !bf.getWriteBackExpr().isEmpty()) {
                        fm.writeBackSource = summarizeWriteBack(bf.getWriteBackExpr());
                        break;
                    }
                }
            } catch (Exception e) {
                // 跳过
            }
        }
    }

    /** 从 writeBackExpr JSON 提取简要摘要 */
    private String summarizeWriteBack(String wbExpr) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(wbExpr);
            String srcObj = root.path("srcObjectType").asText(null);
            String expr = root.path("expression").asText(null);
            if (srcObj != null && expr != null) {
                return srcObj + "." + expr;
            }
            if (expr != null) return expr;
        } catch (Exception e) {
            // 非 JSON 格式的 writeBackExpr
        }
        if (wbExpr.length() > 60) return wbExpr.substring(0, 60) + "...";
        return wbExpr;
    }
}
