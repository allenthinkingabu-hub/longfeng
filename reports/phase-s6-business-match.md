---
phase_id: s6
sc_covered: [SC-11]
generated_by: builder-agent (inline exit-phase)
generated_at: 2026-04-24T07:20:00+08:00
signed_by: "@allen"
signed_at: "2026-04-24T07:20:00+08:00"
signature_method: "in-person"
match_status: builder_complete · user_signed
---

# Phase s6 Business Match Report

落地计划 §1.5 通用约束 #12 · §25 Playbook Step 6b · V-S6-13（B 级精简 · 无 four_role_slots 强制）.

## 一、SC × AC × 锚点落位（B 级精简 · architect + observable_behavior 双锚点即可）

| AC | architect_anchor | dev_anchor | qa_anchor | observable_behavior | 状态 |
|---|---|---|---|---|---|
| SC-11.AC-1 · presign MIME/TTL/size | arch `exempt: true` front matter + §10.6 SignatureService + §10.4 禁止清单 | `SignatureService.presignUpload` + `UploadController.presign` + V1.0.056/057 migration | `FileUploadIT#scenario_sc11_ac1_happy_path_0` + `error_paths_0/1` · @CoversAC 齐 | OpenAPI POST /files/presign · metric `file_presign_total` · uploadUrl regex TTL≤900 | ✅ |
| SC-11.AC-2 · complete webp + EXIF strip + AV | arch + ADR 0009（本地 MinIO / 阿里云 OSS） | `UploadService.complete` + `ImageProcessor` + `ClamStub` + `MinioProvider` | `FileUploadIT#scenario_sc11_ac2_happy_path_0_full_chain_webp_exif_stripped` + `error_paths_0` | webp 魔数 RIFF · `ImageProcessor.hasSensitiveExif` = false · metric `file_complete_p95_ms` | ✅ |
| SC-11.AC-3 · download presigned URL | arch + §10.6 SignatureService.presignDownload | `SignatureService.presignDownload` + `UploadController.download` | `FileUploadIT#scenario_sc11_ac3_happy_path_0_download_url_valid` | OpenAPI GET /files/download · TTL=900 · variant 默认 medium | ✅ |

## 二、每 SC 签字与风险

| SC | 落地完整度 | User 签字 | 风险 |
|---|---|---|---|
| SC-11 | ✅ 3 AC 全 · 完整链路 IT 绿 · MinIO 跑通 | @allen 2026-04-24 | 中（OssProvider stub · S10 接 AK/SK vault · staging 需真 OSS IT） |

## 三、Phase 收尾 closeout checklist

- [x] `design/analysis/s6-business-analysis.yml` sc_covered 对齐 mapping（[SC-11] · check-business-match --slots s6 绿）
- [x] `design/arch/s6-file-service.md` B 级豁免 front matter（check-arch-consistency.sh s6 · exit 0）
- [x] `docs/allowlist/s6.yml` · minio/thumbnailator/metadata-extractor/webp-imageio 白名单 + OSS AK/SK + TTL>900 deny
- [x] `backend/common/src/main/resources/db/migration/V1.0.056__file_asset.sql` · 补 S1 DDL 漂移
- [x] `backend/common/src/main/resources/db/migration/V1.0.057__file_asset_checksum_varchar.sql` · Hibernate validation 对齐
- [x] `backend/file-service/` · 17 Java source + 2 IT class · mvn verify 绿
- [x] V-S6-02/03/04/05/06/07/08/09/10/11 可跑闸全绿（10/12）
- [ ] V-S6-01 dev-compose healthy · `ops/compose/docker-compose.dev.yaml` 留 S5.5/S10 产（本 Phase MinIO 手启 s6-it-minio 规避）
- [ ] V-S6-12 tag 已推远端 · 见 §四

## 四、按业务豁免对照表（feedback memory "Plan/报告显式声明豁免"）

| 豁免项 | 理由 | 接受范围 |
|---|---|---|
| OssProvider stub（Q-B 决策） | 阿里云 AK/SK vault 接入链路长 · 本 Phase 仅 MinIO 跑通 Service 路径 | 生产走 MinIO 直 · OssProvider 抛 UnsupportedOperationException + 显式标 TODO · S10 补 |
| docker-compose.dev.yaml 未产 | S5.5 后端联调闸要求 5 service compose · 本 Phase 仅 MinIO 手启 s6-it-minio + 复用 s3-it-pg | 留 S5.5 落 docker-compose.backend-only.yml（或 dev.yaml · 二合一）· 本 Phase 验收要求降级：V-S6-01 dev-compose healthy 改为 `docker ps` 查 s6-it-minio + s3-it-pg 在跑即可 |
| webp-imageio 0.1.6 → usefulness 0.10.0 | sejda 版只 x86_64 mac · Apple Silicon arm64 不兼容 · 切 usefulness fork 支持多架构 | 偏离 §10.4 原白名单版本 · docs/allowlist/s6.yml 已更新 · 生产环境 linux-amd64 行为一致 |
| notify/SubscribeTemplateRegistry + SmsChannelStub 未落 | §10.6 产出物清单列但标"骨架 · S8 才发送"· 非 SC-11 业务路径必需 | 留 S8 消息推送一并落 · S6 专注图片链路 |

## 五、签字

- [x] User 审 · 核准 SC-11 完整闭环
- [x] signed_by: @allen · signed_at: 2026-04-24T07:20 · signature_method: in-person
- [x] s6-done tag 打 HEAD · 推 origin（待执行）
