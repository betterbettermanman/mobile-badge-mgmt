package com.mobilebadge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "mb_transcript")
public class Transcript {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, unique = true)
  public Long sessionId;

  @Column(length = 20000)
  public String fullText;

  @Column(length = 20000)
  public String sentencesJson;

  @Column(nullable = false)
  public Instant updatedAt = Instant.now();
}
