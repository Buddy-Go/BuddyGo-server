package com.buddygo.buddygoserver.domain.gathering.entity;

import com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus;
import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import com.buddygo.buddygoserver.domain.sport.Sport;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.Duration;
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
@Table(name = "gatherings",
        indexes = {
                @Index(columnList = "status, start_at"),
                @Index(columnList = "status, end_at")
        })
public class Gathering extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Sport sport = Sport.GYM;

    @Column(nullable = false)
    private String title;

    @Column(length = 300)
    private String description;

    @Column(name = "detail_address")
    private String detailAddress;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    /** 호스트 포함 2~3 */
    @Column(nullable = false)
    private short capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender_rule", nullable = false)
    private GenderRule genderRule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private GatheringStatus status = GatheringStatus.RECRUITING;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static Gathering open(User host, Place place, String title, String description, String detailAddress,
                                 Instant startAt, Duration duration, short capacity, GenderRule genderRule) {
        return Gathering.builder()
                .host(host)
                .place(place)
                .title(title)
                .description(description)
                .detailAddress(detailAddress)
                .startAt(startAt)
                .endAt(startAt.plus(duration))
                .capacity(capacity)
                .genderRule(genderRule)
                .build();
    }

    public boolean isHostedBy(Long userId) {
        return host.getId().equals(userId);
    }

    /** 진행중이 되었는지. 상태 전이 스케줄러가 늦어도 시작 시각이 지났으면 시작된 것으로 본다. */
    public boolean hasStarted(Instant now) {
        return status == GatheringStatus.IN_PROGRESS || status == GatheringStatus.FINISHED || !now.isBefore(startAt);
    }

    /** 신청·수락을 받는지. 모집중이어도 데드라인이 지났으면(정원 미달 자동 취소 대상) 받지 않는다. */
    public boolean isRecruiting(Instant now, Instant deadline) {
        return status == GatheringStatus.RECRUITING && now.isBefore(deadline);
    }

    public boolean isCanceled() {
        return status == GatheringStatus.CANCELED;
    }

    public Duration getDuration() {
        return Duration.between(startAt, endAt);
    }

    /** 확정 참가자가 있어도 바꿀 수 있는 항목 */
    public void changeDetails(String title, String description, String detailAddress) {
        this.title = title;
        this.description = description;
        this.detailAddress = detailAddress;
    }

    /** 확정 참가자(호스트 제외)가 없을 때만 바꿀 수 있는 항목 */
    public void changeCore(Place place, Instant startAt, Duration duration, short capacity, GenderRule genderRule) {
        this.place = place;
        this.startAt = startAt;
        this.endAt = startAt.plus(duration);
        this.capacity = capacity;
        this.genderRule = genderRule;
    }

    /** 확정 = 정원이 되면 자동 마감된다. 호스트가 직접 마감하는 기능은 없다. */
    public void close() {
        this.status = GatheringStatus.CLOSED;
    }

    /** 데드라인 전에 마감된 모임에서 확정 참가자가 나가면 다시 모집한다. */
    public void reopen() {
        this.status = GatheringStatus.RECRUITING;
    }

    public void cancel() {
        this.status = GatheringStatus.CANCELED;
    }

    public void delete(Instant now) {
        this.deletedAt = now;
    }
}
