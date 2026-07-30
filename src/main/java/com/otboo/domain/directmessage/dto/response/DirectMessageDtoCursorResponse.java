package com.otboo.domain.directmessage.dto.response;

import java.util.List;
import java.util.UUID;

public record DirectMessageDtoCursorResponse(
    List<DirectMessageDto> data,
    String nextCursor,
    UUID nextIdAfter,
    boolean hasNext,
    long totalCount,
    String sortBy,
    String sortDirection
) {

}
