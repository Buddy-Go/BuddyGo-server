package com.buddygo.buddygoserver.domain.user.dto.response;

import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.enums.Gender;
import com.buddygo.buddygoserver.domain.user.enums.Provider;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/** 마이페이지 기본 정보. 후기 수·개설/참여 횟수·많이 받은 태그는 후기·체크인 기능과 함께 추가한다. */
public record MyInfoResponse(
        @Schema(example = "1") Long userId,
        @Schema(example = "buddy@example.com") String email,
        @Schema(example = "김버디") String name,
        @Schema(example = "헬스버디") String nickname,
        @Schema(example = "MALE") Gender gender,
        @Schema(description = "가입 방식", example = "LOCAL") Provider provider,
        @Schema(example = "36.5") BigDecimal mannerTemperature,
        @Schema(description = "전체 알림 ON/OFF", example = "true") boolean pushEnabled
) {

    public static MyInfoResponse from(User user) {
        return new MyInfoResponse(user.getId(), user.getEmail(), user.getName(), user.getNickname(),
                user.getGender(), user.getProvider(), user.getMannerTemperature(), user.isPushEnabled());
    }
}
