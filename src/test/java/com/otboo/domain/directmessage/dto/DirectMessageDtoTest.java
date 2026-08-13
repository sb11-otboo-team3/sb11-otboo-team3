package com.otboo.domain.directmessage.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.directmessage.dto.request.DirectMessageCreateRequest;
import com.otboo.domain.directmessage.dto.response.DirectMessageDto;
import com.otboo.domain.directmessage.dto.response.DirectMessageDtoCursorResponse;
import com.otboo.domain.user.dto.UserSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DirectMessageDtoTest {

  @Test
  @DisplayName("DM 생성 요청 DTO 생성")
  void directMessageCreateRequest_success() {
    UUID senderId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();

    DirectMessageCreateRequest request =
        new DirectMessageCreateRequest(receiverId, senderId, "hello");

    assertThat(request.receiverId()).isEqualTo(receiverId);
    assertThat(request.senderId()).isEqualTo(senderId);
    assertThat(request.content()).isEqualTo("hello");
  }

  @Test
  @DisplayName("DM 응답 DTO 생성")
  void directMessageDto_success() {
    UUID messageId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-08-13T01:00:00Z");
    UserSummary sender = new UserSummary(UUID.randomUUID(), "sender", null);
    UserSummary receiver = new UserSummary(UUID.randomUUID(), "receiver", null);

    DirectMessageDto dto = new DirectMessageDto(
        messageId,
        createdAt,
        sender,
        receiver,
        "hello"
    );

    assertThat(dto.id()).isEqualTo(messageId);
    assertThat(dto.createdAt()).isEqualTo(createdAt);
    assertThat(dto.sender()).isEqualTo(sender);
    assertThat(dto.receiver()).isEqualTo(receiver);
    assertThat(dto.content()).isEqualTo("hello");
  }

  @Test
  @DisplayName("DM 커서 응답 DTO 생성")
  void directMessageDtoCursorResponse_success() {
    UUID nextIdAfter = UUID.randomUUID();

    DirectMessageDtoCursorResponse response = new DirectMessageDtoCursorResponse(
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