package com.deepmodel.relation.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExprUtils.extractCrossObjectRefs() 单元测试。
 */
class ExprUtilsCrossObjectTest {

    @Test
    void extractCrossObjectRefs_singleRef_parsed() {
        Map<String, String> refs = ExprUtils.extractCrossObjectRefs("projectId.projectName");
        assertEquals(1, refs.size());
        assertEquals("projectName", refs.get("projectId"));
    }

    @Test
    void extractCrossObjectRefs_multipleRefs_allParsed() {
        String expr = "projectId.projectName + contractId.contractNo * 100";
        Map<String, String> refs = ExprUtils.extractCrossObjectRefs(expr);
        assertEquals(2, refs.size());
        assertEquals("projectName", refs.get("projectId"));
        assertEquals("contractNo", refs.get("contractId"));
    }

    @Test
    void extractCrossObjectRefs_noRefs_emptyMap() {
        Map<String, String> refs = ExprUtils.extractCrossObjectRefs("quantity * unitPrice + taxAmount");
        assertTrue(refs.isEmpty());
    }

    @Test
    void extractCrossObjectRefs_sqlAlias_excluded() {
        // m.id, t.amount 是 SQL 表别名，应排除
        Map<String, String> refs = ExprUtils.extractCrossObjectRefs("m.id + t.amount");
        assertTrue(refs.isEmpty());
    }

    @Test
    void extractCrossObjectRefs_nestedFunction_parsed() {
        String expr = "COALESCE(projectId.projectName, '') + contractId.contractNo";
        Map<String, String> refs = ExprUtils.extractCrossObjectRefs(expr);
        assertEquals(2, refs.size());
        assertEquals("projectName", refs.get("projectId"));
        assertEquals("contractNo", refs.get("contractId"));
    }

    @Test
    void extractCrossObjectRefs_snakeCase_convertedToCamel() {
        String expr = "project_id.project_name";
        Map<String, String> refs = ExprUtils.extractCrossObjectRefs(expr);
        assertEquals(1, refs.size());
        assertEquals("projectName", refs.get("projectId"));
    }

    @Test
    void extractCrossObjectRefs_nullOrEmpty_emptyMap() {
        assertTrue(ExprUtils.extractCrossObjectRefs(null).isEmpty());
        assertTrue(ExprUtils.extractCrossObjectRefs("").isEmpty());
        assertTrue(ExprUtils.extractCrossObjectRefs("   ").isEmpty());
    }
}
