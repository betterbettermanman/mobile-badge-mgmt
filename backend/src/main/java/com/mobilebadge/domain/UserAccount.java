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
@Table(name = "mb_user")
public class UserAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long organizationId;

  public Long storeId;

  @Column(nullable = false, unique = true, length = 64)
  public String account;

  @Column(nullable = false, length = 64)
  public String name;

  @Column(nullable = false, length = 100)
  public String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "user_role", nullable = false, length = 32)
  public Role role;

  @Column(nullable = false)
  public boolean enabled = true;

  @Column(nullable = false)
  public Instant createdAt = Instant.now();
}
