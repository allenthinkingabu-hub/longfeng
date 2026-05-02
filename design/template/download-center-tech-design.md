# Download Center — Technical Design Document (TDD)

| Field | Value |
|---|---|
| Document ID | TDD-DLC-01 |
| Companion to | PRD-DLC-01 (`download-center-prd.md`) |
| Author | System Architect |
| Date | 2026-05-01 |
| Status | Draft v1.0 — for engineering pickup |
| Audience | Backend engineers, FE engineers, QA, DevOps |

---

## 0. Design Principles & Decisions Register

The architecture of the Download Center is guided by these five core principles, followed by the architect-level defaults applied during this TDD.

### 0.1 Asynchronous Decoupling (Async-by-default)
The design shifts from a synchronous "request-response" model to a background job-based model. By returning an immediate `jobId` (HTTP 202), the system prevents UI blocking and browser timeouts for long-running exports (e.g., large ZIP files or complex PDFs). A legacy `X-Export-Mode: sync` header is maintained for backward compatibility.

### 0.2 State-Machine Driven Reliability
Every download task is managed by a strict state machine (`QUEUED` → `RUNNING` → `READY`/`FAILED`/`CANCELLED`). This ensures deterministic behavior and allows for automated lifecycle management, including an **Expiry mechanism** for storage cleanup and a **Reaper mechanism** to recover from system crashes.

### 0.3 Distributed Scalability & Safety
To support multi-pod deployments, the system uses PostgreSQL `FOR UPDATE SKIP LOCKED` for atomic, thread-safe job claiming. Status transitions are isolated in separate transactions (`REQUIRES_NEW`), ensuring that task metadata is persisted even if the underlying business logic fails.

### 0.4 Architectural Reuse (Port/Adapter Pattern)
The design avoids logic duplication by wrapping existing export pipelines (like the 9-step `ReportExportServiceImpl`) into "Ports." This allows the background worker to execute existing code as a service without requiring a rewrite of complex business logic.

### 0.5 Real-time Feedback Loop
The system provides a modern user experience by combining **Server-Sent Events (SSE)** for live dashboard updates with the existing **Notification Pipeline** to alert users when their files are ready, regardless of their current location in the application.

---

### 0.6 Decisions Register

These are the architect-level defaults applied during this TDD. Each is the recommended option from Step 1 of the design protocol; the user retained the right to override any of them.

| ID | Decision | Chosen | Why | Rejected alternatives |
|---|---|---|---|---|
| D-Q1 | NotificationKind for download events | Reuse `STATUS`; discriminate by `category=DOWNLOAD` + `subKind=READY/FAILED` carried in `NotificationCard.body` (JSON tail) | Zero front-end color-palette change; `NotificationKind` enum stays closed | Adding `DOWNLOAD_READY` / `DOWNLOAD_FAILED` enum values (forces FE coupling) |
| D-Q2 | Progress granularity | 3 buckets — `5 %` (worker pickup), `95 %` (S3 upload done), `100 %` (READY); UI shows indeterminate animation between 5 and 95 | No structural change to the 9-step pipeline (preserves PRD constraint C1) | Adding `ProgressCallback` to `ReportExportService` (touches the locked pipeline) |
| D-Q3 | RUNNING-state cancel semantics | Best-effort: cancel flag observed only at the worker boundary. `RUNNING` → user clicks cancel → worker finishes the in-flight call, then short-circuits before S3 upload, marks `CANCELLED`, no audit publish | Single-method monolithic pipeline cannot be safely interrupted mid-flight without rewrite | True cooperative cancel inside the 9-step pipeline (violates C1) |
| D-Q4 | Object-storage key schema | `{normalizedPrefix}{jobId}/{uuid}_{sanitizedFilename}` — generated via existing `AttachmentObjectKeys.buildObjectKey(prefix, jobId.toString(), filename)` | Reuses existing key building convention; **no PII in path**; `AttachmentStorageCleanupService` works unchanged | `download/{operatorId}/{jobId}/{filename}` (PRD §14 Q2) — leaks operator identity into bucket path |
| D-Q5 | Edits to existing `ReportExportController` | Allowed but minimal: (a) drop method-level `@Transactional`, (b) read `X-Export-Mode` header to branch sync vs async, (c) sync branch returns 200 + bytes (unchanged contract); async branch returns 202 + jobId | Smallest possible change to support dual-mode coexistence | Build a v2 controller and freeze the old one (doubles surface area, complicates routing) |
| D-Scope | Phase 1 entry-point coverage | **PRD §FR-1 lock — 4 endpoints only**: A1, A2 (Report Export); B4 (Crisis Snapshot XLSX export-xlsx); B5 (Admin Report ZIP export-zip) | Honors PRD scope. Other download paths (Case PDF/DOCX/XLSX, NCEMA matrix, generic Excel framework) deferred to Phase 2 with the same Worker framework | Expanding to all 24 download endpoints found in the codebase audit (out-of-PRD scope) |
| D-DB | Database dialect | PostgreSQL ≥ 9.5 (assumed — confirmed by PRD §7.1 `JSONB` and existing migrations) | `SELECT ... FOR UPDATE SKIP LOCKED` is PG-only | None |
| D-Pod | Deploy topology | Multi-pod (assumed — confirmed by existing `SchedulerLockConfig` + ShedLock usage) | Worker pickup, expiry scheduler, and stuck-job reaper all need cluster-safe singleton/quorum primitives | None |
| D-Vol | Sizing assumptions | Conservative baseline: 50–500 exports/day, p99 ZIP ≤ 50 MB, p99 PDF ≤ 5 MB. Drives `core=4 / max=16 / queue=256` thread-pool defaults | No measured volumes provided; chosen to be safe for known UCMDS export patterns | None |
| D-Mem | Memory model for large bytes | **Temp-file spool**: worker writes `byte[]` to a dedicated temp dir immediately after the underlying call returns, releases the heap reference, then streams the temp file to object storage via the active `AttachmentStorage` impl. Per-backend upload strategy: **OBS (production, G42)** → SDK-native multipart (`createMultipartUpload` / `uploadPart` / `completeMultipartUpload`); **S3 / MinIO (dev)** → `S3TransferManager` with `~8 MB` part buffers. Plus **size-class pools** (small ≤ 10 MB / large > 10 MB) to bound concurrency × bytes. Plus **disk watchdog** rejecting new enqueue when temp dir < 1 GB free. | `ReportExportArtifact.bytes()` is `byte[]` and the contract cannot change (C1). Without spooling, peak heap = 20 workers × max ZIP ≈ 4 GB → OOM Sev-1. | (a) Hold bytes in heap → OOM risk; (b) refactor artifact to `InputStream` → violates C1; (c) presigned PUT from worker → still needs full bytes in memory. |
| D-SSE | SSE delivery model | **Per-pod in-memory `LocalEmitterRegistry`** + **dedicated `dlcSseFanoutExecutor`** (fallback path under platform threads) + **per-emitter 2 s send timeout** + **3 emitter cap per operator** + nginx `X-Accel-Buffering: no`. **VT-aware**: when `Thread.currentThread().isVirtual()` (default in this project, JDK 21), publisher sends synchronously and skips the executor — VT scheduler handles client backpressure. Multi-pod fanout deferred to Phase 2 (`LISTEN/NOTIFY` bridge); polling fallback covers the gap. | Synchronous `emitter.send()` on a platform thread blocks on slow clients; under VT this is no longer a problem but the executor stays as fallback for `SPRING_VT_ENABLED=false`. | Redis pub/sub (extra dep), Kafka (overkill), WebSocket (re-architects the contract). |
| D-Storage | Object storage backend | **OBS-first** for production (`app.attachment.storage=obs`, G42 endpoint `obs.ae-ad-1.g42cloud.com`, virtual-hosted-style via AWS S3 SDK v2). `S3AttachmentStorage` (MinIO / AWS S3, path-style) for dev/test. `ObsProxyAttachmentStorage` available when FE direct upload needs to bypass browser CORS. Worker's `storeStreaming` is per-impl: OBS uses SDK-native multipart; S3 uses `S3TransferManager`. | Project already ships three production-relevant `AttachmentStorage` impls; OBS is the certified production target on G42 cloud. | (a) Hardcode S3 / `S3TransferManager` everywhere → breaks G42 deployment; (b) Use OBS-native SDK (`com.obs.services`) → conflicts with the project's "AWS S3 SDK throughout" choice in existing `ObsAttachmentStorage`. |

> **Override protocol:** any of D-Q1 … D-Q5 / D-Scope / D-Vol / D-Mem / D-SSE can be flipped before Phase 0 by changing this register; Phase 1 acceptance tests will be regenerated from whichever values are in effect at engineering kickoff.

---

## 1. Scope

### 1.1 In scope (Phase 1)

The Download Center wraps these 4 entry points in async lifecycle + central dashboard:

| # | HTTP entry | Source service (unchanged) | Output | Source type enum |
|---|---|---|---|---|
| A1 | `POST /api/v1/admin/incidents/{id}/exports/report` | `ReportExportApplicationService` → `ReportExportServiceImpl` (9-step pipeline) | PDF / ZIP | `REPORT_INCIDENT` |
| A2 | `POST /api/v1/admin/cases/{caseFileId}/exports/report` | `ReportExportApplicationService` → `ReportExportServiceImpl` | PDF / ZIP | `REPORT_CASE` |
| B4 | `GET  /api/v1/admin/snapshots/{id}/export-xlsx` | `AdminCrisisSnapshotController` (existing handler) | XLSX | `CRISIS_SNAPSHOT_XLSX` |
| B5 | `GET  /api/v1/admin/reports/{id}/export-zip` | `AdminReportController` (existing handler) | ZIP | `ADMIN_REPORT_ZIP` |

> A1/A2 share the rich `ReportExportRequest` payload + 9-step pipeline. B4/B5 use simpler request shapes — TDD §6.4 introduces a `DownloadJobPayload` sealed-interface so the worker can dispatch on type without a god-object request DTO.

### 1.2 Out of scope (Phase 1)

| Surface | Reason | Future phase |
|---|---|---|
| Case PDF/DOCX/XLSX (`AdminCaseExportController`) | Not in PRD §FR-1 list | Phase 2 candidate |
| NCEMA matrix (`AdminCrisisController`) | Not in PRD §FR-1 list | Phase 2 candidate |
| Generic Excel framework (`AdminExcelController` D1–D9) | Not in PRD §FR-1 list; needs cross-cutting injection at `ExcelService` | Phase 3 candidate |
| Attachment direct download (E1/E2) | <100 ms latency; not a generation flow | Never |
| PDF verify endpoint (F1) | Upload, not download | Never |
| Final-file generation (H1) | Already returns JSON reference | Phase 2 — surface in dashboard via attachment FK |

### 1.3 Hard non-goals (PRD constraints carried forward)

- C1 — `ReportExportServiceImpl` 9-step pipeline structure is not modified
- C2 — `attachment` row contract preserved (storage_key may now be non-null while bytes are alive; SHA-256 contract unchanged)
- C3 — `ReportExportedEvent` remains the single source of truth for the audit listener; the worker does not emit a duplicate event
- C4 — Sync mode (`X-Export-Mode: sync`) coexists for one full release before decommission
- C5 — Server-side error responses use `msgkey:` payloads; CI gate enforces EN/AR key parity
- C6 — SHA-256 verification continues to work against `attachment.sha256_hash` for both Phase-1 download-center exports and legacy sync exports

---

## 2. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                  Browser                                     │
└──────────┬──────────────────────────────────────────────▲────────────────────┘
           │ POST .../exports/report                      │ SSE: download.updated
           │ (default: async)                             │ GET .../downloads/stream
           ▼                                              │
┌──────────────────────────┐    ┌───────────────────────────────────────────┐
│ ReportExportController   │    │ DownloadCenterController (NEW)            │
│   - keeps sync path      │    │   GET    /downloads          (list)       │
│   - X-Export-Mode: sync  │    │   GET    /downloads/{id}     (detail)     │
│     → legacy 200 + bytes │    │   GET    /downloads/stream   (SSE)        │
│   - default → enqueue    │    │   GET    /downloads/{id}/file (stream)    │
│ AdminCrisisSnapshotCtrl  │    │   POST   /downloads/{id}/cancel           │
│ AdminReportController    │    │   POST   /downloads/{id}/retry            │
│   (B4/B5 same pattern)   │    │   DELETE /downloads/{id}                  │
└──────────┬───────────────┘    └─────────┬─────────────────────────────────┘
           │ enqueue()                    │ all reads/writes via
           ▼                              ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ DownloadJobApplicationService (NEW — Spring @Service)                    │
│   enqueue / list / get / cancel / retry / softDelete / streamForOperator│
│   ALL ownership checks live here (operator vs holder of VIEW_ALL)       │
└──────────┬───────────────────────────────────────────────────────────────┘
           │ INSERT download_job (status=QUEUED, request_payload=jsonb)
           │ publish DownloadJobEnqueuedEvent  (in-process Spring event)
           ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ DownloadJobDispatcher (NEW)                                              │
│   - listens to enqueue events                                            │
│   - submits Runnable to downloadCenterExecutor                           │
│   - also drains queue on startup (recovers QUEUED rows from a crash)    │
└──────────┬───────────────────────────────────────────────────────────────┘
           │ executor.submit(() -> worker.process(jobId))
           ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ DownloadJobWorker (NEW — non-transactional shell)                        │
│   1. txOps.markRunning(jobId)                ── REQUIRES_NEW             │
│   2. payload = repo.loadFrozenPayload(jobId)                             │
│   3. switch (payload.sourceType) {                                       │
│        REPORT_INCIDENT, REPORT_CASE                                      │
│          → reportExportApplicationService.export(toReportRequest(...))  │
│        CRISIS_SNAPSHOT_XLSX                                              │
│          → crisisSnapshotExportPort.export(payload.snapshotId, ...)     │
│        ADMIN_REPORT_ZIP                                                  │
│          → adminReportExportPort.exportZip(payload.reportId, ...)       │
│      }                                                                   │
│   4. if (cancelRequested) → txOps.markCancelled(jobId); return          │
│   5. storageKey = s3.store(jobId, filename, bytesStream, size)          │
│   6. txOps.markReady(jobId, storageKey, attachmentId, sha256, size)    │
│   7. publish DownloadJobReadyEvent  ── triggers notification            │
│   On any throw at step 3-5: txOps.markFailed(jobId, errorCode, message) │
│   on a NEW transaction so the failure record is durable                 │
└──────────┬───────────────────────────────────────────────────────────────┘
           │ artifact.bytes()  →  S3
           ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ S3AttachmentStorage (UNCHANGED)                                          │
│   .store(reportId=jobId, fileName, InputStream, size)                    │
└──────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│ DownloadJobReadyEvent / DownloadJobFailedEvent                           │
│   ↓ NotificationFanoutListener (NEW)                                     │
│   ↓ NotificationPipeline.publish(payload)                                │
│   ↓ DownloadReadyStrategy / DownloadFailedStrategy (NEW NotificationStrategy beans) │
│   ↓ NotificationCard(kind=STATUS, body=JSON{category:DOWNLOAD, subKind:READY|FAILED, jobId}) │
└──────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│ DownloadJobExpiryScheduler   (@Scheduled hourly + @SchedulerLock)        │
│   - finds READY rows with expires_at < now                               │
│   - calls AttachmentStorageCleanupService.deleteObjectsAsync(keys)      │
│   - flips status=EXPIRED, attachment.storage_key=null                    │
│                                                                           │
│ DownloadJobReaperScheduler   (@Scheduled every 5m + @SchedulerLock)      │
│   - finds RUNNING rows with started_at < now - stuck-after (10m)         │
│   - flips status=FAILED, errorCode=download.error.worker_lost            │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Package & File Layout

### 3.1 New packages

```
ae.gov.safar.domain.download/
├── entity/
│   ├── DownloadJob.java                  @Entity @Table(name="download_job")
│   ├── DownloadJobStatus.java            enum (QUEUED|RUNNING|READY|FAILED|CANCELLED|EXPIRED)
│   └── DownloadJobSourceType.java        enum (REPORT_INCIDENT|REPORT_CASE|CRISIS_SNAPSHOT_XLSX|ADMIN_REPORT_ZIP)
├── repository/
│   └── DownloadJobRepository.java        Spring Data JPA + native claim query
├── payload/
│   ├── DownloadJobPayload.java           sealed interface
│   ├── ReportExportPayload.java          implements DownloadJobPayload
│   ├── CrisisSnapshotXlsxPayload.java    implements DownloadJobPayload
│   ├── AdminReportZipPayload.java        implements DownloadJobPayload
│   └── DownloadJobPayloadCodec.java      Jackson read/write — round-trip tested
├── port/
│   ├── CrisisSnapshotExportPort.java     interface — implemented by AdminCrisisSnapshotController/service refactor
│   └── AdminReportExportPort.java        interface — implemented by AdminReportController/service refactor
├── service/
│   ├── DownloadJobWorker.java            non-transactional shell, @Async via executor.submit()
│   ├── DownloadJobTxOperations.java      4 REQUIRES_NEW methods (markRunning|markReady|markFailed|markCancelled)
│   ├── DownloadJobDispatcher.java        listens to JobEnqueuedEvent, also drains on startup
│   ├── DownloadJobExpiryScheduler.java   hourly cleanup
│   ├── DownloadJobReaperScheduler.java   stuck-job recovery
│   └── DownloadJobNotificationFanoutListener.java   bridges DownloadJobReady/FailedEvent → NotificationPipeline
├── event/
│   ├── DownloadJobEnqueuedEvent.java
│   ├── DownloadJobReadyEvent.java
│   └── DownloadJobFailedEvent.java
└── notification/
    ├── DownloadReadyStrategy.java        @Component implements NotificationStrategy
    └── DownloadFailedStrategy.java       @Component implements NotificationStrategy

ae.gov.safar.application.download/
├── DownloadJobApplicationService.java    facade: enqueue / list / get / cancel / retry / softDelete / streamForOperator
└── dto/
    ├── DownloadJobView.java              read DTO for list/get
    ├── EnqueueRequest.java               polymorphic (delegate to DownloadJobPayload subtypes)
    └── EnqueueResult.java                { jobId, status }

ae.gov.safar.controller.admin/
└── DownloadCenterController.java         8 endpoints, NO @Transactional
```

### 3.2 Modified files

```
ae/gov/safar/controller/admin/ReportExportController.java
  - drop method-level @Transactional
  - read X-Export-Mode header; default → enqueue, sync → existing path

ae/gov/safar/controller/admin/AdminCrisisSnapshotController.java
  - same X-Export-Mode pattern for the export-xlsx endpoint
  - extract the existing handler body into CrisisSnapshotExportPort impl

ae/gov/safar/controller/admin/AdminReportController.java
  - same X-Export-Mode pattern for the export-zip endpoint
  - extract the existing handler body into AdminReportExportPort impl

ae/gov/safar/security/Permissions.java
  + DOWNLOAD_CENTER_VIEW       = "download_center:view"
  + DOWNLOAD_CENTER_VIEW_ALL   = "download_center:view_all"

ae/gov/safar/<seed>/UserSeed.java       (or wherever seedPermissions() lives)
  + grant DOWNLOAD_CENTER_VIEW to every persona currently holding
    REPORT_VIEW | EXPORT_CASE | PROSECUTION_EXPORT_FILE | CRISIS_VIEW
  + grant DOWNLOAD_CENTER_VIEW_ALL to system-admin role only

src/main/resources/messages.properties
src/main/resources/messages_ar.properties
  + download.center.* (per PRD §6.1)
  + download.error.* (per PRD §5.2)

src/main/resources/db/migration/V<next>__download_job.sql
  (new)

application.yml / application-*.yml
  + safar.export.download-center.*  (see §10)
```

---

## 4. Data Model

### 4.1 `download_job` DDL (Flyway migration)

```sql
-- V<NEXT>__download_job.sql

CREATE TABLE download_job (
    job_id              UUID         PRIMARY KEY,
    operator_id         BIGINT       NOT NULL,
    operator_username   VARCHAR(128) NOT NULL,
    source_type         VARCHAR(32)  NOT NULL,
    source_entity_id    BIGINT       NOT NULL,
    source_business_no  VARCHAR(64),
    request_payload     JSONB        NOT NULL,
    locale              VARCHAR(10)  NOT NULL,
    status              VARCHAR(16)  NOT NULL,
    progress            SMALLINT     NOT NULL DEFAULT 0,
    cancel_requested    BOOLEAN      NOT NULL DEFAULT FALSE,
    attachment_id       BIGINT       REFERENCES attachment(id),
    file_name           VARCHAR(512),
    format              VARCHAR(8),
    media_type          VARCHAR(64),
    size_bytes          BIGINT,
    sha256              CHAR(64),
    storage_key         VARCHAR(512),
    error_code          VARCHAR(64),
    error_message       TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at          TIMESTAMPTZ,
    finished_at         TIMESTAMPTZ,
    expires_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    source_ip           VARCHAR(64),
    user_agent          TEXT,
    -- Idempotency: FE may pass an Idempotency-Key header; same key from same
    -- operator returns the existing row instead of inserting a duplicate.
    -- Scope: holds until the row is soft-deleted.
    idempotency_key     VARCHAR(64),
    -- Optimistic-locking column for JPA @Version (prevents lost updates
    -- between SSE-stream readers and the worker's status flip).
    version             BIGINT       NOT NULL DEFAULT 0
);

-- List view (operator's own active+history dashboard)
CREATE INDEX idx_dlj_operator_status_created
    ON download_job (operator_id, status, created_at DESC)
    WHERE deleted_at IS NULL;

-- Worker queue scan (claimNextQueued): only the live work set
CREATE INDEX idx_dlj_status_created
    ON download_job (status, created_at)
    WHERE status IN ('QUEUED', 'RUNNING');

-- Reaper: stuck RUNNING jobs
CREATE INDEX idx_dlj_running_started
    ON download_job (started_at)
    WHERE status = 'RUNNING';

-- Expiry sweep: READY past TTL
CREATE INDEX idx_dlj_ready_expires
    ON download_job (expires_at)
    WHERE status = 'READY' AND deleted_at IS NULL;

-- Idempotency: same operator + same key → existing row replays
CREATE UNIQUE INDEX uq_dlj_idempotency
    ON download_job (operator_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL AND deleted_at IS NULL;
```

> Naming note: `cancel_requested` (column) is the durable form of the cancel intent (PRD §FR-6, D-Q3). Workers read it at the boundary check after the underlying export call returns; the controller path that handles `POST /downloads/{id}/cancel` simply sets it to TRUE plus writes `cancelled_at` (when QUEUED) or leaves the worker to short-circuit (when RUNNING).

### 4.2 Entity (sketch)

```java
@Entity
@Table(name = "download_job")
@Getter @Setter @NoArgsConstructor
public class DownloadJob {

    @Id
    private UUID jobId;

    @Column(nullable = false) private Long operatorId;
    @Column(nullable = false) private String operatorUsername;

    @Enumerated(EnumType.STRING) @Column(nullable = false) private DownloadJobSourceType sourceType;
    @Column(nullable = false) private Long sourceEntityId;
    private String sourceBusinessNo;

    /** Frozen at enqueue time; deserialized by DownloadJobPayloadCodec on pickup. */
    @Type(JsonBinaryType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode requestPayload;

    @Column(nullable = false) private String locale;

    @Enumerated(EnumType.STRING) @Column(nullable = false) private DownloadJobStatus status;
    @Column(nullable = false) private short progress;
    @Column(nullable = false) private boolean cancelRequested;

    private Long attachmentId;
    private String fileName;
    private String format;
    private String mediaType;
    private Long sizeBytes;
    private String sha256;
    private String storageKey;

    private String errorCode;
    @Column(columnDefinition = "text") private String errorMessage;

    @Column(nullable = false, updatable = false) private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant expiresAt;
    private Instant deletedAt;

    private String sourceIp;
    @Column(columnDefinition = "text") private String userAgent;

    @Version private long version;
}
```

> `JsonBinaryType` comes from `hypersistence-utils` (already on the classpath if other JSONB columns exist; otherwise add the dependency in `pom.xml`).

### 4.3 Polymorphic payload

```java
public sealed interface DownloadJobPayload
        permits ReportExportPayload, CrisisSnapshotXlsxPayload, AdminReportZipPayload {

    DownloadJobSourceType sourceType();

    /** Used to derive the eventual filename and the source_business_no list-row column. */
    String sourceBusinessNoHint();
}
```

`ReportExportPayload` is a thin record wrapping the existing `ReportExportRequest` (the worker re-hydrates the full request at pickup time, then calls `ReportExportApplicationService.export()` — **no logic duplication**).

`DownloadJobPayloadCodec` is a single Jackson `ObjectMapper` configured with:
- `JavaTimeModule`
- A custom `JsonSubTypes` registry keyed off `DownloadJobSourceType`
- A `@JsonTypeInfo(use=Id.NAME, include=As.EXISTING_PROPERTY, property="sourceType")` annotation on the sealed interface

Round-trip test required: `assertEquals(payload, codec.read(codec.write(payload), DownloadJobPayload.class))` for each impl.

---

## 5. State Machine

### 5.1 Allowed transitions (re-stated)

```
QUEUED   ─┬─► RUNNING       (worker pickup)
          └─► CANCELLED     (user cancel before pickup)

RUNNING  ─┬─► READY         (success path)
          ├─► FAILED        (any throw inside worker)
          └─► CANCELLED     (cancel_requested observed at boundary)

READY    ─── EXPIRED        (expiry scheduler)

(All other transitions: 409 Conflict, msgkey:download.error.illegal_transition)
```

### 5.2 Persistence rules per state

| Target state | Setter | What gets written | tx-bean method | Propagation |
|---|---|---|---|---|
| `RUNNING` | `markRunning(jobId)` | `status=RUNNING`, `started_at=now`, `progress=5` | `DownloadJobTxOperations.markRunning` | `REQUIRES_NEW` |
| `READY` | `markReady(...)` | `status=READY`, `progress=100`, `finished_at=now`, `attachment_id`, `file_name`, `format`, `media_type`, `size_bytes`, `sha256`, `storage_key`, `expires_at=now+TTL`. Same tx also patches `attachment.storage_key`. | `DownloadJobTxOperations.markReady` | `REQUIRES_NEW` |
| `FAILED` | `markFailed(jobId, code, msg)` | `status=FAILED`, `progress=0`, `finished_at=now`, `error_code`, `error_message` | `DownloadJobTxOperations.markFailed` | `REQUIRES_NEW` |
| `CANCELLED` | `markCancelled(jobId)` | `status=CANCELLED`, `finished_at=now` | `DownloadJobTxOperations.markCancelled` | `REQUIRES_NEW` |
| `EXPIRED` | `markExpired(jobId)` | `status=EXPIRED`, `storage_key=null`. Also patches `attachment.storage_key=null`. | `DownloadJobTxOperations.markExpired` | `REQUIRES_NEW` |

### 5.3 The cancel race

```
T0  user clicks Cancel on a RUNNING job
T0  controller → DownloadJobApplicationService.cancel(jobId)
T0  UPDATE download_job SET cancel_requested=true WHERE job_id=? AND status='RUNNING'
T0  return 200

T0+x  worker is mid-call inside ReportExportApplicationService.export()
       (no way to interrupt — D-Q3)

T0+y  worker call returns
T0+y  worker reads the row again: SELECT cancel_requested FROM download_job WHERE job_id=?
T0+y  if cancel_requested=true:
        - SKIP S3 upload
        - markCancelled(jobId)
        - return — **no DownloadJobReadyEvent emitted**
        - **The audit-publish that the underlying pipeline already did** (ReportExportedEvent) is still in audit_log.
          This is the only minor inconsistency: cancelled jobs that ran briefly do produce an audit row.
          We accept this — the audit log is the truth-of-record for "an export was attempted by user X."
```

> If the user wants zero audit row for cancelled exports, the only compliant path is to deny cancellation once the worker has invoked the underlying service. That is more confusing UX. D-Q3 documents the chosen tradeoff.

---

## 6. Transaction & Async Boundaries

### 6.0 The submission ordering rule (AFTER_COMMIT)

A subtle bug class kills naive Spring async + JPA designs:

```
@Transactional
public EnqueueResult enqueue(...) {
    DownloadJob row = repo.save(...);          // INSERT, not yet committed
    events.publishEvent(new EnqueuedEvent(row.getJobId()));
    return new EnqueueResult(row.getJobId());
}                                              // ← tx commits here
```

If `EnqueuedEvent` is handled by a synchronous `@EventListener` that submits `worker.process(jobId)` to the executor, the worker thread can race ahead of the commit. `repo.findById(jobId)` then returns empty → spurious "job missing" failure.

**Mandatory fix**: the dispatcher's listener MUST use `@TransactionalEventListener(phase=AFTER_COMMIT)`. Submission is then guaranteed to see the row. If the tx rolls back, `AFTER_COMMIT` doesn't fire — also correct.

```java
@Component
@RequiredArgsConstructor
public class DownloadJobDispatcher {

    @Qualifier("dlcSmallExecutor")  private final ThreadPoolTaskExecutor smallPool;
    @Qualifier("dlcLargeExecutor")  private final ThreadPoolTaskExecutor largePool;
    private final DownloadJobWorker worker;
    private final DownloadJobRepository repo;
    @Value("${safar.export.download-center.size-class-threshold:10485760}")  // 10 MB
    private long sizeClassThreshold;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEnqueued(DownloadJobEnqueuedEvent ev) {
        ThreadPoolTaskExecutor pool = ev.estimatedBytes() > sizeClassThreshold ? largePool : smallPool;
        try {
            pool.execute(() -> worker.process(ev.jobId()));
        } catch (RejectedExecutionException rex) {
            log.warn("dlj.dispatch_rejected jobId={} pool={}", ev.jobId(), pool.getThreadNamePrefix());
            // Row stays QUEUED; reaper / startup-drain re-picks on a healthy pod.
        }
    }

    /** Recover orphan QUEUED rows after pod restart or earlier rejected dispatch. */
    @EventListener(ApplicationReadyEvent.class)
    public void drainOnStartup() {
        Optional<UUID> claimed;
        while ((claimed = repo.claimNextQueued()).isPresent()) {
            largePool.execute(() -> worker.process(claimed.get()));
        }
    }
}
```

### 6.0.1 Context propagation across the @Async boundary

The pool thread inherits **none** of the request thread's contexts. We propagate **by value** (columns on the row), not by reference:

| Context | Source on worker thread | How it reaches the worker |
|---|---|---|
| `locale` | `job.getLocale()` (column) | Worker calls `LocaleContextHolder.setLocale(parseLocale(job.getLocale()))` at top of `process()`; clears in `finally`. |
| `operatorId` / `username` | columns | Read from row. |
| `sourceIp` / `userAgent` | columns | Frozen at enqueue. The audit row records *who triggered it*, not *which pod ran it*. |
| `MDC.jobId` | n/a | Worker sets `MDC.put("jobId", jobId.toString())` at top; clears in `finally`. |
| `SecurityContextHolder` | **not propagated** | The underlying `ReportExportApplicationService` takes a fully-resolved `ReportExportRequest` and does not read `SecurityContextHolder` (verified — see §16). If a future caller introduces such a dependency, wrap the submitted task in `DelegatingSecurityContextRunnable`. |

### 6.1 Bean separation — why each one exists

The Spring proxy doesn't honor `@Async` or `@Transactional` on **self-invoked** methods. To get correct semantics for "worker shell + 4 narrow tx writes + reuse of an existing `@Transactional` service + clean separation of disk I/O", we split:

| Bean | Annotation / role | Tx behavior |
|---|---|---|
| `DownloadJobWorker` | `@Component` — non-transactional shell, runs on the executor thread | No `@Transactional`. Calls every other bean externally so proxies engage. |
| `DownloadJobTxOperations` | `@Service` — narrow CAS-style state writes only | Each method `@Transactional(propagation=REQUIRES_NEW)`. Pure status writes, no business logic, no external I/O. CAS via `UPDATE ... WHERE status='X' RETURNING` — see §20.1.2. |
| `DownloadJobTempFileService` | `@Component` — disk I/O isolated, no JPA, no tx | No `@Transactional`. Wraps temp-file lifecycle (§20.4). |
| `DownloadJobDispatcher` | `@Component` — transactional event listener | Submission via `@TransactionalEventListener(phase=AFTER_COMMIT)` — see §6.0. |
| `ReportExportApplicationService` | `@Service` (existing) — unchanged | `export()` declares `@Transactional(REQUIRED)`. Called from worker's non-tx context, opens a fresh tx. |

### 6.2 The worker code skeleton (temp-file spool path — D-Mem)

The worker **never holds the artifact `byte[]` simultaneously with the S3 upload**. The artifact reference is dropped immediately after being spooled to disk, so heap pressure is bounded by the underlying generation step alone. See §20.4 for the full memory model.

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class DownloadJobWorker {

    private final DownloadJobRepository repo;
    private final DownloadJobTxOperations txOps;
    private final DownloadJobPayloadCodec codec;
    private final ReportExportApplicationService reportExportApp;
    private final CrisisSnapshotExportPort crisisSnapshotPort;
    private final AdminReportExportPort adminReportPort;
    private final AttachmentStorage storage;
    private final ApplicationEventPublisher events;
    private final DownloadJobTempFileService tempFiles;            // §20.4
    @Value("${safar.export.download-center.ttl:P7D}")
    private Duration ttl;

    public void process(UUID jobId) {
        Path temp = null;
        try {
            // 1. CAS: QUEUED → RUNNING (atomic, throws StateTransitionLostException if not QUEUED)
            txOps.markRunning(jobId);

            DownloadJob job = repo.findById(jobId).orElseThrow();
            // Per §6.0.1 — propagate locale + MDC by value
            LocaleContextHolder.setLocale(parseLocale(job.getLocale()));
            MDC.put("jobId", jobId.toString());
            MDC.put("operatorId", String.valueOf(job.getOperatorId()));

            DownloadJobPayload payload = codec.read(job.getRequestPayload(), DownloadJobPayload.class);

            // 2. Run underlying pipeline. Returns metadata + bytes.
            DownloadJobArtifact artifact = switch (payload.sourceType()) {
                case REPORT_INCIDENT, REPORT_CASE -> {
                    var rp = (ReportExportPayload) payload;
                    var result = reportExportApp.export(rp.toReportRequest());
                    yield DownloadJobArtifact.fromReportExport(result);
                }
                case CRISIS_SNAPSHOT_XLSX -> crisisSnapshotPort.export((CrisisSnapshotXlsxPayload) payload);
                case ADMIN_REPORT_ZIP    -> adminReportPort.exportZip((AdminReportZipPayload) payload);
            };

            // 3. Spool to disk and IMMEDIATELY release the byte[] reference.
            //    Without this, peak heap = pool_max × max_artifact_bytes.
            temp = tempFiles.spool(jobId, artifact.bytes());          // creates 0700 file under temp-dir
            String fileName     = artifact.fileName();
            String mediaType    = artifact.mediaType();
            String format       = artifact.format();
            long   sizeBytes    = artifact.size();
            String sha256       = artifact.sha256();
            Long   attachmentId = artifact.attachmentId();
            artifact = null;                                          // hint to GC; the byte[] is now unreachable

            // 4. Cooperative cancel re-check (D-Q3) — observed at the boundary.
            if (repo.isCancelRequested(jobId)) {
                txOps.markCancelled(jobId);
                return;                                               // finally{} deletes temp; no S3 upload happens
            }

            // 5. Stream temp file → S3 via TransferManager (multipart auto for files > 16 MB).
            String storageKey = tempFiles.uploadAndDelete(temp, jobId, fileName, mediaType, sizeBytes);
            temp = null;                                              // ownership transferred; uploadAndDelete handles cleanup

            // 6. CAS: RUNNING → READY. Throws StateTransitionLostException if reaper raced us.
            txOps.markReady(jobId, storageKey, attachmentId, fileName, format, mediaType,
                            sizeBytes, sha256, Instant.now().plus(ttl));
            events.publishEvent(new DownloadJobReadyEvent(jobId, job.getOperatorId(), fileName, job.getLocale()));

        } catch (StateTransitionLostException ghost) {
            // Reaper already flipped us to FAILED, OR another worker claimed the row.
            // Don't override the truth-of-record. Just clean up our side. (§20.1.4)
            log.warn("dlj.ghost_completion jobId={} expectedFrom={} actual={}", jobId, ghost.expectedFrom(), ghost.actualState());
            cleanupGhost(jobId, temp, /*storageKey=*/ null);
        } catch (Exception e) {
            String code = ErrorCodeMapper.fromThrowable(e);
            try { txOps.markFailed(jobId, code, e.getMessage()); } catch (Exception ignored) {}
            events.publishEvent(new DownloadJobFailedEvent(jobId, /*operatorId fetched lazily*/ null, code, /*locale*/ null));
            log.error("dlj.failed jobId={} code={}", jobId, code, e);
        } finally {
            if (temp != null) tempFiles.deleteQuietly(temp);
            MDC.clear();
            LocaleContextHolder.resetLocaleContext();
        }
    }
}
```

**Why no `try-with-resources` on the temp file**: ownership of the temp `Path` is transferred to `tempFiles.uploadAndDelete()` on the success path; we set `temp = null` so the `finally` block is a no-op. On every error path the `finally` block runs `deleteQuietly`. This matches the existing `AttachmentStorageCleanupService` pattern (fire-and-forget, never throw).

The four CAS state writes (`markRunning`, `markReady`, `markFailed`, `markCancelled`) all use guarded `UPDATE ... WHERE status='X'` — see §5.2 and §20.1.2.

### 6.3 Tx-bean

```java
@Service
@RequiredArgsConstructor
public class DownloadJobTxOperations {

    private final DownloadJobRepository repo;
    private final AttachmentRepository attachmentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRunning(UUID jobId) {
        int n = repo.transitionStatus(jobId, DownloadJobStatus.QUEUED, DownloadJobStatus.RUNNING, Instant.now());
        if (n != 1) throw new IllegalStateTransitionException(jobId, "QUEUED→RUNNING");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markReady(UUID jobId, String storageKey, Long attachmentId, String filename,
                          String format, String mediaType, long sizeBytes, String sha256,
                          Instant expiresAt) {
        int n = repo.markReady(jobId, storageKey, attachmentId, filename, format, mediaType,
                                sizeBytes, sha256, expiresAt, Instant.now());
        if (n != 1) throw new IllegalStateTransitionException(jobId, "RUNNING→READY");

        // C2: patch attachment.storage_key (was null in legacy contract)
        if (attachmentId != null) {
            attachmentRepository.findById(attachmentId).ifPresent(att -> {
                att.setStorageKey(storageKey);
                attachmentRepository.save(att);
            });
        }
    }

    // markFailed, markCancelled, markExpired — same pattern
}
```

### 6.4 Source-type dispatch (B4/B5 ports)

To wrap B4/B5 without ripping their controllers, define narrow Ports:

```java
public interface CrisisSnapshotExportPort {
    DownloadJobArtifact export(CrisisSnapshotXlsxPayload payload);
}

public interface AdminReportExportPort {
    DownloadJobArtifact exportZip(AdminReportZipPayload payload);
}
```

Each Port impl is **a thin extraction of the existing handler body** in the corresponding controller, exposing it as a service callable from the worker. The original `@GetMapping` continues to work; in sync mode it calls the same Port.

---

## 7. Worker Pickup & Distribution

### 7.1 Submission paths

There are **two ways** a job ends up on the executor:

| Trigger | Mechanism |
|---|---|
| Just-enqueued | `DownloadJobApplicationService.enqueue()` publishes `DownloadJobEnqueuedEvent`; `DownloadJobDispatcher` listens and immediately submits a `Runnable` to `downloadCenterExecutor`. |
| On startup, after a pod crash | `DownloadJobDispatcher.@PostConstruct` (delayed via `ApplicationReadyEvent`) calls `repo.claimNextQueued(...)` in a loop until none returned, submitting each to the executor. |

### 7.2 The native claim query (PostgreSQL)

```java
public interface DownloadJobRepository extends JpaRepository<DownloadJob, UUID> {

    /**
     * Atomically claim a single QUEUED job by flipping it to RUNNING.
     * SKIP LOCKED prevents thundering-herd contention across multiple
     * worker pods. Returns Optional.empty() when the queue is drained.
     */
    @Query(value = """
        UPDATE download_job
           SET status     = 'RUNNING',
               started_at = now(),
               version    = version + 1
         WHERE job_id = (
                SELECT job_id FROM download_job
                 WHERE status = 'QUEUED'
                 ORDER BY created_at
                 FOR UPDATE SKIP LOCKED
                 LIMIT 1
              )
         RETURNING job_id
        """,
        nativeQuery = true)
    Optional<UUID> claimNextQueued();
}
```

> The dispatcher's recovery loop calls this after `ApplicationReadyEvent`. The just-enqueued fast path doesn't need the SKIP-LOCKED query — the application service has the row and can submit directly. (We keep the claim query for crash recovery, scale-out, and the periodic re-drain in `DownloadJobReaperScheduler`.)

### 7.3 Executor config

```java
@Configuration
public class DownloadCenterAsyncConfig {

    @Bean("downloadCenterExecutor")
    public ThreadPoolTaskExecutor downloadCenterExecutor(
            @Value("${safar.export.download-center.executor.core-size:4}")  int core,
            @Value("${safar.export.download-center.executor.max-size:16}")  int max,
            @Value("${safar.export.download-center.executor.queue:256}")    int queue) {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(core);
        ex.setMaxPoolSize(max);
        ex.setQueueCapacity(queue);
        ex.setThreadNamePrefix("dlc-worker-");
        ex.setRejectedExecutionHandler(new CallerRunsPolicy()); // back-pressure: dispatcher blocks; admin user gets enqueue latency, not 500
        ex.initialize();
        return ex;
    }
}
```

### 7.4 Per-operator and global caps

Caps are **enforced at enqueue time** in `DownloadJobApplicationService.enqueue()`:

```java
long runningForOperator = repo.countByOperatorIdAndStatusIn(operatorId, EnumSet.of(QUEUED, RUNNING));
if (runningForOperator >= operatorLimit)
    throw new TooManyDownloadsException("download.error.operator_limit");

long runningGlobal = repo.countByStatusIn(EnumSet.of(QUEUED, RUNNING));
if (runningGlobal >= globalLimit)
    throw new TooManyDownloadsException("download.error.global_limit");
```

Both checks happen inside the same transaction as the INSERT, so racing enqueues are bounded by the row count visible to that tx.

---

## 8. SSE Stream

The SSE endpoint is a **resource-sensitive surface**. A naive implementation can chain-block the publisher thread on a single slow client, leak emitters under reconnect storms, or get silently buffered by upstream proxies. This section specifies the hardened design (D-SSE).

### 8.0 Two operating modes — Virtual Threads vs Platform Threads

The project sets `spring.threads.virtual.enabled=true` by default (JDK 21). With virtual threads (VT), each blocking `emitter.send(...)` call parks a virtual thread cheaply (~few KB) instead of pinning a platform thread — which fundamentally changes the SSE memory math. The design must work in **both** modes because `application.yml` reserves an explicit kill-switch (`SPRING_VT_ENABLED=false`) for "any sign of carrier-pinning / native-pinning / library incompat in production."

| Concern | VT mode (default) | Platform-thread mode (fallback) |
|---|---|---|
| `emitter.send` blocking on slow client | Cheap — VT parks itself, doesn't consume a kernel thread | Expensive — pins a Tomcat worker thread; one slow client can chain-block the executor |
| Required mitigation | None — synchronous `send()` is fine | Dedicated `dlcSseFanoutExecutor` to absorb async sends |
| Per-emitter "send budget" | Implicit (VT scheduler) | Explicit 2 s send timeout |
| Heartbeat scheduler | Synchronous send across all emitters | Submits each send to the fanout executor |
| 1000 concurrent emitters | Trivial (~12 MB total) | Trivial (~12 MB total) — capacity is the same; the difference is **latency under load** |

**The §8.2–§8.9 design below is written for the platform-thread fallback** because that is the strictly-stronger path: if it works there, it definitely works under VT (VT only relaxes constraints, never tightens them). When VT is on, the `dlcSseFanoutExecutor` is still allocated but mostly idle — sends complete on their VTs without ever entering the executor's queue.

#### 8.0.1 The simplified VT path (the actual hot path in production)

When `Thread.currentThread().isVirtual()` is true at runtime (i.e. the Spring servlet thread that delivered the SSE-event listener call is a VT), the fanout service can **skip the executor entirely**:

```java
@EventListener
public void onDownloadUpdate(DownloadJobUpdatedInternalEvent ev) {
    String json = serialize(ev);
    for (var entry : registry.snapshot(ev.operatorId())) {
        if (Thread.currentThread().isVirtual()) {
            sendOrEvict(entry, "download.updated", json);     // direct sync — VT scheduler handles it
        } else {
            fanout.execute(() -> sendOrEvict(entry, "download.updated", json));
        }
    }
}
```

This costs nothing extra in the platform-thread case (still goes through the executor) and saves a thread hop in the VT case (the common case in production).

#### 8.0.2 Carrier-pinning awareness

Two situations would force the operator to flip `SPRING_VT_ENABLED=false`:

| Symptom | Likely cause | Diagnostic |
|---|---|---|
| Worker throughput drops dramatically under load; thread dumps show many threads stuck inside `S3Client.putObject` | AWS SDK v2 sync client uses `synchronized` blocks → carrier-pinning under VT | `jcmd <pid> Thread.print \| grep -A 2 carrier` |
| SSE delivery latency jumps to seconds | Same root cause but observed via `download_center_sse_send_dropped_total` increase | Same diagnostic |

If either symptom appears, the immediate action is `SPRING_VT_ENABLED=false` + restart. The design **continues to work correctly** (just with the extra thread hop) — no re-architecture needed.

> **Verification before Phase 2 ships**: run the §14.4 load test with VT both on and off; both must pass `p99 enqueue ≤ 150 ms / p99 time-to-READY ≤ 90 s`. If VT-on is faster, ship with VT on. If VT-on is slower (carrier-pinning), ship with VT off and file a JDK / SDK issue.

### 8.1 Endpoint contract

```java
@RestController
@RequestMapping("/api/v1/admin/downloads")
@RequiredArgsConstructor
public class DownloadCenterStreamController {

    private final DownloadJobApplicationService appService;

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission(Permissions.DOWNLOAD_CENTER_VIEW)
    public ResponseEntity<SseEmitter> stream(@AuthenticationPrincipal AdminPrincipal principal) {
        SseEmitter emitter = appService.openStream(principal.getUserId());      // §8.2
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header(HttpHeaders.CONNECTION, "keep-alive")
                .header("X-Accel-Buffering", "no")                              // §8.6
                .body(emitter);
    }
}
```

The controller is **not** `@Transactional`. Holding a JPA tx for the duration of an SSE stream would pin a connection from the Hikari pool for an hour.

### 8.2 LocalEmitterRegistry — lifecycle & memory bound

```java
@Component
@Slf4j
public class LocalEmitterRegistry {

    /** operatorId → list of live emitters on THIS pod. */
    private final ConcurrentMap<Long, CopyOnWriteArrayList<Entry>> byOperator = new ConcurrentHashMap<>();

    @Value("${safar.export.download-center.sse.max-emitters-per-operator:3}")
    private int maxPerOperator;
    @Value("${safar.export.download-center.sse.emitter-timeout:PT60M}")
    private Duration emitterTimeout;

    public SseEmitter open(long operatorId) {
        var list = byOperator.computeIfAbsent(operatorId, k -> new CopyOnWriteArrayList<>());

        // Enforce per-operator cap — newest wins, oldest evicted.
        while (list.size() >= maxPerOperator) {
            Entry oldest = list.get(0);
            list.remove(oldest);
            try { oldest.emitter.complete(); } catch (Exception ignored) {}
        }

        SseEmitter em = new SseEmitter(emitterTimeout.toMillis());
        Entry entry = new Entry(em, Instant.now());
        list.add(entry);

        // Single closure, idempotent removal — covers all three terminal callbacks.
        Runnable remove = () -> {
            list.remove(entry);
            byOperator.computeIfPresent(operatorId, (k, v) -> v.isEmpty() ? null : v);
        };
        em.onCompletion(remove);
        em.onTimeout(remove);
        em.onError(t -> remove.run());

        return em;
    }

    public List<Entry> snapshot(long operatorId) {
        var list = byOperator.get(operatorId);
        return list == null ? List.of() : List.copyOf(list);
    }

    /** Used by the heartbeat task — no per-operator filter. */
    public Stream<Map.Entry<Long, CopyOnWriteArrayList<Entry>>> all() {
        return byOperator.entrySet().stream();
    }

    public record Entry(SseEmitter emitter, Instant openedAt) {}
}
```

**Memory math**: each `SseEmitter` + Tomcat `AsyncContext` ≈ 2–4 KB. 1000 concurrent operators × 3 emitters = ~12 MB. Bounded.

### 8.3 Send path — never block the publisher

Synchronous `emitter.send(payload)` blocks if the client/proxy isn't reading. **Never** call it from a Spring `@EventListener` thread or from the worker thread.

```java
@Component
@RequiredArgsConstructor
public class SseFanoutService {

    private final LocalEmitterRegistry registry;
    @Qualifier("dlcSseFanoutExecutor")
    private final ThreadPoolTaskExecutor fanout;
    private final ObjectMapper mapper;

    @EventListener
    public void onDownloadUpdate(DownloadJobUpdatedInternalEvent ev) {
        String json;
        try {
            json = mapper.writeValueAsString(ev.toPayload());
        } catch (JsonProcessingException e) {
            log.warn("dlj.sse.serialize_failed jobId={}", ev.jobId(), e);
            return;
        }
        for (var entry : registry.snapshot(ev.operatorId())) {
            fanout.execute(() -> sendOrEvict(entry, "download.updated", json));
        }
    }

    private void sendOrEvict(LocalEmitterRegistry.Entry e, String name, String data) {
        try {
            e.emitter().send(SseEmitter.event().name(name).data(data));
        } catch (IOException ioe) {
            // Client gone or proxy closed connection. Treat as a graceful close.
            try { e.emitter().complete(); } catch (Exception ignored) {}
            // The onCompletion callback registered in registry.open() removes the entry.
            Counter.builder("download_center_sse_send_dropped_total").register(meterRegistry).increment();
        } catch (Exception other) {
            try { e.emitter().completeWithError(other); } catch (Exception ignored) {}
        }
    }
}
```

The `dlcSseFanoutExecutor` is sized `core=2 / max=8 / queue=1024`. Beyond 1024 queued sends, oldest tasks are dropped via `DiscardOldestPolicy` — polling fallback recovers the state.

### 8.4 Heartbeat

```java
@Component
@RequiredArgsConstructor
public class SseHeartbeatScheduler {

    private final LocalEmitterRegistry registry;
    @Qualifier("dlcSseFanoutExecutor")
    private final ThreadPoolTaskExecutor fanout;

    @Scheduled(fixedRateString = "${safar.export.download-center.sse.heartbeat-interval-ms:25000}")
    public void heartbeat() {
        registry.all().forEach(e -> e.getValue().forEach(entry ->
            fanout.execute(() -> {
                try {
                    entry.emitter().send(SseEmitter.event().comment("keepalive"));
                } catch (Exception ex) {
                    try { entry.emitter().complete(); } catch (Exception ignored) {}
                }
            })));
    }
}
```

25 s heartbeat sits well under nginx default `proxy_read_timeout=60s` and most corporate LB idle timeouts (typically 60–120 s).

### 8.5 Auth lifecycle

- `JwtAuthFilter` validates the JWT on the initial `GET /downloads/stream`. Auth is checked once per connection.
- If the JWT expires mid-stream, the next `emitter.send()` succeeds at the protocol layer (the connection is still open) — but the *browser* will have refreshed its token by then; on the next reconnect it presents the new token. No mid-stream re-auth is needed.
- Hard cap: `emitter-timeout=60m` forces a reconnect every hour, which is also a re-auth.

### 8.6 Required HTTP headers (proxy survival)

| Header | Reason |
|---|---|
| `Content-Type: text/event-stream` | SSE protocol |
| `Cache-Control: no-cache, no-transform` | prevents intermediate caching and content rewriting |
| `Connection: keep-alive` | keeps TCP connection open |
| `X-Accel-Buffering: no` | **critical** — disables nginx response buffering. Without this, nginx buffers the entire stream and SSE never delivers. |

Confirm corporate proxy + ingress: many enterprise gateways strip SSE entirely. The polling fallback (§8.7) is the contract for those environments.

### 8.7 Polling fallback — when SSE doesn't work

The dashboard FE uses this fallback when:
1. Browser doesn't support `EventSource` (unlikely in 2026 — IE only).
2. SSE connection fails to establish 3 times in a row.
3. SSE connection is established but no `download.updated` event arrives within 30 s of an active job (likely proxy buffering or multi-pod gap).

Fallback contract:

```
GET /api/v1/admin/downloads?since=<iso-cursor>&onlyActive=true
```

- Returns rows updated after `since` (default cursor = "5 min ago" on first call).
- FE polls every 3 s while the dashboard tab is foregrounded; pauses to 60 s when backgrounded (Page Visibility API).
- Same ownership rules as the list endpoint — no SSE-specific authorization shortcut.

### 8.8 Multi-pod limitation (Phase 1) and Phase 2 path

**Phase 1 limitation, made explicit**: emitters live per-pod. If client A is connected to pod P1 but its job runs on pod P2, the `DownloadJobReadyEvent` fires only on P2 → never reaches A's emitter. This is detected by the FE's 30-s silence rule (§8.7) which switches to polling.

**Phase 2 fix** — Postgres `LISTEN/NOTIFY`:

1. Inside the worker's `markReady` / `markFailed` tx, append `NOTIFY dlj_updates, '{"jobId":"...","operatorId":42,"status":"READY"}'`.
2. Each pod runs a dedicated `LISTEN dlj_updates` on a long-lived JDBC connection (one connection out of the Hikari pool, owned for the pod's lifetime).
3. On NOTIFY received, the pod's `LocalEmitterRegistry` fans out to its emitters for that operator.
4. Payload size limit: PG NOTIFY payload ≤ 8 kB → we send only keys; the FE GETs the row if it needs detail.

Phase 2 is non-blocking for Phase 1 because the polling fallback already provides cross-pod correctness.

### 8.9 SSE risk register (Phase 1)

| Failure mode | VT mode (default) | Platform-thread mode | Detection | Mitigation |
|---|---|---|---|---|
| Slow client blocks publisher | n/a — VT parks cheaply | real risk | `download_center_sse_send_dropped_total` counter | Async send via `dlcSseFanoutExecutor` (§8.3); 2 s send timeout per emitter |
| Emitter leak after disconnect | applies | applies | `download_center_sse_emitters_connected` gauge sustained high | Single idempotent removal closure on `onCompletion` / `onTimeout` / `onError` (§8.2) |
| Reconnect storm after laptop wake | applies | applies | spike in `download_center_sse_open_total` | Cap at 3 emitters/operator; oldest-wins eviction; polling fallback handles transient loss |
| Proxy buffering | applies | applies | `download_center_sse_silence_seconds` p95 high | `X-Accel-Buffering: no` header; FE 30 s silence → switch to polling |
| Thread starvation in fanout pool | rare — fanout mostly idle under VT | real risk | `dlcSseFanoutExecutor.activeCount` saturated | `DiscardOldestPolicy`; events lost are recovered by polling fallback |
| Token expiry mid-stream | applies | applies | next reconnect presents fresh token | 60-min hard timeout forces reconnect anyway |
| Multi-pod event miss | applies | applies | FE 30 s silence rule | Polling fallback (Phase 1); LISTEN/NOTIFY (Phase 2) |
| **Carrier-pinning under VT** | real risk | n/a | thread dump shows pinned carriers; `download_job_runtime_seconds` p99 spikes | Operator flips `SPRING_VT_ENABLED=false` → mode degrades to platform threads, design still correct (§8.0.2) |

---

## 9. Notifications

### 9.1 New Spring beans

```java
@Component
public class DownloadReadyStrategy implements NotificationStrategy {
    @Override public String eventKey() { return "download.ready"; }
    @Override public Audience audience(NotificationPayload p) {
        return Audience.byUserIds(List.of(p.userId()));   // single addressee = the operator
    }
    @Override public NotificationCard render(NotificationPayload p, Locale locale) {
        String title = msgs.getMessage("download.center.notif.ready.title", null, locale);
        // body is JSON so the FE can render the right action; keeps NotificationKind enum closed (D-Q1)
        String body = """
            {"category":"DOWNLOAD","subKind":"READY","jobId":"%s","fileName":"%s"}
            """.formatted(p.string("jobId"), p.string("fileName"));
        String link = "/download-center?focus=" + p.string("jobId");
        return new NotificationCard(title, body, link, NotificationKind.STATUS);
    }
}

@Component
public class DownloadFailedStrategy implements NotificationStrategy { /* mirrors above with subKind=FAILED */ }
```

### 9.2 Bridge listener

```java
@Component
@RequiredArgsConstructor
public class DownloadJobNotificationFanoutListener {

    private final NotificationPipeline pipeline;

    @EventListener
    public void onReady(DownloadJobReadyEvent ev) {
        pipeline.publish(new NotificationPayload(
                "download.ready",
                ev.jobId().toString(),
                Map.of("jobId", ev.jobId(), "fileName", ev.fileName(), "userId", ev.operatorId())
        ));
    }

    @EventListener
    public void onFailed(DownloadJobFailedEvent ev) { /* mirror */ }
}
```

### 9.3 Why `STATUS` and not a new kind

D-Q1 — preserves the closed enum and the FE color-tag scheme. The `body` payload carries `category=DOWNLOAD` for any FE that wants to switch icon or CTA; the FE can opt into that handling without a backend deploy.

---

## 10. Configuration Surface

```yaml
safar:
  export:
    download-center:
      enabled: true
      ttl: P7D                                  # ISO-8601 — see FR-8
      stuck-after: PT10M                        # reaper threshold
      job-timeout: PT5M                         # hard per-job timeout (§20.2.5)
      max-running-per-operator: 3
      max-running-global: 32
      max-job-bytes: 524288000                  # 500 MB hard ceiling at enqueue (§20.4.6)
      list-default-page-size: 20
      list-max-page-size: 100

      # ── Memory model (§20.4) ────────────────────────────────────────────
      temp-dir: /var/tmp/safar-dlc              # 0700, owned by service account
      temp-dir-startup-sweep-age: PT1H          # delete *.bin older than this on boot
      disk-min-free: 1073741824                 # 1 GB; below → enqueue rejected with 503
      size-class-threshold: 10485760            # 10 MB; routes to small vs large pool
      s3-multipart-threshold: 16777216          # 16 MB; below = single PUT, above = TransferManager multipart
      s3-multipart-part-size: 8388608           # 8 MB part buffers

      # ── Async pools ──────────────────────────────────────────────────────
      executor:
        small:
          core-size: 4
          max-size: 16
          queue: 256
        large:
          core-size: 1
          max-size: 4
          queue: 64
        sse-fanout:
          core-size: 2
          max-size: 8
          queue: 1024

      # ── SSE (§8) ────────────────────────────────────────────────────────
      sse:
        emitter-timeout: PT60M
        heartbeat-interval-ms: 25000
        max-emitters-per-operator: 3
        send-timeout-ms: 2000

      # ── Schedulers ──────────────────────────────────────────────────────
      cleanup:
        cron: "0 0 * * * *"                     # hourly expiry sweep
        reaper-cron: "0 */5 * * * *"            # every 5 minutes
        disk-watchdog-rate-ms: 30000            # every 30 seconds
```

`safar.export.download-center.enabled=false` short-circuits the new controller (returns 404) AND makes the legacy controllers behave as if no `X-Export-Mode` header existed. Used during Phase-0 dark-launch.

**JVM flag recommendations** (pod manifest):

```
-XX:MaxDirectMemorySize=512m       # bound S3 SDK direct buffers (§20.4.7)
-XX:MaxRAMPercentage=70            # leaves headroom for OS + temp-file cache
-XX:+ExitOnOutOfMemoryError        # let Kubernetes restart cleanly on OOM
```

**Kubernetes pod spec**:

```
terminationGracePeriodSeconds: 75   # 60 s graceful drain (§20.2.4) + 15 s buffer
```

---

## 11. Backward Compatibility (D-Q5 in detail)

### 11.1 The `ReportExportController` micro-edit

```java
@PostMapping("/incidents/{incidentId}/exports/report")
@RequirePermission(Permissions.REPORT_VIEW)
public ResponseEntity<?> exportFromIncident(@PathVariable Long incidentId,
                                            @RequestBody(required = false) JsonNode body,
                                            @AuthenticationPrincipal AdminPrincipal principal,
                                            Authentication authentication,
                                            HttpServletRequest http,
                                            @RequestHeader(value = "X-Export-Mode", required = false) String mode) {

    ReportExportRequest req = requestBuilder.fromHttp(
            SourceContext.incident(incidentId), body, principal, authentication, http);

    if ("sync".equalsIgnoreCase(mode) || !downloadCenterEnabled) {
        // Legacy 200 + bytes path — UNCHANGED contract
        return artifactResponse(applicationService.export(req));
    }

    // New default: enqueue
    EnqueueResult result = downloadJobAppService.enqueueReportExport(
            DownloadJobSourceType.REPORT_INCIDENT, req);
    return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .header(HttpHeaders.LOCATION, "/api/v1/admin/downloads/" + result.jobId())
            .body(Map.of("jobId", result.jobId().toString(), "status", "QUEUED"));
}
```

Removed `@Transactional` from the method — the sync branch's transaction is owned by `applicationService.export()` (already `@Transactional`), the async branch's INSERT is owned by `downloadJobAppService.enqueueReportExport()` (a single-tx method).

### 11.2 Deprecation timeline

| Window | Behavior |
|---|---|
| Phase 0 | `safar.export.download-center.enabled=false`. New controller returns 404. Legacy unchanged. |
| Phase 1 | Worker live but the controllers still default to sync. Internal cron exercises a synthetic enqueue every 6 h. |
| Phase 2 | `safar.export.download-center.enabled=true`. Default flips to async. `X-Export-Mode: sync` keeps legacy 200-bytes shape. Telemetry counter on header presence: `download_center_legacy_sync_calls_total`. |
| Phase 3 | Notifications + cleanup scheduler enabled. |
| Phase 4 | When `download_center_legacy_sync_calls_total` is zero for ≥ 14 days, remove the header check. |

---

## 12. API Contract — Concrete Shapes

### 12.1 Enqueue (legacy controller, async branch)

**Request**
```
POST /api/v1/admin/incidents/{id}/exports/report
Content-Type: application/json
{
  "format": "PDF",
  "reportIds": [101, 102],
  "annexIds": [],
  "annexKeys": ["caseSummary"],
  "lang": "ar"
}
```

**Response**
```
HTTP/1.1 202 Accepted
Location: /api/v1/admin/downloads/0190f2c6-3a7e-7a20-9de6-7bc1a83910a4
Content-Type: application/json

{
  "jobId": "0190f2c6-3a7e-7a20-9de6-7bc1a83910a4",
  "status": "QUEUED"
}
```

### 12.2 List (`GET /downloads`)

```
GET /api/v1/admin/downloads?status=READY,RUNNING&page=0&size=20&q=INC-2026
```

```json
{
  "page": 0, "size": 20, "total": 47,
  "items": [
    {
      "jobId": "0190f2c6-3a7e-7a20-9de6-7bc1a83910a4",
      "sourceType": "REPORT_CASE",
      "sourceBusinessNo": "CF-2026-00012",
      "sourceEntityId": 8421,
      "fileName": "CF-2026-00012-reports-1746115223000.zip",
      "format": "ZIP",
      "mediaType": "application/zip",
      "status": "READY",
      "progress": 100,
      "sizeBytes": 8423144,
      "sha256": "5e1b...c2",
      "errorCode": null,
      "createdAt": "2026-05-01T08:35:11Z",
      "expiresAt": "2026-05-08T08:35:11Z",
      "attachmentId": 99211,
      "_links": {
        "self":      "/api/v1/admin/downloads/0190f2c6-...",
        "file":      "/api/v1/admin/downloads/0190f2c6-.../file",
        "cancel":    null,
        "retry":     null,
        "softDelete":"/api/v1/admin/downloads/0190f2c6-..."
      }
    }
  ]
}
```

### 12.3 Stream (`GET /downloads/stream`)

```
HTTP/1.1 200 OK
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive

event: download.updated
data: {"jobId":"0190...","status":"RUNNING","progress":5}

event: download.updated
data: {"jobId":"0190...","status":"READY","progress":100,"sizeBytes":8423144,"sha256":"5e1b...c2"}

:keepalive
```

### 12.4 File (`GET /downloads/{jobId}/file`)

```
HTTP/1.1 200 OK
Content-Type: application/zip
Content-Length: 8423144
Content-Disposition: attachment; filename="CF-2026-00012-reports-1746115223000.zip"
X-Export-Ref: 99211
X-Export-Sha256: 5e1b...c2
X-Download-Job-Id: 0190f2c6-3a7e-7a20-9de6-7bc1a83910a4
```

Body streamed directly from `S3AttachmentStorage.getInputStream(storage_key)`. No buffering. Permission + ownership re-checked here (D-NFR-Security).

### 12.5 Errors

```json
HTTP/1.1 409 Conflict
{
  "code": "ILLEGAL_TRANSITION",
  "message": "msgkey:download.error.illegal_transition",
  "details": { "from": "READY", "to": "RUNNING" }
}
```

`MessageKeyLocalizer` resolves `message` against the request locale at the response boundary.

---

## 13. i18n Keys

### 13.1 New keys (full list, with EN / AR values)

These get added to **both** `messages.properties` and `messages_ar.properties`. The CI parity gate (§14.3) catches drift.

```
# messages.properties (EN side shown; AR has identical key set with translated values)

download.center.title                   = Download Center
download.center.empty                   = No downloads yet
download.center.column.file             = File
download.center.column.source           = Source
download.center.column.businessNo       = Reference
download.center.column.format           = Format
download.center.column.size             = Size
download.center.column.submittedAt      = Submitted
download.center.column.status           = Status
download.center.column.actions          = Actions

download.center.status.QUEUED           = Queued
download.center.status.RUNNING          = Generating
download.center.status.READY            = Ready
download.center.status.FAILED           = Failed
download.center.status.CANCELLED        = Cancelled
download.center.status.EXPIRED          = Expired

download.center.action.download         = Download
download.center.action.retry            = Retry
download.center.action.cancel           = Cancel
download.center.action.delete           = Delete

download.center.toast.enqueued          = Export started — track it in the Download Center
download.center.notif.ready.title       = Your download is ready
download.center.notif.failed.title      = Your download failed

download.error.not_found                = Download not found
download.error.forbidden                = You don't have access to this download
download.error.illegal_transition       = This action isn't allowed in the current state
download.error.operator_limit           = You already have the maximum number of downloads in flight
download.error.global_limit             = The system is busy — please try again in a moment
download.error.expired                  = This download has expired
download.error.cancelled                = This download was cancelled
download.error.upstream_render_failed   = The export couldn't be generated
download.error.storage_unavailable      = Couldn't save the export — please retry
download.error.worker_lost              = The export was interrupted — please retry
```

(AR translations finalized by translator; key set MUST be byte-identical to EN.)

### 13.2 RTL implementation

Server emits `msgkey:` payloads only. All RTL handling is FE-side per PRD §6.2:
- `<html dir="rtl" lang="ar">` driven by active locale
- Logical CSS properties only (`margin-inline-start`, etc.)
- Directional icons flipped via `transform: scaleX(-1)` in RTL
- Filenames wrapped in `<bdi>` to neutralize BiDi reordering

Backend ensures every error/notification surface uses `MessageKeyLocalizer` so the same payload renders correctly in both locales.

---

## 14. Test Plan

### 14.1 Unit tests (per bean)

| Bean | Test class | Key cases |
|---|---|---|
| `DownloadJobPayloadCodec` | `DownloadJobPayloadCodecTest` | round-trip each subtype; rejects unknown `sourceType`; unmarshals frozen-payload from a fixed Postgres JSONB string |
| `DownloadJobTxOperations` | `DownloadJobTxOperationsTest` | each transition writes the expected columns; illegal transition throws; markReady patches `attachment.storage_key` |
| `DownloadJobApplicationService` | `DownloadJobApplicationServiceTest` | per-operator cap; global cap; ownership rule; soft-delete sets `deleted_at`; retry creates new row with same payload |
| `DownloadJobWorker` | `DownloadJobWorkerTest` (mockito) | success path; cancel observed mid-flight; storage throw → markFailed; payload codec throw → markFailed; cancel-after-success still skips upload |
| `DownloadJobExpiryScheduler` | `DownloadJobExpirySchedulerTest` | only `READY` past TTL get touched; deleted/failed rows untouched; `attachment.storage_key` reset to null |
| `DownloadJobReaperScheduler` | `DownloadJobReaperSchedulerTest` | `RUNNING` rows past `stuck-after` flip to FAILED with `worker_lost` |

### 14.2 Integration tests (`@SpringBootTest`)

| Scenario | Assertion |
|---|---|
| Enqueue → wait READY → download | Bytes downloaded SHA-256 match `attachment.sha256_hash` (C6) |
| Enqueue → cancel before pickup | row in `CANCELLED`; no S3 object; no `ReportExportedEvent` |
| Enqueue → cancel after worker started | row in `CANCELLED`; no S3 object; **one** `ReportExportedEvent` allowed (D-Q3) |
| Two operators enqueue same case | two distinct `download_job` rows, two distinct S3 objects |
| User A tries to GET user B's job | 404 (we 404, not 403, to avoid existence leak) |
| Admin with `VIEW_ALL` lists across users | sees both rows |
| Sync header path | `X-Export-Mode: sync` returns 200 + bytes; no `download_job` row created |
| Sync path SHA-256 verify | byte-identical match against `PdfVerificationService` |
| Pod crash during RUNNING | reaper flips to FAILED within `stuck-after`; retryable |
| TTL expiry sweep | `READY` past TTL → `EXPIRED`; S3 object gone; `attachment.storage_key=null`; SHA-256 still on `attachment` |
| Concurrent enqueue from same operator above cap | 4th request gets 429 with `msgkey:download.error.operator_limit` |
| Multi-pod claim | `claimNextQueued` returns each row exactly once (PG `SKIP LOCKED` test on Testcontainers PostgreSQL) |

### 14.3 CI gates

| Gate | Failure mode |
|---|---|
| EN/AR key parity (`scripts/ci/check-i18n-parity.sh`) | Any key in `messages.properties` not in `messages_ar.properties` (and vice-versa) for the `download.*` namespace fails the build. |
| Permission-mirror gate (existing) | `Permissions.java` constants must match `safar-web/src/constants/permissions.ts` (out-of-repo CI hook). |
| JSONB round-trip gate | `DownloadJobPayloadCodec` round-trip test runs on every commit. |

### 14.4 Load tests (Phase 2 gate)

Per PRD §11.4: 100 concurrent users × 1 ZIP each. p99 enqueue ≤ 150 ms; p99 time-to-READY ≤ 90 s on staging. Run under `gatling/load/download-center.scala`.

---

## 15. Observability

### 15.1 Metrics (Micrometer)

| Name | Type | Tags | Purpose |
|---|---|---|---|
| **Lifecycle** | | | |
| `download_jobs_enqueued_total` | counter | `sourceType` | Volume |
| `download_jobs_ready_total` | counter | `sourceType`, `format` | Success rate |
| `download_jobs_failed_total` | counter | `sourceType`, `errorCode` | Failure breakdown |
| `download_jobs_cancelled_total` | counter | `sourceType` | Cancellation rate |
| `download_jobs_ghost_completed_total` | counter | `from` (`RUNNING`/`READY`) | Reaper-vs-worker race count (§20.1.4) — should stay near zero |
| `download_job_runtime_seconds` | histogram | `sourceType`, `format` | RUNNING → READY duration |
| `download_job_spool_seconds` | histogram | `sourceType` | bytes → temp file write time (§20.4) — surfaces slow disk |
| `download_job_upload_seconds` | histogram | `sourceType` | temp file → S3 upload time |
| `download_job_queue_depth` | gauge | — | QUEUED count |
| `download_job_running_global` | gauge | `pool` (`small`/`large`) | Per-pool concurrency |
| `download_job_in_flight_bytes` | gauge | — | sum of size_bytes for RUNNING rows — early-warning for memory pressure |
| **Memory + storage** | | | |
| `download_center_temp_dir_free_bytes` | gauge | — | Disk watchdog signal (§20.4.5) |
| `download_center_temp_files_count` | gauge | — | Live `*.bin` count under temp-dir |
| `download_center_disk_exhausted` | gauge (0/1) | — | 1 when watchdog has rejected enqueue |
| **SSE** | | | |
| `download_center_sse_emitters_connected` | gauge | — | live `SseEmitter` count across all operators |
| `download_center_sse_open_total` | counter | — | new emitter opens (reconnect-storm signal) |
| `download_center_sse_send_dropped_total` | counter | `reason` (`io`/`timeout`/`queue_full`) | failed sends; FE will fall back to polling |
| `download_center_sse_silence_seconds` | summary | — | time since last successful send per emitter |
| **Compatibility** | | | |
| `download_center_legacy_sync_calls_total` | counter | `entryPoint` (`incident`/`case`/`snapshot`/`adminreport`) | Drives Phase 4 decommission |

### 15.2 Logs

Each lifecycle transition emits a single INFO line via `DownloadJobTxOperations`:

```
INFO  ae.gov.safar.domain.download.service.DownloadJobTxOperations
  event=dlj.transition jobId={jobId} operator={operatorId} from={from} to={to} latencyMs={latencyMs} sourceType={sourceType}
```

Failures additionally log `errorCode` and `errorMessage` at WARN.

### 15.3 Alerts (Grafana / Prometheus)

| Alert | Trigger | Severity |
|---|---|---|
| `download_jobs_failed_total{errorCode="worker_lost"}` increase | rate > 0 over 10 min | page on-call |
| `download_jobs_ghost_completed_total` increase | rate > 0 over 10 min | page on-call (means `stuck-after` is racing real workers) |
| `download_center_disk_exhausted == 1` | any pod, > 30 s | page ops |
| `download_center_temp_dir_free_bytes` | < 2 GB sustained 5 min | ticket |
| `download_job_in_flight_bytes` | > 70 % of `MaxRAMPercentage × pod.heap` | page on-call (memory pressure precursor) |
| `download_job_queue_depth` sustained high | > 100 for 15 min | page ops |
| `download_center_sse_send_dropped_total` rate | > 10/s for 5 min | ticket (proxy or client misbehavior) |
| `download_center_sse_emitters_connected` | sustained growth without commensurate `sse_open_total` | ticket (emitter leak) |
| Storage delete failure rate | logs WARN at > 1/min | ticket |

---

## 16. Security

| Concern | Control |
|---|---|
| Authentication | Existing `JwtAuthFilter` — all endpoints behind it. |
| Authorization | `@RequirePermission(Permissions.DOWNLOAD_CENTER_VIEW)` on every endpoint. Ownership re-checked in `DownloadJobApplicationService` for every read/write of a specific job. |
| Cross-user leakage | Ownership check returns 404 (not 403) for "exists but not yours" — avoids existence leak. |
| Watermark & classification | Provided by the underlying `ReportExportServiceImpl` (UNCHANGED). Worker doesn't strip or alter. |
| Storage key not enumerable | Object key derives from `jobId` (UUID v7). No directory listing exposed. |
| Pre-signed URLs | NOT used for Phase 1 — the file endpoint streams via the controller so ownership is re-checked on every fetch. (Pre-signed URLs are available in `S3AttachmentStorage` but bypass the application-layer check; deferred.) |
| Audit | Underlying `ReportExportedEvent` continues to fire from inside the worker tx → `audit_log` row written by existing listener. No audit logic added or removed in this TDD. |
| Input validation | `EnqueueRequest` re-uses the existing `ReportExportRequestBuilder` validation pipeline — same `INVALID_LANG`, `UNKNOWN_PROPERTY`, `PAYLOAD_TOO_LARGE` guards apply. |

---

## 17. Migration & Rollout

| Phase | Steps | Rollback |
|---|---|---|
| 0 | Apply Flyway V<NEXT>__download_job.sql; deploy with `safar.export.download-center.enabled=false`; new controller returns 404 | Revert deploy; the migration is additive only — no rollback DDL needed |
| 1 | Enable executor + worker + dispatcher; legacy controllers untouched; synthetic cron exercises one enqueue every 6 h on staging for 48 h | `enabled=false` short-circuits the new path |
| 2 | Flip `enabled=true`; FE swap to async by default; `X-Export-Mode: sync` keeps legacy shape; ship Download Center page (EN + AR); pilot agency = NCEMA AR | Set `enabled=false` to revert; FE remains backwards-compatible (sync header) |
| 3 | Enable notification fan-out + expiry/reaper schedulers | Disable schedulers via `cleanup.cron=-` (Spring no-op), keep DB rows |
| 4 | After ≥ 14 days of zero `download_center_legacy_sync_calls_total`, remove the `X-Export-Mode` branch; controllers always async | Re-add the branch (one revert PR) |

> **The migration itself is additive — no existing row, column, index, or constraint is changed.** Phase 0 ships **with** the migration but **without** any new behavior, so the rollback path is "deploy the previous binary"; the table stays empty.

---

## 18. Open Issues for Engineering

These are items that need an engineer's call during implementation, not architectural decisions:

| # | Item | Default if not addressed |
|---|---|---|
| O1 | Choice of UUID generator for `jobId` (UUID v7 preferred for time-ordering) | If `java.util.UUID` v4 is used, the `idx_dlj_status_created` index still works; just less locality. |
| O2 | `hypersistence-utils` dependency presence — confirm it's already on the classpath; if not, add it for `JsonBinaryType` | Otherwise convert `JsonNode` ⇌ `String` manually using `ObjectMapper`. |
| O3 | (CLOSED — addressed in §6.0.1) ~~Locale propagation through async~~ | Worker explicitly sets `LocaleContextHolder.setLocale(parseLocale(job.getLocale()))` at top of `process()`; clears in finally. |
| O4 | B4/B5 Port extraction may touch shared state in `AdminCrisisSnapshotController` / `AdminReportController` (current handlers are written assuming a request thread) — confirm idempotency | If shared state surfaces, refactor to plain service classes and have controllers delegate. |
| O5 | (CLOSED — addressed by D-Mem / §20.4) ~~Bytes-in-heap concurrency × size OOM~~ | Temp-file spool + S3 TransferManager multipart + size-class pools + disk watchdog. |
| O6 | SSE behind corporate proxies — confirm reverse proxy disables buffering for `/downloads/stream` (`proxy_buffering off` for nginx) | Polling fallback handles the rest (§8.7). The TDD already mandates `X-Accel-Buffering: no` response header (§8.6). |
| O7 | OBS multipart upload — verify the `createMultipartUpload + uploadPart + completeMultipartUpload` flow against G42's `obs.ae-ad-1.g42cloud.com` endpoint with a real 200 MB+ file before Phase 2 ships. Existing `ObsAttachmentStorage` uses single-PUT only, so multipart is a new code path. For dev/test backends (`S3AttachmentStorage`), confirm `s3-transfer-manager` is on the classpath. | Phase-1 fallback: single-PUT for any file size; OOM risk re-emerges for files > 200 MB. The 500 MB hard cap (§20.4.6) limits blast radius. |
| O8 | Temp-dir filesystem — confirm `/var/tmp/safar-dlc` is on a fast local disk (not NFS, not the same volume as `/var/log`). Ideally a dedicated SSD volume. | Slow disk shows up as elevated `download_job_spool_seconds` p99; functionally still correct, just slower throughput. |
| O9 | Pod stability under SIGTERM — confirm `terminationGracePeriodSeconds` ≥ 75 s in the Kubernetes deployment manifest; `setWaitForTasksToCompleteOnShutdown=true` on both executors | Workers cut off mid-flight leave `RUNNING` rows; reaper recovers within `stuck-after` (10 min). |
| O10 | Idempotency-Key header — confirm FE library passes the same key on automatic retries (e.g. axios-retry) | Without FE cooperation, idempotency degrades to "best effort" — duplicate enqueues create duplicate rows but per-operator cap still bounds cost. |

---

## 19. Acceptance Mapping

PRD acceptance criterion (§11) → TDD section:

| PRD AC | TDD section |
|---|---|
| AC-1.1 enqueue returns 202 with parseable jobId | §6, §11, §12.1 |
| AC-1.2 byte-identical attachment ↔ download (SHA-256) | §6.2, §16, §14.2 |
| AC-1.3 closed tab does not halt job | §7 (executor), §14.2 |
| AC-1.4 cancel QUEUED ≤ instant; cancel RUNNING ≤ 2s after current call | §5.3, §14.2 |
| AC-1.5 retry produces new jobId, same input | §3.1 (apply service .retry), §14.1 |
| AC-1.6 AR locale fully mirrors | §13.2 (FE), §14.2 |
| AC-2 backward-compat (sync, audit, verify) | §11, §14.2 |
| AC-3 i18n parity CI gate | §14.3 |
| AC-4 perf load p99 enqueue ≤ 150 ms / p99 time-to-READY ≤ 90 s | §14.4 |
| AC-5 cross-user isolation | §16, §14.2 |

---

## 20. Critical Risk Hardening — Job, Async, SSE, Memory

This section consolidates the four high-impact risk surfaces. Every failure mode listed here is covered by a specific code or config detail elsewhere in the TDD; this section makes the tradeoffs explicit so a reviewer can verify nothing is hand-waved.

### 20.1 Job model — atomicity, idempotency, ghost completions

#### 20.1.1 FE retry idempotency
The FE may retry an enqueue POST (axios-retry, manual refresh, double-click). Without protection, each retry creates a new `download_job` row → duplicate worker runs → duplicate audit rows → duplicate S3 objects.

- **Contract**: client MAY send `Idempotency-Key: <uuid>` on enqueue. Server stores it in `idempotency_key`.
- **Storage**: partial unique index `uq_dlj_idempotency (operator_id, idempotency_key) WHERE idempotency_key IS NOT NULL AND deleted_at IS NULL` (§4.1).
- **Conflict resolution**: on `INSERT` violating the unique index, the application service catches `DataIntegrityViolationException`, looks up the existing row by `(operatorId, idempotencyKey)`, and returns `200 OK + X-Idempotent: replay` with the same body shape as 202.
- **Window**: holds until soft-delete. Recommended FE convention: scope the key to a UI submission session.

#### 20.1.2 Atomic state transitions (CAS, never SELECT-then-UPDATE)
Every transition uses a guarded UPDATE:

```sql
-- markRunning
UPDATE download_job SET status='RUNNING', started_at=now(), progress=5, version=version+1
 WHERE job_id=? AND status='QUEUED' RETURNING job_id;

-- markReady
UPDATE download_job SET status='READY', progress=100, finished_at=now(),
       attachment_id=?, file_name=?, format=?, media_type=?, size_bytes=?,
       sha256=?, storage_key=?, expires_at=?, version=version+1
 WHERE job_id=? AND status='RUNNING' RETURNING job_id;
```

If `RETURNING` produces zero rows, `DownloadJobTxOperations` throws `StateTransitionLostException(expectedFrom, jobId)`. The worker catches this distinctly from `Exception` (§6.2) — see "ghost completions" below.

This eliminates the lost-update class entirely. JPA `@Version` is still kept on the entity to protect read-modify-write paths in the application service (e.g. cancel, retry).

#### 20.1.3 Submit-before-commit hazard
Already covered in §6.0. Restated as a rule: **all worker submissions go through `@TransactionalEventListener(phase=AFTER_COMMIT)`**. This is non-negotiable.

#### 20.1.4 Ghost completions (reaper races worker)
- Scenario: long GC pause delays worker; reaper threshold (`stuck-after=10m`) elapses; reaper flips `RUNNING → FAILED`. 30 s later worker resumes and tries `markReady`. CAS fails because the state is `FAILED`.
- Worker policy:
  1. Catch `StateTransitionLostException`.
  2. Log `WARN dlj.ghost_completion`.
  3. Delete the local temp file.
  4. If the S3 upload happened (i.e. the exception came from `markReady` *after* `tempFiles.uploadAndDelete` returned), fire-and-forget `AttachmentStorageCleanupService.deleteObjectsAsync(List.of(storageKey))`.
  5. **Never** reverse the truth-of-record. Reaper-set `FAILED` stands. The user retries.
- Metric: `download_jobs_ghost_completed_total{from}`. Steady > 0 → reaper threshold too aggressive; consider raising `stuck-after`.

#### 20.1.5 Job timeout
Per-job hard timeout (`safar.export.download-center.job-timeout=PT5M`) wraps the worker invocation:

```java
Future<?> f = pool.submit(() -> worker.process(jobId));
try {
    f.get(jobTimeout.toMillis(), TimeUnit.MILLISECONDS);
} catch (TimeoutException te) {
    f.cancel(true);                                   // sends Thread.interrupt
    txOps.markFailed(jobId, "download.error.timeout", "exceeded " + jobTimeout);
}
```

The underlying pipeline is not interrupt-aware (it doesn't check `Thread.interrupted()`). The interrupt only surfaces if the thread blocks on something interruptible (most JDBC drivers, NIO, parking). Worst case: the thread keeps running until the underlying call returns naturally; the worker then tries `markReady` and gets `StateTransitionLostException` (§20.1.4). Same cleanup path applies. **No leak.**

#### 20.1.6 At-least-once notifications (Phase 2 path)
After `markReady` commits, the worker publishes `DownloadJobReadyEvent`. If the JVM dies between commit and publish, the notification is lost — the user sees `READY` in the dashboard but no toast/email.

Phase 1: accept this; the dashboard is the truth of record.

Phase 2 fix: add `notification_sent_at TIMESTAMPTZ` column. A small follow-up scheduler (every 60 s) queries `READY rows WHERE finished_at < now - 60s AND notification_sent_at IS NULL` and re-fires the notification. Idempotency on the notification side is provided by `notification.dedup_key = jobId`. (Out of Phase 1 scope; tracked here so the column can be added on day one to avoid a future migration.)

### 20.2 Async runtime — propagation, sizing, shutdown

#### 20.2.1 Two pools, by size class
Single uniform pool starves: one 200 MB ZIP holds a slot for 60 s while ten 2 MB PDFs queue behind it.

| Pool | Bean qualifier | Core / Max / Queue | Eligible jobs |
|---|---|---|---|
| Small | `dlcSmallExecutor` | 4 / 16 / 256 | `estimatedBytes ≤ size-class-threshold` (10 MB default) |
| Large | `dlcLargeExecutor` | 1 / 4 / 64 | `estimatedBytes > size-class-threshold` |

Estimation source: `validator.enforceByteLimit` already computes `estimatedBytes` per request. The dispatcher reads it from the frozen payload (or, for B4/B5, from a per-source-type heuristic) and routes accordingly.

Worst-case heap with size classes: `16×10 MB + 4×200 MB = 960 MB` peak (vs `16×200 MB = 3.2 GB` on a single pool).

#### 20.2.2 Rejection policy
Both pools use `AbortPolicy` (throws `RejectedExecutionException`). The dispatcher catches it, logs, and **leaves the row `QUEUED`**. The startup-drain loop (§6.0) and reaper (§20.1.4 inverse — "rows in QUEUED for too long") re-pick on a healthy pod.

We deliberately reject `CallerRunsPolicy`. Caller-runs would pin the AFTER_COMMIT thread (a Spring framework thread) for the full duration of a worker run, which is unsafe.

#### 20.2.3 Graceful shutdown
Both executors:
- `setWaitForTasksToCompleteOnShutdown(true)`
- `setAwaitTerminationSeconds(60)`

Spring's `@PreDestroy` triggers on SIGTERM. In-flight workers get up to 60 s to finish their current artifact + commit `markReady`/`markFailed`. Anything past 60 s is force-cancelled → row stays `RUNNING` → reaper recovers it on the new pod.

**Required Kubernetes config**: `terminationGracePeriodSeconds ≥ 75` (60 s drain + 15 s buffer for OS-level cleanup). Smaller values cause forcible kill mid-write → orphan S3 objects (acceptable, swept by lifecycle policy).

#### 20.2.4 Tx propagation gotchas (recap)
- Worker thread starts with **no** tx, **no** SecurityContext, **no** LocaleContextHolder, **no** MDC.
- Propagation is **by value** through DB columns. See §6.0.1 for the table.
- The worker calls `reportExportApp.export(req)` which has `@Transactional(REQUIRED)` — opens a fresh tx in the new thread. ✅
- The worker calls `txOps.markReady(...)` which has `@Transactional(REQUIRES_NEW)` — opens its own tx. ✅
- Both are external calls (different bean → through Spring proxy → annotation honored). Self-invocation pitfalls do not apply.

### 20.3 SSE — backpressure, fanout, lifecycle

(Detailed mechanics live in §8 — this is the consolidated risk view.)

| Risk | Why it matters | Mitigation in design |
|---|---|---|
| Slow client blocks publisher | `emitter.send()` blocks if proxy/client TCP window is full. One stuck client could chain-block all workers. | All sends run on the dedicated `dlcSseFanoutExecutor`; per-emitter 2 s timeout; failure → evict + log. (§8.3) |
| Emitter leak on disconnect | A reconnect storm during a corporate VPN drop creates emitters faster than they're cleaned. | Single idempotent removal closure on `onCompletion`/`onTimeout`/`onError`; per-operator cap of 3; oldest-wins eviction. (§8.2) |
| Proxy buffering | Some intermediaries buffer the entire response before forwarding → SSE never delivers. | `X-Accel-Buffering: no` + `Cache-Control: no-cache, no-transform`; FE 30 s silence detector switches to polling. (§8.6 + §8.7) |
| Multi-pod event miss | Pod P1 holds the emitter, worker on P2 fires the event — never crosses. | Phase 1: polling fallback closes the gap. Phase 2: PostgreSQL `LISTEN/NOTIFY` bridge. (§8.8) |
| Heap from emitter map | 1000 operators × 3 emitters × 4 KB = 12 MB. Bounded. | Hard caps + monitoring (`download_center_sse_emitters_connected`). (§8.2) |
| Auth expiry mid-stream | JWT lifetime can be shorter than the 60 m emitter cap. | Hard 60 m emitter timeout forces reconnect (which re-authenticates). (§8.5) |
| Fanout pool saturation | Burst of 1000 simultaneous status changes during a campaign. | `core=2 / max=8 / queue=1024 / DiscardOldestPolicy` — drops oldest, polling recovers state. (§8.3) |

### 20.4 Memory — large-file OOM defense in depth

This is the **highest-severity risk** in the entire design. Without intervention, peak heap can hit `(small.max + large.max) × max_artifact_size = 20 × 200 MB = 4 GB` → JVM OOM in production.

#### 20.4.1 The artifact contract problem
`ReportExportArtifact.bytes()` returns `byte[]`. Per C1, we cannot change this. So we must release the byte[] reference as fast as possible after the underlying call returns.

#### 20.4.2 Temp-file spool — the core mitigation

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class DownloadJobTempFileService {

    private final AttachmentStorage storage;
    @Value("${safar.export.download-center.temp-dir}")
    private Path tempDir;
    @Value("${safar.export.download-center.s3-multipart-threshold:16777216}")
    private long multipartThreshold;

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(tempDir);
        // Linux: 0700; on other OSes the call is best-effort.
        try { Files.setPosixFilePermissions(tempDir, PosixFilePermissions.fromString("rwx------")); }
        catch (UnsupportedOperationException ignored) {}
    }

    /** Write byte[] to a uniquely-named temp file. Returns the Path. */
    public Path spool(UUID jobId, byte[] bytes) throws IOException {
        Path p = Files.createTempFile(tempDir, "dlc-" + jobId + "-", ".bin");
        try {
            Files.write(p, bytes, StandardOpenOption.WRITE);
        } catch (IOException e) {
            Files.deleteIfExists(p);
            throw e;
        }
        return p;
    }

    /**
     * Stream the temp file to S3, then delete it. Caller MUST set its local
     * temp Path reference to null after this returns successfully so the
     * outer finally{} block's deleteQuietly is a no-op.
     */
    public String uploadAndDelete(Path temp, UUID jobId, String fileName,
                                   String contentType, long size) throws Exception {
        try {
            return storage.storeStreaming(jobId.toString(), fileName, temp, contentType, size);
        } finally {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
        }
    }

    public void deleteQuietly(Path p) {
        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
    }
}
```

#### 20.4.3 Streaming upload — extend `AttachmentStorage`

Add one method to the existing interface (default impl falls back to `store(InputStream)`):

```java
public interface AttachmentStorage {
    // ... existing methods ...

    /**
     * Multipart-aware streaming upload from a local file. Implementations
     * SHOULD use chunked / multipart upload above an SDK-defined threshold
     * to bound heap to the part-size buffer.
     */
    default String storeStreaming(String reportId, String fileName, Path file,
                                  String contentType, long size) throws Exception {
        try (InputStream in = Files.newInputStream(file)) {
            return store(reportId, fileName, in, size);              // existing fallback
        }
    }
}
```

**Per-backend implementation** — the project has three production-relevant `AttachmentStorage` impls (`S3AttachmentStorage` / `ObsAttachmentStorage` / `ObsProxyAttachmentStorage`); each one needs its own `storeStreaming` override:

#### `ObsAttachmentStorage` (production — G42 OBS, virtual-hosted-style)

OBS is S3-compatible but `S3TransferManager` (which depends on AWS-CRT native libs) has not been validated against G42's `obs.ae-ad-1.g42cloud.com` endpoint. We use the **SDK-native multipart API** instead — same memory profile (heap stays at part-size), zero native-lib dependency:

```java
@Override
public String storeStreaming(String reportId, String fileName, Path file,
                             String contentType, long size) throws Exception {
    String key = AttachmentObjectKeys.buildObjectKey(objectKeyPrefix, reportId, fileName);

    // Below the multipart threshold → single PUT (heap-bounded by file size,
    // but the size-class router never sends > 10 MB to the small pool, and
    // for the large pool the multipart branch below kicks in).
    if (size < multipartThreshold) {
        try (InputStream in = Files.newInputStream(file)) {
            s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).contentLength(size).build(),
                RequestBody.fromInputStream(in, size));
        }
        return key;
    }

    // Multipart path — heap usage = partSize regardless of total file size.
    String uploadId = s3Client.createMultipartUpload(b -> b.bucket(bucket).key(key).contentType(contentType))
                              .uploadId();
    List<CompletedPart> parts = new ArrayList<>();
    try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
        ByteBuffer buf = ByteBuffer.allocate(partSize);                    // single 8 MB buffer reused per part
        int partNumber = 1;
        long remaining = size;
        while (remaining > 0) {
            buf.clear();
            int read = ch.read(buf);
            if (read <= 0) break;
            buf.flip();
            UploadPartRequest req = UploadPartRequest.builder()
                    .bucket(bucket).key(key).uploadId(uploadId)
                    .partNumber(partNumber).contentLength((long) read).build();
            UploadPartResponse rsp = s3Client.uploadPart(req,
                    RequestBody.fromByteBuffer(buf));
            parts.add(CompletedPart.builder().partNumber(partNumber).eTag(rsp.eTag()).build());
            partNumber++;
            remaining -= read;
        }
        s3Client.completeMultipartUpload(b -> b.bucket(bucket).key(key).uploadId(uploadId)
                .multipartUpload(m -> m.parts(parts)));
        return key;
    } catch (Exception e) {
        // Best-effort abort to avoid orphan multipart uploads racking up cost.
        try { s3Client.abortMultipartUpload(b -> b.bucket(bucket).key(key).uploadId(uploadId)); }
        catch (Exception ignored) {}
        throw e;
    }
}
```

**Why this rather than `S3TransferManager`**: G42 OBS ships its own SDK validation only for the AWS S3 SDK *async client*; `S3TransferManager` wraps `software.amazon.awssdk.crt.s3.S3Client` (a native CRT implementation) which has not been certified against G42's endpoint URL signing. The native `createMultipartUpload + uploadPart + complete` path is what the existing `ObsAttachmentStorage` already uses (`PutObjectRequest`), just extended to multipart.

#### `S3AttachmentStorage` (dev — MinIO / AWS S3, path-style)

MinIO ≥ `RELEASE.2021-09` and AWS S3 both certify against `S3TransferManager`. For dev / test environments we use the simpler form:

```java
@Override
public String storeStreaming(String reportId, String fileName, Path file,
                             String contentType, long size) throws Exception {
    String key = AttachmentObjectKeys.buildObjectKey(objectKeyPrefix, reportId, fileName);
    UploadFileRequest req = UploadFileRequest.builder()
            .putObjectRequest(p -> p.bucket(bucket).key(key).contentType(contentType))
            .source(file)
            .build();
    transferManager.uploadFile(req).completionFuture().get();         // multipart automatic
    return key;
}
```

`S3TransferManager` reads via `AsyncRequestBody.fromFile(...)` (NIO chunked I/O). Heap stays at the part-size buffer (8 MB) regardless of total file size.

#### `ObsProxyAttachmentStorage`

Inherits the `ObsAttachmentStorage` multipart path verbatim — same `S3Client`, same OBS endpoint. The "proxy" only affects the *upload presign* surface (`presignPut` returns a backend proxy URL to bypass browser CORS), not the worker-driven upload path used by Download Center.

#### Default fallback

The `AttachmentStorage` interface default `storeStreaming` (single-PUT via `Files.newInputStream`) still exists. Any future backend that doesn't override gets correct behavior + a `download_job_upload_seconds` p99 spike on large files — which the alerts in §15.3 will flag.

#### 20.4.4 Disk-space watchdog

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class DownloadJobDiskWatchdog {

    @Value("${safar.export.download-center.temp-dir}")
    private Path tempDir;
    @Value("${safar.export.download-center.disk-min-free:1073741824}")
    private long minFreeBytes;

    private final AtomicBoolean exhausted = new AtomicBoolean(false);

    @Scheduled(fixedRateString = "${safar.export.download-center.cleanup.disk-watchdog-rate-ms:30000}")
    public void check() {
        try {
            long free = Files.getFileStore(tempDir).getUsableSpace();
            boolean nowExhausted = free < minFreeBytes;
            if (exhausted.compareAndSet(!nowExhausted, nowExhausted)) {
                log.warn("dlj.disk_watchdog free={} threshold={} exhausted={}", free, minFreeBytes, nowExhausted);
            }
        } catch (Exception e) {
            log.error("dlj.disk_watchdog_failed", e);
            // Fail-open: do NOT mark exhausted on read errors — that would
            // refuse all enqueues on a transient FS hiccup.
        }
    }

    public boolean isExhausted() { return exhausted.get(); }
}
```

`DownloadJobApplicationService.enqueue()` calls `watchdog.isExhausted()` before INSERT and rejects with `503 download.error.storage_unavailable` if true. In-flight workers are not interrupted (they already wrote their temp files; finite extra disk is tolerable).

#### 20.4.5 Startup sweep
On `ApplicationReadyEvent`, `DownloadJobTempFileService.startupSweep()` walks `tempDir`, deletes any `*.bin` older than `temp-dir-startup-sweep-age` (default 1 hour). This protects against pod-crash leaks where the worker died mid-upload and left a temp file behind.

#### 20.4.6 Hard size cap at enqueue
A second line of defense, independent of the existing `validator.enforceByteLimit`:

```
safar.export.download-center.max-job-bytes=524288000   # 500 MB
```

`DownloadJobApplicationService.enqueue()` rejects estimated-bytes-over-cap with `413 download.error.payload_too_large`. Rationale: existing per-source byte limits may be set high for sync flows where the bytes never persist; for async flows the disk and storage cost compound across concurrent jobs.

#### 20.4.7 JVM and Kubernetes sizing
Pod spec MUST include:

```
resources:
  limits:
    memory: 2Gi
  requests:
    memory: 1.5Gi
spec:
  terminationGracePeriodSeconds: 75

env:
  - name: JAVA_TOOL_OPTIONS
    value: "-XX:MaxRAMPercentage=70 -XX:MaxDirectMemorySize=512m -XX:+ExitOnOutOfMemoryError"
```

Math: 2 GiB pod limit × 70 % = ~1.4 GiB heap. Worst-case worker heap: `16×10 MB + 4×200 MB = 960 MB`. Headroom: ~440 MB for app, S3 SDK direct buffers, NIO temp file caches. Comfortable.

#### 20.4.8 Memory risk register (post-mitigation)

| Failure mode | Without mitigation | With this design | Residual |
|---|---|---|---|
| OOM from concurrent large ZIPs | Sev-1 (pod kill) | Temp-file spool + multipart upload + size-class pools + 500 MB cap | Low |
| Disk fill from leaked temp files | Sev-2 (eventual disk full) | finally-delete + startup sweep + 30 s watchdog | Low |
| Disk fill from legitimate concurrent large jobs | Sev-2 | 1 GB free → enqueue rejected with 503 | Low |
| Direct memory bloat from S3 SDK | Sev-3 | `MaxDirectMemorySize=512m` | Low |
| Slow temp file IO becomes throughput ceiling | Sev-3 | Dedicated temp dir on fast disk + `download_job_spool_seconds` p99 alert | Low |
| Pod evicted while temp file exists | Sev-3 | Startup sweep on next pod's first boot | Low |

---

## 21. Deployment & Environment Requirements

The four hardenings in §20 (CAS state machine, AFTER_COMMIT submission, non-blocking SSE, temp-file spool) all impose **concrete, testable demands on the runtime environment**. This section catalogues every demand so an SRE / DevOps reviewer can verify the target deployment is compatible **before** Phase 1 ships. Where the project deploys via Docker (or Docker Compose / Kubernetes), the requirements are translated into container-level constructs.

### 21.1 Hard requirements (Phase 1 will not work without these)

| # | Requirement | Why | Verification |
|---|---|---|---|
| H-1 | **PostgreSQL ≥ 9.5** with `JSONB` enabled | `SELECT … FOR UPDATE SKIP LOCKED` (§7.2) and `JSONB` payload column (§4.1) | `SELECT version();` must show ≥ 9.5 |
| H-2 | **Single shared Postgres instance** across all app pods | Cluster-wide queue claim and ShedLock leadership (§7.2, §17) | Same JDBC URL across pods; verify with `SELECT inet_server_addr();` |
| H-3 | **G42 OBS reachable from app pods** (`obs.ae-ad-1.g42cloud.com`) with an `app.attachment.storage=obs` profile and valid AK/SK. **Multipart upload smoke-tested against this endpoint with a real 200 MB+ file** before Phase 2 ships. (Dev / staging environments may run MinIO ≥ RELEASE.2021-09 instead.) | `ObsAttachmentStorage.storeStreaming` uses SDK-native multipart (§20.4.3). | `mc ls` against the endpoint + a manual 200 MB upload test using the SDK script in §21.7 |
| H-4 | **Persistent local writable filesystem** at `temp-dir` (default `/var/tmp/safar-dlc`) | Spool path bedrock (§20.4) | `df -h` inside container; `touch` test on mount |
| H-5 | **Temp-dir mounted as a Docker `tmpfs` OR a fast local volume**, NOT NFS | Per-job spool latency dominates throughput | If using NFS, `download_job_spool_seconds` p99 will spike |
| H-6 | **`terminationGracePeriodSeconds ≥ 75`** (Kubernetes) / `--stop-timeout 75s` (Docker) | 60 s graceful drain (§20.2.3) + 15 s buffer | `kubectl get deploy <name> -o yaml \| grep terminationGracePeriod` |
| H-7 | **Reverse proxy MUST disable response buffering** for `/downloads/stream` | Without this, SSE never delivers (§8.6) | `curl -N` against the public URL; should see `event:` lines incrementally, not in one batch |
| H-8 | **JDK 21** (project ships with virtual threads enabled by default per `application.yml` `spring.threads.virtual.enabled=true`). Sealed interfaces / switch expressions in §3.1 require ≥ 17, but VT requires 21. | §8.0 SSE design depends on VT for the simplified path; falls back correctly to platform-thread mode (`SPRING_VT_ENABLED=false`) but loses the latency advantage. | `docker exec safar-server java -version` shows 21+; `/actuator/env` shows `spring.threads.virtual.enabled=true` |
| H-9 | **`hypersistence-utils` on classpath** (or equivalent JSONB type mapper) | Hibernate `JsonBinaryType` for `request_payload JSONB` column | `mvn dependency:tree \| grep hypersistence-utils` (O2) |
| H-10 | **`software.amazon.awssdk:s3-transfer-manager` on classpath** | Multipart streaming upload (§20.4.3) | `mvn dependency:tree \| grep s3-transfer-manager` (O7) |

### 21.2 Recommended (warnings if missing)

| # | Requirement | Recommendation | If skipped |
|---|---|---|---|
| R-1 | Pod memory limit ≥ 2 GiB | Math: 16×10MB + 4×200MB + app overhead | OOMKilled under bulk-export load |
| R-2 | Direct memory cap (`-XX:MaxDirectMemorySize=512m`) | Bound S3 SDK NIO buffers | Container RSS exceeds limit even when heap looks fine |
| R-3 | `MaxRAMPercentage=70` | Leave headroom for OS + temp-file cache | RSS overshoots under burst |
| R-4 | Dedicated temp-dir volume (separate from `/var/log`) | Independent fill semantics | One filling-up affects the other; harder to alert |
| R-5 | NTP-synced clocks across pods | ShedLock and reaper rely on `now()` correctness | Reaper false positives or missed picks; ghost completions spike |
| R-6 | Health-check endpoint `/actuator/health` reachable | K8s readiness/liveness | Pod stuck in `RUNNING` workers can't be detected |

### 21.3 Docker / Docker Compose — concrete shape

#### 21.3.1 Service `Dockerfile` (the app)

```dockerfile
FROM eclipse-temurin:17-jre-alpine

# Create a dedicated user — never run as root.
RUN addgroup -S safar && adduser -S safar -G safar
USER safar

# Dedicated temp directory for download-center spool files (§20.4.4)
RUN mkdir -p /var/tmp/safar-dlc && chmod 0700 /var/tmp/safar-dlc

WORKDIR /app
COPY --chown=safar:safar target/safar-server-*.jar /app/app.jar

# JVM hardening (§10 + §20.4.7)
ENV JAVA_TOOL_OPTIONS="\
  -XX:MaxRAMPercentage=70 \
  -XX:MaxDirectMemorySize=512m \
  -XX:+ExitOnOutOfMemoryError \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/tmp/safar-dlc \
  -Djava.io.tmpdir=/var/tmp/safar-dlc \
  -Duser.timezone=Asia/Dubai"

# Graceful drain budget (§20.2.3) — 60s in-flight + 15s OS cleanup
STOPSIGNAL SIGTERM

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

> The `--stop-timeout 75s` flag is set on `docker run` / Compose, not in the Dockerfile.

#### 21.3.2 `docker-compose.yml` (production-shaped)

```yaml
version: "3.9"

services:

  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: safar
      POSTGRES_USER: safar
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U safar"]
      interval: 10s
      timeout: 5s
      retries: 5

  # NOTE: Production targets external G42 OBS — no `minio` service in this
  # compose file. For local dev/test where OBS is unreachable, swap
  # APP_ATTACHMENT_STORAGE to `s3` and stand up a separate
  # `docker-compose.dev.yml` overlay with a MinIO sidecar.

  safar-server:
    image: safar-server:${VERSION}
    depends_on:
      postgres: { condition: service_healthy }
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/safar
      SPRING_DATASOURCE_USERNAME: safar
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}

      # Object storage — external G42 OBS (production)
      APP_ATTACHMENT_STORAGE: obs
      APP_ATTACHMENT_ENDPOINT: ${OBS_ENDPOINT:-https://obs.ae-ad-1.g42cloud.com}
      APP_ATTACHMENT_BUCKET:   ${OBS_BUCKET:-safar-attachments}
      APP_ATTACHMENT_REGION:   ${OBS_REGION:-ae-ad-1}
      APP_ATTACHMENT_ACCESS_KEY: ${OBS_AK}
      APP_ATTACHMENT_SECRET_KEY: ${OBS_SK}
      # If you need to bypass browser CORS for FE-direct uploads, switch to obs-proxy
      # APP_ATTACHMENT_STORAGE: obs-proxy

      # Download Center — see §10
      SAFAR_EXPORT_DOWNLOAD_CENTER_ENABLED: "true"
      SAFAR_EXPORT_DOWNLOAD_CENTER_TEMP_DIR: /var/tmp/safar-dlc
      SAFAR_EXPORT_DOWNLOAD_CENTER_DISK_MIN_FREE: 1073741824    # 1 GB
      SAFAR_EXPORT_DOWNLOAD_CENTER_MAX_JOB_BYTES: 524288000     # 500 MB

    volumes:
      # Dedicated tmpfs for spool files (§20.4) — survives the container,
      # not the host. Sized to comfortably hold worst-case concurrent jobs.
      - type: tmpfs
        target: /var/tmp/safar-dlc
        tmpfs:
          size: 2147483648         # 2 GB tmpfs

    # Graceful shutdown budget (§20.2.3 + R-1) ───────────────────────────
    stop_grace_period: 75s
    stop_signal: SIGTERM

    # Memory caps — matches JVM MaxRAMPercentage=70 ─────────────────────
    deploy:
      resources:
        limits:
          memory: 2147483648       # 2 GiB
        reservations:
          memory: 1610612736       # 1.5 GiB

    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:8080/actuator/health/readiness"]
      interval: 15s
      timeout: 5s
      retries: 3
      start_period: 30s

    # If running multiple replicas behind a load balancer ─────────────
    # deploy:
    #   replicas: 3
    # NB: scale=N requires a reverse proxy (nginx) routing /api to all pods.

  nginx:
    image: nginx:1.25-alpine
    depends_on: [safar-server]
    ports:
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
      - ./certs:/etc/nginx/certs:ro

volumes:
  pgdata:
```

**Why `tmpfs` for the spool dir**: it's RAM-backed, so writes don't go to disk. That sounds counter-intuitive for a memory-pressure mitigation, but the trick is that `byte[]` in Java heap is GC-managed (slow to free, fragmented), while `tmpfs` is OS-managed (instantly reusable). The peak heap drop from §20.4 is real — what we *trade* is RAM-as-disk for RAM-as-heap. If the deployment can't spare 2 GB of tmpfs, mount a real disk volume instead — performance trade is ~30 % worse `download_job_spool_seconds` p99, still correct.

> **Quick decision rule for ops**: 4-pod deployment → 4 × 2 GiB tmpfs = 8 GiB total RAM cost on the host. If that's too much, switch to a host-volume mount on a fast SSD. Both work.

#### 21.3.3 `nginx.conf` — the SSE-critical bits

```nginx
http {
    upstream safar_app {
        # Round-robin across replicas; each SSE client sticks to one pod
        # via ip_hash to prevent reconnect-bouncing (§8.8).
        ip_hash;
        server safar-server-1:8080;
        server safar-server-2:8080;
        server safar-server-3:8080;
    }

    server {
        listen 443 ssl http2;

        # ────────────────────────────────────────────────────────────────
        # The SSE endpoint MUST disable buffering — see §8.6 / H-7
        # ────────────────────────────────────────────────────────────────
        location /api/v1/admin/downloads/stream {
            proxy_pass               http://safar_app;
            proxy_http_version       1.1;
            proxy_set_header         Connection           "";
            proxy_set_header         Host                 $host;
            proxy_buffering          off;          # ← critical
            proxy_cache              off;
            proxy_request_buffering  off;
            proxy_read_timeout       3600s;        # match emitter-timeout=60m
            proxy_send_timeout       3600s;
            chunked_transfer_encoding on;
            add_header               X-Accel-Buffering    no always;
        }

        # The file-stream endpoint also wants no buffering for big files.
        location ~ ^/api/v1/admin/downloads/[^/]+/file$ {
            proxy_pass            http://safar_app;
            proxy_buffering       off;
            proxy_request_buffering off;
            proxy_read_timeout    300s;
        }

        # Everything else uses normal proxying.
        location / {
            proxy_pass http://safar_app;
            proxy_set_header Host $host;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        }
    }
}
```

**Three rules** the operator MUST not break:
1. `proxy_buffering off` on `/downloads/stream` — without it, SSE never streams.
2. `ip_hash` (or `sticky` cookie) — without it, an SSE-connected browser bounces between pods on reconnect, and the polling fallback effectively becomes the only working path.
3. `proxy_read_timeout ≥ emitter-timeout` — otherwise the proxy closes connections **before** the heartbeat says "still alive."

#### 21.3.4 Multi-replica considerations

| Concern | Single-pod | Multi-pod |
|---|---|---|
| Worker queue claim | Trivial — one pod owns everything | `FOR UPDATE SKIP LOCKED` ensures correctness |
| Expiry / reaper schedulers | Always run | ShedLock pins to one replica per cron tick |
| SSE event delivery | All emitters and workers on same JVM | Emitters per-pod; cross-pod jobs invisible until polling fallback (§8.7); Phase 2 PG `LISTEN/NOTIFY` |
| Temp-dir | Per-pod | Per-pod (don't share via NFS — H-5) |
| Graceful shutdown | One pod drains | Each pod drains; rolling-restart ordering matters less because reaper covers any orphans |

### 21.4 Postgres-side configuration

| Setting | Recommended | Why |
|---|---|---|
| `max_connections` | ≥ pods × (Hikari max + 5) | Each pod's Hikari pool, plus 1 for the future Phase 2 `LISTEN/NOTIFY` connection |
| `max_locks_per_transaction` | ≥ 64 (default) | `FOR UPDATE SKIP LOCKED` claims one row at a time — default is enough |
| `idle_in_transaction_session_timeout` | 5 min | Defensive against bugs where worker holds tx; pairs with reaper |
| Logical replication slots | not required | We don't use logical replication |

If using PgBouncer:

| Mode | Verdict |
|---|---|
| **Session pooling** | ✅ Works as-is. |
| **Transaction pooling** | ⚠️ `LISTEN/NOTIFY` (Phase 2 only) breaks here — reserve a direct connection bypassing PgBouncer for the listener. Phase 1 does not need this. |
| **Statement pooling** | ❌ `FOR UPDATE SKIP LOCKED` requires a transaction; statement pooling will reject. |

### 21.5 G42 OBS configuration (production)

Configured in the OBS Console (https://console.g42cloud.com → OBS → bucket detail), not via `mc` / `aws s3api` (those are for MinIO / AWS S3 only).

| OBS setting | Recommended | Why |
|---|---|---|
| Bucket lifecycle rule | 8-day expiration on `<prefix>/{jobId}/*` prefix | Belt-and-braces backup for §17 cleanup scheduler. 8d > 7d application TTL so the app remains the primary cleaner. **Path matches `AttachmentObjectKeys.buildObjectKey`** — `safar/{jobId}/{uuid}_{filename}`. |
| Multipart-upload abort | Auto-abort incomplete uploads after 24 h (Bucket → Lifecycle Rules → "Aborts incomplete multipart uploads") | Defends against worker-crash mid-multipart leaving orphan parts that accrue cost |
| CORS | Not required for Phase 1 (FE downloads via controller `GET /downloads/{jobId}/file`, same origin via reverse proxy) | If a future Phase enables direct presigned-URL download from the browser, then add allowed-origins matching the FE host |
| Bucket versioning | Off | Never overwrite a download key |
| Server-side encryption | SSE-OBS (default) | Inherits existing project policy |
| Bucket access | Private only — never public-read | Defense in depth; presigned URLs are explicitly NOT used in Phase 1 (§16) |

**Dev / staging override**: when `APP_ATTACHMENT_STORAGE=s3` (MinIO) the same lifecycle rules can be applied via:

```bash
mc ilm rule add --expire-days 8 --prefix "safar/" minio/safar-attachments
mc ilm rule add --expire-days 1 --abort-multipart-uploads --prefix "safar/" minio/safar-attachments
```

### 21.5.1 OBS endpoint smoke test (one-shot, before going live)

```bash
# inside the safar-server pod, exercise the exact code path the worker will use:
docker exec -it safar-server-1 sh -c '
  java -cp /app/app.jar ae.gov.safar.tools.ObsMultipartSmoke \
    --endpoint  https://obs.ae-ad-1.g42cloud.com \
    --bucket    safar-attachments \
    --region    ae-ad-1 \
    --size-mb   200
'
# expect: "OK 200 MB uploaded in <T> seconds; multipart parts=N; verification passed"
```

A small `ObsMultipartSmoke` utility (an internal test main) should be added to the codebase — same SDK calls as `ObsAttachmentStorage.storeStreaming` but without dragging up the full Spring context. **This is the single most important pre-Phase-2 verification.**

### 21.6 Observability stack (recommended)

| Component | What it shows |
|---|---|
| Prometheus | All metrics in §15.1 (`/actuator/prometheus`) |
| Grafana dashboard "Download Center" | 4 rows: Volume / Latency / Memory / SSE — drives Phase 2 acceptance and Phase 4 decommission |
| Loki / structured logs | `dlj.transition`, `dlj.ghost_completion`, `dlj.dispatch_rejected`, `dlj.disk_watchdog` |
| Alert rules | All in §15.3 |

### 21.7 Pre-deployment checklist (one-page)

Before flipping `safar.export.download-center.enabled=true` in any environment:

- [ ] H-1 Postgres version ≥ 9.5 (run `SELECT version();`)
- [ ] H-2 All app pods point to the same DB (compare `jdbc.url`)
- [ ] H-3 **G42 OBS reachable** + endpoint AK/SK valid + **multipart upload smoke-tested** with a real 200 MB file via `ObsMultipartSmoke` (§21.5.1). Or: dev profile MinIO `mc admin info` if testing locally.
- [ ] H-4 / H-5 Temp dir writable, ≥ 2 GB free, NOT on NFS (`stat -f /var/tmp/safar-dlc` shows local FS)
- [ ] H-6 `stop_grace_period: 75s` set in Compose / `terminationGracePeriodSeconds: 75` in K8s
- [ ] H-7 SSE endpoint streams through nginx (curl test with `-N` flag — events arrive incrementally, NOT in one batch at end)
- [ ] H-8 **JDK 21** + virtual threads enabled by default (`docker exec safar-server java -version`; `curl /actuator/env \| grep spring.threads.virtual`)
- [ ] H-9 `hypersistence-utils` on classpath
- [ ] H-10 `s3-transfer-manager` on classpath (only required if dev/test runs MinIO; production OBS uses SDK-native multipart)
- [ ] R-5 NTP sync (`chronyc tracking` in host or sidecar)
- [ ] Flyway V<NEXT>__download_job.sql applied (`SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;`)
- [ ] Permissions seeded (`SELECT code FROM permission WHERE code LIKE 'download_center:%';`)
- [ ] **VT-on load test passed** — §14.4 with `SPRING_VT_ENABLED=true` AND `=false`; both must meet p99 enqueue ≤ 150 ms. If VT-on is materially slower → ship with VT off + file an upstream issue.
- [ ] Smoke test: enqueue → READY → file download with byte-identical SHA-256

If any of H-1..H-10 fails, **do not proceed**. The system will appear to work but will exhibit one of the §20 failure modes under load.

---

# Step 3 — Requirement Mapping Checklist (self-verification)

This is the cross-check that the TDD covers every PRD requirement and respects every existing-code constraint. Each row pairs a PRD requirement with the TDD evidence and a "current code preserved?" verdict.

## A. Functional requirements (PRD §3)

| PRD ID | Requirement | TDD location | Honors C1–C6? | Verdict |
|---|---|---|---|---|
| FR-1 | Async enqueue, 202 + jobId; legacy `X-Export-Mode: sync` keeps 200 + bytes | §6.2, §11.1, §12.1 | C1 (no pipeline change), C4 (sync coexists) | ✅ |
| FR-2 | 6 states + transitions; illegal → 409 | §5.1, §5.2, §12.5 | — | ✅ |
| FR-3 | Dashboard with columns, filters, search | §3.1 (`DownloadCenterController`), §12.2 | — | ✅ (UI rendering is FE) |
| FR-4 | Real-time progress via SSE + polling fallback | §8 (full hardened design), §12.3, §20.3 (risk register), D-Q2 (3-bucket) | C1 (no pipeline hooks) | ✅ (3-bucket per D-Q2; non-blocking fanout, polling fallback covers proxy/multi-pod gaps) |
| FR-5 | File retrieval streams from S3, mirrors sync headers | §12.4, §16 | C2 (attachment row), C6 (SHA-256) | ✅ |
| FR-6 | Cancel/delete with state-aware visibility | §5.3, §11 (legacy), §3.1 | D-Q3 (best-effort RUNNING cancel) | ✅ (with documented downgrade) |
| FR-7 | In-system notification on READY/FAILED | §9 | D-Q1 (STATUS kind reuse) | ✅ |
| FR-8 | 7-day TTL with hourly purge; FAILED/CANCELLED 30-day | §10, §17, §15 | C2 (storage_key reset) | ✅ |
| FR-9 | i18n + RTL, msgkey: payloads | §13 | C5 (msgkey: contract) | ✅ |

## B. Architecture & non-functional (PRD §4, §9)

| PRD requirement | TDD location | Verdict |
|---|---|---|
| `ReportExportServiceImpl` not modified | §3.2 (modified files list excludes it), §6.2 | ✅ C1 |
| `attachment` row contract preserved | §5.2, §6.3 (markReady), §17 (additive migration) | ✅ C2 |
| `ReportExportedEvent` still single source | §5.3, §16 | ✅ C3 |
| ShedLock for cluster-safe scheduling | §10 (cron config), §3.1 (Schedulers) | ✅ |
| Pessimistic + SKIP LOCKED for queue pickup | §7.2 | ✅ |
| Stuck-job reaper | §3.1, §15.3, §20.1.4 (ghost-completion handling) | ✅ |
| Per-operator cap 3 / global cap 32 | §7.4, §10 | ✅ |
| Worker tx isolation (failure record durable) | §6.0 (AFTER_COMMIT), §6.1 (REQUIRES_NEW), §6.2 (markFailed catch-all) | ✅ |
| Bytes streamed by controller (no presigned) | §12.4, §16 | ✅ |
| Metrics emitted | §15.1 | ✅ |
| Audit unchanged | §16, §6 (no duplicate publish) | ✅ C3 |
| **Memory safety under concurrent large jobs** | **§20.4** (temp-file spool + S3 multipart + size-class pools + disk watchdog + 500 MB cap + JVM/Pod sizing) | ✅ D-Mem |
| **SSE non-blocking fanout** | **§8.3, §20.3** (dedicated executor, 2 s send timeout, evict-on-fail) | ✅ D-SSE |
| **Job-state atomicity** | **§5.2, §20.1.2** (CAS via UPDATE-RETURNING; `StateTransitionLostException`) | ✅ |
| **Idempotent enqueue retry** | **§4.1 (column + unique index), §20.1.1** | ✅ |
| **Submit-after-commit ordering** | **§6.0** (`@TransactionalEventListener(AFTER_COMMIT)`) | ✅ |
| **Graceful shutdown** | **§20.2.3** (`waitForTasksToCompleteOnShutdown`, `terminationGracePeriodSeconds≥75`) | ✅ |
| **Per-job hard timeout** | **§20.1.5** (`Future.get(timeout)` + ghost-completion cleanup) | ✅ |

## C. Backward compatibility (PRD §10)

| Surface | Treatment | Verdict |
|---|---|---|
| `POST .../exports/report` 200 + bytes | Preserved via `X-Export-Mode: sync` branch | ✅ C4 |
| `attachment` row contract | `storage_key` may be non-null while bytes alive (was always null) — readers tolerate either | ✅ C2 |
| `ReportExportedEvent` | Published once per export, by underlying pipeline, inside worker tx | ✅ C3 |
| SHA-256 verify | Unchanged column, unchanged listener | ✅ C6 |
| Existing UI buttons | Coordinated FE swap during Phase 2 | ✅ |
| Permissions | Additive — no existing permission removed | ✅ |

## D. Acceptance criteria (PRD §11)

| AC | TDD evidence | Verdict |
|---|---|---|
| Enqueue → READY byte-identical | §14.2 row 1 | ✅ |
| Tab close doesn't halt | §14.2 row "Pod crash during RUNNING" + §7 | ✅ |
| Cancel timing | §5.3 + §14.2 | ✅ (D-Q3 caveat) |
| Retry produces new jobId | §3.1 (DownloadJobApplicationService.retry) | ✅ |
| AR locale mirrors | §13.2 | ✅ |
| Sync regression suite passes | §11.1 | ✅ |
| Audit shape parity | §16 | ✅ |
| Verify-pdf works against DC ZIP | §14.2 row "Sync path SHA-256 verify" + §16 | ✅ C6 |
| EN/AR parity CI gate | §14.3 | ✅ C5 |
| Cross-user isolation | §14.2 + §16 | ✅ |

## E. Open PRD questions

| PRD §14 Q | TDD answer |
|---|---|
| Q1 — partial bytes streaming during RUNNING | "Wait for READY" — D-Q2 / §6.2 |
| Q2 — object storage path schema | `<prefix>/{jobId}/{uuid}_{filename}` — D-Q4 / §3.2 |
| Q3 — notification opt-out vs opt-in | Opt-out (delegated to existing `NotificationPreferences` per FR-7) — §9 |
| Q4 — Crisis snapshots routing | On-demand snapshots (B4) routed through DC; recurring `CrisisSnapshotScheduleRunner` stays on its existing pipeline — §1.1 |
| Q5 — Per-operator cap | 3 default, `safar.export.download-center.max-running-per-operator` — §10 |

## F. Existing-code preservation summary

| Existing artifact | Touch type |
|---|---|
| `ReportExportServiceImpl` | **Untouched** |
| `ReportExportRequest` / `ReportExportArtifact` / `ReportExportedEvent` | **Untouched** |
| `ReportExportApplicationService` | **Untouched** |
| `ReportExportAuditListener` | **Untouched** |
| `PdfVerificationService` / `AdminExportVerificationController` | **Untouched** |
| `S3AttachmentStorage` / `AttachmentStorage` interface | **Untouched** |
| `Attachment` entity columns | **Untouched** (only `storage_key` lifecycle changed — both states already legal) |
| `NotificationKind` enum | **Untouched** (D-Q1) |
| `NotificationPipeline` / `NotificationStrategy` interface | **Untouched** (we add new strategy beans only) |
| `SchedulerLockConfig` | **Untouched** (we add new schedulers using existing pattern) |
| `MessageKeyLocalizer` / `MessageSource` | **Untouched** (we add new keys) |
| `JwtAuthFilter` / `AdminPrincipal` / `RequirePermission` | **Untouched** |
| `ReportExportController` | **Micro-edit per D-Q5** (drop `@Transactional`, add header branch) — exhaustively documented in §11.1 |
| `AdminCrisisSnapshotController` (B4) | **Micro-edit** — extract handler body to Port, add header branch |
| `AdminReportController` (B5) | **Micro-edit** — extract handler body to Port, add header branch |
| `Permissions.java` | **Additive** (2 new constants) |
| `UserSeed.seedPermissions()` | **Additive** (grant logic for new permissions) |
| `messages.properties` / `messages_ar.properties` | **Additive** (new `download.*` keys) |
| `db/migration/` | **Additive** (one new V file, no DDL on existing tables) |

**Conclusion:** every PRD functional requirement, non-functional requirement, acceptance criterion, and backward-compatibility hard guarantee maps to a concrete TDD section. The hard constraints C1–C6 are honored end-to-end. The 5 architectural defaults (D-Q1 … D-Q5) and the scope decision (D-Scope) are explicit and reversible without redoing the design.

---

**End of TDD.** Ready for engineering pickup. Sequencing recommendation: Phase 0 (migration + dark deploy) → Phase 1 (worker + dispatcher) → Phase 2 (controllers + UI) → Phase 3 (notifications + cleanup) → Phase 4 (sync removal). Each phase is independently revertable.
