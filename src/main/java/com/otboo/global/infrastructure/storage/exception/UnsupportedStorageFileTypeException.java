package com.otboo.global.infrastructure.storage.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public class UnsupportedStorageFileTypeException extends OtbooException {

    public UnsupportedStorageFileTypeException(String contentType) {
        super(
                HttpStatus.BAD_REQUEST,
                "지원하지 않는 이미지 형식입니다: " + String.valueOf(contentType) // null 이여도 다시 오류 x
        );
    }
}
