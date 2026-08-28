package com.otboo.domain.feed.comment.dto.response;

import java.util.List;
import java.util.UUID;

public record FeedCommentDtoCursorResponse(
    List<FeedCommentDto> data,
    String nextCursor,
    UUID nextIdAfter,
    boolean hasNext,
    long totalCount,
    String sortBy,
    String sortDirection
){

}
