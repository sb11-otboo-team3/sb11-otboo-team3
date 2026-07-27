package com.otboo.domain.auth.dto;

public record SignInRequest(
    String username,
    String password
) {
}