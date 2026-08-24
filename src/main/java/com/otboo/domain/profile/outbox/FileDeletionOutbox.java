package com.otboo.domain.profile.outbox;

import com.otboo.global.common.entity.UpdatableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "file_deletion_outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileDeletionOutbox extends UpdatableEntity {

  @Column(name = "object_key", nullable = false)
  private String objectKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private FileDeletionStatus status;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "last_error_message")
  private String lastErrorMessage;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  private FileDeletionOutbox(String objectKey) {
    this.objectKey = objectKey;
    this.status = FileDeletionStatus.PENDING;
    this.retryCount = 0;
  }

  public static FileDeletionOutbox create(String objectKey) {
    return new FileDeletionOutbox(objectKey);
  }

  public void recordFailure(String errorMessage) {
    this.retryCount++;
    this.lastErrorMessage = errorMessage;
  }

  public void markSucceeded() {
    this.status = FileDeletionStatus.SUCCEEDED;
    this.deletedAt = Instant.now();
  }

  // 과도한 재시도를 방지하기 위한 상한. 이 이상 실패하면 스케줄러가
  // 더 이상 자동 재시도하지 않고, 수동 확인이 필요한 상태로 남긴다.
  public boolean exceededMaxAttempts(int maxAttempts) {
    return this.retryCount >= maxAttempts;
  }

  public void markFailedPermanently() {
    this.status = FileDeletionStatus.FAILED;
  }
}