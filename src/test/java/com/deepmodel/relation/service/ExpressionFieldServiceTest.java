package com.deepmodel.relation.service;

import com.deepmodel.relation.env.EnvSnapshot;
import com.deepmodel.relation.env.EnvSnapshotManager;
import com.deepmodel.relation.env.TestEnvSupport;
import com.deepmodel.relation.model.BaseappObjectField;
import com.deepmodel.relation.model.ExpressionFieldInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ExpressionFieldServiceTest {

    private EnvSnapshotManager snapshotManager;
    private EnvSnapshot snapshot;
    private ExpressionFieldService service;

    @BeforeEach
    void setUp() {
        snapshotManager = TestEnvSupport.createManager();
        snapshot = TestEnvSupport.snapshot(snapshotManager);
        service = new ExpressionFieldService(snapshotManager);
    }

    @AfterEach
    void tearDown() {
        TestEnvSupport.teardown(snapshotManager);
    }

    private BaseappObjectField createField(String objectType, String name, String expression) {
        BaseappObjectField f = new BaseappObjectField();
        f.setObjectType(objectType);
        f.setName(name);
        f.setExpression(expression);
        return f;
    }

    @Test
    void buildIndex_singleLevelExpression_correctLevels() {
        // amount = qty * unitPrice → qty/unitPrice 是 level -1, amount 是 level 0
        List<BaseappObjectField> rows = Arrays.asList(
                createField("Order", "qty", null),
                createField("Order", "unitPrice", null),
                createField("Order", "amount", "qty * unit_price"));

        service.buildIndex(snapshot, rows, Collections.emptyMap());

        ExpressionFieldInfo info = service.getExpressionFieldInfo("Order");
        assertNotNull(info);
        // amount 依赖 qty 和 unitPrice
        assertTrue(info.getExprFieldToVars().containsKey("Order.amount"));
        Set<String> vars = info.getExprFieldToVars().get("Order.amount");
        assertTrue(vars.contains("Order.qty"));
        assertTrue(vars.contains("Order.unitPrice"));
        // 层级：-1=[qty,unitPrice], 0=[amount]
        assertTrue(info.getLevelToFields().containsKey(-1));
        assertTrue(info.getLevelToFields().containsKey(0));
        assertTrue(info.getLevelToFields().get(0).contains("Order.amount"));
    }

    @Test
    void buildIndex_multiLevelDependencyChain_correctLevels() {
        // amountWithTax = amount + taxAmount
        // amount = qty * price
        // → qty/price level -1, amount level 0, amountWithTax level 1
        List<BaseappObjectField> rows = Arrays.asList(
                createField("Order", "qty", null),
                createField("Order", "price", null),
                createField("Order", "taxAmount", null),
                createField("Order", "amount", "qty * price"),
                createField("Order", "amountWithTax", "amount + tax_amount"));

        service.buildIndex(snapshot, rows, Collections.emptyMap());

        ExpressionFieldInfo info = service.getExpressionFieldInfo("Order");
        assertNotNull(info);
        // amountWithTax 依赖 amount（level 0），所以在 level 1
        Map<Integer, Set<String>> levels = info.getLevelToFields();
        assertTrue(levels.get(-1).contains("Order.qty"));
        assertTrue(levels.get(-1).contains("Order.price"));
        assertTrue(levels.get(-1).contains("Order.taxAmount"));
        assertTrue(levels.get(0).contains("Order.amount"));
        assertTrue(levels.get(1).contains("Order.amountWithTax"));
    }

    @Test
    void buildIndex_subTableFieldMerge_mergedIntoMainEntity() {
        // 主表 Order 有子表 OrderItem
        Map<String, Set<String>> mainToDetails = new HashMap<>();
        mainToDetails.put("Order", new LinkedHashSet<>(Arrays.asList("OrderItem")));

        List<BaseappObjectField> rows = Arrays.asList(
                createField("OrderItem", "qty", null),
                createField("OrderItem", "price", null),
                createField("OrderItem", "lineAmount", "qty * price"),
                createField("Order", "totalAmount", "sum(qty * price)"));

        service.buildIndex(snapshot, rows, mainToDetails);

        // 子表的 ExpressionFieldInfo 应该合并到主表
        ExpressionFieldInfo info = service.getExpressionFieldInfo("Order");
        assertNotNull(info);
        // OrderItem 不应单独存在
        assertNull(service.getExpressionFieldInfo("OrderItem"));
    }

    @Test
    void buildIndex_circularDependency_handledGracefully() {
        // a = b + 1, b = a + 1 (循环依赖)
        List<BaseappObjectField> rows = Arrays.asList(
                createField("Entity", "a", "b"),
                createField("Entity", "b", "a"));

        // 不应抛异常
        assertDoesNotThrow(() -> service.buildIndex(snapshot, rows, Collections.emptyMap()));

        ExpressionFieldInfo info = service.getExpressionFieldInfo("Entity");
        assertNotNull(info);
        // 循环依赖应被检测并放到某个层级
        assertFalse(info.getLevelToFields().isEmpty());
    }

    @Test
    void buildIndex_noExpressionFields_notIndexed() {
        List<BaseappObjectField> rows = Arrays.asList(
                createField("Simple", "name", null),
                createField("Simple", "code", null));

        service.buildIndex(snapshot, rows, Collections.emptyMap());

        assertNull(service.getExpressionFieldInfo("Simple"));
    }

    @Test
    void buildIndex_emptyData_returnsNull() {
        service.buildIndex(snapshot, Collections.emptyList(), Collections.emptyMap());
        assertNull(service.getExpressionFieldInfo("Anything"));
    }

    @Test
    void buildIndex_fieldToExprFields_reverseMapping() {
        List<BaseappObjectField> rows = Arrays.asList(
                createField("Order", "qty", null),
                createField("Order", "amount", "qty * 10"),
                createField("Order", "discountAmount", "qty * 5"));

        service.buildIndex(snapshot, rows, Collections.emptyMap());

        ExpressionFieldInfo info = service.getExpressionFieldInfo("Order");
        assertNotNull(info);
        // qty 被 amount 和 discountAmount 引用
        Map<String, Set<String>> reverse = info.getFieldToExprFields();
        assertTrue(reverse.containsKey("Order.qty"));
        Set<String> refs = reverse.get("Order.qty");
        assertTrue(refs.contains("Order.amount"));
        assertTrue(refs.contains("Order.discountAmount"));
    }
}
