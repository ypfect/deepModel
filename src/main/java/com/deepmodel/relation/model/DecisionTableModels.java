package com.deepmodel.relation.model;

import java.util.*;

/**
 * 决策表分析相关的数据模型。
 */
public class DecisionTableModels {

    /**
     * 从 CSV 中解析出的单条 FuncUnit 声明。
     */
    public static class AssyFuncUnit {
        private String step;        // 功能步骤：Preprocess, ValidateParameter, etc.
        private String spec;        // FuncUnitSpec DSL 表达式
        private boolean excluded;   // 是否为 exclude 操作

        public String getStep() { return step; }
        public void setStep(String step) { this.step = step; }
        public String getSpec() { return spec; }
        public void setSpec(String spec) { this.spec = spec; }
        public boolean isExcluded() { return excluded; }
        public void setExcluded(boolean excluded) { this.excluded = excluded; }
    }

    /**
     * 一个 CSV 文件的完整解析结果。
     */
    public static class CsvParseResult {
        public String csvPath;                      // CSV 文件相对路径
        public String operationName;                // 推导出的操作名（Add/Update/Remove...）
        public String entityFolder;                 // 所属目录（base/common/arcontract...）
        public Map<String, String> attributes = new LinkedHashMap<>();  // CSV 头部属性
        public List<AssyFuncUnit> funcUnits = new ArrayList<>();        // 所有规则行
        public boolean succession = true;           // 是否继承父类

        /** 按步骤分组 */
        public Map<String, List<AssyFuncUnit>> groupByStep() {
            Map<String, List<AssyFuncUnit>> map = new LinkedHashMap<>();
            for (AssyFuncUnit fu : funcUnits) {
                map.computeIfAbsent(fu.getStep(), k -> new ArrayList<>()).add(fu);
            }
            return map;
        }
    }

    /**
     * FuncUnit 在某个 CSV 中的使用记录。
     */
    public static class FuncUnitUsage {
        public String funcUnitName;     // FuncUnit 类名
        public String csvPath;          // 所在 CSV 文件
        public String operationName;    // 操作名
        public String stepType;         // 步骤类型
        public String fullSpec;         // 完整 DSL 表达式
        public boolean isExclude;       // 是否为 exclude

        @Override
        public String toString() {
            return String.format("%s in %s [%s] %s", funcUnitName, csvPath, stepType,
                    isExclude ? "(exclude)" : "");
        }
    }

    /**
     * 操作级别的 FuncUnit 编排视图。
     */
    public static class OperationView {
        public String operationName;
        public Map<String, List<FuncUnitEntry>> steps = new LinkedHashMap<>();  // stepType → entries
    }

    /**
     * 单个 FuncUnit 条目。
     */
    public static class FuncUnitEntry {
        public String name;             // FuncUnit 类名
        public String fullSpec;         // 完整 DSL 表达式（含优先级/位置词/参数）
        public String csvSource;        // 来源 CSV
        public boolean isExclude;       // 是否为 exclude

        /** 从 DSL 提取名称 */
        public static String extractName(String spec) {
            if (spec == null || spec.trim().isEmpty()) return "";
            String s = spec.trim();
            // "TestValidator 1" → "TestValidator"
            // "TestValidator first" → "TestValidator"
            // "TestValidator before Xxx" → "TestValidator"
            // "TestProcessor withParam {k=v}" → "TestProcessor"
            // "TestValidator,0" → "TestValidator"
            // 先处理逗号分隔的情况
            if (s.contains(",")) {
                s = s.substring(0, s.indexOf(",")).trim();
            }
            // 取第一个空格前的部分
            int spaceIdx = s.indexOf(' ');
            return spaceIdx > 0 ? s.substring(0, spaceIdx) : s;
        }
    }
}
