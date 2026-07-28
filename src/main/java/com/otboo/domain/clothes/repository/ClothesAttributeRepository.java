package com.otboo.domain.clothes.repository;

import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.entity.ClothesAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClothesAttributeRepository extends JpaRepository<ClothesAttribute, UUID> {

    List<ClothesAttribute> findByClothes(Clothes clothes);
}
