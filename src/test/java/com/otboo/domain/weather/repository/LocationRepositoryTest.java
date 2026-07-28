package com.otboo.domain.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.weather.entity.Location;
import com.otboo.global.config.JpaAuditingConfig;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class LocationRepositoryTest {

  @Autowired
  private LocationRepository locationRepository;

  @Test
  @DisplayName("저장된 행정구역으로 조회하면 Location을 반환한다")
  void findsLocationByAdministrativeRegion() {
    // given
    Location location = Location.builder()
        .x(60)
        .y(127)
        .province("서울특별시")
        .city("강서구")
        .district("마곡동")
        .build();
    locationRepository.save(location);

    // when
    Optional<Location> found =
        locationRepository.findByProvinceAndCityAndDistrict("서울특별시", "강서구", "마곡동");

    // then
    assertThat(found).isPresent();
    assertThat(found.get().getX()).isEqualTo(60);
    assertThat(found.get().getY()).isEqualTo(127);
  }

  @Test
  @DisplayName("저장되지 않은 행정구역으로 조회하면 빈 Optional을 반환한다")
  void returnsEmptyWhenAdministrativeRegionNotFound() {
    // when
    Optional<Location> found =
        locationRepository.findByProvinceAndCityAndDistrict("서울특별시", "강서구", "마곡동");

    // then
    assertThat(found).isEmpty();
  }
}