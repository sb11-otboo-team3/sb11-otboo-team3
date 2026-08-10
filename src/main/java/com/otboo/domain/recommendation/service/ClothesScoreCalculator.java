package com.otboo.domain.recommendation.service;

import com.otboo.domain.clothes.entity.ClothesType;
import com.otboo.domain.weather.entity.PrecipitationType;
import org.springframework.stereotype.Component;

@Component
public class ClothesScoreCalculator {

    private static final double NEUTRAL_SCORE = 1.0;
    private static final double PRECIPITATION_BONUS = 5.0;

    public double calculateScore(
            ClothesType type,
            double minTemperature,
            double maxTemperature,
            PrecipitationType precipitationType,
            int temperatureSensitivity
    ) {
        if (type != ClothesType.OUTER) {
            return NEUTRAL_SCORE;
        }

        double representativeTemperature = (minTemperature + maxTemperature) / 2;
        double adjustedTemperature = representativeTemperature - (temperatureSensitivity - 3) * 2.0;

        double score = outerScoreByTemperature(adjustedTemperature);
        if (precipitationType != PrecipitationType.NONE) {
            score += PRECIPITATION_BONUS;
        }

        return score;
    }

    private double outerScoreByTemperature(double temperature) {
        if (temperature >= 20) {
            return 0;
        }
        if (temperature >= 15) {
            return 3;
        }
        if (temperature >= 10) {
            return 6;
        }
        if (temperature >= 4) {
            return 9;
        }
        return 12;
    }
}
