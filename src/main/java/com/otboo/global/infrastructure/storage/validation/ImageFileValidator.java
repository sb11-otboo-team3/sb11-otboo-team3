package com.otboo.global.infrastructure.storage.validation;

import com.otboo.global.infrastructure.storage.ImageContentType;
import com.otboo.global.infrastructure.storage.exception.EmptyStorageFileException;
import com.otboo.global.infrastructure.storage.exception.InvalidImageFileException;
import com.otboo.global.infrastructure.storage.exception.StorageFileSizeExceededException;
import com.otboo.global.infrastructure.storage.exception.StorageUploadException;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

public class ImageFileValidator {

    private final long maxFileSizeBytes;

    public ImageFileValidator(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public ValidatedImageFile validate(
            MultipartFile file
    ) {
        validateNotEmpty(file);
        validateFileSize(file);

        ImageContentType imageContentType =
                ImageContentType.from(file.getContentType());

        byte[] bytes = readBytes(file);

        validateSignature(
                imageContentType,
                bytes
        );

        return new ValidatedImageFile(
                bytes,
                imageContentType,
                file.getSize()
        );
    }

    private void validateNotEmpty(
            MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new EmptyStorageFileException();
        }
    }

    private void validateFileSize(
            MultipartFile file
    ) {
        if (file.getSize() > maxFileSizeBytes) {
            throw new StorageFileSizeExceededException(
                    file.getSize(),
                    maxFileSizeBytes
            );
        }
    }

    private byte[] readBytes(
            MultipartFile file
    ) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new StorageUploadException(exception);
        }
    }

    private void validateSignature(
            ImageContentType imageContentType,
            byte[] bytes
    ) {
        if (!imageContentType.matchesSignature(bytes)) {
            throw new InvalidImageFileException(
                    imageContentType.getContentType()
            );
        }
    }
}