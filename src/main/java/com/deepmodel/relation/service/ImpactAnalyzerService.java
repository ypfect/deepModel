package com.deepmodel.relation.service;

import com.deepmodel.relation.dao.BaseappObjectFieldMapper;
import com.deepmodel.relation.model.BaseappObjectField;
import com.deepmodel.relation.model.GraphModels;
import com.deepmodel.relation.model.WriteBackExpr;
import com.deepmodel.relation.util.ExprUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

@Service
public class ImpactAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(ImpactAnalyzerService.class);

    private final BaseappObjectFieldMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
            .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
            .configure(JsonParser.Feature.ALLOW_COMMENTS, true)
            .configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true);

    // 缓存
    private volatile List<BaseappObjectField> allRows = Collections.emptyList();
    private final Map<String, List<BaseappObjectField>> rowsByObject = new ConcurrentHashMap<String, List<BaseappObjectField>>();
    // 缓存对象标题: objectType -> title
    private final Map<String, String> objectTitles = new ConcurrentHashMap<>();
    // bill 类型对象集合（来自 baseapp_object_type.type='bill'）
    private volatile Set<String> billObjectTypes = Collections.emptySet();

    // 视图依赖反向索引: SourceObject.field -> Set<ViewName.field> (用于下游分析)
    private final Map<String, Set<String>> viewReverseDeps = new ConcurrentHashMap<>();
    // 视图依赖正向索引: ViewName.field -> Set<SourceObject.field> (用于上游溯源)
    private final Map<String, Set<String>> viewDirectDeps = new ConcurrentHashMap<>();

    // 分析结果缓存（使用Guava Cache）
    private final Cache<String, GraphModels.Graph> graphCache = CacheBuilder.newBuilder()
            .maximumSize(1000) // 最多缓存1000个结果
            .build();

    // 解释结果缓存
    private final Cache<String, GraphModels.ExplainResponse> explainCache = CacheBuilder.newBuilder()
            .maximumSize(1000) // 最多缓存1000个结果
            .build();

    public ImpactAnalyzerService(BaseappObjectFieldMapper mapper) {
        this.mapper = mapper;
    }

    @PostConstruct
    public void loadCache() {
        reload();
        loadViews();
    }

    public synchronized void reload() {
        List<BaseappObjectField> rows = mapper.selectAll();
        Map<String, List<BaseappObjectField>> byObj = rows.stream()
                .collect(Collectors.groupingBy(BaseappObjectField::getObjectType));
        rowsByObject.clear();
        rowsByObject.putAll(byObj);
        allRows = rows;

        // 重新加载视图字段（因为 allRows 覆盖了）
        loadViews();

        // 加载对象标题
        objectTitles.clear();
        try {
            List<BaseappObjectField> titles = mapper.selectObjectTitles();
            for (BaseappObjectField t : titles) {
                if (t.getName() != null && t.getTitle() != null) {
                    objectTitles.put(t.getName(), t.getTitle());
                }
            }
            log.info("Loaded {} object titles", objectTitles.size());
        } catch (Exception e) {
            log.warn("Failed to load object titles", e);
        }

        // 加载 bill 类型对象列表（baseapp_object_type.type='bill'）
        try {
            List<String> billNames = mapper.selectBillObjectTypes();
            billObjectTypes = new HashSet<String>();
            for (String n : billNames) {
                if (n != null && !n.trim().isEmpty()) {
                    billObjectTypes.add(n.trim());
                }
            }
            log.info("Loaded {} bill object types", billObjectTypes.size());
        } catch (Exception e) {
            log.warn("Failed to load bill object types", e);
            billObjectTypes = Collections.emptySet();
        }

        // 清除分析结果缓存（因为数据源已更新）
        clearAnalysisCache();

        log.info("缓存加载完成: 对象数={}, 记录数={}, 视图={}", rowsByObject.size(), allRows.size(), viewReverseDeps.size());
    }

    /**
     * 清除分析结果缓存
     */
    public void clearAnalysisCache() {
        graphCache.invalidateAll();
        explainCache.invalidateAll();
        log.info("已清除分析结果缓存");
    }

    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("graphCacheSize", graphCache.size());
        stats.put("explainCacheSize", explainCache.size());
        stats.put("graphCacheStats", graphCache.stats().toString());
        stats.put("explainCacheStats", explainCache.stats().toString());
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

    private void loadViews() {
        viewReverseDeps.clear();
        viewDirectDeps.clear();
        try {
            // 从数据库查询所有视图定义
            List<String> viewJsonList = mapper.selectViewDefinitions();
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

                    // 注入到 Graph 数据源 (仅当对象不存在时，避免覆盖实体对象)
                    if (!rowsByObject.containsKey(viewName)) {
                        rowsByObject.put(viewName, viewFields);
                    }
                    totalViewsLoaded++;

                    JsonNode viewDefs = root.path("viewDef");
                    if (viewDefs.isArray()) {
                        // log.info("[视图加载] viewDef SQL数量={}", viewDefs.size());
                        for (JsonNode def : viewDefs) {
                            String objectName = def.path("objectName").asText();
                            String sqlText = def.path("sql").asText();
                            // log.info("[视图加载] 解析SQL: objectName={}, SQL长度={}", objectName,
                            // sqlText.length());
                            parseSqlDependencies(viewName, sqlText);
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

            // log.info("[视图加载] 完成，加载视图数={}, 解析SQL数={}, viewReverseDeps总条目数={}",
            // totalViewsLoaded, totalSqlsParsed, viewReverseDeps.size());

            // 输出前5条依赖关系示例
            int count = 0;
            for (Map.Entry<String, Set<String>> entry : viewReverseDeps.entrySet()) {
                if (count++ < 5) {
                    // log.info("[视图加载] 依赖示例: {} -> {}", entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception e) {
            log.error("加载视图定义失败", e);
        }
    }

    /**
     * 提取主 SELECT 和其对应 FROM 之间的子句。避免子查询中的 FROM 导致截断（如
     * case when ... then (select ... from baseapp_customer ...) when ...）。
     */
    private String extractSelectClause(String cleanSql) {
        if (cleanSql == null || cleanSql.isEmpty()) return null;
        int selIdx = cleanSql.toUpperCase().indexOf("SELECT");
        if (selIdx < 0) return null;
        int start = selIdx + 6; // 跳过 SELECT
        // 跳过 SELECT 后的空白，定位到 SELECT 后的第一个非空白字符（即列表达式的开始）
        while (start < cleanSql.length() && Character.isWhitespace(cleanSql.charAt(start))) {
            start++;
        }
        int balance = 0;
        int fromStart = -1;
        for (int i = start; i < cleanSql.length(); i++) {
            char c = cleanSql.charAt(i);
            if (c == '(') balance++;
            else if (c == ')') balance--;
            else if (balance == 0 && i + 4 <= cleanSql.length()) {
                String chunk = cleanSql.substring(i, Math.min(i + 5, cleanSql.length())).toUpperCase();
                if (chunk.startsWith("FROM") && (i + 4 == cleanSql.length() || !Character.isLetterOrDigit(cleanSql.charAt(i + 4)))) {
                    fromStart = i;
                    break;
                }
            }
        }
        if (fromStart < 0) return null;
        return cleanSql.substring(start, fromStart).trim();
    }

    private void parseSqlDependencies(String viewName, String sql) {
        if (sql == null || sql.isEmpty())
            return;
        // 简单清理
        String cleanSql = sql.replace("${SystemFields}", " ");

        // 1. 提取 FROM 和 JOIN 的别名映射 Map<Alias, TableName>
        Map<String, String> aliasMap = new HashMap<>();
        // 支持 "FROM table alias" 和 "FROM table AS alias" 以及 "FROM table"
        // 支持 schema.table 格式
        Pattern tablePat = Pattern.compile("\\b(FROM|JOIN)\\s+([a-zA-Z0-9_\\.]+)(?:\\s+(?:AS\\s+)?([a-zA-Z0-9_]+))?",
                Pattern.CASE_INSENSITIVE);
        Matcher mTable = tablePat.matcher(cleanSql);
        while (mTable.find()) {
            String tableName = mTable.group(2);
            String alias = mTable.group(3);
            if (alias == null)
                alias = tableName; // 若无别名，使用表名本身作为别名
            aliasMap.put(alias, tableName);
        }

        // log.info("[视图解析] 视图={}, 别名映射数量={}", viewName, aliasMap.size());
        if (aliasMap.size() <= 3) {
            // log.info("[视图解析] 别名映射明细: {}", aliasMap);
        }

        // 2. 提取 SELECT ... FROM 之间的内容（需避免子查询中的 FROM 截断，按括号层级匹配主 SELECT 的 FROM）
        String selectClause = extractSelectClause(cleanSql);
        if (selectClause == null) {
            log.warn("[视图解析] 无法匹配 SELECT...FROM 语句");//IsOrgCrossDocView
            return;
        }

        // 3. 简单的逗号分割（忽略括号内的逗号）
        List<String> columns = splitColumns(selectClause);
        // log.info("[视图解析] SELECT 子句列数={}", columns.size());

        int parsedCount = 0;
        for (String colDef : columns) {
            // 查找 AS alias (支持不带 AS 的列别名，但这比较困难，这里假设规范 SQL 都有 AS)
            Pattern asPat = Pattern.compile("(.+?)\\s+AS\\s+([a-zA-Z0-9_]+)$",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher mAs = asPat.matcher(colDef.trim());
            if (mAs.find()) {
                String expr = mAs.group(1).trim();
                String targetSnake = mAs.group(2).trim();
                String targetCamel = ExprUtils.snakeToCamel(targetSnake);

                // 查找 expr 中的 alias.column
                // 简单匹配: 单词.单词
                Pattern refPat = Pattern.compile("\\b([a-zA-Z0-9_]+)\\.([a-zA-Z0-9_]+)\\b");
                Matcher mRef = refPat.matcher(expr);
                while (mRef.find()) {
                    String alias = mRef.group(1);
                    String col = mRef.group(2);
                    String tableName = aliasMap.get(alias);
                    if (tableName != null) {
                        String srcObj = tableToObject(tableName);
                        String srcField = ExprUtils.snakeToCamel(col);
                        if (srcObj != null && srcField != null) {
                            // 记录依赖: srcObj.srcField -> viewName.targetCamel (下游)
                            String srcKey = srcObj + "." + srcField;
                            String tgtKey = viewName + "." + targetCamel;
                            viewReverseDeps.computeIfAbsent(srcKey, k -> new HashSet<>()).add(tgtKey);

                            // 记录依赖: viewName.targetCamel -> srcObj.srcField (上游溯源)
                            viewDirectDeps.computeIfAbsent(tgtKey, k -> new HashSet<>()).add(srcKey);

                            parsedCount++;
                            if (parsedCount <= 5) {
                                // log.info("[视图解析] 依赖: {} -> {}", srcKey, tgtKey);
                            }
                        }
                    }
                }
            }
        }
        // log.info("[视图解析] 视图={}, 解析出依赖关系总数={}", viewName, parsedCount);
    }

    private List<String> splitColumns(String text) {
        List<String> list = new ArrayList<>();
        int balance = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(')
                balance++;
            else if (c == ')')
                balance--;
            else if (c == ',' && balance == 0) {
                list.add(text.substring(start, i));
                start = i + 1;
            }
        }
        list.add(text.substring(start));
        return list;
    }

    private String canonicalFieldName(BaseappObjectField r) {
        if (r.getApiName() != null && !r.getApiName().trim().isEmpty())
            return r.getApiName().trim();
        return r.getName() != null ? r.getName().trim() : null;
    }

    // 新增：获取字段元数据
    public BaseappObjectField getFieldInfo(String objectType, String fieldCamel) {
        List<BaseappObjectField> rows = rowsByObject.get(objectType);
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
        List<BaseappObjectField> rows = rowsByObject.getOrDefault(objectType,
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
        List<BaseappObjectField> rows = rowsByObject.getOrDefault(targetObjectType, Collections.emptyList());
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
        for (String target : rowsByObject.keySet()) {
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
            if (a.fieldCount != b.fieldCount) return Integer.compare(b.fieldCount, a.fieldCount);
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
        List<BaseappObjectField> rows = rowsByObject.getOrDefault(targetObjectType,
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
            if (a.fieldCount != b.fieldCount) return Integer.compare(b.fieldCount, a.fieldCount);
            return a.sourceObject.compareTo(b.sourceObject);
        });
        return out;
    }

    // Meta APIs for Frontend
    public Set<String> getAllObjectTypes() {
        // 仅保留 baseapp_object_type 中 type='bill' 的对象；若配置为空则退回全部
        Set<String> all = new TreeSet<>(rowsByObject.keySet());
        if (billObjectTypes == null || billObjectTypes.isEmpty()) {
            return all;
        }
        Set<String> billOnly = all.stream()
                .filter(name -> name != null && billObjectTypes.contains(name))
                .collect(Collectors.toCollection(TreeSet::new));
        return billOnly.isEmpty() ? all : billOnly;
    }

    public List<Map<String, String>> getObjectDetails() {
        Set<String> types = getAllObjectTypes();
        List<Map<String, String>> result = new ArrayList<>();
        for (String type : types) {
            Map<String, String> map = new HashMap<>();
            map.put("name", type);
            map.put("title", objectTitles.getOrDefault(type, type)); // fallback to name
            result.add(map);
        }
        // Sort by name
        result.sort(Comparator.comparing(m -> m.get("name")));
        return result;
    }

    public List<String> getFieldsForObject(String objectType) {
        List<BaseappObjectField> rows = rowsByObject.get(objectType);
        if (rows == null)
            return Collections.emptyList();
        return rows.stream()
                .map(this::canonicalFieldName)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());
    }

    public List<BaseappObjectField> getFieldDetailsForObject(String objectType) {
        List<BaseappObjectField> rows = rowsByObject.get(objectType);
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
        List<BaseappObjectField> rows = rowsByObject.getOrDefault(objectType, Collections.emptyList());
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
            refs.addAll(ExprUtils.extractCamelFieldsFromSql(row.getTriggerExpr()));
        if (row.getExpression() != null)
            refs.addAll(ExprUtils.extractCamelFieldsFromSql(row.getExpression()));
        if (row.getVirtualExpr() != null)
            refs.addAll(ExprUtils.extractCamelFieldsFromSql(row.getVirtualExpr()));
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

    /**
     * 构建当前对象内「trigger 聚合字段」到其组成字段集合的映射。
     *
     * <p>例如：<code>makeInvoiceAmount</code> 的 triggerExpr 为
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
        List<BaseappObjectField> rows = rowsByObject.getOrDefault(objectType,
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
        if (haystack == null || needle == null) return false;
        int n = haystack.size(), m = needle.size();
        if (m == 0 || n < m) return false;
        for (int i = 0; i <= n - m; i++) {
            boolean ok = true;
            for (int j = 0; j < m; j++) {
                if (!needle.get(j).equals(haystack.get(i + j))) {
                    ok = false;
                    break;
                }
            }
            if (ok) return true;
        }
        return false;
    }

    /**
     * intra 依赖：给定 sourceFieldCamel（同一对象内的某个字段），找出所有
     * 公式中引用了该字段（或其 trigger 聚合等价字段集合）的「被影响字段」列表。
     *
     * <p>特殊处理 trigger 聚合字段：如果 sourceFieldCamel 恰好是一个 trigger 聚合字段，
     * 那么凡是引用了其组成字段（例如 make_invoice_amount_blue 等）的字段，也会被认为
     * 是由 sourceFieldCamel 触发，从而补上「等价关系」这层语义。
     */
    private List<Map.Entry<String, String>> buildIntraDependencies(String objectType, String sourceFieldCamel) {
        List<BaseappObjectField> rows = rowsByObject.getOrDefault(objectType,
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
        List<BaseappObjectField> rows = rowsByObject.getOrDefault(objectType,
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
                    refs.addAll(ExprUtils.extractCamelFieldsFromSql(r.getTriggerExpr()));
                if (r.getExpression() != null)
                    refs.addAll(ExprUtils.extractCamelFieldsFromSql(r.getExpression()));
                if (r.getVirtualExpr() != null)
                    refs.addAll(ExprUtils.extractCamelFieldsFromSql(r.getVirtualExpr()));
                refs.remove(targetFieldCamel);
                List<String> list = new ArrayList<String>(refs);
                // log.info("upstream: {}.{} <- {} 条, 字段: {}", objectType, targetFieldCamel,
                // list.size(), list);
                return list;
            }
        }
        // log.warn("upstream: {}.{} <- 0 条 (未找到该字段)", objectType, targetFieldCamel);
        // Debug info to see why it wasn't found
        for (BaseappObjectField r : rows) {
            if (r.getName().contains("invoiceAppAmount") || r.getApiName().contains("invoiceAppAmount")) {
                log.warn("Missed candidate: name={}, apiName={}, snakeToCamel={}", r.getName(), r.getApiName(),
                        ExprUtils.snakeToCamel(r.getName()));
            }
        }
        return Collections.<String>emptyList();
    }

    private List<Map.Entry<String, String>> buildCrossObjectDependencies(String objectType, String sourceFieldCamel) {
        List<Map.Entry<String, String>> out = new ArrayList<Map.Entry<String, String>>();
        int scanned = 0;
        for (BaseappObjectField r : allRows) {
            scanned++;
            WriteBackExpr wb = parseWriteBack(r.getWriteBackExpr());
            if (!writebackHitsCurrentObject(wb, objectType))
                continue;
            String expr = (wb != null) ? wb.getExpression() : null;
            Set<String> refs = ExprUtils.extractCamelFieldsFromSql(expr);
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
            GraphModels.Graph cached = graphCache.get(cacheKey, () -> {
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
        boolean includeWriteBack = (relType == 0 || relType == 1);
        boolean includeIntra = (relType == 0 || relType == 2);
        boolean includeView = (relType == 0 || relType == 3);

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
                            for (String srcFld : srcFields) {
                                String uId = srcObj + "." + srcFld;
                                addEdgeIfAbsent(edges, edgeSet, uId, cur, "writeBack"); // Source -> Me
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
                    Set<String> upstreamSources = viewDirectDeps.get(cur); // cur is View.Field
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
                        addEdgeIfAbsent(edges, edgeSet, cur, nid, "writeBack");
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
                    Set<String> views = viewReverseDeps.get(cur);
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
            GraphModels.ExplainResponse cached = explainCache.get(cacheKey, () -> {
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
            GraphModels.Graph cached = graphCache.get(cacheKey, () -> {
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
        result.put("totalEntries", viewReverseDeps.size());

        // 转换为易读格式
        Map<String, List<String>> readableFormat = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : viewReverseDeps.entrySet()) {
            readableFormat.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        result.put("dependencies", readableFormat);

        // 统计信息
        int totalMappings = 0;
        for (Set<String> targets : viewReverseDeps.values()) {
            totalMappings += targets.size();
        }
        result.put("totalMappings", totalMappings);

        return result;
    }
}
