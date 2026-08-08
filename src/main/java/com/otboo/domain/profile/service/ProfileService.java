package com.otboo.domain.profile.service;

import com.otboo.domain.profile.dto.ProfileDto;
import com.otboo.domain.profile.dto.ProfileUpdateRequest;
import com.otboo.domain.profile.entity.Profile;
import com.otboo.domain.profile.exception.LocationResolutionFailedException;
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

  public ProfileDto getProfile(UUID userId) {
    Profile profile = profileRepository.findById(userId)
        .orElseThrow(() -> new ProfileNotFoundException(userId));

    return ProfileDto.from(
        profile,
        resolveImageUrl(profile.getImageKey())
    );
  }

  // 외부 위치 조회와 S3 업로드 중에는 DB 트랜잭션을 점유하지 않는다.
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public ProfileDto updateProfile(
      UUID userId,
      ProfileUpdateRequest request,
      MultipartFile image
  ) {
    Profile currentProfile = profileRepository.findById(userId)
        .orElseThrow(() -> new ProfileNotFoundException(userId));

    WeatherAPILocation location = resolveLocation(request);

    String newImageKey = null;

    if (image != null) {
      StoredFile storedFile = fileStorage.upload(
          StorageDirectory.PROFILES,
          userId,
          image
      );

      newImageKey = storedFile.objectKey();
    }

    String resultImageKey =
        newImageKey != null
            ? newImageKey
            : currentProfile.getImageKey();

    try {
      // 기존 이미지 삭제(AFTER_COMMIT) 및 새 이미지 정리(AFTER_ROLLBACK)는
      // ProfileUpdateTransactionalService가 FileReplacementEvent 발행을 통해 담당한다. (#108)
      return profileUpdateTransactionalService.update(
          userId,
          request,
          location,
          newImageKey,
          resolveImageUrl(resultImageKey)
      );
    } catch (RuntimeException e) {
      // update() 진입 자체(예: 동시 삭제로 인한 findById 실패)를 포함해 어느 시점에
      // 실패하더라도, 이벤트가 아예 등록되지 못했을 수 있으므로 여기서 한 번 더
      // 새로 업로드한 이미지를 직접 정리한다. (이벤트로 이미 정리된 경우와 중복될
      // 수 있으나 S3 삭제는 멱등하므로 안전하다.)
      if (newImageKey != null) {
        log.warn(
            "프로필 갱신 실패로 신규 업로드 이미지를 정리합니다. userId={}, newImageKey={}",
            userId, newImageKey, e
        );
        fileDeletionRetryService.deleteWithRetry(newImageKey);
      }
      throw e;
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