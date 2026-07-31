package com.otboo.domain.directmessage.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.otboo.domain.directmessage.dto.request.DirectMessageCreateRequest;
import com.otboo.domain.directmessage.dto.response.DirectMessageDto;
import com.otboo.domain.directmessage.service.DirectMessageService;
import com.otboo.domain.directmessage.support.DirectMessageKeyGenerator;
import com.otboo.domain.follow.dto.response.UserSummary;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class DirectMessageWebSocketControllerTest {

  @Mock
  private SimpMessagingTemplate messagingTemplate;

  @Mock
  private DirectMessageService directMessageService;

  @InjectMocks
  private DirectMessageWebSocketController directMessageWebSocketController;

  @Test
  @DisplayName("WebSocket DM 전송 성공 - 저장 후 구독 주소로 메시지를 발행한다")
  void sendMessage_success() {
    UUID senderId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();

    DirectMessageCreateRequest request = new DirectMessageCreateRequest(
        receiverId,
        senderId,
        "안녕하세요"
    );

    DirectMessageDto response = new DirectMessageDto(
        messageId,
        Instant.now(),
        new UserSummary(senderId, "sender", null),
        new UserSummary(receiverId, "receiver", null),
        "안녕하세요"
    );

    Authentication authentication =
        new UsernamePasswordAuthenticationToken(senderId, null);

    given(directMessageService.createDirectMessage(request, senderId))
        .willReturn(response);

    directMessageWebSocketController.sendMessage(request, authentication);

    String dmKey = DirectMessageKeyGenerator.generate(senderId, receiverId);

    verify(directMessageService).createDirectMessage(request, senderId);
    verify(messagingTemplate).convertAndSend(
        "/sub/direct-messages_" + dmKey,
        response
    );
  }
}