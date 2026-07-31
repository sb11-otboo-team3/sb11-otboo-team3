package com.otboo.domain.weather.client;

import com.otboo.domain.weather.dto.VilageFcstItem;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.WindStrength;
import com.otboo.domain.weather.util.VilageFcstBaseTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.client.RestClient;

public class KmaWeatherClient {

  private final RestClient restClient;
  private final String apiKey;

  public KmaWeatherClient(RestClient restClient, String apiKey) {
    this.restClient = restClient;
    this.apiKey = apiKey;
  }

  public List<VilageFcstItem> getForecast(int nx, int ny, VilageFcstBaseTime baseTime) {
    String baseDate = baseTime.baseDate().format(DateTimeFormatter.BASIC_ISO_DATE);
    String baseTimeValue = baseTime.baseTime().format(DateTimeFormatter.ofPattern("HHmm"));

    KmaApiResponse response = restClient.get()
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
        .body(KmaApiResponse.class);

    List<Item> items = response.response().body().items().item();

    Map<String, List<Item>> groupedByForecastSlot = items.stream()
        .collect(Collectors.groupingBy(item -> item.fcstDate() + item.fcstTime()));

    return groupedByForecastSlot.values().stream()
        .map(this::toVilageFcstItem)
        .toList();
  }

  private VilageFcstItem toVilageFcstItem(List<Item> group) {
    Map<String, String> valuesByCategory = group.stream()
        .collect(Collectors.toMap(Item::category, Item::fcstValue));

    Item first = group.get(0);
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
        mapWindStrength(windSpeed)
    );
  }

  private LocalDateTime parseDateTime(String date, String time) {
    return LocalDateTime.of(
        LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE),
        LocalTime.parse(time, DateTimeFormatter.ofPattern("HHmm"))
    );
  }

  private Double parsePrecipitationAmount(String value) {
    if (value == null || value.equals("-") || value.equals("강수없음")) {
      return 0.0;
    }
    return parseDoubleOrNull(value);
  }

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

  private WindStrength mapWindStrength(Double windSpeed) {
    if (windSpeed == null) {
      return null;
    }
    if (windSpeed < 4.0) {
      return WindStrength.WEAK;
    }
    if (windSpeed < 9.0) {
      return WindStrength.MODERATE;
    }
    return WindStrength.STRONG;
  }

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