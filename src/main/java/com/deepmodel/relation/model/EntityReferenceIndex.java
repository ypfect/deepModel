package com.deepmodel.relation.model;

/**
 * 对象引用关系反向索引项。
 * <p>
 * 全量索引结构：{@code Map<被引用对象, Map<引用对象, Map<FK字段, Boolean(isDetail)>>>}，
 * 其中 key "ALL" 表示多态引用。
 */
public class EntityReferenceIndex {
    private String referredEntity;
    private String referringEntity;
    private String fkFieldName;
    private boolean isDetail;

    public EntityReferenceIndex() {}

    public EntityReferenceIndex(String referredEntity, String referringEntity, String fkFieldName, boolean isDetail) {
        this.referredEntity = referredEntity;
        this.referringEntity = referringEntity;
        this.fkFieldName = fkFieldName;
        this.isDetail = isDetail;
    }

    public String getReferredEntity() { return referredEntity; }
    public void setReferredEntity(String referredEntity) { this.referredEntity = referredEntity; }

    public String getReferringEntity() { return referringEntity; }
    public void setReferringEntity(String referringEntity) { this.referringEntity = referringEntity; }

    public String getFkFieldName() { return fkFieldName; }
    public void setFkFieldName(String fkFieldName) { this.fkFieldName = fkFieldName; }

    public boolean isDetail() { return isDetail; }
    public void setDetail(boolean detail) { isDetail = detail; }
}
