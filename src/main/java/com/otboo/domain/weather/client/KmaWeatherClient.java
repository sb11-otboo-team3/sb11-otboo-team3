package com.otboo.domain.weather.client;

import com.otboo.domain.weather.dto.VilageFcstItem;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.WindStrength;
import com.otboo.domain.weather.exception.KmaApiException;
import com.otboo.domain.weather.util.VilageFcstBaseTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
public class KmaWeatherClient {

  private final WebClient webClient;
  private final String apiKey;

  public KmaWeatherClient(WebClient webClient, String apiKey) {
    this.webClient = webClient;
    this.apiKey = apiKey;
  }
//격자 단위, 현재 가지고 예보 발표 시간을 통해 기상청 api로부터 날씨 데이터를 가져온다.
  public Mono<List<VilageFcstItem>> getForecast(int nx, int ny, VilageFcstBaseTime baseTime) {
    //발표 시간 맞추기
    String baseDate = baseTime.baseDate().format(DateTimeFormatter.BASIC_ISO_DATE);
    String baseTimeValue = baseTime.baseTime().format(DateTimeFormatter.ofPattern("HHmm"));

    log.info("기상청 예보 조회 요청 시작: nx={}, ny={}, baseDate={}, baseTime={}", nx, ny, baseDate, baseTimeValue);

    return webClient.get() // 요청 보내기 (여기선 아직 아무것도 안 감. 티켓만 만들어짐)
        .uri(uriBuilder -> uriBuilder
            .path("/getVilageFcst")
            .queryParam("authKey", apiKey)
            .queryParam("numOfRows", 1000)
            .queryParam("pageNo", 1)
            .queryParam("dataType", "JSON")
            .queryParam("base_date", baseDate)
            .queryParam("base_time", baseTimeValue)
            .queryParam("nx", nx)
            .queryParam("ny", ny)
            .build())
        .retrieve()
        .bodyToMono(KmaApiResponse.class)
        // WebClientException(통신 실패)뿐 아니라 JSON 파싱 실패(DecodingException, WebClientException과 무관한 별도 계층)도
        // 여기서 잡아야 한다 - 안 그러면 파싱 에러가 그대로 흘러가서 GlobalExceptionHandler의 500 catch-all로 떨어진다.
        .onErrorMap(Exception.class, e -> {
          log.error("기상청 예보 조회 실패: nx={}, ny={}, baseDate={}, baseTime={}", nx, ny, baseDate, baseTimeValue, e);
          return new KmaApiException(nx, ny, baseTime, e);
        })
        // 응답이 아예 안 온(빈 바디) 경우도 있어서 null 체크 대신 switchIfEmpty로 처리
        .switchIfEmpty(Mono.defer(() -> {
          log.error("기상청 예보 응답 본문이 비어있음: nx={}, ny={}, baseDate={}, baseTime={}", nx, ny, baseDate, baseTimeValue);
          return Mono.error(new KmaApiException(nx, ny, baseTime, null));
        }))
        .flatMap(response -> {
          if (response.response() == null || response.response().body() == null
              || response.response().body().items() == null || response.response().body().items().item() == null) {
            log.error("기상청 예보 응답 본문이 비어있음: nx={}, ny={}, baseDate={}, baseTime={}", nx, ny, baseDate, baseTimeValue);
            return Mono.<List<VilageFcstItem>>error(new KmaApiException(nx, ny, baseTime, null));
          }

          List<Item> items = response.response().body().items().item();
          //응답 중첩된걸 벗겨내고 핵심만 가져오기

          Map<String, List<Item>> groupedByForecastSlot = items.stream() //예보 대상 시간별로 데이터들 모으기.
              .collect(Collectors.groupingBy(item -> item.fcstDate() + item.fcstTime()));

          List<VilageFcstItem> result = groupedByForecastSlot.values().stream() //VilageFcstItem Dto 리스트로 묶어 가져오기.
              .map(this::toVilageFcstItem)
              // 예보 시각 오름차순으로 정렬 - WeatherServiceImpl이 전날 대비 계산 시 같은 배치 내에서도
              // 하루 앞선 항목이 먼저 저장돼 있도록 순서를 보장하기 위함(HashMap 그룹핑은 순서 미보장).
              .sorted(Comparator.comparing(VilageFcstItem::forecastAt))
              .toList();

          log.info("기상청 예보 조회 완료: nx={}, ny={}, baseDate={}, baseTime={}, count={}",
              nx, ny, baseDate, baseTimeValue, result.size());

          return Mono.just(result);
        });
  }

  private VilageFcstItem toVilageFcstItem(List<Item> group) {
    Map<String, String> valuesByCategory = group.stream() //묶음을 카테고리:값으로 변환.
        .collect(Collectors.toMap(Item::category, Item::fcstValue));

    Item first = group.get(0); //묶음의 첫줄에서 값 가져오기.
    Double windSpeed = parseDoubleOrNull(valuesByCategory.get("WSD"));

    return new VilageFcstItem(
        parseDateTime(first.baseDate(), first.baseTime()),
        parseDateTime(first.fcstDate(), first.fcstTime()),
        mapSkyStatus(valuesByCategory.get("SKY")),
        mapPrecipitationType(valuesByCategory.get("PTY")),
        parsePrecipitationAmount(valuesByCategory.get("PCP")),
        parseDoubleOrNull(valuesByCategory.get("POP")),
        parseDoubleOrNull(valuesByCategory.get("REH")),
        parseDoubleOrNull(valuesByCategory.get("TMP")),
        parseDoubleOrNull(valuesByCategory.get("TMN")),
        parseDoubleOrNull(valuesByCategory.get("TMX")),
        windSpeed,
        WindStrength.fromSpeed(windSpeed)
    );
  }

  private LocalDateTime parseDateTime(String date, String time) {
    return LocalDateTime.of(
        LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE),
        LocalTime.parse(time, DateTimeFormatter.ofPattern("HHmm"))
    );
  }

  //강수량이 null또는 문자열 강수없음 등 으로 올때 0으로 변환
  private Double parsePrecipitationAmount(String value) {
    if (value == null || value.equals("-") || value.equals("강수없음")) {
      return 0.0;
    }
    return parseDoubleOrNull(value);
  }

  //double 또는 null값만 유효
  private Double parseDoubleOrNull(String value) {
    if (value == null) {
      return null;
    }
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  // 기상청 api에서는 숫자로 하늘 상태를 알려줍니다. 그걸 변환
  private SkyStatus mapSkyStatus(String code) {
    if (code == null) {
      return null;
    }
    return switch (code) {
      case "1" -> SkyStatus.CLEAR;
      case "3" -> SkyStatus.MOSTLY_CLOUDY;
      case "4" -> SkyStatus.CLOUDY;
      default -> null;
    };
  }

  //마찬가지로 비 상태 또한 숫자로 내려주는걸 변환
  private PrecipitationType mapPrecipitationType(String code) {
    if (code == null) {
      return null;
    }
    return switch (code) {
      case "0" -> PrecipitationType.NONE;
      case "1" -> PrecipitationType.RAIN;
      case "2" -> PrecipitationType.RAIN_SNOW;
      case "3" -> PrecipitationType.SNOW;
      case "4" -> PrecipitationType.SHOWER;
      default -> null;
    };
  }

  // 기상청 응답 구조
  private record KmaApiResponse(Response response) {
  }

  private record Response(Body body) {
  }

  private record Body(Items items) {
  }

  private record Items(List<Item> item) {
  }

  private record Item(
      String baseDate,
      String baseTime,
      String category,
      String fcstDate,
      String fcstTime,
      String fcstValue,
      int nx,
      int ny
  ) {
  }
}