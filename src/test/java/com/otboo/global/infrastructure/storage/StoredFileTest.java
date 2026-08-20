package com.otboo.global.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StoredFileTest {

    @Test
    @DisplayName("저장 결과는 Object Key와 파일 메타데이터를 제공한다")
    void createStoredFileWithObjectKeyAndMetadata() throws Exception {
        // given
        String objectKey =
            "clothes/11111111-1111-1111-1111-111111111111/"
                + "22222222-2222-2222-2222-222222222222.jpg";
        String contentType = "image/jpeg";
        long size = 3L;
        String thumbnailKey =
            "clothes/11111111-1111-1111-1111-111111111111/"
                + "thumb_22222222-2222-2222-2222-222222222222.jpg";

        // when
        StoredFile storedFile = new StoredFile(
            objectKey,
            contentType,
            size,
            thumbnailKey
        );

        // then
        assertThat(storedFile.objectKey()).isEqualTo(objectKey);
        assertThat(storedFile.contentType()).isEqualTo(contentType);
        assertThat(storedFile.size()).isEqualTo(size);
        assertThat(storedFile.thumbnailKey()).isEqualTo(thumbnailKey);
    }
}