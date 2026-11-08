package com.buddygo.buddygoserver.domain.notification.api;

import com.buddygo.buddygoserver.domain.notification.dto.response.NotificationListResponse;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import com.buddygo.buddygoserver.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "notification", description = "알림 목록 · 읽음 · 모두 읽음")
@RequestMapping("/api/notifications")
@ApiResponses(@ApiResponse(responseCode = "401", description = "G004 토큰 없음 · A002 유효하지 않음 · A003 만료"))
public interface NotificationApi {
    @Operation(summary = "알림 목록", description = """
            본인이 받은 알림만 최신순(createdAt·id 내림차순)으로 조회한다. 조회만으로 읽음 처리하지 않는다.
            page는 0부터, size는 1~100(기본 20). unreadCount는 전체 미읽음 개수다.
            읽음·미읽음을 모두 반환하고 관련 대상이 삭제되어도 알림은 남는다.
            대상 ID가 null이면 해당 대상으로 이동할 수 없다. 실제 화면 이동 시 대상 API의 권한 검사를 따른다.""")
    @ApiResponse(responseCode = "400", description = "G002 잘못된 페이지·크기")
    @GetMapping
    ResponseEntity<SuccessResponse<NotificationListResponse>> getList(@LoginUserId Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size);

    @Operation(summary = "알림 읽음", description = "본인 알림만 읽음 처리한다. 반복 요청도 성공하고 최초 읽은 시각은 유지한다.")
    @ApiResponse(responseCode = "404", description = "NT001 없거나 본인의 알림이 아님")
    @PatchMapping("/{notificationId}/read")
    ResponseEntity<SuccessResponse<Void>> read(@LoginUserId Long userId, @PathVariable Long notificationId);

    @Operation(summary = "알림 모두 읽음", description = "본인의 미읽음 알림을 한 번에 읽음 처리한다. 이미 읽은 시각은 유지하며, 대상이 없어도 성공한다.")
    @PatchMapping("/read-all")
    ResponseEntity<SuccessResponse<Void>> readAll(@LoginUserId Long userId);
}
