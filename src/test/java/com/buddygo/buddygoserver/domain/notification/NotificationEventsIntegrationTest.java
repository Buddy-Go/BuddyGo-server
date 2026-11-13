package com.buddygo.buddygoserver.domain.notification;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.buddy.entity.Buddy;
import com.buddygo.buddygoserver.domain.buddy.repository.BuddyRepository;
import com.buddygo.buddygoserver.domain.checkin.entity.CheckIn;
import com.buddygo.buddygoserver.domain.checkin.enums.Attendance;
import com.buddygo.buddygoserver.domain.checkin.repository.CheckInRepository;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.entity.Place;
import com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus;
import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.PlaceRepository;
import com.buddygo.buddygoserver.domain.gathering.scheduler.GatheringStatusScheduler;
import com.buddygo.buddygoserver.domain.notification.entity.Notification;
import com.buddygo.buddygoserver.domain.notification.enums.NotificationType;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 각 이벤트가 규칙 12장의 받는 사람에게 알림을 남기는지. 모임은 NOW + 3시간에 시작하고(데드라인 NOW + 1시간 30분),
 * 정원은 호스트 포함 3명이다. 처음에는 모집중이고 alice 가 확정, carol 이 대기다.
 */
@Transactional
class NotificationEventsIntegrationTest extends IntegrationTest {

    static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");
    static final Instant START = NOW.plus(Duration.ofHours(3));
    static final Instant END = START.plus(Duration.ofHours(1));
    static final Instant AFTER_DEADLINE = START.minus(Duration.ofHours(1));
    static final String TITLE = "같이 운동해요";

    @Override
    protected Instant fixedNow() {
        return NOW;
    }

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired GatheringRepository gatheringRepository;
    @Autowired ParticipationRepository participationRepository;
    @Autowired CheckInRepository checkInRepository;
    @Autowired BuddyRepository buddyRepository;
    @Autowired GatheringStatusScheduler scheduler;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager entityManager;

    User host;
    User alice;
    User bob;
    User carol;
    private final List<Long> userIds = new ArrayList<>();
    Place place;
    Gathering gathering;
    Participation carolApplication;

    @BeforeEach
    void setUp() {
        host = user("nt-host", "호스트");
        alice = user("nt-alice", "앨리스");
        bob = user("nt-bob", "밥");
        carol = user("nt-carol", "캐럴");
        place = placeRepository.save(Place.pinned("버디짐", null, "서울 강남구 역삼동 1", "1168010100",
                new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(127.0, 37.5))));
        gathering = gathering(TITLE, GatheringStatus.RECRUITING, START);
        confirmed(gathering, alice);
        carolApplication = pending(gathering, carol);
        flushAndClear();
    }

    @Nested
    class 신청 {

        @Test
        void 신청하면_호스트와_신청자를_팔로우하는_버디가_받는다() throws Exception {
            User dave = user("nt-dave", "데이브");
            User eve = user("nt-eve", "이브");
            buddyRepository.save(Buddy.of(dave, bob));
            Buddy muted = Buddy.of(eve, bob);
            muted.changeNotification(null, false);
            buddyRepository.save(muted);
            // 호스트도 bob 을 팔로우하지만 '신청 받음'을 따로 받으므로 버디 알림은 받지 않는다
            buddyRepository.save(Buddy.of(host, bob));
            flushAndClear();

            perform(bob, post("/api/gatherings/{id}/applications", gathering.getId())).andExpect(status().isCreated());

            assertSent(NotificationType.APPLICATION_RECEIVED, Set.of(host), bob, gathering.getId());
            assertThat(messageOf(NotificationType.APPLICATION_RECEIVED)).contains("nt-bob", TITLE);
            assertSent(NotificationType.BUDDY_GATHERING_APPLIED, Set.of(dave), bob, gathering.getId());
        }

        @Test
        void 수락으로_정원이_차면_수락_마감_자동거절을_보낸다() throws Exception {
            Participation bobApplication = pending(gathering, bob);
            flushAndClear();

            perform(host, post("/api/gatherings/{id}/applications/{aid}/accept", gathering.getId(), bobApplication.getId()))
                    .andExpect(status().isOk());

            assertSent(NotificationType.APPLICATION_ACCEPTED, Set.of(bob), host, gathering.getId());
            // 마감은 시스템 이벤트라 호스트도 받는다
            assertSent(NotificationType.GATHERING_CLOSED, Set.of(host, alice, bob), null, gathering.getId());
            assertSent(NotificationType.APPLICATION_REJECTED, Set.of(carol), null, gathering.getId());
        }

        @Test
        void 호스트가_거절하면_신청자가_받는다() throws Exception {
            perform(host, post("/api/gatherings/{id}/applications/{aid}/reject", gathering.getId(), carolApplication.getId()))
                    .andExpect(status().isOk());

            assertSent(NotificationType.APPLICATION_REJECTED, Set.of(carol), host, gathering.getId());
        }
    }

    @Nested
    class 나가기 {

        @Test
        void 모집중인_모임에서_나가면_보내지_않는다() throws Exception {
            perform(alice, post("/api/gatherings/{id}/leave", gathering.getId())).andExpect(status().isOk());

            assertThat(rows()).isEmpty();
        }

        @Test
        void 마감된_모임에서_데드라인_전에_나가면_호스트와_남은_참가자가_받는다() throws Exception {
            closeWith(bob);

            perform(alice, post("/api/gatherings/{id}/leave", gathering.getId())).andExpect(status().isOk());

            assertSent(NotificationType.MEMBER_LEFT_REOPENED, Set.of(host, bob), alice, gathering.getId());
        }

        @Test
        void 데드라인_후에_나가면_취소_알림을_받는다() throws Exception {
            closeWith(bob);
            mutableClock.fix(AFTER_DEADLINE);

            perform(alice, post("/api/gatherings/{id}/leave", gathering.getId())).andExpect(status().isOk());

            assertSent(NotificationType.GATHERING_CANCELED_MEMBER_LEFT, Set.of(host, bob), alice, gathering.getId());
        }
    }

    @Nested
    class 취소_삭제 {

        @Test
        void 호스트_취소는_확정_참가자에게_사유와_함께_대기자에게는_자동거절을_보낸다() throws Exception {
            perform(host, post("/api/gatherings/{id}/cancel", gathering.getId())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"갑자기 출장이 잡혔어요\"}"))
                    .andExpect(status().isOk());

            assertSent(NotificationType.GATHERING_CANCELED_BY_HOST, Set.of(alice), host, gathering.getId());
            assertThat(messageOf(NotificationType.GATHERING_CANCELED_BY_HOST)).contains(TITLE, "갑자기 출장이 잡혔어요");
            assertSent(NotificationType.APPLICATION_REJECTED, Set.of(carol), null, gathering.getId());
        }

        @Test
        void 최대_길이_제목과_사유도_실패하지_않고_255자_안에_저장된다() throws Exception {
            Gathering longTitle = gathering("가".repeat(50), GatheringStatus.RECRUITING, START);
            confirmed(longTitle, alice);
            flushAndClear();

            perform(host, post("/api/gatherings/{id}/cancel", longTitle.getId())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"%s\"}".formatted("나".repeat(100))))
                    .andExpect(status().isOk());

            String message = messageOf(NotificationType.GATHERING_CANCELED_BY_HOST);
            assertThat(message).contains("가".repeat(50), "나".repeat(100));
            assertThat(message.length()).isLessThanOrEqualTo(Notification.MESSAGE_MAX);
        }

        @Test
        void 삭제하면_대기자에게_제목을_담은_자동거절이_남고_모임_연결은_끊긴다() throws Exception {
            Gathering empty = gathering("삭제될 모임", GatheringStatus.RECRUITING, START);
            pending(empty, bob);
            flushAndClear();

            perform(host, delete("/api/gatherings/{id}", empty.getId())).andExpect(status().isOk());

            List<Map<String, Object>> rejected = rows(NotificationType.APPLICATION_REJECTED);
            assertThat(rejected).hasSize(1);
            assertThat(rejected.getFirst().get("receiver_id")).isEqualTo(bob.getId());
            assertThat(rejected.getFirst().get("gathering_id")).isNull();
            assertThat((String) rejected.getFirst().get("message")).contains("삭제될 모임");
        }

        @Test
        void 정원_미달_자동_취소는_호스트와_확정자_그리고_대기자에게_보낸다() {
            scheduler.runAt(AFTER_DEADLINE);
            flushAndClear();

            assertSent(NotificationType.GATHERING_CANCELED_UNDER_CAPACITY, Set.of(host, alice), null, gathering.getId());
            assertSent(NotificationType.APPLICATION_REJECTED, Set.of(carol), null, gathering.getId());
        }

        @Test
        void 긴_문구는_255자로_잘린다() {
            String message = Notification.fit("가".repeat(300));
            assertThat(message.codePointCount(0, message.length())).isEqualTo(Notification.MESSAGE_MAX);
            assertThat(message).endsWith("…");
        }
    }

    @Nested
    class 진행 {

        @BeforeEach
        void closed() {
            closeWith(bob);
        }

        @Test
        void 체크인_열림은_시작_10분_전에_확정자_전원에게_한_번만_보낸다() {
            Instant openAt = START.minus(Duration.ofMinutes(10));
            scheduler.runAt(openAt.minus(Duration.ofMinutes(10)));
            assertThat(rows(NotificationType.CHECK_IN_OPENED)).isEmpty();

            scheduler.runAt(openAt);
            scheduler.runAt(openAt);
            scheduler.runAt(openAt.plus(Duration.ofMinutes(5)));
            flushAndClear();

            assertSent(NotificationType.CHECK_IN_OPENED, Set.of(host, alice, bob), null, gathering.getId());
        }

        @Test
        void 시작하면_아직_체크인하지_않은_사람만_받는다() {
            checkIn(alice, Attendance.ON_TIME);
            flushAndClear();

            scheduler.runAt(START);
            flushAndClear();

            assertSent(NotificationType.GATHERING_STARTED, Set.of(host, bob), null, gathering.getId());
            // 시작한 뒤에는 체크인 열림을 보내지 않는다
            assertThat(rows(NotificationType.CHECK_IN_OPENED)).isEmpty();
        }

        @Test
        void 노쇼_판정이_지난_따라잡기에서는_운동_시작을_보내지_않는다() {
            scheduler.runAt(START.plus(Duration.ofMinutes(10)));
            flushAndClear();

            assertThat(rows(NotificationType.GATHERING_STARTED)).isEmpty();
        }

        @Test
        void 종료하면_체크인한_사람이_평가_알림을_받는다() {
            checkIn(alice, Attendance.ON_TIME);
            checkIn(bob, Attendance.LATE);
            flushAndClear();

            scheduler.runAt(START);
            scheduler.runAt(END);
            flushAndClear();

            assertSent(NotificationType.GATHERING_FINISHED, Set.of(alice, bob), null, gathering.getId());
        }
    }

    @Nested
    class 버디 {

        @Test
        void 모임을_열면_호스트를_팔로우하는_버디가_받는다() throws Exception {
            User dave = user("nt-dave", "데이브");
            User eve = user("nt-eve", "이브");
            buddyRepository.save(Buddy.of(dave, host));
            Buddy muted = Buddy.of(eve, host);
            muted.changeNotification(false, null);
            buddyRepository.save(muted);
            flushAndClear();

            String json = perform(host, post("/api/gatherings").contentType(MediaType.APPLICATION_JSON).content("""
                    {"title": "새 모임", "startAt": "2026-11-10T03:00:00Z", "durationMinutes": 90, "capacity": 3,
                     "genderRule": "ANY",
                     "place": {"kakaoPlaceId": "777", "name": "버디짐", "jibunAddress": "서울 강남구 역삼동 123-45",
                               "regionCode": "1168010100", "latitude": 37.5009, "longitude": 127.0364},
                     "gym": {"parts": ["CHEST"], "showMyRecord": false}}"""))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            long createdId = ((Number) JsonPath.read(json, "$.data.gatheringId")).longValue();

            assertSent(NotificationType.BUDDY_GATHERING_CREATED, Set.of(dave), host, createdId);
            assertThat(messageOf(NotificationType.BUDDY_GATHERING_CREATED)).contains("nt-host", "새 모임");
        }

        @Test
        void 버디_신청을_받으면_알림이_오고_수락하면_신청한_사람이_받는다() throws Exception {
            workedOutTogether(alice, bob);

            String json = perform(alice, post("/api/buddy-requests").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"receiverId\": %d}".formatted(bob.getId())))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            long requestId = ((Number) JsonPath.read(json, "$.data.requestId")).longValue();

            List<Map<String, Object>> received = rows(NotificationType.BUDDY_REQUEST_RECEIVED);
            assertThat(received).hasSize(1);
            assertThat(received.getFirst().get("receiver_id")).isEqualTo(bob.getId());
            assertThat(received.getFirst().get("actor_id")).isEqualTo(alice.getId());
            assertThat(received.getFirst().get("buddy_request_id")).isEqualTo(requestId);

            perform(bob, post("/api/buddy-requests/{id}/accept", requestId)).andExpect(status().isOk());

            assertSent(NotificationType.BUDDY_REQUEST_ACCEPTED, Set.of(alice), bob, null);
            // 신청 행이 지워져 받은 알림의 연결은 끊긴다
            assertThat(rows(NotificationType.BUDDY_REQUEST_RECEIVED).getFirst().get("buddy_request_id")).isNull();
        }

        @Test
        void 맞신청으로_자동_수락되면_먼저_신청한_사람이_받는다() throws Exception {
            workedOutTogether(alice, bob);
            perform(alice, post("/api/buddy-requests").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"receiverId\": %d}".formatted(bob.getId()))).andExpect(status().isCreated());

            perform(bob, post("/api/buddy-requests").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"receiverId\": %d}".formatted(alice.getId()))).andExpect(status().isCreated());

            assertSent(NotificationType.BUDDY_REQUEST_ACCEPTED, Set.of(alice), bob, null);
        }
    }

    // ───────── helpers ─────────

    private void assertSent(NotificationType type, Set<User> receivers, User actor, Long gatheringId) {
        flushAndClear();
        List<Map<String, Object>> rows = rows(type);
        Set<Long> receiverIds = new HashSet<>();
        rows.forEach(r -> receiverIds.add(((Number) r.get("receiver_id")).longValue()));
        assertThat(rows).as("%s 는 받는 사람마다 한 건", type).hasSize(receivers.size());
        assertThat(receiverIds).as("%s 받는 사람", type)
                .isEqualTo(receivers.stream().map(User::getId).collect(java.util.stream.Collectors.toSet()));
        rows.forEach(r -> {
            assertThat(r.get("actor_id")).as("%s actor", type).isEqualTo(actor == null ? null : actor.getId());
            assertThat(r.get("gathering_id") == null ? null : ((Number) r.get("gathering_id")).longValue())
                    .as("%s gathering", type).isEqualTo(gatheringId);
        });
    }

    /** 이 테스트가 만든 사람들이 받은 알림만. 공유 로컬 DB 에 남아 있는 다른 알림은 세지 않는다. */
    private List<Map<String, Object>> rows() {
        flushAndClear();
        return jdbcTemplate.queryForList("SELECT * FROM notifications WHERE receiver_id IN (" + receiverIds() + ") ORDER BY id");
    }

    private List<Map<String, Object>> rows(NotificationType type) {
        flushAndClear();
        return jdbcTemplate.queryForList("SELECT * FROM notifications WHERE type = ? AND receiver_id IN ("
                + receiverIds() + ") ORDER BY id", type.name());
    }

    private String receiverIds() {
        return userIds.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    private String messageOf(NotificationType type) {
        return (String) rows(type).getFirst().get("message");
    }

    private ResultActions perform(User user, MockHttpServletRequestBuilder request) throws Exception {
        flushAndClear();
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + jwtTokenProvider.issueAccessToken(user.getId()).value()));
    }

    /** bob 을 확정해 정원을 채우고 대기자는 거절된 마감 상태로 만든다(알림은 남기지 않는다). */
    private void closeWith(User member) {
        confirmed(gathering, member);
        jdbcTemplate.update("UPDATE participations SET status = 'REJECTED' WHERE id = ?", carolApplication.getId());
        jdbcTemplate.update("UPDATE gatherings SET status = 'CLOSED' WHERE id = ?", gathering.getId());
        flushAndClear();
    }

    private void checkIn(User user, Attendance attendance) {
        Participation participation = participationRepository.findActive(gathering.getId(), user.getId()).orElseThrow();
        checkInRepository.save(CheckIn.attended(participation, attendance, START, 10));
    }

    /** 끝난 다른 모임에서 둘 다 체크인한 기록을 만든다(버디 신청 조건). */
    private void workedOutTogether(User a, User b) {
        Gathering past = gathering("지난 모임", GatheringStatus.FINISHED, NOW.minus(Duration.ofDays(2)));
        Participation pa = confirmed(past, a);
        Participation pb = confirmed(past, b);
        checkInRepository.save(CheckIn.attended(pa, Attendance.ON_TIME, past.getStartAt(), 5));
        checkInRepository.save(CheckIn.attended(pb, Attendance.ON_TIME, past.getStartAt(), 5));
        flushAndClear();
    }

    private Gathering gathering(String title, GatheringStatus status, Instant startAt) {
        Gathering saved = gatheringRepository.save(Gathering.builder()
                .host(host).place(place).title(title)
                .startAt(startAt).endAt(startAt.plus(Duration.ofHours(1)))
                .capacity((short) 3).genderRule(GenderRule.ANY).status(status)
                .build());
        participationRepository.save(Participation.hostOf(saved, NOW.minus(Duration.ofDays(3))));
        return saved;
    }

    private Participation confirmed(Gathering g, User user) {
        return participationRepository.save(Participation.builder()
                .gathering(g).user(user).status(ParticipationStatus.CONFIRMED)
                .appliedAt(NOW.minus(Duration.ofDays(1))).decidedAt(NOW.minus(Duration.ofDays(1))).build());
    }

    private Participation pending(Gathering g, User user) {
        return participationRepository.save(Participation.builder()
                .gathering(g).user(user).status(ParticipationStatus.PENDING)
                .appliedAt(NOW.minus(Duration.ofHours(1))).build());
    }

    private User user(String key, String nickname) {
        User user = userRepository.save(User.signUpWithEmail(key + "@example.com", "hash", nickname, key, Gender.FEMALE));
        userIds.add(user.getId());
        return user;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
