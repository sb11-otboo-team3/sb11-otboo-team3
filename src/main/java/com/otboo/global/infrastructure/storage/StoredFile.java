package com.otboo.global.infrastructure.storage;

public record StoredFile(
        String objectKey,
        String contentType,
        long size,
        String thumbnailKey
) {
}