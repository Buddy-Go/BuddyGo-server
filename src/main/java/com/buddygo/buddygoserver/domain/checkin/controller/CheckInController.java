package com.buddygo.buddygoserver.domain.checkin.controller;

import com.buddygo.buddygoserver.domain.checkin.api.CheckInApi;
import com.buddygo.buddygoserver.domain.checkin.dto.request.CheckInRequest;
import com.buddygo.buddygoserver.domain.checkin.dto.response.CheckInResponse;
import com.buddygo.buddygoserver.domain.checkin.dto.response.CheckInStatusResponse;
import com.buddygo.buddygoserver.domain.checkin.exception.CheckInOutOfRangeException;
import com.buddygo.buddygoserver.domain.checkin.exception.CheckInOutOfRangeResponse;
import com.buddygo.buddygoserver.domain.checkin.service.CheckInCommandService;
import com.buddygo.buddygoserver.domain.checkin.service.CheckInQueryService;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class CheckInController implements CheckInApi {

    private final CheckInCommandService commandService;
    private final CheckInQueryService queryService;

    @Override
    public ResponseEntity<SuccessResponse<CheckInResponse>> checkIn(Long userId, Long gatheringId, CheckInRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SuccessResponse.created(commandService.checkIn(userId, gatheringId, request)));
    }

    @Override
    public ResponseEntity<SuccessResponse<CheckInStatusResponse>> getStatus(Long userId, Long gatheringId) {
        return ResponseEntity.ok(SuccessResponse.from(queryService.getStatus(userId, gatheringId)));
    }

    /**
     * 범위 밖은 공통 에러 형식에 거리·반경을 더해 준다. 컨트롤러의 핸들러가 전역 처리기(CustomException)보다 먼저 선택된다.
     */
    @ExceptionHandler(CheckInOutOfRangeException.class)
    public ResponseEntity<CheckInOutOfRangeResponse> handleOutOfRange(CheckInOutOfRangeException e) {
        log.warn("[Business Warning] {} {}m > {}m", e.getErrorType().getCode(), e.getDistanceMeters(), e.getRadiusMeters());
        return ResponseEntity.status(e.getErrorType().getStatus()).body(CheckInOutOfRangeResponse.from(e));
    }
}
