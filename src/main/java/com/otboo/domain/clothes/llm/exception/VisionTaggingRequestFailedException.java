package com.otboo.domain.clothes.llm.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public class VisionTaggingRequestFailedException extends OtbooException {
    public VisionTaggingRequestFailedException() {
        super(HttpStatus.BAD_GATEWAY, "이미지 속성 자동 태깅 요청이 실패했습니다.");
    }
}
