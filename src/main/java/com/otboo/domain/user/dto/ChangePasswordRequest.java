package com.otboo.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(
    @NotBlank(message = "비밀번호는 필수입니다.")
    @Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*?&]{6,}$",
        message = "비밀번호는 최소 6자 이상이며, 영문자와 숫자를 각각 1개 이상 포함해야 합니다. 허용된 특수문자는 @$!%*?& 입니다."
    )
    String password
) {
}