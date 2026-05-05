# BUG-LF-20 · FE GuestCapture 没解析 BE presign response envelope · imageUrl=undefined → analyze 400

**Severity**: P1 (游客拍题 100% fail · 文案误导成"相机权限")
**Discovered**: 2026-05-05 · user 截图 chrome desktop 反馈"明明在电脑上为何报相机权限"
**Status**: OPEN
**Module**: frontend/apps/h5 (GuestCapture)
**Related**: BUG-LF-18 (FE PUT MinIO Webkit CORS · 不同 root cause)

## 现象

User 在 desktop chrome 访问 `http://localhost:9873/welcome` → 点"试一试" → 选 unsplash 数学题图 → 触发 ERROR overlay 显示:
> 🔴 **请检查相机权限**
> 允许访问相机后即可拍题

详见: `qa/welcome-landing-e2e/diagnosis-camera-error-overlay.md` (含完整 root cause + log 串证)

## Root cause

`frontend/apps/h5/src/pages/GuestCapture/index.tsx:135`:

```ts
const { url: uploadUrl, image_url: imageUrl } = await presignRes.json() as { url: string; image_url: string };
```

但 BE 返:
```json
{
  "code": 0,
  "message": "OK",
  "data": { "url": "...", "image_url": "...", "object_key": "...", ... },
  "trace_id": "..."
}
```

顶层无 `url` / `image_url` · 解构得到 `undefined` · PUT undefined 静默失败 · analyze body `image_url=null` · BE validation 400 · ERROR overlay 显示 hardcode 文案"相机权限"。

BE log 真证据 (anonymous-service · 时间 15:07:13/15:07:42 两次重试都同样 fail):
```
WARN GlobalExceptionHandler: validation-error msg=Validation failed for argument [0] in 
public ResponseEntity<?> GuestController.analyze(...): 
[Field error in object 'guestAnalyzeRequest' on field 'imageUrl': rejected value [null]; ...]
```

## Fix

`GuestCapture/index.tsx:135` 改:

```ts
type PresignResp = {
  code: number;
  message: string;
  data: {
    url: string;
    image_url: string;
    object_key: string;
    expires_in_sec: string;
    method: string;
  };
};

const json = (await presignRes.json()) as PresignResp;
if (json.code !== 0 || !json.data?.url || !json.data?.image_url) {
  setCaptureState('ERROR');
  setLastError({ code: 'PRESIGN', detail: `BE response invalid · code=${json.code}` });
  return;
}
const { url: uploadUrl, image_url: imageUrl } = json.data;
```

并且 PUT MinIO 加 status check (line 138):
```ts
const putRes = await fetch(uploadUrl, { method: 'PUT', body: file });
if (!putRes.ok) {
  setCaptureState('ERROR');
  setLastError({ code: 'UPLOAD', detail: `MinIO PUT HTTP ${putRes.status}` });
  return;
}
```

## 影响

- ✅ 修后 · happy path 真通 · 不再 imageUrl=null
- ✅ 修后 · 即使 envelope 不正常 (e.g. BE 改字段名) 也走 ERROR 而非装着上传成功
- ✅ 修后 · errorCode='PRESIGN' / 'UPLOAD' 区分 · 配合 Phase 1 UX 文案准确

## Verification

修后用 chrome 真测:
1. http://localhost:9873/welcome → 点"试一试" → 选 unsplash 图 (≥10×10 像素)
2. 期望: 跳 `/analyzing/{taskId}` · DashScope 真分析 · 9-15s 完成
3. 期望: BE log 无 imageUrl null validation-error
4. 期望: ai_usage_log 新 row · tokens_out > 100

## 关联 BUG 链

- BUG-LF-09 (BE endpoint 缺 · ✅ FIXED)
- BUG-LF-17 (PromptInjectionGuard self-block · ✅ FIXED)
- BUG-LF-18 (FE PUT MinIO Webkit CORS · OPEN · 跟本 bug 不同 root)
- BUG-LF-19 (Qianwen stub · ✅ FIXED)
- BUG-LF-20 (本 · FE 没解析 envelope · 文案 hardcode 误导成"相机权限")

LF-09 → 17 → 19 三层 BE stub 链全闭后 · 暴露 LF-20 这个 FE 解析 bug · 直接影响 user 体感。

## 后续 Phase 1 UX 改造 (独立)

修 LF-20 envelope 后 · 仍要做 Phase 1 (errorCode 分类 + ERROR overlay 加 fallback button + 3 source button testid) · 因为:
- presign / analyze / 真网络断 时 ERROR overlay 仍 hardcode "相机权限"
- 用户 retry 没真出路 (仅 setCaptureState('IDLE'))
- 详见: `/Users/allenwang/.claude/plans/hashed-gathering-kahn.md` Phase 1
