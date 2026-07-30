package com.deepmodel.relation.service;

import com.deepmodel.relation.dao.MetadataRepository;
import com.deepmodel.relation.env.EnvContext;
import com.deepmodel.relation.env.EnvSnapshot;
import com.deepmodel.relation.env.EnvSnapshotManager;
import com.deepmodel.relation.model.BaseappObjectField;
import com.deepmodel.relation.model.GraphModels;
import com.deepmodel.relation.model.ObjectTypeMeta;
import com.deepmodel.relation.model.EnumTypeMeta;
import com.deepmodel.relation.model.EnumValueMeta;
import com.deepmodel.relation.model.WriteBackExpr;
import com.deepmodel.relation.util.ExprUtils;
import com.deepmodel.relation.util.JiebaUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
public class ImpactAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(ImpactAnalyzerService.class);

    private final MetadataRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
            .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
            .configure(JsonParser.Feature.ALLOW_COMMENTS, true)
            .configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true);

    private static final Map<String, List<String>> GLOBAL_SYNONYMS =
            Collections.unmodifiableMap(new HashMap<>(JiebaUtils.loadSynonyms()));

    private final FormulaParserService formulaParserService;
    private final WriteBackRelationService writeBackRelationService;
    private final ExpressionFieldService expressionFieldService;
    private final EntityReferenceService entityReferenceService;
    private final EnvSnapshotManager snapshotManager;

    // @Lazy 避免与 SkillsService 循环依赖：SkillsService 注入 ImpactAnalyzerService，
    // ImpactAnalyzerService 仅在 clearAnalysisCache 时通知 SkillsService 清缓存。
    @Lazy
    @Autowired
    private SkillsService skillsService;

    public ImpactAnalyzerService(MetadataRepository repository, FormulaParserService formulaParserService,
                                 WriteBackRelationService writeBackRelationService,
                                 ExpressionFieldService expressionFieldService,
                                 EntityReferenceService entityReferenceService,
                                 EnvSnapshotManager snapshotManager) {
        this.repository = repository;
        this.formulaParserService = formulaParserService;
        this.writeBackRelationService = writeBackRelationService;
        this.expressionFieldService = expressionFieldService;
        this.entityReferenceService = entityReferenceService;
        this.snapshotManager = snapshotManager;
    }

    @PostConstruct
    public void init() {
        // 注册 loader：当 EnvSnapshotManager 首次访问某 env 时调用
        snapshotManager.registerLoader(this::loadInto);
    }

    /** 获取当前请求上下文对应的 EnvSnapshot（首次访问时触发加载）。 */
    private EnvSnapshot env() {
        return snapshotManager.current();
    }

    /** 强制重新加载当前 env 的数据。 */
    public synchronized void reload() {
        String env = EnvContext.requireCurrent();
        snapshotManager.invalidate(env);
        snapshotManager.getOrLoad(env);
    }

    /**
     * 将指定 env 的全量元数据 + 各索引填充到 snapshot。
     * 由 {@link EnvSnapshotManager} 在首次访问 env 时回调。
     */
    private synchronized void loadInto(EnvSnapshot snap) {
        long t0 = System.currentTimeMillis();

        long tSelectStart = System.currentTimeMillis();
        List<BaseappObjectField> rows = repository.selectAll();
        long tSelectEnd = System.currentTimeMillis();

        long tGroupStart = System.currentTimeMillis();
        Map<String, List<BaseappObjectField>> byObj = rows.stream()
                .collect(Collectors.groupingBy(BaseappObjectField::getObjectType));
        long tGroupEnd = System.currentTimeMillis();

        snap.rowsByObject.clear();
        snap.rowsByObject.putAll(byObj);
        snap.allRows = rows;

        // 解析 referInfo JSON，设置 refObjectType（供链式引用解析和级联搜索使用）
        int refCount = 0;
        for (BaseappObjectField f : rows) {
            String ref = extractRefFromReferInfo(f.getReferInfo());
            if (ref != null && !ref.isEmpty()) {
                f.setRefObjectType(ref);
                refCount++;
            }
        }
        log.info("Parsed {} referInfo → refObjectType mappings", refCount);

        long disabledFieldCount = rows.stream()
                .filter(f -> Boolean.TRUE.equals(f.getIsDisabled()))
                .count();
        if (disabledFieldCount > 0) {
            log.info("Loaded {} fields ({} disabled)", rows.size(), disabledFieldCount);
        }

        long tViewsStart = System.currentTimeMillis();
        loadViews(snap);
        long tViewsEnd = System.currentTimeMillis();

        long tTitleStart = System.currentTimeMillis();
        snap.objectTitles.clear();
        snap.objectTypeMetas.clear();
        snap.titleToObjectTypes.clear();
        try {
            List<ObjectTypeMeta> metas = repository.selectObjectTitles();
            Map<String, List<String>> titleIndex = new HashMap<>();
            int disabledCount = 0;
            for (ObjectTypeMeta m : metas) {
                if (m.getName() != null) {
                    snap.objectTypeMetas.put(m.getName(), m);
                    if (m.getTitle() != null) {
                        snap.objectTitles.put(m.getName(), m.getTitle());
                        titleIndex.computeIfAbsent(m.getTitle(), k -> new ArrayList<>()).add(m.getName());
                    }
                    if (Boolean.TRUE.equals(m.getIsDisabled())) {
                        disabledCount++;
                    }
                }
            }
            snap.titleToObjectTypes.putAll(titleIndex);
            log.info("Loaded {} object metas ({} disabled), {} title reverse-index entries",
                    snap.objectTypeMetas.size(), disabledCount, snap.titleToObjectTypes.size());
            enrichFieldAppNamesFromObjectMetas(snap);
        } catch (Exception e) {
            log.warn("Failed to load object titles", e);
        }
        long tTitleEnd = System.currentTimeMillis();

        long tBillStart = System.currentTimeMillis();
        try {
            snap.billObjectTypes = deriveBillObjectTypes(snap);
            log.info("Loaded {} bill object types (from object metas)", snap.billObjectTypes.size());
        } catch (Exception e) {
            log.warn("Failed to derive bill object types", e);
            snap.billObjectTypes = Collections.emptySet();
        }
        long tBillEnd = System.currentTimeMillis();

        long tChangeBillStart = System.currentTimeMillis();
        try {
            List<String> cbNames = repository.selectChangeBillSupportedEntities();
            Set<String> cbs = new HashSet<>();
            for (String n : cbNames) {
                if (n != null && !n.trim().isEmpty()) {
                    cbs.add(n.trim());
                }
            }
            snap.changeBillEntities = cbs;
            log.info("Loaded {} change-bill supported entities", snap.changeBillEntities.size());
        } catch (Exception e) {
            log.warn("Failed to load change-bill supported entities", e);
            snap.changeBillEntities = Collections.emptySet();
        }
        long tChangeBillEnd = System.currentTimeMillis();

        long tDetailStart = System.currentTimeMillis();
        loadDetailRelations(snap);
        long tDetailEnd = System.currentTimeMillis();

        long tWbStart = System.currentTimeMillis();
        writeBackRelationService.buildIndex(snap, snap.allRows);
        long tWbEnd = System.currentTimeMillis();

        long tExprStart = System.currentTimeMillis();
        expressionFieldService.buildIndex(snap, snap.allRows, snap.mainToDetails);
        long tExprEnd = System.currentTimeMillis();

        long tRefStart = System.currentTimeMillis();
        entityReferenceService.buildIndex(snap, snap.allRows);
        long tRefEnd = System.currentTimeMillis();

        long tEnumStart = System.currentTimeMillis();
        loadEnumDefinitions(snap);
        long tEnumEnd = System.currentTimeMillis();

        long tEnrichStart = System.currentTimeMillis();
        enrichFieldMetadata(snap);
        long tEnrichEnd = System.currentTimeMillis();

        long tEnumFieldStart = System.currentTimeMillis();
        buildEnumFieldIndex(snap);
        long tEnumFieldEnd = System.currentTimeMillis();

        // 此处不再调用 clearAnalysisCache：snap 是新建的，env().graphCache/env().explainCache 本就为空。
        // SkillsService 由于按 env scope 维护 cache，由其自身在调用时按 env 查找。

        long tEnd = System.currentTimeMillis();

        log.info(
                "[loadInto] env={}, total={}ms, selectAll={}ms, groupBy={}ms, loadViews={}ms, loadTitles={}ms, loadBillTypes={}ms, loadDetails={}ms, loadEnums={}ms, objects={}, fields={}, views={}, details={}, enums={}",
                snap.env,
                (tEnd - t0),
                (tSelectEnd - tSelectStart),
                (tGroupEnd - tGroupStart),
                (tViewsEnd - tViewsStart),
                (tTitleEnd - tTitleStart),
                (tBillEnd - tBillStart),
                (tDetailEnd - tDetailStart),
                (tEnumEnd - tEnumStart),
                snap.rowsByObject.size(), snap.allRows.size(), snap.viewReverseDeps.size(),
                snap.mainToDetails.size(), snap.enumValueMap.size());
    }

    /** 清除当前 env 的分析结果缓存。 */
    public void clearAnalysisCache() {
        EnvSnapshot snap = env();
        snap.graphCache.invalidateAll();
        snap.explainCache.invalidateAll();
        // 联动清除 SkillsService 缓存（@Lazy，首次 reload 前可能为 null）
        if (skillsService != null) {
            try {
                skillsService.clearCache();
            } catch (Exception ignored) {
            }
        }
        log.info("已清除分析结果缓存（env={}，含 SkillsService 缓存）", snap.env);
    }

    /** 获取当前 env 的缓存统计信息。 */
    public Map<String, Object> getCacheStats() {
        EnvSnapshot snap = env();
        Map<String, Object> stats = new HashMap<>();
        stats.put("env", snap.env);
        stats.put("graphCacheSize", snap.graphCache.size());
        stats.put("explainCacheSize", snap.explainCache.size());
        stats.put("graphCacheStats", snap.graphCache.stats().toString());
        stats.put("explainCacheStats", snap.explainCache.stats().toString());
        return stats;
    }

    /**
     * 生成缓存key
     */
    private String cacheKey(String objectType, String field, int depth, int relType, boolean includeUpstream) {
        return String.format("%s.%s.d%d.r%d.u%s", objectType, field, depth, relType, includeUpstream);
    }

    // 表名映射简单实现
    private String tableToObject(String table) {
        if (table == null)
            return null;
        String t = table.toLowerCase();
        // 如果包含 schema (如 public.table)，去掉 schema
        int lastDot = t.lastIndexOf('.');
        if (lastDot >= 0) {
            t = t.substring(lastDot + 1);
        }

        // 命名规范: appName_tableName，截取第一个下划线后的部分作为对象名
        int firstUnderscore = t.indexOf('_');
        if (firstUnderscore > 0 && firstUnderscore < t.length() - 1) {
            t = t.substring(firstUnderscore + 1);
        }
        String camelCase = ExprUtils.snakeToCamel(t); // 转为 camelCase
        if (camelCase == null || camelCase.isEmpty())
            return camelCase;
        // 首字母大写转换为 PascalCase
        return Character.toUpperCase(camelCase.charAt(0)) + camelCase.substring(1);
    }

    /**
     * 加载子表关系映射：从 source_info 中提取 isDetail=true 的 LIST 字段，
     * 构建 mainEntity → Set(detailEntity) 和 detailEntity → mainEntity 映射。
     */
    /** 用已加载的 ObjectType 元数据补全字段 appName（避免 ObjectField 行级 exprField 关联查询）。 */
    private void enrichFieldAppNamesFromObjectMetas(EnvSnapshot snap) {
        if (snap.allRows == null || snap.objectTypeMetas.isEmpty()) {
            return;
        }
        int enriched = 0;
        for (BaseappObjectField f : snap.allRows) {
            if (f.getObjectType() == null) {
                continue;
            }
            ObjectTypeMeta meta = snap.objectTypeMetas.get(f.getObjectType());
            if (meta != null && meta.getAppName() != null && !meta.getAppName().isBlank()) {
                f.setAppName(meta.getAppName());
                enriched++;
            }
        }
        log.info("Enriched appName on {} fields from object type metas", enriched);
    }

    private Set<String> deriveBillObjectTypes(EnvSnapshot snap) {
        Set<String> bills = new HashSet<>();
        for (ObjectTypeMeta m : snap.objectTypeMetas.values()) {
            if (m.getName() == null || m.getName().isBlank()) {
                continue;
            }
            if ("bill".equalsIgnoreCase(m.getType() != null ? m.getType().trim() : "")) {
                bills.add(m.getName().trim());
            }
        }
        return bills;
    }

    private void loadDetailRelations(EnvSnapshot snap) {
        snap.mainToDetails.clear();
        snap.detailToMain.clear();
        try {
            for (BaseappObjectField field : snap.allRows) {
                if (field.getSourceInfo() == null || field.getSourceInfo().isEmpty()) {
                    continue;
                }
                if (field.getType() == null || !"list".equalsIgnoreCase(field.getType().trim())) {
                    continue;
                }
                try {
                    JsonNode si = objectMapper.readTree(field.getSourceInfo());
                    boolean isDetail = si.has("isDetail") && si.get("isDetail").asBoolean(false);
                    String sourceEntityName = si.has("sourceEntityName") ? si.get("sourceEntityName").asText(null) : null;
                    if (isDetail && sourceEntityName != null && !sourceEntityName.isEmpty()) {
                        String mainEntity = field.getObjectType();
                        snap.mainToDetails.computeIfAbsent(mainEntity, k -> new LinkedHashSet<>()).add(sourceEntityName);
                        snap.detailToMain.put(sourceEntityName, mainEntity);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse source_info for {}.{}: {}", field.getObjectType(), field.getName(), e.getMessage());
                }
            }
            log.info("Loaded detail relations: {} main entities, {} detail entities",
                    snap.mainToDetails.size(), snap.detailToMain.size());
        } catch (Exception e) {
            log.warn("Failed to load detail relations", e);
        }
    }

    /**
     * 获取主表→子表列表映射（全量）。
     */
    public Map<String, Set<String>> getMainToDetails() {
        return Collections.unmodifiableMap(env().mainToDetails);
    }

    /**
     * 获取子表→主表映射（全量）。
     */
    public Map<String, String> getDetailToMain() {
        return Collections.unmodifiableMap(env().detailToMain);
    }

    /**
     * 判断指定实体是否支持变更单（isSupportChangeBill=true）。
     */
    public boolean isSupportChangeBill(String entityName) {
        return env().changeBillEntities.contains(entityName);
    }

    /**
     * 获取指定主表的所有子表（含递归子表，最多 3 层）。
     *
     * @param mainEntityName 主表对象名
     * @return 所有子表名称集合
     */
    public Set<String> getAllDetailEntities(String mainEntityName) {
        return collectDetails(mainEntityName, 0);
    }

    private Set<String> collectDetails(String entityName, int level) {
        Set<String> result = new LinkedHashSet<>();
        Set<String> directDetails = env().mainToDetails.get(entityName);
        if (directDetails == null || directDetails.isEmpty() || level >= 3) {
            return result;
        }
        for (String detail : directDetails) {
            result.add(detail);
            result.addAll(collectDetails(detail, level + 1));
        }
        return result;
    }

    /**
     * 从 baseapp_system_metadata 加载所有枚举定义，构建 enumName → Set(validValues) 映射。
     */
    private void loadEnumDefinitions(EnvSnapshot snap) {
        snap.enumValueMap.clear();
        snap.enumTypeIndex.clear();
        snap.enumTitleIndex.clear();
        try {
            List<String> enumJsonList = repository.selectEnumDefinitions();
            for (String json : enumJsonList) {
                try {
                    JsonNode root = objectMapper.readTree(json);
                    String enumName = root.path("name").asText();
                    if (enumName == null || enumName.isEmpty()) continue;

                    String enumTitle = root.path("title").asText(null);
                    String enumDesc = root.path("description").asText(null);

                    JsonNode valueDefs = root.path("enumValueDefs");
                    if (!valueDefs.isArray()) continue;

                    Set<String> values = new HashSet<>();
                    List<EnumValueMeta> valueMetas = new ArrayList<>();
                    for (JsonNode def : valueDefs) {
                        String val = def.path("value").asText();
                        if (val != null && !val.isEmpty()) {
                            values.add(val);
                            valueMetas.add(new EnumValueMeta(
                                    val,
                                    def.path("title").asText(null),
                                    def.has("ordinal") ? def.path("ordinal").asInt() : null,
                                    def.has("isDisabled") ? def.path("isDisabled").asBoolean(false) : null
                            ));
                        }
                    }
                    if (!values.isEmpty()) {
                        snap.enumValueMap.put(enumName, values);
                    }
                    EnumTypeMeta meta = new EnumTypeMeta(enumName, enumTitle, enumDesc);
                    meta.setValues(valueMetas);
                    snap.enumTypeIndex.put(enumName, meta);
                    if (enumTitle != null && !enumTitle.isEmpty()) {
                        snap.enumTitleIndex.computeIfAbsent(enumTitle, k -> new ArrayList<>()).add(enumName);
                    }
                } catch (Exception e) {
                    log.debug("解析枚举定义 JSON 失败，跳过: {}", e.getMessage());
                }
            }
            log.info("Loaded {} enum definitions, {} enum type metas",
                    snap.enumValueMap.size(), snap.enumTypeIndex.size());
        } catch (Exception e) {
            log.warn("Failed to load enum definitions from DB", e);
        }
    }

    /**
     * 获取枚举定义映射（供 ExpressionValidatorService 使用）
     */
    public Map<String, Set<String>> getEnumValueMap() {
        return env().enumValueMap;
    }

    /** 获取枚举类型索引（resolve 枚举搜索用） */
    public Map<String, EnumTypeMeta> getEnumTypeIndex() {
        return env().enumTypeIndex;
    }

    /** 获取枚举标题反向索引（中文标题 → 枚举名列表） */
    public Map<String, List<String>> getEnumTitleIndex() {
        return env().enumTitleIndex;
    }

    /** 获取枚举字段索引（枚举名 → 使用该枚举的字段列表） */
    public Map<String, List<String>> getEnumFieldIndex() {
        return env().enumFieldIndex;
    }

    /** 获取 ExpressionFieldService（resolve 依赖摘要用） */
    public ExpressionFieldService getExpressionFieldService() {
        return expressionFieldService;
    }

    /** 获取 EntityReferenceService（resolve 反向引用查询用） */
    public EntityReferenceService getEntityReferenceService() {
        return entityReferenceService;
    }

    /**
     * 从 baseapp_system_metadata 的 entity content JSON 中解析字段级属性，
     * 补充到已加载的 BaseappObjectField 上（description/enumType/isDisabled/isMasterField）。
     */
    /**
     * 从 content JSON 提取对象级特性到 ObjectTypeMeta。
     * 在 enrichFieldMetadata 之前调用，因为需要 content JSON 的根节点属性。
     */
    private void enrichObjectTraits(EnvSnapshot snap, List<Map<String, Object>> metaList) {
        int traitCount = 0;
        for (Map<String, Object> meta : metaList) {
            String entityName = (String) meta.get("name");
            String content = (String) meta.get("content");
            if (entityName == null || content == null) continue;

            ObjectTypeMeta otm = snap.objectTypeMetas.get(entityName);
            if (otm == null) continue;

            try {
                JsonNode root = objectMapper.readTree(content);
                // isTree/isDetail/isSupportChangeLog/isCustomizedEntity/isMultiDataVersion
                // 已从 baseapp_object_type 表直读（selectObjectTitles），此处仅补充表中没有的字段
                if (root.has("businessModuleId")) otm.setBusinessModuleId(root.path("businessModuleId").asText(null));
                traitCount++;
            } catch (Exception e) {
                log.debug("解析对象 {} 特性失败: {}", entityName, e.getMessage());
            }
        }
        log.info("Enriched {} object type traits from content JSON", traitCount);
    }

    /**
     * 构建 env().enumFieldIndex：遍历所有字段，将有 enumType 的字段注册到枚举字段索引。
     */
    private void buildEnumFieldIndex(EnvSnapshot snap) {
        snap.enumFieldIndex.clear();
        for (BaseappObjectField f : snap.allRows) {
            String enumType = f.getEnumType();
            if (enumType != null && !enumType.isEmpty()) {
                String fieldRef = f.getObjectType() + "." + f.getName();
                snap.enumFieldIndex.computeIfAbsent(enumType, k -> new ArrayList<>()).add(fieldRef);
            }
        }
        log.info("Built enum field index with {} enum types", snap.enumFieldIndex.size());
    }

    private void enrichFieldMetadata(EnvSnapshot snap) {
        try {
            List<Map<String, Object>> metaList = repository.selectEntityMetadataContents();
            try {
                List<Map<String, Object>> customizedList = repository.selectCustomizedMetadataContents();
                if (customizedList != null) {
                    metaList.addAll(customizedList);
                    log.info("Loaded {} customized metadata entries", customizedList.size());
                }
            } catch (Exception e) {
                log.warn("Failed to load customized metadata (table may not exist): {}", e.getMessage());
            }

            enrichObjectTraits(snap, metaList);
            int enrichedCount = 0;
            for (Map<String, Object> meta : metaList) {
                String entityName = (String) meta.get("name");
                String content = (String) meta.get("content");
                if (entityName == null || content == null) continue;

                List<BaseappObjectField> fields = snap.rowsByObject.get(entityName);
                if (fields == null || fields.isEmpty()) continue;

                // 构建 apiName → BaseappObjectField 的快速查找
                Map<String, BaseappObjectField> fieldMap = new HashMap<>();
                for (BaseappObjectField f : fields) {
                    String key = (f.getApiName() != null && !f.getApiName().isEmpty()) ? f.getApiName() : f.getName();
                    if (key != null && !key.isEmpty()) fieldMap.put(key, f);
                }

                try {
                    JsonNode root = objectMapper.readTree(content);
                    JsonNode fieldsNode = root.path("fields");
                    if (!fieldsNode.isArray()) continue;

                    for (JsonNode fn : fieldsNode) {
                        String apiName = fn.path("apiName").asText(null);
                        if (apiName == null) apiName = fn.path("name").asText(null);
                        if (apiName == null) continue;

                        BaseappObjectField bf = fieldMap.get(apiName);
                        if (bf == null) continue;

                        try {
                            bf.setMetadataJson(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(fn));
                        } catch (JsonProcessingException ignored) {
                            bf.setMetadataJson(fn.toString());
                        }

                        // 补充 description
                        String desc = fn.path("description").asText(null);
                        if (desc == null || desc.isEmpty()) desc = fn.path("desc").asText(null);
                        if (desc != null && !desc.isEmpty() && bf.getDescription() == null) {
                            bf.setDescription(desc);
                        }
                        // 补充 enumType（直接属性或 properties 子节点）
                        String enumType = fn.path("enumType").asText(null);
                        if (enumType == null || enumType.isEmpty()) {
                            enumType = fn.path("properties").path("enumType").asText(null);
                        }
                        if (enumType != null && !enumType.isEmpty() && bf.getEnumType() == null) {
                            bf.setEnumType(enumType);
                        }
                        // 补充 isDisabled
                        if (bf.getIsDisabled() == null && fn.has("isDisabled")) {
                            bf.setIsDisabled(fn.path("isDisabled").asBoolean(false));
                        }
                        // 补充 isMasterField
                        if (bf.getIsMasterField() == null && fn.has("isMasterField")) {
                            bf.setIsMasterField(fn.path("isMasterField").asBoolean(false));
                        }
                        enrichedCount++;
                    }
                } catch (Exception e) {
                    log.debug("解析实体 {} 元数据 JSON 失败: {}", entityName, e.getMessage());
                }
            }
            long enumCount = snap.allRows.stream().filter(f -> f.getEnumType() != null).count();
            log.info("Enriched {} field metadata entries from entity content JSON, {} fields with enumType", enrichedCount, enumCount);
        } catch (Exception e) {
            log.warn("Failed to enrich field metadata from system_metadata", e);
        }
    }

    private void loadViews(EnvSnapshot snap) {
        snap.viewReverseDeps.clear();
        snap.viewDirectDeps.clear();
        try {
            // 从数据库查询所有视图定义
            List<String> viewJsonList = repository.selectViewDefinitions();
            log.info("[视图加载] 从数据库查询到 {} 个视图定义", viewJsonList.size());

            if (viewJsonList.isEmpty()) {
                log.warn("[视图加载] 数据库中没有视图定义");
                return;
            }

            int totalViewsLoaded = 0;
            int totalSqlsParsed = 0;

            for (String viewJson : viewJsonList) {
                try {
                    JsonNode root = objectMapper.readTree(viewJson);
                    String viewName = root.path("name").asText();
                    if (viewName == null || viewName.isEmpty()) {
                        log.warn("[视图加载] 视图名称为空，跳过");
                        continue;
                    }

                    // log.info("[视图加载] 开始加载视图: {}", viewName);

                    List<BaseappObjectField> viewFields = new ArrayList<>();
                    JsonNode fields = root.path("fields");
                    if (fields.isArray()) {
                        for (JsonNode f : fields) {
                            BaseappObjectField bf = new BaseappObjectField();
                            bf.setObjectType(viewName);
                            bf.setName(f.path("name").asText());
                            bf.setType(f.path("type").asText());
                            bf.setTitle(f.path("title").asText());
                            bf.setExpression(optText(f, "expression"));
                            bf.setTriggerExpr(optText(f, "triggerExpr"));
                            bf.setVirtualExpr(optText(f, "virtualExpr"));
                            bf.setWriteBackExpr(optText(f, "writeBackExpr"));
                            viewFields.add(bf);
                        }
                    }
                    // log.info("[视图加载] 视图字段数量={}", viewFields.size());

                    if (!snap.rowsByObject.containsKey(viewName)) {
                        snap.rowsByObject.put(viewName, viewFields);
                    }
                    totalViewsLoaded++;

                    JsonNode viewDefs = root.path("viewDef");
                    if (viewDefs.isArray()) {
                        for (JsonNode def : viewDefs) {
                            String objectName = def.path("objectName").asText();
                            String sqlText = def.path("sql").asText();
                            parseSqlDependencies(snap, viewName, sqlText);
                            totalSqlsParsed++;
                        }
                    } else {
                        log.warn("[视图加载] viewDef 不是数组 - 视图名称: {}, viewDef类型: {}, viewDef值: {}",
                                viewName,
                                viewDefs.isMissingNode() ? "missing" : viewDefs.getNodeType().toString(),
                                viewDefs.isMissingNode() ? "null" : viewDefs.toString());
                    }
                } catch (Exception e) {
                    String viewNameInError = null;
                    try {
                        JsonNode root = objectMapper.readTree(viewJson);
                        viewNameInError = root.path("name").asText();
                    } catch (Exception ignored) {
                    }
                    log.error("[视图加载] 解析视图定义失败，跳过此视图 - 视图名称: {}, 错误: {}",
                            viewNameInError != null ? viewNameInError : "未知", e.getMessage(), e);
                }
            }

            int count = 0;
            for (Map.Entry<String, Set<String>> entry : snap.viewReverseDeps.entrySet()) {
                if (count++ < 5) {
                    // example
                }
            }
        } catch (Exception e) {
            log.error("加载视图定义失败", e);
        }
    }

    private void parseSqlDependencies(EnvSnapshot snap, String viewName, String sql) {
        if (sql == null || sql.isEmpty())
            return;

        Map<String, Map<String, Set<String>>> lineage = formulaParserService.extractColumnLineage(sql);

        int parsedCount = 0;

        for (Map.Entry<String, Map<String, Set<String>>> entry : lineage.entrySet()) {
            String targetSnake = entry.getKey();
            String targetCamel = ExprUtils.snakeToCamel(targetSnake);
            Map<String, Set<String>> sources = entry.getValue();

            for (Map.Entry<String, Set<String>> srcEntry : sources.entrySet()) {
                String tableName = srcEntry.getKey();
                if (tableName == null || "UNKNOWN".equals(tableName)) {
                    continue;
                }

                String srcObj = tableToObject(tableName);
                if (srcObj == null) {
                    continue;
                }

                for (String col : srcEntry.getValue()) {
                    String srcField = ExprUtils.snakeToCamel(col);
                    if (srcField == null)
                        continue;

                    String srcKey = srcObj + "." + srcField;
                    String tgtKey = viewName + "." + targetCamel;
                    snap.viewReverseDeps.computeIfAbsent(srcKey, k -> new HashSet<>()).add(tgtKey);

                    snap.viewDirectDeps.computeIfAbsent(tgtKey, k -> new HashSet<>()).add(srcKey);

                    parsedCount++;
                }
            }
        }
        // log.info("[视图解析] 视图={}, 解析出依赖关系总数={}", viewName, parsedCount);
    }

    private String canonicalFieldName(BaseappObjectField r) {
        if (r.getApiName() != null && !r.getApiName().trim().isEmpty())
            return r.getApiName().trim();
        // api_name 未填写时，把 name（数据库 snake_case）转 camelCase，确保图中节点 ID 统一
        if (r.getName() != null && !r.getName().trim().isEmpty()) {
            String n = r.getName().trim();
            return n.contains("_") ? ExprUtils.snakeToCamel(n) : n;
        }
        return null;
    }

    // 新增：获取字段元数据
    public BaseappObjectField getFieldInfo(String objectType, String fieldCamel) {
        List<BaseappObjectField> rows = env().rowsByObject.get(objectType);
        if (rows == null)
            return null;
        for (BaseappObjectField r : rows) {
            String camel = canonicalFieldName(r);
            if (camel != null && camel.equals(fieldCamel)) {
                return r;
            }
        }
        return null;
    }

    /**
     * 查询：某个字段作为“触发源”时，在当前对象内被它影响到的字段集合。
     * 换句话说：在同一对象中，哪些字段的 trigger/expression/virtualExpr 里引用了 targetFieldCamel。
     * 仅限当前对象，不跨对象。
     */
    public List<BaseappObjectField> getTriggerFieldsForTarget(String objectType, String targetFieldCamel) {
        if (objectType == null || targetFieldCamel == null) {
            log.warn("[getTriggerFieldsForTarget] 参数为空: objectType={}, targetFieldCamel={}", objectType,
                    targetFieldCamel);
            return Collections.emptyList();
        }

        // 使用已有的 intra 依赖分析：它的含义正好是
        // 给定 sourceFieldCamel，找出“内部触发”到的字段列表（同一对象内）
        List<Map.Entry<String, String>> deps = buildIntraDependencies(objectType, targetFieldCamel);
        log.info("[getTriggerFieldsForTarget] intra 依赖: objectType={}, sourceField={}, 命中条数={}",
                objectType, targetFieldCamel, deps.size());

        if (deps.isEmpty()) {
            return Collections.emptyList();
        }

        // 为当前对象构建一个 camelCase -> 字段行 的索引
        List<BaseappObjectField> rows = env().rowsByObject.getOrDefault(objectType,
                Collections.<BaseappObjectField>emptyList());
        Map<String, BaseappObjectField> byCamel = new HashMap<String, BaseappObjectField>();
        for (BaseappObjectField r : rows) {
            String camel = canonicalFieldName(r);
            if (camel != null && !camel.isEmpty()) {
                byCamel.put(camel, r);
            }
        }

        List<BaseappObjectField> result = new ArrayList<BaseappObjectField>();
        for (Map.Entry<String, String> e : deps) {
            String obj = e.getKey();
            String fldCamel = e.getValue();
            if (!objectType.equals(obj)) {
                // 按需求：仅限当前对象，忽略跨对象
                continue;
            }
            BaseappObjectField r = byCamel.get(fldCamel);
            if (r != null) {
                result.add(r);
                log.debug("[getTriggerFieldsForTarget] 命中字段: objectType={}, fieldCamel={}, name={}, title={}",
                        obj, fldCamel, r.getName(), r.getTitle());
            } else {
                log.debug("[getTriggerFieldsForTarget] 找不到字段行: objectType={}, fieldCamel={}", obj, fldCamel);
            }
        }

        log.info("[getTriggerFieldsForTarget] 最终返回 {} 个字段（仅当前对象）", result.size());
        return result;
    }

    /**
     * 查询：某个「目标对象」中，哪些字段是由指定「来源对象」写回/聚合而来。
     * 典型场景：在 ArContractSubjectMatterItem 中，找出由 RevenueConfirmationItem 写回的字段。
     */
    public List<BaseappObjectField> getFieldsImpactedBySourceObject(String targetObjectType, String sourceObjectType) {
        if (targetObjectType == null || sourceObjectType == null) {
            return Collections.emptyList();
        }
        List<BaseappObjectField> rows = env().rowsByObject.getOrDefault(targetObjectType, Collections.emptyList());
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<BaseappObjectField> result = new ArrayList<BaseappObjectField>();
        for (BaseappObjectField r : rows) {
            WriteBackExpr wb = parseWriteBack(r.getWriteBackExpr());
            if (wb == null)
                continue;
            if (sourceObjectType.equals(wb.getSrcObjectType())) {
                result.add(r);
            }
        }
        return result;
    }

    // ======== Cross-object mapping ========

    public static class CrossTargetSummary {
        public String targetObject;
        public int fieldCount;
    }

    /**
     * 视角一：给定来源对象（sourceObjectType），它作为 writeBack 源，影响到哪些目标对象。
     * 即：有哪些对象的字段定义中，writeBackExpr.srcObjectType = sourceObjectType。
     */
    public List<CrossTargetSummary> listTargetsBySource(String sourceObjectType) {
        if (sourceObjectType == null || sourceObjectType.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<CrossTargetSummary> out = new ArrayList<CrossTargetSummary>();
        for (String target : env().rowsByObject.keySet()) {
            List<BaseappObjectField> fields = getFieldsImpactedBySourceObject(target, sourceObjectType);
            if (fields != null && !fields.isEmpty()) {
                CrossTargetSummary s = new CrossTargetSummary();
                s.targetObject = target;
                s.fieldCount = fields.size();
                out.add(s);
            }
        }

        // 按关联字段数降序，再按对象名排序，便于前端展示
        out.sort((a, b) -> {
            if (a.fieldCount != b.fieldCount)
                return Integer.compare(b.fieldCount, a.fieldCount);
            return a.targetObject.compareTo(b.targetObject);
        });
        return out;
    }

    public static class CrossSourceSummary {
        public String sourceObject;
        public int fieldCount;
    }

    /**
     * 视角二（本需求）：给定目标对象（targetObjectType），它的字段定义中，
     * 引用了哪些来源对象（writeBackExpr.srcObjectType），以及每个来源对象涉及多少字段。
     */
    public List<CrossSourceSummary> listSourcesForTarget(String targetObjectType) {
        if (targetObjectType == null || targetObjectType.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<BaseappObjectField> rows = env().rowsByObject.getOrDefault(targetObjectType,
                Collections.<BaseappObjectField>emptyList());
        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (BaseappObjectField r : rows) {
            WriteBackExpr wb = parseWriteBack(r.getWriteBackExpr());
            if (wb == null)
                continue;
            String srcObj = wb.getSrcObjectType();
            if (srcObj == null || srcObj.trim().isEmpty())
                continue;
            counts.put(srcObj, counts.getOrDefault(srcObj, 0) + 1);
        }
        List<CrossSourceSummary> out = new ArrayList<CrossSourceSummary>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            CrossSourceSummary s = new CrossSourceSummary();
            s.sourceObject = e.getKey();
            s.fieldCount = e.getValue();
            out.add(s);
        }
        out.sort((a, b) -> {
            if (a.fieldCount != b.fieldCount)
                return Integer.compare(b.fieldCount, a.fieldCount);
            return a.sourceObject.compareTo(b.sourceObject);
        });
        return out;
    }

    /**
     * 获取对象中文标题映射（objectType → 中文标题）。
     */
    public Map<String, String> getObjectTitles() {
        return Collections.unmodifiableMap(env().objectTitles);
    }

    /**
     * 获取指定对象的字段列表。
     */
    public List<BaseappObjectField> getFieldsByObject(String objectType) {
        return env().rowsByObject.getOrDefault(objectType, Collections.emptyList());
    }

    /**
     * 获取对象类型元信息映射（objectType → ObjectTypeMeta）。
     */
    public Map<String, ObjectTypeMeta> getObjectTypeMetas() {
        return Collections.unmodifiableMap(env().objectTypeMetas);
    }

    /**
     * 获取中文标题到对象名的反向索引（title → List&lt;objectType&gt;）。
     */
    public Map<String, List<String>> getTitleToObjectTypes() {
        return Collections.unmodifiableMap(env().titleToObjectTypes);
    }

    /**
     * 获取全局同义词映射（objectType → 同义词列表）。
     */
    public Map<String, List<String>> getGlobalSynonyms() {
        return Collections.unmodifiableMap(GLOBAL_SYNONYMS);
    }

    // Meta APIs for Frontend
    public Set<String> getAllObjectTypes() {
        // 仅保留 baseapp_object_type 中 type='bill' 的对象；若配置为空则退回全部
        Set<String> all = new TreeSet<>(env().rowsByObject.keySet());
        if (env().billObjectTypes == null || env().billObjectTypes.isEmpty()) {
            return all;
        }
        Set<String> billOnly = all.stream()
                .filter(name -> name != null && env().billObjectTypes.contains(name))
                .collect(Collectors.toCollection(TreeSet::new));
        return billOnly.isEmpty() ? all : billOnly;
    }

    public List<Map<String, String>> getObjectDetails() {
        Set<String> types = getAllObjectTypes();
        List<Map<String, String>> result = new ArrayList<>();
        for (String type : types) {
            Map<String, String> map = new HashMap<>();
            map.put("name", type);
            map.put("title", env().objectTitles.getOrDefault(type, type)); // fallback to name
            result.add(map);
        }
        // Sort by name
        result.sort(Comparator.comparing(m -> m.get("name")));
        return result;
    }

    public List<String> getFieldsForObject(String objectType) {
        List<BaseappObjectField> rows = env().rowsByObject.get(objectType);
        if (rows == null)
            return Collections.emptyList();
        return rows.stream()
                .map(this::canonicalFieldName)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());
    }

    public List<BaseappObjectField> getFieldDetailsForObject(String objectType) {
        List<BaseappObjectField> rows = env().rowsByObject.get(objectType);
        if (rows == null)
            return Collections.emptyList();
        // Filter and return valid fields
        return rows.stream()
                .filter(r -> canonicalFieldName(r) != null)
                .sorted(Comparator.comparing(this::canonicalFieldName))
                .collect(Collectors.toList());
    }

    /**
     * 对象级健康度概览，用于前端模型健康面板。
     */
    public static class ObjectHealth {
        public String object;
        public int totalFields;
        public int formulaFields;
        public int referencedFields;
        public int deadFields;
        public int writeBackFields;
        public int writeBackNoDownstream;
        // 字段列表（用于穿透查看）
        public List<String> deadFieldList = new ArrayList<>();
        public List<String> writeBackNoDownstreamList = new ArrayList<>();
    }

    /**
     * 计算指定对象的字段健康度指标：
     * - totalFields: 字段总数
     * - formulaFields: 有任意公式（trigger/expression/virtual/writeBack）的字段数
     * - referencedFields: 被其他字段公式引用到的字段数
     * - deadFields: 无公式且从未被引用的“疑似废字段”数量
     * - writeBackFields: 有 writeBackExpr 的字段数
     * - writeBackNoDownstream: 有 writeBack 但从未被下游使用的字段数
     */
    public ObjectHealth getObjectHealth(String objectType) {
        List<BaseappObjectField> rows = env().rowsByObject.getOrDefault(objectType, Collections.emptyList());
        ObjectHealth h = new ObjectHealth();
        h.object = objectType;
        h.totalFields = rows.size();

        // 统计每个字段被引用次数（仅限本对象内）
        Map<String, Integer> refCount = new HashMap<String, Integer>();
        for (BaseappObjectField r : rows) {
            List<String> refs = collectCamelRefs(r);
            for (String f : refs) {
                refCount.put(f, refCount.getOrDefault(f, 0) + 1);
            }
        }

        for (BaseappObjectField r : rows) {
            String camel = canonicalFieldName(r);
            if (camel == null || camel.isEmpty())
                continue;

            boolean hasFormula = (r.getExpression() != null && !r.getExpression().trim().isEmpty())
                    || (r.getTriggerExpr() != null && !r.getTriggerExpr().trim().isEmpty())
                    || (r.getVirtualExpr() != null && !r.getVirtualExpr().trim().isEmpty())
                    || (r.getWriteBackExpr() != null && !r.getWriteBackExpr().trim().isEmpty());
            boolean referenced = refCount.getOrDefault(camel, 0) > 0;

            if (hasFormula)
                h.formulaFields++;
            if (referenced)
                h.referencedFields++;
            if (!hasFormula && !referenced) {
                h.deadFields++;
                h.deadFieldList.add(camel);
            }

            boolean hasWriteBack = r.getWriteBackExpr() != null && !r.getWriteBackExpr().trim().isEmpty();
            if (hasWriteBack) {
                h.writeBackFields++;
                if (!referenced) {
                    h.writeBackNoDownstream++;
                    h.writeBackNoDownstreamList.add(camel);
                }
            }
        }

        return h;
    }


    private void fillNodeMeta(GraphModels.Node node) {
        BaseappObjectField info = getFieldInfo(node.object, node.field);
        if (info != null) {
            node.title = info.getTitle();
            node.type = info.getType();
            node.bizType = info.getBizType();
            node.apiName = canonicalFieldName(info);
            node.expression = info.getExpression();
            node.triggerExpr = info.getTriggerExpr();
            node.virtualExpr = info.getVirtualExpr();
        }
    }

    private List<String> collectCamelRefs(BaseappObjectField row) {
        Set<String> refs = new HashSet<String>();
        if (row.getTriggerExpr() != null)
            refs.addAll(formulaParserService.extractCamelFields(row.getTriggerExpr()));
        if (row.getExpression() != null)
            refs.addAll(formulaParserService.extractCamelFields(row.getExpression()));
        if (row.getVirtualExpr() != null)
            refs.addAll(formulaParserService.extractCamelFields(row.getVirtualExpr()));
        return new ArrayList<String>(refs);
    }

    /**
     * 收集某个字段在 trigger/expression/virtualExpr 中引用到的字段序列（按出现顺序）。
     */
    private List<String> collectCamelRefSequence(BaseappObjectField row) {
        StringBuilder sb = new StringBuilder();
        if (row.getTriggerExpr() != null)
            sb.append(' ').append(row.getTriggerExpr());
        if (row.getExpression() != null)
            sb.append(' ').append(row.getExpression());
        if (row.getVirtualExpr() != null)
            sb.append(' ').append(row.getVirtualExpr());
        return ExprUtils.extractCamelFieldSequence(sb.toString());
    }

    private boolean writebackHitsCurrentObject(WriteBackExpr wb, String currentObject) {
        if (wb == null)
            return false;
        if (currentObject.equals(wb.getSrcObjectType()))
            return true;
        String cond = wb.getCondition();
        if (cond != null && cond.contains("srcItemObjectType='" + currentObject + "'"))
            return true;
        return false;
    }

    private static String optText(JsonNode n, String field) {
        if (n == null)
            return null;
        JsonNode v = n.get(field);
        return (v != null && !v.isNull()) ? v.asText() : null;
    }

    public WriteBackExpr parseWriteBack(String text) {
        if (text == null || text.trim().isEmpty())
            return null;
        String raw = text.trim();
        try {
            WriteBackExpr wb = objectMapper.readValue(raw, WriteBackExpr.class);
            if (wb != null && wb.getSrcObjectType() != null && wb.getExpression() != null) {
                return wb;
            }
        } catch (Exception ignored) {
        }
        try {
            JsonNode node;
            try {
                node = objectMapper.readTree(raw);
            } catch (Exception e) {
                node = objectMapper.readTree(raw.replace('\'', '"'));
            }
            if (node == null)
                return null;
            java.util.function.Function<JsonNode, WriteBackExpr> pick = (jn) -> {
                String src = optText(jn, "srcObjectType");
                String expr = optText(jn, "expression");
                String cond = optText(jn, "condition");
                if (src != null && expr != null) {
                    WriteBackExpr wb = new WriteBackExpr();
                    wb.setSrcObjectType(src);
                    wb.setExpression(expr);
                    wb.setCondition(cond);
                    wb.setIdField(optText(jn, "idField"));
                    wb.setExecutingMoment(optText(jn, "executingMoment"));
                    wb.setValidateExpr(optText(jn, "validateExpr"));
                    wb.setValidateMessage(optText(jn, "validateMessage"));
                    return wb;
                }
                return null;
            };
            if (node.isArray()) {
                for (JsonNode it : node) {
                    WriteBackExpr wb = pick.apply(it);
                    if (wb != null)
                        return wb;
                }
                return null;
            } else {
                return pick.apply(node);
            }
        } catch (Exception e) {
            log.debug("writeBackExpr 解析失败，原始: {}", text);
            return null;
        }
    }

    private String extractRefFromReferInfo(String referInfo) {
        if (referInfo == null || referInfo.trim().isEmpty())
            return null;
        try {
            JsonNode node = objectMapper.readTree(referInfo);
            JsonNode entities = node.get("referEntities");
            if (entities != null && entities.isArray() && entities.size() > 0) {
                return optText(entities.get(0), "referEntityName");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 构建当前对象内「trigger 聚合字段」到其组成字段集合的映射。
     *
     * <p>
     * 例如：<code>makeInvoiceAmount</code> 的 triggerExpr 为
     * <code>make_invoice_amount_blue + make_invoice_amount_red + red_app_make_invoice_amount</code>，
     * 则会记录一条：
     *
     * <pre>
     *   makeInvoiceAmount -> { makeInvoiceAmountBlue, makeInvoiceAmountRed, redAppMakeInvoiceAmount }
     * </pre>
     *
     * 这样，当后续把 <code>makeInvoiceAmount</code> 作为「触发源」查询时，可以认为凡是引用了这些组成字段的公式，
     * 也等价于被 <code>makeInvoiceAmount</code> 影响到。
     */
    private Map<String, List<String>> buildTriggerAliasMap(String objectType) {
        List<BaseappObjectField> rows = env().rowsByObject.getOrDefault(objectType,
                Collections.<BaseappObjectField>emptyList());
        Map<String, List<String>> aliasMap = new HashMap<String, List<String>>();
        for (BaseappObjectField r : rows) {
            String triggerExpr = r.getTriggerExpr();
            if (triggerExpr == null || triggerExpr.trim().isEmpty())
                continue;
            String rootCamel = canonicalFieldName(r);
            if (rootCamel == null || rootCamel.isEmpty())
                continue;
            // 解析 trigger 表达式中的字段序列（按出现顺序）
            List<String> seq = ExprUtils.extractCamelFieldSequence(triggerExpr);
            if (seq == null || seq.isEmpty())
                continue;
            // 自身不算在组成字段里
            seq.removeIf(f -> f.equals(rootCamel));
            if (seq.isEmpty())
                continue;
            aliasMap.put(rootCamel, new ArrayList<String>(seq));
        }
        return aliasMap;
    }

    /**
     * 判断 needle 是否为 haystack 的一个「连续子序列」（顺序一致且中间不被其他字段打断）。
     */
    private boolean containsSubsequence(List<String> haystack, List<String> needle) {
        if (haystack == null || needle == null)
            return false;
        int n = haystack.size(), m = needle.size();
        if (m == 0 || n < m)
            return false;
        for (int i = 0; i <= n - m; i++) {
            boolean ok = true;
            for (int j = 0; j < m; j++) {
                if (!needle.get(j).equals(haystack.get(i + j))) {
                    ok = false;
                    break;
                }
            }
            if (ok)
                return true;
        }
        return false;
    }

    /**
     * intra 依赖：给定 sourceFieldCamel（同一对象内的某个字段），找出所有
     * 公式中引用了该字段（或其 trigger 聚合等价字段集合）的「被影响字段」列表。
     *
     * <p>
     * 特殊处理 trigger 聚合字段：如果 sourceFieldCamel 恰好是一个 trigger 聚合字段，
     * 那么凡是引用了其组成字段（例如 make_invoice_amount_blue 等）的公式，也会被认为
     * 是由 sourceFieldCamel 触发，从而补上「等价关系」这层语义。
     */
    private List<Map.Entry<String, String>> buildIntraDependencies(String objectType, String sourceFieldCamel) {
        List<BaseappObjectField> rows = env().rowsByObject.getOrDefault(objectType,
                Collections.<BaseappObjectField>emptyList());
        List<Map.Entry<String, String>> out = new ArrayList<Map.Entry<String, String>>();

        // 预先解析当前对象中所有 trigger 聚合字段的「等价组成字段序列」
        Map<String, List<String>> triggerAliasSeqMap = buildTriggerAliasMap(objectType);
        List<String> aliasSeq = triggerAliasSeqMap.get(sourceFieldCamel);

        for (BaseappObjectField r : rows) {
            String targetCamel = canonicalFieldName(r);
            if (targetCamel == null)
                continue;
            if (targetCamel.equals(sourceFieldCamel))
                continue;
            List<String> seq = collectCamelRefSequence(r);

            boolean directRef = seq.contains(sourceFieldCamel);
            // 对于 trigger 聚合字段：只有在「组成字段序列连续出现」时，才认为等价依赖成立
            boolean aliasRef = aliasSeq != null
                    && !aliasSeq.isEmpty()
                    && containsSubsequence(seq, aliasSeq);

            if (directRef || aliasRef) {
                out.add(new AbstractMap.SimpleEntry<String, String>(objectType, targetCamel));
            }
        }
        // log.info("intra: {}.{} -> {} 条", objectType, sourceFieldCamel, out.size());
        return out;
    }

    private List<String> buildIntraUpstreamDependencies(String objectType, String targetFieldCamel) {
        List<BaseappObjectField> rows = env().rowsByObject.getOrDefault(objectType,
                Collections.<BaseappObjectField>emptyList());
        for (BaseappObjectField r : rows) {
            // 尝试多种匹配方式：apiName (camelCase) 或 name (snake_case 转 camelCase)
            String apiName = r.getApiName();
            String name = r.getName();
            boolean matched = false;

            if (apiName != null && !apiName.trim().isEmpty() && apiName.trim().equals(targetFieldCamel)) {
                matched = true;
            } else if (name != null && !name.trim().isEmpty()) {
                // name 可能是 snake_case，转成 camelCase 再比较
                String nameCamel = ExprUtils.snakeToCamel(name.trim());
                if (nameCamel != null && nameCamel.equals(targetFieldCamel)) {
                    matched = true;
                } else if (name.trim().equals(targetFieldCamel)) {
                    matched = true;
                }
            }

            if (matched) {
                Set<String> refs = new HashSet<String>();
                if (r.getTriggerExpr() != null)
                    refs.addAll(formulaParserService.extractCamelFields(r.getTriggerExpr()));
                if (r.getExpression() != null)
                    refs.addAll(formulaParserService.extractCamelFields(r.getExpression()));
                if (r.getVirtualExpr() != null)
                    refs.addAll(formulaParserService.extractCamelFields(r.getVirtualExpr()));
                refs.remove(targetFieldCamel);
                List<String> list = new ArrayList<String>(refs);
                // log.info("upstream: {}.{} <- {} 条, 字段: {}", objectType, targetFieldCamel,
                // list.size(), list);
                return list;
            }
        }
        return Collections.<String>emptyList();
    }

    /**
     * 获取指定字段的直接上游边（一层）：intra 同对象依赖 + writeBack 回写来源。
     * 用于多根升级脚本时闭合依赖图。
     */
    public List<GraphModels.Edge> getDirectUpstreamEdges(String objectType, String fieldCamel,
            boolean includeIntra, boolean includeWriteBack) {
        List<GraphModels.Edge> out = new ArrayList<>();
        String targetId = objectType + "." + fieldCamel;

        if (includeIntra) {
            List<String> upstream = buildIntraUpstreamDependencies(objectType, fieldCamel);
            for (String upstreamFld : upstream) {
                String srcId = objectType + "." + upstreamFld;
                out.add(new GraphModels.Edge(srcId, targetId, "intra"));
            }
        }

        if (includeWriteBack) {
            BaseappObjectField def = getFieldInfo(objectType, fieldCamel);
            if (def != null) {
                WriteBackExpr wb = parseWriteBack(def.getWriteBackExpr());
                if (wb != null && wb.getSrcObjectType() != null) {
                    String srcObj = wb.getSrcObjectType();
                    Set<String> srcFields = ExprUtils.extractCamelFieldsFromSql(wb.getExpression());
                    for (String srcFld : srcFields) {
                        // 统一转为 camelCase，避免表达式中 snake_case 字段名产生"幽灵节点"
                        String camelFld = srcFld.contains("_") ? ExprUtils.snakeToCamel(srcFld) : srcFld;
                        String srcId = srcObj + "." + camelFld;
                        out.add(new GraphModels.Edge(srcId, targetId, "writeBack"));
                    }
                }
            }
        }

        return out;
    }

    /**
     * 多根合并图并闭合上游：先合并各根的下游图，再对图中每个节点补充直接上游（intra + writeBack），
     * 递归直到无新节点，得到完整 DAG 用于拓扑排序生成脚本顺序。
     */
    public GraphModels.Graph buildMultiRootClosedGraph(List<Map.Entry<String, String>> roots, int depth, int relType) {
        // relType 4 = intra+writeBack only (exclude view)
        boolean includeWriteBack = (relType == 0 || relType == 1 || relType == 4);
        boolean includeIntra = (relType == 0 || relType == 2 || relType == 4);

        GraphModels.Graph merged = new GraphModels.Graph();
        Set<String> nodeIds = new HashSet<>();
        Set<String> edgeKeys = new HashSet<>();

        for (Map.Entry<String, String> root : roots) {
            String obj = root.getKey();
            String fld = root.getValue();
            if (obj == null || fld == null)
                continue;
            GraphModels.Graph g = analyzeInternal(obj, fld, depth, relType, false);
            for (GraphModels.Node n : g.nodes) {
                String id = n.id != null ? n.id : (n.object + "." + n.field);
                if (nodeIds.add(id)) {
                    merged.nodes.add(n);
                }
            }
            for (GraphModels.Edge e : g.edges) {
                String key = e.source + "|" + e.type + "|" + e.target;
                if (edgeKeys.add(key)) {
                    merged.edges.add(e);
                }
            }
        }

        // 闭合：对图中每个节点补充直接上游，直到无新节点（有界迭代防环）
        int maxRounds = Math.max(500, nodeIds.size() * 2);
        for (int round = 0; round < maxRounds; round++) {
            List<String> toProcess = new ArrayList<>(nodeIds);
            int added = 0;
            for (String nodeId : toProcess) {
                int dot = nodeId.indexOf('.');
                if (dot <= 0)
                    continue;
                String obj = nodeId.substring(0, dot);
                String fld = nodeId.substring(dot + 1);
                for (GraphModels.Edge e : getDirectUpstreamEdges(obj, fld, includeIntra, includeWriteBack)) {
                    String key = e.source + "|" + e.type + "|" + e.target;
                    if (edgeKeys.add(key)) {
                        merged.edges.add(e);
                        added++;
                    }
                    if (nodeIds.add(e.source)) {
                        String so = e.source.substring(0, e.source.indexOf('.'));
                        String sf = e.source.substring(e.source.indexOf('.') + 1);
                        GraphModels.Node up = new GraphModels.Node(so, sf);
                        fillNodeMeta(up);
                        merged.nodes.add(up);
                        added++;
                    }
                }
            }
            if (added == 0)
                break;
        }

        return merged;
    }

    private List<Map.Entry<String, String>> buildCrossObjectDependencies(String objectType, String sourceFieldCamel) {
        List<Map.Entry<String, String>> out = new ArrayList<Map.Entry<String, String>>();
        int scanned = 0;
        for (BaseappObjectField r : env().allRows) {
            scanned++;
            WriteBackExpr wb = parseWriteBack(r.getWriteBackExpr());
            if (!writebackHitsCurrentObject(wb, objectType))
                continue;
            String expr = (wb != null) ? wb.getExpression() : null;
            Set<String> refs = formulaParserService.extractCamelFields(expr);
            if (refs.contains(sourceFieldCamel)) {
                String dstObj = r.getObjectType();
                if (objectType.equals(dstObj))
                    continue;
                String dstFld = canonicalFieldName(r);
                if (dstFld != null)
                    out.add(new AbstractMap.SimpleEntry<String, String>(dstObj, dstFld));
            }
        }
        // log.info("writeBack: candidates(scanned)={}, hitEdges={}", scanned,
        // out.size());
        return out;
    }

    private boolean addEdgeIfAbsent(List<GraphModels.Edge> edges, Set<String> edgeSet, String src, String dst,
            String type) {
        String key = src + "|" + type + "|" + dst;
        if (edgeSet.contains(key))
            return false;
        edgeSet.add(key);
        edges.add(new GraphModels.Edge(src, dst, type));
        return true;
    }

    public GraphModels.Graph analyze(String objectType, String fieldCamel, int depth, int relType) {
        // 兼容旧调用：默认包含上游展开
        return analyze(objectType, fieldCamel, depth, relType, true);
    }

    /**
     * 分析依赖图
     * 
     * @param includeUpstream 是否包含上游展开（上游 -> 当前，再继续向外扩）。
     *                        当为 false 时，只走从根出发的下游影响链，统计口径会与 explain 页面一致。
     */
    public GraphModels.Graph analyze(String objectType, String fieldCamel, int depth, int relType,
            boolean includeUpstream) {
        // 先检查缓存
        String cacheKey = cacheKey(objectType, fieldCamel, depth, relType, includeUpstream);
        try {
            GraphModels.Graph cached = env().graphCache.get(cacheKey, () -> {
                return analyzeInternal(objectType, fieldCamel, depth, relType, includeUpstream);
            });
            log.info("从缓存获取分析结果: {}", cacheKey);
            return cached;
        } catch (ExecutionException e) {
            log.error("缓存获取失败，直接执行分析", e);
            return analyzeInternal(objectType, fieldCamel, depth, relType, includeUpstream);
        }
    }

    /**
     * 内部分析方法（实际执行分析逻辑）
     */
    private GraphModels.Graph analyzeInternal(String objectType, String fieldCamel, int depth, int relType,
            boolean includeUpstream) {
        // relType: 0=全部, 1=writeBack, 2=intra, 3=view, 4=intra+writeBack(不含view)
        boolean includeWriteBack = (relType == 0 || relType == 1 || relType == 4);
        boolean includeIntra = (relType == 0 || relType == 2 || relType == 4);
        boolean includeView = (relType == 0 || relType == 3); // 4 排除 view

        // log.info("analyze start object={}, field={}, depth={}, includeWB={},
        // includeIntra={}, includeView={}", objectType, fieldCamel, depth,
        // includeWriteBack, includeIntra, includeView);
        GraphModels.Graph g = new GraphModels.Graph();
        Set<String> nodeSet = new HashSet<String>();
        List<GraphModels.Edge> edges = g.edges;
        Set<String> edgeSet = new HashSet<String>();

        Deque<String> q = new ArrayDeque<String>();
        Map<String, Integer> level = new HashMap<String, Integer>();

        String startId = objectType + "." + fieldCamel;
        q.offer(startId);
        level.put(startId, 0);
        nodeSet.add(startId);
        GraphModels.Node startNode = new GraphModels.Node(objectType, fieldCamel);
        fillNodeMeta(startNode);
        g.nodes.add(startNode);

        while (!q.isEmpty()) {
            String cur = q.poll();
            int d = level.get(cur);
            if (d >= depth)
                continue;
            int dot = cur.indexOf('.');
            String obj = cur.substring(0, dot);
            String fld = cur.substring(dot + 1);

            // 分支逻辑：如果是上游追溯模式，只查“谁影响了我”
            // 如果是下游影响模式，只查“我影响了谁”
            if (includeUpstream) {
                // =========== 上游溯源模式 (Reverse Lineage) ===========

                // 1. Intra Upstream: 同一对象内，哪些字段通过 Trigger/Expr 引用了我
                List<String> upstream = buildIntraUpstreamDependencies(obj, fld);
                if (includeIntra) {
                    for (String upstreamFld : upstream) {
                        String uId = obj + "." + upstreamFld;
                        addEdgeIfAbsent(edges, edgeSet, uId, cur, "intra"); // Source(Upstream) -> Me
                        if (!nodeSet.contains(uId)) {
                            nodeSet.add(uId);
                            GraphModels.Node n = new GraphModels.Node(obj, upstreamFld);
                            fillNodeMeta(n);
                            g.nodes.add(n);
                            q.offer(uId);
                            level.put(uId, d + 1);
                        }
                    }

                    // T006: 跨对象 triggerExpr 依赖（foreignKey.fieldName 格式）
                    BaseappObjectField fieldInfo = getFieldInfo(obj, fld);
                    if (fieldInfo != null && fieldInfo.getTriggerExpr() != null) {
                        Map<String, String> crossRefs = ExprUtils.extractCrossObjectRefs(fieldInfo.getTriggerExpr());
                        if (!crossRefs.isEmpty()) {
                            log.debug("[TriggerParse] object={}, field={}, deps={}, crossObjectDeps={}",
                                    obj, fld, upstream, crossRefs);
                        }
                        for (Map.Entry<String, String> ref : crossRefs.entrySet()) {
                            String fkField = ref.getKey();    // e.g. projectId
                            String refField = ref.getValue();  // e.g. projectName
                            // 从外键字段的 refObjectType 元数据中获取目标对象
                            BaseappObjectField fkFieldInfo = getFieldInfo(obj, fkField);
                            if (fkFieldInfo != null && fkFieldInfo.getRefObjectType() != null
                                    && !fkFieldInfo.getRefObjectType().trim().isEmpty()) {
                                String refObj = fkFieldInfo.getRefObjectType().trim();
                                String crossId = refObj + "." + refField;
                                addEdgeIfAbsent(edges, edgeSet, crossId, cur, "intra");
                                if (!nodeSet.contains(crossId)) {
                                    nodeSet.add(crossId);
                                    GraphModels.Node cn = new GraphModels.Node(refObj, refField);
                                    fillNodeMeta(cn);
                                    g.nodes.add(cn);
                                    q.offer(crossId);
                                    level.put(crossId, d + 1);
                                }
                            }
                        }
                    }
                }

                // 2. WriteBack Upstream: 谁回写了我 (Source -> Me)
                // 检查当前字段是否有 writeBackExpr
                if (includeWriteBack) {
                    BaseappObjectField currentFieldInfo = getFieldInfo(obj, fld);
                    if (currentFieldInfo != null) {
                        WriteBackExpr wb = parseWriteBack(currentFieldInfo.getWriteBackExpr());
                        if (wb != null && wb.getSrcObjectType() != null) {
                            String srcObj = wb.getSrcObjectType();
                            Set<String> srcFields = ExprUtils.extractCamelFieldsFromSql(wb.getExpression());
                            // T011: 从 condition 中提取引用的过滤字段，合并到依赖集合
                            if (wb.getCondition() != null && !wb.getCondition().trim().isEmpty()) {
                                srcFields.addAll(ExprUtils.extractCamelFieldsFromSql(wb.getCondition()));
                            }
                            String moment = wb.getExecutingMoment();
                            for (String srcFld : srcFields) {
                                String uId = srcObj + "." + srcFld;
                                if (addEdgeIfAbsent(edges, edgeSet, uId, cur, "writeBack")) {
                                    // 设置回写时机到最后添加的边
                                    GraphModels.Edge lastEdge = edges.get(edges.size() - 1);
                                    lastEdge.executingMoment = moment;
                                }
                                if (!nodeSet.contains(uId)) {
                                    nodeSet.add(uId);
                                    GraphModels.Node n = new GraphModels.Node(srcObj, srcFld);
                                    fillNodeMeta(n);
                                    g.nodes.add(n);
                                    q.offer(uId);
                                    level.put(uId, d + 1);
                                }
                            }
                        }
                    }
                }

                // 3. View Upstream: 我是视图字段，我来自哪里 (Source -> Me)
                if (includeView) {
                    Set<String> upstreamSources = env().viewDirectDeps.get(cur); // cur is View.Field
                    if (upstreamSources != null) {
                        for (String srcId : upstreamSources) {
                            addEdgeIfAbsent(edges, edgeSet, srcId, cur, "view"); // Source -> Me
                            if (!nodeSet.contains(srcId)) {
                                nodeSet.add(srcId);
                                int dotS = srcId.indexOf('.');
                                GraphModels.Node n = new GraphModels.Node(srcId.substring(0, dotS),
                                        srcId.substring(dotS + 1));
                                fillNodeMeta(n);
                                g.nodes.add(n);
                                q.offer(srcId);
                                level.put(srcId, d + 1);
                            }
                        }
                    }
                }

            } else {
                // =========== 下游影响模式 (Forward Impact) ===========

                if (includeIntra) {
                    for (Map.Entry<String, String> e : buildIntraDependencies(obj, fld)) {
                        String nid = e.getKey() + "." + e.getValue();
                        addEdgeIfAbsent(edges, edgeSet, cur, nid, "intra");
                        if (!nodeSet.contains(nid)) {
                            nodeSet.add(nid);
                            GraphModels.Node n = new GraphModels.Node(e.getKey(), e.getValue());
                            fillNodeMeta(n);
                            g.nodes.add(n);
                            q.offer(nid);
                            level.put(nid, d + 1);
                        }
                    }
                }
                if (includeWriteBack) {
                    for (Map.Entry<String, String> e : buildCrossObjectDependencies(obj, fld)) {
                        String nid = e.getKey() + "." + e.getValue();
                        if (addEdgeIfAbsent(edges, edgeSet, cur, nid, "writeBack")) {
                            // 从目标字段的 writeBackExpr 中读取 executingMoment
                            BaseappObjectField targetField = getFieldInfo(e.getKey(), e.getValue());
                            if (targetField != null) {
                                WriteBackExpr wbTarget = parseWriteBack(targetField.getWriteBackExpr());
                                if (wbTarget != null) {
                                    edges.get(edges.size() - 1).executingMoment = wbTarget.getExecutingMoment();
                                }
                            }
                        }
                        if (!nodeSet.contains(nid)) {
                            nodeSet.add(nid);
                            GraphModels.Node n = new GraphModels.Node(e.getKey(), e.getValue());
                            fillNodeMeta(n);
                            g.nodes.add(n);
                            q.offer(nid);
                            level.put(nid, d + 1);
                        }
                    }
                }
                if (includeView) {
                    // 查找受当前字段影响的视图字段
                    Set<String> views = env().viewReverseDeps.get(cur);
                    if (views != null) {
                        for (String viewId : views) {
                            addEdgeIfAbsent(edges, edgeSet, cur, viewId, "view");
                            if (!nodeSet.contains(viewId)) {
                                nodeSet.add(viewId);
                                int dotV = viewId.indexOf('.');
                                GraphModels.Node n = new GraphModels.Node(viewId.substring(0, dotV),
                                        viewId.substring(dotV + 1));
                                fillNodeMeta(n);
                                g.nodes.add(n);
                                q.offer(viewId);
                                level.put(viewId, d + 1);
                            }
                        }
                    }
                }
            }
        }
        // log.info("analyze finish nodes={}, edges={}", g.nodes.size(),
        // g.edges.size());
        return g;
    }

    // ===== 解释：按对象分组列出受影响字段及推导路径 =====
    public GraphModels.ExplainResponse explain(String objectType, String fieldCamel, int depth, int relType,
            boolean includeUpstream) {
        // 检查解释结果缓存
        String cacheKey = cacheKey(objectType, fieldCamel, depth, relType, includeUpstream) + ".explain";
        try {
            GraphModels.ExplainResponse cached = env().explainCache.get(cacheKey, () -> {
                return explainInternal(objectType, fieldCamel, depth, relType, includeUpstream);
            });
            // log.info("从缓存获取解释结果: {}", cacheKey);
            return cached;
        } catch (ExecutionException e) {
            log.error("缓存获取失败，直接执行解释", e);
            return explainInternal(objectType, fieldCamel, depth, relType, includeUpstream);
        }
    }

    /**
     * 内部解释方法
     */
    private GraphModels.ExplainResponse explainInternal(String objectType, String fieldCamel, int depth, int relType,
            boolean includeUpstream) {
        GraphModels.Graph g = analyzeInternal(objectType, fieldCamel, depth, relType, includeUpstream);
        // 建立邻接与反向索引
        Map<String, List<GraphModels.Edge>> outEdges = new LinkedHashMap<String, List<GraphModels.Edge>>();
        Map<String, List<GraphModels.Edge>> inEdges = new LinkedHashMap<String, List<GraphModels.Edge>>();
        for (GraphModels.Edge e : g.edges) {
            outEdges.computeIfAbsent(e.source, k -> new ArrayList<GraphModels.Edge>()).add(e);
            inEdges.computeIfAbsent(e.target, k -> new ArrayList<GraphModels.Edge>()).add(e);
        }
        String root = objectType + "." + fieldCamel;
        // 反向 BFS 求最短路径树（从每个节点回溯到 root）
        Deque<String> q = new ArrayDeque<String>();
        q.offer(root);
        Map<String, String> prev = new HashMap<String, String>(); // prev[v] = u 表示 u -> v
        Map<String, GraphModels.Edge> prevEdge = new HashMap<String, GraphModels.Edge>();
        Set<String> visited = new HashSet<String>();
        visited.add(root);
        while (!q.isEmpty()) {
            String u = q.poll();
            // Upstream模式下，我们要从 Subject(u) 往 Source(v) 找
            // 图中边是 v -> u，所以要看入边
            List<GraphModels.Edge> nextEdges = includeUpstream
                    ? inEdges.getOrDefault(u, Collections.<GraphModels.Edge>emptyList())
                    : outEdges.getOrDefault(u, Collections.<GraphModels.Edge>emptyList());

            for (GraphModels.Edge e : nextEdges) {
                // 如果是 Upstream (v->u)，u是target，v是source
                // 如果是 Downstream (u->v)，u是source，v是target
                String v = includeUpstream ? e.source : e.target;
                if (!visited.contains(v)) {
                    visited.add(v);
                    prev.put(v, u);
                    prevEdge.put(v, e);
                    q.offer(v);
                }
            }
        }
        // 将终点按对象分组（排除 root 本身）
        Map<String, GraphModels.ExplainGroup> groups = new LinkedHashMap<String, GraphModels.ExplainGroup>();
        for (GraphModels.Node n : g.nodes) {
            if ((n.object + "." + n.field).equals(root))
                continue;
            // 只收录能从 root 到达的节点
            if (!prev.containsKey(n.object + "." + n.field) && !root.equals(n.object + "." + n.field)) {
                // 若没有直接 prev，但也可能与 root 同层（无边），此类不纳入解释
                continue;
            }
            GraphModels.ExplainGroup grp = groups.get(n.object);
            if (grp == null) {
                grp = new GraphModels.ExplainGroup();
                grp.object = n.object;
                groups.put(n.object, grp);
            }
            // 还原路径
            List<GraphModels.ExplainStep> steps = new ArrayList<GraphModels.ExplainStep>();
            String cur = n.object + "." + n.field;
            while (prev.containsKey(cur)) {
                GraphModels.Edge e = prevEdge.get(cur);
                String src = e.source, dst = e.target;
                String reason;
                if ("intra".equals(e.type)) {
                    // 取目标字段的表达式，说明其依赖来源（避免与目标字段重复展示）
                    int dot = dst.indexOf('.');
                    String obj = dst.substring(0, dot);
                    String fld = dst.substring(dot + 1);
                    BaseappObjectField info = getFieldInfo(obj, fld);
                    String expr = info != null
                            ? (info.getTriggerExpr() != null ? info.getTriggerExpr()
                                    : (info.getExpression() != null ? info.getExpression() : info.getVirtualExpr()))
                            : null;
                    String srcField = src.substring(src.indexOf('.') + 1);
                    reason = expr != null ? ("由表达式计算，包含 " + srcField) : ("依赖 " + srcField);
                } else if ("view".equals(e.type)) {
                    reason = "视图映射: " + src + " -> " + dst;
                } else {
                    // writeBack：根据写回表达式（避免与目标字段重复展示）
                    int dot = dst.indexOf('.');
                    String obj = dst.substring(0, dot);
                    String fld = dst.substring(dot + 1);
                    BaseappObjectField info = getFieldInfo(obj, fld);
                    String raw = info != null ? info.getWriteBackExpr() : null;
                    WriteBackExpr wb = raw != null ? parseWriteBack(raw) : null;
                    String expr = wb != null ? wb.getExpression() : null;
                    String srcField = src.substring(src.indexOf('.') + 1);
                    reason = (expr != null ? ("由回写表达式聚合/计算，包含 " + srcField) : ("回写依赖 " + srcField));
                }
                steps.add(new GraphModels.ExplainStep(e.type, src, dst, reason));
                cur = prev.get(cur);
            }
            // 反转为 root -> target 的顺序
            Collections.reverse(steps);
            GraphModels.FieldExplain fe = new GraphModels.FieldExplain();
            fe.object = n.object;
            fe.field = n.field;
            fe.steps = steps;
            fe.summary = n.object + "." + n.field + " 受 " + root + " 影响，路径长度 " + steps.size();
            grp.fields.add(fe);
        }
        GraphModels.ExplainResponse resp = new GraphModels.ExplainResponse();
        resp.rootObject = objectType;
        resp.rootField = fieldCamel;
        resp.groups = new ArrayList<GraphModels.ExplainGroup>(groups.values());
        return resp;
    }

    /**
     * 批量分析多个字段的影响范围，合并结果（带缓存）
     */
    public GraphModels.Graph analyzeBatch(String objectType, List<String> fields, int depth, int relType,
            boolean includeUpstream) {
        if (fields == null || fields.isEmpty()) {
            return new GraphModels.Graph();
        }

        // 批量分析的缓存key
        String fieldsStr = String.join(",", fields);
        String cacheKey = cacheKey(objectType, fieldsStr, depth, relType, includeUpstream);

        try {
            GraphModels.Graph cached = env().graphCache.get(cacheKey, () -> {
                return analyzeBatchInternal(objectType, fields, depth, relType, includeUpstream);
            });
            // log.info("从缓存获取批量分析结果: {}", cacheKey);
            return cached;
        } catch (ExecutionException e) {
            log.error("缓存获取失败，直接执行批量分析", e);
            return analyzeBatchInternal(objectType, fields, depth, relType, includeUpstream);
        }
    }

    /**
     * 内部批量分析方法
     */
    private GraphModels.Graph analyzeBatchInternal(String objectType, List<String> fields, int depth, int relType,
            boolean includeUpstream) {
        // log.info("批量分析开始: object={}, fields={}, count={}", objectType, fields,
        // fields.size());

        // 合并多个分析结果
        GraphModels.Graph merged = new GraphModels.Graph();
        Set<String> edgeSet = new HashSet<>();
        Map<String, GraphModels.Node> nodeMap = new HashMap<>();

        for (String field : fields) {
            // 使用analyzeInternal方法，避免重复缓存
            GraphModels.Graph g = analyzeInternal(objectType, field, depth, relType, includeUpstream);

            // 合并节点（去重）
            for (GraphModels.Node n : g.nodes) {
                String nodeId = n.id;
                if (!nodeMap.containsKey(nodeId)) {
                    nodeMap.put(nodeId, n);
                    merged.nodes.add(n);
                }
            }

            // 合并边（去重）
            for (GraphModels.Edge e : g.edges) {
                String edgeKey = e.source + "|" + e.type + "|" + e.target;
                if (!edgeSet.contains(edgeKey)) {
                    edgeSet.add(edgeKey);
                    merged.edges.add(e);
                }
            }
        }

        // log.info("批量分析完成: 合并后节点数={}, 边数={}", merged.nodes.size(),
        // merged.edges.size());
        return merged;
    }

    public GraphModels.ObjectGraph analyzeObjects(String objectType, String fieldCamel, int depth, int relType) {
        GraphModels.Graph g = analyze(objectType, fieldCamel, depth, relType);
        Map<String, Set<String>> objToFields = new LinkedHashMap<String, Set<String>>();
        for (GraphModels.Node n : g.nodes) {
            objToFields.computeIfAbsent(n.object, k -> new LinkedHashSet<String>()).add(n.field);
        }
        Map<String, Integer> objFieldCount = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, Set<String>> e : objToFields.entrySet()) {
            objFieldCount.put(e.getKey(), e.getValue().size());
        }
        Map<String, Integer> agg = new LinkedHashMap<String, Integer>();
        for (GraphModels.Edge e : g.edges) {
            String sObj = e.source.substring(0, e.source.indexOf('.'));
            String tObj = e.target.substring(0, e.target.indexOf('.'));
            String key = sObj + "|" + e.type + "|" + tObj;
            agg.put(key, agg.getOrDefault(key, 0) + 1);
        }
        GraphModels.ObjectGraph og = new GraphModels.ObjectGraph();
        for (Map.Entry<String, Integer> e : objFieldCount.entrySet()) {
            og.nodes.add(new GraphModels.ObjectNode(e.getKey(), e.getValue()));
        }
        for (Map.Entry<String, Integer> e : agg.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            og.edges.add(new GraphModels.ObjectEdge(parts[0], parts[2], parts[1], e.getValue()));
        }
        return og;
    }

    /**
     * 返回对象间连线对应的字段级边明细（sourceObject -> targetObject，指定关系类型）。
     */
    public List<GraphModels.Edge> objectEdgeDetails(String objectType, String fieldCamel, int depth, int relType,
            String sourceObject, String targetObject, String type) {
        GraphModels.Graph g = analyze(objectType, fieldCamel, depth, relType);
        List<GraphModels.Edge> out = new ArrayList<GraphModels.Edge>();
        for (GraphModels.Edge e : g.edges) {
            if (type != null && !type.equals(e.type))
                continue;
            String sObj = e.source.substring(0, e.source.indexOf('.'));
            String tObj = e.target.substring(0, e.target.indexOf('.'));
            if (sObj.equals(sourceObject) && tObj.equals(targetObject)) {
                out.add(e);
            }
        }
        return out;
    }

    /**
     * 调试接口：返回视图依赖关系的详细信息
     */
    public Map<String, Object> getViewDependenciesDebugInfo() {
        Map<String, Object> result = new HashMap<>();
        result.put("totalEntries", env().viewReverseDeps.size());

        // 转换为易读格式
        Map<String, List<String>> readableFormat = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : env().viewReverseDeps.entrySet()) {
            readableFormat.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        result.put("dependencies", readableFormat);

        // 统计信息
        int totalMappings = 0;
        for (Set<String> targets : env().viewReverseDeps.values()) {
            totalMappings += targets.size();
        }
        result.put("totalMappings", totalMappings);

        return result;
    }

    public List<BaseappObjectField> getAllFields() {
        return env().allRows;
    }

    // ===== NL2MVEL 自然语言到 MVEL 推演引擎 =====

    // ==========================================
    // NL2MVEL 智能表达式推演引擎核心实现区
    // ==========================================

    // =====================================================================
    // 引用查询：查找通过 refer_info.referEntities 引用了指定对象的所有字段，按对象分组
    // =====================================================================

    public static class ReferenceGroup {
        public String objectType;
        public String objectTitle;
        public String appName;
        public List<FieldRef> fields = new ArrayList<>();

        public static class FieldRef {
            public String name;
            public String apiName;
            public String title;
            public String referInfo;
        }
    }

    public List<ReferenceGroup> findObjectsReferencingEntity(String entityName, boolean excludeView) {
        if (entityName == null || entityName.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<BaseappObjectField> rows;
        try {
            rows = repository.selectReferencingFields(entityName.trim());
        } catch (Exception e) {
            log.warn("[findObjectsReferencingEntity] DB 查询失败: {}", e.getMessage());
            return Collections.emptyList();
        }
        Map<String, ReferenceGroup> groups = new LinkedHashMap<>();
        for (BaseappObjectField f : rows) {
            String obj = f.getObjectType();
            if (obj == null) continue;
            if (excludeView && obj.toLowerCase().contains("view")) continue;
            ReferenceGroup g = groups.computeIfAbsent(obj, k -> {
                ReferenceGroup rg = new ReferenceGroup();
                rg.objectType = k;
                rg.appName = f.getAppName();
                rg.objectTitle = env().objectTitles.getOrDefault(k, "");
                return rg;
            });
            ReferenceGroup.FieldRef fr = new ReferenceGroup.FieldRef();
            fr.name = f.getName();
            fr.apiName = f.getApiName() != null && !f.getApiName().isEmpty() ? f.getApiName() : f.getName();
            fr.title = f.getTitle();
            fr.referInfo = f.getReferInfo();
            g.fields.add(fr);
        }
        // 按 appName + objectType 字母排序
        List<ReferenceGroup> result = new ArrayList<>(groups.values());
        result.sort((a, b) -> {
            String aApp = a.appName != null ? a.appName.toLowerCase() : "";
            String bApp = b.appName != null ? b.appName.toLowerCase() : "";
            int cmp = aApp.compareTo(bApp);
            if (cmp != 0) return cmp;
            return a.objectType.compareToIgnoreCase(b.objectType);
        });
        return result;
    }

}
