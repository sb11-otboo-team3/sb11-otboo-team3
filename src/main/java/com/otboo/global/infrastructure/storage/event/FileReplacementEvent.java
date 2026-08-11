package com.otboo.global.infrastructure.storage.event;

/**
 * 파일(이미지 등) 교체 시 발행되는 이벤트.
 * 트랜잭션 커밋 후에는 oldObjectKey를, 롤백 후에는 newObjectKey를 정리 대상으로 삼는다.
 *
 * @param oldObjectKey 교체 전 기존 Object Key (없으면 null, 최초 업로드인 경우)
 * @param newObjectKey 새로 업로드된 Object Key (이미지 변경이 없었다면 null)
 */
public record FileReplacementEvent(
    String oldObjectKey,
    String newObjectKey
) {
}