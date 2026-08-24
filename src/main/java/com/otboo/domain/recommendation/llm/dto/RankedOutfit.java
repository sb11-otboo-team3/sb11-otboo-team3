package com.otboo.domain.recommendation.llm.dto;

import java.util.List;
import java.util.UUID;

public record RankedOutfit(
        List<UUID> clothesIds
) {
}
