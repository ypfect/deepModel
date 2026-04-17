package com.deepmodel.relation.controller;

import com.deepmodel.relation.model.ValidationReport;
import com.deepmodel.relation.service.ExpressionValidatorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST controller for executing expression consistency validations.
 */
@RestController
@RequestMapping("/api/validation")
public class ValidationController {

    private final ExpressionValidatorService validatorService;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Autowired
    public ValidationController(ExpressionValidatorService validatorService) {
        this.validatorService = validatorService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Generate a validation report for a single object.
     * @param objectType The object type (e.g. ArReceipt)
     * @return ValidationReport JSON
     */
    @GetMapping("/check")
    public ValidationReport checkObject(@RequestParam("objectType") String objectType) {
        return validatorService.checkSingleObject(objectType);
    }

    /**
     * 同步全量扫描（保留兼容）
     */
    @GetMapping("/report")
    public ValidationReport checkAppModule(@RequestParam(value = "appName", required = false) String appName) {
        return validatorService.checkAllObjectsInApp(appName);
    }

    /**
     * SSE 流式全量扫描：边扫描边推进度，彻底消除超时问题。
     * 事件格式（JSON）：
     *   {type:"start",  totalObjects:N, totalFields:M}
     *   {type:"progress", scannedObjects:X, totalObjects:N, scannedFields:Y, totalFields:M, currentObject:"ArContract"}
     *   {type:"complete", report:{...}}
     *   {type:"error",  message:"..."}
     */
    @GetMapping(value = "/report/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAppScan(@RequestParam(value = "appName", required = false) String appName) {
        // 超时设为 10 分钟，防止大模块扫描断开
        SseEmitter emitter = new SseEmitter(10 * 60 * 1_000L);

        executor.execute(() -> {
            try {
                ValidationReport report = validatorService.checkAllObjectsInApp(appName, progress -> {
                    try {
                        String json = objectMapper.writeValueAsString(progress);
                        emitter.send(SseEmitter.event().data(json));
                    } catch (Exception e) {
                        // 推送失败（客户端已断开），中断扫描
                        throw new RuntimeException("SSE client disconnected", e);
                    }
                });

                // 扫描完成：把 report 塞进最后一个事件推送
                ExpressionValidatorService.ScanProgress complete =
                        new ExpressionValidatorService.ScanProgress(
                                "complete", 0, 0, 0, 0, null);
                complete.report = report;
                emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(complete)));
                emitter.complete();

            } catch (Throwable e) {
                try {
                    String msg = e.getMessage() != null
                            ? e.getMessage().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
                            : e.getClass().getSimpleName();
                    emitter.send(SseEmitter.event().data("{\"type\":\"error\",\"message\":\"" + msg + "\"}"));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}

