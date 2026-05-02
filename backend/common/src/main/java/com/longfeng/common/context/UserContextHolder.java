package com.longfeng.common.context;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ThreadLocal user context holder · TDD §3.1 common / plan §5.S0 BE-01.
 *
 * <p>Three scope states:
 * <ul>
 *   <li>{@link Scope#USER} — authenticated student (JWT claims present)</li>
 *   <li>{@link Scope#OBSERVER} — read-only parent/guardian token</li>
 *   <li>{@link Scope#GUEST} — anonymous guest session token</li>
 * </ul>
 *
 * <p><b>Thread propagation</b>: uses {@link InheritableThreadLocal} so child threads spawned by
 * {@code @Async} / {@link java.util.concurrent.ExecutorService} naturally inherit the parent
 * context. For managed thread pools (TaskExecutor) pair with
 * {@link UserContextTaskDecorator}.
 *
 * <p><b>Cleanup</b>: the web filter layer <em>must</em> call {@link #clear()} in its
 * {@code finally} block to prevent thread pool leakage.
 */
public final class UserContextHolder {

  private static final Logger log = LoggerFactory.getLogger(UserContextHolder.class);

  /** Scope discriminator for the current request / task. */
  public enum Scope {
    USER,
    OBSERVER,
    GUEST
  }

  /**
   * Immutable snapshot of all context fields for a single request.
   *
   * @param userId    string user / guest / observer identifier from JWT
   * @param tenantId  tenant identifier
   * @param scope     one of USER / OBSERVER / GUEST
   */
  public record UserContext(String userId, String tenantId, Scope scope) {

    public UserContext {
      Objects.requireNonNull(scope, "scope must not be null");
    }

    public boolean isUser() {
      return scope == Scope.USER;
    }

    public boolean isObserver() {
      return scope == Scope.OBSERVER;
    }

    public boolean isGuest() {
      return scope == Scope.GUEST;
    }
  }

  /**
   * InheritableThreadLocal so @Async child threads receive the parent request context automatically.
   * Callers that use managed executor pools should additionally wrap with
   * {@link UserContextTaskDecorator}.
   */
  private static final InheritableThreadLocal<UserContext> CONTEXT =
      new InheritableThreadLocal<>();

  private UserContextHolder() {}

  /**
   * Store the given context in the current thread (and any child threads spawned after this call).
   *
   * @param context non-null context snapshot
   */
  public static void set(UserContext context) {
    Objects.requireNonNull(context, "UserContext must not be null");
    CONTEXT.set(context);
  }

  /**
   * Convenience builder — sets USER scope context.
   *
   * @param userId   user identifier
   * @param tenantId tenant identifier
   */
  public static void setUser(String userId, String tenantId) {
    CONTEXT.set(new UserContext(userId, tenantId, Scope.USER));
  }

  /**
   * Convenience builder — sets OBSERVER scope context.
   *
   * @param observerId observer identifier (typically a sub from JWT)
   * @param tenantId   tenant identifier
   */
  public static void setObserver(String observerId, String tenantId) {
    CONTEXT.set(new UserContext(observerId, tenantId, Scope.OBSERVER));
  }

  /**
   * Convenience builder — sets GUEST scope context.
   *
   * @param guestId  guest session identifier
   * @param tenantId tenant identifier
   */
  public static void setGuest(String guestId, String tenantId) {
    CONTEXT.set(new UserContext(guestId, tenantId, Scope.GUEST));
  }

  /**
   * Returns the current context, or {@code null} if none is set.
   *
   * @return current {@link UserContext} or null
   */
  public static UserContext get() {
    return CONTEXT.get();
  }

  /**
   * Returns the current userId, or {@code null} if context is absent.
   *
   * @return userId string or null
   */
  public static String currentUserId() {
    UserContext ctx = CONTEXT.get();
    return ctx == null ? null : ctx.userId();
  }

  /**
   * Returns the current tenantId, or {@code null} if context is absent.
   *
   * @return tenantId string or null
   */
  public static String currentTenantId() {
    UserContext ctx = CONTEXT.get();
    return ctx == null ? null : ctx.tenantId();
  }

  /**
   * Returns the current scope, or {@code null} if context is absent.
   *
   * @return {@link Scope} or null
   */
  public static Scope currentScope() {
    UserContext ctx = CONTEXT.get();
    return ctx == null ? null : ctx.scope();
  }

  /**
   * Clears the context from the current thread. <b>Must</b> be called in a {@code finally} block
   * at the request/task boundary to prevent thread pool leakage.
   */
  public static void clear() {
    UserContext ctx = CONTEXT.get();
    if (ctx != null) {
      log.trace("clearing UserContext scope={} userId={}", ctx.scope(), ctx.userId());
    }
    CONTEXT.remove();
  }
}
