package com.otboo.domain.weather.util;

import com.otboo.domain.weather.exception.InvalidWeatherGridException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


//기상청(KMA)이 동네예보 API용으로 공개한 LCC DFS(Lambert Conformal Conic, 표준위도 2개 고정) 투영 변환 공식
@Slf4j
@Component
public class GridConverter {

  private static final double RE = 6371.00877; // 지구 반경(km)
  private static final double GRID = 5.0; // 격자 간격(km)
  private static final double SLAT1 = 30.0; // 투영 위도1(degree)
  private static final double SLAT2 = 60.0; // 투영 위도2(degree)
  private static final double OLON = 126.0; // 기준점 경도(degree)
  private static final double OLAT = 38.0; // 기준점 위도(degree)
  private static final double XO = 43; // 기준점 X좌표(GRID)
  private static final double YO = 136; // 기준점 Y좌표(GRID)
  private static final double DEGRAD = Math.PI / 180.0;

  private static final int MIN_GRID_X = 1;
  private static final int MAX_GRID_X = 149;
  private static final int MIN_GRID_Y = 1;
  private static final int MAX_GRID_Y = 253;

  public WeatherGrid convert(double latitude, double longitude) {
    validateLatLngRange(latitude, longitude);

    double re = RE / GRID;
    double slat1 = SLAT1 * DEGRAD;
    double slat2 = SLAT2 * DEGRAD;
    double olon = OLON * DEGRAD;
    double olat = OLAT * DEGRAD;

    double sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
    sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
    double sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);
    sf = Math.pow(sf, sn) * Math.cos(slat1) / sn;
    double ro = Math.tan(Math.PI * 0.25 + olat * 0.5);
    ro = re * sf / Math.pow(ro, sn);

    double ra = Math.tan(Math.PI * 0.25 + latitude * DEGRAD * 0.5);
    ra = re * sf / Math.pow(ra, sn);
    double theta = longitude * DEGRAD - olon;
    if (theta > Math.PI) {
      theta -= 2.0 * Math.PI;
    }
    if (theta < -Math.PI) {
      theta += 2.0 * Math.PI;
    }
    theta *= sn;

    int x = (int) Math.floor(ra * Math.sin(theta) + XO + 0.5);
    int y = (int) Math.floor(ro - ra * Math.cos(theta) + YO + 0.5);

    validateGridRange(latitude, longitude, x, y);

    return new WeatherGrid(x, y);
  }

  // 유효 위경도 체크
  private void validateLatLngRange(double latitude, double longitude) {
    if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
      log.warn("위경도 범위를 벗어남: latitude={}, longitude={}", latitude, longitude);
      throw new InvalidWeatherGridException(latitude, longitude);
    }
  }

  // 대한민국 내 유효 위경도 체크
  private void validateGridRange(double latitude, double longitude, int x, int y) {
    if (x < MIN_GRID_X || x > MAX_GRID_X || y < MIN_GRID_Y || y > MAX_GRID_Y) {
      log.warn("대한민국 격자 범위를 벗어남: latitude={}, longitude={}, x={}, y={}",
          latitude, longitude, x, y);
      throw new InvalidWeatherGridException(latitude, longitude);
    }
  }
}