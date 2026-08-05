package com.otboo.global.infrastructure.storage.s3;

import com.otboo.global.infrastructure.storage.FileStorage;
import com.otboo.global.infrastructure.storage.ImageContentType;
import com.otboo.global.infrastructure.storage.StorageDirectory;
import com.otboo.global.infrastructure.storage.StoredFile;
import com.otboo.global.infrastructure.storage.config.S3Properties;
import com.otboo.global.infrastructure.storage.exception.StorageUploadException;
import com.otboo.global.infrastructure.storage.validation.ImageFileValidator;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
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

    public S3FileStorage(
            S3Client s3Client,
            S3Presigner s3Presigner,
            S3Properties s3Properties,
            ImageFileValidator imageFileValidator,
            S3ObjectKeyGenerator objectKeyGenerator
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.s3Properties = s3Properties;
        this.imageFileValidator = imageFileValidator;
        this.objectKeyGenerator = objectKeyGenerator;
    }

    @Override
    public StoredFile upload(
            StorageDirectory directory,
            UUID ownerId,
            MultipartFile file
    ) {
        imageFileValidator.validate(file);

        ImageContentType imageContentType =
                ImageContentType.from(file.getContentType());

        String objectKey = objectKeyGenerator.generate(
                directory,
                ownerId,
                imageContentType.getExtension()
        );

        PutObjectRequest putObjectRequest =
                PutObjectRequest.builder()
                        .bucket(s3Properties.bucket())
                        .key(objectKey)
                        .contentType(imageContentType.getContentType())
                        .contentLength(file.getSize())
                        .build();

        s3Client.putObject(
                putObjectRequest,
                createRequestBody(file)
        );

        return new StoredFile(
                objectKey,
                imageContentType.getContentType(),
                file.getSize()
        );
    }

    @Override
    public String generateReadUrl(String objectKey) {
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

        return s3Presigner
                .presignGetObject(presignRequest)
                .url()
                .toString();
    }

    @Override
    public void delete(String objectKey) {
        DeleteObjectRequest deleteObjectRequest =
                DeleteObjectRequest.builder()
                        .bucket(s3Properties.bucket())
                        .key(objectKey)
                        .build();
        s3Client.deleteObject(deleteObjectRequest);
    }

    private RequestBody createRequestBody(MultipartFile file) {
        try {
            return RequestBody.fromBytes(file.getBytes());
        } catch (IOException exception) {
            throw new StorageUploadException(exception);
        }
    }
}

