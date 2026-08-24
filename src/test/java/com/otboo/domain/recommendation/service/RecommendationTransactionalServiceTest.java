package com.otboo.domain.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.otboo.domain.clothes.dto.response.ClothesResponse;
import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.entity.ClothesType;
import com.otboo.domain.clothes.mapper.ClothesMapper;
import com.otboo.domain.clothes.repository.AttributeSelectableValueRepository;
import com.otboo.domain.clothes.repository.ClothesAttributeRepository;
import com.otboo.domain.recommendation.dto.response.RecommendationClothesResponse;
import com.otboo.domain.recommendation.llm.dto.RankedOutfit;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.weather.entity.PrecipitationType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecommendationTransactionalServiceTest {

    @Mock
    private OutfitRecommendationEngine recommendationEngine;

    @Mock
    private ClothesAttributeRepository clothesAttributeRepository;

    @Mock
    private AttributeSelectableValueRepository selectableValueRepository;

    @Mock
    private ClothesMapper clothesMapper;

    @InjectMocks
    private RecommendationTransactionalService service;

    private final User owner = User.create("test@otboo.io", "테스트", "encoded-password");

    @Test
    void LLM_랭킹_결과가_있으면_해당_조합으로_추천하고_기존_로직은_호출하지_않는다() {
        //given
        UUID ownerId = UUID.randomUUID();
        Clothes top = new Clothes(owner, "상의", null, ClothesType.TOP);
        ReflectionTestUtils.setField(top, "id", UUID.randomUUID());
        RankedOutfit rankedOutfit = new RankedOutfit(List.of(top.getId()));

        given(recommendationEngine.resolveFromRanked(List.of(top.getId()))).willReturn(List.of(top));
        given(clothesAttributeRepository.findByClothesIn(List.of(top))).willReturn(List.of());
        given(selectableValueRepository.findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(List.of()))
                .willReturn(List.of());
        given(clothesMapper.toResponse(eq(top), any(), any())).willReturn(clothesResponse(top));

        //when
        List<RecommendationClothesResponse> result = service.recommend(
                ownerId, 5.0, 10.0, PrecipitationType.NONE, 3, Optional.of(List.of(rankedOutfit)));

        //then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).clothesId()).isEqualTo(top.getId());
        verify(recommendationEngine, never()).recommend(any(), anyDouble(), anyDouble(), any(), anyInt());
    }

    @Test
    void LLM_랭킹_결과가_없으면_기존_랜덤_로직으로_폴백한다() {
        //given
        UUID ownerId = UUID.randomUUID();
        Clothes top = new Clothes(owner, "상의", null, ClothesType.TOP);
        ReflectionTestUtils.setField(top, "id", UUID.randomUUID());

        given(recommendationEngine.recommend(ownerId, 5.0, 10.0, PrecipitationType.NONE, 3))
                .willReturn(List.of(top));
        given(clothesAttributeRepository.findByClothesIn(List.of(top))).willReturn(List.of());
        given(selectableValueRepository.findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(List.of()))
                .willReturn(List.of());
        given(clothesMapper.toResponse(eq(top), any(), any())).willReturn(clothesResponse(top));

        //when
        List<RecommendationClothesResponse> result = service.recommend(
                ownerId, 5.0, 10.0, PrecipitationType.NONE, 3, Optional.empty());

        //then
        assertThat(result).hasSize(1);
        verify(recommendationEngine, never()).resolveFromRanked(any());
    }

    @Test
    void LLM이_고른_id가_이미_삭제되어_조회_결과가_비어있으면_기존_로직으로_폴백한다() {
        //given
        UUID ownerId = UUID.randomUUID();
        Clothes top = new Clothes(owner, "상의", null, ClothesType.TOP);
        ReflectionTestUtils.setField(top, "id", UUID.randomUUID());
        UUID deletedId = UUID.randomUUID();
        RankedOutfit rankedOutfit = new RankedOutfit(List.of(deletedId));

        given(recommendationEngine.resolveFromRanked(List.of(deletedId))).willReturn(List.of());
        given(recommendationEngine.recommend(ownerId, 5.0, 10.0, PrecipitationType.NONE, 3))
                .willReturn(List.of(top));
        given(clothesAttributeRepository.findByClothesIn(List.of(top))).willReturn(List.of());
        given(selectableValueRepository.findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(List.of()))
                .willReturn(List.of());
        given(clothesMapper.toResponse(eq(top), any(), any())).willReturn(clothesResponse(top));

        //when
        List<RecommendationClothesResponse> result = service.recommend(
                ownerId, 5.0, 10.0, PrecipitationType.NONE, 3, Optional.of(List.of(rankedOutfit)));

        //then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).clothesId()).isEqualTo(top.getId());
    }

    private ClothesResponse clothesResponse(Clothes clothes) {
        return new ClothesResponse(clothes.getId(), UUID.randomUUID(), clothes.getName(), null, clothes.getType(), List.of());
    }
}
