package com.longfeng.common.context;

import org.springframework.core.task.TaskDecorator;

/**
 * Spring {@link TaskDecorator} that propagates {@link UserContextHolder} across managed thread
 * pool boundaries (e.g. {@code @Async} with a custom executor, Spring Batch step executor).
 *
 * <p>Register this decorator on any {@code ThreadPoolTaskExecutor} that executes business tasks:
 *
 * <pre>{@code
 *   @Bean
 *   public ThreadPoolTaskExecutor asyncExecutor() {
 *     ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
 *     exec.setTaskDecorator(new UserContextTaskDecorator());
 *     // ...
 *     return exec;
 *   }
 * }</pre>
 *
 * <p>Note: for child threads spawned directly (not via a managed executor), the
 * {@link InheritableThreadLocal} inside {@link UserContextHolder} already handles propagation
 * automatically.
 */
public class UserContextTaskDecorator implements TaskDecorator {

  @Override
  public Runnable decorate(Runnable runnable) {
    UserContextHolder.UserContext captured = UserContextHolder.get();
    return () -> {
      try {
        if (captured != null) {
          UserContextHolder.set(captured);
        }
        runnable.run();
      } finally {
        UserContextHolder.clear();
      }
    };
  }
}
