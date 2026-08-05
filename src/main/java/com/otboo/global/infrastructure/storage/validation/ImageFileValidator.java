package com.otboo.global.infrastructure.storage.validation;

import com.otboo.global.infrastructure.storage.exception.EmptyStorageFileException;
import com.otboo.global.infrastructure.storage.exception.StorageFileSizeExceededException;
import com.otboo.global.infrastructure.storage.exception.UnsupportedStorageFileTypeException;

import java.util.Set;

import org.springframework.web.multipart.MultipartFile;

public class ImageFileValidator {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final long maxFileSizeBytes;

    public ImageFileValidator(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public void validate(MultipartFile file) {
        validateNotEmpty(file);
        validateFileSize(file);
        validateContentType(file);
    }

    private void validateNotEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new EmptyStorageFileException();
        }
    }

    private void validateFileSize(MultipartFile file) {
        if (file.getSize() > maxFileSizeBytes) {
            throw new StorageFileSizeExceededException(
                    file.getSize(),
                    maxFileSizeBytes
            );
        }
    }

    private void validateContentType(MultipartFile file) {
        String contentType = file.getContentType();

        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new UnsupportedStorageFileTypeException(contentType);
        }
    }
}
