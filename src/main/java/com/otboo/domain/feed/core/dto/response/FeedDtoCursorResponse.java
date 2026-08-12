package com.otboo.domain.feed.core.dto.response;

import java.util.List;
import java.util.UUID;

public record FeedDtoCursorResponse(
    List<FeedDto> data,
    String nextCursor,
    UUID nextIdAfter,
    boolean hasNext,
    long totalCount,
    String sortBy,
    String sortDirection
) {
}