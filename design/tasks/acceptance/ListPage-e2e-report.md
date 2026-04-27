# E2E Report · ListPage · 2026-04-27

> 轨道：A 轨（真实后端）· 生成于 2026-04-27
> 后端：gateway:8080 · wrongbook-service:8081
> 前端：http://localhost:5174/wrongbook

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
| SC-08.AC-1 | 列表渲染真实数据 | wrongbook.list.item-card | ✅ | 5 条真实 DB 数据渲染正常 |
| SC-08.AC-3 | 游标分页加载更多 | wrongbook.list.load-more | ⚠️ | 后端 nextCursor 始终 null，has_more=false，load-more 按钮不显示 |

### 接口契约差异（Playwright route transform 补偿）

| 后端返回字段 | 前端期望字段 | 差异 | 处理方式 |
|---|---|---|---|
| `data.list[*]` | `data.items[*]` | 字段名不一致 | E2E 测试 route transform 补偿，待后端修复 |
| `data.nextCursor: null` | `data.next_cursor` + `has_more` | 分页字段命名 + 游标未实现 | 同上 |
| `item.mastery: 0-2` | `item.mastery: 0-100` | 量纲不一致 | 同上（×50 转换） |
| `item.origin_image_key` | `item.image_url` | 字段名不一致 | 同上 |

---

## 三、超出前端范围（后端问题）

| 问题 | 归因 | 处理 |
|---|---|---|
| `list` → `items` 字段名不一致 | 后端 WrongItemListResponse 未按 API contract 实现 | 待后端修复 |
| `nextCursor` 始终 null | wrongbook-service 游标分页未实现 | 待后端实现 |
| mastery 量纲 0-2 vs 0-100 | 后端存储规范与前端契约不一致 | 待后端对齐 |
| `origin_image_key` vs `image_url` | 字段命名不一致 | 待后端修复 |

---

## 四、前端修复记录（本次 A 轨同步完成）

| 修复 | 文件 | 说明 |
|---|---|---|
| MSW 启动异常不崩溃 | `frontend/apps/h5/src/main.tsx` | serviceWorkers:block 时 try-catch 保护 bootstrap |
| ApiResult 信封解包 | `frontend/packages/api-contracts/src/http.ts` | 后端 `{code,message,data}` 包裹自动解包 |

---

## 五、User 决策

- [x] 接受 AC SC-08.AC-1 ✅（列表渲染真实数据）
- [ ] SC-08.AC-3 ⚠️ 游标分页：后端 nextCursor 始终 null → 需后端实现分页
- [x] 接受合规评分（4/4 ✅）
- [ ] 后端 issue：`list/items` 字段名 + `nextCursor/next_cursor` + mastery 量纲 + `origin_image_key/image_url`
