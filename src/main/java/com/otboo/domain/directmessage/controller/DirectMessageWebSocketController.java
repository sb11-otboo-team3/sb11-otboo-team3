package com.otboo.domain.directmessage.controller;

import com.otboo.domain.directmessage.dto.request.DirectMessageCreateRequest;
import com.otboo.domain.directmessage.dto.response.DirectMessageDto;
import com.otboo.domain.directmessage.service.DirectMessageService;
import com.otboo.domain.directmessage.support.DirectMessageKeyGenerator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class DirectMessageWebSocketController {

  private final SimpMessagingTemplate messagingTemplate;
  private final DirectMessageService directMessageService;

  @MessageMapping("/direct-messages_send")
  public void sendMessage(
      DirectMessageCreateRequest request,
      Authentication authentication
  ) {
    UUID currentUserId = (UUID) authentication.getPrincipal();

    DirectMessageDto directMessage = directMessageService.createDirectMessage(
        request,
        currentUserId
    );

    String dmKey = DirectMessageKeyGenerator.generate(
        request.senderId(),
        request.receiverId()
    );

    // 메세지 발행
    messagingTemplate.convertAndSend("/sub/direct-messages_" + dmKey, directMessage);
  }
}