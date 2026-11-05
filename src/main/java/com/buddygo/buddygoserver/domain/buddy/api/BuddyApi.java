package com.buddygo.buddygoserver.domain.buddy.api;

import com.buddygo.buddygoserver.domain.buddy.dto.response.ActivityResponse;
import com.buddygo.buddygoserver.domain.buddy.dto.response.BuddyResponse;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import com.buddygo.buddygoserver.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Tag(name = "buddy", description = "버디(메이트) 신청 · 목록 · 함께 운동한 사람")
@ApiResponses(@ApiResponse(responseCode = "401", description = "G004 토큰 없음 · A002 유효하지 않음 · A003 만료"))
public interface BuddyApi {

    @Operation(summary = "버디 목록",
            description = """
                    버디마다 대표 활동 하나와 버디별 알림 설정을 담는다.
                    대표 활동은 지금 운동 중인 모임, 없으면 가장 가까운 예정 모임(개설·신청 대기·확정)이고, 없으면 null('예정된 모임 없어요').
                    지금 운동 중 → 예정 모임이 가까운 순 → 예정 없음 순으로 준다.""")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/api/buddies")
    ResponseEntity<SuccessResponse<List<BuddyResponse>>> getBuddies(@LoginUserId Long userId);

    @Operation(summary = "버디 활동 조회",
            description = """
                    버디만 볼 수 있다. 버디가 개설했거나 신청(대기)·확정한 모임 중 아직 끝나지 않은 것.
                    지금 운동 중(inProgress)인 것이 먼저, 그다음 시작이 이른 순.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "B005 버디가 아님")
    })
    @GetMapping("/api/users/{userId}/activities")
    ResponseEntity<SuccessResponse<List<ActivityResponse>>> getActivities(
            @LoginUserId Long loginUserId, @PathVariable("userId") Long targetId);
}
