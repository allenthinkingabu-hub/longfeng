package com.longfeng.anonymous.support;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Best-effort client IP extraction · honors X-Forwarded-For (left-most non-empty), then
 * X-Real-IP, then {@link HttpServletRequest#getRemoteAddr()}.
 *
 * <p>Used by rate-limiters that bucket on (hashed) IP. The result is hashed downstream — we
 * never store raw IPs.
 */
public final class ClientIpResolver {

  private ClientIpResolver() { }

  /** Returns the best-guess client IP, never null (falls back to "unknown"). */
  public static String resolve(HttpServletRequest req) {
    if (req == null) {
      return "unknown";
    }
    String xff = req.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      // X-Forwarded-For: client, proxy1, proxy2 — use the left-most token
      int comma = xff.indexOf(',');
      String first = comma >= 0 ? xff.substring(0, comma) : xff;
      first = first.trim();
      if (!first.isEmpty()) {
        return first;
      }
    }
    String real = req.getHeader("X-Real-IP");
    if (real != null && !real.isBlank()) {
      return real.trim();
    }
    String addr = req.getRemoteAddr();
    return addr != null && !addr.isBlank() ? addr : "unknown";
  }
}
