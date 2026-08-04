package com.otboo.domain.clothes.repository;

import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.entity.ClothesType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ClothesRepositoryCustom {

    List<Clothes> findClothesList(
            UUID ownerId,
            ClothesType typeEqual,
            Instant cursor,
            UUID idAfter,
            int limit
    );
    long countClothes(UUID ownerId, ClothesType typeEqual);
}
