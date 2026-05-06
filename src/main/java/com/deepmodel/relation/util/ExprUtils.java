package com.deepmodel.relation.util;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExprUtils {

    private static final Set<String> SQL_STOPWORDS = new HashSet<String>(Arrays.asList(
            ("case when then else end null is not and or in like coalesce nvl abs sum min max avg count distinct " +
             "true false between over partition by row_number dense_rank rank lead lag order group having " +
             "on join inner left right full outer union all exists any some as").split(" ")
    ));

    // 将连字符放在字符类开头，避免转义问题
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("[-+*/%_=<>()',;.]" );

    private static final Pattern SINGLE_QUOTE_STR = Pattern.compile("'([^']*)'");

    private static final Pattern NON_ID = Pattern.compile("[^A-Za-z0-9_\\.]");

    public static String snakeToCamel(String s){
        if(s==null) return null;
        String t = s.replaceAll("^_+|_+$", "");
        if(t.isEmpty()) return t;
        String[] parts = t.split("_");
        StringBuilder sb = new StringBuilder(parts[0].toLowerCase());
        for(int i=1;i<parts.length;i++){
            if(parts[i].isEmpty()) continue;
            sb.append(parts[i].substring(0,1).toUpperCase()).append(parts[i].substring(1));
        }
        return sb.toString();
    }

    /**
     * 将 camelCase 转换为 snake_case
     * 例如：invoiceMakeAppAmountWithoutTaxFrame -> invoice_make_app_amount_without_tax_frame
     */
    public static String camelToSnake(String s){
        if(s==null || s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder();
        for(int i=0; i<s.length(); i++){
            char c = s.charAt(i);
            if(Character.isUpperCase(c)){
                if(i>0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean containsUppercase(String s){
        for(int i=0;i<s.length();i++){
            if(Character.isUpperCase(s.charAt(i))) return true;
        }
        return false;
    }

    public static Set<String> extractCamelFieldsFromSql(String expr){
        Set<String> out = new HashSet<String>();
        if(expr==null || expr.trim().isEmpty()) return out;
        // 去掉单引号字符串
        String noStr = SINGLE_QUOTE_STR.matcher(expr).replaceAll(" ");
        // 用非标识符替换为空格，保留点号用于 alias.field 拆分
        String norm = NON_ID.matcher(noStr).replaceAll(" ");
        String[] tokens = norm.split("\\s+");
        for(String tok : tokens){
            if(tok==null || tok.isEmpty()) continue;
            // 去别名
            int dot = tok.lastIndexOf('.');
            String id = (dot>=0 && dot<tok.length()-1) ? tok.substring(dot+1) : tok;
            String low = id.toLowerCase();
            if(SQL_STOPWORDS.contains(low)) continue;
            
            // 修复：检查是否为有效字段标识符
            if(id.matches("[a-zA-Z_][a-zA-Z0-9_]*")){
                // 下划线字段转驼峰
                if(id.indexOf('_')>=0){
                    String camel = snakeToCamel(id);
                    if(!camel.isEmpty()) out.add(camel);
                } 
                // camelCase 字段：首字母小写且包含大写
                else if(id.length()>1 && Character.isLowerCase(id.charAt(0)) && containsUppercase(id)){
                    out.add(id);
                }
                // 纯小写字段（如 amount, id 等）- 这是修复的关键
                else if(id.matches("[a-z][a-z0-9]*") && id.length() >= 2){
                    // 排除明显的 SQL 关键字和常见函数名
                    if(!isCommonSqlFunction(id)) {
                        out.add(id);
                    }
                }
                continue;
            }
        }
        return out;
    }

    /**
     * 和 {@link #extractCamelFieldsFromSql(String)} 逻辑一致，但保留字段出现顺序，
     * 用于判断一段表达式中是否存在某个字段序列的「连续子序列」。
     */
    public static List<String> extractCamelFieldSequence(String expr) {
        List<String> out = new ArrayList<String>();
        if(expr==null || expr.trim().isEmpty()) return out;
        // 去掉单引号字符串
        String noStr = SINGLE_QUOTE_STR.matcher(expr).replaceAll(" ");
        // 用非标识符替换为空格，保留点号用于 alias.field 拆分
        String norm = NON_ID.matcher(noStr).replaceAll(" ");
        String[] tokens = norm.split("\\s+");
        for(String tok : tokens){
            if(tok==null || tok.isEmpty()) continue;
            // 去别名
            int dot = tok.lastIndexOf('.');
            String id = (dot>=0 && dot<tok.length()-1) ? tok.substring(dot+1) : tok;
            String low = id.toLowerCase();
            if(SQL_STOPWORDS.contains(low)) continue;
            
            // 修复：检查是否为有效字段标识符
            if(id.matches("[a-zA-Z_][a-zA-Z0-9_]*")){
                // 下划线字段转驼峰
                if(id.indexOf('_')>=0){
                    String camel = snakeToCamel(id);
                    if(!camel.isEmpty() && !out.contains(camel)) out.add(camel);
                } 
                // camelCase 字段：首字母小写且包含大写
                else if(id.length()>1 && Character.isLowerCase(id.charAt(0)) && containsUppercase(id)){
                    if(!out.contains(id)) out.add(id);
                }
                // 纯小写字段（如 amount, id 等）- 这是修复的关键
                else if(id.matches("[a-z][a-z0-9]*") && id.length() >= 2){
                    // 排除明显的 SQL 关键字和常见函数名
                    if(!isCommonSqlFunction(id)) {
                        if(!out.contains(id)) out.add(id);
                    }
                }
                continue;
            }
        }
        return out;
    }
    
    /**
     * 判断是否为常见的 SQL 函数名或关键字
     */
    private static boolean isCommonSqlFunction(String id) {
        Set<String> commonFunctions = new HashSet<String>(Arrays.asList(
            "select", "from", "where", "insert", "update", "delete", "create", "drop", "alter",
            "limit", "offset", "desc", "asc", "into", "values", "set", "table", "index",
            "char", "varchar", "text", "int", "bigint", "decimal", "date", "time", "timestamp",
            "year", "month", "day", "hour", "minute", "second"
        ));
        return commonFunctions.contains(id.toLowerCase());
    }

    /**
     * 从表达式中提取跨对象字段引用（foreignKey.fieldName 格式）。
     * <p>
     * 例如 triggerExpr 为 {@code projectId.projectName + contractId.contractNo}，
     * 返回 {@code {projectId -> projectName, contractId -> contractNo}}。
     * <p>
     * 仅匹配 camelCase.camelCase 模式（首字母小写、含大写字母或以 Id 结尾的外键 + 点号 + 字段名），
     * 排除 SQL 关键字和表别名（如 m.id、t.amount）。
     *
     * @param expr 表达式文本
     * @return foreignKeyField → referencedFieldName 的映射，无匹配则返回空 Map
     */
    public static Map<String, String> extractCrossObjectRefs(String expr) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        if (expr == null || expr.trim().isEmpty()) return result;

        // 去掉单引号字符串
        String noStr = SINGLE_QUOTE_STR.matcher(expr).replaceAll(" ");

        // 匹配 word.word 模式（点号连接的两个标识符）
        Pattern dotRef = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\.([a-zA-Z_][a-zA-Z0-9_]*)");
        Matcher m = dotRef.matcher(noStr);

        while (m.find()) {
            String left = m.group(1);
            String right = m.group(2);

            // 排除 SQL 表别名（单字母如 m.id, t.amount）
            if (left.length() <= 1) continue;
            // 排除 SQL 关键字
            if (SQL_STOPWORDS.contains(left.toLowerCase())) continue;
            if (SQL_STOPWORDS.contains(right.toLowerCase())) continue;

            // 将 snake_case 转为 camelCase
            String fkField = left.contains("_") ? snakeToCamel(left) : left;
            String refField = right.contains("_") ? snakeToCamel(right) : right;

            if (fkField != null && !fkField.isEmpty()
                    && refField != null && !refField.isEmpty()) {
                result.put(fkField, refField);
            }
        }
        return result;
    }

    /** 标识主表（当前对象自身）字段的 key */
    public static final String KEY_MAIN = "__MAIN__";

    /**
     * 从 expression SQL 中提取变量字段引用。
     * <p>
     * 返回 Map&lt;String, Set&lt;String&gt;&gt;，其中：
     * <ul>
     *   <li>key = {@link #KEY_MAIN} 表示当前对象自身的字段引用</li>
     *   <li>key = listFieldName（如 "orderItems"）表示子表字段引用</li>
     * </ul>
     * 例如 "sum(items.qty * items.price) + discount" 返回：
     * {__MAIN__ = [discount], items = [qty, price]}
     *
     * @param expression expression SQL 字符串（可能含 sum/count/case when 等）
     * @return 变量字段按所属对象（主表/子表）分组的映射；空表达式返回空 Map
     */
    public static Map<String, Set<String>> extractVariablesFromExpression(String expression) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        if (expression == null || expression.trim().isEmpty()) {
            return result;
        }

        // 移除字符串常量（避免把常量中的单词误判为字段名）
        String cleaned = SINGLE_QUOTE_STR.matcher(expression).replaceAll(" ");
        // 移除数字常量
        cleaned = cleaned.replaceAll("\\b\\d+(\\.\\d+)?\\b", " ");
        // 用非标识符字符切割，但保留 "."（用于识别 listField.xxx）
        String[] tokens = NON_ID.matcher(cleaned).replaceAll(" ").trim().split("\\s+");

        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            // 跳过 SQL 关键字
            if (SQL_STOPWORDS.contains(token.toLowerCase())) {
                continue;
            }
            // 跳过纯数字
            if (token.matches("\\d+")) {
                continue;
            }
            // 跳过下划线开头的占位符（如 ${alias}）
            if (token.startsWith("_") || token.startsWith("$")) {
                continue;
            }

            if (token.contains(".")) {
                // 包含点号：listField.fieldName 或 alias.fieldName
                String[] parts = token.split("\\.", 2);
                String prefix = parts[0];
                String fieldName = parts.length > 1 ? parts[1] : null;
                if (prefix.isEmpty() || fieldName == null || fieldName.isEmpty()) {
                    continue;
                }
                if (SQL_STOPWORDS.contains(prefix.toLowerCase()) || SQL_STOPWORDS.contains(fieldName.toLowerCase())) {
                    continue;
                }
                // 转为 camelCase
                String camelPrefix = prefix.contains("_") ? snakeToCamel(prefix) : prefix;
                String camelField = fieldName.contains("_") ? snakeToCamel(fieldName) : fieldName;
                if (camelPrefix != null && camelField != null) {
                    result.computeIfAbsent(camelPrefix, k -> new LinkedHashSet<>()).add(camelField);
                }
            } else {
                // 单独字段名：主表自身字段
                String camelField = token.contains("_") ? snakeToCamel(token) : token;
                if (camelField != null && !camelField.isEmpty()) {
                    result.computeIfAbsent(KEY_MAIN, k -> new LinkedHashSet<>()).add(camelField);
                }
            }
        }
        return result;
    }
}