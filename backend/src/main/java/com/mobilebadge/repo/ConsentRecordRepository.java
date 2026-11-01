package com.mobilebadge.repo;

import com.mobilebadge.domain.ConsentRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {
  List<ConsentRecord> findByUserIdOrderByGrantedAtDesc(Long userId);
}
