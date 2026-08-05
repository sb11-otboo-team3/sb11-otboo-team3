# 공통 S3 파일 저장 모듈

## 1. 개요

프로필 이미지와 의상 이미지를 동일한 방식으로 저장하기 위한 공통 S3 파일 저장 모듈입니다.

프론트엔드는 Spring Boot 백엔드에 Multipart 요청을 보내고, 백엔드는 이미지 파일을 검증한 뒤 AWS SDK for Java v2를 통해 S3에 업로드합니다.

도메인 코드는 AWS SDK 또는 `S3FileStorage` 구현체에 직접 의존하지 않고 공통 `FileStorage` 인터페이스를 통해 다음 기능을 사용합니다.

- Multipart 이미지 파일 업로드
- Object Key 기반 S3 객체 삭제
- Object Key 기반 Presigned GET URL 생성

프로필 및 의상 Controller·Service의 실제 이미지 연동은 각 도메인 담당 이슈에서 진행합니다.

---

## 2. 저장 정책

| 구분 | 정책 |
| --- | --- |
| 업로드 | 프론트엔드가 백엔드에 Multipart 요청을 보내고, 백엔드가 검증 후 S3에 업로드 |
| 조회 | 데이터베이스에 저장된 Object Key를 기준으로 Presigned GET URL 생성 |
| 삭제 | 전체 URL이 아닌 Object Key를 전달받아 S3 객체 삭제 |
| DB 저장값 | 전체 S3 URL이나 Presigned URL이 아닌 Object Key |
| API 응답 | 요청 시점에 Object Key를 기준으로 생성한 Presigned GET URL |
| AWS 인증 | AWS SDK 기본 자격 증명 공급망 |
| 운영 환경 인증 | ECS Task Role |

Presigned GET URL은 만료되는 임시 값이므로 데이터베이스에 저장하지 않습니다.

### 저장값 예시

데이터베이스에는 다음과 같은 Object Key만 저장합니다.

```text
profiles/{userId}/{uuid}.jpg
clothes/{userId}/{uuid}.webp
```

다음 값은 데이터베이스에 저장하지 않습니다.

```text
https://bucket-name.s3.ap-northeast-2.amazonaws.com/...
https://...?X-Amz-Signature=...
```

---

## 3. 패키지 구조

```text
com.otboo.global.infrastructure.storage
├── FileStorage.java
├── ImageContentType.java
├── StorageDirectory.java
├── StoredFile.java
├── config
│   ├── ImageStorageProperties.java
│   ├── S3Properties.java
│   └── StorageConfig.java
├── exception
│   ├── EmptyStorageFileException.java
│   ├── InvalidStorageObjectKeyException.java
│   ├── StorageDeleteException.java
│   ├── StorageFileSizeExceededException.java
│   ├── StorageReadUrlException.java
│   ├── StorageUploadException.java
│   └── UnsupportedStorageFileTypeException.java
├── s3
│   ├── S3FileStorage.java
│   └── S3ObjectKeyGenerator.java
└── validation
    └── ImageFileValidator.java
```

---

## 4. 공통 저장 인터페이스

도메인에서는 구현체인 `S3FileStorage`가 아니라 `FileStorage` 인터페이스에 의존합니다.

```java
public interface FileStorage {

  StoredFile upload(
      StorageDirectory directory,
      UUID ownerId,
      MultipartFile file
  );

  String generateReadUrl(String objectKey);

  void delete(String objectKey);
}
```

### 메서드 역할

| 메서드 | 역할 |
| --- | --- |
| `upload` | 이미지 검증, Object Key 생성 및 S3 업로드 |
| `generateReadUrl` | Object Key 기반 Presigned GET URL 생성 |
| `delete` | Object Key 기반 S3 객체 삭제 |

---

## 5. 업로드 결과

파일 업로드 결과는 `StoredFile`로 반환됩니다.

```java
public record StoredFile(
    String objectKey,
    String contentType,
    long size
) {
}
```

| 필드 | 설명 |
| --- | --- |
| `objectKey` | 데이터베이스에 저장할 S3 Object Key |
| `contentType` | 서버에서 검증한 이미지 Content-Type |
| `size` | 업로드된 파일 크기(Byte) |

`StoredFile`에는 전체 S3 URL이나 Presigned GET URL이 포함되지 않습니다.

Presigned GET URL은 이미지 조회 API 응답을 생성할 때 별도로 발급합니다.

---

## 6. 이미지 업로드 처리 흐름

```text
1. 도메인 Service가 MultipartFile을 FileStorage에 전달
2. 빈 파일 여부 검증
3. 최대 파일 크기 검증
4. Content-Type 검증
5. Content-Type을 안전한 확장자로 변환
6. UUID 기반 Object Key 생성
7. AWS SDK로 S3 업로드
8. Object Key와 메타데이터를 StoredFile로 반환
9. 도메인은 StoredFile.objectKey()만 DB에 저장
```

원본 파일명은 Object Key 생성에 사용하지 않습니다.

예를 들어 원본 파일명이 다음과 같더라도:

```text
my-profile-image.final.jpeg
```

서버는 검증된 Content-Type과 UUID를 이용해 다음과 같은 Key를 생성합니다.

```text
profiles/{userId}/{uuid}.jpg
```

---

## 7. Object Key 규칙

### 프로필 이미지

```text
profiles/{userId}/{uuid}.{extension}
```

예시:

```text
profiles/11111111-1111-1111-1111-111111111111/22222222-2222-2222-2222-222222222222.jpg
```

### 의상 이미지

```text
clothes/{userId}/{uuid}.{extension}
```

예시:

```text
clothes/11111111-1111-1111-1111-111111111111/33333333-3333-3333-3333-333333333333.webp
```

### Prefix 선택

```java
StorageDirectory.PROFILES
StorageDirectory.CLOTHES
```

기존 S3 권한 범위인 `profiles/*`, `clothes/*` 이외의 Prefix는 추가하지 않습니다.

---

## 8. 지원 이미지 형식

파일 확장자는 원본 파일명이 아니라 검증된 Content-Type을 기준으로 결정합니다.

| Content-Type | 저장 확장자 |
| --- | --- |
| `image/jpeg` | `jpg` |
| `image/png` | `png` |
| `image/webp` | `webp` |

다음 형식은 현재 지원하지 않습니다.

```text
image/gif
image/svg+xml
image/bmp
image/tiff
```

지원하지 않는 형식은 S3 호출 전에 `UnsupportedStorageFileTypeException`으로 차단됩니다.

---

## 9. 파일 크기 제한

기본 최대 이미지 파일 크기는 10 MiB입니다.

```text
10 MiB = 10,485,760 bytes
```

다음 환경변수로 변경할 수 있습니다.

```dotenv
IMAGE_MAX_FILE_SIZE_BYTES=10485760
```

파일 크기 정책은 다음과 같습니다.

```text
파일 크기 < 최대 크기  → 허용
파일 크기 = 최대 크기  → 허용
파일 크기 > 최대 크기  → 거부
```

제한 용량을 초과한 파일은 S3 호출 전에 `StorageFileSizeExceededException`으로 차단됩니다.

---

## 10. 프로필 이미지 업로드 적용 방법

아래 코드는 공통 모듈 사용 방법을 설명하기 위한 예시입니다.

실제 적용 시 프로필 도메인의 기존 Service, Entity, DTO 구조에 맞게 수정해야 합니다.

```java
@Service
public class ProfileImageService {

  private final FileStorage fileStorage;

  public ProfileImageService(FileStorage fileStorage) {
    this.fileStorage = fileStorage;
  }

  public String uploadProfileImage(
      UUID userId,
      MultipartFile image
  ) {
    StoredFile storedFile = fileStorage.upload(
        StorageDirectory.PROFILES,
        userId,
        image
    );

    return storedFile.objectKey();
  }
}
```

업로드 결과에서 다음 값만 Entity 또는 데이터베이스에 저장합니다.

```java
String imageKey = storedFile.objectKey();
```

다음 값은 저장하지 않습니다.

```java
String imageUrl = fileStorage.generateReadUrl(imageKey);
```

`imageUrl`은 API 응답을 생성하는 시점에만 사용합니다.

---

## 11. 의상 이미지 업로드 적용 방법

의상 이미지도 동일한 `FileStorage` 인터페이스를 사용합니다.

프로필과의 차이는 `StorageDirectory.CLOTHES`를 전달한다는 점입니다.

```java
@Service
public class ClothesImageService {

  private final FileStorage fileStorage;

  public ClothesImageService(FileStorage fileStorage) {
    this.fileStorage = fileStorage;
  }

  public String uploadClothesImage(
      UUID userId,
      MultipartFile image
  ) {
    StoredFile storedFile = fileStorage.upload(
        StorageDirectory.CLOTHES,
        userId,
        image
    );

    return storedFile.objectKey();
  }
}
```

의상 Entity에도 전체 URL이 아닌 Object Key만 저장합니다.

```java
String imageKey = storedFile.objectKey();
```

---

## 12. API 응답용 이미지 URL 생성

데이터베이스에 저장된 Object Key를 DTO의 `imageUrl`로 변환할 때 Presigned GET URL을 생성합니다.

```java
String imageUrl = null;

if (imageKey != null && !imageKey.isBlank()) {
  imageUrl = fileStorage.generateReadUrl(imageKey);
}
```

DTO 예시:

```java
public record ProfileDto(
    UUID id,
    String name,
    String imageUrl
) {
}
```

처리 결과는 다음과 같습니다.

```text
DB 저장값
profiles/{userId}/{uuid}.jpg

API 응답값
https://...X-Amz-Signature=...
```

Swagger 응답의 `imageUrl`은 요청 시점에 발급한 Presigned GET URL을 의미합니다.

Presigned URL의 만료 시간이 지나면 다음 API 요청에서 새로운 URL을 생성합니다.

---

## 13. 이미지 삭제

이미지를 삭제할 때는 전체 URL이 아닌 Object Key를 전달합니다.

```java
if (imageKey != null && !imageKey.isBlank()) {
  fileStorage.delete(imageKey);
}
```

### 잘못된 사용

```java
fileStorage.delete(imageUrl);
```

### 올바른 사용

```java
fileStorage.delete(imageKey);
```

다음 Object Key는 S3 SDK 호출 전에 차단됩니다.

```text
null
""
"   "
```

잘못된 Object Key가 전달되면 `InvalidStorageObjectKeyException`이 발생합니다.

---

## 14. 이미지 교체 순서

프로필 또는 의상 이미지를 교체할 때는 다음 순서를 사용합니다.

```text
1. 기존 Object Key 보관
2. 새 이미지 업로드
3. 새 Object Key 반환 확인
4. 데이터베이스의 Object Key 변경
5. 기존 Object Key로 이전 S3 객체 삭제
```

예시:

```java
String oldImageKey = profile.getImageKey();

StoredFile newImage = fileStorage.upload(
    StorageDirectory.PROFILES,
    userId,
    multipartFile
);

profile.updateImageKey(newImage.objectKey());
profileRepository.save(profile);

if (oldImageKey != null && !oldImageKey.isBlank()) {
  fileStorage.delete(oldImageKey);
}
```

새 이미지 업로드가 성공하기 전에 기존 이미지를 먼저 삭제하면 안 됩니다.

```text
잘못된 순서
기존 이미지 삭제
→ 새 이미지 업로드 실패
→ 기존 이미지와 새 이미지가 모두 없는 상태 발생
```

```text
권장 순서
새 이미지 업로드 성공
→ DB Object Key 변경
→ 기존 이미지 삭제
```

기존 이미지 삭제 실패에 대한 재시도 또는 보상 처리는 각 도메인 Service에서 결정합니다.

---

## 15. 환경변수

### 공통 설정

```dotenv
AWS_REGION=ap-northeast-2
S3_BUCKET=YOUR_BUCKET_NAME
S3_PRESIGNED_URL_EXPIRATION_SECONDS=600
IMAGE_MAX_FILE_SIZE_BYTES=10485760
```

| 환경변수 | 설명 | 기본값 |
| --- | --- | --- |
| `AWS_REGION` | S3 Region | `ap-northeast-2` |
| `S3_BUCKET` | S3 Bucket 이름 | 운영 환경에서는 필수 |
| `S3_PRESIGNED_URL_EXPIRATION_SECONDS` | Presigned URL 만료 시간(초) | `600` |
| `IMAGE_MAX_FILE_SIZE_BYTES` | 최대 이미지 파일 크기(Byte) | `10485760` |

운영 환경에서는 `S3_BUCKET`이 누락되면 애플리케이션 시작 단계에서 실패하도록 구성합니다.

---

## 16. AWS 인증 방식

고정된 Access Key와 Secret Key를 Java 코드 또는 저장소에 직접 설정하지 않습니다.

### 로컬 환경

AWS SDK의 기본 자격 증명 공급망을 사용합니다.

환경에 따라 다음 인증정보가 사용될 수 있습니다.

```text
시스템 속성
환경변수
AWS 설정 파일
AWS CLI Profile
컨테이너 자격 증명
인스턴스 역할
```

로컬 개발 시 개인 인증정보를 저장소에 커밋하면 안 됩니다.

### 운영 환경

운영 ECS 환경에서는 ECS Task Role을 통해 S3에 접근합니다.

```text
ECS Task
→ ECS Task Role
→ S3 profiles/*, clothes/* 접근
```

실제 ECS Task Role을 이용한 S3 접근 검증은 최초 배포 이슈에서 진행합니다.

---

## 17. Presigned GET URL

S3 Bucket과 객체는 공개하지 않고, 조회 시 만료 시간이 있는 Presigned GET URL을 생성합니다.

기본 만료 시간은 600초입니다.

```dotenv
S3_PRESIGNED_URL_EXPIRATION_SECONDS=600
```

Presigned URL은 다음 특성을 가집니다.

- 일정 시간이 지나면 만료됩니다.
- 데이터베이스에 저장하지 않습니다.
- API 응답 생성 시점에 발급합니다.
- 같은 Object Key라도 요청 시점에 따라 URL이 달라질 수 있습니다.

---

## 18. 예외

| 예외 | 발생 조건 |
| --- | --- |
| `EmptyStorageFileException` | 파일이 null이거나 비어 있음 |
| `UnsupportedStorageFileTypeException` | 허용하지 않는 Content-Type |
| `StorageFileSizeExceededException` | 최대 파일 크기 초과 |
| `InvalidStorageObjectKeyException` | Object Key가 null, 빈 문자열 또는 공백 |
| `StorageUploadException` | Multipart 파일 읽기 또는 S3 업로드 실패 |
| `StorageReadUrlException` | Presigned GET URL 생성 실패 |
| `StorageDeleteException` | S3 객체 삭제 실패 |

파일 검증 예외는 S3 호출 전에 발생합니다.

AWS SDK 호출 중 발생한 예외는 저장 모듈의 공통 예외로 변환합니다.

### 업로드 예외 처리 흐름

```text
잘못된 파일 입력
→ 파일 검증 예외
→ S3 호출 없음
```

```text
정상 파일이지만 파일 읽기 실패
→ StorageUploadException
→ S3 호출 없음
```

```text
S3 SDK 업로드 실패
→ StorageUploadException
```

---

## 19. 테스트 범위

공통 저장 모듈은 실제 AWS를 호출하지 않고 `S3Client`와 `S3Presigner`를 Mock 처리하여 테스트합니다.

검증하는 주요 항목은 다음과 같습니다.

### Object Key

- 프로필 Prefix 규칙
- 의상 Prefix 규칙
- 사용자 UUID 포함
- 객체 UUID 포함
- 검증된 확장자 사용
- 원본 파일명 미사용

### 파일 검증

- JPEG 허용
- PNG 허용
- WebP 허용
- 빈 파일 차단
- Content-Type 누락 차단
- 지원하지 않는 Content-Type 차단
- 최대 크기 초과 차단
- 최대 크기와 동일한 파일 허용

### S3 처리

- 정상 이미지 업로드
- Object Key 반환
- Content-Type 전달
- Content-Length 전달
- 정상 객체 삭제
- Presigned GET URL 생성
- Presigned URL 만료 시간 적용
- 지정한 Object Key가 조회 요청에 전달되는지 확인
- 지정한 Object Key가 삭제 요청에 전달되는지 확인

### 예외 처리

- S3 업로드 실패 변환
- Presigned URL 생성 실패 변환
- S3 삭제 실패 변환
- Multipart 파일 읽기 실패 변환
- 파일 읽기 실패 시 S3 미호출
- 잘못된 Object Key 입력 시 SDK 미호출

### 설정

- S3 설정값 바인딩
- 이미지 설정값 바인딩
- 필수 Bucket 설정 검증
- Presigned URL 만료 시간 검증
- 최대 이미지 크기 검증
- `S3Client` Bean 등록
- `S3Presigner` Bean 등록
- `ImageFileValidator` Bean 등록

---

## 20. 보안 확인

저장소와 이번 기능 브랜치에서 실제 AWS Access Key 패턴이 포함되지 않았는지 확인합니다.

```bash
git grep -nE \
  'AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16}'
```

```bash
git diff develop...HEAD | grep -nE \
  'AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16}'
```

환경변수 이름이 문서에 언급될 수는 있지만 실제 인증정보 값이 포함되어서는 안 됩니다.

```bash
git grep -nEi \
  'aws_access_key_id|aws_secret_access_key|AWS_ACCESS_KEY_ID|AWS_SECRET_ACCESS_KEY'
```

다음은 환경변수 이름이므로 그 자체로 실제 인증정보는 아닙니다.

```text
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
```

검색 결과에 실제 Key 값이 포함돼 있는지는 별도로 확인해야 합니다.

---

## 21. S3 CORS

현재 이미지 업로드는 브라우저에서 S3로 직접 전송하는 방식이 아닙니다.

```text
프론트엔드
→ Spring Boot 백엔드
→ S3
```

브라우저가 S3 Upload API를 직접 호출하지 않으므로 이번 공통 모듈 이슈에서는 S3 Upload CORS를 추가하지 않습니다.

향후 Presigned PUT URL을 사용하는 직접 업로드 방식으로 변경한다면 S3 CORS와 업로드 정책을 별도로 검토해야 합니다.

---

## 22. 이번 모듈의 범위

### 포함

- Multipart 이미지 파일 검증
- Content-Type 기반 확장자 결정
- UUID 기반 Object Key 생성
- S3 이미지 업로드
- Object Key 기반 S3 객체 삭제
- Object Key 기반 Presigned GET URL 생성
- AWS SDK 예외 변환
- 공통 `FileStorage` 인터페이스 제공
- S3 설정 및 이미지 제한 설정
- Mock 기반 단위 테스트
- 프로필·의상 도메인 적용 방법 문서화

### 포함하지 않음

- 프로필 Controller 실제 이미지 연동
- 프로필 Service 실제 이미지 연동
- 의상 Controller 실제 이미지 연동
- 의상 Service 실제 이미지 연동
- 프로필 및 의상 Entity·DB 컬럼 변경
- 브라우저에서 S3로 직접 업로드
- Presigned PUT URL 업로드
- S3 Upload CORS 설정
- ECS Task Role 실제 연결 테스트
- 삭제 실패에 대한 재시도 또는 보상 작업

도메인의 실제 이미지 연동은 각 도메인 담당 이슈에서 진행합니다.

ECS 환경에서 Task Role을 사용한 실제 S3 접근은 최초 배포 이슈에서 검증합니다.