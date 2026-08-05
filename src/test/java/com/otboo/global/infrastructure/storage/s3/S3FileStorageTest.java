package com.otboo.global.infrastructure.storage.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;

import com.otboo.global.infrastructure.storage.StorageDirectory;
import com.otboo.global.infrastructure.storage.StoredFile;
import com.otboo.global.infrastructure.storage.config.S3Properties;
import com.otboo.global.infrastructure.storage.exception.UnsupportedStorageFileTypeException;
import com.otboo.global.infrastructure.storage.validation.ImageFileValidator;
import com.otboo.global.infrastructure.storage.exception.StorageDeleteException;
import com.otboo.global.infrastructure.storage.exception.StorageReadUrlException;
import com.otboo.global.infrastructure.storage.exception.StorageUploadException;
import com.otboo.global.infrastructure.storage.exception.InvalidStorageObjectKeyException;

import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.UUID;
import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;


@ExtendWith(MockitoExtension.class)
class S3FileStorageTest {

    private static final String REGION = "ap-northeast-2";
    private static final String BUCKET = "test-storage-bucket";
    private static final long PRESIGNED_URL_EXPIRATION_SECONDS = 600L;
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    private static final UUID OWNER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID OBJECT_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final String OBJECT_KEY =
            "profiles/" + OWNER_ID + "/" + OBJECT_ID + ".jpg";

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private PresignedGetObjectRequest presignedGetObjectRequest;

    private S3FileStorage s3FileStorage;

    @BeforeEach
    void setUp() {
        S3Properties s3Properties = new S3Properties(
                REGION,
                BUCKET,
                PRESIGNED_URL_EXPIRATION_SECONDS
        );

        ImageFileValidator imageFileValidator =
                new ImageFileValidator(MAX_FILE_SIZE_BYTES);

        S3ObjectKeyGenerator objectKeyGenerator =
                new S3ObjectKeyGenerator(() -> OBJECT_ID);

        s3FileStorage = new S3FileStorage(
                s3Client,
                s3Presigner,
                s3Properties,
                imageFileValidator,
                objectKeyGenerator
        );
    }

    @Test
    @DisplayName("유효한 이미지 파일을 업로드하면 S3에 저장하고 Object Key와 메타데이터를 반환한다")
    void uploadImageStoresObjectAndReturnsMetadata() throws Exception {
        // given
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "profile.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );

        given(
                s3Client.putObject(
                        any(PutObjectRequest.class),
                        any(RequestBody.class)
                )
        ).willReturn(
                PutObjectResponse.builder()
                        .eTag("test-etag")
                        .build()
        );

        // when
        StoredFile result = s3FileStorage.upload(
                StorageDirectory.PROFILES,
                OWNER_ID,
                image
        );

        // then
        assertThat(result.objectKey()).isEqualTo(OBJECT_KEY);
        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.size()).isEqualTo(3L);

        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);

        verify(s3Client).putObject(
                requestCaptor.capture(),
                any(RequestBody.class)
        );

        PutObjectRequest request = requestCaptor.getValue();

        assertThat(request.bucket()).isEqualTo(BUCKET);
        assertThat(request.key()).isEqualTo(OBJECT_KEY);
        assertThat(request.contentType()).isEqualTo("image/jpeg");
        assertThat(request.contentLength()).isEqualTo(3L);
    }

    @Test
    @DisplayName("지원하지 않는 이미지 형식이면 S3를 호출하지 않고 예외를 발생시킨다")
    void uploadUnsupportedImageDoesNotCallS3() throws Exception {
        // given
        MockMultipartFile unsupportedImage = new MockMultipartFile(
                "image",
                "profile.gif",
                "image/gif",
                new byte[]{1, 2, 3}
        );

        // when & then
        assertThatThrownBy(() ->
                s3FileStorage.upload(
                        StorageDirectory.PROFILES,
                        OWNER_ID,
                        unsupportedImage
                )
        ).isInstanceOf(UnsupportedStorageFileTypeException.class);

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("Object Key로 Presigned GET URL을 생성한다")
    void generateReadUrlReturnsPresignedGetUrl() throws Exception {
        // given
        URL expectedUrl = URI.create(
                "https://test-storage-bucket.s3.ap-northeast-2.amazonaws.com/"
                        + OBJECT_KEY
                        + "?X-Amz-Signature=test-signature"
        ).toURL();

        given(
                s3Presigner.presignGetObject(
                        any(GetObjectPresignRequest.class)
                )
        ).willReturn(presignedGetObjectRequest);

        given(presignedGetObjectRequest.url())
                .willReturn(expectedUrl);

        // when
        String result = s3FileStorage.generateReadUrl(OBJECT_KEY);

        // then
        assertThat(result).isEqualTo(expectedUrl.toString());

        ArgumentCaptor<GetObjectPresignRequest> requestCaptor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);

        verify(s3Presigner).presignGetObject(
                requestCaptor.capture()
        );

        GetObjectPresignRequest request =
                requestCaptor.getValue();

        assertThat(request.signatureDuration())
                .isEqualTo(
                        Duration.ofSeconds(
                                PRESIGNED_URL_EXPIRATION_SECONDS
                        )
                );

        assertThat(request.getObjectRequest().bucket())
                .isEqualTo(BUCKET);

        assertThat(request.getObjectRequest().key())
                .isEqualTo(OBJECT_KEY);
    }

    @Test
    @DisplayName("Object Key로 S3 객체를 삭제한다")
    void deleteObjectRemovesObjectFromS3() throws Exception {
        // given
        given(
                s3Client.deleteObject(
                        any(DeleteObjectRequest.class)
                )
        ).willReturn(
                DeleteObjectResponse.builder().build()
        );

        // when
        s3FileStorage.delete(OBJECT_KEY);

        // then
        ArgumentCaptor<DeleteObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);

        verify(s3Client).deleteObject(
                requestCaptor.capture()
        );

        DeleteObjectRequest request =
                requestCaptor.getValue();

        assertThat(request.bucket()).isEqualTo(BUCKET);
        assertThat(request.key()).isEqualTo(OBJECT_KEY);
    }

    @Test
    @DisplayName("S3 업로드 호출이 실패하면 StorageUploadException을 발생시킨다")
    void uploadImageThrowsStorageUploadExceptionWhenS3Fails() throws Exception {
        // given
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "profile.jpg",
                "image/jpeg",
                new byte[] {1, 2, 3}
        );

        SdkClientException sdkException =
                SdkClientException.create("S3 upload failed");

        given(
                s3Client.putObject(
                        any(PutObjectRequest.class),
                        any(RequestBody.class)
                )
        ).willThrow(sdkException);

        // when & then
        assertThatThrownBy(() ->
                s3FileStorage.upload(
                        StorageDirectory.PROFILES,
                        OWNER_ID,
                        image
                )
        )
                .isInstanceOf(StorageUploadException.class)
                .hasCause(sdkException);
    }

    @Test
    @DisplayName("Presigned URL 생성이 실패하면 StorageReadUrlException을 발생시킨다")
    void generateReadUrlThrowsStorageReadUrlExceptionWhenPresigningFails()
            throws Exception {
        // given
        SdkClientException sdkException =
                SdkClientException.create("S3 presigning failed");

        given(
                s3Presigner.presignGetObject(
                        any(GetObjectPresignRequest.class)
                )
        ).willThrow(sdkException);

        // when & then
        assertThatThrownBy(() ->
                s3FileStorage.generateReadUrl(OBJECT_KEY)
        )
                .isInstanceOf(StorageReadUrlException.class)
                .hasCause(sdkException);
    }

    @Test
    @DisplayName("S3 객체 삭제가 실패하면 StorageDeleteException을 발생시킨다")
    void deleteObjectThrowsStorageDeleteExceptionWhenS3Fails()
            throws Exception {
        // given
        SdkClientException sdkException =
                SdkClientException.create("S3 delete failed");

        given(
                s3Client.deleteObject(
                        any(DeleteObjectRequest.class)
                )
        ).willThrow(sdkException);

        // when & then
        assertThatThrownBy(() ->
                s3FileStorage.delete(OBJECT_KEY)
        )
                .isInstanceOf(StorageDeleteException.class)
                .hasCause(sdkException);
    }

    @Test
    @DisplayName("Object Key가 null이면 조회 URL을 생성하지 않고 예외를 발생시킨다")
    void generateReadUrlRejectsNullObjectKey() throws Exception {
        // given
        String objectKey = null;

        // when & then
        assertThatThrownBy(() ->
                s3FileStorage.generateReadUrl(objectKey)
        ).isInstanceOf(InvalidStorageObjectKeyException.class);

        verifyNoInteractions(s3Presigner);
    }

    @Test
    @DisplayName("Object Key가 빈 문자열이면 조회 URL을 생성하지 않고 예외를 발생시킨다")
    void generateReadUrlRejectsEmptyObjectKey() throws Exception {
        // given
        String objectKey = "";

        // when & then
        assertThatThrownBy(() ->
                s3FileStorage.generateReadUrl(objectKey)
        ).isInstanceOf(InvalidStorageObjectKeyException.class);

        verifyNoInteractions(s3Presigner);
    }

    @Test
    @DisplayName("Object Key가 공백이면 조회 URL을 생성하지 않고 예외를 발생시킨다")
    void generateReadUrlRejectsBlankObjectKey() throws Exception {
        // given
        String objectKey = "   ";

        // when & then
        assertThatThrownBy(() ->
                s3FileStorage.generateReadUrl(objectKey)
        ).isInstanceOf(InvalidStorageObjectKeyException.class);

        verifyNoInteractions(s3Presigner);
    }

    @Test
    @DisplayName("Object Key가 null이면 S3 객체를 삭제하지 않고 예외를 발생시킨다")
    void deleteRejectsNullObjectKey() throws Exception {
        // given
        String objectKey = null;

        // when & then
        assertThatThrownBy(() ->
                s3FileStorage.delete(objectKey)
        ).isInstanceOf(InvalidStorageObjectKeyException.class);

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("Object Key가 빈 문자열이면 S3 객체를 삭제하지 않고 예외를 발생시킨다")
    void deleteRejectsEmptyObjectKey() throws Exception {
        // given
        String objectKey = "";

        // when & then
        assertThatThrownBy(() ->
                s3FileStorage.delete(objectKey)
        ).isInstanceOf(InvalidStorageObjectKeyException.class);

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("Object Key가 공백이면 S3 객체를 삭제하지 않고 예외를 발생시킨다")
    void deleteRejectsBlankObjectKey() throws Exception {
        // given
        String objectKey = "   ";

        // when & then
        assertThatThrownBy(() ->
                s3FileStorage.delete(objectKey)
        ).isInstanceOf(InvalidStorageObjectKeyException.class);

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("업로드 파일 데이터를 읽지 못하면 StorageUploadException을 발생시키고 S3를 호출하지 않는다")
    void uploadImageThrowsStorageUploadExceptionWhenReadingFileFails()
            throws Exception {
        // given
        MultipartFile unreadableFile = mock(MultipartFile.class);

        IOException ioException =
                new IOException("file read failed");

        given(unreadableFile.isEmpty())
                .willReturn(false);

        given(unreadableFile.getSize())
                .willReturn(3L);

        given(unreadableFile.getContentType())
                .willReturn("image/jpeg");

        given(unreadableFile.getBytes())
                .willThrow(ioException);

        // when & then
        assertThatThrownBy(() ->
                s3FileStorage.upload(
                        StorageDirectory.PROFILES,
                        OWNER_ID,
                        unreadableFile
                )
        )
                .isInstanceOf(StorageUploadException.class)
                .hasCause(ioException);

        verifyNoInteractions(s3Client);
    }
}