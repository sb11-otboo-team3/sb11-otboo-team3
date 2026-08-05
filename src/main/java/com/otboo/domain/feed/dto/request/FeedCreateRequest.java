package com.otboo.domain.feed.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record FeedCreateRequest(

    @NotNull(message = "작성자 ID는 필수입니다.")
    UUID authorId,

    @NotNull(message = "날씨 ID는 필수입니다.")
    UUID weatherId,

    @NotEmpty(message = "의상 ID 목록은 필수입니다.")
    List<@NotNull UUID> clothesIds,

    @NotBlank(message = "피드 내용은 필수입니다.")
    String content
) {
}