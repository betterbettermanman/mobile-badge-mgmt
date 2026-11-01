package com.mobilebadge.repo;

import com.mobilebadge.domain.CaptureSession;
import com.mobilebadge.domain.CaptureStatus;
import com.mobilebadge.domain.DeviceSource;
import com.mobilebadge.domain.TranscriptStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaptureSessionRepository extends JpaRepository<CaptureSession, Long> {

  @Query("""
      select s from CaptureSession s
      where s.ownerUserId = :owner
        and (:source is null or s.source = :source)
        and (:transcriptStatus is null or s.transcriptStatus = :transcriptStatus)
        and (:captureStatus is null or s.captureStatus = :captureStatus)
      order by s.createdAt desc
      """)
  Page<CaptureSession> searchOwn(
      @Param("owner") Long owner,
      @Param("source") DeviceSource source,
      @Param("transcriptStatus") TranscriptStatus transcriptStatus,
      @Param("captureStatus") CaptureStatus captureStatus,
      Pageable pageable);

  @Query("""
      select s from CaptureSession s
      where s.organizationId = :orgId
        and (:source is null or s.source = :source)
        and (:transcriptStatus is null or s.transcriptStatus = :transcriptStatus)
        and (:captureStatus is null or s.captureStatus = :captureStatus)
      order by s.createdAt desc
      """)
  Page<CaptureSession> searchOrg(
      @Param("orgId") Long orgId,
      @Param("source") DeviceSource source,
      @Param("transcriptStatus") TranscriptStatus transcriptStatus,
      @Param("captureStatus") CaptureStatus captureStatus,
      Pageable pageable);

  long countByOrganizationIdAndCreatedAtGreaterThanEqual(Long organizationId, Instant createdAt);

  long countByOrganizationIdAndTranscriptStatus(Long organizationId, TranscriptStatus transcriptStatus);

  List<CaptureSession> findByOrganizationId(Long organizationId);

  List<CaptureSession> findByIdIn(Collection<Long> ids);
}
