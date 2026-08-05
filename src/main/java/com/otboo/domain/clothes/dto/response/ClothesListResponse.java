package com.otboo.domain.clothes.dto.response;

import java.util.List;
import java.util.UUID;

public record ClothesListResponse(
        List<ClothesResponse> data,
        String nextCursor,
        UUID nextIdAfter,
        boolean hasNext,
        long totalCount,
        String sortBy,
        String sortDirection
) {
}
