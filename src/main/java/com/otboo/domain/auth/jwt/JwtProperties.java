package com.otboo.domain.auth.jwt;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    @NotBlank(message = "JWT secret은 필수입니다.")
    String secret,
    @Positive(message = "accessExpiration은 양수여야 합니다.")
    long accessExpiration,
    @Positive(message = "refreshExpiration은 양수여야 합니다.")
    long refreshExpiration,
    @Positive(message = "adminAccessExpiration은 양수여야 합니다.")
    long adminAccessExpiration
) {

    @AssertTrue(message = "adminAccessExpiration은 accessExpiration보다 짧아야 합니다.")
    public boolean isAdminExpirationShorterThanDefault() {
        return adminAccessExpiration < accessExpiration;
    }
}