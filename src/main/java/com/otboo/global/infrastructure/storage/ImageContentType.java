package com.otboo.global.infrastructure.storage;

import com.otboo.global.infrastructure.storage.exception.UnsupportedStorageFileTypeException;
import java.util.Arrays;
import java.util.Locale;

public enum ImageContentType {

    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private static final byte[] JPEG_SIGNATURE = {
            (byte) 0xFF,
            (byte) 0xD8,
            (byte) 0xFF
    };

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89,
            (byte) 0x50,
            (byte) 0x4E,
            (byte) 0x47,
            (byte) 0x0D,
            (byte) 0x0A,
            (byte) 0x1A,
            (byte) 0x0A
    };

    private static final byte[] RIFF_SIGNATURE = {
            (byte) 0x52,
            (byte) 0x49,
            (byte) 0x46,
            (byte) 0x46
    };

    private static final byte[] WEBP_SIGNATURE = {
            (byte) 0x57,
            (byte) 0x45,
            (byte) 0x42,
            (byte) 0x50
    };

    private final String contentType;
    private final String extension;

    ImageContentType(
            String contentType,
            String extension
    ) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public static ImageContentType from(
            String contentType
    ) {
        String normalizedContentType =
                normalizeContentType(contentType);

        return Arrays.stream(values())
                .filter(imageContentType ->
                        imageContentType.contentType.equals(
                                normalizedContentType
                        )
                )
                .findFirst()
                .orElseThrow(() ->
                        new UnsupportedStorageFileTypeException(
                                contentType
                        )
                );
    }

    public boolean matchesSignature(
            byte[] bytes
    ) {
        if (bytes == null) {
            return false;
        }

        return switch (this) {
            case JPEG ->
                    startsWithAt(
                            bytes,
                            JPEG_SIGNATURE,
                            0
                    );

            case PNG ->
                    startsWithAt(
                            bytes,
                            PNG_SIGNATURE,
                            0
                    );

            case WEBP ->
                    startsWithAt(
                            bytes,
                            RIFF_SIGNATURE,
                            0
                    )
                            && startsWithAt(
                            bytes,
                            WEBP_SIGNATURE,
                            8
                    );
        };
    }

    public String getContentType() {
        return contentType;
    }

    public String getExtension() {
        return extension;
    }

    private static String normalizeContentType(
            String contentType
    ) {
        if (contentType == null
                || contentType.isBlank()) {
            throw new UnsupportedStorageFileTypeException(
                    contentType
            );
        }

        int parameterSeparatorIndex =
                contentType.indexOf(';');

        String baseContentType =
                parameterSeparatorIndex >= 0
                        ? contentType.substring(
                        0,
                        parameterSeparatorIndex
                )
                        : contentType;

        return baseContentType
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static boolean startsWithAt(
            byte[] bytes,
            byte[] signature,
            int offset
    ) {
        if (bytes.length
                < offset + signature.length) {
            return false;
        }

        for (int index = 0;
             index < signature.length;
             index++) {
            if (bytes[offset + index]
                    != signature[index]) {
                return false;
            }
        }

        return true;
    }
}
