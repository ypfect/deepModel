package com.deepmodel.relation.util;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ExprUtilsExpressionVarsTest {

    @Test
    void extractVariables_mainFieldOnly_returnsSingleMainKey() {
        Map<String, Set<String>> result = ExprUtils.extractVariablesFromExpression("qty * unit_price");
        assertTrue(result.containsKey(ExprUtils.KEY_MAIN));
        Set<String> mainFields = result.get(ExprUtils.KEY_MAIN);
        assertTrue(mainFields.contains("qty"));
        assertTrue(mainFields.contains("unitPrice")); // snake_case → camelCase
    }

    @Test
    void extractVariables_subTableField_returnsPrefixedKey() {
        Map<String, Set<String>> result = ExprUtils.extractVariablesFromExpression("sum(items.qty * items.price)");
        assertTrue(result.containsKey("items"));
        Set<String> itemsFields = result.get("items");
        assertTrue(itemsFields.contains("qty"));
        assertTrue(itemsFields.contains("price"));
        // 不应有主表字段
        assertFalse(result.containsKey(ExprUtils.KEY_MAIN));
    }

    @Test
    void extractVariables_mixedMainAndSubTable_returnsBothKeys() {
        Map<String, Set<String>> result = ExprUtils.extractVariablesFromExpression(
                "sum(order_items.price * order_items.qty) + discount");
        assertTrue(result.containsKey("orderItems")); // snake → camel
        assertTrue(result.containsKey(ExprUtils.KEY_MAIN));
        assertTrue(result.get(ExprUtils.KEY_MAIN).contains("discount"));
        assertTrue(result.get("orderItems").contains("price"));
        assertTrue(result.get("orderItems").contains("qty"));
    }

    @Test
    void extractVariables_emptyExpression_returnsEmptyMap() {
        assertTrue(ExprUtils.extractVariablesFromExpression(null).isEmpty());
        assertTrue(ExprUtils.extractVariablesFromExpression("").isEmpty());
        assertTrue(ExprUtils.extractVariablesFromExpression("   ").isEmpty());
    }

    @Test
    void extractVariables_onlySqlKeywords_returnsEmptyMap() {
        Map<String, Set<String>> result = ExprUtils.extractVariablesFromExpression("sum(count(distinct null))");
        assertTrue(result.isEmpty());
    }

    @Test
    void extractVariables_caseWhenExpression_extractsCorrectFields() {
        Map<String, Set<String>> result = ExprUtils.extractVariablesFromExpression(
                "case when bill_status_id = 'BillStatus.effective' then amount else 0 end");
        assertTrue(result.containsKey(ExprUtils.KEY_MAIN));
        Set<String> fields = result.get(ExprUtils.KEY_MAIN);
        assertTrue(fields.contains("billStatusId"));
        assertTrue(fields.contains("amount"));
    }

    @Test
    void extractVariables_nestedFunction_extractsFields() {
        Map<String, Set<String>> result = ExprUtils.extractVariablesFromExpression(
                "coalesce(sum(items.amount), 0) + coalesce(tax_amount, 0)");
        assertTrue(result.containsKey("items"));
        assertTrue(result.get("items").contains("amount"));
        assertTrue(result.containsKey(ExprUtils.KEY_MAIN));
        assertTrue(result.get(ExprUtils.KEY_MAIN).contains("taxAmount"));
    }

    @Test
    void extractVariables_stringLiterals_ignored() {
        Map<String, Set<String>> result = ExprUtils.extractVariablesFromExpression(
                "case when status = 'active' then qty else 0 end");
        assertTrue(result.containsKey(ExprUtils.KEY_MAIN));
        Set<String> fields = result.get(ExprUtils.KEY_MAIN);
        // 'active' 是字符串常量，不应作为字段名
        assertFalse(fields.contains("active"));
        assertTrue(fields.contains("status"));
        assertTrue(fields.contains("qty"));
    }
}
