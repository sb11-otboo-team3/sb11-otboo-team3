package com.otboo.global.infrastructure.storage.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public class InvalidImageFileException
        extends OtbooException {

    public InvalidImageFileException(
            String contentType
    ) {
        super(
                HttpStatus.BAD_REQUEST,
                "파일의 실제 형식이 선언된 Content-Type과 "
                        + "일치하지 않습니다: "
                        + String.valueOf(contentType)
        );
    }
}