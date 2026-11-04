package com.buddygo.buddygoserver.domain.sport.gym.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Objects;

/**
 * 헬스 기록 전체. 보낸 부위가 내 기록 전체가 되고, 빠진 부위는 미설정으로 지워진다.
 * 모든 부위는 선택 입력이라 빈 배열이면 기록을 모두 지운다.
 */
public record GymRecordsRequest(
        @NotNull(message = "records 는 필수입니다. 기록을 모두 지우려면 빈 배열을 보낸다.")
        @Size(max = 5, message = "부위는 최대 5개입니다.")
        List<@NotNull(message = "부위 기록이 비어 있습니다.") @Valid GymRecordRequest> records
) {

    @AssertTrue(message = "같은 부위를 두 번 보낼 수 없습니다.")
    private boolean isRecords() {
        if (records == null) {
            return true;
        }
        List<?> parts = records.stream().filter(Objects::nonNull).map(GymRecordRequest::bodyPart)
                .filter(Objects::nonNull).toList();
        return parts.size() == parts.stream().distinct().count();
    }
}
