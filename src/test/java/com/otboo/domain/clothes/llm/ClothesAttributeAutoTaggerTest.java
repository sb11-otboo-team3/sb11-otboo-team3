package com.otboo.domain.clothes.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.clothes.dto.request.ClothesAttributeRequest;
import com.otboo.domain.clothes.entity.AttributeSelectableValue;
import com.otboo.domain.clothes.entity.ClothesAttributeDefinition;
import com.otboo.domain.clothes.llm.dto.OpenRouterChatResponse;
import com.otboo.domain.clothes.llm.dto.OpenRouterChatResponse.Choice;
import com.otboo.domain.clothes.llm.dto.OpenRouterChatResponse.Message;
import com.otboo.domain.clothes.repository.AttributeSelectableValueRepository;
import com.otboo.domain.clothes.repository.ClothesAttributeDefinitionRepository;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class ClothesAttributeAutoTaggerTest {

    @Mock
    private ClothesVisionTaggingClient visionTaggingClient;

    @Mock
    private ClothesAttributeDefinitionRepository definitionRepository;

    @Mock
    private AttributeSelectableValueRepository selectableValueRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ClothesAttributeAutoTagger tagger;

    private ClothesAttributeDefinition colorDefinition;
    private ClothesAttributeDefinition detailTypeDefinition;

    @BeforeEach
    void setUp() {
        tagger = new ClothesAttributeAutoTagger(visionTaggingClient, definitionRepository, selectableValueRepository, objectMapper);

        colorDefinition = new ClothesAttributeDefinition("색상");
        ReflectionTestUtils.setField(colorDefinition, "id", UUID.randomUUID());
        detailTypeDefinition = new ClothesAttributeDefinition("세부종류");
        ReflectionTestUtils.setField(detailTypeDefinition, "id", UUID.randomUUID());
    }

    @Test
    void 필수_속성이_이미_다_채워져있으면_비전_모델을_호출하지_않는다() {
        //given
        given(definitionRepository.findByDeletedAtIsNullAndRequiredTrue())
                .willReturn(List.of(colorDefinition));
        List<ClothesAttributeRequest> provided = List.of(new ClothesAttributeRequest(colorDefinition.getId(), "빨강"));

        //when
        List<ClothesAttributeRequest> result = tagger.tagMissingRequiredAttributes(provided, new byte[]{1, 2, 3}, "image/png");

        //then
        assertThat(result).isEmpty();
        verify(visionTaggingClient, never()).requestTagging(anyList());
    }

    @Test
    void 정상_응답이면_빠진_속성을_채워서_반환한다() {
        //given
        given(definitionRepository.findByDeletedAtIsNullAndRequiredTrue())
                .willReturn(List.of(colorDefinition, detailTypeDefinition));
        given(selectableValueRepository.findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(anyList()))
                .willReturn(List.of(
                        new AttributeSelectableValue(colorDefinition, "빨강", 0),
                        new AttributeSelectableValue(detailTypeDefinition, "반팔", 0)
                ));
        String content = """
                {"attributes": [{"name": "색상", "value": "빨강"}, {"name": "세부종류", "value": "반팔"}]}
                """;
        given(visionTaggingClient.requestTagging(anyList())).willReturn(Mono.just(chatResponse(content)));

        //when
        List<ClothesAttributeRequest> result = tagger.tagMissingRequiredAttributes(List.of(), new byte[]{1, 2, 3}, "image/png");

        //then
        assertThat(result).containsExactlyInAnyOrder(
                new ClothesAttributeRequest(colorDefinition.getId(), "빨강"),
                new ClothesAttributeRequest(detailTypeDefinition.getId(), "반팔")
        );
    }

    @Test
    void 선택지_밖의_값은_무시된다() {
        //given
        given(definitionRepository.findByDeletedAtIsNullAndRequiredTrue())
                .willReturn(List.of(colorDefinition));
        given(selectableValueRepository.findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(anyList()))
                .willReturn(List.of(new AttributeSelectableValue(colorDefinition, "빨강", 0)));
        String content = """
                {"attributes": [{"name": "색상", "value": "보라"}]}
                """;
        given(visionTaggingClient.requestTagging(anyList())).willReturn(Mono.just(chatResponse(content)));

        //when
        List<ClothesAttributeRequest> result = tagger.tagMissingRequiredAttributes(List.of(), new byte[]{1, 2, 3}, "image/png");

        //then
        assertThat(result).isEmpty();
    }

    @Test
    void 모르는_속성_이름은_무시된다() {
        //given
        given(definitionRepository.findByDeletedAtIsNullAndRequiredTrue())
                .willReturn(List.of(colorDefinition));
        given(selectableValueRepository.findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(anyList()))
                .willReturn(List.of(new AttributeSelectableValue(colorDefinition, "빨강", 0)));
        String content = """
                {"attributes": [{"name": "소재", "value": "면"}]}
                """;
        given(visionTaggingClient.requestTagging(anyList())).willReturn(Mono.just(chatResponse(content)));

        //when
        List<ClothesAttributeRequest> result = tagger.tagMissingRequiredAttributes(List.of(), new byte[]{1, 2, 3}, "image/png");

        //then
        assertThat(result).isEmpty();
    }

    @Test
    void 비전_모델_호출이_실패하면_빈_리스트를_반환한다() {
        //given
        given(definitionRepository.findByDeletedAtIsNullAndRequiredTrue())
                .willReturn(List.of(colorDefinition));
        given(selectableValueRepository.findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(anyList()))
                .willReturn(List.of(new AttributeSelectableValue(colorDefinition, "빨강", 0)));
        given(visionTaggingClient.requestTagging(anyList()))
                .willReturn(Mono.error(new RuntimeException("OpenRouter 호출 실패")));

        //when
        List<ClothesAttributeRequest> result = tagger.tagMissingRequiredAttributes(List.of(), new byte[]{1, 2, 3}, "image/png");

        //then
        assertThat(result).isEmpty();
    }

    @Test
    void 응답을_파싱할_수_없으면_빈_리스트를_반환한다() {
        //given
        given(definitionRepository.findByDeletedAtIsNullAndRequiredTrue())
                .willReturn(List.of(colorDefinition));
        given(selectableValueRepository.findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(anyList()))
                .willReturn(List.of(new AttributeSelectableValue(colorDefinition, "빨강", 0)));
        given(visionTaggingClient.requestTagging(anyList()))
                .willReturn(Mono.just(chatResponse("이건 JSON이 아니다")));

        //when
        List<ClothesAttributeRequest> result = tagger.tagMissingRequiredAttributes(List.of(), new byte[]{1, 2, 3}, "image/png");

        //then
        assertThat(result).isEmpty();
    }

    private OpenRouterChatResponse chatResponse(String content) {
        return new OpenRouterChatResponse(List.of(new Choice(new Message("assistant", content))));
    }
}
