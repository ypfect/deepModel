package com.deepmodel.relation.env;

/**
 * 单元测试用环境快照辅助：注册空 loader，避免触发真实元数据加载。
 */
public final class TestEnvSupport {

    public static final String ENV = "unit-test";

    private TestEnvSupport() {}

    public static EnvSnapshotManager createManager() {
        EnvSnapshotManager manager = new EnvSnapshotManager();
        manager.registerLoader(s -> {});
        EnvContext.set(ENV);
        return manager;
    }

    public static EnvSnapshot snapshot(EnvSnapshotManager manager) {
        return manager.getOrLoad(ENV);
    }

    public static void teardown(EnvSnapshotManager manager) {
        if (manager != null) {
            manager.invalidateAll();
        }
        EnvContext.clear();
    }
}
