package com.otboo.global.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.global.infrastructure.storage.exception.UnsupportedStorageFileTypeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImageContentTypeTest {

    @Test
    @DisplayName("JPEG Content-Type을 jpg 확장자로 변환한다")
    void resolveJpegContentTypeToJpgExtension() throws Exception {
        // given
        String contentType = "image/jpeg";

        // when
        String extension = ImageContentType.from(contentType).getExtension();

        // then
        assertThat(extension).isEqualTo("jpg");
    }

    @Test
    @DisplayName("PNG Content-Type을 png 확장자로 변환한다")
    void resolvePngContentTypeToPngExtension() throws Exception {
        // given
        String contentType = "image/png";

        // when
        String extension = ImageContentType.from(contentType).getExtension();

        // then
        assertThat(extension).isEqualTo("png");
    }

    @Test
    @DisplayName("WebP Content-Type을 webp 확장자로 변환한다")
    void resolveWebpContentTypeToWebpExtension() throws Exception {
        // given
        String contentType = "image/webp";

        // when
        String extension = ImageContentType.from(contentType).getExtension();

        // then
        assertThat(extension).isEqualTo("webp");
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type이면 UnsupportedStorageFileTypeException을 발생시킨다")
    void resolveRejectsUnsupportedContentType() throws Exception {
        // given
        String contentType = "image/gif";

        // when & then
        assertThatThrownBy(() -> ImageContentType.from(contentType))
                .isInstanceOf(UnsupportedStorageFileTypeException.class);
    }

    @Test
    @DisplayName("Content-Type이 null이면 UnsupportedStorageFileTypeException을 발생시킨다")
    void resolveRejectsNullContentType() throws Exception {
        // given
        String contentType = null;

        // when & then
        assertThatThrownBy(() -> ImageContentType.from(contentType))
                .isInstanceOf(UnsupportedStorageFileTypeException.class);
    }
}