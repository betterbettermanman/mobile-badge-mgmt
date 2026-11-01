package com.mobilebadge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 对象存储元数据。storageKey 只关联 sessionId 相对路径，不存绝对磁盘路径。 */
@Entity
@Table(name = "mb_audio_object")
public class AudioObject {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long sessionId;

  @Column(nullable = false, length = 300)
  public String storageKey;

  @Column(nullable = false, length = 200)
  public String originalFilename;

  @Column(nullable = false)
  public long sizeBytes;

  @Column(length = 120)
  public String contentType;

  @Column(nullable = false)
  public Instant createdAt = Instant.now();
}
