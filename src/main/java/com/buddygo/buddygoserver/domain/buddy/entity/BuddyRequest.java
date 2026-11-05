package com.buddygo.buddygoserver.domain.buddy.entity;

import com.buddygo.buddygoserver.domain.buddy.enums.BuddyRequestStatus;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "buddy_requests")
/** UNIQUE (sender_id, receiver_id) WHERE status = 'PENDING' 부분 유니크 인덱스는 마이그레이션 SQL 로 추가한다. */
public class BuddyRequest extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BuddyRequestStatus status = BuddyRequestStatus.PENDING;

    @Column(name = "responded_at")
    private Instant respondedAt;

    public static BuddyRequest send(User sender, User receiver) {
        return BuddyRequest.builder()
                .sender(sender)
                .receiver(receiver)
                .build();
    }

    public boolean isPending() {
        return status == BuddyRequestStatus.PENDING;
    }

    public boolean isSentBy(Long userId) {
        return sender.getId().equals(userId);
    }

    public boolean isReceivedBy(Long userId) {
        return receiver.getId().equals(userId);
    }

    public void accept(Instant now) {
        respond(BuddyRequestStatus.ACCEPTED, now);
    }

    public void reject(Instant now) {
        respond(BuddyRequestStatus.REJECTED, now);
    }

    /** 보낸 사람이 대기 중인 신청을 거둔다. */
    public void cancel(Instant now) {
        respond(BuddyRequestStatus.CANCELED, now);
    }

    private void respond(BuddyRequestStatus status, Instant now) {
        this.status = status;
        this.respondedAt = now;
    }
}
