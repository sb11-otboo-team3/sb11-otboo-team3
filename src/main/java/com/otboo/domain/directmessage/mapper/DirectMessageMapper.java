package com.otboo.domain.directmessage.mapper;

import com.otboo.domain.directmessage.dto.response.DirectMessageDto;
import com.otboo.domain.directmessage.entity.DirectMessage;
import com.otboo.domain.user.dto.UserSummary;
import com.otboo.domain.user.mapper.UserSummaryMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DirectMessageMapper {

  private final UserSummaryMapper userSummaryMapper;

  // 단건
  public DirectMessageDto toDto(DirectMessage directMessage) {
    return new DirectMessageDto(
        directMessage.getId(),
        directMessage.getCreatedAt(),
        userSummaryMapper.toUserSummary(directMessage.getSender()),
        userSummaryMapper.toUserSummary(directMessage.getReceiver()),
        directMessage.getContent()
    );
  }

  // 다건
  public List<DirectMessageDto> toDtos(List<DirectMessage> directMessages) {
    List<UUID> userIds = directMessages.stream()
        .flatMap(directMessage -> Stream.of(
            directMessage.getSender().getId(),
            directMessage.getReceiver().getId()
        ))
        .distinct()
        .toList();

    Map<UUID, UserSummary> userSummaryMap = userSummaryMapper.toUserSummaries(userIds).stream()
        .collect(Collectors.toMap(UserSummary::userId, userSummary -> userSummary));

    return directMessages.stream()
        .map(directMessage -> new DirectMessageDto(
            directMessage.getId(),
            directMessage.getCreatedAt(),
            userSummaryMap.get(directMessage.getSender().getId()),
            userSummaryMap.get(directMessage.getReceiver().getId()),
            directMessage.getContent()
        ))
        .toList();
  }
}