package com.otboo.domain.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.entity.ClothesType;
import com.otboo.domain.clothes.repository.ClothesRepository;
import com.otboo.domain.user.entity.User;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecommendationCandidateServiceTest {

    @Mock
    private ClothesRepository clothesRepository;

    @InjectMocks
    private RecommendationCandidateService service;

    @Test
    void 보유_의상을_타입별로_그룹핑한다() {
        //given
        UUID ownerId = UUID.randomUUID();
        User owner = User.create("test@otboo.io", "테스트", "encoded-password");
        ReflectionTestUtils.setField(owner, "id", ownerId);
        Clothes top = new Clothes(owner, "티셔츠", null, ClothesType.TOP);
        Clothes bottom = new Clothes(owner, "바지", null, ClothesType.BOTTOM);

        given(clothesRepository.findByOwner_IdAndDeletedAtIsNull(ownerId))
                .willReturn(List.of(top, bottom));

        //when
        Map<ClothesType, List<Clothes>> result = service.getCandidatesByType(ownerId);

        //then
        assertThat(result.get(ClothesType.TOP)).containsExactly(top);
        assertThat(result.get(ClothesType.BOTTOM)).containsExactly(bottom);
    }

    @Test
    void 보유_의상이_없으면_빈_맵을_반환한다() {
        //given
        UUID ownerId = UUID.randomUUID();

        given(clothesRepository.findByOwner_IdAndDeletedAtIsNull(ownerId))
                .willReturn(List.of());

        //when
        Map<ClothesType, List<Clothes>> result = service.getCandidatesByType(ownerId);

        //then
        assertThat(result).isEmpty();
    }

    @Test
    void id_목록으로_삭제되지_않은_의상을_조회한다() {
        //given
        User owner = User.create("test@otboo.io", "테스트", "encoded-password");
        Clothes top = new Clothes(owner, "티셔츠", null, ClothesType.TOP);
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());

        given(clothesRepository.findByIdInAndDeletedAtIsNull(ids)).willReturn(List.of(top));

        //when
        List<Clothes> result = service.getByIds(ids);

        //then
        assertThat(result).containsExactly(top);
    }
}