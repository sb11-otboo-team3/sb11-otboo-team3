package com.otboo.domain.feed.controller.docs;

import com.otboo.domain.feed.dto.response.FeedCommentDto;
import com.otboo.global.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Operation(summary = "피드 댓글 등록", description = "피드 댓글 등록 API")
@ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "피드 댓글 등록 성공",
        content = @Content(schema = @Schema(implementation = FeedCommentDto.class))
    ),
    @ApiResponse(
        responseCode = "400",
        description = "피드 댓글 등록 실패",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
})
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CreateFeedCommentApi {
}