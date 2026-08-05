package com.otboo.global.infrastructure.storage.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.global.infrastructure.storage.exception.EmptyStorageFileException;
import com.otboo.global.infrastructure.storage.exception.StorageFileSizeExceededException;
import com.otboo.global.infrastructure.storage.exception.UnsupportedStorageFileTypeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ImageFileValidatorTest {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    private final ImageFileValidator imageFileValidator =
            new ImageFileValidator(MAX_FILE_SIZE_BYTES);

    @Test
    @DisplayName("허용된 JPEG 이미지 파일이면 검증을 통과한다")
    void validateAcceptsJpegImage() throws Exception {
        // given
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "profile.jpg",
                "image/jpeg",
                new byte[] {1, 2, 3}
        );

        // when & then
        assertThatCode(() -> imageFileValidator.validate(image))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("허용된 PNG 이미지 파일이면 검증을 통과한다")
    void validateAcceptsPngImage() throws Exception {
        // given
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "profile.png",
                "image/png",
                new byte[] {1, 2, 3}
        );

        // when & then
        assertThatCode(() -> imageFileValidator.validate(image))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("허용된 WebP 이미지 파일이면 검증을 통과한다")
    void validateAcceptsWebpImage() throws Exception {
        // given
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "clothes.webp",
                "image/webp",
                new byte[] {1, 2, 3}
        );

        // when & then
        assertThatCode(() -> imageFileValidator.validate(image))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("빈 이미지 파일이면 EmptyStorageFileException을 발생시킨다")
    void validateRejectsEmptyImage() throws Exception {
        // given
        MockMultipartFile emptyImage = new MockMultipartFile(
                "image",
                "empty.png",
                "image/png",
                new byte[0]
        );

        // when & then
        assertThatThrownBy(() -> imageFileValidator.validate(emptyImage))
                .isInstanceOf(EmptyStorageFileException.class);
    }

    @Test
    @DisplayName("지원하지 않는 이미지 형식이면 UnsupportedStorageFileTypeException을 발생시킨다")
    void validateRejectsUnsupportedContentType() throws Exception {
        // given
        MockMultipartFile unsupportedFile = new MockMultipartFile(
                "image",
                "document.gif",
                "image/gif",
                new byte[] {1, 2, 3}
        );

        // when & then
        assertThatThrownBy(() -> imageFileValidator.validate(unsupportedFile))
                .isInstanceOf(UnsupportedStorageFileTypeException.class);
    }

    @Test
    @DisplayName("Content-Type이 없으면 UnsupportedStorageFileTypeException을 발생시킨다")
    void validateRejectsMissingContentType() throws Exception {
        // given
        MockMultipartFile fileWithoutContentType = new MockMultipartFile(
                "image",
                "profile.png",
                null,
                new byte[] {1, 2, 3}
        );

        // when & then
        assertThatThrownBy(() -> imageFileValidator.validate(fileWithoutContentType))
                .isInstanceOf(UnsupportedStorageFileTypeException.class);
    }

    @Test
    @DisplayName("최대 허용 크기를 초과하면 StorageFileSizeExceededException을 발생시킨다")
    void validateRejectsOversizedImage() throws Exception {
        // given
        MockMultipartFile oversizedImage = new MockMultipartFile(
                "image",
                "large.png",
                "image/png",
                new byte[(int) MAX_FILE_SIZE_BYTES + 1]
        );

        // when & then
        assertThatThrownBy(() -> imageFileValidator.validate(oversizedImage))
                .isInstanceOf(StorageFileSizeExceededException.class);
    }

    @Test
    @DisplayName("최대 허용 크기와 동일한 이미지 파일이면 검증을 통과한다")
    void validateAcceptsImageAtMaximumSize() throws Exception {
        // given
        MockMultipartFile maximumSizeImage = new MockMultipartFile(
                "image",
                "maximum.png",
                "image/png",
                new byte[(int) MAX_FILE_SIZE_BYTES]
        );

        // when & then
        assertThatCode(() -> imageFileValidator.validate(maximumSizeImage))
                .doesNotThrowAnyException();
    }
}