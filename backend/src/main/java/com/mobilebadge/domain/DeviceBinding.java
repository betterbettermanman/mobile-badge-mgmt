package com.mobilebadge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "mb_device_binding")
public class DeviceBinding {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long organizationId;

  @Column(nullable = false)
  public Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  public DeviceSource source;

  @Column(nullable = false, length = 80)
  public String displayName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  public DeviceBindStatus status = DeviceBindStatus.bound;

  public Instant lastActiveAt;

  @Column(nullable = false)
  public Instant createdAt = Instant.now();
}
