package com.otboo.domain.directmessage.dto.response;

import com.otboo.domain.user.dto.UserSummary;
import java.time.Instant;
import java.util.UUID;

// 프론트엔드에 맞춤
public record DirectMessageDto(
    UUID id,
    Instant createdAt,
    UserSummary sender,
    UserSummary receiver,
    String content
) {

}
