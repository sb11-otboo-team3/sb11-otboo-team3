package com.otboo.domain.weather.entity;

public enum WindStrength {
  WEAK, //0 ~ 4.0m/s
  MODERATE, //4.0 ~ 9.0m/s
  STRONG; //9.0 ~

  //기준은 한국 기상청 공식 예보 용어 4단계에서 강한 바람 + 매우 강한 바람을 합쳐 3단계로 줄인것

  public static WindStrength fromSpeed(Double windSpeed) {
    if (windSpeed == null) {
      return null;
    }
    if (windSpeed < 4.0) {
      return WEAK;
    }
    if (windSpeed < 9.0) {
      return MODERATE;
    }
    return STRONG;
  }
}