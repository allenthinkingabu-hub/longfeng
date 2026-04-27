# E2E Report · DetailPage · 2026-04-27

> 轨道：A 轨（真实后端）· 生成于 2026-04-27
> 后端：gateway:8080 · wrongbook-service:8081 · ai-analysis-service:8082
> 前端：http://localhost:5174/wrongbook/:id

---

## 一、设计系统合规

- Gate 1 硬编码色值：**0 处** ✅
- Gate 2 旧 iOS 变量：**0 处** ✅
- Gate 3 内联品牌色：**0 处** ✅
- Gate 4 testid 覆盖：**15 处** ✅
- **整体合规评分：4/4 ✅**

---

## 二、AC 覆盖矩阵

| AC | 功能 | testid | 测试结果 | 备注 |
|---|---|---|---|---|
| SC-02.AC-3 | 标签保存持久化 | wrongbook.detail.tag-save | ✅ | PATCH /wrongbook/items/:id/tags 返回 204，页面 reload 正常 |
| SC-03.AC-1 | SSE 讲解流渲染 | wrongbook.detail.explain-stream | ✅ | SSE chunk 接收并渲染，文字"示例讲解（stub）"显示正确 |
| SC-04.AC-1 | 软删除 + 列表同步 | wrongbook.detail.delete.btn / delete.confirm | ✅ | 删除后路由跳回列表，列表 count 减少 |

---

## 三、基础设施修复（本次 A 轨完成）

| 问题 | 修复 | 文件 |
|---|---|---|
| PATCH /tags 发送裸数组 | 修改为 `{tags:[...]}` 包裹对象（后端 BulkTagReq） | `packages/api-contracts/src/clients/wrongbook.ts` |
| EventSource 无法发 JWT | URL query param 方式：`?token=...` | `packages/api-contracts/src/clients/analysis.ts` |
| Gateway 不接受 query token | JwtAuthFilter 增加 query param fallback | `backend/gateway/.../JwtAuthFilter.java` |
| Gateway 无 /api/v1/analysis/** 路由 | 添加 analysis-api 路由，StripPrefix=2 | 启动参数 |
| SSE endpoint 路径错误 | `/api/v1/analysis/:id` → `/api/v1/analysis/:id/stream` | `packages/api-contracts/src/clients/analysis.ts` |

---

## 四、后端接口差异（未修改，记录待对齐）

| 差异点 | 后端实际 | 前端期望 | 影响 |
|---|---|---|---|
| tags 格式 | `[{tagCode, weight}]` | `string[]` | 标签显示为 `[object Object]` |
| similar items | GET /analysis/:id/similar 返回 items（analysis service 实现）| wrongbook-service 无此端点，原来返回 500 | 已绕过：相似题调用改走 analysis service |
| mastery 量纲 | 0-2 | 0-100 | route transform 补偿 |

---

## 五、SSE 说明

- ai-analysis-service 使用 dashscope（阿里云百炼）provider，当前 stub 实现返回"示例讲解（stub）"
- SSE 为单 chunk `{chunk:"示例讲解（stub）",done:true}`，瞬时完成，非渐进流式
- 真实 LLM 调用需 ai-analysis-service 连接到 Alibaba Cloud Bailian API 并触发 analyze 任务

---

## 六、User 决策

- [x] 接受 AC SC-02.AC-3 ✅（标签保存接口修复，204 正常）
- [x] 接受 AC SC-03.AC-1 ✅（SSE 接通，stub 文本渲染）
- [x] 接受 AC SC-04.AC-1 ✅（软删除 + 列表同步）
- [x] 接受合规评分（4/4 ✅）
- [ ] 后端 issue：tags 格式 `{tagCode,weight}[]` → `string[]` 对齐
- [ ] 后端 issue：ai-analysis-service 真实 LLM 调用验证（Alibaba Cloud 密钥已配置）
