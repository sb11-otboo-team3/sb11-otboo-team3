package com.otboo.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
    @NotBlank(message = "비밀번호는 필수입니다.")
    String password
) {
}