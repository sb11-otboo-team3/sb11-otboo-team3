package com.otboo.domain.clothes.repository;

import static com.otboo.domain.clothes.entity.QClothes.clothes;

import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.entity.ClothesType;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


public class ClothesRepositoryImpl implements ClothesRepositoryCustom{

    private final JPAQueryFactory queryFactory;

    public ClothesRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public List<Clothes> findClothesList(
            UUID ownerId,
            ClothesType typeEqual,
            Instant cursor,
            UUID idAfter,
            int limit
    ) {
        return queryFactory
                .selectFrom(clothes)
                .where(
                        clothes.owner.id.eq(ownerId),
                        clothes.deletedAt.isNull(),
                        typeEq(typeEqual),
                        cursorCondition(cursor, idAfter)
                )
                .orderBy(clothes.createdAt.desc(), clothes.id.desc())
                .limit(limit)
                .fetch();
    }

    @Override
    public long countClothes(UUID ownerId, ClothesType typeEqual) {
        Long count =queryFactory
                .select(clothes.count())
                .from(clothes)
                .where(
                        clothes.owner.id.eq(ownerId),
                        clothes.deletedAt.isNull(),
                        typeEq(typeEqual)
                )
                .fetchOne();

        return count == null ? 0 : count;
    }

    private BooleanExpression typeEq(ClothesType typeEqual) {
        if (typeEqual == null) {
            return null;
        }
        return clothes.type.eq(typeEqual);
    }

    private BooleanExpression cursorCondition(Instant cursor, UUID idAfter) {
        if (cursor == null || idAfter == null) {
            return null;
        }

        return clothes.createdAt.lt(cursor)
                .or(
                        clothes.createdAt.eq(cursor)
                                .and(clothes.id.lt(idAfter))
                );
    }
}
