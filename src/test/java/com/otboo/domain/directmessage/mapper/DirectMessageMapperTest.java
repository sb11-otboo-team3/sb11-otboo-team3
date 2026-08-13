package com.otboo.domain.directmessage.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.otboo.domain.directmessage.dto.response.DirectMessageDto;
import com.otboo.domain.directmessage.entity.DirectMessage;
import com.otboo.domain.user.dto.UserSummary;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.mapper.UserSummaryMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DirectMessageMapperTest {

  @Mock
  private UserSummaryMapper userSummaryMapper;

  @InjectMocks
  private DirectMessageMapper directMessageMapper;

  @Test
  @DisplayName("DM 단건 DTO 변환 성공 테스트")
  void toDto_success(){
    UUID senderId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();
    UUID directMessageId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-08-13T01:00:00Z");

    User sender = User.create("sender@test.com", "sender", "password");
    User receiver = User.create("receiver@test.com", "receiver", "password");
    ReflectionTestUtils.setField(sender, "id", senderId);
    ReflectionTestUtils.setField(receiver, "id", receiverId);

    DirectMessage directMessage = DirectMessage.create(sender, receiver, "dm-key", "hello");
    ReflectionTestUtils.setField(directMessage, "id", directMessageId);
    ReflectionTestUtils.setField(directMessage, "createdAt", createdAt);

    UserSummary senderSummary = new UserSummary(senderId, "sender", "sender-image");
    UserSummary receiverSummary = new UserSummary(receiverId, "receiver", "receiver-image");

    given(userSummaryMapper.toUserSummary(sender)).willReturn(senderSummary);
    given(userSummaryMapper.toUserSummary(receiver)).willReturn(receiverSummary);

    DirectMessageDto result = directMessageMapper.toDto(directMessage);

    assertThat(result.id()).isEqualTo(directMessageId);
    assertThat(result.createdAt()).isEqualTo(createdAt);
    assertThat(result.sender()).isEqualTo(senderSummary);
    assertThat(result.receiver()).isEqualTo(receiverSummary);
    assertThat(result.content()).isEqualTo("hello");

    verify(userSummaryMapper).toUserSummary(sender);
    verify(userSummaryMapper).toUserSummary(receiver);
  }

  @Test
  @DisplayName("DM 목록 DTO 변환 시 사용자 요약을 벌크 조회한다")
  void toDtos_success() {
    UUID senderId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();

    User sender = User.create("sender@test.com", "sender", "password");
    User receiver = User.create("receiver@test.com", "receiver", "password");
    ReflectionTestUtils.setField(sender, "id", senderId);
    ReflectionTestUtils.setField(receiver, "id", receiverId);

    DirectMessage firstMessage = DirectMessage.create(sender, receiver, "dm-key", "first");
    DirectMessage secondMessage = DirectMessage.create(receiver, sender, "dm-key", "second");

    UserSummary senderSummary = new UserSummary(senderId, "sender", "sender-image");
    UserSummary receiverSummary = new UserSummary(receiverId, "receiver", "receiver-image");

    given(userSummaryMapper.toUserSummaries(argThat(userIds ->
        userIds.size() == 2
            && userIds.contains(senderId)
            && userIds.contains(receiverId)
    ))).willReturn(List.of(senderSummary, receiverSummary));

    List<DirectMessageDto> result =
        directMessageMapper.toDtos(List.of(firstMessage, secondMessage));

    assertThat(result).hasSize(2);

    assertThat(result.get(0).sender()).isEqualTo(senderSummary);
    assertThat(result.get(0).receiver()).isEqualTo(receiverSummary);
    assertThat(result.get(0).content()).isEqualTo("first");

    assertThat(result.get(1).sender()).isEqualTo(receiverSummary);
    assertThat(result.get(1).receiver()).isEqualTo(senderSummary);
    assertThat(result.get(1).content()).isEqualTo("second");

    verify(userSummaryMapper).toUserSummaries(argThat(userIds ->
        userIds.size() == 2
            && userIds.contains(senderId)
            && userIds.contains(receiverId)
    ));
  }

  @Test
  @DisplayName("DM 단건 DTO 변환 시 삭제된 참여자는 null로 매핑한다")
  void toDto_withDeletedParticipant_success() {
    UUID receiverId = UUID.randomUUID();

    User receiver = User.create("receiver@test.com", "receiver", "password");
    ReflectionTestUtils.setField(receiver, "id", receiverId);

    DirectMessage directMessage = DirectMessage.create(receiver, receiver, "dm-key", "hello");
    ReflectionTestUtils.setField(directMessage, "sender", null);

    UserSummary receiverSummary = new UserSummary(receiverId, "receiver", "receiver-image");

    given(userSummaryMapper.toUserSummary(null)).willReturn(null);
    given(userSummaryMapper.toUserSummary(receiver)).willReturn(receiverSummary);

    DirectMessageDto result = directMessageMapper.toDto(directMessage);

    assertThat(result.sender()).isNull();
    assertThat(result.receiver()).isEqualTo(receiverSummary);
    assertThat(result.content()).isEqualTo("hello");
  }

  @Test
  @DisplayName("DM 목록 DTO 변환 시 삭제된 참여자는 조회 ID에서 제외하고 null로 매핑한다")
  void toDtos_withDeletedParticipant_success() {
    UUID userId = UUID.randomUUID();

    User user = User.create("user@test.com", "user", "password");
    ReflectionTestUtils.setField(user, "id", userId);

    DirectMessage firstMessage = DirectMessage.create(user, user, "dm-key", "first");
    ReflectionTestUtils.setField(firstMessage, "sender", null);

    DirectMessage secondMessage = DirectMessage.create(user, user, "dm-key", "second");
    ReflectionTestUtils.setField(secondMessage, "receiver", null);

    UserSummary userSummary = new UserSummary(userId, "user", "user-image");

    given(userSummaryMapper.toUserSummaries(argThat(userIds ->
        userIds.size() == 1 && userIds.contains(userId)
    ))).willReturn(List.of(userSummary));

    List<DirectMessageDto> result =
        directMessageMapper.toDtos(List.of(firstMessage, secondMessage));

    assertThat(result).hasSize(2);

    assertThat(result.get(0).sender()).isNull();
    assertThat(result.get(0).receiver()).isEqualTo(userSummary);

    assertThat(result.get(1).sender()).isEqualTo(userSummary);
    assertThat(result.get(1).receiver()).isNull();

    verify(userSummaryMapper).toUserSummaries(argThat(userIds ->
        userIds.size() == 1 && userIds.contains(userId)
    ));
  }
}
