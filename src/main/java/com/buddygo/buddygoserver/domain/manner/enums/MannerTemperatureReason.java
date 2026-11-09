package com.buddygo.buddygoserver.domain.manner.enums;

/** manner_temperature_logs.reason. 나가기·호스트 취소 페널티와 호스트 출석 보너스는 아직 없다. */
public enum MannerTemperatureReason {
    /** 평가 한 건 (review_id 연결) */
    REVIEW,
    /** 노쇼: 시작 후 no-show-after-start 까지 체크인하지 않음 (gathering_id 연결) */
    NO_SHOW,
    /** 지각 체크인 (gathering_id 연결) */
    LATE
}
