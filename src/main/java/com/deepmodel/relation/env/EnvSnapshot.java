package com.deepmodel.relation.env;

import com.deepmodel.relation.model.BaseappObjectField;
import com.deepmodel.relation.model.CascadeWriteBackInfo;
import com.deepmodel.relation.model.EnumTypeMeta;
import com.deepmodel.relation.model.ExpressionFieldInfo;
import com.deepmodel.relation.model.GraphModels;
import com.deepmodel.relation.model.ObjectTypeMeta;
import com.deepmodel.relation.model.WriteBackRelationInfo;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单个环境（env）下的完整元数据内存快照。
 * <p>
 * 由 {@link EnvSnapshotManager} 按 env key 缓存；每个 env 一份独立 state，
 * 切环境无需重拉。所有原本散落在 ImpactAnalyzerService / WriteBackRelationService /
 * ExpressionFieldService / EntityReferenceService 上的 instance field 都迁到这里。
 */
public class EnvSnapshot {

    /** 环境名 */
    public final String env;

    /** 构建完成时间戳（毫秒） */
    public volatile long builtAt;

    // ====================== ImpactAnalyzerService state ======================
    public volatile List<BaseappObjectField> allRows = Collections.emptyList();
    public final Map<String, List<BaseappObjectField>> rowsByObject = new ConcurrentHashMap<>();
    public final Map<String, String> objectTitles = new ConcurrentHashMap<>();
    public final Map<String, ObjectTypeMeta> objectTypeMetas = new ConcurrentHashMap<>();
    public final Map<String, List<String>> titleToObjectTypes = new ConcurrentHashMap<>();
    public final Map<String, Set<String>> enumValueMap = new ConcurrentHashMap<>();
    public final Map<String, EnumTypeMeta> enumTypeIndex = new ConcurrentHashMap<>();
    public final Map<String, List<String>> enumTitleIndex = new ConcurrentHashMap<>();
    public final Map<String, List<String>> enumFieldIndex = new ConcurrentHashMap<>();
    public final Map<String, String> objectLabels = new HashMap<>();

    public volatile Set<String> billObjectTypes = Collections.emptySet();
    public volatile Set<String> changeBillEntities = Collections.emptySet();

    public final Map<String, Set<String>> mainToDetails = new ConcurrentHashMap<>();
    public final Map<String, String> detailToMain = new ConcurrentHashMap<>();

    public final Map<String, Set<String>> viewReverseDeps = new ConcurrentHashMap<>();
    public final Map<String, Set<String>> viewDirectDeps = new ConcurrentHashMap<>();

    public final Cache<String, GraphModels.Graph> graphCache = CacheBuilder.newBuilder()
            .maximumSize(1000).build();
    public final Cache<String, GraphModels.ExplainResponse> explainCache = CacheBuilder.newBuilder()
            .maximumSize(1000).build();

    // ====================== WriteBackRelationService state ======================
    /** srcObjectType → targetObjectType → Set&lt;WriteBackRelationInfo&gt; */
    public final Map<String, Map<String, Set<WriteBackRelationInfo>>> wbSrcIndex = new ConcurrentHashMap<>();
    /** targetObjectType → targetFieldName → Set&lt;sourceVars&gt; */
    public final Map<String, Map<String, Set<String>>> wbTargetFieldVarsIndex = new ConcurrentHashMap<>();
    /** srcObjectType → List&lt;CascadeWriteBackInfo&gt; */
    public final Map<String, List<CascadeWriteBackInfo>> wbCascadeIndex = new ConcurrentHashMap<>();

    // ====================== ExpressionFieldService state ======================
    /** objectType → ExpressionFieldInfo */
    public final Map<String, ExpressionFieldInfo> exprFieldIndex = new ConcurrentHashMap<>();

    // ====================== EntityReferenceService state ======================
    /** 被引用对象 → 引用对象 → FK字段 → isDetail */
    public final Map<String, Map<String, Map<String, Boolean>>> referIndex = new ConcurrentHashMap<>();

    public EnvSnapshot(String env) {
        this.env = env;
    }

    /** 清空所有内部 state（reload 前调用）。 */
    public void clearAll() {
        allRows = Collections.emptyList();
        rowsByObject.clear();
        objectTitles.clear();
        objectTypeMetas.clear();
        titleToObjectTypes.clear();
        enumValueMap.clear();
        enumTypeIndex.clear();
        enumTitleIndex.clear();
        enumFieldIndex.clear();
        objectLabels.clear();
        billObjectTypes = Collections.emptySet();
        changeBillEntities = Collections.emptySet();
        mainToDetails.clear();
        detailToMain.clear();
        viewReverseDeps.clear();
        viewDirectDeps.clear();
        graphCache.invalidateAll();
        explainCache.invalidateAll();
        wbSrcIndex.clear();
        wbTargetFieldVarsIndex.clear();
        wbCascadeIndex.clear();
        exprFieldIndex.clear();
        referIndex.clear();
    }
}
