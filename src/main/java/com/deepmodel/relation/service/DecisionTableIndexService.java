package com.deepmodel.relation.service;

import com.deepmodel.relation.model.DecisionTableModels;
import com.deepmodel.relation.model.DecisionTableModels.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 决策表索引服务。
 * <p>
 * 扫描代码库中的 CSV 决策表文件，静态解析后构建三个核心索引：
 * <ul>
 *   <li>索引 1: 操作名 → 步骤 → FuncUnit 列表</li>
 *   <li>索引 2: FuncUnit 名 → 所有使用位置</li>
 *   <li>索引 3: CSV 文件继承链</li>
 * </ul>
 */
@Service
public class DecisionTableIndexService {

    private static final Logger log = LoggerFactory.getLogger(DecisionTableIndexService.class);

    @Value("${decision-table.enabled:false}")
    private boolean enabled;

    @Value("${decision-table.scan-path:}")
    private String scanPath;

    /** csvPath → CsvParseResult */
    private final Map<String, CsvParseResult> allCsvResults = new ConcurrentHashMap<>();

    /** operationName → OperationView */
    private final Map<String, OperationView> operationIndex = new ConcurrentHashMap<>();

    /** funcUnitName → List<FuncUnitUsage> */
    private final Map<String, List<FuncUnitUsage>> funcUnitUsageIndex = new ConcurrentHashMap<>();

    /** 所有操作名列表 */
    private final List<String> operationNames = new ArrayList<>();

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("[DecisionTable] 决策表分析功能未启用 (decision-table.enabled=false)");
            return;
        }
        if (scanPath != null && !scanPath.trim().isEmpty()) {
            buildIndex();
        } else {
            log.info("[DecisionTable] scan-path 未配置，决策表分析功能未启用");
        }
    }

    /**
     * 构建决策表索引。
     */
    public synchronized void buildIndex() {
        if (scanPath == null || scanPath.trim().isEmpty()) {
            log.warn("[DecisionTable] scan-path 为空，无法构建索引");
            return;
        }

        long t0 = System.currentTimeMillis();
        allCsvResults.clear();
        operationIndex.clear();
        funcUnitUsageIndex.clear();
        operationNames.clear();

        Path basePath = Paths.get(scanPath);
        if (!Files.exists(basePath)) {
            log.warn("[DecisionTable] scan-path 不存在: {}", scanPath);
            return;
        }

        // Step 1: 扫描所有 CSV 文件
        List<Path> csvFiles = scanCsvFiles(basePath);
        log.info("[DecisionTable] 扫描到 {} 个 CSV 决策表文件", csvFiles.size());

        // Step 2: 解析每个 CSV
        for (Path csvFile : csvFiles) {
            try {
                CsvParseResult result = parseCsvFile(csvFile, basePath);
                if (result != null && !result.funcUnits.isEmpty()) {
                    allCsvResults.put(result.csvPath, result);
                }
            } catch (Exception e) {
                log.warn("[DecisionTable] 解析失败: {} - {}", csvFile, e.getMessage());
            }
        }

        // Step 3: 构建操作索引和 FuncUnit 使用索引
        buildOperationIndex();
        buildFuncUnitUsageIndex();

        long elapsed = System.currentTimeMillis() - t0;
        log.info("[DecisionTable] 索引构建完成: {} 个 CSV, {} 个操作, {} 个 FuncUnit, 耗时 {}ms",
                allCsvResults.size(), operationIndex.size(), funcUnitUsageIndex.size(), elapsed);
    }

    // ==================== 查询接口 ====================

    /**
     * 获取所有操作名列表。
     */
    public List<String> getOperationNames() {
        return Collections.unmodifiableList(operationNames);
    }

    /**
     * 获取某个操作的编排视图。
     */
    public OperationView getOperationView(String operationName) {
        return operationIndex.get(operationName);
    }

    /**
     * 获取全部操作的编排视图。
     */
    public Map<String, OperationView> getAllOperationViews() {
        return Collections.unmodifiableMap(operationIndex);
    }

    /**
     * 获取某个 FuncUnit 的所有使用位置。
     */
    public List<FuncUnitUsage> getFuncUnitUsages(String funcUnitName) {
        List<FuncUnitUsage> usages = funcUnitUsageIndex.get(funcUnitName);
        return usages != null ? Collections.unmodifiableList(usages) : Collections.emptyList();
    }

    /**
     * 获取全部 FuncUnit 使用索引。
     */
    public Map<String, List<FuncUnitUsage>> getAllFuncUnitUsages() {
        return Collections.unmodifiableMap(funcUnitUsageIndex);
    }

    /**
     * 获取某个 CSV 文件的详细解析结果。
     */
    public CsvParseResult getCsvDetail(String csvPath) {
        return allCsvResults.get(csvPath);
    }

    /**
     * 获取所有 CSV 解析结果。
     */
    public Collection<CsvParseResult> getAllCsvResults() {
        return Collections.unmodifiableCollection(allCsvResults.values());
    }

    /**
     * 获取全部 FuncUnit 名称列表（去重、排序）。
     */
    public List<String> getAllFuncUnitNames() {
        return funcUnitUsageIndex.keySet().stream().sorted().collect(Collectors.toList());
    }

    /**
     * 模糊搜索 FuncUnit。
     */
    public List<String> searchFuncUnits(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllFuncUnitNames();
        }
        String lower = keyword.toLowerCase();
        return funcUnitUsageIndex.keySet().stream()
                .filter(name -> name.toLowerCase().contains(lower))
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 获取决策表统计概要。
     */
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCsvFiles", allCsvResults.size());
        summary.put("totalOperations", operationIndex.size());
        summary.put("totalFuncUnits", funcUnitUsageIndex.size());
        summary.put("operationNames", operationNames);
        summary.put("scanPath", scanPath);

        // 按操作统计 FuncUnit 数量
        Map<String, Integer> opCounts = new LinkedHashMap<>();
        for (Map.Entry<String, OperationView> entry : operationIndex.entrySet()) {
            int count = entry.getValue().steps.values().stream()
                    .mapToInt(List::size).sum();
            opCounts.put(entry.getKey(), count);
        }
        summary.put("funcUnitCountByOperation", opCounts);

        return summary;
    }

    // ==================== 内部实现 ====================

    /**
     * 扫描目录下的所有 CSV 决策表文件。
     */
    private List<Path> scanCsvFiles(Path basePath) {
        List<Path> csvFiles = new ArrayList<>();

        // 扫描 platform/app-common/src/main/resources/rules/ 下的 CSV
        Path platformRulesDir = basePath.resolve("platform/app-common/src/main/resources/rules");
        if (Files.exists(platformRulesDir)) {
            scanDirectory(platformRulesDir, csvFiles);
        }

        // 扫描 apps/*/src/main/resources/rules/ 下的 CSV
        Path appsDir = basePath.resolve("apps");
        if (Files.exists(appsDir)) {
            try {
                Files.list(appsDir)
                        .filter(Files::isDirectory)
                        .forEach(appDir -> {
                            Path rulesDir = appDir.resolve("src/main/resources/rules");
                            if (Files.exists(rulesDir)) {
                                scanDirectory(rulesDir, csvFiles);
                            }
                        });
            } catch (IOException e) {
                log.warn("[DecisionTable] 扫描 apps 目录失败: {}", e.getMessage());
            }
        }

        return csvFiles;
    }

    private void scanDirectory(Path dir, List<Path> csvFiles) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".csv")) {
                        csvFiles.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("[DecisionTable] 扫描目录失败 {}: {}", dir, e.getMessage());
        }
    }

    /**
     * 解析单个 CSV 决策表文件。
     * <p>
     * 移植自 platform 的 DecisionTableParser.parseCsv()。
     */
    private CsvParseResult parseCsvFile(Path csvFile, Path basePath) throws IOException {
        CsvParseResult result = new CsvParseResult();
        result.csvPath = basePath.relativize(csvFile).toString();

        // 从文件名推导操作名和实体目录
        String fileName = csvFile.getFileName().toString();
        result.operationName = extractOperationName(fileName);
        result.entityFolder = csvFile.getParent().getFileName().toString();

        try (Reader reader = new InputStreamReader(new FileInputStream(csvFile.toFile()), StandardCharsets.UTF_8)) {
            Iterable<CSVRecord> records = CSVFormat.EXCEL.parse(reader);

            int stepColIdx = -1;
            int specColIdx = -1;
            boolean attrDefsEnded = false;
            boolean nextRowIsRule = false;

            for (CSVRecord record : records) {
                if (nextRowIsRule) {
                    if (!record.isSet(stepColIdx)) break;
                    String step = record.get(stepColIdx);
                    if (step == null || step.trim().isEmpty()) break;

                    String spec = record.isSet(specColIdx) ? record.get(specColIdx) : null;
                    String excluded = record.isSet(specColIdx + 1) ? record.get(specColIdx + 1) : null;

                    AssyFuncUnit bean = new AssyFuncUnit();
                    bean.setStep(step.trim());
                    bean.setSpec(isBlank(spec) ? (excluded != null ? excluded.trim() : null)
                            : spec.trim());
                    bean.setExcluded(isNotBlank(excluded));

                    if (isNotBlank(bean.getSpec())) {
                        result.funcUnits.add(bean);
                    }
                } else {
                    List<String> cells = new ArrayList<>();
                    for (int colIdx = 0; colIdx < record.size(); colIdx++) {
                        if (!record.isSet(colIdx)) continue;
                        String value = record.get(colIdx);
                        if (isBlank(value)) continue;
                        cells.add(value);
                        if (value.contains("步骤")) {
                            stepColIdx = colIdx;
                        } else if (value.contains("功能部件")) {
                            specColIdx = colIdx;
                            break;
                        } else if (value.contains("RuleTable")) {
                            attrDefsEnded = true;
                        }
                    }
                    if (stepColIdx >= 0 && specColIdx >= 0) {
                        nextRowIsRule = true;
                    }
                    if (!attrDefsEnded && cells.size() == 2) {
                        result.attributes.put(cells.get(0).trim(), cells.get(1).trim());
                    }
                }
            }
        }

        // 检查 Succession 属性
        String succession = result.attributes.get("Succession");
        result.succession = succession == null || Boolean.parseBoolean(succession);

        return result;
    }

    /**
     * 从文件名推导操作名。
     * <p>
     * "AddHandlerMatchingRules.csv" → "Add"
     * "UpdateBillStatusMatchingRules.csv" → "UpdateBillStatus"
     * "BatchRemoveHandlerMatchingRules.csv" → "BatchRemove"
     */
    private String extractOperationName(String fileName) {
        String name = fileName.replace(".csv", "");
        // 移除常见后缀
        for (String suffix : new String[]{"HandlerMatchingRules", "MatchingRules"}) {
            if (name.endsWith(suffix)) {
                return name.substring(0, name.length() - suffix.length());
            }
        }
        return name;
    }

    /**
     * 构建操作级别索引。
     */
    private void buildOperationIndex() {
        // 按操作名分组
        Map<String, List<CsvParseResult>> byOperation = new LinkedHashMap<>();
        for (CsvParseResult csv : allCsvResults.values()) {
            byOperation.computeIfAbsent(csv.operationName, k -> new ArrayList<>()).add(csv);
        }

        for (Map.Entry<String, List<CsvParseResult>> entry : byOperation.entrySet()) {
            String opName = entry.getKey();
            OperationView view = new OperationView();
            view.operationName = opName;

            for (CsvParseResult csv : entry.getValue()) {
                for (AssyFuncUnit fu : csv.funcUnits) {
                    FuncUnitEntry fEntry = new FuncUnitEntry();
                    fEntry.name = FuncUnitEntry.extractName(fu.getSpec());
                    fEntry.fullSpec = fu.getSpec();
                    fEntry.csvSource = csv.csvPath;
                    fEntry.isExclude = fu.isExcluded();

                    view.steps.computeIfAbsent(fu.getStep(), k -> new ArrayList<>()).add(fEntry);
                }
            }

            operationIndex.put(opName, view);
        }

        // 按步骤的标准顺序排序
        operationNames.addAll(operationIndex.keySet().stream().sorted().collect(Collectors.toList()));
    }

    /**
     * 构建 FuncUnit 使用索引。
     */
    private void buildFuncUnitUsageIndex() {
        for (CsvParseResult csv : allCsvResults.values()) {
            for (AssyFuncUnit fu : csv.funcUnits) {
                String name = FuncUnitEntry.extractName(fu.getSpec());
                if (name.isEmpty()) continue;

                FuncUnitUsage usage = new FuncUnitUsage();
                usage.funcUnitName = name;
                usage.csvPath = csv.csvPath;
                usage.operationName = csv.operationName;
                usage.stepType = fu.getStep();
                usage.fullSpec = fu.getSpec();
                usage.isExclude = fu.isExcluded();

                funcUnitUsageIndex.computeIfAbsent(name, k -> new ArrayList<>()).add(usage);
            }
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
