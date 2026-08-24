package com.otboo.domain.profile.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileDeletionOutboxRepository extends JpaRepository<FileDeletionOutbox, UUID> {
  List<FileDeletionOutbox> findTop50ByStatusOrderByCreatedAtAsc(FileDeletionStatus status);
}