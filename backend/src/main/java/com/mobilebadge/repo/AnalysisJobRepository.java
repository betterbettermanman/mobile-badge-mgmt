package com.mobilebadge.repo;

import com.mobilebadge.domain.AnalysisJob;
import com.mobilebadge.domain.JobStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {

  Optional<AnalysisJob> findBySessionId(Long sessionId);

  Optional<AnalysisJob> findByVendorTaskId(String vendorTaskId);

  List<AnalysisJob> findByStatusIn(Collection<JobStatus> statuses);
}
