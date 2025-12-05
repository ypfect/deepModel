package com.deepmodel.relation.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

/**
 * 核销对象实体类
 */
public class BaseappVerifyObject {
    private String id;
    private Integer ordinal;
    private String entrySrcSystemId;
    private String externalSystemCode;
    private String externalObjectType;
    private String externalObjectId;
    private String name;
    private String objectType;
    private String itemObjectType;
    private String condition;
    private String originVerifyFieldName;
    private String verifyFieldName;
    private String originAmountWithoutTaxVerifyFieldName;
    private String amountWithoutTaxVerifyFieldName;
    private String reserveOriginVerifyFieldName;
    private String reserveVerifyFieldName;
    private String currencyIdExpr;
    private String businessDateExpr;
    private String exchangeRateExpr;
    private JsonNode dimFieldMappings;
    private JsonNode mappings;
    private JsonNode sortFieldMappings;
    private String mainObjectName;
    private String verifyObjectGroupId;
    private String verifySystemId;
    private String autoVerifyCondition;
    private Boolean isVerifySrcBizObject;
    private String sameSourceDimField;
    private String beanName;
    private JsonNode qtyMappings;
    private String baseQtyVerifyField;
    private String auxQtyVerifyField;
    private String transQtyVerifyField;
    private String transAuxQtyVerifyField;
    private String productField;
    private String baseUnitField;
    private String auxUnitField;
    private String transUnitField;
    private String transAuxUnitField;
    private String transUnitCnvTypeIdField;
    private String transAuxToBaseRateField;
    private String transCnvNumeratorField;
    private String transCnvDenominatorField;
    private String cnvDirectionIdField;
    private String baseAuxToBaseRateField;
    private String baseCnvDenominatorField;
    private String baseCnvNumeratorField;
    private String resultAmountField;
    private String resultOriginAmountField;
    private String resultOriginAmountWithoutTaxField;
    private String resultAmountWithoutTaxField;
    private String resultBaseQtyField;
    private String resultAuxQtyField;
    private String resultTransQtyField;
    private String resultTransAuxQtyField;
    private JsonNode extendFieldList;
    private String createdUserId;
    private LocalDateTime createdTime;
    private String modifiedUserId;
    private LocalDateTime modifiedTime;
    private Boolean isSystem;
    private Boolean isInitData;
    private Boolean isDeleted;
    private Long dataVersion;
    private String lastRequestId;
    private String lastModifiedUserId;
    private LocalDateTime lastModifiedTime;
    private JsonNode customizedFields;
    private JsonNode sameSourceFieldCond;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Integer getOrdinal() { return ordinal; }
    public void setOrdinal(Integer ordinal) { this.ordinal = ordinal; }

    public String getEntrySrcSystemId() { return entrySrcSystemId; }
    public void setEntrySrcSystemId(String entrySrcSystemId) { this.entrySrcSystemId = entrySrcSystemId; }

    public String getExternalSystemCode() { return externalSystemCode; }
    public void setExternalSystemCode(String externalSystemCode) { this.externalSystemCode = externalSystemCode; }

    public String getExternalObjectType() { return externalObjectType; }
    public void setExternalObjectType(String externalObjectType) { this.externalObjectType = externalObjectType; }

    public String getExternalObjectId() { return externalObjectId; }
    public void setExternalObjectId(String externalObjectId) { this.externalObjectId = externalObjectId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }

    public String getItemObjectType() { return itemObjectType; }
    public void setItemObjectType(String itemObjectType) { this.itemObjectType = itemObjectType; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public String getOriginVerifyFieldName() { return originVerifyFieldName; }
    public void setOriginVerifyFieldName(String originVerifyFieldName) { this.originVerifyFieldName = originVerifyFieldName; }

    public String getVerifyFieldName() { return verifyFieldName; }
    public void setVerifyFieldName(String verifyFieldName) { this.verifyFieldName = verifyFieldName; }

    public String getOriginAmountWithoutTaxVerifyFieldName() { return originAmountWithoutTaxVerifyFieldName; }
    public void setOriginAmountWithoutTaxVerifyFieldName(String originAmountWithoutTaxVerifyFieldName) { this.originAmountWithoutTaxVerifyFieldName = originAmountWithoutTaxVerifyFieldName; }

    public String getAmountWithoutTaxVerifyFieldName() { return amountWithoutTaxVerifyFieldName; }
    public void setAmountWithoutTaxVerifyFieldName(String amountWithoutTaxVerifyFieldName) { this.amountWithoutTaxVerifyFieldName = amountWithoutTaxVerifyFieldName; }

    public String getReserveOriginVerifyFieldName() { return reserveOriginVerifyFieldName; }
    public void setReserveOriginVerifyFieldName(String reserveOriginVerifyFieldName) { this.reserveOriginVerifyFieldName = reserveOriginVerifyFieldName; }

    public String getReserveVerifyFieldName() { return reserveVerifyFieldName; }
    public void setReserveVerifyFieldName(String reserveVerifyFieldName) { this.reserveVerifyFieldName = reserveVerifyFieldName; }

    public String getCurrencyIdExpr() { return currencyIdExpr; }
    public void setCurrencyIdExpr(String currencyIdExpr) { this.currencyIdExpr = currencyIdExpr; }

    public String getBusinessDateExpr() { return businessDateExpr; }
    public void setBusinessDateExpr(String businessDateExpr) { this.businessDateExpr = businessDateExpr; }

    public String getExchangeRateExpr() { return exchangeRateExpr; }
    public void setExchangeRateExpr(String exchangeRateExpr) { this.exchangeRateExpr = exchangeRateExpr; }

    public JsonNode getDimFieldMappings() { return dimFieldMappings; }
    public void setDimFieldMappings(JsonNode dimFieldMappings) { this.dimFieldMappings = dimFieldMappings; }

    public JsonNode getMappings() { return mappings; }
    public void setMappings(JsonNode mappings) { this.mappings = mappings; }

    public JsonNode getSortFieldMappings() { return sortFieldMappings; }
    public void setSortFieldMappings(JsonNode sortFieldMappings) { this.sortFieldMappings = sortFieldMappings; }

    public String getMainObjectName() { return mainObjectName; }
    public void setMainObjectName(String mainObjectName) { this.mainObjectName = mainObjectName; }

    public String getVerifyObjectGroupId() { return verifyObjectGroupId; }
    public void setVerifyObjectGroupId(String verifyObjectGroupId) { this.verifyObjectGroupId = verifyObjectGroupId; }

    public String getVerifySystemId() { return verifySystemId; }
    public void setVerifySystemId(String verifySystemId) { this.verifySystemId = verifySystemId; }

    public String getAutoVerifyCondition() { return autoVerifyCondition; }
    public void setAutoVerifyCondition(String autoVerifyCondition) { this.autoVerifyCondition = autoVerifyCondition; }

    public Boolean getIsVerifySrcBizObject() { return isVerifySrcBizObject; }
    public void setIsVerifySrcBizObject(Boolean isVerifySrcBizObject) { this.isVerifySrcBizObject = isVerifySrcBizObject; }

    public String getSameSourceDimField() { return sameSourceDimField; }
    public void setSameSourceDimField(String sameSourceDimField) { this.sameSourceDimField = sameSourceDimField; }

    public String getBeanName() { return beanName; }
    public void setBeanName(String beanName) { this.beanName = beanName; }

    public JsonNode getQtyMappings() { return qtyMappings; }
    public void setQtyMappings(JsonNode qtyMappings) { this.qtyMappings = qtyMappings; }

    public Boolean getIsSystem() { return isSystem; }
    public void setIsSystem(Boolean isSystem) { this.isSystem = isSystem; }

    public Boolean getIsInitData() { return isInitData; }
    public void setIsInitData(Boolean isInitData) { this.isInitData = isInitData; }

    public Boolean getIsDeleted() { return isDeleted; }
//    public void setIsDeleted(String isDeleted) { this.isDeleted = isDeleted; }

    // 省略其他getter/setter方法...

    @Override
    public String toString() {
        return "BaseappVerifyObject{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", objectType='" + objectType + '\'' +
                ", isInitData=" + isInitData +
                '}';
    }
}
