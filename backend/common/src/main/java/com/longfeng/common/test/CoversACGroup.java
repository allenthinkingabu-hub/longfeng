package com.longfeng.common.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 支持一个测试方法重复 {@link CoversAC}（跨 AC 覆盖场景 · 较少见）。 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface CoversACGroup {
  CoversAC[] value();
}
