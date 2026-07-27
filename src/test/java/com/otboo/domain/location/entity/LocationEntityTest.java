package com.otboo.domain.location.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.domain.location.dto.WeatherAPILocation;
import com.otboo.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import java.time.Instant;
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
  void 같은_x_y_좌표는_중복_저장할_수_없다() {
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

    Location duplicate = Location.builder()
        .x(60)
        .y(127)
        .province("서울특별시")
        .city("강서구")
        .district("다른동")
        .build();

    // when & then
    assertThatThrownBy(() -> {
      entityManager.persist(duplicate);
      entityManager.flush();
    }).isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  void builder로_생성하면_최근_요청_시각도_함께_기록된다() {
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
  void refreshRequestedAt_호출시_최근_요청_시각이_갱신된다() throws InterruptedException {
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
  void builder로_생성하면_필드가_그대로_채워진다() {
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
  void toDto는_좌표와_행정구역을_WeatherAPILocation으로_변환한다() {
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
