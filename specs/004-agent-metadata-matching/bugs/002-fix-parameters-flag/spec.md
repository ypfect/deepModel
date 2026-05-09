# Bug Report: /api/skills/resolve 端点 IllegalArgumentException (-parameters 编译标志缺失)

**Bug ID**: 002-fix-parameters-flag
**Parent Feature**: [004-agent-metadata-matching](file:///Users/pengfyu/advance/deepModel/specs/004-agent-metadata-matching/spec.md)
**Reported**: 2026-05-09
**Severity**: P1-严重

## 环境信息

- **Java**: 21
- **Spring Boot**: 4.0.3（Spring Web 7.0.5）
- **Maven Compiler Plugin**: 3.10.1
- **OS**: macOS

## 复现步骤

1. 编译项目：`mvn compile`
2. 启动应用：`mvn spring-boot:run`
3. 访问新增的 resolve 端点：`curl "http://localhost:18080/api/skills/resolve?query=应收合同"`
4. 触发 IllegalArgumentException

## 期望行为

API 正常返回匹配结果 JSON。

## 实际行为

抛出 `IllegalArgumentException: Name for argument of type [java.lang.String] not specified, and parameter name information not available via reflection. Ensure that the compiler uses the '-parameters' flag.`

## 影响范围

- **直接影响**: `/api/skills/resolve` 端点完全不可用
- **潜在影响**: 所有使用 `@RequestParam` 但未显式指定 `value` 的 Controller 端点都可能受影响（当前其他端点已正常运行，可能是因为 IDE 编译时自动带了 `-parameters`，而 `mvn compile` 未带）

## 根因假设

`pom.xml` 中 `maven-compiler-plugin` 配置缺少 `<parameters>true</parameters>`。

Spring Framework 6+ / Spring Boot 3+ 不再默认从 ASM 字节码中推断参数名，**必须**通过 `-parameters` 编译标志保留参数名信息。当前 pom.xml 第 98-102 行只配置了 `source`、`target`、`encoding`，未配置 `parameters`。

**修复方式**：在 `maven-compiler-plugin` 的 `<configuration>` 中添加 `<parameters>true</parameters>`。

## 验收标准

- [ ] `mvn clean compile && mvn spring-boot:run` 后，`curl "http://localhost:18080/api/skills/resolve?query=应收合同"` 返回正常 JSON
- [ ] 其他已有端点（objectProfile、threadChain 等）回归验证无异常
