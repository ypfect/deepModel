package com.deepmodel.relation.service;

import com.deepmodel.relation.env.EnvSnapshot;
import com.deepmodel.relation.env.EnvSnapshotManager;
import com.deepmodel.relation.env.TestEnvSupport;
import com.deepmodel.relation.model.BaseappObjectField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class EntityReferenceServiceTest {

    private EnvSnapshotManager snapshotManager;
    private EnvSnapshot snapshot;
    private EntityReferenceService service;

    @BeforeEach
    void setUp() {
        snapshotManager = TestEnvSupport.createManager();
        snapshot = TestEnvSupport.snapshot(snapshotManager);
        service = new EntityReferenceService(snapshotManager);
    }

    @AfterEach
    void tearDown() {
        TestEnvSupport.teardown(snapshotManager);
    }

    private BaseappObjectField createField(String objectType, String name, String referInfo) {
        BaseappObjectField f = new BaseappObjectField();
        f.setObjectType(objectType);
        f.setName(name);
        f.setReferInfo(referInfo);
        return f;
    }

    @Test
    void buildIndex_normalFkReference_createsCorrectIndex() {
        String referInfo = "{\"referEntities\":[{\"referEntityName\":\"ArContract\",\"isDetail\":false}]}";
        service.buildIndex(snapshot, Arrays.asList(
                createField("ArInvoiceItem", "contractId", referInfo)));

        Map<String, Map<String, Boolean>> result = service.getReferRelations("ArContract");
        assertFalse(result.isEmpty());
        assertTrue(result.containsKey("ArInvoiceItem"));
        assertFalse(result.get("ArInvoiceItem").get("contractId")); // isDetail=false
    }

    @Test
    void buildIndex_detailReference_markedAsDetail() {
        String referInfo = "{\"referEntities\":[{\"referEntityName\":\"ArContract\",\"isDetail\":true}]}";
        service.buildIndex(snapshot, Arrays.asList(
                createField("ContractSubjectMatterItem", "contractId", referInfo)));

        Map<String, Map<String, Boolean>> result = service.getReferRelations("ArContract");
        assertTrue(result.get("ContractSubjectMatterItem").get("contractId")); // isDetail=true
    }

    @Test
    void buildIndex_polymorphicReference_goesToAll() {
        String referInfo = "{\"referEntityFieldName\":\"targetType\",\"referEntities\":["
                + "{\"referEntityName\":\"ArContract\",\"isDetail\":false},"
                + "{\"referEntityName\":\"ApContract\",\"isDetail\":false}]}";
        service.buildIndex(snapshot, Arrays.asList(
                createField("Approval", "targetId", referInfo)));

        // 多态引用应归入 ALL
        Map<String, Map<String, Boolean>> allResult = service.getReferRelations("ALL");
        assertFalse(allResult.isEmpty());
        assertTrue(allResult.containsKey("Approval"));
        // 直接查 ArContract 不应有结果
        assertTrue(service.getReferRelations("ArContract").isEmpty());
    }

    @Test
    void buildIndex_nullReferInfo_skipped() {
        service.buildIndex(snapshot, Arrays.asList(
                createField("Order", "name", null),
                createField("Order", "code", ""),
                createField("Order", "status", "null")));

        assertTrue(service.getReferRelations("Order").isEmpty());
        assertTrue(service.getAllReferRelations().isEmpty());
    }

    @Test
    void buildIndex_emptyData_returnsEmptyMaps() {
        service.buildIndex(snapshot, Collections.emptyList());
        assertTrue(service.getReferRelations("Anything").isEmpty());
        assertTrue(service.getAllReferRelations().isEmpty());
    }

    @Test
    void buildIndex_invalidJson_skipsAndContinues() {
        String validRef = "{\"referEntities\":[{\"referEntityName\":\"Project\",\"isDetail\":false}]}";
        service.buildIndex(snapshot, Arrays.asList(
                createField("Task", "bad", "{broken}"),
                createField("Task", "projectId", validRef)));

        Map<String, Map<String, Boolean>> result = service.getReferRelations("Project");
        assertEquals(1, result.size());
        assertTrue(result.containsKey("Task"));
    }

    @Test
    void getReferRelations_unknownEntity_returnsEmptyMap() {
        service.buildIndex(snapshot, Collections.emptyList());
        Map<String, Map<String, Boolean>> result = service.getReferRelations("NonExistent");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
