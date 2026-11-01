package com.mobilebadge.repo;

import com.mobilebadge.domain.AudioObject;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AudioObjectRepository extends JpaRepository<AudioObject, Long> {
  Optional<AudioObject> findFirstBySessionIdOrderByIdDesc(Long sessionId);
}
