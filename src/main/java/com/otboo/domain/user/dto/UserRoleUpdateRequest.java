package com.otboo.domain.user.dto;

import com.otboo.domain.user.entity.UserRole;
import jakarta.validation.constraints.NotNull;

public record UserRoleUpdateRequest(
    @NotNull(message = "권한은 필수입니다.")
    UserRole role
) {
}