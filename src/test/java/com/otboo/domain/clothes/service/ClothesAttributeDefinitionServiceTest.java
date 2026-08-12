package com.otboo.domain.clothes.service;

import com.otboo.domain.clothes.dto.request.ClothesAttributeDefinitionRequest;
import com.otboo.domain.clothes.dto.response.ClothesAttributeDefinitionResponse;
import com.otboo.domain.clothes.entity.AttributeSelectableValue;
import com.otboo.domain.clothes.entity.ClothesAttributeDefinition;
import com.otboo.domain.clothes.exception.ClothesAttributeDefinitionNotFoundException;
import com.otboo.domain.clothes.exception.DuplicateAttributeDefinitionNameException;
import com.otboo.domain.clothes.exception.InvalidSortConditionException;
import com.otboo.domain.clothes.mapper.ClothesAttributeDefinitionMapper;
import com.otboo.domain.clothes.repository.AttributeSelectableValueRepository;
import com.otboo.domain.clothes.repository.ClothesAttributeDefinitionRepository;

import com.otboo.domain.notification.entity.NotificationLevel;
import com.otboo.domain.notification.event.NotificationEvent;
import com.otboo.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ClothesAttributeDefinitionServiceTest {

    @Mock
    private ClothesAttributeDefinitionRepository definitionRepository;

    @Mock
    private AttributeSelectableValueRepository selectableValueRepository;

    @Mock
    private ClothesAttributeDefinitionMapper mapper;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

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

        given(userRepository.findAllUserIds()).willReturn(List.of());

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

        given(userRepository.findAllUserIds()).willReturn(List.of());

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
    @Test
    void 잘못된_sortBy면_예외가_발생한다() {
        //when & then
        assertThatThrownBy(() -> service.getList("invalidField", "ASCENDING", null))
                .isInstanceOf(InvalidSortConditionException.class);
    }

    @Test
    void 잘못된_sortDirection이면_예외가_발생한다() {
        //when & then
        assertThatThrownBy(() -> service.getList("createdAt", "invalidDirection", null))
                .isInstanceOf(InvalidSortConditionException.class);
    }

    @Test
    void 수정_시_요청에_없는_기존_선택값은_논리_삭제된다() {
        //given
        UUID definitionId = UUID.randomUUID();
        ClothesAttributeDefinition definition = new ClothesAttributeDefinition("색상");
        AttributeSelectableValue red = new AttributeSelectableValue(definition, "빨강", 0);
        AttributeSelectableValue blue = new AttributeSelectableValue(definition, "파랑", 1);

        ClothesAttributeDefinitionRequest request = new ClothesAttributeDefinitionRequest("색상", List.of("빨강"));

        given(definitionRepository.findById(definitionId)).willReturn(Optional.of(definition));
        given(selectableValueRepository.findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(List.of(definition)))
                .willReturn(List.of(red, blue));
        given(selectableValueRepository.findByDefinitionAndValue(definition, "빨강"))
                .willReturn(Optional.of(red));
        given(mapper.toResponse(any(), any()))
                .willReturn(new ClothesAttributeDefinitionResponse(definitionId, "색상", List.of("빨강"), null));

        //when
        service.update(definitionId, request);

        //then
        assertThat(blue.getDeletedAt()).isNotNull();
        assertThat(red.getDeletedAt()).isNull();
    }

    @Test
    void 수정_시_논리_삭제됐던_선택값을_다시_요청하면_복구된다() {
        //given
        UUID definitionId = UUID.randomUUID();
        ClothesAttributeDefinition definition = new ClothesAttributeDefinition("색상");
        AttributeSelectableValue green = new AttributeSelectableValue(definition, "초록", 0);
        green.delete();

        ClothesAttributeDefinitionRequest request = new ClothesAttributeDefinitionRequest("색상", List.of("초록"));

        given(definitionRepository.findById(definitionId)).willReturn(Optional.of(definition));
        given(selectableValueRepository.findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(List.of(definition)))
                .willReturn(List.of());
        given(selectableValueRepository.findByDefinitionAndValue(definition, "초록"))
                .willReturn(Optional.of(green));
        given(mapper.toResponse(any(), any()))
                .willReturn(new ClothesAttributeDefinitionResponse(definitionId, "색상", List.of("초록"), null));

        //when
        service.update(definitionId, request);

        //then
        assertThat(green.getDeletedAt()).isNull();
    }

    @Test
    void 수정_시_새로운_선택값은_새로_생성된다() {
        //given
        UUID definitionId = UUID.randomUUID();
        ClothesAttributeDefinition definition = new ClothesAttributeDefinition("색상");

        ClothesAttributeDefinitionRequest request = new ClothesAttributeDefinitionRequest("색상", List.of("노랑"));

        given(definitionRepository.findById(definitionId)).willReturn(Optional.of(definition));
        given(selectableValueRepository.findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(List.of(definition)))
                .willReturn(List.of());
        given(selectableValueRepository.findByDefinitionAndValue(definition, "노랑"))
                .willReturn(Optional.empty());
        given(selectableValueRepository.save(any(AttributeSelectableValue.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(mapper.toResponse(any(), any()))
                .willReturn(new ClothesAttributeDefinitionResponse(definitionId, "색상", List.of("노랑"), null));

        //when
        service.update(definitionId, request);

        //then
        verify(selectableValueRepository).save(any(AttributeSelectableValue.class));
    }

    @Test
    void 수정_시_요청_순서대로_displayOrder가_갱신된다() {
        //given
        UUID definitionId = UUID.randomUUID();
        ClothesAttributeDefinition definition = new ClothesAttributeDefinition("색상");
        AttributeSelectableValue red = new AttributeSelectableValue(definition, "빨강", 0);
        AttributeSelectableValue blue = new AttributeSelectableValue(definition, "파랑", 1);

        ClothesAttributeDefinitionRequest request = new ClothesAttributeDefinitionRequest("색상", List.of("파랑", "빨강"));

        given(definitionRepository.findById(definitionId)).willReturn(Optional.of(definition));
        given(selectableValueRepository.findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(List.of(definition)))
                .willReturn(List.of(red, blue));
        given(selectableValueRepository.findByDefinitionAndValue(definition, "빨강"))
                .willReturn(Optional.of(red));
        given(selectableValueRepository.findByDefinitionAndValue(definition, "파랑"))
                .willReturn(Optional.of(blue));
        given(mapper.toResponse(any(), any()))
                .willReturn(new ClothesAttributeDefinitionResponse(definitionId, "색상", List.of("파랑", "빨강"), null));

        //when
        service.update(definitionId, request);

        //then
        assertThat(blue.getDisplayOrder()).isEqualTo(0);
        assertThat(red.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void 논리_삭제된_정의_재등록_시_요청한_선택값만_복구된다() {
        //given
        ClothesAttributeDefinition definition = new ClothesAttributeDefinition("색상");
        definition.delete();
        AttributeSelectableValue red = new AttributeSelectableValue(definition, "빨강", 0);
        AttributeSelectableValue blue = new AttributeSelectableValue(definition, "파랑", 1);
        red.delete();
        blue.delete();

        ClothesAttributeDefinitionRequest request =
                new ClothesAttributeDefinitionRequest("색상", List.of("빨강"));

        given(definitionRepository.findByName("색상")).willReturn(Optional.of(definition));
        given(selectableValueRepository.findByDefinitionAndValue(definition, "빨강"))
                .willReturn(Optional.of(red));
        given(mapper.toResponse(any(), any()))
                .willReturn(new ClothesAttributeDefinitionResponse(definition.getId(), "색상", List.of("빨강"),null));

        given(userRepository.findAllUserIds()).willReturn(List.of());

        //when
        service.create(request);

        //then
        assertThat(definition.getDeletedAt()).isNull();
        assertThat(red.getDeletedAt()).isNull();
        assertThat(blue.getDeletedAt()).isNotNull();
    }

    @Test
    void 의상_속성_생성시_전체_사용자에게_알림을_발행한다() {
        // given
        UUID userId = UUID.randomUUID();

        ClothesAttributeDefinitionRequest request =
            new ClothesAttributeDefinitionRequest("색상", List.of("빨강", "파랑"));

        given(definitionRepository.findByName("색상"))
            .willReturn(Optional.empty());

        given(definitionRepository.saveAndFlush(any(ClothesAttributeDefinition.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        given(selectableValueRepository.findByDefinitionAndValue(any(), any()))
            .willReturn(Optional.empty());

        given(selectableValueRepository.save(any(AttributeSelectableValue.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        given(userRepository.findAllUserIds())
            .willReturn(List.of(userId));

        given(mapper.toResponse(any(), any()))
            .willReturn(new ClothesAttributeDefinitionResponse(
                UUID.randomUUID(),
                "색상",
                List.of("빨강", "파랑"),
                null
            ));

        // when
        service.create(request);

        // then
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        verify(eventPublisher).publishEvent(captor.capture());

        NotificationEvent notificationEvent = (NotificationEvent) captor.getValue();

        assertThat(notificationEvent.receiverId()).isEqualTo(userId);
        assertThat(notificationEvent.title()).isEqualTo("새 의상 속성이 추가되었습니다.");
        assertThat(notificationEvent.content()).isEqualTo("'색상' 의상 속성이 추가되었습니다.");
        assertThat(notificationEvent.level()).isEqualTo(NotificationLevel.INFO);
    }


}