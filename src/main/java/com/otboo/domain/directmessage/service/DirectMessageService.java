package com.otboo.domain.directmessage.service;

import com.otboo.domain.directmessage.dto.request.DirectMessageCreateRequest;
import com.otboo.domain.directmessage.dto.response.DirectMessageDto;
import com.otboo.domain.directmessage.dto.response.DirectMessageDtoCursorResponse;
import com.otboo.domain.directmessage.entity.DirectMessage;
import com.otboo.domain.directmessage.exception.DirectMessageForbiddenException;
import com.otboo.domain.directmessage.exception.DirectMessageInvalidUserException;
import com.otboo.domain.directmessage.exception.DirectMessageUserNotFoundException;
import com.otboo.domain.directmessage.exception.InvalidDirectMessageCursorException;
import com.otboo.domain.directmessage.exception.SelfDirectMessageNotAllowedException;
import com.otboo.domain.directmessage.mapper.DirectMessageMapper;
import com.otboo.domain.directmessage.repository.DirectMessageRepository;
import com.otboo.domain.directmessage.support.DirectMessageKeyGenerator;
import com.otboo.domain.notification.entity.NotificationLevel;
import com.otboo.domain.notification.event.NotificationEvent;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DirectMessageService {

  private final DirectMessageRepository directMessageRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public DirectMessageDto createDirectMessage(DirectMessageCreateRequest request,
      UUID currentUserId) {
    if (!request.senderId().equals(currentUserId)) {
      throw new DirectMessageForbiddenException();
    }

    // 자기자신에게 메세지를 보낼 수 없음
    if (request.senderId().equals(request.receiverId())) {
      throw new SelfDirectMessageNotAllowedException();
    }

    // User 존재여부 확인
    User sender = userRepository.findById(request.senderId())
        .orElseThrow(() -> new DirectMessageUserNotFoundException(request.senderId()));

    User receiver = userRepository.findById(request.receiverId())
        .orElseThrow(() -> new DirectMessageUserNotFoundException(request.receiverId()));

    // senderId + _ + receiverId 형식의 Dm Key 생성
    String dmKey = DirectMessageKeyGenerator.generate(
        sender.getId(),
        receiver.getId()
    );

    DirectMessage directMessage = DirectMessage.create(
        sender,
        receiver,
        dmKey,
        request.content()
    );

    DirectMessage savedMessage = directMessageRepository.save(directMessage);

    eventPublisher.publishEvent(
        new NotificationEvent(
            receiver.getId(),
            "새로운 DM이 도착했습니다.",
            sender.getName() + "님이 메시지를 보냈습니다.",
            NotificationLevel.INFO
        )
    );

    return DirectMessageMapper.toDto(savedMessage);
  }

  public DirectMessageDtoCursorResponse getDirectMessages(
      UUID userId, String cursor, UUID idAfter, int limit, UUID currentUserId
  ) {
    validateCursor(cursor, idAfter);

    if (userId.equals(currentUserId)) {
      throw new SelfDirectMessageNotAllowedException();
    }

    if (!userRepository.existsById(userId)) {
      throw new DirectMessageInvalidUserException(userId);
    }

    String dmKey = DirectMessageKeyGenerator.generate(
        userId,
        currentUserId
    );

    List<DirectMessage> messages = directMessageRepository.findDirectMessages(
        dmKey,
        cursor,
        idAfter,
        limit + 1
    );

    boolean hasNext = messages.size() > limit;

    if (hasNext) {
      messages = messages.subList(0, limit);
    }

    List<DirectMessageDto> data = messages.stream()
        .map(DirectMessageMapper::toDto)
        .toList();

    // 기본은 다음 페이지 없음
    String nextCursor = null;
    UUID nextIdAfter = null;

    if (hasNext) {
      DirectMessage last = messages.get(messages.size() - 1);
      nextCursor = last.getCreatedAt().toString();
      nextIdAfter = last.getId();
    }

    long totalCount = directMessageRepository.countDirectMessages(dmKey);

    return new DirectMessageDtoCursorResponse(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        "createdAt",
        "DESCENDING"
    );
  }

  // cursor와 idAfter는 둘 다 있거나 둘 다 없어야 함
  private void validateCursor(String cursor, UUID idAfter) {
    boolean hasCursor = cursor != null && !cursor.isBlank();
    boolean hasIdAfter = idAfter != null;

    if (hasCursor != hasIdAfter) {
      throw new InvalidDirectMessageCursorException();
    }

    if (hasCursor) {
      try {
        Instant.parse(cursor);
      } catch (DateTimeParseException e) {
        throw new InvalidDirectMessageCursorException();
      }
    }
  }
}
