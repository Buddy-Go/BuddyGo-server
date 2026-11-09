package com.buddygo.buddygoserver.domain.notification.support;

/**
 * 알림 문구. 모임 알림은 모임 제목을 담는다. 모임이 삭제되면 알림의 gathering_id 가 NULL 이 되어 제목만 남기 때문이다.
 * 길이는 저장할 때 notifications.message(255자)에 맞게 자른다(Notification.of).
 */
public final class NotificationMessages {

    private NotificationMessages() {
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
}
