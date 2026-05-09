# Bug Report: /api/skills/resolve 召回效果差——AI 消费场景需要宽召回

**Bug ID**: 003-fix-resolve-poor-recall
**Parent Feature**: [004-agent-metadata-matching](file:///Users/pengfyu/advance/deepModel/specs/004-agent-metadata-matching/spec.md)
**Reported**: 2026-05-09
**Severity**: P2-一般

> **设计理念修正**: 此 API 的消费者是 AI Agent，不是人类。AI 需要足够多的候选结果来做选择判断，
> 因此应该**宽召回**（返回尽可能多的相关信息），而非精准匹配。

## 环境信息

- **端点**: `GET /api/skills/resolve`
- **实现文件**: `SkillsService.java` 第 491~730 行

## 复现步骤

1. 启动应用
2. 输入各种中文业务术语测试匹配效果
3. 观察到以下场景召回率低或为空

## 期望行为

用户输入常见的中文业务术语时，应能召回到合理的匹配结果。

## 实际行为

多数中文输入返回空结果或匹配不准确，主要问题：

1. **同义词覆盖面极窄**：GLOBAL_SYNONYMS 仅硬编码了 5 个对象（User/Org/ArContract/Project/Customer），系统中数百个对象大部分没有同义词
2. **中文标题精确匹配要求过严**：用户输入的术语与 objectTitles 的 title 必须完全一致才能命中（score=0.8），但用户常用简称/别称（如"采购单" vs "采购订单"）
3. **包含匹配噪音大**：score=0.6 的包含匹配没有考虑匹配长度比例，短关键词（如"合同"）会匹配到大量不相关对象
4. **缺少英文名拆词匹配**：用户输入 "contract" 无法匹配到 "ArContract"——当前只做 equalsIgnoreCase 全名比较
5. **缺少拼音/首字母匹配**：用户输入 "ysht"（应收合同首字母）无法匹配

## 影响范围

- **直接影响**: resolve API 的核心可用性——Agent 调用此接口时，大部分自然语言查询无法返回有用结果
- **间接影响**: 前端 resolve.html 测试页面体验差

## 根因假设

匹配算法过于依赖硬编码同义词和精确字符串匹配，缺少以下能力：

1. **objectTitles 数据源不完整**：很多对象的 title 可能为空或与通用业务术语不匹配
2. **缺少英文名子串/拆词匹配**：PascalCase 名称应该拆分为词段（Ar+Contract）再匹配
3. **缺少模糊匹配降级**：当精确匹配和包含匹配都失败时，没有模糊/编辑距离/拼音降级策略
4. **包含匹配缺少相关性排序**：应该按匹配长度占比（输入长度/标题长度）调整 score
5. **同义词应该从元数据自动推导**：可基于已有的 objectTitles 自动生成中文变体

## 验收标准

- [ ] 输入"采购订单"能匹配到对应对象（即使 title 是"采购订单"的变体）
- [ ] 输入"contract"能匹配到所有包含 Contract 的对象（ArContract、ApContract 等）
- [ ] 输入短关键词"合同"时，结果按相关性排序（title 为"合同"的对象 > title 含"合同"的对象）
- [ ] 包含匹配的 score 应考虑匹配长度比例，避免短关键词召回过多低质量结果
- [ ] 其他已有 resolve 功能（子表查询、字段匹配）回归无异常
