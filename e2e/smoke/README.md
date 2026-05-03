# e2e/smoke · 6 份冒烟用例（plan §5.S9 出口门禁）

本目录是 6 份 smoke 的"独立运行入口"。**实际测试逻辑全部在 `../specs/sc-*.spec.ts`**，
通过 `@smoke` tag 选择子集运行。

## 6 份组成（plan §5.S9）

| Smoke | SC | 主路径 | 命令 |
|---|---|---|---|
| smoke-01-capture | SC-01 | 拍题入库 | `pnpm e2e:smoke --grep "@sc-01"` |
| smoke-02-push    | SC-02 | 推送→执行 | `pnpm e2e:smoke --grep "@sc-02"` |
| smoke-05-fusion  | SC-05 | 视图融合 | `pnpm e2e:smoke --grep "@sc-05"` |
| smoke-11-landing | SC-11 | 访客落地 | `pnpm e2e:smoke --grep "@sc-11"` |
| smoke-12-claim   | SC-12 | 游客 + claim | `pnpm e2e:smoke --grep "@sc-12"` |
| smoke-13-share   | SC-13 | 分享接收 | `pnpm e2e:smoke --grep "@sc-13"` |
| smoke-16-vip     | SC-16 | VIP AI 模型 | `pnpm e2e:smoke --grep "@sc-16"` |

## 全跑（≤ 8 min · plan §5.S9 出口门禁）

```bash
pnpm e2e:smoke
# = playwright test --grep @smoke --reporter=list
```

## 出口门禁（必须 0 失败）

- ≤ 8 min wall clock
- 0 failed test
- axe-core 0 serious
- 截图 + trace 留 reports/artifacts/
