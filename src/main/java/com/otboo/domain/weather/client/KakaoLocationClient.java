package com.otboo.domain.weather.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.otboo.domain.weather.dto.KakaoRegion;
import com.otboo.domain.weather.exception.KakaoApiException;
import com.otboo.domain.weather.exception.KakaoRegionNotFoundException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
public class KakaoLocationClient {

  // 행정동만 파싱. 법정동은 B
  private static final String ADMINISTRATIVE_REGION_TYPE = "H";

  private final WebClient webClient;
  private final String apiKey;

  public KakaoLocationClient(WebClient webClient, String apiKey) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("카카오 API 키가 설정되지 않았습니다.");
    }
    this.webClient = webClient;
    this.apiKey = apiKey;
  }

  public Mono<KakaoRegion> getRegion(double latitude, double longitude) {
    log.info("Kakao 좌표->행정구역 조회 요청 시작: latitude={}, longitude={}", latitude, longitude);

    return webClient.get()
        .uri(uriBuilder -> uriBuilder
            .path("/v2/local/geo/coord2regioncode.json")
            .queryParam("x", longitude)
            .queryParam("y", latitude)
            .build())
        .header("Authorization", "KakaoAK " + apiKey)
        .retrieve()
        .bodyToMono(KakaoRegionResponse.class)
        // WebClientException(통신 실패)뿐 아니라 JSON 파싱 실패(DecodingException, WebClientException과 무관한 별도 계층)도
        // 여기서 잡아야 한다 - 안 그러면 파싱 에러가 그대로 흘러가서 GlobalExceptionHandler의 500 catch-all로 떨어진다.
        .onErrorMap(Exception.class, e -> {
          log.error("Kakao 좌표->행정구역 조회 실패: latitude={}, longitude={}", latitude, longitude, e);
          return new KakaoApiException(latitude, longitude, e);
        })
        // 응답이 아예 빈 바디로 오는 경우도 있어서 null 체크 대신 switchIfEmpty로 처리
        .switchIfEmpty(Mono.defer(() -> {
          log.error("Kakao 응답 본문이 비어있음: latitude={}, longitude={}", latitude, longitude);
          return Mono.error(new KakaoRegionNotFoundException(latitude, longitude));
        }))
        .flatMap(response -> {
          if (response.documents() == null) {
            log.error("Kakao 응답 본문이 비어있음: latitude={}, longitude={}", latitude, longitude);
            return Mono.<KakaoRegion>error(new KakaoRegionNotFoundException(latitude, longitude));
          }

          return response.documents().stream()
              .filter(document -> ADMINISTRATIVE_REGION_TYPE.equals(document.regionType()))
              .findFirst()
              .map(document -> new KakaoRegion(
                  document.region1depthName(),
                  document.region2depthName(),
                  document.region3depthName()
              ))
              .map(Mono::just)
              .orElseGet(() -> {
                log.error("Kakao 응답에 행정동(H) 정보가 없음: latitude={}, longitude={}", latitude, longitude);
                return Mono.error(new KakaoRegionNotFoundException(latitude, longitude));
              });
        })
        .doOnNext(region -> log.info(
            "Kakao 좌표->행정구역 조회 완료: latitude={}, longitude={}, province={}, city={}, district={}",
            latitude, longitude, region.province(), region.city(), region.district()));
  }

  private record KakaoRegionResponse(List<KakaoDocument> documents) {
  }

  private record KakaoDocument(
      @JsonProperty("region_type") String regionType,
      @JsonProperty("region_1depth_name") String region1depthName,
      @JsonProperty("region_2depth_name") String region2depthName,
      @JsonProperty("region_3depth_name") String region3depthName
  ) {
  }
}