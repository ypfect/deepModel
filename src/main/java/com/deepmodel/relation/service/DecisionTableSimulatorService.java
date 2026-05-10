package com.deepmodel.relation.service;

import com.deepmodel.relation.model.DecisionTableModels.*;
import jakarta.annotation.PostConstruct;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * 决策表模拟器：给定对象名+操作方法，模拟 FuncUnitDecider 的完整装配流程
 * 
 * 1. 扫描 rules/ 目录，构建 {folderName → {csvFileName → csvAbsPath}} 索引
 * 2. 解析 CSV 条件列（stepType, rootEntityType, entityName, rootNode, multiOrgEnabled, isTree）
 * 3. 模拟 Service 继承链合并（app CSV → base CSV）
 * 4. 条件匹配 + include/exclude + 排序
 */
@Service
public class DecisionTableSimulatorService {
    private static final Logger log = LoggerFactory.getLogger(DecisionTableSimulatorService.class);

    @Value("${decision-table.enabled:false}")
    private boolean enabled;

    @Value("${decision-table.scan-path:}")
    private String scanPath;

    @Autowired
    private com.deepmodel.relation.dao.BaseappObjectFieldMapper mapper;

    // folderName → { csvFileName → absolutePath }
    private Map<String, Map<String, String>> folderIndex = new LinkedHashMap<>();
    // 所有 folder 名称列表
    private List<String> allFolders = new ArrayList<>();
    // 操作名称集合（从 CSV 文件名推导，如 AddHandlerMatchingRules.csv → Add）
    private Set<String> allMethodNames = new TreeSet<>();

    // 7步枚举
    private static final List<String> STEPS = List.of(
        "Preprocess", "ValidateParameter", "DetermineFeasibility",
        "HandleBusinessLogic", "DataPersistence", "ExecutePostProcessing", "PerformActionOnExit"
    );

    @PostConstruct
    public void init() {
        if (!enabled || scanPath == null || scanPath.isBlank()) return;
        long start = System.currentTimeMillis();
        scanRulesDirectories();
        log.info("[Simulator] 索引构建完成: {} 个 Service 文件夹, {} 个操作方法, 耗时 {}ms",
            allFolders.size(), allMethodNames.size(), System.currentTimeMillis() - start);
    }

    private void scanRulesDirectories() {
        log.info("[Simulator] 开始扫描 scanPath={}", scanPath);
        Path root = Paths.get(scanPath);
        if (!Files.isDirectory(root)) return;

        try {
            // 使用 sh -c 执行 find 命令（确保 glob 正确展开）
            String cmd = String.format(
                "find '%s' -path '*/resources/rules/*/*.csv' ! -path '*/target/*' ! -path '*/test/*'",
                scanPath);
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            int count = 0;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String full = line.trim();
                    if (full.isEmpty() || !full.endsWith(".csv")) continue;

                    int rulesIdx = full.lastIndexOf("/rules/");
                    if (rulesIdx < 0) continue;
                    String relative = full.substring(rulesIdx + 7);
                    String[] parts = relative.split("/");
                    if (parts.length != 2) continue;

                    String folder = parts[0];
                    String csvFile = parts[1];

                    folderIndex.computeIfAbsent(folder, k -> new LinkedHashMap<>())
                        .put(csvFile, full);

                    String methodName = extractMethodName(csvFile);
                    if (methodName != null) allMethodNames.add(methodName);
                    count++;
                }
            }
            process.waitFor();
            log.info("[Simulator] find 命令扫描到 {} 个 CSV 文件", count);

            allFolders = new ArrayList<>(folderIndex.keySet());
            Collections.sort(allFolders);
        } catch (Exception e) {
            log.error("[Simulator] 扫描失败", e);
        }
    }

    /**
     * 从 CSV 文件名提取操作名称
     */
    private String extractMethodName(String csvFileName) {
        // AddHandlerMatchingRules.csv → Add
        // UpdateBillStatusMatchingRules.csv → UpdateBillStatus
        String name = csvFileName.replace(".csv", "");
        name = name.replace("MatchingRules", "").replace("Handler", "");
        if (name.isEmpty()) return null;
        return name;
    }

    /**
     * 获取所有 Service 文件夹名称
     */
    public List<String> getAllFolders() {
        return allFolders;
    }

    /**
     * 获取所有操作方法名称
     */
    public Set<String> getAllMethodNames() {
        return allMethodNames;
    }

    /**
     * 获取指定 folder 下的所有操作
     */
    public List<String> getMethodsForFolder(String folder) {
        Map<String, String> csvs = folderIndex.get(folder);
        if (csvs == null) return List.of();
        return csvs.keySet().stream()
            .map(this::extractMethodName)
            .filter(Objects::nonNull)
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * 调试：返回原始的 FuncUnit Customizer 数据库查询结果
     */
    public Map<String, Object> debugCustomizers(String objectName) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            result.put("objectTypeFuncUnits", mapper.selectObjectTypeFuncUnits(objectName));
        } catch (Exception e) {
            result.put("objectTypeFuncUnits_error", e.getMessage());
        }
        try {
            result.put("entityBusinessRules", mapper.selectEntityBusinessRules(objectName));
        } catch (Exception e) {
            result.put("entityBusinessRules_error", e.getMessage());
        }
        try {
            result.put("preDoRules", mapper.selectPreDoRules(objectName));
        } catch (Exception e) {
            result.put("preDoRules_error", e.getMessage());
        }
        return result;
    }

    /**
     * 核心方法：模拟给定对象+操作的完整 FuncUnit 装配
     *
     * @param objectName 对象名称，如 ArContract
     * @param csvFileName CSV 文件名，如 AddHandlerMatchingRules.csv
     * @param entityType 实体类型（bill/document/...），可为空
     * @return 模拟结果
     */
    public SimulationResult simulate(String objectName, String csvFileName, String entityType) {
        // 1. 推导 folder 名称（对象名小写）
        String folder = objectName.toLowerCase();

        // 2. 构建继承链：app CSV → base CSV
        List<CsvLayer> layers = new ArrayList<>();

        // base 层
        Map<String, String> baseCsvs = folderIndex.get("base");
        if (baseCsvs != null && baseCsvs.containsKey(csvFileName)) {
            layers.add(new CsvLayer("base", baseCsvs.get(csvFileName)));
        }

        // common 层
        Map<String, String> commonCsvs = folderIndex.get("common");
        if (commonCsvs != null && commonCsvs.containsKey(csvFileName)) {
            layers.add(new CsvLayer("common", commonCsvs.get(csvFileName)));
        }

        // supplychain 混入层（若有）
        Map<String, String> scCsvs = folderIndex.get("supplychain");
        if (scCsvs != null && scCsvs.containsKey(csvFileName)) {
            layers.add(new CsvLayer("supplychain (mixin)", scCsvs.get(csvFileName)));
        }

        // app 层
        Map<String, String> appCsvs = folderIndex.get(folder);
        if (appCsvs != null && appCsvs.containsKey(csvFileName)) {
            layers.add(new CsvLayer(folder, appCsvs.get(csvFileName)));
        }

        // 3. 解析每层 CSV，提取匹配规则行
        Map<String, List<AssembledFuncUnit>> stepResults = new LinkedHashMap<>();
        STEPS.forEach(s -> stepResults.put(s, new ArrayList<>()));
        Set<String> globalExclusions = new LinkedHashSet<>();
        List<ExclusionInfo> exclusionDetails = new ArrayList<>();

        for (CsvLayer layer : layers) {
            List<CsvRuleRow> rows = parseCsvRules(layer.csvPath);
            for (CsvRuleRow row : rows) {
                // 条件匹配
                if (!matchConditions(row, objectName, entityType)) continue;

                String step = row.stepType;
                if (step == null || !stepResults.containsKey(step)) continue;

                // include
                if (row.attachSpec != null && !row.attachSpec.isBlank()) {
                    AssembledFuncUnit fu = new AssembledFuncUnit();
                    fu.name = extractFuncUnitName(row.attachSpec);
                    fu.fullSpec = row.attachSpec.trim();
                    fu.source = layer.name;
                    fu.csvPath = layer.csvPath;
                    fu.isRootNode = row.rootNode;
                    fu.entityNameCondition = row.entityName;
                    fu.priority = row.ruleOrder;
                    // 解析排序信息（priorityNbr / prioritizeMode / relativeName）
                    parseSpecSortInfo(row.attachSpec, fu);
                    stepResults.get(step).add(fu);
                }

                // exclude
                if (row.detachNames != null && !row.detachNames.isBlank()) {
                    for (String ex : row.detachNames.split(",")) {
                        String trimmed = ex.trim();
                        globalExclusions.add(trimmed);
                        exclusionDetails.add(new ExclusionInfo(trimmed, step, layer.name, csvFileName));
                    }
                }
            }
        }

        // 3.5 混入 FuncUnitCustomizer 数据（来自数据库）
        List<CustomizedFuncUnit> customizedFuncUnits = new ArrayList<>();
        String methodName = extractMethodName(csvFileName);
        try {
            // ① ObjectTypeFuncUnitCustomizer: 对象绑定的 FuncUnit
            List<Map<String, Object>> otfuList = mapper.selectObjectTypeFuncUnits(objectName);
            for (Map<String, Object> row : otfuList) {
                String fuName = (String) row.get("func_unit_name");
                String stepType = (String) row.get("method_step_type");
                String execMethod = (String) row.get("exec_base_method");
                if (fuName == null || stepType == null) continue;

                // 方法匹配：execBaseMethod 包含当前方法（或为空表示所有方法）
                if (execMethod != null && !execMethod.isBlank()) {
                    boolean methodMatch = false;
                    for (String m : execMethod.split(",")) {
                        // 匹配方法名（CSV 用 AddGenImpl, 数据库可能用 AddGenImpl 或 Add）
                        if (methodName != null && (m.trim().contains(methodName) || methodName.contains(m.trim()))) {
                            methodMatch = true;
                            break;
                        }
                    }
                    // CheckValidGenImpl 默认对所有方法生效
                    if (!methodMatch && !execMethod.contains("CheckValidGenImpl")) continue;
                }

                // 加入对应步骤
                if (stepResults.containsKey(stepType)) {
                    AssembledFuncUnit fu = new AssembledFuncUnit();
                    fu.name = fuName.substring(0, 1).toUpperCase() + fuName.substring(1);
                    fu.fullSpec = fu.name + " (ObjectTypeFuncUnit)";
                    fu.source = "DB:ObjectTypeFuncUnit";
                    fu.csvPath = null;
                    stepResults.get(stepType).add(fu);
                }
                customizedFuncUnits.add(new CustomizedFuncUnit(
                    fuName, stepType, "ObjectTypeFuncUnit", execMethod, (String) row.get("id")));
            }

            // ② EntityBusinessRuleCustomizer: 实体业务规则
            List<Map<String, Object>> ebrList = mapper.selectEntityBusinessRules(objectName);
            for (Map<String, Object> row : ebrList) {
                String fuName = (String) row.get("func_unit_name");
                String stepType = (String) row.get("func_unit_step");
                String triggerMethod = (String) row.get("trigger_base_method");
                if (fuName == null || stepType == null) continue;

                // 方法匹配
                if (triggerMethod != null && !triggerMethod.isBlank() && methodName != null) {
                    boolean methodMatch = false;
                    for (String m : triggerMethod.split(",")) {
                        if (m.trim().contains(methodName) || methodName.contains(m.trim())) {
                            methodMatch = true;
                            break;
                        }
                    }
                    if (!methodMatch) continue;
                }

                if (stepResults.containsKey(stepType)) {
                    AssembledFuncUnit fu = new AssembledFuncUnit();
                    fu.name = fuName;
                    fu.fullSpec = fuName + " (EntityBusinessRule)";
                    fu.source = "DB:EntityBusinessRule";
                    fu.csvPath = null;
                    stepResults.get(stepType).add(fu);
                }
                customizedFuncUnits.add(new CustomizedFuncUnit(
                    fuName, stepType, "EntityBusinessRule", triggerMethod, (String) row.get("id")));
            }

            // ③ PreDoRule: 仅记录数量，不混入步骤（运行时动态）
            try {
                List<Map<String, Object>> preDoList = mapper.selectPreDoRules(objectName);
                for (Map<String, Object> row : preDoList) {
                    customizedFuncUnits.add(new CustomizedFuncUnit(
                        (String) row.get("name"), "ValidateParameter", "PreDoRule(dynamic)", null, (String) row.get("id")));
                }
            } catch (Exception e) {
                log.debug("[Simulator] PreDoRule 表不存在或查询失败: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn("[Simulator] FuncUnit Customizer 查询失败: {}", e.getMessage());
        }

        // 4. 排除 + 去重 + 排序（移植 FuncUnitSpec.distinctAndOrder 的完整逻辑）
        for (var entry : stepResults.entrySet()) {
            List<AssembledFuncUnit> list = entry.getValue();
            // 排除
            list.removeIf(fu -> globalExclusions.contains(fu.name));
            // 去重（同 id+name 只保留最后出现的）
            LinkedHashMap<String, AssembledFuncUnit> dedup = new LinkedHashMap<>();
            for (AssembledFuncUnit fu : list) {
                dedup.put(fu.name, fu);
            }
            List<AssembledFuncUnit> sorted = new ArrayList<>(dedup.values());
            // 排序：移植 FuncUnitSpec.distinctAndOrder
            distinctAndOrder(sorted);
            entry.setValue(sorted);
        }

        // 5. 构建结果
        SimulationResult result = new SimulationResult();
        result.objectName = objectName;
        result.csvFileName = csvFileName;
        result.layers = layers;
        result.steps = stepResults;
        result.exclusions = exclusionDetails;
        result.customizedFuncUnits = customizedFuncUnits;
        result.totalFuncUnits = stepResults.values().stream().mapToInt(List::size).sum();
        return result;
    }

    // ========== 排序逻辑（移植自 FuncUnitSpec.distinctAndOrder） ==========

    /**
     * 完全移植 FuncUnitSpec.distinctAndOrder 的排序逻辑：
     * 1. 按 priorityNbr 升序（nullsLast）
     * 2. 处理方位词 first → last → before → after
     *    其中 first、after 需要先逆序再处理（isNeedToReverse）以保持自然序
     */
    private void distinctAndOrder(List<AssembledFuncUnit> list) {
        // ① 按 priorityNbr 排序（nullsLast）
        list.sort(Comparator.comparing(
            (AssembledFuncUnit fu) -> fu.priorityNbr,
            Comparator.nullsLast(Comparator.naturalOrder())));

        // ② 收集有方位词但无 priorityNbr 的 FuncUnit
        List<AssembledFuncUnit> nullPriWithMode = list.stream()
            .filter(fu -> fu.priorityNbr == null && fu.prioritizeMode != null)
            .collect(Collectors.toList());

        // first（逆序处理）
        List<AssembledFuncUnit> firsts = filterByMode(nullPriWithMode, "FIRST", true);
        for (AssembledFuncUnit fu : firsts) {
            list.remove(fu);
            list.add(0, fu);
        }

        // last（正序）
        List<AssembledFuncUnit> lasts = filterByMode(nullPriWithMode, "LAST", false);
        for (AssembledFuncUnit fu : lasts) {
            list.remove(fu);
            list.add(fu);
        }

        // before（正序）
        List<AssembledFuncUnit> befores = filterByMode(nullPriWithMode, "BEFORE", false);
        for (AssembledFuncUnit fu : befores) {
            moveBefore(fu, list);
        }

        // after（逆序处理）
        List<AssembledFuncUnit> afters = filterByMode(nullPriWithMode, "AFTER", true);
        for (AssembledFuncUnit fu : afters) {
            moveAfter(fu, list);
        }
    }

    private List<AssembledFuncUnit> filterByMode(List<AssembledFuncUnit> specs, String mode, boolean reverse) {
        List<AssembledFuncUnit> result = specs.stream()
            .filter(fu -> mode.equals(fu.prioritizeMode))
            .collect(Collectors.toList());
        if (reverse) {
            Collections.reverse(result);
        }
        return result;
    }

    private void moveBefore(AssembledFuncUnit spec, List<AssembledFuncUnit> list) {
        if (spec.relativeName == null) return;
        int relPos = -1;
        for (int i = 0; i < list.size(); i++) {
            if (spec.relativeName.equals(list.get(i).name)) { relPos = i; break; }
        }
        if (relPos < 0) return;

        int specPos = list.indexOf(spec);
        if (specPos < 0 || specPos + 1 == relPos) return;

        list.remove(spec);
        if (specPos < relPos) {
            list.add(relPos - 1, spec);
        } else {
            list.add(relPos, spec);
        }
    }

    private void moveAfter(AssembledFuncUnit spec, List<AssembledFuncUnit> list) {
        if (spec.relativeName == null) return;
        int relPos = -1;
        for (int i = 0; i < list.size(); i++) {
            if (spec.relativeName.equals(list.get(i).name)) { relPos = i; break; }
        }
        if (relPos < 0) return;

        int specPos = list.indexOf(spec);
        if (specPos < 0 || specPos - 1 == relPos) return;

        list.remove(spec);
        if (specPos < relPos) {
            list.add(relPos, spec);
        } else {
            list.add(relPos + 1, spec);
        }
    }

    // ========== Spec 解析（移植自 FuncUnitSpec.parse 的双正则） ==========

    // 新版本: Name 优先级? (first|last|before|after RelativeName?)? (withParam {k=v})? (applyTo type:which)?
    private static final Pattern NEW_SPEC = Pattern.compile(
        "^([A-Z]\\w+)\\s*(\\d+)?\\s*((first|last|before|after)\\s*([A-Z]\\w+)?)?\\s*(withParam\\s*\\{[^}]+\\})?\\s*(applyTo\\s*(entity|field|action)\\s*:(.+))?$");
    // 老版本: Name, 优先级?, (, entity|field|action:which)?
    private static final Pattern OLD_SPEC = Pattern.compile(
        "^([A-Z]\\w+)\\s*(,\\s*(\\d+))?\\s*(,\\s*(entity|field|action)\\s*:(.+))?$");

    /**
     * 解析 attachSpec 字符串，提取排序信息（priorityNbr / prioritizeMode / relativeName）
     */
    private void parseSpecSortInfo(String spec, AssembledFuncUnit fu) {
        if (spec == null || spec.isBlank()) return;
        String trimmed = spec.trim();

        Matcher m = NEW_SPEC.matcher(trimmed);
        if (m.matches()) {
            // 新格式
            String priority = m.group(2);
            if (priority != null) {
                fu.priorityNbr = Integer.valueOf(priority);
                fu.prioritizeMode = "ASSIGN";
            } else {
                String position = m.group(4);
                if (position != null) {
                    fu.prioritizeMode = position.toUpperCase();
                    fu.relativeName = m.group(5); // before/after 的相对名称
                }
            }
            return;
        }

        Matcher mOld = OLD_SPEC.matcher(trimmed);
        if (mOld.matches()) {
            // 老格式
            String priority = mOld.group(3);
            if (priority != null) {
                fu.priorityNbr = Integer.valueOf(priority);
                fu.prioritizeMode = "ASSIGN";
            }
        }
    }

    /**
     * 解析 CSV 文件的规则行
     */
    private List<CsvRuleRow> parseCsvRules(String csvPath) {
        List<CsvRuleRow> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(csvPath), StandardCharsets.UTF_8)) {
            List<String> lines = reader.lines().collect(Collectors.toList());
            if (lines.size() < 13) return rows;

            // 找到 CONDITION/ACTION 行来确定列布局
            int conditionLine = -1;
            for (int i = 0; i < Math.min(15, lines.size()); i++) {
                if (lines.get(i).contains("CONDITION")) {
                    conditionLine = i;
                    break;
                }
            }
            if (conditionLine < 0) return rows;

            // 解析条件模板行（conditionLine + 2）来确定每列的含义
            String templateLine = conditionLine + 2 < lines.size() ? lines.get(conditionLine + 2) : "";
            String[] templates = parseCsvLine(templateLine);

            // 确定列映射
            int colStepType = -1, colRootEntityType = -1, colEntityName = -1;
            int colRootNode = -1, colMultiOrg = -1, colIsTree = -1;
            int colAttach = -1, colDetach = -1;

            // 解析 CONDITION/ACTION 行确定列类型
            String[] condActLine = parseCsvLine(lines.get(conditionLine));
            for (int i = 0; i < condActLine.length; i++) {
                String cell = condActLine[i].trim();
                if ("ACTION".equals(cell)) {
                    // 第一个 ACTION 是 attach，第二个是 detach
                    if (colAttach < 0) colAttach = i;
                    else if (colDetach < 0) colDetach = i;
                }
            }

            // 通过模板内容判断条件列
            for (int i = 0; i < templates.length; i++) {
                String t = templates[i].trim();
                if (t.contains("stepType")) colStepType = i;
                else if (t.contains("rootEntityType")) colRootEntityType = i;
                else if (t.contains("entityName")) colEntityName = i;
                else if (t.contains("rootNode")) colRootNode = i;
                else if (t.contains("multiOrgEnabled")) colMultiOrg = i;
                else if (t.contains("isTree") || t.contains("getIsTree")) colIsTree = i;
                else if (t.contains("attach")) colAttach = i;
                else if (t.contains("detach")) colDetach = i;
            }

            // 数据行从 conditionLine + 4 开始（跳过 header 描述行）
            int dataStart = conditionLine + 4;
            for (int i = dataStart; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) continue;

                String[] cells = parseCsvLine(line);
                CsvRuleRow row = new CsvRuleRow();
                row.ruleOrder = safeGet(cells, 0);
                row.stepType = colStepType >= 0 ? safeGet(cells, colStepType) : null;
                row.rootEntityType = colRootEntityType >= 0 ? safeGet(cells, colRootEntityType) : null;
                row.entityName = colEntityName >= 0 ? safeGet(cells, colEntityName) : null;
                row.rootNode = colRootNode >= 0 ? safeGet(cells, colRootNode) : null;
                row.multiOrgEnabled = colMultiOrg >= 0 ? safeGet(cells, colMultiOrg) : null;
                row.isTree = colIsTree >= 0 ? safeGet(cells, colIsTree) : null;
                row.attachSpec = colAttach >= 0 ? safeGet(cells, colAttach) : null;
                row.detachNames = colDetach >= 0 ? safeGet(cells, colDetach) : null;

                if (row.stepType != null && !row.stepType.isEmpty()) {
                    rows.add(row);
                }
            }
        } catch (Exception e) {
            log.warn("[Simulator] 解析 CSV 失败: {}", csvPath, e);
        }
        return rows;
    }

    /**
     * 条件匹配：模拟 Drools 的条件判断
     */
    private boolean matchConditions(CsvRuleRow row, String objectName, String entityType) {
        // rootEntityType 匹配（若指定了）
        if (row.rootEntityType != null && !row.rootEntityType.isBlank()) {
            if (entityType == null || entityType.isBlank()) {
                // 无类型信息，宽松匹配（不过滤）
            } else if (!row.rootEntityType.equalsIgnoreCase(entityType)) {
                return false;
            }
        }
        // entityName 使用 endsWith 匹配（与 Drools 一致）
        // 不做过滤，展示所有规则（让用户看到完整编排，包括主表和子表的规则）
        return true;
    }

    private String extractFuncUnitName(String spec) {
        if (spec == null || spec.isBlank()) return spec;
        String trimmed = spec.trim();
        // 处理老版本格式 "Name, 1, field:xxx" 和新版本 "Name first" 等
        Matcher m = Pattern.compile("^([A-Z]\\w+)").matcher(trimmed);
        return m.find() ? m.group(1) : trimmed;
    }

    private String[] parseCsvLine(String line) {
        // 简单 CSV 解析（处理引号内的逗号）
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result.toArray(new String[0]);
    }

    private String safeGet(String[] arr, int idx) {
        if (idx < 0 || idx >= arr.length) return null;
        String val = arr[idx].trim();
        return val.isEmpty() ? null : val;
    }

    // === 内部数据结构 ===

    public static class CsvLayer {
        public String name;
        public String csvPath;
        public CsvLayer(String name, String csvPath) {
            this.name = name;
            this.csvPath = csvPath;
        }
    }

    public static class CsvRuleRow {
        public String ruleOrder;
        public String stepType;
        public String rootEntityType;
        public String entityName;
        public String rootNode;
        public String multiOrgEnabled;
        public String isTree;
        public String attachSpec;
        public String detachNames;
    }

    public static class AssembledFuncUnit {
        public String name;
        public String fullSpec;
        public String source;     // 来自哪个 CSV 层
        public String csvPath;
        public String isRootNode;
        public String entityNameCondition;
        public String priority;
        // 排序相关（移植自 FuncUnitSpec）
        public Integer priorityNbr;       // 优先级序号，nullsLast 升序
        public String prioritizeMode;     // ASSIGN/FIRST/LAST/BEFORE/AFTER
        public String relativeName;       // before/after 的相对部件名称
    }

    public static class CustomizedFuncUnit {
        public String name;
        public String stepType;
        public String source;        // ObjectTypeFuncUnit / EntityBusinessRule / PreDoRule
        public String execMethod;
        public String ruleId;
        public CustomizedFuncUnit(String name, String stepType, String source, String execMethod, String ruleId) {
            this.name = name;
            this.stepType = stepType;
            this.source = source;
            this.execMethod = execMethod;
            this.ruleId = ruleId;
        }
    }

    public static class SimulationResult {
        public String objectName;
        public String csvFileName;
        public List<CsvLayer> layers;
        public Map<String, List<AssembledFuncUnit>> steps;
        public List<ExclusionInfo> exclusions;
        public List<CustomizedFuncUnit> customizedFuncUnits; // 来自数据库的 FuncUnit
        public int totalFuncUnits;
    }

    public static class ExclusionInfo {
        public String name;      // 被排除的 FuncUnit 名称
        public String step;      // 在哪个步骤被排除
        public String source;    // 哪一层排除的（base/supplychain/arcontract等）
        public String csvFile;   // 决策表文件名
        public ExclusionInfo(String name, String step, String source, String csvFile) {
            this.name = name;
            this.step = step;
            this.source = source;
            this.csvFile = csvFile;
        }
    }
}
