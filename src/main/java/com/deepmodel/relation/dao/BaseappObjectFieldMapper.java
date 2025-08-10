package com.deepmodel.relation.dao;

import com.deepmodel.relation.model.BaseappObjectField;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface BaseappObjectFieldMapper {
    List<BaseappObjectField> selectByObjectType(@Param("objectType") String objectType);

    List<BaseappObjectField> selectWriteBackCandidates(@Param("objectType") String objectType);

    List<BaseappObjectField> selectAll();
}
