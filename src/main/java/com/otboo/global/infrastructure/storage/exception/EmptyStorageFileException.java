package com.otboo.global.infrastructure.storage.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public class EmptyStorageFileException extends OtbooException {

    public EmptyStorageFileException() {
        super(HttpStatus.BAD_REQUEST, "업로드할 이미지 파일이 비어 있습니다.");
    }
}
