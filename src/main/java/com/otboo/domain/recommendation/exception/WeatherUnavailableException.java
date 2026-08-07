package com.otboo.domain.recommendation.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class WeatherUnavailableException extends OtbooException {
    public WeatherUnavailableException(UUID userId) {
        super(HttpStatus.BAD_REQUEST, "날씨 정보를 가져올 수 없어 추천할 수 없습니다: " + userId);
    }
}
