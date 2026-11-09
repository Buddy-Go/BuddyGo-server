package com.buddygo.buddygoserver.domain.notification.support;

/**
 * 알림 문구. 모임 알림은 모임 제목을 담는다. 모임이 삭제되면 알림의 gathering_id 가 NULL 이 되어 제목만 남기 때문이다.
 * 길이는 저장할 때 notifications.message(255자)에 맞게 자른다(Notification.of).
 */
public final class NotificationMessages {

    private NotificationMessages() {
    }

    public static String applicationReceived(String title, String applicantNickname) {
        return "%s님이 %s 모임에 참여를 신청했어요.".formatted(applicantNickname, quote(title));
    }

    public static String applicationAccepted(String title) {
        return "%s 모임 참여가 확정됐어요.".formatted(quote(title));
    }

    public static String applicationRejectedByHost(String title) {
        return "%s 모임 참여 신청이 거절됐어요.".formatted(quote(title));
    }

    public static String applicationRejectedFull(String title) {
        return "%s 모임 정원이 다 차서 신청이 마감됐어요.".formatted(quote(title));
    }

    public static String applicationRejectedCanceled(String title) {
        return "%s 모임이 취소되어 신청이 거절됐어요.".formatted(quote(title));
    }

    public static String applicationRejectedDeleted(String title) {
        return "%s 모임이 삭제되어 신청이 거절됐어요.".formatted(quote(title));
    }

    public static String gatheringClosed(String title) {
        return "%s 모임 정원이 다 찼어요. 모임이 확정됐어요.".formatted(quote(title));
    }

    public static String memberLeftReopened(String title) {
        return "%s 모임에서 참가자가 나가 다시 모집해요.".formatted(quote(title));
    }

    public static String canceledMemberLeft(String title) {
        return "%s 모임에서 참가자가 나가 모임이 취소됐어요.".formatted(quote(title));
    }

    public static String canceledUnderCapacity(String title) {
        return "%s 모임이 정원이 차지 않아 취소됐어요.".formatted(quote(title));
    }

    public static String canceledByHost(String title, String reason) {
        return "%s 모임을 호스트가 취소했어요. 사유: %s".formatted(quote(title), reason);
    }

    public static String checkInOpened(String title) {
        return "%s 모임 체크인이 열렸어요. 모임 장소에서 체크인해 주세요.".formatted(quote(title));
    }

    public static String gatheringStarted(String title) {
        return "%s 모임이 시작됐어요. 아직 체크인하지 않았어요.".formatted(quote(title));
    }

    public static String gatheringFinished(String title) {
        return "%s 모임이 끝났어요. 함께한 사람을 평가해 주세요.".formatted(quote(title));
    }

    public static String noticeCreated() {
        return "새 모임 공지가 등록되었습니다.";
    }

    public static String noticeUpdated() {
        return "모임 공지가 수정되었습니다.";
    }

    public static String noticeCommented() {
        return "모임 공지에 새 댓글이 등록되었습니다.";
    }

    public static String buddyRequestReceived(String senderNickname) {
        return "%s님이 버디 신청을 보냈어요.".formatted(senderNickname);
    }

    public static String buddyRequestAccepted(String accepterNickname) {
        return "%s님과 버디가 됐어요.".formatted(accepterNickname);
    }

    public static String buddyGatheringCreated(String buddyNickname, String title) {
        return "버디 %s님이 %s 모임을 열었어요.".formatted(buddyNickname, quote(title));
    }

    public static String buddyGatheringApplied(String buddyNickname, String title) {
        return "버디 %s님이 %s 모임에 신청했어요.".formatted(buddyNickname, quote(title));
    }

    private static String quote(String title) {
        return "'" + title + "'";
    }
}
