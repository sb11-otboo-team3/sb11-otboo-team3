package com.otboo.domain.profile.service;

import com.otboo.domain.profile.dto.ProfileDto;
import com.otboo.domain.profile.dto.ProfileUpdateRequest;
import com.otboo.domain.profile.entity.Profile;
import com.otboo.domain.profile.exception.LocationResolutionFailedException;
import com.otboo.domain.profile.exception.ProfileAccessDeniedException;
import com.otboo.domain.profile.exception.ProfileConcurrentUpdateException;
import com.otboo.domain.profile.exception.ProfileNotFoundException;
import com.otboo.domain.profile.repository.ProfileRepository;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.service.LocationResolver;
import com.otboo.global.infrastructure.storage.FileStorage;
import com.otboo.global.infrastructure.storage.StorageDirectory;
import com.otboo.global.infrastructure.storage.StoredFile;
import com.otboo.global.infrastructure.storage.event.FileDeletionRetryService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

  private final ProfileRepository profileRepository;
  private final LocationResolver locationResolver;
  private final FileStorage fileStorage;
  private final ProfileUpdateTransactionalService profileUpdateTransactionalService;
  private final FileDeletionRetryService fileDeletionRetryService;

  public ProfileDto getProfile(UUID userId, UUID currentUserId) {
    Profile profile = profileRepository.findById(userId)
        .orElseThrow(() -> new ProfileNotFoundException(userId));

    return ProfileDto.from(
        profile,
        resolveImageUrl(profile.getImageKey()),
        resolveImageUrl(profile.getThumbnailKey())
    );
  }

  // 외부 위치 조회와 S3 업로드 중에는 DB 트랜잭션을 점유하지 않는다.
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public ProfileDto updateProfile(
      UUID userId,
      UUID currentUserId,
      ProfileUpdateRequest request,
      MultipartFile image
  ) {
    validateOwnership(userId, currentUserId);

    Profile currentProfile = profileRepository.findById(userId)
        .orElseThrow(() -> new ProfileNotFoundException(userId));

    WeatherAPILocation location = resolveLocation(request);

    String newImageKey = null;
    String newThumbnailKey = null;

    try {
      if (image != null) {
        StoredFile storedFile = uploadImageSafely(userId, image);
        newImageKey = storedFile.objectKey();
        newThumbnailKey = storedFile.thumbnailKey();
      }

      String resultImageKey =
          newImageKey != null
              ? newImageKey
              : currentProfile.getImageKey();
      String resultThumbnailKey =
          newThumbnailKey != null
              ? newThumbnailKey
              : currentProfile.getThumbnailKey();

      // 기존 이미지 삭제(AFTER_COMMIT) 및 새 이미지 정리(AFTER_ROLLBACK)는
      // ProfileUpdateTransactionalService가 FileReplacementEvent 발행을 통해 담당한다. (#108)
      return profileUpdateTransactionalService.update(
          userId,
          request,
          location,
          newImageKey,
          newThumbnailKey,
          resolveImageUrl(resultImageKey),
          resolveImageUrl(resultThumbnailKey)
      );
    } catch (ObjectOptimisticLockingFailureException e) {
      // 동시 수정으로 낙관적 락 충돌이 발생한 경우. 새로 업로드한 이미지는
      // 정리하고, 클라이언트가 재시도할 수 있도록 명확한 예외로 변환한다. (#121)
      cleanUpNewFiles(userId, newImageKey, newThumbnailKey, e,
          "낙관적 락 충돌로 프로필 갱신이 실패하여 신규 업로드 파일을 정리합니다.");
      throw new ProfileConcurrentUpdateException();
    } catch (RuntimeException e) {
      // update() 진입 자체(예: 동시 삭제로 인한 findById 실패)뿐 아니라, 원본
      // 업로드는 성공했지만 썸네일 생성/업로드가 실패한 경우(uploadWithThumbnail
      // 자체에서 발생한 예외)까지 포함해 어느 시점에 실패하더라도, 여기서 한 번
      // 더 새로 업로드한 파일을 직접 정리한다. (#256 - 기존에는 uploadWithThumbnail
      // 호출이 try 블록 밖에 있어 이 케이스가 전혀 정리되지 않고 원본이 orphan으로
      // 남는 문제가 있었음)
      cleanUpNewFiles(userId, newImageKey, newThumbnailKey, e,
          "프로필 갱신 실패로 신규 업로드 파일을 정리합니다.");
      throw e;
    }
  }

  private StoredFile uploadImageSafely(UUID userId, MultipartFile image) {
    try {
      return fileStorage.uploadWithThumbnail(StorageDirectory.PROFILES, userId, image);
    } catch (RuntimeException e) {
      // uploadWithThumbnail() 내부에서 원본 업로드는 성공했으나 썸네일
      // 생성/업로드 단계에서 실패했을 가능성이 있다. 이 경우 원본의
      // objectKey를 이 시점엔 알 수 없어(예외로 인해 반환값을 못 받음)
      // 자동으로 정리할 수 없으므로, 운영자가 S3를 확인할 수 있도록
      // 명확한 경고 로그를 남긴다. 근본적인 원자성 보장(원본 업로드 성공 +
      // 썸네일 실패 시 원본 자동 롤백)은 FileStorage 계층(S3FileStorage)에서
      // 다뤄야 하며, 별도 후속 작업으로 분리한다. (#256)
      log.error(
          "프로필 이미지 업로드 중 실패가 발생했습니다. 원본 이미지가 S3에 이미 "
              + "업로드되었으나 추적되지 않는 상태로 남아있을 수 있습니다(orphan 의심). "
              + "userId={}, fileName={}",
          userId, image.getOriginalFilename(), e
      );
      throw e;
    }
  }

  private void cleanUpNewFiles(
      UUID userId, String newImageKey, String newThumbnailKey, RuntimeException e, String logMessage
  ) {
    if (newImageKey != null) {
      log.warn(logMessage + " userId={}, newImageKey={}", userId, newImageKey, e);
      fileDeletionRetryService.deleteWithRetry(newImageKey);
    }
    if (newThumbnailKey != null) {
      log.warn(logMessage + " userId={}, newThumbnailKey={}", userId, newThumbnailKey, e);
      fileDeletionRetryService.deleteWithRetry(newThumbnailKey);
    }
  }

  private void validateOwnership(UUID userId, UUID currentUserId) {
    if (!userId.equals(currentUserId)) {
      throw new ProfileAccessDeniedException();
    }
  }

  private WeatherAPILocation resolveLocation(ProfileUpdateRequest request) {
    if (request.location() == null
        || request.location().latitude() == null
        || request.location().longitude() == null) {
      return null;
    }

    double latitude = request.location().latitude();
    double longitude = request.location().longitude();

    WeatherAPILocation location =
        locationResolver.resolve(latitude, longitude).block();

    if (location == null) {
      throw new LocationResolutionFailedException(latitude, longitude);
    }

    return location;
  }

  private String resolveImageUrl(String imageKey) {
    if (imageKey == null || imageKey.isBlank()) {
      return null;
    }

    return fileStorage.generateReadUrl(imageKey);
  }
}