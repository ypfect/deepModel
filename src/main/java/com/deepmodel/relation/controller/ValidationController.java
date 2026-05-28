package com.deepmodel.relation.controller;

import com.deepmodel.relation.dao.LocalPostgresMetadataRepository;
import com.deepmodel.relation.env.EnvContext;
import com.deepmodel.relation.env.EnvSnapshotManager;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST controller for executing expression consistency validations.
 */
@RestController
@RequestMapping("/api/validation")
public class ValidationController {

    private final ExpressionValidatorService validatorService;
    private final EnvSnapshotManager snapshotManager;
    private final LocalPostgresMetadataRepository localRepo;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Autowired
    public ValidationController(ExpressionValidatorService validatorService,
                                EnvSnapshotManager snapshotManager,
                                LocalPostgresMetadataRepository localRepo) {
        this.validatorService = validatorService;
        this.snapshotManager = snapshotManager;
        this.localRepo = localRepo;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Generate a validation report for a single object.
     * @param objectType The object type (e.g. ArReceipt)
     * @return ValidationReport JSON
     */
    @GetMapping("/check")
    public ValidationReport checkObject(
            @RequestParam("objectType") String objectType,
            @RequestParam(value = "source", required = false, defaultValue = "env") String source,
            @RequestParam(value = "env", required = false) String env) {
        String scanEnv = resolveScanEnv(source, env);
        try {
            EnvContext.set(scanEnv);
            if (EnvContext.isLocalEnv(scanEnv)) {
                snapshotManager.invalidate(scanEnv);
            }
            return validatorService.checkSingleObject(objectType);
        } finally {
            EnvContext.clear();
        }
    }

    /** 本地库 App 模块列表（供体检中心下拉）。 */
    @GetMapping("/local/apps")
    public List<String> localApps() {
        return localRepo.selectDistinctAppNames();
    }

    /** 本地库连接信息（不含密码）。 */
    @GetMapping("/local/info")
    public Map<String, Object> localInfo() {
        Map<String, Object> info = localRepo.connectionInfo();
        info.put("mode", "local");
        info.put("envKey", EnvContext.LOCAL_ENV_KEY);
        return info;
    }

    /**
     * 同步全量扫描（保留兼容）
     */
    @GetMapping("/report")
    public ValidationReport checkAppModule(
            @RequestParam(value = "appName", required = false) String appName,
            @RequestParam(value = "source", required = false, defaultValue = "env") String source,
            @RequestParam(value = "env", required = false) String env) {
        String scanEnv = resolveScanEnv(source, env);
        try {
            EnvContext.set(scanEnv);
            if (EnvContext.isLocalEnv(scanEnv)) {
                snapshotManager.invalidate(scanEnv);
            }
            return validatorService.checkAllObjectsInApp(appName);
        } finally {
            EnvContext.clear();
        }
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
    public SseEmitter streamAppScan(
            @RequestParam(value = "appName", required = false) String appName,
            @RequestParam(value = "env", required = false) String env,
            @RequestParam(value = "source", required = false, defaultValue = "env") String source) {
        SseEmitter emitter = new SseEmitter(10 * 60 * 1_000L);

        final String scanEnv;
        try {
            scanEnv = resolveScanEnv(source, env);
            if (EnvContext.isLocalEnv(scanEnv)) {
                localRepo.ping();
            }
        } catch (RuntimeException e) {
            SseEmitter errEmitter = new SseEmitter(5_000L);
            executor.execute(() -> {
                try {
                    String msg = e.getMessage() != null
                            ? e.getMessage().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
                            : e.getClass().getSimpleName();
                    errEmitter.send(SseEmitter.event().data("{\"type\":\"error\",\"message\":\"" + msg + "\"}"));
                    errEmitter.complete();
                } catch (Exception ignored) {
                    errEmitter.completeWithError(e);
                }
            });
            return errEmitter;
        }

        executor.execute(() -> {
            try {
                EnvContext.set(scanEnv);
                if (EnvContext.isLocalEnv(scanEnv)) {
                    snapshotManager.invalidate(scanEnv);
                }
                ValidationReport report = validatorService.checkAllObjectsInApp(appName, progress -> {
                    try {
                        String json = objectMapper.writeValueAsString(progress);
                        emitter.send(SseEmitter.event().data(json));
                    } catch (Exception e) {
                        throw new RuntimeException("SSE client disconnected", e);
                    }
                });

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
            } finally {
                EnvContext.clear();
            }
        });

        return emitter;
    }

    /**
     * @param source {@code env}（默认，走 GraphQL + X-Env/运维环境）或 {@code local}（本地 PostgreSQL）
     */
    private static String resolveScanEnv(String source, String envParam) {
        if ("local".equalsIgnoreCase(source != null ? source.trim() : "")) {
            return EnvContext.LOCAL_ENV_KEY;
        }
        if (envParam != null && !envParam.isBlank()) {
            return envParam.trim();
        }
        String fromHeader = EnvContext.currentOrNull();
        if (fromHeader != null && !fromHeader.isBlank()) {
            return fromHeader.trim();
        }
        throw new IllegalStateException("未指定工作环境：请选择右上角环境，或使用 source=local 进行本地库检查");
    }
}

