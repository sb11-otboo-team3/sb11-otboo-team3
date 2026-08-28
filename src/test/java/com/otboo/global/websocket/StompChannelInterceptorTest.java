package com.otboo.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.otboo.domain.auth.jwt.JwtProvider;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StompChannelInterceptorTest {

  @Mock
  private JwtProvider jwtProvider;

  @Mock
  private UserRepository userRepository;

  @Mock
  private MessageChannel messageChannel;

  @InjectMocks
  private StompChannelInterceptor interceptor;

  @Test
  @DisplayName("WebSocket CONNECT 성공 시 테스트")
  void preSend_connectSuccess_setsAuthentication() {
    UUID userId = UUID.randomUUID();
    String token = "valid-token";

    User user = User.create("test@otboo.io", "tester", "encoded-password");
    ReflectionTestUtils.setField(user, "id", userId);

    given(jwtProvider.isValid(token)).willReturn(true);
    given(jwtProvider.getUserId(token)).willReturn(userId);
    given(jwtProvider.getTokenVersion(token)).willReturn(0L);
    given(userRepository.findById(userId)).willReturn(Optional.of(user));

    Message<byte[]> message = connectMessage("Bearer " + token);

    Message<?> result = interceptor.preSend(message, messageChannel);

    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);

    assertThat(accessor).isNotNull();
    assertThat(accessor.getUser()).isInstanceOf(Authentication.class);

    Authentication authentication = (Authentication) accessor.getUser();
    assertThat(authentication.getPrincipal()).isEqualTo(userId);
  }

  @Test
  @DisplayName("Authorization 헤더가 없을때 예외 발생 테스트")
  void preSend_missingAuthorization_throwsException() {
    Message<byte[]> message = connectMessage(null);

    assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("유효하지 않은 토큰일때 예외 발생 테스트")
  void preSend_invalidToken_throwsException() {
    String token = "invalid-token";
    Message<byte[]> message = connectMessage("Bearer " + token);

    given(jwtProvider.isValid(token)).willReturn(false);

    assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("사용자를 찾을 수 없을때 에외 발생 테스트")
  void preSend_userNotFound_throwsException() {
    UUID userId = UUID.randomUUID();
    String token = "valid-token";
    Message<byte[]> message = connectMessage("Bearer " + token);

    given(jwtProvider.isValid(token)).willReturn(true);
    given(jwtProvider.getUserId(token)).willReturn(userId);
    given(jwtProvider.getTokenVersion(token)).willReturn(0L);
    given(userRepository.findById(userId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("버전이 다른 토큰일때 예외 발생 테스트")
  void preSend_tokenVersionMismatch_throwsException() {
    UUID userId = UUID.randomUUID();
    String token = "valid-token";

    User user = User.create("test@otboo.io", "tester", "encoded-password");
    ReflectionTestUtils.setField(user, "id", userId);
    ReflectionTestUtils.setField(user, "tokenVersion", 1L);

    Message<byte[]> message = connectMessage("Bearer " + token);

    given(jwtProvider.isValid(token)).willReturn(true);
    given(jwtProvider.getUserId(token)).willReturn(userId);
    given(jwtProvider.getTokenVersion(token)).willReturn(0L);
    given(userRepository.findById(userId)).willReturn(Optional.of(user));

    assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("잠긴 계정일때 예외 발생 테스트")
  void preSend_lockedUser_throwsException() {
    UUID userId = UUID.randomUUID();
    String token = "valid-token";

    User user = User.create("test@otboo.io", "tester", "encoded-password");
    ReflectionTestUtils.setField(user, "id", userId);
    user.lock();

    Message<byte[]> message = connectMessage("Bearer " + token);

    given(jwtProvider.isValid(token)).willReturn(true);
    given(jwtProvider.getUserId(token)).willReturn(userId);
    given(jwtProvider.getTokenVersion(token)).willReturn(user.getTokenVersion());
    given(userRepository.findById(userId)).willReturn(Optional.of(user));

    assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
        .isInstanceOf(AccessDeniedException.class);
  }

  private Message<byte[]> connectMessage(String authorization) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);

    if (authorization != null) {
      accessor.setNativeHeader("Authorization", authorization);
    }

    accessor.setLeaveMutable(true);

    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }
}