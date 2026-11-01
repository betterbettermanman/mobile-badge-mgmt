package com.mobilebadge.repo;

import com.mobilebadge.domain.Transcript;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TranscriptRepository extends JpaRepository<Transcript, Long> {
  Optional<Transcript> findBySessionId(Long sessionId);
}
