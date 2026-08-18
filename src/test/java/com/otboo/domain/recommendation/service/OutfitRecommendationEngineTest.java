package com.otboo.domain.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.entity.ClothesType;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.weather.entity.PrecipitationType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OutfitRecommendationEngineTest {

    @Mock
    private RecommendationCandidateService candidateService;

    @Mock
    private OutfitCombinationRule combinationRule;

    @Mock
    private ClothesScoreCalculator scoreCalculator;

    @InjectMocks
    private OutfitRecommendationEngine engine;

    private final User owner = User.create("test@otboo.io", "테스트", "encoded-password");

    @Test
    void 조합_대상이_아닌_타입은_결과에서_제외된다() {
        //given
        UUID ownerId = UUID.randomUUID();
        Clothes hat = new Clothes(owner, "모자", null, ClothesType.HAT);

        Map<ClothesType, List<Clothes>> candidatesByType =
                Map.of(ClothesType.HAT, List.of(hat));

        given(candidateService.getCandidatesByType(ownerId)).willReturn(
                candidatesByType);
        given(combinationRule.apply(candidatesByType)).willReturn(candidatesByType);

        //when
        List<Clothes> result = engine.recommend(ownerId, 10.0, 15.0,
                PrecipitationType.NONE, 3);

        //then
        assertThat(result).isEmpty();
    }

    @Test
    void 보유_후보가_없으면_빈_조합을_반환한다() {
        //given
        UUID ownerId = UUID.randomUUID();

        given(candidateService.getCandidatesByType(ownerId)).willReturn(
                Map.of());
        given(combinationRule.apply(Map.of())).willReturn(Map.of());

        //when
        List<Clothes> result = engine.recommend(ownerId, 10.0, 15.0,
                PrecipitationType.NONE, 3);

        //then
        assertThat(result).isEmpty();
    }

    @Test
    void 여러_타입에서_각각_하나씩_선택해_조합을_생성한다() {
        //given
        UUID ownerId = UUID.randomUUID();
        Clothes top = new Clothes(owner, "상의", null, ClothesType.TOP);
        ReflectionTestUtils.setField(top, "id", UUID.randomUUID());
        Clothes bottom = new Clothes(owner, "하의", null,
                ClothesType.BOTTOM);
        ReflectionTestUtils.setField(bottom, "id", UUID.randomUUID());
        Clothes shoes = new Clothes(owner, "신발", null,
                ClothesType.SHOES);
        ReflectionTestUtils.setField(shoes, "id", UUID.randomUUID());

        Map<ClothesType, List<Clothes>> candidatesByType = Map.of(
                ClothesType.TOP, List.of(top),
                ClothesType.BOTTOM, List.of(bottom),
                ClothesType.SHOES, List.of(shoes)
        );

        given(candidateService.getCandidatesByType(ownerId)).willReturn(
                candidatesByType);
        given(combinationRule.apply(candidatesByType)).willReturn(candidatesByType);
        given(scoreCalculator.calculateScore(any(), anyDouble(), anyDouble(), any(), anyInt()))
                .willReturn(1.0);

        //when
        List<Clothes> result = engine.recommend(ownerId, 10.0, 15.0,
                PrecipitationType.NONE, 3);

        //then
        assertThat(result).containsExactlyInAnyOrder(top, bottom,
                shoes);
    }

    @Test
    void 점수가_0인_후보는_조합에서_제외된다() {
        //given
        UUID ownerId = UUID.randomUUID();
        Clothes top = new Clothes(owner, "상의", null, ClothesType.TOP);
        ReflectionTestUtils.setField(top, "id", UUID.randomUUID());
        Clothes outer = new Clothes(owner, "패딩", null, ClothesType.OUTER);
        ReflectionTestUtils.setField(outer, "id", UUID.randomUUID());

        Map<ClothesType, List<Clothes>> candidatesByType = Map.of(
                ClothesType.TOP, List.of(top),
                ClothesType.OUTER, List.of(outer)
        );

        given(candidateService.getCandidatesByType(ownerId)).willReturn(candidatesByType);
        given(combinationRule.apply(candidatesByType)).willReturn(candidatesByType);
        given(scoreCalculator.calculateScore(eq(ClothesType.TOP), anyDouble(), anyDouble(), any(), anyInt()))
                .willReturn(1.0);
        given(scoreCalculator.calculateScore(eq(ClothesType.OUTER), anyDouble(), anyDouble(), any(), anyInt()))
                .willReturn(0.0);

        //when
        List<Clothes> result = engine.recommend(ownerId, 25.0, 28.0,
                PrecipitationType.NONE, 3);

        //then
        assertThat(result).containsExactly(top);
    }

    @Test
    void 동점_후보가_2개면_둘_다_선택될_수_있다() {
        //given
        UUID ownerId = UUID.randomUUID();
        Clothes older = new Clothes(owner, "옷1", null, ClothesType.TOP);
        ReflectionTestUtils.setField(older, "id", UUID.randomUUID());
        Clothes newer = new Clothes(owner, "옷2", null, ClothesType.TOP);
        ReflectionTestUtils.setField(newer, "id", UUID.randomUUID());

        Map<ClothesType, List<Clothes>> candidatesByType =
                Map.of(ClothesType.TOP, List.of(older, newer));

        given(candidateService.getCandidatesByType(ownerId)).willReturn(candidatesByType);
        given(combinationRule.apply(candidatesByType)).willReturn(candidatesByType);
        given(scoreCalculator.calculateScore(eq(ClothesType.TOP), anyDouble(), anyDouble(), any(), anyInt()))
                .willReturn(1.0);

        //when
        Set<Clothes> pickedOverManyRuns = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            List<Clothes> result = engine.recommend(ownerId, 10.0, 15.0, PrecipitationType.NONE, 3);
            assertThat(result).hasSize(1);
            pickedOverManyRuns.add(result.get(0));
        }

        //then
        assertThat(pickedOverManyRuns).containsExactlyInAnyOrder(older, newer);
    }

    @Test
    void 동점_후보가_3개보다_많으면_매번_같은_3개만_뽑히지_않는다() {
        //given
        UUID ownerId = UUID.randomUUID();
        List<Clothes> candidates = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Clothes clothes = new Clothes(owner, "옷" + i, null, ClothesType.TOP);
            ReflectionTestUtils.setField(clothes, "id", UUID.randomUUID());
            candidates.add(clothes);
        }

        Map<ClothesType, List<Clothes>> candidatesByType = Map.of(ClothesType.TOP, candidates);

        given(candidateService.getCandidatesByType(ownerId)).willReturn(candidatesByType);
        given(combinationRule.apply(candidatesByType)).willReturn(candidatesByType);
        given(scoreCalculator.calculateScore(eq(ClothesType.TOP), anyDouble(), anyDouble(), any(), anyInt()))
                .willReturn(1.0);

        //when
        Set<Clothes> pickedOverManyRuns = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            List<Clothes> result = engine.recommend(ownerId, 10.0, 15.0, PrecipitationType.NONE, 3);
            pickedOverManyRuns.add(result.get(0));
        }

        //then
        // 매 호출마다 후보 5개 중 상위 3개만 추리는데, 그 "상위 3개" 자체가 매번 무작위로 섞여 정해지므로
        // 100번 반복하면 5개 후보 모두 한 번쯤은 뽑힐 확률이 매우 높다.
        assertThat(pickedOverManyRuns).hasSizeGreaterThan(3);
    }
}