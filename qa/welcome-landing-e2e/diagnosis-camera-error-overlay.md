# 诊断 · GuestCapture "请检查相机权限" overlay 真 root cause

> **诊断时间**: 2026-05-05 15:22 (UTC+4)
> **触发**: User 在 desktop chrome 截图 (`issues/Captch no permiession.png`) · 反馈"还是不是问题，因为我在电脑上使用的"
> **方法**: 看 BE log + curl presign 响应 + 比对 FE 解析代码
> **结论**: **不是相机权限问题** · 是 FE 没解析 BE response envelope · imageUrl 取成 undefined · BE validation 400 · FE 文案 hardcode 误导

---

## 真 root cause

### 1. BE presign response 是 envelope wrap (不是直接的 dict)

`curl POST /api/file/presign` 真返:
```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "image_url": "http://localhost:19000/wrongbook-dev/wrongbook/...?X-Amz-...",
    "object_key": "wrongbook/.../diagnosis-test.jpg",
    "expires_in_sec": "900",
    "url": "http://localhost:19000/wrongbook-dev/wrongbook/...?X-Amz-...",
    "method": "PUT"
  },
  "trace_id": "d3a43196-..."
}
```

### 2. FE 直接解构顶层 (line 135 GuestCapture/index.tsx)

```ts
const { url: uploadUrl, image_url: imageUrl } = await presignRes.json() as { url: string; image_url: string };
```

但顶层只有 `code/message/data/trace_id` · **没有** `url` 和 `image_url`。所以:
- `uploadUrl` = `undefined`
- `imageUrl` = `undefined`

### 3. PUT undefined 静默失败

```ts
await fetch(uploadUrl, { method: 'PUT', body: file });  // uploadUrl=undefined
```

`fetch(undefined)` 在 chrome 表现:
- TypeError 或 fetch 当字符串 "undefined" 处理 (浏览器实现差异)
- 没 try/catch · 但下面继续走

### 4. analyze body image_url=null → BE 400

```ts
const analyzeRes = await fetch('/api/guest/analyze', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    device_fp: deviceFp,
    subject: selectedSubject,
    image_url: imageUrl,  // imageUrl=undefined → JSON.stringify 后 image_url 字段不存在 → BE 当成 null
  }),
});
```

BE log 真证据 (anonymous-service · 时间 2026-05-05T15:07:13/15:07:42):
```
WARN GlobalExceptionHandler: validation-error msg=Validation failed for argument [0] in 
public ResponseEntity<?> GuestController.analyze(GuestAnalyzeRequest, HttpServletRequest): 
[Field error in object 'guestAnalyzeRequest' on field 'imageUrl': rejected value [null]; 
codes [NotBlank.guestAnalyzeRequest.imageUrl,...]; default message [must not be blank]]
```

### 5. FE setCaptureState('ERROR') · 文案误导

`processCapture()` line 174-176:
```ts
if (!analyzeRes.ok) {
  setCaptureState('ERROR');
  return;
}
```

ERROR overlay (line 502-503) hardcode:
```jsx
<h2>请检查相机权限</h2>
<p>允许访问相机后即可拍题</p>
```

User 看到 "请检查相机权限" · **完全误导** · 真问题是 FE 解析 BE envelope 错。

---

## 时间线 (BE log 串证据)

```
15:07:13.258  file-service:        presign 200 OK · key=wrongbook/.../327068609538973696_jonatan-pie-3l3RwQdHRHg-unsplash.jpg
              ↓ user (browser) 解析失败 · imageUrl=undefined
              ↓ PUT undefined (静默 / failed)
              ↓ analyze body image_url=null
15:07:13.355  anonymous-service:   400 validation-error · imageUrl null

15:07:42.519  file-service:        presign 200 OK · 同 key (user 重试一次)
              ↓ 同样问题
15:07:42.542  anonymous-service:   400 validation-error · imageUrl null
```

User 选了 unsplash 图 (jonatan-pie-3l3RwQdHRHg-unsplash.jpg) · 点了至少 2 次 · 都触发同样的 envelope 解析 bug。

---

## 跟 BUG-LF-18 (Webkit CORS) 不同

LF-18 是 Webkit-specific · PUT MinIO 因 CORS preflight fail。本次 user 在 **desktop chrome** · presign + PUT 都不在浏览器报 CORS · 仅 FE 代码解析 envelope 错。

LF-18 修不修不影响本 bug · 这是独立的 FE bug。

---

## file 新 BUG: BUG-LF-20

**Severity**: P1 (导致游客拍题 100% fail · UI 文案误导)
**Owner**: FE (frontend/apps/h5/src/pages/GuestCapture/index.tsx:135)
**Modules affected**: GuestCapture

### 修法 (1 行核心 + 类型)

```ts
// before (line 135):
const { url: uploadUrl, image_url: imageUrl } = await presignRes.json() as { url: string; image_url: string };

// after:
type PresignResp = { code: number; message: string; data: { url: string; image_url: string } };
const json = await presignRes.json() as PresignResp;
if (json.code !== 0 || !json.data) {
  setCaptureState('ERROR');
  return;
}
const { url: uploadUrl, image_url: imageUrl } = json.data;
```

并且 PUT MinIO 加 try/catch + status check:
```ts
const putRes = await fetch(uploadUrl, { method: 'PUT', body: file });
if (!putRes.ok) {
  setCaptureState('ERROR');
  return;
}
```

---

## 跟 Phase 1 UX 改造关系

Phase 1 (errorCode 分类 + fallback button) 仍要做 — 即使修了 LF-20 envelope · 文案 hardcode "相机权限" 还是错的 (presign 5xx / analyze 5xx / 真网络断 都会触发)。但 LF-20 修后 · 截图那种"明明上传成功却报相机权限"的极端误导消失。

**修序**:
1. **先修 LF-20** (envelope 解析) · 让 happy path 真通 · user 一次成功
2. **再做 Phase 1 UX** (errorCode 分类 + fallback) · 让真错误时文案准确 + 用户有出路

---

## Verification (Phase 0 收尾)

修 LF-20 后 · 用 chrome desktop 重测:
1. 打开 http://localhost:9873/welcome
2. 点 "试一试，无需登录"
3. 选张正经图 (≥10×10 像素 · 任意 unsplash 数学题图)
4. 期望: 跳 /analyzing/{taskId} · 不再触发 ERROR overlay
5. 期望 BE log: anonymous-service 不再有 imageUrl null validation-error

跑完 OK 才视为 LF-20 真 close。
