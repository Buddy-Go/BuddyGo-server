package com.buddygo.buddygoserver.domain.notification.entity;

import com.buddygo.buddygoserver.domain.notification.enums.NotificationType;
import com.buddygo.buddygoserver.domain.notification.support.NotificationTarget;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 앱 알림 목록의 한 건. 모임·공지·버디 신청은 엔티티 연관 없이 ID 만 둔다(DB FK 와 ON DELETE SET NULL 은 그대로).
 * 알림은 여러 도메인이 부르는 쪽이라, 연관을 두면 notification 과 그 도메인들이 서로를 참조하게 된다.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "notifications")
public class Notification extends BaseCreatedEntity {

    /** notifications.message 길이 */
    public static final int MESSAGE_MAX = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    /** 알림을 일으킨 사람. 시스템 알림이면 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    /**
     * {@link NotificationType} 의 이름. 앱이 모르는 값(이후 추가·이름 변경)이 저장돼 있어도 읽을 수 있도록 문자열로 둔다.
     */
    @Column(nullable = false)
    private String type;

    /** gatherings(id), 모임이 삭제되면 NULL */
    @Column(name = "gathering_id")
    private Long gatheringId;

    /** notices(id), 공지가 삭제되면 NULL */
    @Column(name = "notice_id")
    private Long noticeId;

    /** buddy_requests(id), 신청이 수락·취소되어 지워지면 NULL */
    @Column(name = "buddy_request_id")
    private Long buddyRequestId;

    @Column(nullable = false)
    private String message;

    @Column(name = "read_at")
    private Instant readAt;

    public static Notification of(User receiver, User actor, NotificationType type, NotificationTarget target,
                                  String message) {
        return Notification.builder()
                .receiver(receiver)
                .actor(actor)
                .type(type.name())
                .gatheringId(target.gatheringId())
                .noticeId(target.noticeId())
                .buddyRequestId(target.buddyRequestId())
                .message(fit(message))
                .build();
    }

    /** 저장이 길이 초과로 실패해 알림을 일으킨 작업까지 롤백되지 않도록 자른다(코드 포인트 기준). */
    public static String fit(String message) {
        if (message.codePointCount(0, message.length()) <= MESSAGE_MAX) {
            return message;
        }
        int end = message.offsetByCodePoints(0, MESSAGE_MAX - 1);
        return message.substring(0, end) + "…";
    }
}
