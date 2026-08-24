package com.otboo.global.infrastructure.storage;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorage {
    StoredFile upload(
        StorageDirectory directory,
        UUID ownerId,
        MultipartFile file
    );

    StoredFile uploadWithThumbnail(
        StorageDirectory directory,
        UUID ownerId,
        MultipartFile file
    );

    String generateReadUrl(String objectKey);

    void delete(String objectKey);
}