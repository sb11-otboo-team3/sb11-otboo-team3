package com.otboo.domain.feed.dto.request;

import jakarta.validation.constraints.NotBlank;

public record FeedUpdateRequest(

    @NotBlank(message = "피드 내용은 필수입니다.")
    String content
) {
}
