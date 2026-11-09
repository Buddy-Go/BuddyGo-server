package com.buddygo.buddygoserver.domain.checkin.exception;

import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.Getter;

/** 범위 밖(C003). 앱이 현재 거리를 보여줄 수 있도록 거리와 반경을 함께 싣는다. */
@Getter
public class CheckInOutOfRangeException extends CustomException {

    private final int distanceMeters;
    private final int radiusMeters;

    public CheckInOutOfRangeException(int distanceMeters, int radiusMeters) {
        super(CheckInErrorType.OUT_OF_RANGE);
        this.distanceMeters = distanceMeters;
        this.radiusMeters = radiusMeters;
    }
}
