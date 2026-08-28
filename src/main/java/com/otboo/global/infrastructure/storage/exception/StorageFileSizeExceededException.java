package com.otboo.global.infrastructure.storage.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public class StorageFileSizeExceededException extends OtbooException {

    public StorageFileSizeExceededException(
            long actualSizeBytes,
            long maxSizeBytes
    ) {
        super(
                HttpStatus.BAD_REQUEST,
                "이미지 파일 크기가 최대 허용 크기를 초과했습니다. "
                + "actualSizeBytes=" + actualSizeBytes
                + ", maxSizeBytes=" + maxSizeBytes
        );
    }
}
