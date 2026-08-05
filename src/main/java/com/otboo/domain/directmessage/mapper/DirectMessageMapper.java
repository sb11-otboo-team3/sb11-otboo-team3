package com.otboo.domain.directmessage.mapper;

import com.otboo.domain.directmessage.dto.response.DirectMessageDto;
import com.otboo.domain.directmessage.entity.DirectMessage;
import com.otboo.domain.user.dto.UserSummary;
import com.otboo.domain.user.entity.User;

public final class DirectMessageMapper {

  private DirectMessageMapper() {
  }

  public static DirectMessageDto toDto(DirectMessage directMessage) {
    return new DirectMessageDto(
        directMessage.getId(),
        directMessage.getCreatedAt(),
        toUserSummary(directMessage.getSender()),
        toUserSummary(directMessage.getReceiver()),
        directMessage.getContent()
    );
  }

  private static UserSummary toUserSummary(User user) {
    if (user == null) {
      return null;
    }

    return new UserSummary(
        user.getId(),
        user.getName(),
        null
    );
  }
}