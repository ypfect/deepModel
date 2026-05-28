package com.deepmodel.relation.env;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 按 env 缓存 {@link EnvSnapshot} 的容器。
 * <p>
 * - 首次访问某 env 触发 loader（由 ImpactAnalyzerService 注册）拉数据 + 建索引；<br>
 * - 切回已加载过的 env 直接命中缓存，无需重拉；<br>
 * - 提供 {@link #invalidate(String)} 强制重建。
 */
@Component
public class EnvSnapshotManager {

    private static final Logger log = LoggerFactory.getLogger(EnvSnapshotManager.class);

    private final Map<String, EnvSnapshot> cache = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    private volatile Consumer<EnvSnapshot> loader;

    /** 由 {@link com.deepmodel.relation.service.ImpactAnalyzerService} 注册数据加载器。 */
    public void registerLoader(Consumer<EnvSnapshot> loader) {
        this.loader = loader;
    }

    /**
     * 获取当前请求的环境快照（按需触发首次加载）。
     */
    public EnvSnapshot current() {
        String env = EnvContext.requireCurrent();
        return getOrLoad(env);
    }

    public EnvSnapshot getOrLoad(String env) {
        EnvSnapshot snap = cache.get(env);
        if (snap != null) {
            return snap;
        }
        Object lock = locks.computeIfAbsent(env, k -> new Object());
        synchronized (lock) {
            snap = cache.get(env);
            if (snap != null) {
                return snap;
            }
            if (loader == null) {
                throw new IllegalStateException("EnvSnapshotManager loader 未注册");
            }
            log.info("[EnvSnapshot] 开始加载 env={}", env);
            long t0 = System.currentTimeMillis();
            EnvSnapshot fresh = new EnvSnapshot(env);
            loader.accept(fresh);
            fresh.builtAt = System.currentTimeMillis();
            cache.put(env, fresh);
            log.info("[EnvSnapshot] env={} 加载完成，耗时 {} ms，字段数={}",
                    env, fresh.builtAt - t0, fresh.allRows.size());
            return fresh;
        }
    }

    /** 仅在已加载时返回，不触发加载。 */
    public EnvSnapshot peek(String env) {
        return cache.get(env);
    }

    public Set<String> loadedEnvs() {
        return cache.keySet();
    }

    /** 失效某 env 的快照。下次访问会重新加载。 */
    public void invalidate(String env) {
        EnvSnapshot removed = cache.remove(env);
        if (removed != null) {
            removed.clearAll();
            log.info("[EnvSnapshot] 已失效 env={}", env);
        }
    }

    public void invalidateAll() {
        for (String env : cache.keySet()) {
            invalidate(env);
        }
    }
}
