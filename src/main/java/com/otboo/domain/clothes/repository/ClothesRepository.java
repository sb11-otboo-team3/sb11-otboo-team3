package com.otboo.domain.clothes.repository;

import com.otboo.domain.clothes.entity.Clothes;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClothesRepository extends JpaRepository<Clothes, UUID>, ClothesRepositoryCustom {

    List<Clothes> findByOwner_IdAndDeletedAtIsNull(UUID ownerId);

    List<Clothes> findByIdInAndDeletedAtIsNull(List<UUID> clothesIds);
}
