package com.buddygo.buddygoserver.domain.notification.support;

/** 알림을 눌렀을 때 이동할 대상. 없는 것은 null 이다. */
public record NotificationTarget(Long gatheringId, Long noticeId, Long buddyRequestId) {

    public static NotificationTarget none() {
        return new NotificationTarget(null, null, null);
    }

    public static NotificationTarget gathering(Long gatheringId) {
        return new NotificationTarget(gatheringId, null, null);
    }

    public static NotificationTarget notice(Long gatheringId, Long noticeId) {
        return new NotificationTarget(gatheringId, noticeId, null);
    }

    public static NotificationTarget buddyRequest(Long buddyRequestId) {
        return new NotificationTarget(null, null, buddyRequestId);
    }
}
