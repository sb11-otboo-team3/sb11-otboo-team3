package com.otboo.domain.notification.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.notification.dto.response.NotificationDto;
import com.otboo.domain.notification.dto.response.NotificationDtoCursorResponse;
import com.otboo.domain.notification.entity.NotificationLevel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationDtoTest {

  @Test
  @DisplayName("알림 응답 DTO 생성")
  void notificationDto_success() {
    UUID notificationId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-08-13T01:00:00Z");

    NotificationDto dto = new NotificationDto(
        notificationId,
        createdAt,
        receiverId,
        "알림 제목",
        "알림 내용",
        NotificationLevel.INFO
    );

    assertThat(dto.id()).isEqualTo(notificationId);
    assertThat(dto.createdAt()).isEqualTo(createdAt);
    assertThat(dto.receiverId()).isEqualTo(receiverId);
    assertThat(dto.title()).isEqualTo("알림 제목");
    assertThat(dto.content()).isEqualTo("알림 내용");
    assertThat(dto.level()).isEqualTo(NotificationLevel.INFO);
  }

  @Test
  @DisplayName("알림 커서 응답 DTO 생성")
  void notificationDtoCursorResponse_success() {
    UUID nextIdAfter = UUID.randomUUID();

    NotificationDtoCursorResponse response = new NotificationDtoCursorResponse(
        List.of(),
        "2026-08-13T01:00:00Z",
        nextIdAfter,
        false,
        0L,
        "createdAt",
        "DESCENDING"
    );

    assertThat(response.data()).isEmpty();
    assertThat(response.nextCursor()).isEqualTo("2026-08-13T01:00:00Z");
    assertThat(response.nextIdAfter()).isEqualTo(nextIdAfter);
    assertThat(response.hasNext()).isFalse();
    assertThat(response.totalCount()).isZero();
    assertThat(response.sortBy()).isEqualTo("createdAt");
    assertThat(response.sortDirection()).isEqualTo("DESCENDING");
  }
}