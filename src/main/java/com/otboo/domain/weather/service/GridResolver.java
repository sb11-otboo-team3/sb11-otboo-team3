package com.otboo.domain.weather.service;

import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.exception.GridRegistrationFailedException;
import com.otboo.domain.weather.repository.GridRepository;
import com.otboo.domain.weather.util.WeatherGrid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

// 격자(x,y)를 찾아서 있으면 그대로, 없으면 등록해서 돌려준다. LocationResolver와 WeatherForecastFinder
// 양쪽에서 거의 똑같이 하던 "찾고 없으면 만들고, 동시성 충돌 나면 재조회" 로직을 한 곳으로 모은 것.
@Slf4j
@Component
@RequiredArgsConstructor
public class GridResolver {

  private final GridRepository gridRepository;
  private final GridSaver gridSaver;

  public Grid findOrRegister(WeatherGrid weatherGrid) {
    return gridRepository.findByXAndY(weatherGrid.x(), weatherGrid.y())
        .orElseGet(() -> register(weatherGrid));
  }

  private Grid register(WeatherGrid weatherGrid) {
    log.warn("격자 없음 - 등록 시도, x={}, y={}", weatherGrid.x(), weatherGrid.y());
    try {
      gridSaver.saveInNewTransaction(Grid.builder().x(weatherGrid.x()).y(weatherGrid.y()).build());
    } catch (DataIntegrityViolationException e) {
      // REQUIRES_NEW로 분리된 저장 시도가 유니크 제약 위반으로 실패해도, 그 실패는 별도 트랜잭션 안에서
      // 끝나므로 여기서 잡아도 이 메서드의 트랜잭션(바깥)엔 영향 없다. PostgreSQL은 유니크 제약 충돌을
      // 먼저 커밋된 트랜잭션이 있을 때만 던지므로(그 전엔 블로킹), 이 예외를 받았다는 것 자체가 이미
      // 그 격자가 커밋되어 존재한다는 뜻 - 그래도 방어적으로 재조회해서 확인한다.
      log.warn("격자 등록 - 동시성 충돌 발생, x={}, y={}", weatherGrid.x(), weatherGrid.y(), e);
    }
    return gridRepository.findByXAndY(weatherGrid.x(), weatherGrid.y())
        .orElseThrow(() -> new GridRegistrationFailedException(weatherGrid.x(), weatherGrid.y()));
  }
}
