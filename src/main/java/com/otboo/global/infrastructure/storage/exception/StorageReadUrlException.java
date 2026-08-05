package com.otboo.global.infrastructure.storage.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public class StorageReadUrlException extends OtbooException {

    public StorageReadUrlException(Throwable cause) {
        super(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "파일 조회 URL 생성 중 오류가 발생했습니다."
        );
        initCause(cause);
    }
}
