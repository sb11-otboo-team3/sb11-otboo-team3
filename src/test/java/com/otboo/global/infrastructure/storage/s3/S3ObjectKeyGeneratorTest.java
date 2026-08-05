package com.otboo.global.infrastructure.storage.s3;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.global.infrastructure.storage.StorageDirectory;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class S3ObjectKeyGeneratorTest {

    private static final UUID OWNER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID OBJECT_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final S3ObjectKeyGenerator objectKeyGenerator =
            new S3ObjectKeyGenerator(() -> OBJECT_ID);

    @Test
    @DisplayName("프로필 이미지의 Object Key를 지정된 형식으로 생성한다")
    void generateProfileImageObjectKey() throws Exception {
        // given
        String extension = "png";

        // when
        String objectKey = objectKeyGenerator.generate(
                StorageDirectory.PROFILES,
                OWNER_ID,
                extension
        );

        // then
        assertThat(objectKey)
                .isEqualTo(
                        "profiles/11111111-1111-1111-1111-111111111111/"
                                + "22222222-2222-2222-2222-222222222222.png"
                );
    }

    @Test
    @DisplayName("의상 이미지의 Object Key를 지정된 형식으로 생성한다")
    void generateClothesImageObjectKey() throws Exception {
        // given
        String extension = "webp";

        // when
        String objectKey = objectKeyGenerator.generate(
                StorageDirectory.CLOTHES,
                OWNER_ID,
                extension
        );

        // then
        assertThat(objectKey)
                .isEqualTo(
                        "clothes/11111111-1111-1111-1111-111111111111/"
                                + "22222222-2222-2222-2222-222222222222.webp"
                );
    }
}