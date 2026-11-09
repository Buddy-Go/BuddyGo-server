package com.buddygo.buddygoserver.domain.manner.enums;

/**
 * manner_temperature_logs.reason.
 *
 * <p>{@code oncePerGathering} 사유는 한 모임에서 사람마다 한 번만 생기므로, 부분 유니크 인덱스
 * uk_manner_temperature_logs_once_per_gathering(V9)에 걸고 ON CONFLICT 로 넣는다. 이 목록을 바꾸면 인덱스 조건(새 마이그레이션)과
 * {@code MannerTemperatureLogRepository.insertOncePerGatheringIgnoringDuplicate} 의 조건도 함께 바꾼다.
 */
public enum MannerTemperatureReason {
    /** 평가 한 건 (review_id 연결) */
    REVIEW(false),
    /** 노쇼: 시작 후 no-show-after-start 까지 체크인하지 않음 */
    NO_SHOW(true),
    /** 지각 체크인 */
    LATE(true),
    /** 마감된 모임에서 데드라인 전에 나감(마감이 풀려 다시 모집). 다시 들어와 또 나가면 또 생긴다. */
    LEFT_BEFORE_DEADLINE(false),
    /** 마감된 모임에서 데드라인 후에 나감(모임 취소, 방폭) */
    LEFT_AFTER_DEADLINE(true),
    /** 호스트 취소 · 모집중(확정 참가자 있음) */
    HOST_CANCELED_RECRUITING(true),
    /** 호스트 취소 · 마감 후 */
    HOST_CANCELED_CLOSED(true),
    /** 호스트 출석 보너스: 종료된 모임에서 호스트가 체크인(정상·지각)함 */
    HOST_ATTENDED(true);

    private final boolean oncePerGathering;

    MannerTemperatureReason(boolean oncePerGathering) {
        this.oncePerGathering = oncePerGathering;
    }

    public boolean isOncePerGathering() {
        return oncePerGathering;
    }
}
