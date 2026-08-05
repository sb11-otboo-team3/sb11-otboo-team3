package com.otboo.domain.weather.service;

import com.otboo.domain.weather.cache.GridRecencyCache;
import com.otboo.domain.weather.client.KakaoLocationClient;
import com.otboo.domain.weather.dto.KakaoRegion;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.repository.GridRepository;
import com.otboo.domain.weather.util.GridConverter;
import com.otboo.domain.weather.util.WeatherGrid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocationResolver {

  private final GridConverter gridConverter;
  private final GridRepository gridRepository;
  private final KakaoLocationClient kakaoLocationClient;
  private final GridRecencyCache gridRecencyCache;
  private final GridSaver gridSaver;

  public Mono<WeatherAPILocation> resolve(double latitude, double longitude) {
    WeatherGrid grid = gridConverter.convert(latitude, longitude);

    // 카카오 호출과 격자 레지스트리 갱신(DB/캐시)은 서로 결과를 필요로 하지 않는 독립적인 작업이라 동시에 실행한다.
    Mono<KakaoRegion> regionMono = kakaoLocationClient.getRegion(latitude, longitude);

    // 격자 레지스트리 갱신은 JPA(블로킹) 호출이라, 이벤트 루프 스레드가 아니라 별도 스레드풀(boundedElastic)에서 실행한다.
    Mono<Boolean> gridRegistryMono = Mono.fromCallable(() -> {
          updateGridRegistry(grid);
          return true;
        })
        .subscribeOn(Schedulers.boundedElastic());

    return Mono.zip(regionMono, gridRegistryMono)
        .map(tuple -> toDto(latitude, longitude, grid, tuple.getT1()));
  }

  //최근 접근 한적 있는 격자인지 캐시에서 체크.(너무 많은 최근 접근한 지역인지 DB 접근을 줄이기 위해)
  private void updateGridRegistry(WeatherGrid grid) {
    if (gridRecencyCache.isRecentlyConfirmed(grid)) {
      return;
    }

    // 격자 레지스트리 갱신 (날씨 프리페치 배치가 실제로 쓰이는 격자만 골라낼 때 참고할 용도)
    Optional<Grid> existing = gridRepository.findByXAndY(grid.x(), grid.y());
    if (existing.isPresent()) {
      Grid found = existing.get();
      found.refreshRequestedAt();
      gridRepository.save(found);
    } else {
      // REQUIRES_NEW로 분리된 저장 시도가 유니크 제약 위반으로 실패해도, 그 실패는 별도 트랜잭션 안에서
      // 끝나므로 여기서 잡아도 이 메서드의 트랜잭션(바깥)엔 영향 없다.
      try {
        gridSaver.saveInNewTransaction(Grid.builder().x(grid.x()).y(grid.y()).build());
      } catch (DataIntegrityViolationException e) {
        log.warn("격자 등록 - 동시성 충돌 발생, x={}, y={}", grid.x(), grid.y(), e);
      }
    }

    gridRecencyCache.markConfirmed(grid);
  }

  private WeatherAPILocation toDto(
      double latitude, double longitude, WeatherGrid grid, KakaoRegion region
  ) {
    return new WeatherAPILocation(
        latitude,
        longitude,
        grid.x(),
        grid.y(),
        new String[]{region.province(), region.city(), region.district()}
    );
  }
}
