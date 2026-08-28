package com.otboo.domain.recommendation.llm.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public class LlmRequestFailedException extends OtbooException {
    public LlmRequestFailedException() {
        super(HttpStatus.BAD_GATEWAY, "추천 보정 요청이 실패했습니다.");
    }
}
