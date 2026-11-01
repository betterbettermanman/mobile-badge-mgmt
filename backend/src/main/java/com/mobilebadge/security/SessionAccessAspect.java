package com.mobilebadge.security;

import com.mobilebadge.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SessionAccessAspect {

  private final SessionAccessService access;

  public SessionAccessAspect(SessionAccessService access) {
    this.access = access;
  }

  @Before("@annotation(rule)")
  public void enforce(SessionAccess rule) {
    ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
    HttpServletRequest request = attributes.getRequest();
    Object raw = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (!(raw instanceof Map<?, ?> vars) || vars.get(rule.idPath()) == null) {
      throw ApiException.badRequest("VALIDATION", "缺少会话编号");
    }
    long id;
    try {
      id = Long.parseLong(String.valueOf(vars.get(rule.idPath())));
    } catch (NumberFormatException ex) {
      throw ApiException.badRequest("VALIDATION", "会话编号不正确");
    }
    access.require(id, CurrentUser.require(), rule.value());
  }
}
