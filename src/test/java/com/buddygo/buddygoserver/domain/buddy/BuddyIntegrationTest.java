package com.buddygo.buddygoserver.domain.buddy;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.buddy.entity.Buddy;
import com.buddygo.buddygoserver.domain.buddy.entity.BuddyId;
import com.buddygo.buddygoserver.domain.buddy.entity.BuddyRequest;
import com.buddygo.buddygoserver.domain.buddy.enums.BuddyRequestStatus;
import com.buddygo.buddygoserver.domain.buddy.repository.BuddyRepository;
import com.buddygo.buddygoserver.domain.buddy.repository.BuddyRequestRepository;
import com.buddygo.buddygoserver.domain.checkin.entity.CheckIn;
import com.buddygo.buddygoserver.domain.checkin.enums.Attendance;
import com.buddygo.buddygoserver.domain.checkin.repository.CheckInRepository;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.entity.Place;
import com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus;
import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.PlaceRepository;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.enums.Gender;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.security.jwt.JwtTokenProvider;
import com.buddygo.buddygoserver.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 로컬 PostgreSQL(docker-compose.local.yml)이 떠 있어야 한다. DB 는 테스트마다 롤백되고 현재 시각은 NOW 로 고정된다. */
@Transactional
class BuddyIntegrationTest extends IntegrationTest {

    static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

    @Override
    protected Instant fixedNow() {
        return NOW;
    }
    /** 지난 모임의 시작 시각 */
    static final Instant PAST = NOW.minus(Duration.ofDays(3));

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired GatheringRepository gatheringRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired ParticipationRepository participationRepository;
    @Autowired CheckInRepository checkInRepository;
    @Autowired BuddyRepository buddyRepository;
    @Autowired BuddyRequestRepository buddyRequestRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired EntityManager entityManager;

    User alice;
    User bob;
    User carol;

    @BeforeEach
    void setUp() {
        alice = user("alice");
        bob = user("bob");
        carol = user("carol");
    }

    @Nested
    class 신청 {

        @Test
        void 함께_운동한_사람에게_신청하면_대기_상태가_된다() throws Exception {
            workedOut(PAST, alice, bob);

            send(alice, bob).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.requestId").isNumber());
        }

        @Test
        void 함께_운동하지_않았으면_B007() throws Exception {
            send(alice, bob).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("B007"));
        }

        @Test
        void 같은_모임이어도_상대가_노쇼면_함께_운동한_사람이_아니다() throws Exception {
            Gathering gathering = gathering(PAST, GatheringStatus.FINISHED, alice);
            checkIn(participationOf(gathering, alice), Attendance.ON_TIME);
            checkIn(confirmed(gathering, bob), Attendance.NO_SHOW);

            send(alice, bob).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("B007"));
        }

        @Test
        void 체크인했어도_취소된_모임이면_함께_운동한_것이_아니다() throws Exception {
            Gathering gathering = gathering(PAST, GatheringStatus.CANCELED, alice);
            checkIn(participationOf(gathering, alice), Attendance.ON_TIME);
            checkIn(confirmed(gathering, bob), Attendance.LATE);

            send(alice, bob).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("B007"));
        }

        @Test
        void 자기_자신은_B006_없는_사용자는_U001() throws Exception {
            send(alice, alice).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("B006"));
            mockMvc.perform(post("/api/buddy-requests").header(HttpHeaders.AUTHORIZATION, bearer(alice))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"receiverId\": 999999999}"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("U001"));
        }

        @Test
        void 이미_보낸_신청이_대기_중이면_B003_이미_버디면_B002() throws Exception {
            workedOut(PAST, alice, bob);
            send(alice, bob).andExpect(status().isCreated());
            send(alice, bob).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("B003"));

            accept(bob, requestId(alice, bob)).andExpect(status().isOk());
            send(alice, bob).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("B002"));
        }

        @Test
        void 상대가_보낸_신청이_대기_중이면_맞신청으로_바로_버디가_된다() throws Exception {
            workedOut(PAST, alice, bob);
            long aliceRequestId = idOf(send(alice, bob));

            send(bob, alice).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.data.requestId").value(aliceRequestId));

            flushAndClear();
            assertThat(buddyRepository.areBuddies(alice.getId(), bob.getId())).isTrue();
            assertThat(buddyRepository.areBuddies(bob.getId(), alice.getId())).isTrue();
            assertThat(buddyRequestRepository.findAll()).filteredOn(r -> isBetween(r, alice, bob)).isEmpty();
        }

        @Test
        void 거절은_기록이_남고_다시_신청할_수_있다() throws Exception {
            workedOut(PAST, alice, bob);
            long requestId = idOf(send(alice, bob));
            reject(bob, requestId).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("REJECTED"));
            accept(bob, requestId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("B004"));
            flushAndClear();
            assertThat(buddyRequestRepository.findById(requestId)).get()
                    .extracting(BuddyRequest::getStatus).isEqualTo(BuddyRequestStatus.REJECTED);

            send(alice, bob).andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("PENDING"));
        }
    }

    @Nested
    class 취소_수락_거절 {

        @Test
        void 보낸_사람만_취소할_수_있다() throws Exception {
            workedOut(PAST, alice, bob);
            long requestId = idOf(send(alice, bob));

            cancel(bob, requestId).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("B001"));
            cancel(carol, requestId).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("B001"));
            cancel(alice, requestId).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELED"));
            cancel(alice, requestId).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("B001"));

            flushAndClear();
            assertThat(buddyRequestRepository.findById(requestId)).isEmpty();
            send(alice, bob).andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("PENDING"));
        }

        @Test
        void 받은_사람만_수락할_수_있고_수락하면_서로_버디가_된다() throws Exception {
            workedOut(PAST, alice, bob);
            long requestId = idOf(send(alice, bob));

            accept(alice, requestId).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("B001"));
            accept(bob, requestId).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACCEPTED"));
            accept(bob, requestId).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("B001"));
            reject(bob, requestId).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("B001"));

            flushAndClear();
            assertThat(buddyRequestRepository.findById(requestId)).isEmpty();
            assertThat(buddyRepository.areBuddies(alice.getId(), bob.getId())).isTrue();
            assertThat(buddyRepository.areBuddies(bob.getId(), alice.getId())).isTrue();
        }

        @Test
        void 받은_신청_목록에는_대기_중인_것만_보낸_사람_정보와_참여_횟수가_나온다() throws Exception {
            workedOut(PAST, alice, carol);
            // 참여 횟수는 남이 연 모임만 센다: bob 이 연 모임은 빼고 2회
            workedOut(PAST.minus(Duration.ofDays(1)), bob, carol);
            workedOut(PAST.minus(Duration.ofDays(2)), carol, bob);
            workedOut(PAST.minus(Duration.ofDays(3)), alice, bob);
            send(alice, carol);
            send(bob, carol);
            reject(carol, requestId(alice, carol));

            received(carol).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].userId").value(bob.getId()))
                    .andExpect(jsonPath("$.data[0].nickname").value("bob"))
                    .andExpect(jsonPath("$.data[0].mannerTemperature").value(36.5))
                    .andExpect(jsonPath("$.data[0].participationCount").value(2));
            received(bob).andExpect(jsonPath("$.data", hasSize(0)));
        }
    }

    @Nested
    class 함께_운동한_사람 {

        @Test
        void 함께한_횟수와_마지막_운동일_관계가_최근_순으로_나온다() throws Exception {
            workedOut(PAST, alice, bob);
            workedOut(PAST.minus(Duration.ofDays(7)), alice, bob);
            workedOut(PAST.minus(Duration.ofDays(1)), carol, alice);
            send(alice, bob);

            partners(alice, null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[*].nickname", contains("bob", "carol")))
                    .andExpect(jsonPath("$.data[0].workoutCount").value(2))
                    .andExpect(jsonPath("$.data[0].lastWorkoutAt").value(PAST.toString()))
                    .andExpect(jsonPath("$.data[0].relation").value("REQUEST_SENT"))
                    .andExpect(jsonPath("$.data[0].requestId").isNumber())
                    .andExpect(jsonPath("$.data[1].workoutCount").value(1))
                    .andExpect(jsonPath("$.data[1].relation").value("NONE"))
                    .andExpect(jsonPath("$.data[1].requestId").doesNotExist());
            partners(bob, null).andExpect(jsonPath("$.data[0].relation").value("REQUEST_RECEIVED"));

            accept(bob, requestId(alice, bob));
            partners(alice, null).andExpect(jsonPath("$.data[0].relation").value("BUDDY"));
        }

        @Test
        void 닉네임_일부로_찾고_노쇼한_사람은_빠진다() throws Exception {
            workedOut(PAST, alice, bob, carol);
            Gathering noShow = gathering(PAST.plus(Duration.ofDays(1)), GatheringStatus.FINISHED, alice);
            checkIn(participationOf(noShow, alice), Attendance.ON_TIME);
            User dave = user("dave");
            checkIn(confirmed(noShow, dave), Attendance.NO_SHOW);

            partners(alice, "CAR").andExpect(jsonPath("$.data[*].nickname", contains("carol")));
            partners(alice, "%").andExpect(jsonPath("$.data", hasSize(0)));
            partners(alice, " ").andExpect(jsonPath("$.data[*].nickname", contains("bob", "carol")));
        }
    }

    @Nested
    class 버디_목록과_활동 {

        @Test
        void 지금_운동_중인_버디가_먼저_그다음_가까운_예정_모임_순이고_예정이_없으면_activity_null() throws Exception {
            User dave = user("dave");
            buddies(alice, bob);
            buddies(alice, carol);
            buddies(alice, dave);
            // bob: 다음 주 모임 개설
            Gathering bobHosts = gathering(NOW.plus(Duration.ofDays(7)), GatheringStatus.RECRUITING, bob);
            // carol: 지금 진행 중인 모임 확정 참가(호스트는 dave 가 아닌 새 사용자)
            User host = user("host");
            Gathering running = gathering(NOW.minus(Duration.ofMinutes(20)), GatheringStatus.IN_PROGRESS, host);
            confirmed(running, carol);
            // carol 은 내일 모임에도 신청 대기 중이지만 대표 활동은 진행 중인 것
            participationRepository.save(Participation.apply(
                    gathering(NOW.plus(Duration.ofDays(1)), GatheringStatus.RECRUITING, host), carol, null, NOW));

            buddyList(alice).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[*].nickname", contains("carol", "bob", "dave")))
                    .andExpect(jsonPath("$.data[0].activity.gatheringId").value(running.getId()))
                    .andExpect(jsonPath("$.data[0].activity.inProgress").value(true))
                    .andExpect(jsonPath("$.data[1].activity.gatheringId").value(bobHosts.getId()))
                    .andExpect(jsonPath("$.data[1].activity.placeName").value("버디짐"))
                    .andExpect(jsonPath("$.data[1].activity.startAt").value(bobHosts.getStartAt().toString()))
                    .andExpect(jsonPath("$.data[1].activity.inProgress").value(false))
                    .andExpect(jsonPath("$.data[1].activity.title").doesNotExist())
                    .andExpect(jsonPath("$.data[1].activity.role").doesNotExist())
                    .andExpect(jsonPath("$.data[2].activity").doesNotExist())
                    .andExpect(jsonPath("$.data[0].notifyGatheringCreated").value(true));
        }

        @Test
        void 버디의_활동은_버디만_볼_수_있고_끝났거나_빠진_모임은_나오지_않는다() throws Exception {
            buddies(alice, bob);
            User host = user("host");
            Gathering hosted = gathering(NOW.plus(Duration.ofDays(2)), GatheringStatus.RECRUITING, bob);
            Gathering applied = gathering(NOW.plus(Duration.ofDays(1)), GatheringStatus.RECRUITING, host);
            participationRepository.save(Participation.apply(applied, bob, null, NOW));
            // 빠지는 것: 끝난 모임, 취소된 모임, 데드라인 지난 모집중 모임, 나간 모임, 노쇼한 진행중 모임
            confirmed(gathering(PAST, GatheringStatus.FINISHED, host), bob);
            confirmed(gathering(NOW.plus(Duration.ofDays(3)), GatheringStatus.CANCELED, host), bob);
            confirmed(gathering(NOW.plus(Duration.ofMinutes(60)), GatheringStatus.RECRUITING, host), bob);
            Participation left = confirmed(gathering(NOW.plus(Duration.ofDays(4)), GatheringStatus.RECRUITING, host), bob);
            left.leave(NOW);
            Gathering running = gathering(NOW.minus(Duration.ofMinutes(20)), GatheringStatus.IN_PROGRESS, host);
            checkIn(confirmed(running, bob), Attendance.NO_SHOW);

            activities(alice, bob).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[*].gatheringId", contains(
                            applied.getId().intValue(), hosted.getId().intValue())))
                    .andExpect(jsonPath("$.data[0].title").value("같이 운동해요"))
                    .andExpect(jsonPath("$.data[0].placeName").value("버디짐"))
                    .andExpect(jsonPath("$.data[0].startAt").value(applied.getStartAt().toString()))
                    .andExpect(jsonPath("$.data[0].inProgress").value(false))
                    .andExpect(jsonPath("$.data[0].confirmedCount").value(1))
                    .andExpect(jsonPath("$.data[0].capacity").value(3))
                    .andExpect(jsonPath("$.data[0].role").doesNotExist());
            activities(carol, bob).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("B005"));
        }
    }

    @Nested
    class 끊기와_알림_설정 {

        @Test
        void 끊으면_두_방향_모두_사라지고_다시_신청할_수_있다() throws Exception {
            workedOut(PAST, alice, bob);
            accept(bob, idOf(send(alice, bob)));

            unbuddy(bob, alice).andExpect(status().isOk());
            flushAndClear();
            assertThat(buddyRepository.areBuddies(alice.getId(), bob.getId())).isFalse();
            assertThat(buddyRepository.areBuddies(bob.getId(), alice.getId())).isFalse();

            unbuddy(bob, alice).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("B008"));
            send(alice, bob).andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("PENDING"));
        }

        @Test
        void 알림_설정은_보낸_항목만_내_쪽만_바뀐다() throws Exception {
            buddies(alice, bob);

            mockMvc.perform(patch("/api/buddies/{userId}/notification", bob.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(alice))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"gatheringCreated\": false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(bob.getId()))
                    .andExpect(jsonPath("$.data.gatheringCreated").value(false))
                    .andExpect(jsonPath("$.data.gatheringApplied").value(true));

            flushAndClear();
            Buddy mine = buddyRepository.findById(new BuddyId(alice.getId(), bob.getId())).orElseThrow();
            Buddy theirs = buddyRepository.findById(new BuddyId(bob.getId(), alice.getId())).orElseThrow();
            assertThat(mine.isNotifyGatheringCreated()).isFalse();
            assertThat(theirs.isNotifyGatheringCreated()).isTrue();

            mockMvc.perform(patch("/api/buddies/{userId}/notification", carol.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(alice))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"gatheringApplied\": false}"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("B008"));
        }
    }

    // ───────── helpers ─────────

    private User user(String name) {
        return userRepository.save(User.signUpWithEmail(name + "@example.com", "hash", name, name, Gender.MALE));
    }

    /** 원하는 상태로 직접 저장한다. 호스트 확정 레코드도 만든다. */
    private Gathering gathering(Instant startAt, GatheringStatus status, User host) {
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

    /** 끝난 모임에서 모두 정상 체크인했다. 첫 사람이 호스트다. */
    private Gathering workedOut(Instant startAt, User host, User... members) {
        Gathering gathering = gathering(startAt, GatheringStatus.FINISHED, host);
        checkIn(participationOf(gathering, host), Attendance.ON_TIME);
        for (User member : members) {
            checkIn(confirmed(gathering, member), Attendance.ON_TIME);
        }
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

    private void checkIn(Participation participation, Attendance attendance) {
        checkInRepository.save(CheckIn.builder().participation(participation).attendance(attendance).build());
    }

    private void buddies(User a, User b) {
        buddyRepository.save(Buddy.of(a, b));
        buddyRepository.save(Buddy.of(b, a));
    }

    private ResultActions send(User sender, User receiver) throws Exception {
        return mockMvc.perform(post("/api/buddy-requests").header(HttpHeaders.AUTHORIZATION, bearer(sender))
                .contentType(MediaType.APPLICATION_JSON).content("{\"receiverId\": " + receiver.getId() + "}"));
    }

    private ResultActions cancel(User user, long requestId) throws Exception {
        return mockMvc.perform(delete("/api/buddy-requests/{id}", requestId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions accept(User user, long requestId) throws Exception {
        return mockMvc.perform(post("/api/buddy-requests/{id}/accept", requestId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions reject(User user, long requestId) throws Exception {
        return mockMvc.perform(post("/api/buddy-requests/{id}/reject", requestId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions received(User user) throws Exception {
        return mockMvc.perform(get("/api/buddy-requests/received").header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions partners(User user, String query) throws Exception {
        var request = get("/api/buddies/workout-partners").header(HttpHeaders.AUTHORIZATION, bearer(user));
        return mockMvc.perform(query == null ? request : request.param("q", query));
    }

    private ResultActions buddyList(User user) throws Exception {
        return mockMvc.perform(get("/api/buddies").header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions activities(User user, User target) throws Exception {
        return mockMvc.perform(get("/api/users/{userId}/activities", target.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions unbuddy(User user, User buddy) throws Exception {
        return mockMvc.perform(delete("/api/buddies/{userId}", buddy.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private long idOf(ResultActions result) throws Exception {
        String json = result.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.data.requestId")).longValue();
    }

    /** sender → receiver 로 대기 중인 신청 ID */
    private long requestId(User sender, User receiver) {
        return buddyRequestRepository.findPending(sender.getId(), receiver.getId()).orElseThrow().getId();
    }

    private boolean isBetween(BuddyRequest request, User a, User b) {
        Long sender = request.getSender().getId();
        Long receiver = request.getReceiver().getId();
        return (sender.equals(a.getId()) && receiver.equals(b.getId()))
                || (sender.equals(b.getId()) && receiver.equals(a.getId()));
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.issueAccessToken(user.getId()).value();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
