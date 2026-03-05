package com.deepmodel.relation.controller;

import com.deepmodel.relation.service.SkillsService;
import com.deepmodel.relation.service.SkillsService.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 模型辅助 Skills API Controller。
 *
 * 路径前缀：/api/skills
 *
 * 接口清单：
 *  GET  /api/skills/objectProfile   — 对象完整业务语义画像
 *  GET  /api/skills/threadChain     — 以线索字段为轴的对象执行链
 *  GET  /api/skills/patternCheck    — 标准金额/数量字段完整性检查
 *  POST /api/skills/changeScope     — 改动影响范围预估
 *  GET  /api/skills/searchFields    — 跨对象字段模糊搜索
 */
@RestController
@RequestMapping("/api/skills")
public class SkillsController {

    private final SkillsService skillsService;

    public SkillsController(SkillsService skillsService) {
        this.skillsService = skillsService;
    }

    /**
     * 对象完整业务语义画像。
     *
     * 返回指定对象的所有字段按业务语义分组（金额字段、数量字段、回写字段、触发字段、虚拟字段、基础字段），
     * 以及对象级别的入站/出站回写关系摘要，供 AI 模型一次性掌握对象全貌。
     *
     * @param objectType 对象类型名，如 ArContractSubjectMatterItem
     */
    @GetMapping("/objectProfile")
    public ResponseEntity<ObjectProfile> objectProfile(@RequestParam String objectType) {
        if (objectType == null || objectType.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(skillsService.objectProfile(objectType.trim()));
    }

    /**
     * 以线索字段为轴的对象执行链。
     *
     * 线索字段（如 ArContractSubjectMatterItemId）出现在多个对象中，充当执行业务的串联键。
     * 返回所有持有该字段的对象，及其通过 writeBack 构成的执行链关系图。
     *
     * @param threadField 线索字段名，如 ArContractSubjectMatterItemId
     */
    @GetMapping("/threadChain")
    public ResponseEntity<ThreadChainResult> threadChain(@RequestParam String threadField) {
        if (threadField == null || threadField.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(skillsService.threadChain(threadField.trim()));
    }

    /**
     * 标准金额/数量字段完整性检查。
     *
     * 对照以下标准字段集检查对象当前有哪些、缺哪些：
     *   金额：originAmount / amount / amountWithoutTax / originAmountWithoutTax
     *   数量：quantity(交易主) / transAuxQty(交易辅) / baseQty(主) / auxQty(辅)
     *
     * 同时检查执行类字段（金额/数量）中，哪些已有 writeBack 定义，哪些缺少。
     *
     * @param objectType 对象类型名
     */
    @GetMapping("/patternCheck")
    public ResponseEntity<PatternCheckResult> patternCheck(@RequestParam String objectType) {
        if (objectType == null || objectType.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(skillsService.patternCheck(objectType.trim()));
    }

    /**
     * 改动影响范围预估。
     *
     * 根据改动意图推导需要调整的对象/字段范围，输出每个受影响对象需要执行的操作及原因。
     *
     * 请求体示例：
     * <pre>
     * {
     *   "scenario": "addExecution",           // addExecution | adjustWriteBack | addBranch
     *   "newSourceObject": "ArNewInvoice",    // 新增来源对象（addExecution 时必填）
     *   "targetObject": "ArContractSubjectMatterItem",
     *   "fields": ["originAmount", "amount"]  // 需要新增/调整的字段
     * }
     * </pre>
     *
     * action 说明：
     * - ADD_FIELD：目标对象上不存在该字段，需新增
     * - ADD_WRITE_BACK_SOURCE：字段已有 writeBack，需纳入新的来源对象
     * - ADD_WRITE_BACK_DEFINITION：字段尚无 writeBack 定义，需新增
     * - CHECK_TRIGGER：依赖被修改字段的 trigger，需人工确认聚合逻辑仍正确
     */
    @PostMapping("/changeScope")
    public ResponseEntity<ChangeScopeResult> changeScope(@RequestBody ChangeScopeRequest request) {
        if (request == null || request.targetObject == null || request.targetObject.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        request.targetObject = request.targetObject.trim();
        return ResponseEntity.ok(skillsService.changeScope(request));
    }

    /**
     * 跨对象字段模糊搜索。
     *
     * 按字段名、标题关键字、bizType 搜索字段，支持缩小到指定对象范围。
     * 便于 AI 模型查找"哪些对象有 invoiceAmount 类型字段"或"哪些字段的 bizType 是 Amount"。
     *
     * @param namePattern 字段名或标题中包含的关键字（大小写不敏感），不传则不按名称过滤
     * @param objectType  限定对象类型，不传则搜索所有对象
     * @param bizType     按 bizType 模糊过滤，不传则不限
     * @param limit       最多返回条数，默认 200，最大 1000
     */
    @GetMapping("/searchFields")
    public ResponseEntity<List<FieldSearchResult>> searchFields(
            @RequestParam(required = false) String namePattern,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String bizType,
            @RequestParam(defaultValue = "200") int limit) {
        limit = Math.min(limit, 1000);
        return ResponseEntity.ok(skillsService.searchFields(namePattern, objectType, bizType, limit));
    }
}
