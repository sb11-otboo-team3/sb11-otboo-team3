package com.otboo.global.infrastructure.storage;

import com.otboo.global.infrastructure.storage.exception.UnsupportedStorageFileTypeException;
import java.util.Arrays;

public enum ImageContentType {

    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String contentType;
    private final String extension;

    ImageContentType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public static ImageContentType from(String contentType) {
        return Arrays.stream(values())
                .filter(imageContentType ->
                        imageContentType.contentType.equals(contentType)
                )
                .findFirst()
                .orElseThrow(() ->
                        new UnsupportedStorageFileTypeException(contentType)
                );
    }

    public String getContentType() {
        return contentType;
    }

    public String getExtension() {
        return extension;
    }
}