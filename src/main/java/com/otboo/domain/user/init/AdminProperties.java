package com.otboo.domain.user.init;

import com.otboo.global.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(
    @NotBlank(message = "초기 어드민 이메일은 필수입니다.")
    String initEmail,
    @NotBlank(message = "초기 어드민 이름은 필수입니다.")
    String initName,
    @NotBlank(message = "초기 어드민 비밀번호는 필수입니다.")
    @StrongPassword
    String initPassword
) {
}