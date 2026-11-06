package com.buddygo.buddygoserver.domain.buddy.entity;

import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
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
@Table(name = "buddies")
/**
 * 수락 시 A→B, B→A 두 행이 생긴다.
 *
 * <p>알림 설정은 행의 주인(user) 기준이다: (user=U, buddy=X) 행의 notify_* 는 'X 가 모임을 개설·신청하면 U 에게
 * 알린다'는 뜻이다. 알림을 보낼 때는 {@code buddy_id = 행동한 사람 and notify_* = true} 인 행의 user 에게 보낸다.
 */
public class Buddy extends BaseCreatedEntity {

    @EmbeddedId
    private BuddyId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @MapsId("buddyId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buddy_id")
    private User buddy;

    @Column(name = "notify_gathering_created", nullable = false)
    @Builder.Default
    private boolean notifyGatheringCreated = true;

    @Column(name = "notify_gathering_applied", nullable = false)
    @Builder.Default
    private boolean notifyGatheringApplied = true;

    /** user 쪽 한 방향 행. 알림은 기본 ON 이다. */
    public static Buddy of(User user, User buddy) {
        return Buddy.builder()
                .id(new BuddyId(user.getId(), buddy.getId()))
                .user(user)
                .buddy(buddy)
                .build();
    }

    /** null 인 항목은 그대로 둔다. */
    public void changeNotification(Boolean gatheringCreated, Boolean gatheringApplied) {
        if (gatheringCreated != null) {
            this.notifyGatheringCreated = gatheringCreated;
        }
        if (gatheringApplied != null) {
            this.notifyGatheringApplied = gatheringApplied;
        }
    }
}
