package com.otboo.global.infrastructure.storage.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.otboo.global.infrastructure.storage.ImageContentType;
import com.otboo.global.infrastructure.storage.exception.EmptyStorageFileException;
import com.otboo.global.infrastructure.storage.exception.InvalidImageFileException;
import com.otboo.global.infrastructure.storage.exception.StorageFileSizeExceededException;
import com.otboo.global.infrastructure.storage.exception.StorageUploadException;
import com.otboo.global.infrastructure.storage.exception.UnsupportedStorageFileTypeException;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class ImageFileValidatorTest {

    private static final long MAX_FILE_SIZE_BYTES =
            10L * 1024 * 1024;

    private static final byte[] JPEG_BYTES = {
            (byte) 0xFF,
            (byte) 0xD8,
            (byte) 0xFF
    };

    private static final byte[] PNG_BYTES = {
            (byte) 0x89,
            (byte) 0x50,
            (byte) 0x4E,
            (byte) 0x47,
            (byte) 0x0D,
            (byte) 0x0A,
            (byte) 0x1A,
            (byte) 0x0A
    };

    private static final byte[] WEBP_BYTES = {
            (byte) 0x52,
            (byte) 0x49,
            (byte) 0x46,
            (byte) 0x46,
            (byte) 0x00,
            (byte) 0x00,
            (byte) 0x00,
            (byte) 0x00,
            (byte) 0x57,
            (byte) 0x45,
            (byte) 0x42,
            (byte) 0x50
    };

    private ImageFileValidator imageFileValidator;

    @BeforeEach
    void setUp() {
        imageFileValidator =
                new ImageFileValidator(MAX_FILE_SIZE_BYTES);
    }

    @Test
    @DisplayName("허용된 JPEG 이미지 파일이면 검증을 통과한다")
    void validateAcceptsJpegImage() throws Exception {
        // given
        MockMultipartFile jpegImage =
                new MockMultipartFile(
                        "image",
                        "image.jpg",
                        "image/jpeg",
                        JPEG_BYTES
                );

        // when
        ValidatedImageFile validatedImage =
                imageFileValidator.validate(jpegImage);

        // then
        assertThat(validatedImage.contentType())
                .isEqualTo(ImageContentType.JPEG);

        assertThat(validatedImage.size())
                .isEqualTo(JPEG_BYTES.length);

        assertThat(validatedImage.bytes())
                .containsExactly(JPEG_BYTES);
    }

    @Test
    @DisplayName("허용된 PNG 이미지 파일이면 검증을 통과한다")
    void validateAcceptsPngImage() throws Exception {
        // given
        MockMultipartFile pngImage =
                new MockMultipartFile(
                        "image",
                        "image.png",
                        "image/png",
                        PNG_BYTES
                );

        // when
        ValidatedImageFile validatedImage =
                imageFileValidator.validate(pngImage);

        // then
        assertThat(validatedImage.contentType())
                .isEqualTo(ImageContentType.PNG);

        assertThat(validatedImage.size())
                .isEqualTo(PNG_BYTES.length);

        assertThat(validatedImage.bytes())
                .containsExactly(PNG_BYTES);
    }

    @Test
    @DisplayName("허용된 WebP 이미지 파일이면 검증을 통과한다")
    void validateAcceptsWebpImage() throws Exception {
        // given
        MockMultipartFile webpImage =
                new MockMultipartFile(
                        "image",
                        "image.webp",
                        "image/webp",
                        WEBP_BYTES
                );

        // when
        ValidatedImageFile validatedImage =
                imageFileValidator.validate(webpImage);

        // then
        assertThat(validatedImage.contentType())
                .isEqualTo(ImageContentType.WEBP);

        assertThat(validatedImage.size())
                .isEqualTo(WEBP_BYTES.length);

        assertThat(validatedImage.bytes())
                .containsExactly(WEBP_BYTES);
    }

    @Test
    @DisplayName("이미지 파일이 null이면 EmptyStorageFileException을 발생시킨다")
    void validateRejectsNullFile() throws Exception {
        // when & then
        assertThatThrownBy(() ->
                imageFileValidator.validate(null)
        ).isInstanceOf(
                EmptyStorageFileException.class
        );
    }

    @Test
    @DisplayName("이미지 파일이 비어 있으면 EmptyStorageFileException을 발생시킨다")
    void validateRejectsEmptyFile() throws Exception {
        // given
        MockMultipartFile emptyImage =
                new MockMultipartFile(
                        "image",
                        "empty.jpg",
                        "image/jpeg",
                        new byte[0]
                );

        // when & then
        assertThatThrownBy(() ->
                imageFileValidator.validate(emptyImage)
        ).isInstanceOf(
                EmptyStorageFileException.class
        );
    }

    @Test
    @DisplayName("Content-Type이 없으면 UnsupportedStorageFileTypeException을 발생시킨다")
    void validateRejectsMissingContentType() throws Exception {
        // given
        MockMultipartFile imageWithoutContentType =
                new MockMultipartFile(
                        "image",
                        "image.jpg",
                        null,
                        JPEG_BYTES
                );

        // when & then
        assertThatThrownBy(() ->
                imageFileValidator.validate(
                        imageWithoutContentType
                )
        ).isInstanceOf(
                UnsupportedStorageFileTypeException.class
        );
    }

    @Test
    @DisplayName("허용하지 않는 Content-Type이면 UnsupportedStorageFileTypeException을 발생시킨다")
    void validateRejectsUnsupportedContentType()
            throws Exception {
        // given
        MockMultipartFile gifImage =
                new MockMultipartFile(
                        "image",
                        "image.gif",
                        "image/gif",
                        new byte[]{
                                (byte) 0x47,
                                (byte) 0x49,
                                (byte) 0x46
                        }
                );

        // when & then
        assertThatThrownBy(() ->
                imageFileValidator.validate(gifImage)
        ).isInstanceOf(
                UnsupportedStorageFileTypeException.class
        );
    }

    @Test
    @DisplayName("최대 파일 크기를 초과하면 StorageFileSizeExceededException을 발생시킨다")
    void validateRejectsFileExceedingMaximumSize()
            throws Exception {
        // given
        byte[] oversizedBytes =
                new byte[(int) MAX_FILE_SIZE_BYTES + 1];

        System.arraycopy(
                PNG_BYTES,
                0,
                oversizedBytes,
                0,
                PNG_BYTES.length
        );

        MockMultipartFile oversizedImage =
                new MockMultipartFile(
                        "image",
                        "oversized.png",
                        "image/png",
                        oversizedBytes
                );

        // when & then
        assertThatThrownBy(() ->
                imageFileValidator.validate(oversizedImage)
        ).isInstanceOf(
                StorageFileSizeExceededException.class
        );
    }

    @Test
    @DisplayName("최대 파일 크기와 동일한 이미지 파일이면 검증을 통과한다")
    void validateAcceptsFileAtMaximumSize()
            throws Exception {
        // given
        byte[] maximumSizeBytes =
                new byte[(int) MAX_FILE_SIZE_BYTES];

        System.arraycopy(
                PNG_BYTES,
                0,
                maximumSizeBytes,
                0,
                PNG_BYTES.length
        );

        MockMultipartFile maximumSizeImage =
                new MockMultipartFile(
                        "image",
                        "maximum.png",
                        "image/png",
                        maximumSizeBytes
                );

        // when & then
        assertThatCode(() ->
                imageFileValidator.validate(
                        maximumSizeImage
                )
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("HTML 바이트를 JPEG로 선언하면 InvalidImageFileException을 발생시킨다")
    void validateRejectsHtmlBytesDeclaredAsJpeg()
            throws Exception {
        // given
        MockMultipartFile fakeJpegImage =
                new MockMultipartFile(
                        "image",
                        "fake.jpg",
                        "image/jpeg",
                        "<html>not an image</html>".getBytes()
                );

        // when & then
        assertThatThrownBy(() ->
                imageFileValidator.validate(fakeJpegImage)
        ).isInstanceOf(
                InvalidImageFileException.class
        );
    }

    @Test
    @DisplayName("PNG 바이트를 JPEG로 선언하면 InvalidImageFileException을 발생시킨다")
    void validateRejectsContentTypeAndSignatureMismatch()
            throws Exception {
        // given
        MockMultipartFile mismatchedImage =
                new MockMultipartFile(
                        "image",
                        "mismatch.jpg",
                        "image/jpeg",
                        PNG_BYTES
                );

        // when & then
        assertThatThrownBy(() ->
                imageFileValidator.validate(mismatchedImage)
        ).isInstanceOf(
                InvalidImageFileException.class
        );
    }

    @Test
    @DisplayName("Multipart 이미지 파일을 읽지 못하면 StorageUploadException을 발생시킨다")
    void validateThrowsStorageUploadExceptionWhenReadingFileFails()
            throws Exception {
        // given
        MultipartFile unreadableImage =
                mock(MultipartFile.class);

        IOException ioException =
                new IOException("file read failed");

        given(unreadableImage.isEmpty())
                .willReturn(false);

        given(unreadableImage.getSize())
                .willReturn((long) JPEG_BYTES.length);

        given(unreadableImage.getContentType())
                .willReturn("image/jpeg");

        given(unreadableImage.getBytes())
                .willThrow(ioException);

        // when & then
        assertThatThrownBy(() ->
                imageFileValidator.validate(unreadableImage)
        )
                .isInstanceOf(StorageUploadException.class)
                .hasCause(ioException);
    }
}