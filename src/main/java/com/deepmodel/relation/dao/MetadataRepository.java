package com.deepmodel.relation.dao;

import com.deepmodel.relation.model.BaseappObjectField;
import com.deepmodel.relation.model.ObjectTypeMeta;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class MetadataRepository {

    private static final String OBJECT_FIELD_SELECTION = """
            id
            objectType
            name
            apiName
            title
            type
            bizType
            expression
            triggerExpr
            virtualExpr
            writeBackExpr
            referInfo
            sourceInfo
            enumType
            isDisabled
            isCustomizedField
            """;

    private final MetadataGraphQLClient client;
    private final ObjectMapper objectMapper;

    public MetadataRepository(MetadataGraphQLClient client) {
        this.client = client;
        this.objectMapper = client.getObjectMapper();
    }

    public List<BaseappObjectField> selectByObjectType(String objectType) {
        String criteria = "objectType='" + MetadataGraphQLClient.escapeSqlLiteral(objectType) + "'";
        List<BaseappObjectField> fields = mapObjectFields(
                client.asList(client.queryRoot("ObjectField", criteria, OBJECT_FIELD_SELECTION)));
        String appName = selectAppNameByObjectType(objectType);
        if (appName != null) {
            for (BaseappObjectField f : fields) {
                f.setAppName(appName);
            }
        }
        return fields;
    }

    public List<BaseappObjectField> selectWriteBackCandidates() {
        return mapObjectFields(client.queryAllPages(
                "ObjectField",
                "COALESCE(writeBackExpr::text,'')!=''",
                OBJECT_FIELD_SELECTION));
    }

    public List<BaseappObjectField> selectAll() {
        return mapObjectFields(client.queryAllPages("ObjectField", "1=1", OBJECT_FIELD_SELECTION));
    }

    /**
     * 跨环境拉取「bill 类型对象」的字段定义（供 Version Comparison 远程拉取使用）。
     *
     * @param env       指定环境名（不影响当前 EnvContext）
     * @param appNames  可选 appName 过滤，为空表示不过滤
     */
    public List<BaseappObjectField> selectBillFieldsForEnv(String env, List<String> appNames) {
        StringBuilder criteria = new StringBuilder(
                "objectType in (select name from ObjectType where COALESCE(type,'')='bill'");
        if (appNames != null && !appNames.isEmpty()) {
            criteria.append(" and appName in (");
            for (int i = 0; i < appNames.size(); i++) {
                if (i > 0) criteria.append(",");
                criteria.append("'").append(MetadataGraphQLClient.escapeSqlLiteral(appNames.get(i))).append("'");
            }
            criteria.append(")");
        }
        criteria.append(")");
        List<BaseappObjectField> fields = mapObjectFields(
                client.queryAllPagesWithEnv(env, "ObjectField", criteria.toString(), OBJECT_FIELD_SELECTION));
        enrichFieldAppNames(fields, loadObjectTypeAppNameMap(env));
        return fields;
    }

    /** objectType → appName，用于内存补全字段 appName（避免 ObjectField 行级 exprField 关联）。 */
    public Map<String, String> loadObjectTypeAppNameMap(String env) {
        List<JsonNode> rows = (env == null || env.isBlank())
                ? client.queryAllPages("ObjectType", "1=1", "name\nappName")
                : client.queryAllPagesWithEnv(env, "ObjectType", "1=1", "name\nappName");
        Map<String, String> map = new HashMap<>();
        for (JsonNode row : rows) {
            String name = text(row, "name");
            String appName = text(row, "appName");
            if (name != null && !name.isBlank() && appName != null && !appName.isBlank()) {
                map.put(name.trim(), appName.trim());
            }
        }
        return map;
    }

    public void enrichFieldAppNames(List<BaseappObjectField> fields, Map<String, String> objectTypeAppNames) {
        if (fields == null || objectTypeAppNames == null || objectTypeAppNames.isEmpty()) {
            return;
        }
        for (BaseappObjectField f : fields) {
            if (f.getObjectType() == null) {
                continue;
            }
            String app = objectTypeAppNames.get(f.getObjectType());
            if (app != null) {
                f.setAppName(app);
            }
        }
    }

    /** 查询当前环境中存在的全部 appName（聚合 distinct），供前端下拉过滤。 */
    public List<String> selectDistinctAppNames() {
        String gql = "{\n  AggregateQueryOne(entity:\"ObjectType\",criteriaStr:\"\") {\n"
                + "    apps: aggr(expr: \"string_agg(distinct quote_literal(appName), ',')\")\n"
                + "  }\n}";
        JsonNode data = client.execute(gql).path("AggregateQueryOne").path("apps");
        if (data == null || data.isNull() || !data.isTextual()) {
            return new ArrayList<>();
        }
        String raw = data.asText().trim();
        if (raw.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String item : raw.split(",")) {
            String stripped = item.trim();
            if (stripped.startsWith("'") && stripped.endsWith("'") && stripped.length() >= 2) {
                stripped = stripped.substring(1, stripped.length() - 1);
            }
            if (!stripped.isEmpty() && !"NULL".equalsIgnoreCase(stripped)) {
                result.add(stripped);
            }
        }
        return result;
    }

    /** 跨环境版本：从指定 env 拉取 distinct appName 列表。 */
    public List<String> selectDistinctAppNamesForEnv(String env) {
        String gql = "{\n  AggregateQueryOne(entity:\"ObjectType\",criteriaStr:\"\") {\n"
                + "    apps: aggr(expr: \"string_agg(distinct quote_literal(appName), ',')\")\n"
                + "  }\n}";
        JsonNode data = (env == null || env.isBlank())
                ? client.execute(gql).path("AggregateQueryOne").path("apps")
                : client.executeWithEnv(env, gql).path("AggregateQueryOne").path("apps");
        if (data == null || data.isNull() || !data.isTextual()) {
            return new ArrayList<>();
        }
        String raw = data.asText().trim();
        if (raw.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String item : raw.split(",")) {
            String stripped = item.trim();
            if (stripped.startsWith("'") && stripped.endsWith("'") && stripped.length() >= 2) {
                stripped = stripped.substring(1, stripped.length() - 1);
            }
            if (!stripped.isEmpty() && !"NULL".equalsIgnoreCase(stripped)) {
                result.add(stripped);
            }
        }
        return result;
    }

    public List<String> selectViewDefinitions() {
        String criteria = "name like '%View%' and isDeleted=false and typeId='MetaType.entity' "
                + "and name in (select name from ObjectType where COALESCE(type,'')='bill' "
                + "and lower(COALESCE(appName,'')) in ('arap','purchase','sales','contract'))";
        List<JsonNode> rows = client.queryAllPages("SystemMetadata", criteria, "content");
        List<String> result = new ArrayList<>();
        for (JsonNode row : rows) {
            String content = jsonToText(row.get("content"));
            if (content != null && !content.isBlank()) {
                result.add(content);
            }
        }
        return result;
    }

    public String selectAppNameByObjectType(String objectType) {
        String criteria = "name='" + MetadataGraphQLClient.escapeSqlLiteral(objectType) + "' limit 1";
        JsonNode rows = client.queryRoot("ObjectType", criteria, "appName");
        if (rows.isArray() && !rows.isEmpty()) {
            JsonNode appName = rows.get(0).get("appName");
            return appName != null && !appName.isNull() ? appName.asText() : null;
        }
        return null;
    }

    public List<ObjectTypeMeta> selectObjectTitles() {
        String selection = """
                name
                title
                type
                description
                isDisabled
                appName
                isCustomizedEntity
                isDetail
                isTree
                isMultiDataVersion
                isSupportChangeBill
                """;
        List<JsonNode> rows = client.queryAllPages("ObjectType", "1=1", selection);
        List<ObjectTypeMeta> result = new ArrayList<>();
        for (JsonNode row : rows) {
            ObjectTypeMeta meta = new ObjectTypeMeta();
            meta.setName(text(row, "name"));
            meta.setTitle(text(row, "title"));
            meta.setType(text(row, "type"));
            meta.setDescription(text(row, "description"));
            meta.setIsDisabled(bool(row, "isDisabled"));
            meta.setAppName(text(row, "appName"));
            meta.setIsCustomizedEntity(bool(row, "isCustomizedEntity"));
            meta.setIsDetail(bool(row, "isDetail"));
            meta.setIsTree(bool(row, "isTree"));
            meta.setIsMultiDataVersion(bool(row, "isMultiDataVersion"));
            meta.setIsSupportChangeLog(bool(row, "isSupportChangeBill"));
            result.add(meta);
        }
        return result;
    }

    public List<String> selectBillObjectTypes() {
        List<JsonNode> rows = client.queryAllPages("ObjectType", "COALESCE(type,'')='bill'", "name");
        List<String> result = new ArrayList<>();
        for (JsonNode row : rows) {
            String name = text(row, "name");
            if (name != null && !name.isBlank()) {
                result.add(name);
            }
        }
        return result;
    }

    public List<BaseappObjectField> selectReferencingFields(String entityName) {
        String escaped = MetadataGraphQLClient.escapeSqlLiteral(entityName);
        String criteria = "referInfo IS NOT NULL AND referInfo::text != 'null' "
                + "AND jsonb_typeof(referInfo->'referEntities') = 'array' "
                + "AND EXISTS ( SELECT 1 FROM jsonb_array_elements(m.referInfo->'referEntities') elem "
                + "WHERE elem->>'referEntityName' = '" + escaped + "' )";
        String selection = """
                id
                objectType
                name
                apiName
                title
                type
                bizType
                referInfo
                """;
        List<BaseappObjectField> fields = mapObjectFields(client.queryAllPages("ObjectField", criteria, selection));
        enrichFieldAppNames(fields, loadObjectTypeAppNameMap(null));
        return fields;
    }

    public List<String> selectEnumDefinitions() {
        List<JsonNode> rows = client.queryAllPages(
                "SystemMetadata", "isDeleted=false and lower(typeId) like '%enum%'", "content");
        List<String> result = new ArrayList<>();
        for (JsonNode row : rows) {
            String content = jsonToText(row.get("content"));
            if (content != null && !content.isBlank()) {
                result.add(content);
            }
        }
        return result;
    }

    public List<BaseappObjectField> selectSourceInfoFields() {
        String selection = """
                objectType
                name
                type
                sourceInfo
                referInfo
                """;
        return mapObjectFields(client.queryAllPages(
                "ObjectField",
                "lower(type)='list' and sourceInfo IS NOT NULL and sourceInfo::text != 'null'",
                selection));
    }

    public List<String> selectChangeBillSupportedEntities() {
        List<JsonNode> rows = client.queryAllPages(
                "SystemMetadata",
                "isDeleted=false and typeId='MetaType.entity' and content::jsonb->>'isSupportChangeBill'='true'",
                "name");
        List<String> result = new ArrayList<>();
        for (JsonNode row : rows) {
            String name = text(row, "name");
            if (name != null && !name.isBlank()) {
                result.add(name);
            }
        }
        return result;
    }

    public List<Map<String, Object>> selectEntityMetadataContents() {
        return mapNameContentRows(client.queryAllPages(
                "SystemMetadata",
                "isDeleted=false and typeId='MetaType.entity' and content IS NOT NULL",
                "name\ncontent"));
    }

    public List<Map<String, Object>> selectCustomizedMetadataContents() {
        return mapNameContentRows(client.queryAllPages(
                "CustomizedMetadata",
                "isDeleted=false and typeId='MetaType.entity' and content IS NOT NULL",
                "name\ncontent"));
    }

    public List<Map<String, Object>> selectObjectTypeFuncUnits(String entityName) {
        String escaped = MetadataGraphQLClient.escapeSqlLiteral(entityName);
        String criteria = "objectType='" + escaped + "' and isDeleted=false and funcUnitIdObject.isDeleted=false limit 100000";
        String selection = """
                id
                func_unit_name:exprField(expr:"funcUnitIdObject.name")
                method_step_type:exprField(expr:"funcUnitIdObject.methodStepType")
                exec_base_method:exprField(expr:"funcUnitIdObject.execBaseMethod")
                func_unit_type_id:exprField(expr:"funcUnitIdObject.funcUnitTypeId")
                """;
        List<JsonNode> rows = client.asList(client.queryRoot("ObjectTypeFuncUnit", criteria, selection));
        return mapAliasRows(rows);
    }

    public List<Map<String, Object>> selectEntityBusinessRules(String entityName) {
        String escaped = MetadataGraphQLClient.escapeSqlLiteral(entityName);
        String ebrFilter = "objectType='" + escaped + "' and isDeleted=false and COALESCE(isDisabled,false)=false";

        Map<String, JsonNode> triggerToRule = new LinkedHashMap<>();
        List<JsonNode> triggers = client.asList(client.queryRoot(
                "EntityRuleTrigger",
                "entityRuleId in (select id from EntityBusinessRule where " + ebrFilter + ")",
                """
                        triggerId
                        entityRuleIdObject {
                          id
                          ruleId
                          isBindingUse
                          isDisabled
                        }
                        """));
        for (JsonNode trigger : triggers) {
            String triggerId = text(trigger, "triggerId");
            if (triggerId != null) {
                triggerToRule.putIfAbsent(triggerId, trigger);
            }
        }

        List<JsonNode> actionSpecs = client.asList(client.queryRoot(
                "BusinessTriggerActionSpec",
                "triggerId in (select triggerId from EntityRuleTrigger where entityRuleId in "
                        + "(select id from EntityBusinessRule where " + ebrFilter + ")) "
                        + "and actionSpecIdObject.liveStyleId='ActionLiveStyle.plugin' "
                        + "and COALESCE(actionSpecIdObject.funcUnitName,'')!='' limit 100000",
                """
                        id
                        funcUnitStep
                        triggerId
                        triggerIdObject { baseMethod }
                        actionSpecIdObject {
                          funcUnitName
                          funcUnitStep
                          funcUnitTypeId
                          liveStyleId
                        }
                        """));

        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode row : actionSpecs) {
            Map<String, Object> map = new HashMap<>();
            String triggerId = text(row, "triggerId");
            JsonNode trigger = triggerToRule.get(triggerId);
            JsonNode rule = trigger != null ? trigger.path("entityRuleIdObject") : null;
            JsonNode actionSpec = row.path("actionSpecIdObject");
            JsonNode triggerObj = row.path("triggerIdObject");

            map.put("id", rule != null && !rule.isMissingNode() ? text(rule, "id") : null);
            map.put("rule_id", rule != null && !rule.isMissingNode() ? text(rule, "ruleId") : null);
            map.put("is_binding_use", rule != null && !rule.isMissingNode() ? bool(rule, "isBindingUse") : null);
            map.put("is_disabled", rule != null && !rule.isMissingNode() ? bool(rule, "isDisabled") : null);
            map.put("func_unit_name", text(actionSpec, "funcUnitName"));
            map.put("func_unit_step", resolveFuncUnitStep(text(row, "funcUnitStep"), text(actionSpec, "funcUnitStep")));
            map.put("func_unit_type_id", text(actionSpec, "funcUnitTypeId"));
            map.put("trigger_base_method", text(triggerObj, "baseMethod"));
            map.put("action_spec_style_id", text(actionSpec, "liveStyleId"));
            result.add(map);
        }
        return result;
    }

    public List<Map<String, Object>> selectPreDoRules(String entityName) {
        String escaped = MetadataGraphQLClient.escapeSqlLiteral(entityName);
        String criteria = "objectType='" + escaped + "' and isDeleted=false limit 10";
        List<JsonNode> rows = client.asList(client.queryRoot(
                "PreDoRuleGroup", criteria, "id\nname\nobjectType"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode row : rows) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", text(row, "id"));
            map.put("name", text(row, "name"));
            map.put("object_type", text(row, "objectType"));
            result.add(map);
        }
        return result;
    }

    private List<BaseappObjectField> mapObjectFields(List<JsonNode> rows) {
        List<BaseappObjectField> result = new ArrayList<>();
        for (JsonNode row : rows) {
            BaseappObjectField field = new BaseappObjectField();
            field.setId(text(row, "id"));
            field.setObjectType(text(row, "objectType"));
            field.setName(text(row, "name"));
            field.setApiName(text(row, "apiName"));
            field.setTitle(text(row, "title"));
            field.setType(text(row, "type"));
            field.setBizType(text(row, "bizType"));
            field.setExpression(text(row, "expression"));
            field.setTriggerExpr(text(row, "triggerExpr"));
            field.setVirtualExpr(text(row, "virtualExpr"));
            field.setWriteBackExpr(jsonToText(row.get("writeBackExpr")));
            field.setReferInfo(jsonToText(row.get("referInfo")));
            field.setSourceInfo(jsonToText(row.get("sourceInfo")));
            field.setEnumType(text(row, "enumType"));
            field.setIsDisabled(bool(row, "isDisabled"));
            field.setIsCustomizedField(bool(row, "isCustomizedField"));
            field.setAppName(text(row, "appName"));
            result.add(field);
        }
        return result;
    }

    private List<Map<String, Object>> mapNameContentRows(List<JsonNode> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode row : rows) {
            Map<String, Object> map = new HashMap<>();
            map.put("name", text(row, "name"));
            map.put("content", jsonToText(row.get("content")));
            result.add(map);
        }
        return result;
    }

    private List<Map<String, Object>> mapAliasRows(List<JsonNode> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode row : rows) {
            Map<String, Object> map = new HashMap<>();
            MetadataGraphQLClient.forEachField(row, (key, value) -> {
                if (value == null || value.isNull()) {
                    map.put(key, null);
                } else if (value.isTextual()) {
                    map.put(key, value.asText());
                } else if (value.isBoolean()) {
                    map.put(key, value.asBoolean());
                } else if (value.isNumber()) {
                    map.put(key, value.numberValue());
                } else {
                    map.put(key, value.toString());
                }
            });
            result.add(map);
        }
        return result;
    }

    private static String resolveFuncUnitStep(String btasStep, String actionStep) {
        if (btasStep != null && !btasStep.isBlank()) {
            return btasStep;
        }
        return actionStep;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node != null ? node.get(field) : null;
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private Boolean bool(JsonNode node, String field) {
        JsonNode value = node != null ? node.get(field) : null;
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asBoolean();
    }

    private String jsonToText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return node.toString();
        }
    }
}
