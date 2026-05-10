package com.deepmodel.relation.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 自然语言元数据匹配的请求/响应模型。
 *
 * 分层结构：ResolveResult → ObjectMatch → FieldMatch
 */
public class ResolveModels {

    /** 匹配来源枚举 */
    public enum MatchSource {
        /** 精确英文名匹配（PascalCase / camelCase），置信度 1.0 */
        EXACT_NAME,
        /** 同义词精确匹配（GLOBAL_SYNONYMS），置信度 0.9 */
        SYNONYM,
        /** 中文标题精确匹配（objectTitles / field.title），置信度 0.8 */
        TITLE_EXACT,
        /** 中文标题包含匹配，置信度 0.6 */
        TITLE_CONTAINS
    }

    /** 字段业务分类 */
    public enum FieldCategory {
        AMOUNT, QTY, WRITE_BACK, TRIGGER, VIRTUAL, BASE
    }

    /** 匹配结果（顶层） */
    public static class ResolveResult {
        public String query;
        public List<ObjectMatch> objectMatches = new ArrayList<>();
    }

    /** 对象匹配项 */
    public static class ObjectMatch {
        public String objectType;
        public String title;
        public String description;
        public String type;
        public Boolean isDisabled;
        public double score;
        public MatchSource matchSource;
        public List<String> detailEntities = new ArrayList<>();
        public String parentEntity;
        public List<FieldMatch> fieldMatches = new ArrayList<>();
    }

    /** 字段匹配项（嵌套在 ObjectMatch 下） */
    public static class FieldMatch {
        public String field;
        public String title;
        public String description;
        public String enumType;
        public Boolean isDisabled;
        public double score;
        public MatchSource matchSource;
        public String bizType;
        public FieldCategory category;
        public boolean hasWriteBack;
        public boolean hasTrigger;
        /** 引用链路径（如 "projectId → Project"），null 表示直接匹配 */
        public String refPath;
        /** 匹配类型：DIRECT=直接匹配, CHAIN=链式引用, CASCADE=级联搜索 */
        public String matchType;
    }

    /** 分词解析后的查询结构 */
    public static class ParsedQuery {
        /** 对象部分（如 "应收合同"） */
        public String objectPart;
        /** 是否为子表查询 */
        public boolean isDetailQuery;
        /** 子表导航词（如 "子表"、"明细"） */
        public String detailNavWord;
        /** 字段部分（如 "收款金额"） */
        public String fieldPart;
    }
}
