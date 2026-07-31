package com.otboo.domain.weather.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VilageFcstBaseTimeResolverTest {

  private final VilageFcstBaseTimeResolver resolver = new VilageFcstBaseTimeResolver();

  @Test
  @DisplayName("발표시각으로부터 10분이 지났으면 그 발표시각을 그대로 사용한다")
  void resolvesSameSlotWhenPastAvailabilityDelay() {
    // given
    LocalDateTime now = LocalDateTime.of(2026, 7, 30, 5, 15);

    // when
    VilageFcstBaseTime result = resolver.resolve(now);

    // then
    assertThat(result.baseDate()).isEqualTo(LocalDate.of(2026, 7, 30));
    assertThat(result.baseTime()).isEqualTo(LocalTime.of(5, 0));
  }

  @Test
  @DisplayName("발표시각으로부터 10분이 지나기 전이면 이전 발표시각을 사용한다")
  void resolvesPreviousSlotWhenBeforeAvailabilityDelay() {
    // given
    LocalDateTime now = LocalDateTime.of(2026, 7, 30, 5, 5);

    // when
    VilageFcstBaseTime result = resolver.resolve(now);

    // then
    assertThat(result.baseDate()).isEqualTo(LocalDate.of(2026, 7, 30));
    assertThat(result.baseTime()).isEqualTo(LocalTime.of(2, 0));
  }

  @Test
  @DisplayName("하루의 첫 발표시각(02시) 이전이면 전날의 마지막 발표시각(23시)을 사용한다")
  void resolvesPreviousDayLastSlotWhenBeforeFirstSlot() {
    // given
    LocalDateTime now = LocalDateTime.of(2026, 7, 30, 1, 0);

    // when
    VilageFcstBaseTime result = resolver.resolve(now);

    // then
    assertThat(result.baseDate()).isEqualTo(LocalDate.of(2026, 7, 29));
    assertThat(result.baseTime()).isEqualTo(LocalTime.of(23, 0));
  }

  @Test
  @DisplayName("이전 발표시각을 구하면 같은 날의 한 슬롯 전을 반환한다")
  void previousReturnsPriorSlotOnSameDay() {
    // given
    VilageFcstBaseTime current = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));

    // when
    VilageFcstBaseTime result = resolver.previous(current);

    // then
    assertThat(result.baseDate()).isEqualTo(LocalDate.of(2026, 7, 30));
    assertThat(result.baseTime()).isEqualTo(LocalTime.of(2, 0));
  }

  @Test
  @DisplayName("이전 발표시각이 하루의 첫 슬롯(02시)이면 전날의 마지막 슬롯(23시)을 반환한다")
  void previousReturnsPreviousDayLastSlotWhenAtFirstSlot() {
    // given
    VilageFcstBaseTime current = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(2, 0));

    // when
    VilageFcstBaseTime result = resolver.previous(current);

    // then
    assertThat(result.baseDate()).isEqualTo(LocalDate.of(2026, 7, 29));
    assertThat(result.baseTime()).isEqualTo(LocalTime.of(23, 0));
  }
}