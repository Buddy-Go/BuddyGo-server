package com.buddygo.buddygoserver.domain.review.repository;

import com.buddygo.buddygoserver.domain.review.entity.Review;
import com.buddygo.buddygoserver.domain.review.exception.ReviewErrorType;
import com.buddygo.buddygoserver.global.exception.CustomException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * 받은 평가 목록의 무한 스크롤 커서: 마지막으로 받은 평가의 (작성 시각, id). 다음 요청은 이 키 뒤부터 가져오므로
 * 스크롤하는 사이에 새 평가가 쌓여도 중복되지 않는다. 앱에는 내용을 알 필요 없는 문자열로 준다.
 */
public record ReviewCursor(Instant createdAt, long reviewId) {

    /** 첫 페이지: 어떤 평가보다도 뒤인 키 (PostgreSQL timestamptz 가 담을 수 있는 값) */
    public static final ReviewCursor FIRST = new ReviewCursor(Instant.parse("9999-12-31T23:59:59Z"), Long.MAX_VALUE);

    public static ReviewCursor after(Review review) {
        return new ReviewCursor(review.getCreatedAt(), review.getId());
    }

    public String encode() {
        String raw = createdAt + "|" + reviewId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** null 이나 빈 값이면 첫 페이지 */
    public static ReviewCursor decode(String value) {
        if (value == null || value.isBlank()) {
            return FIRST;
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 2) {
                throw invalid();
            }
            return new ReviewCursor(Instant.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw invalid();
        }
    }

    private static CustomException invalid() {
        return new CustomException(ReviewErrorType.INVALID_CURSOR);
    }
}
