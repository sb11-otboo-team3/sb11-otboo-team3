package com.otboo.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record SignInRequest(
    @NotBlank(message = "이메일은 필수입니다.")
    String username,

    @NotBlank(message = "비밀번호는 필수입니다.")
    String password
) {
}