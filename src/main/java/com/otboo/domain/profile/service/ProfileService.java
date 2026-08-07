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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

  private final ProfileRepository profileRepository;
  private final LocationResolver locationResolver;
  private final FileStorage fileStorage;
  private final ProfileUpdateTransactionalService profileUpdateTransactionalService;

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

    String oldImageKey = currentProfile.getImageKey();

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
            : oldImageKey;

    ProfileDto result = profileUpdateTransactionalService.update(
        userId,
        request,
        location,
        newImageKey,
        resolveImageUrl(resultImageKey)
    );

    // 현재 PR의 기존 동작 유지.
    // AFTER_COMMIT/AFTER_ROLLBACK 및 Retry 처리는 후속 브랜치에서 공통화한다.
    if (newImageKey != null
        && oldImageKey != null
        && !oldImageKey.isBlank()) {
      fileStorage.delete(oldImageKey);
    }

    return result;
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