# Contract Gaps — S5

> 比对来源：
> - 前端真源：`frontend/packages/api-contracts/src/types.ts:96-106` (`ReviewPlanVO`)
> - 后端真源：`design/arch/s5-review-plan.md §3.1` (`ReviewPlanDto` schema)
> - 业务分析：`design/analysis/s5-business-analysis.yml`
>
> **状态**：全部 gap 已自解决 · 2026-04-28 · `has_blocking_gaps: false` · Builder 可开工

---

## BLOCKER

| Gap ID | 前端期望 | arch 定义 | 裁决 |
|---|---|---|---|
| ~~G-01~~ | `next_due_at: string` | `nextReviewAt: date-time` | ✅ **已解决**：VO 使用 `@JsonProperty("next_due_at")`。"due" 语义更贴近前端使用场景（到期），前端明确使用此名。 |

---

## WARNING（全部已自解决）

| Gap ID | 前端期望 | arch 定义 | 裁决 |
|---|---|---|---|
| ~~G-02~~ | `id: string` | `id: integer(int64)` | ✅ **已解决**：`String.valueOf(id)`，S3/S4 已有先例 |
| ~~G-03~~ | `wrong_item_id: string` | `wrongItemId: integer(int64)` | ✅ **已解决**：`@JsonProperty("wrong_item_id")` + `String.valueOf()` |
| ~~G-04~~ | `user_id: string` | arch DTO 未列 | ✅ **已解决**：`review_plan.user_id` 列存在（arch line 171 `+Long userId`）。VO 补 `@JsonProperty("user_id")` + String 序列化 |
| ~~G-05~~ | `mastery: number` | arch DTO 未列 | ✅ **已解决**：`review_plan` 无 mastery 列，来自 `wrong_item.mastery`（SMALLINT 0..2）。`ReviewPlanRepository` 查询 `JOIN wrong_item wi ON wi.id=rp.wrong_item_id` 回填 `mastery` |
| ~~G-06~~ | `interval: number` | `intervalDays: integer` | ✅ **已解决**：`@JsonProperty("interval")`，无 `_days` 后缀 |
| G-07 | 无 `clients/review*.ts` 文件 | — | ⏭ **S7/S8 处理**：S5 是后端 Phase，S8 负责前端 client 文件；不阻断后端开工 |

---

## v2 增量 AC（本次不实现）

`SC-09.AC-v2-1/2/3`（subjectBreakdown[]、ebbinghaus[]、ownerId param）状态为 `gate_status: in_review`，待 G-Arch 审签。be-builder S5 只实现 v1 五端点契约，v2 字段预留 TODO 注释。

---

## 结论

`has_blocking_gaps: false` · `/be-builder s5` 可开工
