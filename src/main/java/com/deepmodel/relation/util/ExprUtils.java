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
            // 下划线字段
            if(id.matches("[a-z_][a-z0-9_]*")){
                if(id.indexOf('_')>=0){
                    String camel = snakeToCamel(id);
                    if(!camel.isEmpty()) out.add(camel);
                } else {
                    // 全小写无下划线，通常不是字段（可能别名/函数），忽略
                }
                continue;
            }
            // camelCase 字段：首字母小写且包含大写
            if(id.length()>1 && Character.isLowerCase(id.charAt(0)) && containsUppercase(id)){
                out.add(id);
            }
        }
        return out;
    }
}
