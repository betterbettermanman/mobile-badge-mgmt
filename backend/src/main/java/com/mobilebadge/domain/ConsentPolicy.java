package com.mobilebadge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mb_consent_policy")
public class ConsentPolicy {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  /** 一期锁定：无有效授权禁止开录。不提供「仅提醒」模式。 */
  @Column(nullable = false)
  public boolean blockWithoutConsent = true;

  @Column(nullable = false)
  public int validityDays = 30;

  /** H-12：一期不强制每次会话重新确认。 */
  @Column(nullable = false)
  public boolean requirePerSession = false;
}
