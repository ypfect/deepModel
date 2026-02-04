package com.deepmodel.relation.service;

import com.deepmodel.relation.dao.BaseappObjectFieldMapper;
import com.deepmodel.relation.model.BaseappObjectField;
import com.deepmodel.relation.model.GraphModels;
import com.deepmodel.relation.model.WriteBackExpr;
import com.deepmodel.relation.util.ExprUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * 根据字段影响关系生成数据升级 SQL 脚本（只生成文本，不执行）。
 *
 * 用法：
 *  - 通过 ImpactAnalyzerService 获取某个根字段的下游依赖图；
 *  - 按 depth 计算字段层级；
 *  - 按层级 + 关系类型（intra / writeBack）生成对应的 UPDATE/聚合 SQL；
 *  - 返回一整段可在生产环境执行的脚本，由使用方自行审阅和执行。
 *
 * 说明：
 *  - 使用 EQL 格式（对象名 + 驼峰字段名），通过 eqlExecutor.parseEql 转换为真实 SQL（含前缀 + 下划线列名）。
 *  - 视图关系（type = "view"）目前默认不生成 SQL，由调用方通过 relTypes 控制是否包含。
 */
@Service
public class UpgradeScriptService {

    private static final Logger log = LoggerFactory.getLogger(UpgradeScriptService.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    @Value("${writeback-sql.api-url:http://arap.test-tx-16.e7link.com/arap/gen/debug/writeBackField2sql}")
    private String writeBackSqlApiUrl;
    @Value("${writeback-sql.tenant-id:711FNX50G6V0009}")
    private String writeBackSqlTenantId;

    private final ImpactAnalyzerService impactAnalyzerService;
    private final BaseappObjectFieldMapper mapper;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public UpgradeScriptService(ImpactAnalyzerService impactAnalyzerService,
                                BaseappObjectFieldMapper mapper,
                                OkHttpClient httpClient) {
        this.impactAnalyzerService = impactAnalyzerService;
        this.mapper = mapper;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 关系类型枚举。
     */
    enum RelType {
        INTRA("intra"),
        WRITE_BACK("writeBack"),
        VIEW("view"),
        UNKNOWN("unknown");

        final String code;

        RelType(String code) {
            this.code = code;
        }

        static RelType fromCode(String code) {
            if (code == null) return UNKNOWN;
            for (RelType t : values()) {
                if (t.code.equals(code)) return t;
            }
            return UNKNOWN;
        }
    }

    static class FixStep {
        final String objectType;
        final String field;
        final int depth;
        final EnumSet<RelType> incomingTypes;

        FixStep(String objectType, String field, int depth, EnumSet<RelType> incomingTypes) {
            this.objectType = objectType;
            this.field = field;
            this.depth = depth;
            this.incomingTypes = incomingTypes;
        }
    }

    /**
     * 生成升级 SQL 脚本。
     *
     * @param rootObject 根对象类型
     * @param rootField  根字段名
     * @param maxDepth   下游层级深度
     * @param relTypes   包含的关系类型字符串，例如 "intra,writeBack" 或 "intra,writeBack,view"
     * @return 可直接保存为 .sql 的脚本文本
     */
    public String generateUpgradeScript(String rootObject,
                                        String rootField,
                                        int maxDepth,
                                        String relTypes) {

        Set<RelType> included = parseRelTypes(relTypes);

        GraphModels.Graph graph = impactAnalyzerService.analyze(rootObject, rootField, maxDepth, 0, false);
        if (graph.nodes == null || graph.nodes.isEmpty()) {
            return "-- 未找到任何影响关系: " + rootObject + "." + rootField + "\n";
        }

        Map<String, Integer> depthMap = computeDepthMap(graph, rootObject, rootField);
        List<FixStep> steps = buildFixSteps(graph, depthMap, included);

        StringBuilder sb = new StringBuilder();
        sb.append("-- =========================================\n");
        sb.append("-- 升级脚本 - 根字段: ").append(rootObject).append(".").append(rootField).append("\n");
        sb.append("-- maxDepth = ").append(maxDepth).append(", relTypes = ").append(included).append("\n");
        sb.append("-- 说明：\n");
        sb.append("--  1. 第 0 层为根字段本身，请根据新口径规则手工补充 UPDATE 语句。\n");
        sb.append("--  2. 后续各层按 depth 从小到大，先触发(intra)，再回写(writeBack)。\n");
        sb.append("-- =========================================\n\n");

        // 根字段：占位说明
        appendRootFieldPlaceholder(sb, rootObject, rootField);

        // 按 depth 分组
        Map<Integer, List<FixStep>> byDepth = new TreeMap<Integer, List<FixStep>>();
        for (FixStep s : steps) {
            if (s.depth < 0) continue;
            if (s.depth > maxDepth) continue;
            List<FixStep> list = byDepth.get(s.depth);
            if (list == null) {
                list = new ArrayList<FixStep>();
                byDepth.put(s.depth, list);
            }
            list.add(s);
        }

        for (Map.Entry<Integer, List<FixStep>> e : byDepth.entrySet()) {
            int depth = e.getKey();
            if (depth == 0) {
                // 根字段已单独处理
                continue;
            }
            List<FixStep> list = e.getValue();
            if (list.isEmpty()) continue;

            sb.append("\n-- =========================================\n");
            sb.append("-- depth ").append(depth).append("\n");
            sb.append("-- =========================================\n\n");

            // 先 intra，再 writeBack
            // 为了输出稳定性，先按对象/字段排序
            Collections.sort(list, new Comparator<FixStep>() {
                @Override
                public int compare(FixStep a, FixStep b) {
                    int c1 = a.objectType.compareTo(b.objectType);
                    if (c1 != 0) return c1;
                    return a.field.compareTo(b.field);
                }
            });

            for (FixStep step : list) {
                if (step.incomingTypes.contains(RelType.INTRA) && included.contains(RelType.INTRA)) {
                    appendIntraSql(sb, step);
                }
            }
            for (FixStep step : list) {
                if (step.incomingTypes.contains(RelType.WRITE_BACK) && included.contains(RelType.WRITE_BACK)) {
                    appendWriteBackSql(sb, step);
                }
            }
        }

        return sb.toString();
    }

    private Set<RelType> parseRelTypes(String relTypes) {
        Set<RelType> set = EnumSet.noneOf(RelType.class);
        if (relTypes == null || relTypes.trim().isEmpty()) {
            // 默认都包含
            set.add(RelType.INTRA);
            set.add(RelType.WRITE_BACK);
            // VIEW 通常不参与数据修复，先不加入
            return set;
        }
        String[] parts = relTypes.split(",");
        for (String raw : parts) {
            String t = raw.trim();
            if ("intra".equalsIgnoreCase(t)) set.add(RelType.INTRA);
            if ("writeBack".equalsIgnoreCase(t) || "writeback".equalsIgnoreCase(t)) set.add(RelType.WRITE_BACK);
            if ("view".equalsIgnoreCase(t)) set.add(RelType.VIEW);
        }
        if (set.isEmpty()) {
            set.add(RelType.INTRA);
            set.add(RelType.WRITE_BACK);
        }
        return set;
    }

    private void appendRootFieldPlaceholder(StringBuilder sb, String rootObject, String rootField) {
        sb.append("-- 第0步：根字段重算（请按新口径补充 SET 表达式）\n");
        sb.append("-- 下面示例使用 EQL 风格（对象名 + 驼峰字段名），请根据实际口径和范围改写：\n");
        sb.append("-- UPDATE ").append(rootObject).append("\n")
          .append("-- SET ").append(rootField).append(" = /* TODO: 新口径表达式 */\n")
          .append("-- WHERE /* TODO: 限定需要修复的范围，例如某个账期或批次 */;\n\n");
    }

    /**
     * 计算每个节点的 depth（从 root 出发的最短步数，只走下游边）。
     */
    private Map<String, Integer> computeDepthMap(GraphModels.Graph graph,
                                                 String rootObject,
                                                 String rootField) {
        Map<String, Integer> depth = new HashMap<String, Integer>();
        Map<String, List<String>> adj = new HashMap<String, List<String>>();

        for (GraphModels.Edge e : graph.edges) {
            List<String> list = adj.get(e.source);
            if (list == null) {
                list = new ArrayList<String>();
                adj.put(e.source, list);
            }
            list.add(e.target);
        }

        String rootId = rootObject + "." + rootField;
        Queue<String> queue = new ArrayDeque<String>();

        for (GraphModels.Node n : graph.nodes) {
            String id = n.id != null ? n.id : (n.object + "." + n.field);
            if (rootObject.equals(n.object) && rootField.equals(n.field)) {
                depth.put(id, Integer.valueOf(0));
                queue.offer(id);
            }
        }

        if (queue.isEmpty() && !graph.nodes.isEmpty()) {
            GraphModels.Node n0 = graph.nodes.get(0);
            String id0 = n0.id != null ? n0.id : (n0.object + "." + n0.field);
            depth.put(id0, Integer.valueOf(0));
            queue.offer(id0);
            log.warn("[UpgradeScript] root node not found for {}.{}, fallback to {}", rootObject, rootField, id0);
        }

        while (!queue.isEmpty()) {
            String cur = queue.poll();
            int d = depth.get(cur).intValue();
            List<String> outs = adj.get(cur);
            if (outs == null) continue;
            for (String next : outs) {
                if (!depth.containsKey(next)) {
                    depth.put(next, Integer.valueOf(d + 1));
                    queue.offer(next);
                }
            }
        }

        return depth;
    }

    private List<FixStep> buildFixSteps(GraphModels.Graph graph,
                                        Map<String, Integer> depthMap,
                                        Set<RelType> includedTypes) {
        Map<String, EnumSet<RelType>> incoming = new HashMap<String, EnumSet<RelType>>();

        for (GraphModels.Edge e : graph.edges) {
            RelType t = RelType.fromCode(e.type);
            if (!includedTypes.contains(t)) {
                // 不在关注范围的关系类型，直接忽略
                continue;
            }
            EnumSet<RelType> set = incoming.get(e.target);
            if (set == null) {
                set = EnumSet.noneOf(RelType.class);
                incoming.put(e.target, set);
            }
            set.add(t);
        }

        List<FixStep> result = new ArrayList<FixStep>();

        for (GraphModels.Node n : graph.nodes) {
            String id = n.id != null ? n.id : (n.object + "." + n.field);
            Integer d = depthMap.get(id);
            if (d == null) continue;
            EnumSet<RelType> types = incoming.get(id);
            if (types == null) {
                types = EnumSet.noneOf(RelType.class);
            }
            result.add(new FixStep(n.object, n.field, d.intValue(), types));
        }

        return result;
    }

    private void appendIntraSql(StringBuilder sb, FixStep step) {
        BaseappObjectField def = impactAnalyzerService.getFieldInfo(step.objectType, step.field);
        if (def == null) {
            sb.append("-- [SKIP] 未找到字段定义: ").append(step.objectType).append(".").append(step.field).append("\n\n");
            return;
        }
        String formula = firstNonEmpty(def.getTriggerExpr(), def.getExpression(), def.getVirtualExpr());
        if (formula == null || formula.trim().isEmpty()) {
            sb.append("-- [SKIP] 字段无公式: ").append(step.objectType).append(".").append(step.field).append("\n\n");
            return;
        }

        sb.append("-- Trigger 字段: ").append(step.objectType).append(".").append(step.field);
        if (def.getTitle() != null) {
            sb.append(" (").append(def.getTitle()).append(")");
        }
        sb.append("\n");
        sb.append("-- 公式: ").append(formula).append("\n");

        // 获取表名（带前缀）和字段名（下划线格式）
        String tableName = objectTypeToTableName(step.objectType);
        String columnName = fieldCamelToColumnName(step.field, step.objectType);
        
        // 公式中的字段名也需要转换为下划线格式
        String formulaSnake = convertFormulaToSnakeCase(formula, step.objectType);

        // 直接生成 SQL（带前缀表名 + 下划线字段名）
        sb.append("UPDATE ").append(tableName).append("\n")
          .append("SET ").append(columnName).append(" = (").append(formulaSnake).append(")\n")
          .append("WHERE ").append(columnName).append(" IS NULL")
          .append(" OR ").append(columnName).append(" <> (").append(formulaSnake).append(");\n\n");
    }

    private void appendWriteBackSql(StringBuilder sb, FixStep step) {
        BaseappObjectField def = impactAnalyzerService.getFieldInfo(step.objectType, step.field);
        if (def == null || def.getWriteBackExpr() == null || def.getWriteBackExpr().trim().isEmpty()) {
            sb.append("-- [SKIP] 字段无 writeBackExpr: ").append(step.objectType).append(".").append(step.field).append("\n\n");
            return;
        }

        WriteBackExpr wb = impactAnalyzerService.parseWriteBack(def.getWriteBackExpr());
        if (wb == null) {
            sb.append("-- [WARN] writeBackExpr 解析失败: ").append(step.objectType).append(".").append(step.field).append("\n\n");
            return;
        }

        sb.append("-- 回写: ").append(wb.getSrcObjectType()).append(" -> ")
          .append(step.objectType).append(".").append(step.field);
        if (def.getTitle() != null) {
            sb.append(" (").append(def.getTitle()).append(")");
        }
        sb.append("\n");

        // 调用外部接口生成 SQL
        String fieldPath = step.objectType + "." + step.field;
        String sql = callWriteBackSqlApi(fieldPath);
        
        if (sql != null && !sql.trim().isEmpty()) {
            sb.append(sql.trim()).append("\n\n");
        } else {
            sb.append("-- [WARN] 接口调用失败，无法生成 SQL: ").append(fieldPath).append("\n\n");
        }
    }

    /**
     * 调用外部接口生成回写字段的 SQL（使用 OkHttp，仅设置 Content-Type，不设置 Accept，避免 406）
     *
     * @param fieldPath 字段路径，格式：ObjectType.fieldName，例如：ArContractSubjectMatterItem.invoiceMakeAppAmountDir
     * @return SQL 字符串，如果调用失败返回 null
     */
    private String callWriteBackSqlApi(String fieldPath) {
        try {
            Map<String, String> body = new HashMap<String, String>();
            body.put("fieldPath", fieldPath);
            String json = objectMapper.writeValueAsString(body);

            Request.Builder builder = new Request.Builder()
                    .url(writeBackSqlApiUrl)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Tenant-Id", writeBackSqlTenantId)
                    .post(RequestBody.create(json, JSON));
            Request request = builder.build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("[UpgradeScript] 接口返回非 2xx: fieldPath={}, code={}", fieldPath, response.code());
                    return null;
                }
                ResponseBody responseBody = response.body();
                String sql = responseBody != null ? responseBody.string() : null;
                return normalizeWriteBackSql(sql);
            }
        } catch (IOException ex) {
            log.error("[UpgradeScript] 调用回写 SQL 接口失败: fieldPath={}, error={}", fieldPath, ex.getMessage(), ex);
            return null;
        } catch (Exception ex) {
            log.error("[UpgradeScript] 调用回写 SQL 接口失败: fieldPath={}, error={}", fieldPath, ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * 对回写接口返回的 SQL 做后处理：去掉首尾引号；去掉结尾的 WHERE m.id IN (#{targetIds[0]})
     */
    private String normalizeWriteBackSql(String sql) {
        if (sql == null) return null;
        String s = sql.trim();
        if (s.isEmpty()) return s;
        // 去掉首尾双引号（接口可能返回带引号的字符串）
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        // 去掉结尾的 WHERE m.id IN (#{targetIds[0]})
        String suffix = " WHERE m.id IN (#{targetIds[0]})";
        if (s.endsWith(suffix)) {
            s = s.substring(0, s.length() - suffix.length()).trim();
        }
        return s;
    }

    private String firstNonEmpty(String... arr) {
        if (arr == null) return null;
        for (String s : arr) {
            if (s != null && !s.trim().isEmpty()) return s.trim();
        }
        return null;
    }

    /**
     * 将对象类型转换为表名（带前缀）
     * 例如：InvoiceApplicationItem -> arap_invoice_application_item
     */
    private String objectTypeToTableName(String objectType) {
        if (objectType == null || objectType.trim().isEmpty()) {
            return "";
        }
        String appName = mapper.selectAppNameByObjectType(objectType);
        String snake = ExprUtils.camelToSnake(objectType);
        if (appName != null && !appName.trim().isEmpty()) {
            return appName.trim() + "_" + snake;
        }
        return snake;
    }

    /**
     * 将驼峰字段名转换为数据库列名（下划线格式）
     * 优先使用 BaseappObjectField.name（如果存在且是下划线格式），否则转换为下划线格式
     */
    private String fieldCamelToColumnName(String fieldCamel, String objectType) {
        BaseappObjectField def = impactAnalyzerService.getFieldInfo(objectType, fieldCamel);
        if (def != null && def.getName() != null && !def.getName().trim().isEmpty()) {
            String name = def.getName().trim();
            // 如果 name 已经是下划线格式（包含下划线），直接使用
            if (name.contains("_")) {
                return name;
            }
            // 如果 name 是驼峰格式，转换为下划线格式
            return ExprUtils.camelToSnake(name);
        }
        // 如果没有 name，将输入的驼峰字段名转换为下划线格式
        return ExprUtils.camelToSnake(fieldCamel);
    }

    /**
     * 将公式中的字段名转换为下划线格式
     * 例如：closedStatus -> closed_status, makeInvoiceAmount -> make_invoice_amount
     */
    private String convertFormulaToSnakeCase(String formula, String objectType) {
        if (formula == null || formula.trim().isEmpty()) {
            return formula;
        }
        
        // 从公式中提取所有字段名（使用 ExprUtils 的方法）
        Set<String> camelFields = ExprUtils.extractCamelFieldsFromSql(formula);
        
        // 构建字段名映射（驼峰 -> 下划线）
        Map<String, String> fieldMap = new HashMap<String, String>();
        for (String camelField : camelFields) {
            // 尝试通过 getFieldInfo 获取字段定义
            BaseappObjectField def = impactAnalyzerService.getFieldInfo(objectType, camelField);
            if (def != null && def.getName() != null && !def.getName().trim().isEmpty()) {
                // 如果 name 已经是下划线格式，直接使用
                fieldMap.put(camelField, def.getName().trim());
            } else {
                // 否则转换为下划线格式
                fieldMap.put(camelField, ExprUtils.camelToSnake(camelField));
            }
        }
        
        // 替换公式中的字段名
        String result = formula;
        // 按长度从长到短排序，避免短字段名被长字段名的一部分替换
        List<Map.Entry<String, String>> sorted = new ArrayList<Map.Entry<String, String>>(fieldMap.entrySet());
        Collections.sort(sorted, new Comparator<Map.Entry<String, String>>() {
            @Override
            public int compare(Map.Entry<String, String> a, Map.Entry<String, String> b) {
                return Integer.compare(b.getKey().length(), a.getKey().length());
            }
        });
        
        for (Map.Entry<String, String> e : sorted) {
            String camel = e.getKey();
            String snake = e.getValue();
            // 使用单词边界匹配，避免部分替换
            // 匹配：字段名前后是空格、括号、逗号、运算符等
            String pattern = "\\b" + java.util.regex.Pattern.quote(camel) + "\\b";
            result = result.replaceAll(pattern, snake);
        }
        
        return result;
    }

}
