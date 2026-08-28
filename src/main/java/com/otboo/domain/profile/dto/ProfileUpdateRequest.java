package com.otboo.domain.profile.dto;

import com.otboo.domain.profile.entity.Gender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;

public record ProfileUpdateRequest(
    String name,
    Gender gender,
    LocalDate birthDate,
    @Valid
    LocationUpdateRequest location,
    @Min(value = 1, message = "온도 민감도는 1 이상이어야 합니다.")
    @Max(value = 5, message = "온도 민감도는 5 이하여야 합니다.")
    Integer temperatureSensitivity
) {

  public record LocationUpdateRequest(
      Double latitude,
      Double longitude
  ) {
    @AssertTrue(message = "latitude와 longitude는 함께 제공되어야 합니다.")
    public boolean isLatLngConsistent() {
      return (latitude == null) == (longitude == null);
    }
  }
}