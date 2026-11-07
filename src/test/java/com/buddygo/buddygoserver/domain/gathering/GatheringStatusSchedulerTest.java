package com.buddygo.buddygoserver.domain.gathering;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import com.buddygo.buddygoserver.domain.application.enums.RejectReason;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.checkin.entity.CheckIn;
import com.buddygo.buddygoserver.domain.checkin.enums.Attendance;
import com.buddygo.buddygoserver.domain.checkin.repository.CheckInRepository;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.entity.GatheringCancellation;
import com.buddygo.buddygoserver.domain.gathering.entity.Place;
import com.buddygo.buddygoserver.domain.gathering.enums.CancellationType;
import com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus;
import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringCancellationRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.PlaceRepository;
import com.buddygo.buddygoserver.domain.gathering.scheduler.GatheringStatusScheduler;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.enums.Gender;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * 로컬 PostgreSQL(docker-compose.local.yml)이 떠 있어야 한다. DB 는 테스트마다 롤백된다.
 * 스케줄 트리거는 테스트에서 꺼져 있고(build.gradle), 시각을 넘겨 {@code runAt} 을 직접 호출한다.
 */
@Transactional
class GatheringStatusSchedulerTest extends IntegrationTest {

    static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");
    static final Instant START = NOW.plus(Duration.ofHours(3));
    /** 시작 - 90분 */
    static final Instant DEADLINE = START.minus(Duration.ofMinutes(90));
    /** 시작 + 10분 */
    static final Instant NO_SHOW_AT = START.plus(Duration.ofMinutes(10));
    /** 시작 + 60분 */
    static final Instant END = START.plus(Duration.ofMinutes(60));

    @Autowired GatheringStatusScheduler scheduler;
    @Autowired UserRepository userRepository;
    @Autowired GatheringRepository gatheringRepository;
    @Autowired GatheringCancellationRepository gatheringCancellationRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired ParticipationRepository participationRepository;
    @Autowired CheckInRepository checkInRepository;
    @Autowired EntityManager entityManager;

    User host;
    User alice;
    User bob;

    @BeforeEach
    void setUp() {
        host = user("host");
        alice = user("alice");
        bob = user("bob");
    }

    @Nested
    class 데드라인 {

        @Test
        void 데드라인에_모집중이면_정원_미달로_취소되고_대기_신청은_자동_거절된다() {
            Gathering gathering = gathering(START, GatheringStatus.RECRUITING);
            Participation pending = participationRepository.save(Participation.apply(gathering, bob, null, NOW));

            run(DEADLINE);

            assertThat(statusOf(gathering)).isEqualTo(GatheringStatus.CANCELED);
            GatheringCancellation cancellation = gatheringCancellationRepository.findById(gathering.getId()).orElseThrow();
            assertThat(cancellation.getType()).isEqualTo(CancellationType.UNDER_CAPACITY);
            assertThat(cancellation.getReason()).isNull();
            assertThat(cancellation.getCanceledAt()).isEqualTo(DEADLINE);
            Participation rejected = participationRepository.findById(pending.getId()).orElseThrow();
            assertThat(rejected.getStatus()).isEqualTo(ParticipationStatus.REJECTED);
            assertThat(rejected.getRejectReason()).isEqualTo(RejectReason.MEETING_CANCELED);
        }

        @Test
        void 데드라인_전이면_그대로_모집중이다() {
            Gathering gathering = gathering(START, GatheringStatus.RECRUITING);

            run(DEADLINE.minusSeconds(1));

            assertThat(statusOf(gathering)).isEqualTo(GatheringStatus.RECRUITING);
            assertThat(gatheringCancellationRepository.findById(gathering.getId())).isEmpty();
        }

        @Test
        void 데드라인에_마감_상태면_그대로_확정된다() {
            Gathering gathering = gathering(START, GatheringStatus.CLOSED);

            run(DEADLINE);

            assertThat(statusOf(gathering)).isEqualTo(GatheringStatus.CLOSED);
        }

        @Test
        void 시작이_지나도록_모집중이었던_모임은_진행중이_아니라_취소된다() {
            Gathering gathering = gathering(START, GatheringStatus.RECRUITING);

            run(END);

            assertThat(statusOf(gathering)).isEqualTo(GatheringStatus.CANCELED);
            assertThat(checkInRepository.findAll()).noneMatch(c -> c.getParticipation().getGathering().getId()
                    .equals(gathering.getId()));
        }
    }

    @Nested
    class 진행과_종료 {

        @Test
        void 마감된_모임은_시작_시각에_진행중이_된다() {
            Gathering gathering = gathering(START, GatheringStatus.CLOSED);

            run(START.minusSeconds(1));
            assertThat(statusOf(gathering)).isEqualTo(GatheringStatus.CLOSED);

            run(START);
            assertThat(statusOf(gathering)).isEqualTo(GatheringStatus.IN_PROGRESS);
        }

        @Test
        void 진행중인_모임은_종료_시각에_종료된다() {
            Gathering gathering = gathering(START, GatheringStatus.CLOSED);
            run(START);

            run(END.minusSeconds(1));
            assertThat(statusOf(gathering)).isEqualTo(GatheringStatus.IN_PROGRESS);

            run(END);
            assertThat(statusOf(gathering)).isEqualTo(GatheringStatus.FINISHED);
        }

        @Test
        void 취소된_모임은_건드리지_않는다() {
            Gathering canceled = gathering(START, GatheringStatus.CANCELED);

            run(END);

            assertThat(statusOf(canceled)).isEqualTo(GatheringStatus.CANCELED);
            assertThat(attendanceOf(canceled)).isEmpty();
        }
    }

    @Nested
    class 노쇼 {

        @Test
        void 시작_10분이_지나도록_체크인하지_않은_확정_참가자는_노쇼가_되고_체크인한_사람은_그대로다() {
            Gathering gathering = gathering(START, GatheringStatus.CLOSED);
            Participation hostParticipation = participationOf(gathering, host);
            Participation aliceParticipation = confirmed(gathering, alice);
            participationRepository.save(Participation.apply(gathering, bob, null, NOW)); // 대기 신청은 대상이 아니다
            run(START);
            checkInRepository.save(CheckIn.builder().participation(hostParticipation)
                    .attendance(Attendance.ON_TIME).checkedInAt(START).distanceM(10).build());

            run(NO_SHOW_AT.minusSeconds(1));
            assertThat(attendanceOf(gathering)).containsOnly(entry(hostParticipation.getId(), Attendance.ON_TIME));

            run(NO_SHOW_AT);
            assertThat(attendanceOf(gathering)).containsOnly(
                    entry(hostParticipation.getId(), Attendance.ON_TIME),
                    entry(aliceParticipation.getId(), Attendance.NO_SHOW));
        }

        @Test
        void 호스트가_노쇼여도_모임은_진행되고_종료된다() {
            Gathering gathering = gathering(START, GatheringStatus.CLOSED);
            Participation hostParticipation = participationOf(gathering, host);

            run(START);
            run(NO_SHOW_AT);
            assertThat(statusOf(gathering)).isEqualTo(GatheringStatus.IN_PROGRESS);
            assertThat(attendanceOf(gathering)).containsOnly(entry(hostParticipation.getId(), Attendance.NO_SHOW));

            run(END);
            assertThat(statusOf(gathering)).isEqualTo(GatheringStatus.FINISHED);
        }

        @Test
        void 여러_시점이_한꺼번에_지나도_한_번에_종료와_노쇼까지_처리된다() {
            Gathering gathering = gathering(START, GatheringStatus.CLOSED);
            Participation hostParticipation = participationOf(gathering, host);
            Participation aliceParticipation = confirmed(gathering, alice);

            run(END.plus(Duration.ofHours(1)));

            assertThat(statusOf(gathering)).isEqualTo(GatheringStatus.FINISHED);
            assertThat(attendanceOf(gathering)).containsOnly(
                    entry(hostParticipation.getId(), Attendance.NO_SHOW),
                    entry(aliceParticipation.getId(), Attendance.NO_SHOW));
        }

        @Test
        void 다시_돌려도_결과가_같다() {
            Gathering gathering = gathering(START, GatheringStatus.CLOSED);
            Participation aliceParticipation = confirmed(gathering, alice);

            run(NO_SHOW_AT);
            run(NO_SHOW_AT);

            assertThat(statusOf(gathering)).isEqualTo(GatheringStatus.IN_PROGRESS);
            assertThat(attendanceOf(gathering)).hasSize(2)
                    .containsEntry(aliceParticipation.getId(), Attendance.NO_SHOW);
        }
    }

    // ───────── helpers ─────────

    private void run(Instant now) {
        entityManager.flush();
        scheduler.runAt(now);
        entityManager.flush();
        entityManager.clear();
    }

    private User user(String name) {
        return userRepository.save(User.signUpWithEmail(name + "@example.com", "hash", name, name, Gender.MALE));
    }

    /** 원하는 상태로 직접 저장한다. 호스트 확정 레코드도 만든다. */
    private Gathering gathering(Instant startAt, GatheringStatus status) {
        Place place = placeRepository.save(Place.pinned("버디짐", null, "서울 강남구 역삼동 1", "1168010100",
                new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(127.0, 37.5))));
        Gathering gathering = gatheringRepository.save(Gathering.builder()
                .host(host).place(place).title("같이 운동해요")
                .startAt(startAt).endAt(startAt.plus(Duration.ofMinutes(60)))
                .capacity((short) 3).genderRule(GenderRule.ANY).status(status)
                .build());
        participationRepository.save(Participation.hostOf(gathering, NOW));
        return gathering;
    }

    private Participation confirmed(Gathering gathering, User user) {
        return participationRepository.save(Participation.builder()
                .gathering(gathering).user(user).status(ParticipationStatus.CONFIRMED)
                .appliedAt(NOW).decidedAt(NOW).build());
    }

    private Participation participationOf(Gathering gathering, User user) {
        return participationRepository.findActive(gathering.getId(), user.getId()).orElseThrow();
    }

    private GatheringStatus statusOf(Gathering gathering) {
        return gatheringRepository.findById(gathering.getId()).orElseThrow().getStatus();
    }

    /** 참여 ID → 출석 판정 */
    private Map<Long, Attendance> attendanceOf(Gathering gathering) {
        return checkInRepository.findAll().stream()
                .filter(c -> c.getParticipation().getGathering().getId().equals(gathering.getId()))
                .collect(Collectors.toMap(CheckIn::getId, CheckIn::getAttendance));
    }
}
