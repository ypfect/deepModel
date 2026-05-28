package com.deepmodel.relation.service;

import com.deepmodel.relation.dao.MetadataRepository;
import com.deepmodel.relation.model.BaseappObjectField;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 字段定义快照与版本对比服务。
 *
 * <p>已从原 JDBC 直连方式改造为基于 env 的 GraphQL 拉取：
 * 调用方传入 env 名（如 test-tx-21），内部通过 {@link MetadataRepository#selectBillFieldsForEnv}
 * 按 env 路由到对应 GraphQL endpoint。</p>
 */
@Service
public class SnapshotService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);
    private static final String SNAPSHOT_DIR = "./snapshots";

    private final ImpactAnalyzerService analyzerService;
    private final MetadataRepository repository;
    private final ObjectMapper objectMapper;

    public SnapshotService(ImpactAnalyzerService analyzerService, MetadataRepository repository) {
        this.analyzerService = analyzerService;
        this.repository = repository;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

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
     * 比较两个环境的 bill 类型对象字段差异。
     *
     * @param baseEnv         基准环境名
     * @param baseAppNames    基准侧 appName 过滤
     * @param compareEnv      比较环境名
     * @param compareAppNames 比较侧 appName 过滤
     */
    public VersionDiff compareEnvPair(String baseEnv, List<String> baseAppNames,
                                      String compareEnv, List<String> compareAppNames) {
        if (baseEnv == null || baseEnv.isBlank()) {
            throw new IllegalArgumentException("基准环境 baseEnv 不能为空");
        }
        if (compareEnv == null || compareEnv.isBlank()) {
            throw new IllegalArgumentException("比较环境 compareEnv 不能为空");
        }
        List<BaseappObjectField> baseList = repository.selectBillFieldsForEnv(baseEnv, baseAppNames);
        List<BaseappObjectField> compareList = repository.selectBillFieldsForEnv(compareEnv, compareAppNames);
        return compareLists(baseList, compareList);
    }

    /**
     * 跨环境拉取字段定义（供 upgrade script 使用）。
     */
    public List<BaseappObjectField> fetchFieldsByEnv(String env, List<String> appNames) {
        return repository.selectBillFieldsForEnv(env, appNames);
    }

    /** 字段的逻辑唯一键：objectType + name。 */
    private static String fieldKey(BaseappObjectField f) {
        String obj = f.getObjectType() != null ? f.getObjectType() : "";
        String name = f.getName() != null ? f.getName() : (f.getApiName() != null ? f.getApiName() : "");
        return obj + "." + name;
    }

    private VersionDiff compareLists(List<BaseappObjectField> baseList, List<BaseappObjectField> compareList) {
        Map<String, BaseappObjectField> baseMap = new LinkedHashMap<>();
        for (BaseappObjectField f : baseList) {
            baseMap.put(fieldKey(f), f);
        }
        Map<String, BaseappObjectField> compareMap = new LinkedHashMap<>();
        for (BaseappObjectField f : compareList) {
            compareMap.put(fieldKey(f), f);
        }

        VersionDiff diff = new VersionDiff();

        for (String key : baseMap.keySet()) {
            if (!compareMap.containsKey(key)) {
                diff.removed.add(baseMap.get(key));
            }
        }

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
