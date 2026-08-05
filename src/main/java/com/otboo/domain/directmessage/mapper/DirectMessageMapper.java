package com.otboo.domain.directmessage.mapper;

import com.otboo.domain.directmessage.dto.response.DirectMessageDto;
import com.otboo.domain.directmessage.entity.DirectMessage;
import com.otboo.domain.user.mapper.UserSummaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DirectMessageMapper {

  private final UserSummaryMapper userSummaryMapper;

  public DirectMessageDto toDto(DirectMessage directMessage) {
    return new DirectMessageDto(
        directMessage.getId(),
        directMessage.getCreatedAt(),
        userSummaryMapper.toUserSummary(directMessage.getSender()),
        userSummaryMapper.toUserSummary(directMessage.getReceiver()),
        directMessage.getContent()
    );
  }
}