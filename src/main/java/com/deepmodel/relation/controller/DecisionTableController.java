package com.deepmodel.relation.controller;

import com.deepmodel.relation.model.DecisionTableModels.*;
import com.deepmodel.relation.service.DecisionTableIndexService;
import com.deepmodel.relation.service.DecisionTableSimulatorService;
import com.deepmodel.relation.service.DecisionTableSimulatorService.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 决策表分析 REST API。
 */
@RestController
@RequestMapping("/api/decision-table")
public class DecisionTableController {

    private final DecisionTableIndexService indexService;
    private final DecisionTableSimulatorService simulatorService;

    public DecisionTableController(DecisionTableIndexService indexService,
                                   DecisionTableSimulatorService simulatorService) {
        this.indexService = indexService;
        this.simulatorService = simulatorService;
    }

    /**
     * 获取决策表统计概要。
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        return ResponseEntity.ok(indexService.getSummary());
    }

    /**
     * 获取所有操作名列表。
     */
    @GetMapping("/operations")
    public ResponseEntity<List<String>> getOperations() {
        return ResponseEntity.ok(indexService.getOperationNames());
    }

    /**
     * 获取某个操作的编排视图（7步 × FuncUnit 列表）。
     */
    @GetMapping("/operation/{operationName}/steps")
    public ResponseEntity<?> getOperationSteps(@PathVariable("operationName") String operationName) {
        OperationView view = indexService.getOperationView(operationName);
        if (view == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(view);
    }

    /**
     * 获取某个 FuncUnit 的所有使用位置。
     */
    @GetMapping("/funcunit/{name}/usages")
    public ResponseEntity<List<FuncUnitUsage>> getFuncUnitUsages(@PathVariable("name") String name) {
        List<FuncUnitUsage> usages = indexService.getFuncUnitUsages(name);
        return ResponseEntity.ok(usages);
    }

    /**
     * 获取所有 FuncUnit 名称列表。
     */
    @GetMapping("/funcunits")
    public ResponseEntity<List<String>> getAllFuncUnits(
            @RequestParam(value = "keyword", required = false) String keyword) {
        if (keyword != null && !keyword.isEmpty()) {
            return ResponseEntity.ok(indexService.searchFuncUnits(keyword));
        }
        return ResponseEntity.ok(indexService.getAllFuncUnitNames());
    }

    /**
     * 获取某个 CSV 文件的详细解析结果。
     */
    @GetMapping("/csv/detail")
    public ResponseEntity<?> getCsvDetail(@RequestParam("path") String path) {
        CsvParseResult result = indexService.getCsvDetail(path);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 获取所有 CSV 文件列表。
     */
    @GetMapping("/csv/list")
    public ResponseEntity<List<Map<String, Object>>> listCsvFiles() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (CsvParseResult csv : indexService.getAllCsvResults()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("csvPath", csv.csvPath);
            item.put("operationName", csv.operationName);
            item.put("entityFolder", csv.entityFolder);
            item.put("funcUnitCount", csv.funcUnits.size());
            item.put("succession", csv.succession);
            list.add(item);
        }
        return ResponseEntity.ok(list);
    }

    /**
     * 重新加载决策表索引。
     */
    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reload() {
        indexService.buildIndex();
        return ResponseEntity.ok(indexService.getSummary());
    }

    // ===== 模拟器 API =====

    /**
     * 获取所有 Service 文件夹列表。
     */
    @GetMapping("/simulator/folders")
    public ResponseEntity<List<String>> getSimulatorFolders() {
        return ResponseEntity.ok(simulatorService.getAllFolders());
    }

    /**
     * 获取指定 folder 下的所有操作方法。
     */
    @GetMapping("/simulator/folder/{folderName}/methods")
    public ResponseEntity<List<String>> getFolderMethods(@PathVariable("folderName") String folderName) {
        return ResponseEntity.ok(simulatorService.getMethodsForFolder(folderName));
    }

    /**
     * 模拟指定对象+操作的完整 FuncUnit 装配。
     */
    @GetMapping("/simulator/simulate")
    public ResponseEntity<SimulationResult> simulate(
            @RequestParam("objectName") String objectName,
            @RequestParam("csvFile") String csvFile,
            @RequestParam(value = "entityType", required = false) String entityType) {
        return ResponseEntity.ok(simulatorService.simulate(objectName, csvFile, entityType));
    }
    /**
     * 调试：查看指定对象的 FuncUnit Customizer 原始数据。
     */
    @GetMapping("/simulator/debug/customizers")
    public ResponseEntity<Map<String, Object>> debugCustomizers(
            @RequestParam("objectName") String objectName) {
        return ResponseEntity.ok(simulatorService.debugCustomizers(objectName));
    }
}
