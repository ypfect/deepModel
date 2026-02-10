package com.deepmodel.relation.service;

import com.deepmodel.relation.util.ExprUtils;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FormulaParserService {

    private static final Logger log = LoggerFactory.getLogger(FormulaParserService.class);

    /**
     * 从 SQL 视图定义或复杂表达式中提取所有引用的表名 (Table) 和字段名 (Column)。
     * 返回结构：Map<TableName, Set<ColumnName>>
     * 如果 TableName 为 null，表示是未明确指定表别名的字段（如 SELECT col FROM table）。
     */
    public Map<String, Set<String>> extractDependencies(String sql) {
        Map<String, Set<String>> result = new HashMap<>();
        if (sql == null || sql.trim().isEmpty()) {
            return result;
        }

        // 预处理：DeepModel 的公式可能包含非标准 SQL 语法（如字段名直接相加）
        // 如果无法解析为 Statement，尝试包装成 SELECT 表达式解析
        String cleanSql = sql.trim();
        Statement statement = null;
        try {
            statement = CCJSqlParserUtil.parse(cleanSql);
        } catch (JSQLParserException e) {
            // 尝试包装成 SELECT * FROM DUMMY WHERE ... 或 SELECT ...
            try {
                statement = CCJSqlParserUtil.parse("SELECT " + cleanSql);
            } catch (JSQLParserException ex) {
                // log.warn("JSqlParser 无法解析 SQL: {}", cleanSql);
                // 降级：使用正则提取（兼容旧逻辑）
                return extractByRegex(cleanSql);
            }
        }

        if (statement instanceof Select) {
            Select select = (Select) statement;
            extractFromSelect(select, result);
        } else {
            // 简单的表达式解析器
            try {
                Expression expr = CCJSqlParserUtil.parseExpression(cleanSql);
                extractFromExpression(expr, result);
            } catch (JSQLParserException e) {
                return extractByRegex(cleanSql);
            }
        }

        return result;
    }

    /**
     * 专门用于提取公式中的驼峰字段名（兼容旧的 ExprUtils 功能，但更强壮）
     * 例如：make_invoice_amount + red_amount -> [makeInvoiceAmount, redAmount]
     * 注意：SQL 解析出来的是下划线格式，这里需要转换。
     * 但 DeepModel 的 expression 字段里存的可能是 EQL (驼峰) 也可能是 SQL (下划线)。
     * 策略：
     * 1. 尝试解析为 Expression
     * 2. 遍历 Column，记录 ColumnName
     * 3. 如果包含下划线，尝试转驼峰；如果是驼峰，保留。
     */
    public Set<String> extractCamelFields(String formula) {
        Set<String> dependencies = new HashSet<>();
        if (formula == null || formula.trim().isEmpty()) {
            return dependencies;
        }

        try {
            Expression expr = CCJSqlParserUtil.parseExpression(formula);
            expr.accept(new ExpressionVisitorAdapter() {
                @Override
                public void visit(Column column) {
                    String colName = column.getColumnName();
                    // 收集字段名
                    dependencies.add(colName);
                    // 同时也尝试转驼峰（兼容下划线写法）
                    if (colName.contains("_")) {
                        dependencies.add(ExprUtils.snakeToCamel(colName));
                    }
                }
            });
        } catch (JSQLParserException e) {
            // 降级回正则
            return new HashSet<>(ExprUtils.extractCamelFieldsFromSql(formula));
        }
        return dependencies;
    }

    /**
     * 提取视图或 SELECT 语句的字段血缘关系。
     * 返回结构：Map<OutputColumnName, Map<SourceTableName, Set<SourceColumnName>>>
     * 例如：SELECT t1.name AS user_name, t2.amount FROM t1 JOIN t2 ...
     * 返回：
     * user_name -> { t1 -> [name] }
     * amount -> { t2 -> [amount] }
     */
    public Map<String, Map<String, Set<String>>> extractColumnLineage(String sql) {
        Map<String, Map<String, Set<String>>> lineage = new LinkedHashMap<>();
        if (sql == null || sql.trim().isEmpty()) {
            return lineage;
        }

        String cleanSql = sql.trim();
        // 预处理：移除 potential system logic 占位符
        cleanSql = cleanSql.replace("${SystemFields}", " ");

        Statement statement = null;
        try {
            statement = CCJSqlParserUtil.parse(cleanSql);
        } catch (JSQLParserException e) {
            // 尝试包装
            try {
                statement = CCJSqlParserUtil.parse("SELECT " + cleanSql);
            } catch (JSQLParserException ex) {
                return lineage; // 无法解析
            }
        }

        if (statement instanceof Select) {
            Select select = (Select) statement;
            SelectBody selectBody = select.getSelectBody();
            if (selectBody instanceof PlainSelect) {
                PlainSelect plainSelect = (PlainSelect) selectBody;

                // 1. 构建别名映射
                Map<String, String> aliasMap = new HashMap<>();
                processFromItem(plainSelect.getFromItem(), aliasMap, new HashMap<>()); // 仅复用 processFromItem 提取别名
                if (plainSelect.getJoins() != null) {
                    for (Join join : plainSelect.getJoins()) {
                        processFromItem(join.getRightItem(), aliasMap, new HashMap<>());
                    }
                }

                // 2. 遍历 Select Items
                for (SelectItem item : plainSelect.getSelectItems()) {
                    item.accept(new SelectItemVisitorAdapter() {
                        @Override
                        public void visit(SelectExpressionItem item) {
                            String outputName = null;
                            if (item.getAlias() != null) {
                                outputName = item.getAlias().getName();
                            } else {
                                // 如果没有别名，尝试从表达式推断
                                Expression expr = item.getExpression();
                                if (expr instanceof Column) {
                                    outputName = ((Column) expr).getColumnName();
                                }
                            }

                            // 暂时忽略无别名的复杂表达式（如 count(*)）
                            // 或者生成一个随机名？对于视图分析，通常关心有明确名字的列。
                            if (outputName != null) {
                                // 移除可能的引号
                                outputName = outputName.replace("\"", "").replace("'", "");

                                Map<String, Set<String>> deps = new HashMap<>();
                                extractFromExpression(item.getExpression(), deps, aliasMap);
                                lineage.put(outputName, deps);
                            }
                        }

                        @Override
                        public void visit(AllColumns columns) {
                            // SELECT * 不支持展开，忽略
                        }

                        @Override
                        public void visit(AllTableColumns columns) {
                            // SELECT t.* 不支持展开，忽略
                        }
                    });
                }
            }
        }
        return lineage;
    }

    private void extractFromSelect(Select select, Map<String, Set<String>> result) {
        // 提取表名别名映射
        Map<String, String> aliasMap = new HashMap<>(); // Alias -> RealTableName

        SelectBody selectBody = select.getSelectBody();
        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;

            // 处理 FROM
            FromItem fromItem = plainSelect.getFromItem();
            processFromItem(fromItem, aliasMap, result);

            // 处理 JOIN
            List<Join> joins = plainSelect.getJoins();
            if (joins != null) {
                for (Join join : joins) {
                    processFromItem(join.getRightItem(), aliasMap, result);
                    try {
                        if (join.getOnExpression() != null) {
                            extractFromExpression(join.getOnExpression(), result, aliasMap);
                        }
                    } catch (IndexOutOfBoundsException e) {
                        // Ignore: jsqlparser bug when ON expression is missing
                    }
                }
            }

            // 处理 WHERE
            Expression where = plainSelect.getWhere();
            extractFromExpression(where, result, aliasMap);

            // 处理 SELECT Items
            for (SelectItem item : plainSelect.getSelectItems()) {
                item.accept(new SelectItemVisitorAdapter() {
                    @Override
                    public void visit(SelectExpressionItem item) {
                        extractFromExpression(item.getExpression(), result, aliasMap);
                    }
                });
            }
        }
    }

    private void processFromItem(FromItem fromItem, Map<String, String> aliasMap, Map<String, Set<String>> result) {
        if (fromItem instanceof Table) {
            Table table = (Table) fromItem;
            String tableName = table.getName();
            // JSqlParser 的 Alias 类包含 name 和 useAs
            String alias = null;
            if (table.getAlias() != null) {
                alias = table.getAlias().getName();
            }
            if (alias == null) {
                alias = tableName;
            }
            aliasMap.put(alias, tableName);

            // result != null 表示这是 extractDependencies 调用，记录所有引用表
            if (result != null) {
                result.computeIfAbsent(tableName, k -> new HashSet<>());
            }
        } else if (fromItem instanceof SubSelect) {
            SubSelect subSelect = (SubSelect) fromItem;
            if (subSelect.getSelectBody() instanceof PlainSelect && result != null) {
                extractFromSelect((Select) new Select().withSelectBody(subSelect.getSelectBody()), result);
            }
            // 子查询别名处理
            if (subSelect.getAlias() != null) {
                // 子查询作为表源，但不需要映射到物理表名（除非递归解析，这里简化）
            }
        }
    }

    // ... extractFromExpression (with 3 args) already exists ...

    private void extractFromExpression(Expression expr, Map<String, Set<String>> result, Map<String, String> aliasMap) {
        if (expr == null)
            return;
        expr.accept(new ExpressionVisitorAdapter() {
            @Override
            public void visit(Column column) {
                String colName = column.getColumnName();
                Table table = column.getTable();
                String tableName = null;
                if (table != null && table.getName() != null) {
                    String alias = table.getName();
                    tableName = aliasMap.getOrDefault(alias, alias);
                }

                // 如果找不到表名（例如 SELECT col FROM t），暂时归类到 "UNKNOWN" 或者 null
                // 为了兼容性，我们尽量找到它归属的表。如果是单表查询，可以推断。
                if (tableName == null && aliasMap.size() == 1) {
                    tableName = aliasMap.values().iterator().next();
                }

                if (tableName != null) {
                    result.computeIfAbsent(tableName, k -> new HashSet<>()).add(colName);
                } else {
                    // 没有表别名，记录到 null key
                    result.computeIfAbsent("UNKNOWN", k -> new HashSet<>()).add(colName);
                }
            }

            @Override
            public void visit(SubSelect subSelect) {
                if (subSelect.getSelectBody() instanceof PlainSelect) {
                    extractFromSelect((Select) new Select().withSelectBody(subSelect.getSelectBody()), result);
                }
            }

            @Override
            public void visit(CaseExpression caseExpression) {
                // 显式遍历 CASE WHEN，虽然 ExpressionVisitorAdapter 会默认遍历，但确保万一
                super.visit(caseExpression);
            }

            @Override
            public void visit(Function function) {
                // 显式遍历函数参数
                super.visit(function);
            }
        });
    }

    private void extractFromExpression(Expression expr, Map<String, Set<String>> result) {
        extractFromExpression(expr, result, Collections.emptyMap());
    }

    /**
     * 降级正则提取（兼容旧逻辑），主要用于非标准 SQL 片段
     */
    private Map<String, Set<String>> extractByRegex(String sql) {
        // 使用 ExprUtils 的逻辑，或者简单的正则提取单词
        Map<String, Set<String>> map = new HashMap<>();
        Set<String> fields = new HashSet<>();
        Matcher m = Pattern.compile("[a-zA-Z0-9_]+").matcher(sql);
        while (m.find()) {
            fields.add(m.group());
        }
        map.put("UNKNOWN", fields);
        return map;
    }
}
