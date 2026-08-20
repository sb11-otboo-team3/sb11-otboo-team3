package com.otboo.domain.clothes.extraction.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.otboo.domain.clothes.dto.response.ClothesResponse;
import com.otboo.domain.clothes.entity.AttributeSelectableValue;
import com.otboo.domain.clothes.entity.ClothesAttributeDefinition;
import com.otboo.domain.clothes.entity.ClothesType;
import com.otboo.domain.clothes.extraction.dto.RawProductAttribute;
import com.otboo.domain.clothes.extraction.dto.RawProductInfo;
import com.otboo.domain.clothes.repository.AttributeSelectableValueRepository;
import com.otboo.domain.clothes.repository.ClothesAttributeDefinitionRepository;

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
class ClothesExtractionMapperTest {

    @Mock
    private ClothesAttributeDefinitionRepository definitionRepository;

    @Mock
    private AttributeSelectableValueRepository selectableValueRepository;

    @InjectMocks
    private ClothesExtractionMapper mapper;

    @Test
    void 카테고리_힌트에_상의_키워드가_있으면_TOP으로_매핑한다() {
        //given
        UUID ownerId = UUID.randomUUID();
        RawProductInfo raw = new RawProductInfo(
                "https://www.musinsa.com/products/1", "반팔티", "https://img/1.jpg",
                List.of("스포츠/레저", "상의", "반소매 티셔츠"), List.of()
        );

        //when
        ClothesResponse response = mapper.toClothesResponse(raw, ownerId);

        //then
        assertThat(response.type()).isEqualTo(ClothesType.TOP);
        assertThat(response.id()).isNull();
        assertThat(response.ownerId()).isEqualTo(ownerId);
        assertThat(response.name()).isEqualTo("반팔티");
        assertThat(response.imageUrl()).isEqualTo("https://img/1.jpg");
    }

    @Test
    void 매칭되는_카테고리_키워드가_없으면_ETC로_매핑한다() {
        //given
        RawProductInfo raw = new RawProductInfo(
                "https://zigzag.kr/catalog/products/1", "상품", null,
                List.of("알수없는카테고리", "unknown_category"), List.of()
        );

        //when
        ClothesResponse response = mapper.toClothesResponse(raw, UUID.randomUUID());

        //then
        assertThat(response.type()).isEqualTo(ClothesType.ETC);
    }

    @Test
    void 지그재그_영문_카테고리_키_top_으로도_TOP이_매핑된다() {
        //given
        RawProductInfo raw = new RawProductInfo(
                "https://zigzag.kr/catalog/products/1", "셔츠", null,
                List.of("셔츠/남방/블라우스", "top_shirt-flannel"), List.of()
        );

        //when
        ClothesResponse response = mapper.toClothesResponse(raw, UUID.randomUUID());

        //then
        assertThat(response.type()).isEqualTo(ClothesType.TOP);
    }

    @Test
    void 정의와_값이_모두_활성_상태로_일치하면_속성이_포함된다() {
        //given
        ClothesAttributeDefinition definition = new ClothesAttributeDefinition("핏");
        ReflectionTestUtils.setField(definition, "id", UUID.randomUUID());

        AttributeSelectableValue slim = new AttributeSelectableValue(definition, "슬림", 0);
        AttributeSelectableValue loose = new AttributeSelectableValue(definition, "루즈", 1);

        given(definitionRepository.findByName("핏")).willReturn(Optional.of(definition));
        given(selectableValueRepository.findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(List.of(definition)))
                .willReturn(List.of(slim, loose));

        RawProductInfo raw = new RawProductInfo(
                "https://www.musinsa.com/products/1", "반팔티", null,
                List.of(), List.of(new RawProductAttribute("핏", List.of("슬림")))
        );

        //when
        ClothesResponse response = mapper.toClothesResponse(raw, UUID.randomUUID());

        //then
        assertThat(response.attributes()).hasSize(1);
        assertThat(response.attributes().get(0).definitionId()).isEqualTo(definition.getId());
        assertThat(response.attributes().get(0).definitionName()).isEqualTo("핏");
        assertThat(response.attributes().get(0).value()).isEqualTo("슬림");
        assertThat(response.attributes().get(0).selectableValues()).containsExactly("슬림", "루즈");
    }

    @Test
    void 우리_DB에_없는_속성_정의는_무시된다() {
        //given
        given(definitionRepository.findByName("소재")).willReturn(Optional.empty());

        RawProductInfo raw = new RawProductInfo(
                "https://www.musinsa.com/products/1", "반팔티", null,
                List.of(), List.of(new RawProductAttribute("소재", List.of("면 100%")))
        );

        //when
        ClothesResponse response = mapper.toClothesResponse(raw, UUID.randomUUID());

        //then
        assertThat(response.attributes()).isEmpty();
    }

    @Test
    void 선택값이_활성_목록에_없으면_무시된다() {
        //given
        ClothesAttributeDefinition definition = new ClothesAttributeDefinition("핏");
        ReflectionTestUtils.setField(definition, "id", UUID.randomUUID());
        AttributeSelectableValue slim = new AttributeSelectableValue(definition, "슬림", 0);

        given(definitionRepository.findByName("핏")).willReturn(Optional.of(definition));
        given(selectableValueRepository.findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(List.of(definition)))
                .willReturn(List.of(slim));

        RawProductInfo raw = new RawProductInfo(
                "https://www.musinsa.com/products/1", "반팔티", null,
                List.of(), List.of(new RawProductAttribute("핏", List.of("존재하지않는값")))
        );

        //when
        ClothesResponse response = mapper.toClothesResponse(raw, UUID.randomUUID());

        //then
        assertThat(response.attributes()).isEmpty();
    }
}
