package com.otboo.domain.recommendation.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class LocationNotSetException extends OtbooException {

    public LocationNotSetException(UUID userId) {
        super(HttpStatus.BAD_REQUEST, "위치 정보가 설정되지 않아 추천할 수 없습니다: " +userId);
    }
}
