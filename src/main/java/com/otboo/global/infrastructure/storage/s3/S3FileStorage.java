package com.otboo.global.infrastructure.storage.s3;

import com.otboo.global.infrastructure.storage.FileStorage;
import com.otboo.global.infrastructure.storage.ImageContentType;
import com.otboo.global.infrastructure.storage.StorageDirectory;
import com.otboo.global.infrastructure.storage.StoredFile;
import com.otboo.global.infrastructure.storage.ThumbnailGenerator;
import com.otboo.global.infrastructure.storage.config.S3Properties;
import com.otboo.global.infrastructure.storage.exception.StorageDeleteException;
import com.otboo.global.infrastructure.storage.exception.StorageReadUrlException;
import com.otboo.global.infrastructure.storage.exception.StorageUploadException;
import com.otboo.global.infrastructure.storage.exception.InvalidStorageObjectKeyException;
import com.otboo.global.infrastructure.storage.validation.ImageFileValidator;
import com.otboo.global.infrastructure.storage.validation.ValidatedImageFile;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
public class S3FileStorage implements FileStorage {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;
    private final ImageFileValidator imageFileValidator;
    private final S3ObjectKeyGenerator objectKeyGenerator;
    private final ThumbnailGenerator thumbnailGenerator;

    public S3FileStorage(
        S3Client s3Client,
        S3Presigner s3Presigner,
        S3Properties s3Properties,
        ImageFileValidator imageFileValidator,
        S3ObjectKeyGenerator objectKeyGenerator,
        ThumbnailGenerator thumbnailGenerator
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.s3Properties = s3Properties;
        this.imageFileValidator = imageFileValidator;
        this.objectKeyGenerator = objectKeyGenerator;
        this.thumbnailGenerator = thumbnailGenerator;
    }

    @Override
    public StoredFile upload(
        StorageDirectory directory,
        UUID ownerId,
        MultipartFile file
    ) {
        ValidatedImageFile validatedImage =
            imageFileValidator.validate(file);
        ImageContentType imageContentType =
            validatedImage.contentType();
        String objectKey = objectKeyGenerator.generate(
            directory,
            ownerId,
            imageContentType.getExtension()
        );

        putObject(objectKey, validatedImage.bytes(), imageContentType, validatedImage.size());

        // 원본 검증이 끝난 바이트를 재사용해 썸네일을 생성하고, 별도 키로 업로드한다.
        byte[] thumbnailBytes = thumbnailGenerator.generate(validatedImage.bytes(), imageContentType);
        String thumbnailKey = objectKeyGenerator.generateThumbnail(
            directory,
            ownerId,
            imageContentType.getExtension()
        );
        putObject(thumbnailKey, thumbnailBytes, imageContentType, thumbnailBytes.length);

        return new StoredFile(
            objectKey,
            imageContentType.getContentType(),
            validatedImage.size(),
            thumbnailKey
        );
    }

    private void putObject(String objectKey, byte[] bytes, ImageContentType contentType, long size) {
        PutObjectRequest putObjectRequest =
            PutObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(objectKey)
                .contentType(contentType.getContentType())
                .contentLength(size)
                .build();
        RequestBody requestBody = RequestBody.fromBytes(bytes);
        try {
            s3Client.putObject(putObjectRequest, requestBody);
        } catch (SdkException exception) {
            throw new StorageUploadException(exception);
        }
    }

    @Override
    public String generateReadUrl(String objectKey) {
        validateObjectKey(objectKey);
        GetObjectRequest getObjectRequest =
            GetObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(objectKey)
                .build();
        GetObjectPresignRequest presignRequest =
            GetObjectPresignRequest.builder()
                .signatureDuration(
                    Duration.ofSeconds(
                        s3Properties.presignedUrlExpirationSeconds()
                    )
                )
                .getObjectRequest(getObjectRequest)
                .build();
        try {
            return s3Presigner
                .presignGetObject(presignRequest)
                .url()
                .toString();
        } catch (SdkException exception) {
            throw new StorageReadUrlException(exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        validateObjectKey(objectKey);
        DeleteObjectRequest deleteObjectRequest =
            DeleteObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(objectKey)
                .build();
        try {
            s3Client.deleteObject(deleteObjectRequest);
        } catch (SdkException exception) {
            throw new StorageDeleteException(exception);
        }
    }

    private void validateObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new InvalidStorageObjectKeyException();
        }
    }
}