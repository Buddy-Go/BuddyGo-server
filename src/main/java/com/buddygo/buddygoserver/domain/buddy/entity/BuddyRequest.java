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
}
