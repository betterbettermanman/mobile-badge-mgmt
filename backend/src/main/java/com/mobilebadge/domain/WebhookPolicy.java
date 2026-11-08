package com.mobilebadge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mb_webhook_policy")
public class WebhookPolicy {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, unique = true)
  public Long organizationId;

  /** 逗号分隔的域名或 URL 前缀。空 = 不出站。 */
  @Column(nullable = false, length = 2000)
  public String allowlist = "";

  @Column(nullable = false)
  public boolean outboundEnabled;
}
