package com.otboo.global.infrastructure.storage.s3;

import com.otboo.global.infrastructure.storage.StorageDirectory;

import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

@Component
public class S3ObjectKeyGenerator {

    private final Supplier<UUID> uuidSupplier;

    public S3ObjectKeyGenerator() {
        this(UUID::randomUUID);
    }

    S3ObjectKeyGenerator(Supplier<UUID> uuidSupplier) {
        this.uuidSupplier = uuidSupplier;
    }

    public String generate(
            StorageDirectory directory,
            UUID ownerId,
            String extension
    ) {
        return "%s/%s/%s.%s".formatted(
                directory.getPrefix(),
                ownerId,
                uuidSupplier.get(),
                extension
        );
    }
}
