package com.otboo.domain.recommendation.service;

import com.otboo.domain.clothes.entity.ClothesType;
import com.otboo.domain.weather.entity.PrecipitationType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClothesScoreCalculatorTest {

    private final ClothesScoreCalculator calculator = new ClothesScoreCalculator();

    @Test
    void OUTER가_아니면_중립_점수를_반환한다() {
        //when
        double score = calculator.calculateScore(ClothesType.TOP,
                5.0, 10.0, PrecipitationType.NONE, 3);

        //then
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    void 체감기온이_20도_이상이면_OUTER_점수는_0이다() {
        //when
        double score = calculator.calculateScore(ClothesType.OUTER,
                20.0, 22.0, PrecipitationType.NONE, 3);

        //then
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void 체감기온이_15에서_19도면_OUTER_점수는_3이다() {
        //when
        double score = calculator.calculateScore(ClothesType.OUTER,
                15.0, 17.0, PrecipitationType.NONE, 3);

        //then
        assertThat(score).isEqualTo(3.0);
    }

    @Test
    void 체감기온이_10에서_14도면_OUTER_점수는_6이다() {
        //when
        double score = calculator.calculateScore(ClothesType.OUTER,
                10.0, 12.0, PrecipitationType.NONE, 3);

        //then
        assertThat(score).isEqualTo(6.0);
    }

    @Test
    void 체감기온이_4에서_9도면_OUTER_점수는_9이다() {
        //when
        double score = calculator.calculateScore(ClothesType.OUTER, 4.0,
                6.0, PrecipitationType.NONE, 3);

        //then
        assertThat(score).isEqualTo(9.0);
    }

    @Test
    void 체감기온이_4도_미만이면_OUTER_점수는_12이다() {
        //when
        double score = calculator.calculateScore(ClothesType.OUTER,
                -5.0, 0.0, PrecipitationType.NONE, 3);

        //then
        assertThat(score).isEqualTo(12.0);
    }

    @Test
    void 온도민감도가_높으면_체감온도가_낮아져_점수가_올라간다() {
        //given: 대표기온 20도, 민감도 기준값(3)에서는 0점 구간
        //when
        double neutralScore =
                calculator.calculateScore(ClothesType.OUTER, 18.0, 22.0,
                        PrecipitationType.NONE, 3);
        double sensitiveScore =
                calculator.calculateScore(ClothesType.OUTER, 18.0, 22.0,
                        PrecipitationType.NONE, 5);

        //then: 민감도 5 -> 체감기온 16도로 낮아져 3점 구간으로 이동
        assertThat(neutralScore).isEqualTo(0.0);
        assertThat(sensitiveScore).isEqualTo(3.0);
    }

    @Test
    void 강수가_있으면_가산점이_붙는다() {
        //when
        double score = calculator.calculateScore(ClothesType.OUTER,
                20.0, 22.0, PrecipitationType.RAIN, 3);

        //then
        assertThat(score).isEqualTo(5.0);
    }

    @Test
    void 강수가_없으면_가산점이_없다() {
        //when
        double score = calculator.calculateScore(ClothesType.OUTER,
                20.0, 22.0, PrecipitationType.NONE, 3);

        //then
        assertThat(score).isEqualTo(0.0);
    }
}
