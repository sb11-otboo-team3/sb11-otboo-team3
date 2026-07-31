package com.otboo.domain.user.dto;

import jakarta.validation.constraints.NotNull;

public record UserLockUpdateRequest(
    @NotNull(message = "잠금 상태는 필수입니다.")
    Boolean locked
) {
}