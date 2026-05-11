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
        /** 枚举匹配结果，与 objectMatches 平级 */
        public List<EnumMatch> enumMatches = new ArrayList<>();
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
        // 对象特性（US1）
        public Boolean isTree;
        public Boolean isDetail;
        public Boolean isSupportChangeLog;
        public Boolean isCustomizedEntity;
        public Boolean isMultiDataVersion;
        public String appName;
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
        /** 被多少个表达式字段依赖，null 表示未计算 */
        public Integer dependedByCount;
        /** 依赖该字段的表达式字段名列表（最多 5 个） */
        public List<String> dependedByFields;
        /** 回写来源摘要（如 "RevenueConfirmationItem.sum(amount)"），无回写时为 null */
        public String writeBackSource;
        /** 字段关联的枚举值列表，无枚举时为 null */
        public List<EnumValueMeta> enumValues;
        /** 是否为自定义字段 */
        public Boolean isCustomizedField;
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
        /** 特性过滤条件名（如 "isTree"），null 表示无特性过滤 */
        public String traitFilter;
        /** bizType 过滤值（如 "amount"），null 表示无 bizType 过滤 */
        public String bizTypeFilter;
        /** 反向引用查询目标对象（如 "Customer"），null 表示非反向查询 */
        public String reverseRefTarget;
        /** 是否为自定义字段过滤（如 "XX的自定义字段"） */
        public boolean customizedFieldFilter;
    }
}
