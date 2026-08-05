package com.otboo.global.infrastructure.storage.validation;

import com.otboo.global.infrastructure.storage.ImageContentType;
import com.otboo.global.infrastructure.storage.exception.EmptyStorageFileException;
import com.otboo.global.infrastructure.storage.exception.StorageFileSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

public class ImageFileValidator {

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
        ImageContentType.from(file.getContentType());
    }
}