package com.otboo.domain.auth.dto;

import com.otboo.global.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;

public record SignInRequest(
    @NotBlank(message = "이메일은 필수입니다.")
    String username,
    @NotBlank(message = "비밀번호는 필수입니다.")
    @MaxUtf8Bytes(value = 72, message = "비밀번호는 72바이트를 초과할 수 없습니다.")
    String password
) {
}