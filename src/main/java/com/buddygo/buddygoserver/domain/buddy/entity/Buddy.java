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
/** 수락 시 A→B, B→A 두 행이 생긴다. */
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
}
