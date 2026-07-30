package com.otboo.domain.directmessage.controller;

import com.otboo.domain.directmessage.dto.request.DirectMessageCreateRequest;
import com.otboo.domain.directmessage.support.DirectMessageKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class DirectMessageWebSocketController {

  private final SimpMessagingTemplate messagingTemplate;

  @MessageMapping("/direct-messages_send")
  public void sendMessage(DirectMessageCreateRequest request) {

    String dmKey = DirectMessageKeyGenerator.generate(
        request.senderId(),
        request.receiverId()
    );

    messagingTemplate.convertAndSend("/sub/direct-messages_" + dmKey);
  }
}