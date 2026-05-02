package com.longfeng.common.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.longfeng.common.context.UserContextHolder.Scope;
import com.longfeng.common.context.UserContextHolder.UserContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UserContextHolder} · TDD §3.1 / plan §5.S0 BE-01.
 *
 * <p>Covers:
 * <ul>
 *   <li>Three scope states (USER / OBSERVER / GUEST)</li>
 *   <li>ThreadLocal isolation between concurrent threads</li>
 *   <li>InheritableThreadLocal parent → child propagation</li>
 *   <li>Explicit clear() — no leakage after clear</li>
 *   <li>UserContextTaskDecorator propagation to managed thread pools</li>
 * </ul>
 */
class UserContextHolderTest {

  @AfterEach
  void cleanup() {
    UserContextHolder.clear();
  }

  // ── Scope state tests ────────────────────────────────────────────────

  @Test
  void setUser_setsUserScope() {
    UserContextHolder.setUser("u-001", "tenant-1");

    UserContext ctx = UserContextHolder.get();
    assertThat(ctx).isNotNull();
    assertThat(ctx.userId()).isEqualTo("u-001");
    assertThat(ctx.tenantId()).isEqualTo("tenant-1");
    assertThat(ctx.scope()).isEqualTo(Scope.USER);
    assertThat(ctx.isUser()).isTrue();
    assertThat(ctx.isObserver()).isFalse();
    assertThat(ctx.isGuest()).isFalse();
  }

  @Test
  void setObserver_setsObserverScope() {
    UserContextHolder.setObserver("obs-007", "tenant-1");

    UserContext ctx = UserContextHolder.get();
    assertThat(ctx).isNotNull();
    assertThat(ctx.scope()).isEqualTo(Scope.OBSERVER);
    assertThat(ctx.userId()).isEqualTo("obs-007");
    assertThat(ctx.isObserver()).isTrue();
    assertThat(ctx.isUser()).isFalse();
    assertThat(ctx.isGuest()).isFalse();
  }

  @Test
  void setGuest_setsGuestScope() {
    UserContextHolder.setGuest("gs-abc123", "tenant-1");

    UserContext ctx = UserContextHolder.get();
    assertThat(ctx).isNotNull();
    assertThat(ctx.scope()).isEqualTo(Scope.GUEST);
    assertThat(ctx.userId()).isEqualTo("gs-abc123");
    assertThat(ctx.isGuest()).isTrue();
    assertThat(ctx.isUser()).isFalse();
    assertThat(ctx.isObserver()).isFalse();
  }

  @Test
  void set_withFullContext_allFieldsAvailable() {
    UserContext full = new UserContext("u-999", "t-1", Scope.USER);
    UserContextHolder.set(full);

    assertThat(UserContextHolder.currentUserId()).isEqualTo("u-999");
    assertThat(UserContextHolder.currentTenantId()).isEqualTo("t-1");
    assertThat(UserContextHolder.currentScope()).isEqualTo(Scope.USER);
  }

  @Test
  void get_returnsNull_whenNotSet() {
    assertThat(UserContextHolder.get()).isNull();
    assertThat(UserContextHolder.currentUserId()).isNull();
    assertThat(UserContextHolder.currentTenantId()).isNull();
    assertThat(UserContextHolder.currentScope()).isNull();
  }

  // ── ThreadLocal isolation ───────────────────���─────────────────────────

  @Test
  void contextIsIsolatedBetweenThreads() throws Exception {
    UserContextHolder.setUser("main-thread-user", "t-1");

    AtomicReference<UserContext> threadContext = new AtomicReference<>();
    Thread worker = new Thread(() -> {
      // Different thread: should see null (no inheritance when InheritableThreadLocal copies
      // to a newly spawned thread AFTER parent clears — but here we test isolation after set
      UserContextHolder.setUser("worker-thread-user", "t-2");
      threadContext.set(UserContextHolder.get());
      UserContextHolder.clear();
    });
    worker.start();
    worker.join(2_000);

    // Main thread context unchanged
    assertThat(UserContextHolder.get().userId()).isEqualTo("main-thread-user");
    // Worker thread had its own context
    assertThat(threadContext.get().userId()).isEqualTo("worker-thread-user");
  }

  // ── InheritableThreadLocal propagation (parent → child) ──────────────

  @Test
  void inheritableThreadLocal_propagatesFromParentToChild() throws Exception {
    UserContextHolder.setUser("parent-user", "tenant-inherit");

    AtomicReference<UserContext> childContext = new AtomicReference<>();
    // Spawn AFTER context is set — InheritableThreadLocal copies the parent context
    Thread child = new Thread(() -> childContext.set(UserContextHolder.get()));
    child.start();
    child.join(2_000);

    assertThat(childContext.get()).isNotNull();
    assertThat(childContext.get().userId()).isEqualTo("parent-user");
    assertThat(childContext.get().scope()).isEqualTo(Scope.USER);
  }

  @Test
  void inheritableThreadLocal_childChangeDoesNotAffectParent() throws Exception {
    UserContextHolder.setUser("parent-stable", "t-1");

    CountDownLatch childModified = new CountDownLatch(1);
    CountDownLatch parentChecked = new CountDownLatch(1);

    Thread child = new Thread(() -> {
      // Overwrite in child thread — should NOT affect parent
      UserContextHolder.setUser("child-overwrite", "t-1");
      childModified.countDown();
      try {
        parentChecked.await(2, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });
    child.start();
    childModified.await(2, TimeUnit.SECONDS);

    // Parent context remains unchanged
    assertThat(UserContextHolder.currentUserId()).isEqualTo("parent-stable");
    parentChecked.countDown();
    child.join(2_000);
  }

  // ── ThreadLocal cleanup ──────────────────────────────────��────────────

  @Test
  void clear_removesContext() {
    UserContextHolder.setUser("to-clear", "t-1");
    assertThat(UserContextHolder.get()).isNotNull();

    UserContextHolder.clear();

    assertThat(UserContextHolder.get()).isNull();
    assertThat(UserContextHolder.currentUserId()).isNull();
  }

  @Test
  void clear_isIdempotent() {
    UserContextHolder.clear();
    UserContextHolder.clear(); // Should not throw
    assertThat(UserContextHolder.get()).isNull();
  }

  @Test
  void clear_preventsThreadPoolLeakage() throws Exception {
    int threadCount = 5;
    ExecutorService pool = Executors.newFixedThreadPool(threadCount);
    List<Future<UserContext>> futures = new ArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      final int idx = i;
      futures.add(pool.submit(() -> {
        try {
          UserContextHolder.setUser("user-" + idx, "tenant-" + idx);
          return UserContextHolder.get();
        } finally {
          UserContextHolder.clear(); // Simulate filter finally block
        }
      }));
    }

    // All tasks completed with correct contexts (no cross-thread leakage)
    for (int i = 0; i < threadCount; i++) {
      UserContext ctx = futures.get(i).get(5, TimeUnit.SECONDS);
      assertThat(ctx).isNotNull();
      assertThat(ctx.userId()).isEqualTo("user-" + i);
    }

    pool.shutdown();
    assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
  }

  // ── UserContext record validation ─────────────────────��───────────────

  @Test
  void userContext_requiresNonNullScope() {
    assertThatThrownBy(() -> new UserContext("u", "t", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("scope");
  }

  // ── UserContextTaskDecorator ──────────────────────────────────────────

  @Test
  void taskDecorator_propagatesContextToManagedPool() throws Exception {
    UserContextHolder.setUser("decorator-user", "t-1");

    UserContextTaskDecorator decorator = new UserContextTaskDecorator();
    AtomicReference<UserContext> capturedContext = new AtomicReference<>();

    Runnable decorated = decorator.decorate(() -> capturedContext.set(UserContextHolder.get()));

    Thread managedThread = new Thread(decorated);
    managedThread.start();
    managedThread.join(2_000);

    assertThat(capturedContext.get()).isNotNull();
    assertThat(capturedContext.get().userId()).isEqualTo("decorator-user");
    assertThat(capturedContext.get().scope()).isEqualTo(Scope.USER);
  }

  @Test
  void taskDecorator_clearsContextAfterTask() throws Exception {
    UserContextHolder.setUser("pre-task-user", "t-1");

    UserContextTaskDecorator decorator = new UserContextTaskDecorator();
    AtomicReference<UserContext> afterTaskContext = new AtomicReference<>();

    Runnable decorated = decorator.decorate(() -> {
      // Task body — no-op
    });

    Thread managedThread = new Thread(() -> {
      decorated.run();
      // After task completes, decorator should have cleared the context
      afterTaskContext.set(UserContextHolder.get());
    });
    managedThread.start();
    managedThread.join(2_000);

    // Context cleared after task in the worker thread
    assertThat(afterTaskContext.get()).isNull();
  }

  @Test
  void taskDecorator_handlesNullContext() throws Exception {
    // No context set in parent
    UserContextHolder.clear();

    UserContextTaskDecorator decorator = new UserContextTaskDecorator();
    AtomicReference<UserContext> capturedContext = new AtomicReference<>();

    Runnable decorated = decorator.decorate(() -> capturedContext.set(UserContextHolder.get()));
    Thread thread = new Thread(decorated);
    thread.start();
    thread.join(2_000);

    // No context captured in task — null is acceptable
    assertThat(capturedContext.get()).isNull();
  }
}
