package com.otboo.domain.profile.service;

import com.otboo.domain.profile.dto.ProfileDto;
import com.otboo.domain.profile.dto.ProfileUpdateRequest;
import com.otboo.domain.profile.entity.Profile;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

  private final ProfileRepository profileRepository;
  private final LocationResolver locationResolver;
  private final FileStorage fileStorage;

  public ProfileDto getProfile(UUID userId) {
    Profile profile = profileRepository.findById(userId)
        .orElseThrow(() -> new ProfileNotFoundException(userId));
    return ProfileDto.from(profile, resolveImageUrl(profile.getImageKey()));
  }

  @Transactional
  public ProfileDto updateProfile(UUID userId, ProfileUpdateRequest request, MultipartFile image) {
    Profile profile = profileRepository.findById(userId)
        .orElseThrow(() -> new ProfileNotFoundException(userId));

    if (request.name() != null) {
      profile.getUser().changeName(request.name());
    }

    String province = null;
    String city = null;
    String district = null;
    Double latitude = null;
    Double longitude = null;
    Integer x = null;
    Integer y = null;

    if (request.location() != null
        && request.location().latitude() != null
        && request.location().longitude() != null) {
      WeatherAPILocation location = locationResolver.resolve(
          request.location().latitude(), request.location().longitude()
      );
      latitude = location.latitude();
      longitude = location.longitude();
      x = location.x();
      y = location.y();
      province = location.locationNames()[0];
      city = location.locationNames()[1];
      district = location.locationNames()[2];
    }

    profile.update(
        request.gender(),
        request.birthDate(),
        latitude, longitude, x, y,
        province, city, district,
        request.temperatureSensitivity()
    );

    if (image != null) {
      updateProfileImage(profile, userId, image);
    }

    return ProfileDto.from(profile, resolveImageUrl(profile.getImageKey()));
  }

  private void updateProfileImage(Profile profile, UUID userId, MultipartFile image) {
    String oldImageKey = profile.getImageKey();

    StoredFile storedFile = fileStorage.upload(StorageDirectory.PROFILES, userId, image);

    profile.updateImageKey(storedFile.objectKey());

    if (oldImageKey != null && !oldImageKey.isBlank()) {
      fileStorage.delete(oldImageKey);
    }
  }

  private String resolveImageUrl(String imageKey) {
    if (imageKey == null || imageKey.isBlank()) {
      return null;
    }
    return fileStorage.generateReadUrl(imageKey);
  }
}