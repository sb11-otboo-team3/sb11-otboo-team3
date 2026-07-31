package com.otboo.domain.weather.entity;

public enum WindStrength {
  WEAK,
  MODERATE,
  STRONG;

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