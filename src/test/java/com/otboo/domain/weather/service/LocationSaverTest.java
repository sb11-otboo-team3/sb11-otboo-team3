package com.otboo.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.otboo.domain.weather.entity.Location;
import com.otboo.domain.weather.repository.LocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class LocationSaverTest {

  @Mock
  private LocationRepository locationRepository;

  private LocationSaver locationSaver;

  @BeforeEach
  void setUp() {
    locationSaver = new LocationSaver(locationRepository);
  }

  private Location newLocation() {
    return Location.builder()
        .x(60)
        .y(127)
        .province("서울특별시")
        .city("강서구")
        .district("마곡동")
        .build();
  }

  @Test
  @DisplayName("정상 저장이면 saveAndFlush로 즉시 반영한다")
  void savesAndFlushesLocation() {
    // given
    Location location = newLocation();

    // when
    locationSaver.saveInNewTransaction(location);

    // then
    verify(locationRepository).saveAndFlush(location);
  }

  @Test
  @DisplayName("동시 저장으로 유니크 제약 위반이 나면 예외를 그대로 던진다 (호출부에서 잡아야 하므로 여기선 삼키지 않는다)")
  void propagatesDataIntegrityViolationOnConcurrentSave() {
    // given
    Location location = newLocation();
    given(locationRepository.saveAndFlush(any(Location.class)))
        .willThrow(new DataIntegrityViolationException("duplicate key"));

    // when & then
    assertThatThrownBy(() -> locationSaver.saveInNewTransaction(location))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}