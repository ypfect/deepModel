package com.deepmodel.relation.service;

import com.deepmodel.relation.model.BaseappObjectField;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SnapshotService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);
    private static final String SNAPSHOT_DIR = "./snapshots";

    private final ImpactAnalyzerService analyzerService;
    private final ObjectMapper objectMapper;

    public SnapshotService(ImpactAnalyzerService analyzerService) {
        this.analyzerService = analyzerService;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        // Ensure directory exists
        new File(SNAPSHOT_DIR).mkdirs();
    }

    public String createSnapshot() throws IOException {
        List<BaseappObjectField> fields = analyzerService.getAllFields();
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String filename = "snapshot_" + timestamp + ".json";
        File file = new File(SNAPSHOT_DIR, filename);

        objectMapper.writeValue(file, fields);
        log.info("Created snapshot: {}", file.getAbsolutePath());
        return filename;
    }

    public List<String> listSnapshots() {
        File dir = new File(SNAPSHOT_DIR);
        File[] files = dir.listFiles((d, name) -> name.startsWith("snapshot_") && name.endsWith(".json"));
        if (files == null)
            return Collections.emptyList();

        return Arrays.stream(files)
                .map(File::getName)
                .sorted(Collections.reverseOrder())
                .collect(Collectors.toList());
    }

    public static class FieldDiff {
        public String fieldId;
        public String objectType;
        public String fieldName;
        public String property;
        public String oldValue;
        public String newValue;

        public FieldDiff(String fieldId, String objectType, String fieldName, String property, String oldValue,
                String newValue) {
            this.fieldId = fieldId;
            this.objectType = objectType;
            this.fieldName = fieldName;
            this.property = property;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
    }

    public static class VersionDiff {
        public List<BaseappObjectField> added = new ArrayList<>();
        public List<BaseappObjectField> removed = new ArrayList<>();
        public List<FieldDiff> modified = new ArrayList<>();
    }

    /**
     * 比较当前 Spring Boot 数据源（本地）与远程库之间的差异。
     *
     * @param remoteUrl   远程 JDBC URL
     * @param username    远程用户名
     * @param password    远程密码
     * @param appNames    需要过滤的 appName 列表（可为空）
     * @param localAsBase 是否以本地库为「基准库」：
     *                    true  => 基准库 = 本地，比较库 = 远程
     *                    false => 基准库 = 远程，比较库 = 本地（兼容原有行为）
     */
    public VersionDiff compareWithRemote(String remoteUrl, String username, String password, List<String> appNames,
            boolean localAsBase) throws Exception {
        // 远端：只取 baseapp_object_type.type = 'bill' 的对象，视图不要，可按 appName 过滤
        List<BaseappObjectField> remoteList = fetchRemoteFields(remoteUrl, username, password, appNames);

        // 本地：同样只比较 bill 类型对象
        List<BaseappObjectField> localList = loadLocalFields(appNames);

        // 通用差异含义：
        // - 基准库：baseList
        // - 比较库：compareList
        List<BaseappObjectField> baseList;
        List<BaseappObjectField> compareList;
        if (localAsBase) {
            baseList = localList;
            compareList = remoteList;
        } else {
            baseList = remoteList;
            compareList = localList;
        }

        return compareLists(baseList, compareList);
    }

    /**
     * 比较两个任意端点（可以都是 JDBC，也可以一端留空代表“当前 Spring Boot 数据源”）。
     *
     * @param baseUrl       基准库 JDBC URL（为空则使用当前数据源）
     * @param baseUser      基准库用户名
     * @param basePassword  基准库密码
     * @param baseAppNames  基准库 appName 过滤
     * @param compareUrl    比较库 JDBC URL（为空则使用当前数据源）
     * @param compareUser   比较库用户名
     * @param comparePass   比较库密码
     * @param compareAppNames 比较库 appName 过滤
     */
    public VersionDiff compareJdbcPair(String baseUrl, String baseUser, String basePassword, List<String> baseAppNames,
            String compareUrl, String compareUser, String comparePass, List<String> compareAppNames) throws Exception {
        List<BaseappObjectField> baseList;
        List<BaseappObjectField> compareList;

        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            baseList = loadLocalFields(baseAppNames);
        } else {
            baseList = fetchRemoteFields(baseUrl, baseUser, basePassword, baseAppNames);
        }

        if (compareUrl == null || compareUrl.trim().isEmpty()) {
            compareList = loadLocalFields(compareAppNames);
        } else {
            compareList = fetchRemoteFields(compareUrl, compareUser, comparePass, compareAppNames);
        }

        return compareLists(baseList, compareList);
    }

    /**
     * 从当前 Spring Boot 数据源加载字段定义（只取 bill 类型对象，支持按 appName 过滤）。
     */
    private List<BaseappObjectField> loadLocalFields(List<String> appNames) {
        Set<String> billObjects = analyzerService.getAllObjectTypes();
        List<BaseappObjectField> localList = analyzerService.getAllFields().stream()
                .filter(f -> billObjects.contains(f.getObjectType()))
                .collect(Collectors.toList());

        if (appNames != null && !appNames.isEmpty()) {
            localList = localList.stream()
                    .filter(f -> {
                        String app = f.getAppName();
                        if (app == null)
                            return false;
                        for (String target : appNames) {
                            if (target == null)
                                continue;
                            if (app.equals(target))
                                return true;
                        }
                        return false;
                    })
                    .collect(Collectors.toList());
        }
        return localList;
    }

    /**
     * 对外暴露：从任意 JDBC 库拉取字段定义（用于升级脚本按最新定义生成 SQL）。
     */
    public List<BaseappObjectField> fetchCompareDbFields(String url, String user, String password,
            List<String> appNames) throws Exception {
        return fetchRemoteFields(url, user, password, appNames);
    }

    private List<BaseappObjectField> fetchRemoteFields(String url, String user, String password, List<String> appNames)
            throws Exception {
        List<BaseappObjectField> list = new ArrayList<>();
        // Load driver explicitly just in case
        Class.forName("org.postgresql.Driver");

        StringBuilder sql = new StringBuilder();
        sql.append("select t1.id, t1.object_type, t1.name, t1.api_name, t1.title, t1.type, t1.biz_type, ")
                .append("t1.expression, t1.trigger_expr, t1.virtual_expr, t1.write_back_expr::text as write_back_expr, ")
                .append("t2.app_name ")
                .append("from baseapp_object_field t1 ")
                .append("left join baseapp_object_type t2 on t1.object_type = t2.name ");

        boolean hasWhere = false;
        if (appNames != null && !appNames.isEmpty()) {
            sql.append("where t2.app_name IN (");
            for (int i = 0; i < appNames.size(); i++) {
                if (i > 0)
                    sql.append(",");
                sql.append("?");
            }
            sql.append(") ");
            hasWhere = true;
        }
        // 只比较 bill 类型对象，过滤掉视图等其它类型
        sql.append(hasWhere ? "and " : "where ")
                .append("coalesce(t2.type,'') = 'bill' ");

        try (java.sql.Connection result = java.sql.DriverManager.getConnection(url, user, password);
                java.sql.PreparedStatement stmt = result.prepareStatement(sql.toString())) {

            if (appNames != null && !appNames.isEmpty()) {
                for (int i = 0; i < appNames.size(); i++) {
                    stmt.setString(i + 1, appNames.get(i));
                }
            }

            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    BaseappObjectField f = new BaseappObjectField();
                    f.setId(rs.getString("id"));
                    f.setObjectType(rs.getString("object_type"));
                    f.setName(rs.getString("name"));
                    f.setApiName(rs.getString("api_name"));
                    f.setTitle(rs.getString("title"));
                    f.setType(rs.getString("type"));
                    f.setBizType(rs.getString("biz_type"));
                    f.setExpression(rs.getString("expression"));
                    f.setTriggerExpr(rs.getString("trigger_expr"));
                    f.setVirtualExpr(rs.getString("virtual_expr"));
                    f.setWriteBackExpr(rs.getString("write_back_expr"));
                    f.setAppName(rs.getString("app_name"));
                    list.add(f);
                }
            }
        }
        return list;
    }

    /**
     * 字段的逻辑唯一键：objectType + name（下划线格式）。
     * 不用 id（UUID），因为不同环境导入时 UUID 可能不同，但 objectType+name 是业务上的唯一标识。
     */
    private static String fieldKey(BaseappObjectField f) {
        String obj = f.getObjectType() != null ? f.getObjectType() : "";
        String name = f.getName() != null ? f.getName() : (f.getApiName() != null ? f.getApiName() : "");
        return obj + "." + name;
    }

    /**
     * 按「基准库 / 比较库」的通用语义做字段差异对比。
     *
     * @param baseList    基准库字段列表
     * @param compareList 比较库字段列表
     */
    private VersionDiff compareLists(List<BaseappObjectField> baseList, List<BaseappObjectField> compareList) {
        // 用 objectType+name 做逻辑唯一键，避免不同环境 UUID 不同导致所有字段都落入 added/removed
        Map<String, BaseappObjectField> baseMap = new LinkedHashMap<>();
        for (BaseappObjectField f : baseList) {
            baseMap.put(fieldKey(f), f);
        }
        Map<String, BaseappObjectField> compareMap = new LinkedHashMap<>();
        for (BaseappObjectField f : compareList) {
            compareMap.put(fieldKey(f), f);
        }

        VersionDiff diff = new VersionDiff();

        // removed: 在基准库有、比较库没有
        for (String key : baseMap.keySet()) {
            if (!compareMap.containsKey(key)) {
                diff.removed.add(baseMap.get(key));
            }
        }

        // added: 在比较库有、基准库没有；modified: 两边都存在但定义不同
        for (String key : compareMap.keySet()) {
            if (!baseMap.containsKey(key)) {
                diff.added.add(compareMap.get(key));
            } else {
                compareFields(baseMap.get(key), compareMap.get(key), diff.modified);
            }
        }
        return diff;
    }

    public VersionDiff compare(String snapshotId1, String snapshotId2) throws IOException {
        List<BaseappObjectField> list1 = loadSnapshot(snapshotId1);
        List<BaseappObjectField> list2 = loadSnapshot(snapshotId2);
        // 这里约定 snapshotId1 为「基准快照」，snapshotId2 为「比较快照」
        return compareLists(list1, list2);
    }

    private List<BaseappObjectField> loadSnapshot(String filename) throws IOException {
        File file = new File(SNAPSHOT_DIR, filename);
        if (!file.exists()) {
            throw new IOException("Snapshot file not found: " + filename);
        }
        return objectMapper.readValue(file, new TypeReference<List<BaseappObjectField>>() {
        });
    }

    private void compareFields(BaseappObjectField oldF, BaseappObjectField newF, List<FieldDiff> diffs) {
        compareProperty(oldF, newF, "expression", oldF.getExpression(), newF.getExpression(), diffs);
        compareProperty(oldF, newF, "triggerExpr", oldF.getTriggerExpr(), newF.getTriggerExpr(), diffs);
        compareProperty(oldF, newF, "writeBackExpr", oldF.getWriteBackExpr(), newF.getWriteBackExpr(), diffs);
        compareProperty(oldF, newF, "virtualExpr", oldF.getVirtualExpr(), newF.getVirtualExpr(), diffs);
        compareProperty(oldF, newF, "title", oldF.getTitle(), newF.getTitle(), diffs);
        compareProperty(oldF, newF, "type", oldF.getType(), newF.getType(), diffs);
    }

    private void compareProperty(BaseappObjectField oldF, BaseappObjectField newF, String propName, String val1,
            String val2, List<FieldDiff> diffs) {
        if (!Objects.equals(val1, val2)) {
            diffs.add(new FieldDiff(oldF.getId(), oldF.getObjectType(), oldF.getName(), propName, val1, val2));
        }
    }
}
