package com.buddygo.buddygoserver.domain.reschedule;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import com.buddygo.buddygoserver.domain.application.enums.RejectReason;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.entity.Place;
import com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus;
import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.PlaceRepository;
import com.buddygo.buddygoserver.domain.gathering.scheduler.GatheringStatusScheduler;
import com.buddygo.buddygoserver.domain.reschedule.scheduler.TimeProposalExpiryScheduler;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시간 변경 제안. 모임은 03:00(UTC)에 시작해 1시간 진행하고, 데드라인은 01:30, 체크인 열림은 02:50 이다.
 * 기본은 호스트·앨리스·밥이 확정된 마감 모임(정원 3)이다.
 */
@Transactional
class TimeProposalIntegrationTest extends IntegrationTest {

    static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");
    static final Instant START = Instant.parse("2026-11-10T03:00:00Z");

    @Override
    protected Instant fixedNow() {
        return NOW;
    }

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired GatheringRepository gatheringRepository;
    @Autowired ParticipationRepository participationRepository;
    @Autowired TimeProposalExpiryScheduler expiryScheduler;
    @Autowired GatheringStatusScheduler statusScheduler;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbcTemplate;

    User host;
    User alice;
    User bob;
    User carol;
    Gathering gathering;

    @BeforeEach
    void setUp() {
        host = user("tp-host", "호스트");
        alice = user("tp-alice", "앨리스");
        bob = user("tp-bob", "밥");
        carol = user("tp-carol", "캐럴");
        Place place = placeRepository.save(Place.pinned("버디짐", null, "서울 강남구 역삼동 1", "1168010100",
                new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(127.0, 37.5))));
        gathering = gatheringRepository.save(Gathering.builder()
                .host(host).place(place).title("같이 운동해요")
                .startAt(START).endAt(START.plus(Duration.ofHours(1)))
                .capacity((short) 3).genderRule(GenderRule.ANY).status(GatheringStatus.CLOSED)
                .build());
        participationRepository.save(Participation.hostOf(gathering, NOW.minus(Duration.ofDays(1))));
        participation(alice, ParticipationStatus.CONFIRMED);
        participation(bob, ParticipationStatus.CONFIRMED);
        flushAndClear();
    }

    @Nested
    class 제안 {

        @Test
        void 참가자가_제안하면_제안자는_수락한_것으로_치고_나머지_구성원에게_알린다() throws Exception {
            propose(alice, "2026-11-10T03:30:00Z")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.proposerId").value(alice.getId()))
                    .andExpect(jsonPath("$.data.fromStartAt").value("2026-11-10T03:00:00Z"))
                    .andExpect(jsonPath("$.data.toStartAt").value("2026-11-10T03:30:00Z"))
                    // 원래 체크인 열림(02:50)과 새 시작 - 30분(03:00) 중 이른 쪽
                    .andExpect(jsonPath("$.data.expiresAt").value("2026-11-10T02:50:00Z"))
                    .andExpect(jsonPath("$.data.votes[0].userId").value(host.getId()))
                    .andExpect(jsonPath("$.data.votes[0].host").value(true))
                    .andExpect(jsonPath("$.data.votes[*].decision").value(contains("PENDING", "ACCEPTED", "PENDING")));

            assertThat(receivers("TIME_CHANGE_PROPOSED")).containsExactlyInAnyOrder(host.getId(), bob.getId());
            assertThat(messages("TIME_CHANGE_PROPOSED")).allSatisfy(m -> assertThat(m).contains("tp-alice님이", "11월 10일 12:30"));
        }

        @Test
        void 호스트_외_확정_참가자가_없으면_TP003() throws Exception {
            setStatus(GatheringStatus.RECRUITING);
            leave(alice).andExpect(status().isOk());
            leave(bob).andExpect(status().isOk());

            propose(host, "2026-11-10T03:30:00Z")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("TP003"));
        }

        @Test
        void 구성원이_아니면_TP002() throws Exception {
            participation(carol, ParticipationStatus.PENDING);
            propose(carol, "2026-11-10T03:30:00Z")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("TP002"));
        }

        @Test
        void 진행_중인_제안이_있으면_TP004() throws Exception {
            propose(alice, "2026-11-10T03:30:00Z").andExpect(status().isCreated());
            propose(bob, "2026-11-10T02:40:00Z")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("TP004"));
        }

        @Test
        void 단위가_안_맞거나_같거나_1시간을_넘으면_TP005() throws Exception {
            for (String startAt : List.of("2026-11-10T03:05:00Z", "2026-11-10T03:00:00Z", "2026-11-10T04:10:00Z",
                    "2026-11-10T01:50:00Z")) {
                propose(alice, startAt)
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("TP005"));
            }
        }

        @Test
        void 마감_모임의_새_시작은_지금부터_30분_이후여야_한다() throws Exception {
            mutableClock.fix(Instant.parse("2026-11-10T02:10:00Z"));
            propose(alice, "2026-11-10T02:40:00Z")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TP005"));
            propose(alice, "2026-11-10T02:50:00Z").andExpect(status().isCreated());
        }

        @Test
        void 마감_모임은_체크인이_열리면_제안할_수_없다() throws Exception {
            mutableClock.fix(Instant.parse("2026-11-10T02:50:00Z"));
            propose(alice, "2026-11-10T03:30:00Z")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("TP006"));
        }

        @Test
        void 모집중_모임은_새_데드라인이_지나지_않는_시각만_고를_수_있다() throws Exception {
            setStatus(GatheringStatus.RECRUITING);
            mutableClock.fix(Instant.parse("2026-11-10T00:50:00Z"));
            // 새 데드라인 00:40 이 이미 지났다
            propose(alice, "2026-11-10T02:10:00Z")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TP005"));
            propose(alice, "2026-11-10T02:30:00Z")
                    .andExpect(status().isCreated())
                    // 원래 데드라인(01:30)과 새 데드라인(01:00) 중 이른 쪽
                    .andExpect(jsonPath("$.data.expiresAt").value("2026-11-10T01:00:00Z"));
        }

        @Test
        void 기한이_지났는데_남아_있던_제안은_새_제안을_막지_않고_만료된다() throws Exception {
            mutableClock.fix(Instant.parse("2026-11-10T01:40:00Z"));
            // 새 시작 02:20 - 30분 = 01:50 이 기한
            long first = proposalId(propose(alice, "2026-11-10T02:20:00Z").andExpect(status().isCreated()));
            mutableClock.fix(Instant.parse("2026-11-10T01:55:00Z"));

            propose(bob, "2026-11-10T03:20:00Z").andExpect(status().isCreated());

            assertThat(statusOf(first)).isEqualTo("EXPIRED");
        }
    }

    @Nested
    class 응답 {

        @Test
        void 모두_수락하면_시작_시각이_바뀌고_데드라인도_새_시각_기준이다() throws Exception {
            long id = proposalId(propose(alice, "2026-11-10T03:30:00Z"));

            accept(host, id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PENDING"));
            accept(bob, id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("APPROVED"))
                    .andExpect(jsonPath("$.data.resolvedAt").value("2026-11-10T00:00:00Z"));

            mockMvc.perform(get("/api/gatherings/{id}", gathering.getId()).header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                    .andExpect(jsonPath("$.data.startAt").value("2026-11-10T03:30:00Z"))
                    .andExpect(jsonPath("$.data.endAt").value("2026-11-10T04:30:00Z"))
                    .andExpect(jsonPath("$.data.deadlineAt").value("2026-11-10T02:00:00Z"));
            // 마지막으로 수락한 밥은 받지 않는다
            assertThat(receivers("TIME_CHANGE_APPROVED")).containsExactlyInAnyOrder(host.getId(), alice.getId());
        }

        @Test
        void 통과하면_데드라인을_새_시각으로_다시_계산해_그_전에_나가면_다시_모집한다() throws Exception {
            long id = proposalId(propose(alice, "2026-11-10T04:00:00Z"));
            accept(host, id);
            accept(bob, id).andExpect(jsonPath("$.data.status").value("APPROVED"));
            // 원래 데드라인(01:30)은 지났지만 새 데드라인(02:30)은 아직이다
            mutableClock.fix(Instant.parse("2026-11-10T02:00:00Z"));

            leave(bob).andExpect(status().isOk());

            assertThat(jdbcTemplate.queryForObject("select status from gatherings where id = ?", String.class,
                    gathering.getId())).isEqualTo("RECRUITING");
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from manner_temperature_logs where user_id = ? and reason = 'LEFT_BEFORE_DEADLINE'",
                    Long.class, bob.getId())).isEqualTo(1);
        }

        @Test
        void 모집중_모임에서_통과하면_대기_신청은_자동_거절된다() throws Exception {
            setStatus(GatheringStatus.RECRUITING);
            leave(bob).andExpect(status().isOk());
            Participation pending = participation(carol, ParticipationStatus.PENDING);
            long id = proposalId(propose(alice, "2026-11-10T04:00:00Z"));

            accept(host, id).andExpect(jsonPath("$.data.status").value("APPROVED"));
            flushAndClear();

            Participation rejected = participationRepository.findById(pending.getId()).orElseThrow();
            assertThat(rejected.getStatus()).isEqualTo(ParticipationStatus.REJECTED);
            assertThat(rejected.getRejectReason()).isEqualTo(RejectReason.SCHEDULE_CHANGED);
            assertThat(receivers("APPLICATION_REJECTED")).containsExactly(carol.getId());
            assertThat(gatheringRepository.findById(gathering.getId()).orElseThrow().getStartAt())
                    .isEqualTo(Instant.parse("2026-11-10T04:00:00Z"));
        }

        @Test
        void 한_명이_거절하면_바로_무산되고_시각은_그대로다() throws Exception {
            long id = proposalId(propose(alice, "2026-11-10T03:30:00Z"));

            reject(bob, id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("REJECTED"));
            accept(host, id)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("TP007"));

            assertThat(gatheringRepository.findById(gathering.getId()).orElseThrow().getStartAt()).isEqualTo(START);
            assertThat(receivers("TIME_CHANGE_REJECTED")).containsExactlyInAnyOrder(host.getId(), alice.getId());
        }

        @Test
        void 이미_응답했거나_구성원이_아니면_거절된다() throws Exception {
            long id = proposalId(propose(alice, "2026-11-10T03:30:00Z"));
            accept(alice, id)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("TP009"));
            accept(carol, id)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("TP002"));
        }

        @Test
        void 제안자만_철회할_수_있다() throws Exception {
            long id = proposalId(propose(alice, "2026-11-10T03:30:00Z"));
            withdraw(bob, id)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("TP010"));

            withdraw(alice, id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));
            assertThat(receivers("TIME_CHANGE_CANCELED")).containsExactlyInAnyOrder(host.getId(), bob.getId());
        }
    }

    @Nested
    class 만료 {

        @Test
        void 기한이_지나면_응답할_수_없고_만료_작업이_무산시키고_모두에게_알린다() throws Exception {
            long id = proposalId(propose(alice, "2026-11-10T03:30:00Z"));
            Instant expiresAt = Instant.parse("2026-11-10T02:50:00Z");
            mutableClock.fix(expiresAt);

            accept(host, id)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("TP008"));
            expiryScheduler.runAt(expiresAt);
            flushAndClear();

            assertThat(statusOf(id)).isEqualTo("EXPIRED");
            assertThat(receivers("TIME_CHANGE_CANCELED"))
                    .containsExactlyInAnyOrder(host.getId(), alice.getId(), bob.getId());
            // 다시 돌아도 한 번만 처리된다
            expiryScheduler.runAt(expiresAt.plus(Duration.ofMinutes(10)));
            assertThat(receivers("TIME_CHANGE_CANCELED")).hasSize(3);
        }

        @Test
        void 기한_전에는_만료_작업이_건드리지_않는다() throws Exception {
            long id = proposalId(propose(alice, "2026-11-10T03:30:00Z"));
            expiryScheduler.runAt(Instant.parse("2026-11-10T02:40:00Z"));
            flushAndClear();
            assertThat(statusOf(id)).isEqualTo("PENDING");
        }
    }

    @Nested
    class 명단_변경 {

        @Test
        void 진행_중에_새_참가자가_수락되면_무산되고_기존_구성원에게만_알린다() throws Exception {
            setStatus(GatheringStatus.RECRUITING);
            leave(bob).andExpect(status().isOk());
            long carolApplication = participation(carol, ParticipationStatus.PENDING).getId();
            long id = proposalId(propose(alice, "2026-11-10T03:30:00Z"));

            mockMvc.perform(post("/api/gatherings/{id}/applications/{applicationId}/accept", gathering.getId(), carolApplication)
                            .header(HttpHeaders.AUTHORIZATION, bearer(host)))
                    .andExpect(status().isOk());
            flushAndClear();

            assertThat(statusOf(id)).isEqualTo("CANCELED");
            // 수락한 호스트는 행동한 사람, 캐럴은 제안을 본 적이 없다
            assertThat(receivers("TIME_CHANGE_CANCELED")).containsExactly(alice.getId());
        }

        @Test
        void 데드라인_전에_구성원이_나가면_무산되고_남은_구성원에게_알린다() throws Exception {
            long id = proposalId(propose(alice, "2026-11-10T03:30:00Z"));
            leave(bob).andExpect(status().isOk());
            flushAndClear();

            assertThat(statusOf(id)).isEqualTo("CANCELED");
            assertThat(receivers("TIME_CHANGE_CANCELED")).containsExactlyInAnyOrder(host.getId(), alice.getId());
        }

        @Test
        void 정원_미달로_자동_취소되면_무산되고_제안_알림은_보내지_않는다() throws Exception {
            setStatus(GatheringStatus.RECRUITING);
            leave(bob).andExpect(status().isOk());
            long id = proposalId(propose(alice, "2026-11-10T03:30:00Z"));

            statusScheduler.runAt(Instant.parse("2026-11-10T01:30:00Z"));
            flushAndClear();

            assertThat(statusOf(id)).isEqualTo("CANCELED");
            assertThat(receivers("TIME_CHANGE_CANCELED")).isEmpty();
        }

        @Test
        void 모임이_취소되면_무산되지만_제안_알림은_따로_보내지_않는다() throws Exception {
            long id = proposalId(propose(alice, "2026-11-10T03:30:00Z"));
            mockMvc.perform(post("/api/gatherings/{id}/cancel", gathering.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(host))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"출장\"}"))
                    .andExpect(status().isOk());
            flushAndClear();

            assertThat(statusOf(id)).isEqualTo("CANCELED");
            assertThat(receivers("TIME_CHANGE_CANCELED")).isEmpty();
        }
    }

    @Nested
    class 조회 {

        @Test
        void 구성원은_진행_중인_제안을_보고_없으면_data가_없다() throws Exception {
            current(bob).andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
            propose(alice, "2026-11-10T03:30:00Z");

            current(bob)
                    .andExpect(jsonPath("$.data.toStartAt").value("2026-11-10T03:30:00Z"))
                    .andExpect(jsonPath("$.data.votes.length()").value(3));
            current(carol)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("TP002"));
        }
    }

    @Test
    void 제안_기록이_있어도_확정_참가자가_없으면_모임을_삭제할_수_있다() throws Exception {
        setStatus(GatheringStatus.RECRUITING);
        long id = proposalId(propose(alice, "2026-11-10T03:30:00Z"));
        reject(host, id).andExpect(status().isOk());
        leave(alice).andExpect(status().isOk());
        leave(bob).andExpect(status().isOk());

        mockMvc.perform(delete("/api/gatherings/{id}", gathering.getId()).header(HttpHeaders.AUTHORIZATION, bearer(host)))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("select count(*) from gathering_time_proposals where id = ?", Long.class, id))
                .isZero();
    }

    private ResultActions propose(User user, String startAt) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/gatherings/{id}/time-proposals", gathering.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startAt\": \"" + startAt + "\"}"));
        flushAndClear();
        return result;
    }

    private ResultActions accept(User user, long proposalId) throws Exception {
        return respond(user, proposalId, "accept");
    }

    private ResultActions reject(User user, long proposalId) throws Exception {
        return respond(user, proposalId, "reject");
    }

    private ResultActions respond(User user, long proposalId, String action) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/gatherings/{id}/time-proposals/{proposalId}/" + action,
                gathering.getId(), proposalId).header(HttpHeaders.AUTHORIZATION, bearer(user)));
        flushAndClear();
        return result;
    }

    private ResultActions withdraw(User user, long proposalId) throws Exception {
        ResultActions result = mockMvc.perform(delete("/api/gatherings/{id}/time-proposals/{proposalId}",
                gathering.getId(), proposalId).header(HttpHeaders.AUTHORIZATION, bearer(user)));
        flushAndClear();
        return result;
    }

    private ResultActions current(User user) throws Exception {
        return mockMvc.perform(get("/api/gatherings/{id}/time-proposals/current", gathering.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions leave(User user) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/gatherings/{id}/leave", gathering.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
        flushAndClear();
        return result;
    }

    private static long proposalId(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.proposalId"))
                .longValue();
    }

    private String statusOf(long proposalId) {
        return jdbcTemplate.queryForObject("select status from gathering_time_proposals where id = ?", String.class,
                proposalId);
    }

    private List<Long> receivers(String type) {
        return jdbcTemplate.queryForList("select receiver_id from notifications where gathering_id = ? and type = ?",
                Long.class, gathering.getId(), type);
    }

    private List<String> messages(String type) {
        return jdbcTemplate.queryForList("select message from notifications where gathering_id = ? and type = ?",
                String.class, gathering.getId(), type);
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.issueAccessToken(user.getId()).value();
    }

    private Participation participation(User user, ParticipationStatus status) {
        Participation saved = participationRepository.save(Participation.builder()
                .gathering(gathering).user(user).status(status)
                .appliedAt(NOW.minus(Duration.ofDays(1)))
                .decidedAt(status == ParticipationStatus.CONFIRMED ? NOW.minus(Duration.ofDays(1)) : null)
                .build());
        flushAndClear();
        return saved;
    }

    private User user(String key, String nickname) {
        return userRepository.save(User.signUpWithEmail(key + "@example.com", "hash", nickname, key, Gender.MALE));
    }

    private void setStatus(GatheringStatus status) {
        entityManager.createNativeQuery("UPDATE gatherings SET status = :status WHERE id = :id")
                .setParameter("status", status.name()).setParameter("id", gathering.getId()).executeUpdate();
        flushAndClear();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
