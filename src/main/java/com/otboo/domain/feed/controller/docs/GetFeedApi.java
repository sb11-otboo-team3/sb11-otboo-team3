package com.otboo.domain.feed.controller.docs;

import com.otboo.domain.feed.dto.response.FeedDtoCursorResponse;
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

@Operation(summary = "피드 목록 조회", description = "피드 목록 조회 API")
@ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "피드 목록 조회 성공",
        content = @Content(schema = @Schema(implementation = FeedDtoCursorResponse.class))
    ),
    @ApiResponse(
        responseCode = "400",
        description = "피드 목록 조회 실패",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
})
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GetFeedApi {
}