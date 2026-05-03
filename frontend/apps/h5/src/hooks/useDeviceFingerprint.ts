/**
 * useDeviceFingerprint · FE-06 · 5-source device fingerprint hash
 *
 * Sources: Canvas / WebGL / AudioContext / UA / Accept-Language
 * Hash: SHA-256 → hex
 * Storage: localStorage `lf_device_fp`
 *
 * C3 Red Line: Never write user-id field. Do NOT pollute auth session.
 */
import { useEffect, useState } from 'react';

const STORAGE_KEY = 'lf_device_fp';

/** FNV-1a 32-bit hash as a fast fallback when SubtleCrypto is unavailable. */
function fnv1a32(str: string): string {
  let h = 0x811c9dc5;
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 0x01000193) >>> 0;
  }
  return h.toString(16).padStart(8, '0');
}

async function sha256hex(text: string): Promise<string> {
  if (typeof crypto !== 'undefined' && crypto.subtle) {
    const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
    return Array.from(new Uint8Array(buf))
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('');
  }
  // Fallback: fnv1a32 (no SubtleCrypto in old WebView)
  return fnv1a32(text);
}

/** Source 1: Canvas 2D fingerprint. */
function getCanvasFingerprint(): string {
  try {
    const canvas = document.createElement('canvas');
    canvas.width = 200;
    canvas.height = 50;
    const ctx = canvas.getContext('2d');
    if (!ctx) return '';
    ctx.textBaseline = 'top';
    ctx.font = '14px Arial';
    ctx.fillStyle = '#f60';
    ctx.fillRect(125, 1, 62, 20);
    ctx.fillStyle = '#069';
    ctx.fillText('LF-FP-2026', 2, 15);
    ctx.fillStyle = 'rgba(102,204,0,0.7)';
    ctx.fillText('LF-FP-2026', 4, 17);
    return canvas.toDataURL().slice(-64); // last 64 chars are content-specific
  } catch {
    return '';
  }
}

/** Source 2: WebGL renderer string. */
function getWebGLFingerprint(): string {
  try {
    const canvas = document.createElement('canvas');
    const gl =
      (canvas.getContext('webgl') as WebGLRenderingContext | null) ??
      (canvas.getContext('experimental-webgl') as WebGLRenderingContext | null);
    if (!gl) return '';
    const ext = gl.getExtension('WEBGL_debug_renderer_info');
    if (!ext) return '';
    const renderer = gl.getParameter(ext.UNMASKED_RENDERER_WEBGL) as string;
    const vendor = gl.getParameter(ext.UNMASKED_VENDOR_WEBGL) as string;
    return `${vendor}|${renderer}`;
  } catch {
    return '';
  }
}

/** Source 3: AudioContext fingerprint (oscillator characteristics). */
async function getAudioFingerprint(): Promise<string> {
  try {
    const AudioCtx =
      window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!AudioCtx) return '';
    const ctx = new AudioCtx();
    const osc = ctx.createOscillator();
    const analyser = ctx.createAnalyser();
    const gain = ctx.createGain();
    gain.gain.value = 0;
    osc.connect(analyser);
    analyser.connect(gain);
    gain.connect(ctx.destination);
    osc.start(0);
    const buf = new Float32Array(analyser.frequencyBinCount);
    analyser.getFloatFrequencyData(buf);
    osc.stop();
    await ctx.close();
    // Use first 8 non-zero values as signature
    const sig = buf.slice(0, 8).join(',');
    return sig;
  } catch {
    return '';
  }
}

/** Source 4: User-Agent string. */
function getUAFingerprint(): string {
  return navigator.userAgent;
}

/** Source 5: Accept-Language. */
function getLangFingerprint(): string {
  return navigator.language + '|' + (navigator.languages ?? []).join(',');
}

/** Assemble all 5 sources → SHA-256 hex. */
async function computeFingerprint(): Promise<string> {
  const [audioFp] = await Promise.all([getAudioFingerprint()]);
  const raw = [
    getCanvasFingerprint(),
    getWebGLFingerprint(),
    audioFp,
    getUAFingerprint(),
    getLangFingerprint(),
  ].join('||');
  return sha256hex(raw);
}

/** Persist fingerprint to localStorage (C3: never user-id, never auth token key). */
function loadCached(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

function saveCache(fp: string): void {
  try {
    localStorage.setItem(STORAGE_KEY, fp);
  } catch {
    // storage blocked — non-fatal
  }
}

/**
 * Returns a stable device fingerprint string.
 * `null` while computing (async, one-time).
 */
export function useDeviceFingerprint(): string | null {
  const [fp, setFp] = useState<string | null>(() => loadCached());

  useEffect(() => {
    if (fp) return; // already resolved from cache
    let cancelled = false;
    computeFingerprint().then((hash) => {
      if (cancelled) return;
      setFp(hash);
      saveCache(hash);
    });
    return () => {
      cancelled = true;
    };
  }, [fp]);

  return fp;
}
