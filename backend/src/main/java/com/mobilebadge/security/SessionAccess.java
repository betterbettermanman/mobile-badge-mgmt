package com.mobilebadge.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 会话资源的越权拦截。切面在进入控制器前按路径变量校验所有者或组织读权限。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SessionAccess {
  SessionAccessMode value();

  String idPath() default "id";
}
