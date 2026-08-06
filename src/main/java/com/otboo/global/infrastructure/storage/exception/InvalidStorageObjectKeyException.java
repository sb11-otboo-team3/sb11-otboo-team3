package com.otboo.global.infrastructure.storage.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public class InvalidStorageObjectKeyException extends OtbooException {

    public InvalidStorageObjectKeyException() {
        super(
                HttpStatus.BAD_REQUEST,
                "Object Key는 비어 있을 수 없습니다."
        );
    }
}
