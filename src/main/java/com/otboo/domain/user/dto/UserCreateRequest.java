package com.otboo.domain.user.dto;

import com.otboo.global.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
    @NotBlank(message = "이름은 필수입니다.")
    @Size(max = 100, message = "이름은 100자를 초과할 수 없습니다.")
    String name,
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 320, message = "이메일은 320자를 초과할 수 없습니다.")
    String email,
    @NotBlank(message = "비밀번호는 필수입니다.")
    @Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*?&]{6,}$",
        message = "비밀번호는 최소 6자 이상이며, 영문자와 숫자를 각각 1개 이상 포함해야 합니다. 허용된 특수문자는 @$!%*?& 입니다."
    )
    @MaxUtf8Bytes(value = 72, message = "비밀번호는 72바이트를 초과할 수 없습니다.")
    String password
) {
}