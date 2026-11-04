package com.buddygo.buddygoserver.domain.sport.gym.enums;

/** 부위 하나에서 내 기록을 모임 기준과 비교한 결과 (모임 상세의 헬스 기준 시트) */
public enum RecordComparison {
    /** 범위 안(팔은 숙련도가 같음) */
    WITHIN,
    /** 범위보다 높음(팔은 숙련도가 더 높음) */
    ABOVE,
    /** 범위보다 낮음(팔은 숙련도가 더 낮음) */
    BELOW,
    /** 내 기록이 없음('기록 추가' 표시) */
    NO_MY_RECORD,
    /** 호스트가 기록을 숨겼거나 기록이 없어 비교할 수 없음 */
    HOST_HIDDEN;

    /** 기준에 맞는지. 비교할 수 없으면 null */
    public Boolean matches() {
        return switch (this) {
            case WITHIN -> true;
            case ABOVE, BELOW -> false;
            case NO_MY_RECORD, HOST_HIDDEN -> null;
        };
    }
}
