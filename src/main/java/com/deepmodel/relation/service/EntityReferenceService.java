package com.deepmodel.relation.service;

import com.deepmodel.relation.model.BaseappObjectField;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对象引用关系反向索引服务。
 * <p>
 * 从 refer_info JSON 中解析 referEntities，构建：
 * {@code Map<被引用对象, Map<引用对象, Map<FK字段, Boolean(isDetail)>>>}
 * <p>
 * 多态引用（referEntityFieldName 不为空）归入 "ALL" 键。
 */
@Service
public class EntityReferenceService {

    private static final Logger log = LoggerFactory.getLogger(EntityReferenceService.class);

    /** 多态引用的特殊 key */
    public static final String KEY_ALL = "ALL";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
            .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
            .configure(JsonParser.Feature.ALLOW_COMMENTS, true)
            .configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true);

    /** 被引用对象 → 引用对象 → FK字段 → isDetail */
    private final Map<String, Map<String, Map<String, Boolean>>> referIndex = new ConcurrentHashMap<>();

    /**
     * 从字段列表构建引用关系反向索引。
     *
     * @param allRows 所有字段记录
     */
    public void buildIndex(List<BaseappObjectField> allRows) {
        long t0 = System.currentTimeMillis();
        referIndex.clear();

        for (BaseappObjectField row : allRows) {
            if (row.getReferInfo() == null || row.getReferInfo().trim().isEmpty()
                    || "null".equals(row.getReferInfo().trim())) {
                continue;
            }
            try {
                JsonNode ri = objectMapper.readTree(row.getReferInfo());
                JsonNode referEntities = ri.get("referEntities");
                if (referEntities == null || !referEntities.isArray()) {
                    continue;
                }

                // 检查是否多态引用
                String referEntityFieldName = ri.has("referEntityFieldName")
                        ? ri.get("referEntityFieldName").asText(null) : null;
                boolean isPolymorphic = referEntityFieldName != null && !referEntityFieldName.isEmpty();

                for (JsonNode entity : referEntities) {
                    String referEntityName = entity.has("referEntityName")
                            ? entity.get("referEntityName").asText(null) : null;
                    if (referEntityName == null || referEntityName.isEmpty()) {
                        continue;
                    }
                    boolean isDetail = entity.has("isDetail") && entity.get("isDetail").asBoolean(false);

                    String indexKey = isPolymorphic ? KEY_ALL : referEntityName;

                    referIndex.computeIfAbsent(indexKey, k -> new ConcurrentHashMap<>())
                            .computeIfAbsent(row.getObjectType(), k -> new LinkedHashMap<>())
                            .put(row.getName(), isDetail);
                }
            } catch (Exception e) {
                log.warn("Failed to parse refer_info for {}.{}: {}",
                        row.getObjectType(), row.getName(), e.getMessage());
            }
        }

        long elapsed = System.currentTimeMillis() - t0;
        log.info("Built entity reference index: {} referred entities in {}ms", referIndex.size(), elapsed);
    }

    /**
     * 查询指定对象被谁引用。
     *
     * @param referredEntity 被引用的对象类型名
     * @return 引用对象 → FK字段 → isDetail；未找到返回空 Map
     */
    public Map<String, Map<String, Boolean>> getReferRelations(String referredEntity) {
        Map<String, Map<String, Boolean>> result = referIndex.get(referredEntity);
        return result != null ? Collections.unmodifiableMap(result) : Collections.emptyMap();
    }

    /**
     * 查询全量引用关系索引。
     *
     * @return 被引用对象 → 引用对象 → FK字段 → isDetail
     */
    public Map<String, Map<String, Map<String, Boolean>>> getAllReferRelations() {
        return Collections.unmodifiableMap(referIndex);
    }
}
