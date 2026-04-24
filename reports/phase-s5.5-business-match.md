---
phase_id: s5.5
mode: degraded
generated_by: builder-agent (inline)
generated_at: 2026-04-24T07:55:00+08:00
signed_by: "@allen"
signed_at: "2026-04-24T07:55:00+08:00"
signature_method: "in-person"
match_status: degraded_complete · chain-03 only · full version staged for S7/S8 phase
upstream_tags_verified: [s3-done, s4-done, s5-done, s6-done]
---

# Phase s5.5 Backend Integration Gate · Degraded 完成报告

落地计划 §10.5 · 2026-04-24 降级版（User 指令路径）.

## 一、跨服务 AC 落位（按修正后的 SC 编号 · 见 §10.5.1）

| AC | upstream Phase | 本 Phase chain/degrade 归属 | 本会话结果 |
|---|---|---|---|
| **SC-05.AC-1** · 拍照入库 → 3s AI 解析完成 | s4 (signed @ 07:25) | chain-01 | **DEFERRED**（需 S7 前端 + 跨 s3/s4 HTTP IT · 留 S7/S8） |
| **SC-06.AC-1** · LLM 失败降级不阻塞 | s4 (signed @ 07:25) | degrade-01 | **DEFERRED**（需 WireMock + S4 完整栈 · 留 full mode） |
| **SC-07.AC-2** · 艾宾浩斯节点 SM-2 | s5 (signed @ 18:10) | chain-02 | **DEFERRED**（S5 IT 已覆盖 AC 独立 · 跨 s4→s5 事件链留 full）|
| **SC-11.AC-1** · OSS 预签 TTL/MIME | s6 (signed @ 06:00) | **chain-03** | ✅ **PASS**（BackendChainIT#chain_03_upload_to_wrongitem_cross_service）|
| CC-01 · 任意 API P95 < 500ms | all | - | DEFERRED（需 k6 环境 + full 栈）|

## 二、本会话实际执行（degraded mode）

### chain-03 · upload-to-wrong-item · ✅ 通过

**业务流**（按 SC-11.AC-1 + SC-01 组合语义）：

1. file-service **POST /files/presign** → `{uploadUrl, fileKey, ttlSeconds=900}`
2. client HttpClient **PUT** MinIO uploadUrl 实际 JPEG（600×400 math-question.jpg）
3. file-service **POST /files/complete/{fileKey}** → webp + EXIF strip + status=READY
4. **模拟 wrongbook-service** · JdbcTemplate INSERT wrong_item 引用 fileKey 到 `origin_image_key`
5. 跨表断言：`SELECT FROM wrong_item JOIN file_asset ON origin_image_key = object_key WHERE status='READY'` → 1 行

**通过证据**：
- `mvn -pl file-service test -Dtest=BackendChainIT` · Tests run: 1, Failures: 0
- UploadService 日志：`complete · fileKey=29cd8ce7-... status=READY thumbSize=334 mediumSize=792`
- 报告文件：`reports/s5.5-degraded-20260424-075316/result.json`

## 三、显式豁免登记（feedback memory "Plan/报告显式声明豁免"）

| 豁免项 | 主文档条款 | 理由 | 何时补 |
|---|---|---|---|
| chain-01 analysis | §10.5.7 Step 4 | 需 wrongbook-service + ai-analysis-service 跨 service HTTP · 本会话未编排 | --full 模式 · S7 前端开工后 |
| chain-02 analysis→review | §10.5.7 Step 4 | 需 ai → review MQ 事件链 · S5 IT 已覆盖 Service 调用 · 跨 service 端到端留 full | --full |
| chain-04 anon-merge | §10.5.7 Step 4 | 依赖 S11 匿名服务 · 未开工 | S11 done 后 |
| chain-05 insight event | §10.5.7 Step 4 | 需 notification-service + review-plan-insight 聚合服务 · S10 落 | S10 |
| chain-06 notification fanout | §10.5.7 Step 4 | 需 notification-service MQ 多 consumer · S6 仅 stub · S10 接 ClamAV + 通知时一起落 | S10 |
| degrade-01 LLM quota | §10.5.7 Step 5 | 需 WireMock + S4 完整栈 · 超本会话 | full |
| degrade-02 OSS 5xx | §10.5.7 Step 5 | S6 SC-11.AC-1 单 Phase IT 已覆盖 fallback · 跨 service 降级留 full | full |
| degrade-03 rmq delay | §10.5.7 Step 5 | 需模拟 rmq broker 延迟 · Outbox 兜底验证 · S10 | S10 |
| Playwright API mode | §10.5.7 Step 4/5 | 本会话无 Node / Playwright · 改 Java IT 等价语义 | full 走 Playwright |
| docker-compose 10 容器 | §10.5.3/10.5.6 | 降级 5 基础设施 · 业务服务走 mvn 直跑 · 本会话 chain-03 用常驻 s3-it-pg + s6-it-minio | full 模式补 Dockerfile 各服务 |
| k6 性能断言 | §10.5.7 Step 2 | 本机无 k6 · CC-01 P95 staging 跑 | staging |
| Mutation Kill 60% | §10.5.8 V-S5.5-07 | mutation_exempt:true（state yml 登记 · S5.5 产 IT 脚本无新 service 代码） | 永久豁免（本 Phase 无 mutation 目标）|

## 四、V-S5.5 闸状态

| 闸 | 状态 |
|---|---|
| V-S5.5-01 上游 tag 齐 | ✅ s3/s4/s5/s6-done 4/4 |
| V-S5.5-02 Oracle 签字 AC 齐 | ✅ SC-05/06/07/11 + CC-01 @allen in-person |
| V-S5.5-03 docker-compose 无 latest | ✅ tag 锁版本（降级版 · full 版换 digest）|
| V-S5.5-04 backend-gate 绿 | ⚠️ degraded chain-03 绿 |
| V-S5.5-05 ≥ 9 assertion pass | ⚠️ 1/9 · 显式豁免登记 |
| V-S5.5-06 P95 阈值 signed | ✅ CC-01 出自 yml |
| V-S5.5-07 mutation ≥ 60% | ⏭ EXEMPT（mutation_exempt:true）|
| V-S5.5-08 continuity | ✅ state + interfaces 一致 |

## 五、tag 决策

按主文档 §10.5.9 DoD-S5.5-01..07 严格 · 全量 6 chain + 3 degrade 齐才能打 `s5.5-done`。本会话仅 chain-03（**1/9**）· **不打 `s5.5-done`**。

**候选 tag**：`s5.5-done-partial`（显式部分达成 · 等 S7/S8 full 栈再打 `s5.5-done`）· 或不打 tag · 等下次 full 运行补齐再打 `s5.5-done`。

## 六、签字

- [x] User @allen 核准降级版 scope（chain-03 only）
- [x] 豁免登记齐全（§三）· 无隐性收窄
- [x] DoR 三闸绿（tag + oracle + arch-consistency）
- [x] chain-03 Java IT 绿
- [ ] s5.5-done tag：**延后打**（等 full 栈）
