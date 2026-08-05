package com.otboo.domain.directmessage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;

import com.otboo.domain.directmessage.dto.response.DirectMessageDtoCursorResponse;
import com.otboo.domain.directmessage.exception.DirectMessageForbiddenException;
import com.otboo.domain.directmessage.exception.DirectMessageInvalidUserException;
import com.otboo.domain.directmessage.exception.DirectMessageUserNotFoundException;
import com.otboo.domain.directmessage.exception.InvalidDirectMessageCursorException;
import com.otboo.domain.directmessage.exception.SelfDirectMessageNotAllowedException;
import com.otboo.domain.directmessage.dto.request.DirectMessageCreateRequest;
import com.otboo.domain.directmessage.dto.response.DirectMessageDto;
import com.otboo.domain.directmessage.entity.DirectMessage;
import com.otboo.domain.directmessage.mapper.DirectMessageMapper;
import com.otboo.domain.directmessage.repository.DirectMessageRepository;
import com.otboo.domain.directmessage.support.DirectMessageKeyGenerator;
import com.otboo.domain.notification.event.NotificationEvent;
import com.otboo.domain.user.dto.UserSummary;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DirectMessageServiceTest {

  @Mock
  private DirectMessageRepository directMessageRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @Mock
  private DirectMessageMapper directMessageMapper;

  @InjectMocks
  private DirectMessageService directMessageService;


  @Test
  @DisplayName("DM 생성 성공")
  void createDirectMessage_success() {
    UUID senderId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();

    User sender = User.create("sender@test.com", "sender", "password");
    User receiver = User.create("receiver@test.com", "receiver", "password");

    ReflectionTestUtils.setField(sender, "id", senderId);
    ReflectionTestUtils.setField(receiver, "id", receiverId);

    DirectMessageCreateRequest request = new DirectMessageCreateRequest(
        receiverId,
        senderId,
        "안녕하세요"
    );

    DirectMessageDto directMessageDto = new DirectMessageDto(
        UUID.randomUUID(),
        Instant.now(),
        new UserSummary(senderId, "sender", null),
        new UserSummary(receiverId, "receiver", null),
        "안녕하세요"
    );

    given(directMessageMapper.toDto(any(DirectMessage.class))).willReturn(directMessageDto);

    given(userRepository.findById(senderId)).willReturn(Optional.of(sender));
    given(userRepository.findById(receiverId)).willReturn(Optional.of(receiver));
    given(directMessageRepository.save(any(DirectMessage.class)))
        .willAnswer(invocation -> {
          DirectMessage directMessage = invocation.getArgument(0);
          ReflectionTestUtils.setField(directMessage, "id", UUID.randomUUID());
          return directMessage;
        });

    DirectMessageDto result = directMessageService.createDirectMessage(
        request,
        senderId
    );

    assertThat(result).isNotNull();
    assertThat(result.sender().userId()).isEqualTo(senderId);
    assertThat(result.receiver().userId()).isEqualTo(receiverId);
    assertThat(result.content()).isEqualTo("안녕하세요");

    verify(directMessageRepository).save(any(DirectMessage.class));
    verify(eventPublisher).publishEvent(any(NotificationEvent.class));
  }

  @Test
  @DisplayName("senderId와 현재 사용자 ID가 다를때 예외 테스트")
  void createDirectMessage_senderMismatch_throwsException() {
    UUID senderId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();

    DirectMessageCreateRequest request = new DirectMessageCreateRequest(
        receiverId,
        senderId,
        "안녕하세요"
    );

    assertThatThrownBy(() -> directMessageService.createDirectMessage(request, currentUserId))
        .isInstanceOf(DirectMessageForbiddenException.class);

    verify(directMessageRepository, never()).save(any());
  }

  @Test
  @DisplayName("자기 자신에게 DM을 보낼때 예외 테스트")
  void createDirectMessage_selfMessage_throwsException() {
    UUID userId = UUID.randomUUID();

    DirectMessageCreateRequest request = new DirectMessageCreateRequest(
        userId,
        userId,
        "안녕하세요"
    );

    assertThatThrownBy(() -> directMessageService.createDirectMessage(request, userId))
        .isInstanceOf(SelfDirectMessageNotAllowedException.class);

    verify(directMessageRepository, never()).save(any());
  }

  @Test
  @DisplayName("sender 사용자가 존재하지 않을때 예외 테스트")
  void createDirectMessage_senderNotFound_throwsException() {
    UUID senderId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();

    DirectMessageCreateRequest request = new DirectMessageCreateRequest(
        receiverId,
        senderId,
        "안녕하세요"
    );

    given(userRepository.findById(senderId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> directMessageService.createDirectMessage(request, senderId))
        .isInstanceOf(DirectMessageUserNotFoundException.class);

    verify(directMessageRepository, never()).save(any());
  }

  @Test
  @DisplayName("receiver 사용자가 존재하지 않을때 예외 테스트")
  void createDirectMessage_receiverNotFound_throwsException() {
    UUID senderId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();

    User sender = User.create("sender@test.com", "sender", "password");
    ReflectionTestUtils.setField(sender, "id", senderId);

    DirectMessageCreateRequest request = new DirectMessageCreateRequest(
        receiverId,
        senderId,
        "안녕하세요"
    );

    given(userRepository.findById(senderId)).willReturn(Optional.of(sender));
    given(userRepository.findById(receiverId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> directMessageService.createDirectMessage(request, senderId))
        .isInstanceOf(DirectMessageUserNotFoundException.class);

    verify(directMessageRepository, never()).save(any());
  }

  @Test
  @DisplayName("DM 목록 조회 성공")
  void getDirectMessages_success() {
    UUID currentUserId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();

    User currentUser = User.create("current@test.com", "current", "password");
    User targetUser = User.create("target@test.com", "target", "password");

    ReflectionTestUtils.setField(currentUser, "id", currentUserId);
    ReflectionTestUtils.setField(targetUser, "id", targetUserId);

    String dmKey = DirectMessageKeyGenerator.generate(currentUserId, targetUserId);

    DirectMessage message = DirectMessage.create(
        currentUser,
        targetUser,
        dmKey,
        "안녕하세요"
    );
    ReflectionTestUtils.setField(message, "id", UUID.randomUUID());

    DirectMessageDto directMessageDto = new DirectMessageDto(
        UUID.randomUUID(),
        Instant.now(),
        new UserSummary(currentUserId, "current", null),
        new UserSummary(targetUserId, "target", null),
        "안녕하세요"
    );

    given(directMessageMapper.toDto(any(DirectMessage.class))).willReturn(directMessageDto);
    given(userRepository.existsById(targetUserId)).willReturn(true);
    given(directMessageRepository.findDirectMessages(
        dmKey,
        null,
        null,
        21
    )).willReturn(List.of(message));
    given(directMessageRepository.countDirectMessages(dmKey)).willReturn(1L);

    DirectMessageDtoCursorResponse result = directMessageService.getDirectMessages(
        targetUserId,
        null,
        null,
        20,
        currentUserId
    );

    assertThat(result).isNotNull();
    assertThat(result.data()).hasSize(1);
    assertThat(result.data().get(0).content()).isEqualTo("안녕하세요");
    assertThat(result.hasNext()).isFalse();
    assertThat(result.totalCount()).isEqualTo(1L);
    assertThat(result.sortBy()).isEqualTo("createdAt");
    assertThat(result.sortDirection()).isEqualTo("DESCENDING");
  }

  @Test
  @DisplayName("자기 자신을 조회 시 예외 테스트")
  void getDirectMessages_selfUser_throwsException() {
    UUID userId = UUID.randomUUID();

    assertThatThrownBy(() -> directMessageService.getDirectMessages(
        userId,
        null,
        null,
        20,
        userId
    )).isInstanceOf(SelfDirectMessageNotAllowedException.class);

    verify(directMessageRepository, never()).findDirectMessages(any(), any(), any(), anyInt());
  }

  @Test
  @DisplayName("targetUserId가 존재하지 않을 시 예외 테스트")
  void getDirectMessages_targetUserNotFound_throwsException() {
    UUID currentUserId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();

    given(userRepository.existsById(targetUserId)).willReturn(false);

    assertThatThrownBy(() -> directMessageService.getDirectMessages(
        targetUserId,
        null,
        null,
        20,
        currentUserId
    )).isInstanceOf(DirectMessageInvalidUserException.class);

    verify(directMessageRepository, never()).findDirectMessages(any(), any(), any(), anyInt());
  }

  @Test
  @DisplayName("cursor만 있고 idAfter가 없을때 예외 테스트(둘다 있거나 둘다 없어야함)")
  void getDirectMessages_onlyCursor_throwsException() {
    UUID currentUserId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();

    assertThatThrownBy(() -> directMessageService.getDirectMessages(
        targetUserId,
        "2026-07-31T00:00:00Z",
        null,
        20,
        currentUserId
    )).isInstanceOf(InvalidDirectMessageCursorException.class);

    verify(directMessageRepository, never()).findDirectMessages(any(), any(), any(), anyInt());
  }

  @Test
  @DisplayName("idAfter만 있고 cursor가 없을때 예외 테스트(둘다 있거나 둘다 없어야함)")
  void getDirectMessages_onlyIdAfter_throwsException() {
    UUID currentUserId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();

    assertThatThrownBy(() -> directMessageService.getDirectMessages(
        targetUserId,
        null,
        UUID.randomUUID(),
        20,
        currentUserId
    )).isInstanceOf(InvalidDirectMessageCursorException.class);

    verify(directMessageRepository, never()).findDirectMessages(any(), any(), any(), anyInt());
  }
}
