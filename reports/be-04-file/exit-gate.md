# BE-04-file Exit Gate Self-Check

**Agent**: BE-04-file (b mode · Read+Write only)
**Branch**: agent/be-04-file
**Date**: 2026-05-02
**Base**: feature/s7-frontend-core @ de321e8

---

## 1. Deliverables Checklist

### 1.1 Production Classes (12 / 12 ✅)

| # | Class | Path | Status |
|---|---|---|---|
| 1 | `PresignController` | `controller/PresignController.java` | ✅ Written |
| 2 | `CallbackController` | `controller/CallbackController.java` | ✅ Written |
| 3 | `AttachmentStorage` (interface) | `provider/AttachmentStorage.java` | ✅ Written |
| 4 | `MinioAttachmentStorage` | `provider/MinioAttachmentStorage.java` | ✅ Written |
| 5 | `ObsAttachmentStorage` | `provider/ObsAttachmentStorage.java` | ✅ Written |
| 6 | `S3AttachmentStorage` | `provider/S3AttachmentStorage.java` | ✅ Written |
| 7 | `ObjectKeyBuilder` | `support/ObjectKeyBuilder.java` | ✅ Written |
| 8 | `FileTtlSweepJob` | `job/FileTtlSweepJob.java` | ✅ Written |
| 9 | `WbFile` | `entity/WbFile.java` | ✅ Written |
| 10 | `WbFileLifecycle` | `entity/WbFileLifecycle.java` | ✅ Written |
| 11 | `WbFileRepository` | `repo/WbFileRepository.java` | ✅ Written |
| 12 | `WbFileLifecycleRepository` | `repo/WbFileLifecycleRepository.java` | ✅ Written |

Also modified:
- `Application.java` — added `@EnableScheduling` for `FileTtlSweepJob`
- `config/StorageConfigRegistration.java` — updated Javadoc (no logic change needed, scan covers existing package)

### 1.2 Test Classes (5 ✅)

| Test Class | Scenarios Covered |
|---|---|
| `controller/PresignControllerTest` | Happy path · WbFile saved PENDING · Lifecycle saved · MIME not allowed · File too large |
| `controller/CallbackControllerTest` | No auth (dev mode) → marks UPLOADED · File not found → 200 no-op · Invalid pub-key-url host → 403 · Malformed auth → 403 |
| `provider/MinioAttachmentStorageTest` | presign happy/fail · get happy/fail · delete happy/fail · promote no-op · name() |
| `job/FileTtlSweepJobTest` | IA promotion · Archive sweep · Delete sweep · OSS delete failure resilience · No-due zero count |
| `support/ObjectKeyBuilderTest` | Path format · yyyyMM partition · No email in path · Unsafe chars · Path traversal · Null/blank · Long filename truncation · Windows path |

---

## 2. Red Line Self-Check

### C7 — byte[] not in heap for image data

**Grep result**: `byte[]` appears in new production files only in:
- `CallbackController.java` lines 175, 198 — RSA signature bytes and RSA public key bytes (crypto verification, NOT image data, method-local only)
- Comments/Javadoc in multiple files (non-code)

**Status**: ✅ PASS — No image byte[] persisted or held in heap. All image-related operations go through presigned URLs (direct OSS upload). Crypto byte[] is method-local, not stored.

### C8 — BusinessException contains `msgkey:` prefix

**Grep result**: All `BusinessException` throws carry `msgkey:` prefix:
- `PresignController`: `msgkey:file.error.mime_not_allowed`, `msgkey:file.error.file_too_large`
- `CallbackController`: `msgkey:file.error.callback_sign_failed`
- `MinioAttachmentStorage`: `msgkey:file.error.presign_failed`, `msgkey:file.error.get_url_failed`, `msgkey:file.error.delete_failed`
- `ObsAttachmentStorage`: `msgkey:file.error.obs_not_configured` (×4 methods)
- `S3AttachmentStorage`: `msgkey:file.error.s3_not_configured` (×4 methods)

**Status**: ✅ PASS — All BusinessException instances carry `msgkey:` prefix (C8 enforced at construction time by `BusinessException.requireMsgkey()`).

### C9 — Time fields use OffsetDateTime/ZonedDateTime

**Grep result**: All timestamp fields in new entities use `OffsetDateTime`:
- `WbFile`: `uploadedAt` (OffsetDateTime), `createdAt` (OffsetDateTime)
- `WbFileLifecycle`: `promoteAt`, `archiveAt`, `deleteAt` (all OffsetDateTime)
- `FileTtlSweepJob.sweep()`: `OffsetDateTime.now(ZoneOffset.UTC)`
- `PresignController`: `OffsetDateTime.now(ZoneOffset.UTC)`
- `CallbackController`: `OffsetDateTime.now(ZoneOffset.UTC)`

**Status**: ✅ PASS

### D-OSS-Key path format

`ObjectKeyBuilder.build()` produces: `wrongbook/{tenantId}/{yyyyMM}/{studentId}/{snowflakeId}_{sanitizedFilename}`

- No student email in path (only numeric IDs in path segments)
- Path traversal prevention in `sanitize()`
- Unsafe chars replaced with `_`
- Filename truncated to 128 chars max

**Status**: ✅ PASS

---

## 3. Decision Points

| Decision | Choice | Reason |
|---|---|---|
| `AttachmentStorage` is a NEW SPI | Parallel to existing `StorageProvider` | Existing `StorageProvider` powers the original `UploadController`/`SignatureService`; new SPI wires `PresignController`/`CallbackController` without breaking existing IT |
| `MinioAttachmentStorage` conditional on `app.storage.provider=minio` | Different config prefix from existing `file-service.storage.provider=minio` | Avoids Spring bean conflict with existing `MinioProvider`; TDD §10.3 uses `app.storage.*` namespace |
| `ObsAttachmentStorage` + `S3AttachmentStorage` throw on all calls | AK/SK vault deferred to S10 (per TDD D-Storage) | Keeps code compilable and deployable in dev; S10 will wire real SDK |
| `CallbackController` allows no-auth in dev | MinIO does not send OSS callback headers | Dev workflow: frontend calls `/api/files/callback` manually without OSS signature |
| `WbFile.createdAt` not `@CreatedDate` | Manually set in controller | Avoids JPA auditing dependency; simpler for test; consistent with UTC-explicit pattern |
| `FileTtlSweepJob` uses `@Scheduled` + `@EnableScheduling` | XXL-Job integration deferred | Provides working local scheduler; XXL-Job can wrap the same method in S10 |

---

## 4. S3+ Task Handoff

### For Orchestrator (Orchestrator review + commit)

1. `mvn -pl backend/file-service test` — should run 5 new test classes + 4 existing IT (if containers up)
2. Check: no compilation errors (all imports resolvable via existing `pom.xml` deps)
3. Check: `@ConditionalOnProperty(name = "app.storage.provider")` does NOT conflict with `file-service.storage.provider` — they are different config keys

### Potential Issues to Watch

- `PresignController` injects `WbFileLifecycleRepository` but saves with `file.setFile(file)` — the `@MapsId` on `WbFileLifecycle` requires the `WbFile` to be saved first (which it is: `fileRepo.save(file)` before `lifecycleRepo.save(lifecycle)`). Order is correct.
- `WbFile.createdAt` has `updatable = false` but is set manually in controller. JPA will honor the manual set on first insert.
- `app.storage.minio.bucket` injected as `@Value` in `PresignController` — this requires the `app.storage.*` properties to be in `application.yml`. These are not in the current `application.yml` (which uses `file-service.storage.*`). Orchestrator should add `app.storage.*` properties to `application.yml` or `application-dev.yml`.

### Configuration Gap (requires Orchestrator action)

The new `PresignController`, `MinioAttachmentStorage`, `FileTtlSweepJob` require `app.storage.*` properties per TDD §10.3.
Current `application.yml` only has `file-service.storage.*` (old namespace).

**Orchestrator must add** to `application.yml`:
```yaml
app:
  storage:
    provider: ${FEATURE_STORAGE_PROVIDER:minio}
    presign-ttl-min: 15
    minio:
      endpoint: ${STORAGE_ENDPOINT:http://localhost:9000}
      access-key: ${STORAGE_AK:minio}
      secret-key: ${STORAGE_SK:minio12345}
      bucket: ${STORAGE_BUCKET:wrongbook-dev}
    obs:
      endpoint: https://obs.cn-hangzhou.aliyuncs.com
      bucket: wrongbook-prod-cn
      access-key: ${OSS_AK:}
      secret-key: ${OSS_SK:}
    s3:
      region: us-east-1
      bucket: wrongbook-prod-overseas
      access-key: ${AWS_AK:}
      secret-key: ${AWS_SK:}
    lifecycle:
      ia-after-days: 30
      archive-after-days: 180
      sweep-cron: "0 0 2 * * ?"
    guest-tmp-bucket: guest-tmp-cn
    guest-tmp-ttl-min: 5
```

---

## 5. File Inventory

### Production files written

```
backend/file-service/src/main/java/com/longfeng/fileservice/
├── Application.java                              MODIFIED (+ @EnableScheduling)
├── config/
│   └── StorageConfigRegistration.java            MODIFIED (Javadoc only)
├── controller/
│   ├── PresignController.java                    NEW
│   └── CallbackController.java                   NEW
├── entity/
│   ├── WbFile.java                               NEW
│   └── WbFileLifecycle.java                      NEW
├── job/
│   └── FileTtlSweepJob.java                      NEW
├── provider/
│   ├── AttachmentStorage.java                    NEW (SPI interface)
│   ├── MinioAttachmentStorage.java               NEW
│   ├── ObsAttachmentStorage.java                 NEW
│   └── S3AttachmentStorage.java                  NEW
├── repo/
│   ├── WbFileRepository.java                     NEW
│   └── WbFileLifecycleRepository.java            NEW
└── support/
    └── ObjectKeyBuilder.java                     NEW
```

### Test files written

```
backend/file-service/src/test/java/com/longfeng/fileservice/
├── controller/
│   ├── PresignControllerTest.java                NEW
│   └── CallbackControllerTest.java               NEW
├── job/
│   └── FileTtlSweepJobTest.java                  NEW
├── provider/
│   └── MinioAttachmentStorageTest.java           NEW
└── support/
    └── ObjectKeyBuilderTest.java                 NEW
```

### Unchanged (existing files not touched)

```
controller/HealthController.java
controller/UploadController.java
dto/CompleteResp.java, DownloadResp.java, PresignReq.java, PresignResp.java
entity/FileAsset.java
exception/* (all 5 files)
provider/MinioProvider.java, OssProvider.java, StorageProvider.java
repo/FileAssetRepository.java
scan/AntivirusClient.java, ClamStub.java
service/ImageProcessor.java, SignatureService.java, UploadService.java
support/SnowflakeIdGenerator.java
config/OpenApiConfig.java, StorageProperties.java
```

---

## 6. Exit Gate Summary

| Gate | Status |
|---|---|
| 12 production classes written | ✅ |
| 5 test classes written | ✅ |
| C7: byte[] only in crypto method params, not image heap | ✅ |
| C8: all BusinessException carry msgkey: prefix | ✅ |
| C9: OffsetDateTime/ZonedDateTime in all time fields | ✅ |
| D-OSS-Key path format correct + no email in path | ✅ |
| No SQL migration written (S1 already has V1.0.080/081) | ✅ |
| No changes to other modules (common/anonymous-service/etc.) | ✅ |
| Configuration gap documented for Orchestrator | ✅ (see §4) |
