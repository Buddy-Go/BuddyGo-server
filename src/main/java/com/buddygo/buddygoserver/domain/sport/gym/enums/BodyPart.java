package com.buddygo.buddygoserver.domain.sport.gym.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 헬스 부위. 부위마다 기록하는 값의 종류가 다르다 (서비스 규칙 정의서 1장). */
@Getter
@RequiredArgsConstructor
public enum BodyPart {
    CHEST("가슴", RecordType.WEIGHT),    // 벤치프레스 1RM
    BACK("등", RecordType.REPS),         // 맨몸 턱걸이 연속 최대 횟수
    LEGS("하체", RecordType.WEIGHT),     // 스쿼트 1RM
    SHOULDER("어깨", RecordType.WEIGHT), // 오버헤드 프레스 1RM
    ARM("팔", RecordType.LEVEL);         // 숙련도

    private final String label;
    private final RecordType recordType;

    public enum RecordType {
        /** record_value 에 kg */
        WEIGHT,
        /** record_value 에 횟수 */
        REPS,
        /** arm_level 에 숙련도 */
        LEVEL
    }
}
