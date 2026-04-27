# E2E Report · CapturePage · 2026-04-27

> 轨道：A 轨（真实后端）· 生成于 2026-04-27
> 后端：gateway:8080 · wrongbook-service:8081 · file-service:8083（未启动）
> 前端：http://localhost:5174/wrongbook/capture

---

## 一、设计系统合规

- Gate 1 硬编码色值：**0 处** ✅
- Gate 2 旧 iOS 变量：**0 处** ✅
- Gate 3 内联品牌色：**0 处** ✅
- Gate 4 testid 覆盖：**17 处** ✅
- **整体合规评分：4/4 ✅**

---

## 二、AC 覆盖矩阵

| AC | 功能 | testid | 测试结果 | 备注 |
|---|---|---|---|---|
| SC-01.AC-1 | OCR 拍题识别 | capture.upload-progress | ⚠️ | file-service（:8083）未启动；OCR 流程无法端到端验证 |
| SC-11.AC-1 | 文件直传 OSS | capture.file-input | ⚠️ | 同上，依赖 file-service + MinIO presign |

### CapturePage 说明

CapturePage 的 A 轨核心 AC（SC-01.AC-1 OCR + SC-11.AC-1 文件上传）依赖 file-service（:8083）。本次 A 轨 file-service 未启动，无法完成端到端验证。

页面本身渲染、相机控件（`capture.camera.btn`、`capture.shutter`、`capture.gallery.btn`）在 MSW B 轨已验证通过（见 CapturePage-gap-report.md）。

---

## 三、超出 A 轨范围（待下次 Sprint 联调）

| 功能 | AC | 原因 |
|---|---|---|
| OCR 识别（拍题 → stem_text） | SC-01.AC-1 | 需启动 file-service:8083 + MinIO:19000 |
| 文件直传 OSS | SC-11.AC-1 | 需真实 presign URL + MinIO PUT |
| 拍题后 analyzing 状态轮询 | SC-01.AC-2 | 需 ai-analysis-service RocketMQ 消费（topic 命名问题待修复） |

---

## 四、已知后端问题

| 问题 | 详情 | 影响 |
|---|---|---|
| RocketMQ topic 含点号 | `wrongbook.item.changed` 不符合 RocketMQ 命名规范（仅允许 `^[%|a-zA-Z0-9_-]+$`） | 软删除/更新后 MQ 事件发送失败（有 outbox fallback） |
| file-service 未在本次 A 轨启动 | 拍题/OCR 流程无法测试 | CapturePage A 轨 AC 推迟验收 |

---

## 五、User 决策

- [x] 接受合规评分（4/4 ✅）
- [ ] SC-01.AC-1 / SC-11.AC-1 ⚠️：推迟到下次 Sprint，启动 file-service 后重验
- [ ] 后端 issue：RocketMQ topic 命名修复（点号 → 下划线：`wrongbook_item_changed`）
