package com.otboo.domain.feed.controller.docs;

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

@Operation(summary = "피드 좋아요 취소", description = "피드 좋아요 취소 API")
@ApiResponses({
    @ApiResponse(responseCode = "204", description = "피드 좋아요 취소 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "피드 좋아요 취소 실패",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
})
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DeleteFeedLikeApi {

}
