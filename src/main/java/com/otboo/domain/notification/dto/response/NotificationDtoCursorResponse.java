package com.otboo.domain.notification.dto.response;

import java.util.List;
import java.util.UUID;

public record NotificationDtoCursorResponse(
    List<NotificationDto> data,
    String nextCursor,
    UUID nextIdAfter,
    boolean hasNext,
    long totalCount,
    String sortBy,
    String sortDirection
) {
}