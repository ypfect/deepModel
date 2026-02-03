package com.deepmodel.relation.dao;

import com.deepmodel.relation.model.ProjectionModels.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 单据映射关系数据访问接口
 */
@Mapper
public interface ProjectionMapper {

    /**
     * 查询所有映射关系
     */
    List<Projection> findAllProjections();

    /**
     * 根据ID查询映射关系
     */
    Projection findProjectionById(@Param("id") String id);

    /**
     * 根据源对象查询下游映射
     */
    List<Projection> findDownstreamProjections(@Param("objectType") String objectType);

    /**
     * 根据目标对象查询上游映射
     */
    List<Projection> findUpstreamProjections(@Param("toObjectType") String toObjectType);

    /**
     * 查询所有涉及的对象类型
     */
    List<String> findAllObjectTypes();

    /**
     * 查询映射的主表字段配置
     */
    List<FieldMapping> findFieldMappings(@Param("projectionId") String projectionId);

    /**
     * 查询映射的子表配置
     */
    List<ProjectionChild> findProjectionChildren(@Param("projectionId") String projectionId);

    /**
     * 查询子表的字段映射配置
     */
    List<ProjectionChildFieldMapping> findChildFieldMappings(@Param("childId") String childId);

    /**
     * 字段追踪 - 主表字段
     */
    List<FieldTrackResult> trackFieldInMain(@Param("field") String field);

    /**
     * 字段追踪 - 子表字段
     */
    List<FieldTrackResult> trackFieldInChild(@Param("field") String field);

    /**
     * 统计映射关系的字段数量
     */
    int countFieldMappings(@Param("projectionId") String projectionId);

    /**
     * 统计映射关系的子表数量
     */
    int countChildren(@Param("projectionId") String projectionId);
}
