package com.longfeng.common.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注测试方法覆盖的 Acceptance Criteria matrix 行 · v1.8 § 1.5 约束 #13.
 *
 * <p>格式：{@code SC-XX.AC-Y#<category>.<index>} · 例：{@code "SC-07.AC-2#happy_path.0"}.
 *
 * <p>check-ac-coverage.sh {@code --tests} AST 扫此注解 · 核对与 business-analysis.yml
 * verification_matrix 行数齐活（机械等式：matrix 行数 == @CoversAC 方法数）。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Repeatable(CoversACGroup.class)
public @interface CoversAC {
  String value();
}
