package com.otboo.domain.feed.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record FeedCommentCreateRequest(

    @NotNull(message = "피드 ID는 필수입니다.")
    UUID feedId,

    @NotNull(message = "작성자 ID는 필수입니다.")
    UUID authorId,

    @NotBlank(message = "댓글 내용은 필수입니다.")
    String content
) {

}
