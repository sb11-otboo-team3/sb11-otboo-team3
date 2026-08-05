package com.otboo.global.infrastructure.storage.validation;

import com.otboo.global.infrastructure.storage.ImageContentType;

public record ValidatedImageFile(
        byte[] bytes,
        ImageContentType contentType,
        long size
) {

    public ValidatedImageFile {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}