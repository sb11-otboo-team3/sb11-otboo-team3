package com.otboo.global.infrastructure.storage.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public class StorageDeleteException extends OtbooException {

    public StorageDeleteException(Throwable cause) {
        super(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "파일 삭제 중 오류가 발생했습니다."
        );
        initCause(cause);
    }
}
