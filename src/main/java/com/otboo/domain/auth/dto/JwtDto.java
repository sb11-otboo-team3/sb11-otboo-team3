package com.otboo.domain.auth.dto;

import com.otboo.domain.user.dto.UserDto;

public record JwtDto(
    UserDto userDto,
    String accessToken
) {
}