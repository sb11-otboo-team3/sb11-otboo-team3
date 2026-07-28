package com.otboo.domain.weather.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.entity.Location;
import com.otboo.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class LocationEntityTest {

  @Autowired
  private EntityManager entityManager;

  @Test
  @DisplayName("같은 행정구역(province, city, district)은 중복 저장할 수 없다")
  void throwsExceptionWhenDuplicateAdministrativeRegionIsSaved() {
    // given
    Location first = Location.builder()
        .x(60)
        .y(127)
        .province("서울특별시")
        .city("강서구")
        .district("마곡동")
        .build();
    entityManager.persist(first);
    entityManager.flush();

    // 격자(x,y)는 다르지만 행정구역은 같음
    Location duplicate = Location.builder()
        .x(61)
        .y(128)
        .province("서울특별시")
        .city("강서구")
        .district("마곡동")
        .build();

    // when & then
    assertThatThrownBy(() -> {
      entityManager.persist(duplicate);
      entityManager.flush();
    }).isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  @DisplayName("builder로 생성하면 최근 요청 시각도 함께 기록된다")
  void recordsLastRequestedAtWhenBuilt() {
    // given & when
    Location location = Location.builder()
        .x(60)
        .y(127)
        .province("서울특별시")
        .city("강서구")
        .district("마곡동")
        .build();

    // then
    assertThat(location.getLastRequestedAt()).isNotNull();
  }

  @Test
  @DisplayName("refreshRequestedAt 호출 시 최근 요청 시각이 갱신된다")
  void updatesLastRequestedAtWhenRefreshed() throws InterruptedException {
    // given
    Location location = Location.builder()
        .x(60)
        .y(127)
        .province("서울특별시")
        .city("강서구")
        .district("마곡동")
        .build();
    Instant before = location.getLastRequestedAt();
    Thread.sleep(5);

    // when
    location.refreshRequestedAt();

    // then
    assertThat(location.getLastRequestedAt()).isAfter(before);
  }

  @Test
  @DisplayName("builder로 생성하면 필드가 그대로 채워진다")
  void fillsFieldsWhenBuilt() {
    // given & when
    Location location = Location.builder()
        .x(60)
        .y(127)
        .province("서울특별시")
        .city("강서구")
        .district("마곡동")
        .build();

    // then
    assertThat(location.getX()).isEqualTo(60);
    assertThat(location.getY()).isEqualTo(127);
    assertThat(location.getProvince()).isEqualTo("서울특별시");
    assertThat(location.getCity()).isEqualTo("강서구");
    assertThat(location.getDistrict()).isEqualTo("마곡동");
  }

  @Test
  @DisplayName("toDto는 좌표와 행정구역을 WeatherAPILocation으로 변환한다")
  void convertsToWeatherAPILocation() {
    // given
    Location location = Location.builder()
        .x(60)
        .y(127)
        .province("서울특별시")
        .city("강서구")
        .district("마곡동")
        .build();

    // when
    WeatherAPILocation dto = location.toDto(37.5665, 126.9780);

    // then
    assertThat(dto.latitude()).isEqualTo(37.5665);
    assertThat(dto.longitude()).isEqualTo(126.9780);
    assertThat(dto.x()).isEqualTo(60);
    assertThat(dto.y()).isEqualTo(127);
    assertThat(dto.locationNames()).containsExactly("서울특별시", "강서구", "마곡동");
  }
}
