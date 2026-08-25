package com.otboo.domain.clothes.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.clothes.dto.request.ClothesAttributeRequest;
import com.otboo.domain.clothes.entity.AttributeSelectableValue;
import com.otboo.domain.clothes.entity.ClothesAttributeDefinition;
import com.otboo.domain.clothes.llm.dto.VisionMessage;
import com.otboo.domain.clothes.repository.AttributeSelectableValueRepository;
import com.otboo.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClothesAttributeAutoTagger {

    private final ClothesVisionTaggingClient visionTaggingClient;
    private final ClothesAttributeDefinitionRepository definitionRepository;
    private final AttributeSelectableValueRepository selectableValueRepository;
    private final ObjectMapper objectMapper;

    public List<ClothesAttributeRequest> tagMissingRequiredAttributes(
            List<ClothesAttributeRequest> providedAttributes,
            byte[] imageBytes,
            String imageContentType
    ) {
        List<ClothesAttributeDefinition> missingDefinitions = findMissingRequiredDefinitions(providedAttributes);
        if (missingDefinitions.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<String>> selectableValuesByDefinitionId = selectableValueRepository
                .findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(missingDefinitions)
                .stream()
                .collect(Collectors.groupingBy(
                        value -> value.getDefinition().getId(),
                        Collectors.mapping(AttributeSelectableValue::getValue, Collectors.toList())
                ));

        try {
            String prompt = buildPrompt(missingDefinitions, selectableValuesByDefinitionId);
            String imageDataUrl = "data:" + imageContentType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

            String content = visionTaggingClient
                    .requestTagging(List.of(VisionMessage.user(prompt, imageDataUrl)))
                    .block()
                    .choices()
                    .get(0)
                    .message()
                    .content();

            TaggedAttributesPayload payload = objectMapper.readValue(content, TaggedAttributesPayload.class);
            return toValidAttributeRequests(payload, missingDefinitions, selectableValuesByDefinitionId);
        } catch (Exception e) {
            log.warn("이미지 기반 속성 자동 태깅 실패, 사용자 입력만으로 진행합니다.", e);
            return List.of();
        }
    }

    private List<ClothesAttributeDefinition> findMissingRequiredDefinitions(List<ClothesAttributeRequest> providedAttributes) {
        Set<UUID> providedDefinitionIds = providedAttributes.stream()
                .map(ClothesAttributeRequest::definitionId)
                .collect(Collectors.toSet());

        return definitionRepository.findByDeletedAtIsNullAndRequiredTrue().stream()
                .filter(definition -> !providedDefinitionIds.contains(definition.getId()))
                .toList();
    }

    private List<ClothesAttributeRequest> toValidAttributeRequests(
            TaggedAttributesPayload payload,
            List<ClothesAttributeDefinition> missingDefinitions,
            Map<UUID, List<String>> selectableValuesByDefinitionId
    ) {
        Map<String, ClothesAttributeDefinition> definitionByName = missingDefinitions.stream()
                .collect(Collectors.toMap(ClothesAttributeDefinition::getName, definition -> definition));

        return payload.attributes().stream()
                .map(tagged -> {
                    ClothesAttributeDefinition definition = definitionByName.get(tagged.name());
                    if (definition == null) {
                        return null;
                    }
                    List<String> selectableValues = selectableValuesByDefinitionId.getOrDefault(definition.getId(), List.of());
                    if (!selectableValues.contains(tagged.value())) {
                        return null;
                    }
                    return new ClothesAttributeRequest(definition.getId(), tagged.value());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private String buildPrompt(
            List<ClothesAttributeDefinition> missingDefinitions,
            Map<UUID, List<String>> selectableValuesByDefinitionId
    ) {
        String attributeLines = missingDefinitions.stream()
                .map(definition -> "- %s: %s".formatted(
                        definition.getName(),
                        selectableValuesByDefinitionId.getOrDefault(definition.getId(), List.of())
                ))
                .collect(Collectors.joining("\n"));

        return """
                다음은 의상 이미지야. 아래 속성 각각에 대해 제시된 선택지 중 하나를 골라줘.
                %s
                확신이 없는 속성은 결과에서 제외해도 돼. 반드시 아래 JSON 형식으로만 답해.
                {"attributes": [{"name": "속성이름", "value": "선택지 중 하나"}, ...]}
                """.formatted(attributeLines);
    }

    private record TaggedAttributesPayload(List<TaggedAttribute> attributes) {
    }

    private record TaggedAttribute(String name, String value) {
    }
}
