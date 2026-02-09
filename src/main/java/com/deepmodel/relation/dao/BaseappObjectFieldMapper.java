package com.deepmodel.relation.dao;

import com.deepmodel.relation.model.BaseappObjectField;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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
}