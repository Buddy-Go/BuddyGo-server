package com.buddygo.buddygoserver.domain.gathering.exception;

import com.buddygo.buddygoserver.global.response.error.type.BaseErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Gathering ErrorCode: GAxxx
 */
@Getter
@RequiredArgsConstructor
public enum GatheringErrorType implements BaseErrorType {

    NOT_FOUND(HttpStatus.NOT_FOUND, "GA001", "모임을 찾을 수 없습니다."),
    NOT_HOST(HttpStatus.FORBIDDEN, "GA002", "호스트만 할 수 있습니다."),
    NOT_RECRUITING(HttpStatus.CONFLICT, "GA003", "모집 중인 모임이 아닙니다."),
    HAS_CONFIRMED_PARTICIPANTS(HttpStatus.CONFLICT, "GA004", "확정된 참가자가 있어 삭제할 수 없습니다. 모임 취소를 이용해 주세요."),
    EDIT_RESTRICTED(HttpStatus.CONFLICT, "GA005", "확정된 참가자가 있으면 제목·설명·상세주소만 수정할 수 있습니다."),
    PARTICIPANTS_NOT_VISIBLE(HttpStatus.FORBIDDEN, "GA006", "마감된 모임의 참가자만 명단을 볼 수 있습니다."),
    NO_CONFIRMED_PARTICIPANTS(HttpStatus.CONFLICT, "GA007", "확정된 참가자가 없는 모임은 취소하지 않고 삭제합니다."),
    ALREADY_STARTED(HttpStatus.CONFLICT, "GA008", "이미 시작된 모임입니다."),
    ALREADY_CANCELED(HttpStatus.CONFLICT, "GA009", "이미 취소된 모임입니다."),
    START_TOO_SOON(HttpStatus.BAD_REQUEST, "GA010", "시작 시각은 데드라인이 지나지 않도록 충분히 뒤여야 합니다."),
    INVALID_CAPACITY(HttpStatus.BAD_REQUEST, "GA011", "정원이 허용 범위를 벗어났습니다."),
    INVALID_DURATION(HttpStatus.BAD_REQUEST, "GA012", "진행 시간이 허용 범위를 벗어났습니다."),
    GENDER_RULE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "GA013", "호스트 성별과 다른 성별 조건은 고를 수 없습니다."),
    INVALID_START_TIME(HttpStatus.BAD_REQUEST, "GA014", "시작 시각 단위가 맞지 않습니다."),
    DEADLINE_PASSED_UNFILLED(HttpStatus.CONFLICT, "GA015", "데드라인까지 정원이 차지 않아 자동 취소되는 모임입니다."),
    INVALID_SEARCH_AREA(HttpStatus.BAD_REQUEST, "GA016", "검색 영역은 지도 범위(swLat·swLng·neLat·neLng)와 구·동(regionCode) 중 하나만 보내거나, 핀을 눌렀으면 parcelKey 로 지정해야 합니다."),
    INVALID_SEARCH_CONDITION(HttpStatus.BAD_REQUEST, "GA017", "검색 조건 조합이 올바르지 않습니다."),
    INVALID_SEARCH_CURSOR(HttpStatus.BAD_REQUEST, "GA018", "커서가 올바르지 않습니다. 첫 페이지부터 다시 불러와 주세요."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
