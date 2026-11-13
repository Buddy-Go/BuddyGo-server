package com.buddygo.buddygoserver.domain.review.api;

import com.buddygo.buddygoserver.domain.review.dto.request.ReviewSubmitRequest;
import com.buddygo.buddygoserver.domain.review.dto.response.PendingReviewsResponse;
import com.buddygo.buddygoserver.domain.review.dto.response.ReceivedReviewsResponse;
import com.buddygo.buddygoserver.domain.review.dto.response.ReviewResponse;
import com.buddygo.buddygoserver.domain.review.dto.response.ReviewTagsResponse;
import com.buddygo.buddygoserver.domain.review.dto.response.ReviewTargetsResponse;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import com.buddygo.buddygoserver.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "review", description = "평가")
@ApiResponses(@ApiResponse(responseCode = "401", description = "G004 토큰 없음 · A002 유효하지 않음 · A003 만료"))
public interface ReviewApi {

    @Operation(summary = "평가 대기 목록", description = """
            내가 체크인(정상·지각)한 끝난 모임 중 평가 기한(종료 후 3일) 전이고 아직 평가하지 않은 사람이 남은 모임. 기한이 가까운 순.""")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/api/reviews/pending")
    ResponseEntity<SuccessResponse<PendingReviewsResponse>> getPending(@LoginUserId Long userId);

    @Operation(summary = "평가 태그 목록", description = "긍정·부정 태그와 문구. 판정과 상관없이 두 묶음 모두 고를 수 있다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/api/review-tags")
    ResponseEntity<SuccessResponse<ReviewTagsResponse>> getTags();

    @Operation(summary = "평가 대상 조회", description = """
            끝난 모임에서 나를 뺀 체크인(정상·지각)한 사람들(호스트 포함)과 내가 평가했는지, 평가 기한.
            노쇼한 사람은 대상이 아니다. 기한이 지나도 볼 수 있고 이때 reviewable=false 다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "GA001 없는 모임"),
            @ApiResponse(responseCode = "409", description = "R001 끝나지 않았거나 취소된 모임, 또는 내가 체크인하지 않은 모임")
    })
    @GetMapping("/api/gatherings/{gatheringId}/review-targets")
    ResponseEntity<SuccessResponse<ReviewTargetsResponse>> getTargets(
            @LoginUserId Long userId, @PathVariable Long gatheringId);

    @Operation(summary = "평가 제출", description = """
            한 사람에 대한 평가. 판정(필수) + 태그(필수 1~5개, 판정과 상관없이 긍정·부정 모두 가능) + 한마디(선택, 200자).
            제출하면 상대 매너온도에 곧바로 반영되고, 고치거나 지울 수 없다. 사람마다 한 번씩 따로 제출한다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "제출 성공"),
            @ApiResponse(responseCode = "400", description = "G002 입력값(판정 없음, 태그 없음·6개 이상, 한마디 200자 초과) · G003 읽을 수 없는 본문(없는 판정·태그 이름) · R004 평가할 수 없는 상대(본인, 노쇼, 이 모임에서 체크인하지 않은 사람)"),
            @ApiResponse(responseCode = "404", description = "GA001 없는 모임"),
            @ApiResponse(responseCode = "409", description = "R001 끝나지 않았거나 취소된 모임, 또는 내가 체크인하지 않은 모임 · R002 이미 평가함 · R003 기한 지남")
    })
    @PostMapping("/api/gatherings/{gatheringId}/reviews")
    ResponseEntity<SuccessResponse<ReviewResponse>> submit(
            @LoginUserId Long userId, @PathVariable Long gatheringId, @Valid @RequestBody ReviewSubmitRequest request);

    @Operation(summary = "내가 받은 후기", description = """
            최신순 무한 스크롤. 아쉬움 평가도 보이지만 누가 줬는지 드러나지 않게 evaluator·gathering 이 null 이다.
            한마디·태그가 없는 평가도 판정만 있는 줄로 나온다. 첫 요청은 cursor 없이, 다음부터는 받은 nextCursor 를 보낸다.
            size는 1~100(기본 20).""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공. 후기가 없으면 content는 빈 배열"),
            @ApiResponse(responseCode = "400", description = "G002 잘못된 size · R005 잘못된 커서")
    })
    @GetMapping("/api/users/me/reviews")
    ResponseEntity<SuccessResponse<ReceivedReviewsResponse>> getMyReceived(
            @LoginUserId Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size);

    @Operation(summary = "다른 사용자가 받은 후기", description = """
            최신순 무한 스크롤. 아쉬움 평가는 공개하지 않아 목록에 없다(프로필의 '후기 n'에는 들어간다).
            나머지는 내가 받은 후기와 같다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공. 후기가 없으면 content는 빈 배열"),
            @ApiResponse(responseCode = "400", description = "G002 잘못된 size · R005 잘못된 커서"),
            @ApiResponse(responseCode = "404", description = "U001 없는 사용자")
    })
    @GetMapping("/api/users/{userId}/reviews")
    ResponseEntity<SuccessResponse<ReceivedReviewsResponse>> getReceived(
            @PathVariable Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size);
}
