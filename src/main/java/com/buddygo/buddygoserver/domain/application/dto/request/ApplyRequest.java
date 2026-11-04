package com.buddygo.buddygoserver.domain.application.dto.request;

import com.buddygo.buddygoserver.domain.sport.gym.dto.request.GymRecordRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Objects;

/** 본문 없이 보내도 된다(한마디·헬스 수준 모두 선택). */
public record ApplyRequest(
        @Schema(description = "호스트에게 남기는 한마디. 선택, 200자 이하", example = "근처 살아서 자주 갈 수 있어요!")
        @Size(max = 200, message = "한마디는 200자 이하입니다.")
        String message,

        @Schema(description = """
                호스트에게 보여줄 내 헬스 수준. 모임 부위만 보낼 수 있다(아니면 AP009).
                - 생략(null): 지금 프로필 기록을 그대로 쓴다(프로필은 바뀌지 않음)
                - 보내면: 그 값을 신청에 저장하고 보낸 부위는 프로필에도 반영한다. 보내지 않은 부위는 '미설정'
                - 값 규칙은 헬스 기록 저장과 같다(가슴·하체·어깨 1RM kg, 등 턱걸이 횟수, 팔 숙련도)""")
        @Size(max = 5, message = "부위는 최대 5개입니다.")
        List<@NotNull(message = "부위 기록이 비어 있습니다.") @Valid GymRecordRequest> gymRecords
) {

    @AssertTrue(message = "같은 부위를 두 번 보낼 수 없습니다.")
    private boolean isGymRecords() {
        if (gymRecords == null) {
            return true;
        }
        List<?> parts = gymRecords.stream().filter(Objects::nonNull).map(GymRecordRequest::bodyPart)
                .filter(Objects::nonNull).toList();
        return parts.size() == parts.stream().distinct().count();
    }
}
