package com.otboo.global.infrastructure.storage.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public class StorageUploadException extends OtbooException {

    public StorageUploadException(Throwable cause) {
        super(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "파일 업로드 데이터를 읽는 중 오류가 발생했습니다."
        );
        initCause(cause);
    }
}