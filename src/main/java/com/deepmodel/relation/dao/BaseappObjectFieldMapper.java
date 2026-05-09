package com.deepmodel.relation.dao;

import com.deepmodel.relation.model.BaseappObjectField;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface BaseappObjectFieldMapper {
    List<BaseappObjectField> selectByObjectType(@Param("objectType") String objectType);

    List<BaseappObjectField> selectWriteBackCandidates();

    List<BaseappObjectField> selectAll();

    /**
     * 查询所有视图定义（name 包含 "View" 的对象）
     */
    List<String> selectViewDefinitions();

    /**
     * 根据对象类型查询 app_name（用于构建表名前缀）
     */
    String selectAppNameByObjectType(@Param("objectType") String objectType);

    /**
     * 查询所有对象类型的标题映射
     */
    List<BaseappObjectField> selectObjectTitles();

    /**
     * 查询 type='bill' 的对象类型名称列表
     */
    List<String> selectBillObjectTypes();

    /**
     * 查询引用了指定对象（通过 refer_info.referEntities[].referEntityName）的所有字段。
     *
     * @param entityName 被引用的对象名，例如 "ArContract"
     */
    List<BaseappObjectField> selectReferencingFields(@Param("entityName") String entityName);

    /**
     * 查询所有枚举类型定义（baseapp_system_metadata 中类型为枚举的条目）
     */
    List<String> selectEnumDefinitions();

    /**
     * 查询所有 LIST 类型字段的 source_info（用于识别子表关系）
     */
    List<BaseappObjectField> selectSourceInfoFields();

    /**
     * 查询支持变更单的实体名称列表（content->>'isSupportChangeBill'='true'）
     */
    List<String> selectChangeBillSupportedEntities();

    // ===== FuncUnit Customizer 查询 =====

    /**
     * 查询对象绑定的 FuncUnit（ObjectTypeFuncUnitCustomizer 数据源）
     * 关联 baseapp_func_unit 获取部件名称、步骤、方法
     */
    List<Map<String, Object>> selectObjectTypeFuncUnits(@Param("entityName") String entityName);

    /**
     * 查询实体业务规则（EntityBusinessRuleCustomizer 数据源）
     * 关联触发动作视图获取部件名称
     */
    List<Map<String, Object>> selectEntityBusinessRules(@Param("entityName") String entityName);

    /**
     * 查询预执行规则（PreDoRuleFuncUnitCustomizer 数据源）
     */
    List<Map<String, Object>> selectPreDoRules(@Param("entityName") String entityName);
}