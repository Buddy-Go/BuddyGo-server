package com.buddygo.buddygoserver.domain.gathering.repository;

import com.buddygo.buddygoserver.domain.gathering.exception.GatheringErrorType;
import com.buddygo.buddygoserver.global.exception.CustomException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * 탐색 리스트의 무한 스크롤 커서: 마지막으로 받은 카드의 정렬 키. 다음 요청은 이 키 뒤부터 가져오므로
 * 스크롤하는 사이에 앞쪽 모임이 빠지거나 생겨도 건너뛰거나 중복되지 않는다.
 * 앱에는 내용을 알 필요 없는 문자열로 준다.
 *
 * @param distanceMeters 내 위치로 정렬했으면 거리, 시작 시각순이면 null
 */
public record GatheringSearchCursor(Double distanceMeters, Instant startAt, long gatheringId) {

    public String encode() {
        String raw = (distanceMeters == null ? "" : Double.toString(distanceMeters)) + "|" + startAt + "|" + gatheringId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static GatheringSearchCursor decode(String value) {
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 3) {
                throw invalid();
            }
            return new GatheringSearchCursor(parts[0].isEmpty() ? null : Double.valueOf(parts[0]),
                    Instant.parse(parts[1]), Long.parseLong(parts[2]));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw invalid();
        }
    }

    private static CustomException invalid() {
        return new CustomException(GatheringErrorType.INVALID_SEARCH_CURSOR);
    }
}
