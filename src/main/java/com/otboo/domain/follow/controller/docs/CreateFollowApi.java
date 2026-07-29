package com.otboo.domain.follow.controller.docs;

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

@Operation(summary = "팔로우 생성", description = "팔로우 생성 API")
@ApiResponses({
    @ApiResponse(responseCode = "201", description = "팔로우 생성 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "팔로우 생성 실패",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
})
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CreateFollowApi {
}