package com.otboo.domain.clothes.service;

import com.otboo.domain.clothes.dto.request.ClothesAttributeDefinitionRequest;
import com.otboo.domain.clothes.dto.response.ClothesAttributeDefinitionResponse;
import com.otboo.domain.clothes.entity.AttributeSelectableValue;
import com.otboo.domain.clothes.entity.ClothesAttributeDefinition;
import com.otboo.domain.clothes.exception.ClothesAttributeDefinitionNotFoundException;
import com.otboo.domain.clothes.exception.DuplicateAttributeDefinitionNameException;
import com.otboo.domain.clothes.mapper.ClothesAttributeDefinitionMapper;
import com.otboo.domain.clothes.repository.AttributeSelectableValueRepository;
import com.otboo.domain.clothes.repository.ClothesAttributeDefinitionRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class ClothesAttributeDefinitionServiceTest {

    @Mock
    private ClothesAttributeDefinitionRepository definitionRepository;

    @Mock
    private AttributeSelectableValueRepository selectableValueRepository;

    @Mock
    private ClothesAttributeDefinitionMapper mapper;

    @InjectMocks
    private ClothesAttributeDefinitionService service;

    @Test
    void 새_이름으로_등록하면_새_속성_정의가_생성된다() {
        //given
        ClothesAttributeDefinitionRequest request =
                new ClothesAttributeDefinitionRequest("색상", List.of("빨강", "파랑"));

        given(definitionRepository.findByName("색상")).willReturn(Optional.empty());
        given(definitionRepository.saveAndFlush(any(ClothesAttributeDefinition.class)))
                        .willAnswer(invocation -> invocation.getArgument(0));
        given(selectableValueRepository.findByDefinitionAndValue(any(), any()))
                .willReturn(Optional.empty());
        given(selectableValueRepository.save(any(AttributeSelectableValue.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(mapper.toResponse(any(), any()))
                .willReturn(new ClothesAttributeDefinitionResponse(
                        UUID.randomUUID(), "색상", List.of("빨강", "파랑"), null
                ));

        //when
        ClothesAttributeDefinitionResponse response = service.create(request);

        //then
        assertThat(response.name()).isEqualTo("색상");
    }

    @Test
    void 활성_상태인_이름으로_등록하면_예외가_발생한다() {
        //given
        ClothesAttributeDefinitionRequest request =
                new ClothesAttributeDefinitionRequest("색상", List.of());
        ClothesAttributeDefinition existing = new ClothesAttributeDefinition("색상");
        given(definitionRepository.findByName("색상")).willReturn(Optional.of(existing));

        //when & then
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateAttributeDefinitionNameException.class);
    }

    @Test
    void 논리_삭제된_이름으로_재등록하면_복구된다() {
        //given
        ClothesAttributeDefinitionRequest request =
                new ClothesAttributeDefinitionRequest("색상", List.of());
        ClothesAttributeDefinition existing = new ClothesAttributeDefinition("색상");
        existing.delete();
        given(definitionRepository.findByName("색상")).willReturn(Optional.of(existing));
        given(mapper.toResponse(any(), any()))
                .willReturn(new ClothesAttributeDefinitionResponse(
                        UUID.randomUUID(), "색상", List.of(), null
                ));

        //when
        service.create(request);

        //then
        assertThat(existing.getDeletedAt()).isNull();
    }

    @Test
    void 존재하지_않는_정의를_수정하면_예외가_발생한다() {
        //given
        UUID definitionId = UUID.randomUUID();
        ClothesAttributeDefinitionRequest request =
                new ClothesAttributeDefinitionRequest("색상", List.of());
        given(definitionRepository.findById(definitionId)).willReturn(Optional.empty());

        //when & then
        assertThatThrownBy(() -> service.update(definitionId, request))
                .isInstanceOf(ClothesAttributeDefinitionNotFoundException.class);
    }

    @Test
    void 존재하지_않는_정의를_삭제하면_예외가_발생한다() {
        //given
        UUID definitionId = UUID.randomUUID();
        given(definitionRepository.findById(definitionId)).willReturn(Optional.empty());

        //when & then
        assertThatThrownBy(() -> service.delete(definitionId))
                .isInstanceOf(ClothesAttributeDefinitionNotFoundException.class);
    }

    @Test
    void 삭제하면_하위_선택값도_함께_논리_삭제된다() {
        //given
        UUID definitionId = UUID.randomUUID();
        ClothesAttributeDefinition definition = new ClothesAttributeDefinition("색상");
        AttributeSelectableValue value = new AttributeSelectableValue(definition, "빨강", 0);

        given(definitionRepository.findById(definitionId)).willReturn(Optional.of(definition));
        given(selectableValueRepository.findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(List.of(definition)))
                .willReturn(List.of(value));

        //when
        service.delete(definitionId);

        //then
        assertThat(definition.getDeletedAt()).isNotNull();
        assertThat(value.getDeletedAt()).isNotNull();
    }
}