package com.buddygo.buddygoserver.global.exception;

import com.buddygo.buddygoserver.global.response.error.type.BaseErrorType;

import java.util.Map;

/**
 * DB 유니크 제약 이름 → 돌려줄 에러 타입. 도메인마다 빈으로 등록하고 {@link GlobalExceptionHandler} 가 모아 쓴다.
 *
 * <p>사전 검사(exists…)를 동시 요청이 함께 통과해 제약에 걸린 경우를 위한 것이다.
 * 중복이 정상 흐름인 제약(찜, 같은 카카오 장소 재사용 등)은 등록하지 말고 ON CONFLICT 로 처리한다.
 */
public interface UniqueConstraintErrors {

    Map<String, BaseErrorType> byConstraint();
}
