package com.otboo.global.websocket;

import com.otboo.domain.auth.jwt.JwtProvider;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompChannelInterceptor implements ChannelInterceptor {

  private final JwtProvider jwtProvider;
  private final UserRepository userRepository;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

    if (accessor == null || accessor.getCommand() == null) {
      return message;
    }

    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
      String authorization = accessor.getFirstNativeHeader("Authorization");

      if (authorization == null || !authorization.startsWith("Bearer ")) {
        throw new AccessDeniedException("WebSocket 인증 토큰이 필요합니다.");
      }

      // Bearer 제거, 다음부분 가져오기
      String token = authorization.substring(7);

      if (!jwtProvider.isValid(token)) {
        throw new AccessDeniedException("WebSocket 인증 토큰이 유효하지 않습니다.");
      }

      UUID userId = jwtProvider.getUserId(token);
      long tokenVersion = jwtProvider.getTokenVersion(token);

      User user = userRepository.findById(userId)
          .orElseThrow(() -> new AccessDeniedException("WebSocket 인증 사용자를 찾을 수 없습니다."));

      if (user.getTokenVersion() != tokenVersion) {
        throw new AccessDeniedException("WebSocket 인증 토큰이 만료되었습니다.");
      }

      if (user.isLocked()) {
        throw new AccessDeniedException("잠긴 계정은 WebSocket에 연결할 수 없습니다.");
      }

      accessor.setUser(new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));
    }

    if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
      String destination = accessor.getDestination();

      if (destination != null && destination.startsWith("/sub/direct-messages_")) {
        if (!(accessor.getUser() instanceof Authentication authentication)) {
          throw new AccessDeniedException("WebSocket 인증 정보가 없습니다.");
        }

        UUID currentUserId = (UUID) authentication.getPrincipal();
        String dmKey = destination.substring("/sub/direct-messages_".length());

        if (!dmKey.contains(currentUserId.toString())) {
          throw new AccessDeniedException("해당 DM 채널을 구독할 권한이 없습니다.");
        }
      }
    }

    return message;
  }
}
