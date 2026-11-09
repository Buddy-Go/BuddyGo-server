package com.buddygo.buddygoserver.domain.manner;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.entity.Place;
import com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus;
import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.PlaceRepository;
import com.buddygo.buddygoserver.domain.gathering.scheduler.GatheringStatusScheduler;
import com.buddygo.buddygoserver.domain.manner.enums.MannerTemperatureReason;
import com.buddygo.buddygoserver.domain.manner.service.MannerTemperatureService;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.enums.Gender;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.security.jwt.JwtTokenProvider;
import com.buddygo.buddygoserver.support.IntegrationTest;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 나가기·호스트 취소 페널티. 점수(application.yaml): 데드라인 전 나가기 -2, 데드라인 후 -5, 모집중 취소 -2, 마감 후 취소 -5.
 * 모임은 NOW + 3시간에 시작하고 데드라인은 시작 90분 전(NOW + 1시간 30분)이다. 정원은 호스트 포함 3명.
 */
@Transactional
class LeaveCancelPenaltyIntegrationTest extends IntegrationTest {

    static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");
    static final Instant START = NOW.plus(Duration.ofHours(3));
    static final Instant AFTER_DEADLINE = START.minus(Duration.ofHours(1));

    @Override
    protected Instant fixedNow() {
        return NOW;
    }

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired GatheringRepository gatheringRepository;
    @Autowired ParticipationRepository participationRepository;
    @Autowired GatheringStatusScheduler scheduler;
    @Autowired MannerTemperatureService mannerTemperatureService;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired EntityManager entityManager;

    User host;
    User alice;
    User bob;
    Gathering gathering;

    @BeforeEach
    void setUp() {
        host = user("lc-host", "호스트");
        alice = user("lc-alice", "앨리스");
        bob = user("lc-bob", "밥");
        Place place = placeRepository.save(Place.pinned("버디짐", null, "서울 강남구 역삼동 1", "1168010100",
                new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(127.0, 37.5))));
        gathering = gatheringRepository.save(Gathering.builder()
                .host(host).place(place).title("같이 운동해요")
                .startAt(START).endAt(START.plus(Duration.ofHours(1)))
                .capacity((short) 3).genderRule(GenderRule.ANY).status(GatheringStatus.CLOSED)
                .build());
        participationRepository.save(Participation.hostOf(gathering, NOW.minus(Duration.ofDays(1))));
        confirmed(alice);
        confirmed(bob);
        flushAndClear();
    }

    @Nested
    class 나가기 {

        @Test
        void 모집중인_모임에서_나가면_영향이_없다() throws Exception {
            setStatus(GatheringStatus.RECRUITING);
            leave(alice).andExpect(status().isOk());

            assertThat(exactTemperature(alice)).isEqualByComparingTo("36.50");
            assertThat(logCount(alice, null)).isZero();
        }

        @Test
        void 마감된_모임에서_데드라인_전에_나가면_내려가고_다시_모집한다() throws Exception {
            leave(alice).andExpect(status().isOk());

            assertThat(exactTemperature(alice)).isEqualByComparingTo("34.50");
            assertThat(gatheringLogCount(alice, MannerTemperatureReason.LEFT_BEFORE_DEADLINE, "-2.00")).isEqualTo(1);
            assertThat(statusOf()).isEqualTo("RECRUITING");
            // 남은 사람은 영향 없음
            assertThat(exactTemperature(bob)).isEqualByComparingTo("36.50");
            assertThat(exactTemperature(host)).isEqualByComparingTo("36.50");
        }

        @Test
        void 데드라인_전_나가기는_다시_들어와_또_나가면_또_내려간다() throws Exception {
            leave(alice).andExpect(status().isOk());
            // 다시 신청·수락되어 마감된 상황. 첫 나가기(LEFT)를 먼저 반영해야 새 확정 행이 활성 신청 유니크에 걸리지 않는다
            flushAndClear();
            confirmed(alice);
            setStatus(GatheringStatus.CLOSED);
            leave(alice).andExpect(status().isOk());

            // 34.50 에서 곡선: -2 × 34.5 / 36.5 = -1.89
            assertThat(exactTemperature(alice)).isEqualByComparingTo("32.61");
            assertThat(logCount(alice, MannerTemperatureReason.LEFT_BEFORE_DEADLINE)).isEqualTo(2);
        }

        @Test
        void 마감된_모임에서_데드라인_후에_나가면_더_크게_내려가고_모임이_취소된다() throws Exception {
            mutableClock.fix(AFTER_DEADLINE);
            leave(alice).andExpect(status().isOk());

            assertThat(exactTemperature(alice)).isEqualByComparingTo("31.50");
            assertThat(gatheringLogCount(alice, MannerTemperatureReason.LEFT_AFTER_DEADLINE, "-5.00")).isEqualTo(1);
            assertThat(statusOf()).isEqualTo("CANCELED");
            // 남은 사람은 페널티 없음
            assertThat(exactTemperature(bob)).isEqualByComparingTo("36.50");
            assertThat(exactTemperature(host)).isEqualByComparingTo("36.50");
        }
    }

    @Nested
    class 호스트_취소 {

        @Test
        void 모집중_취소는_호스트만_내려간다() throws Exception {
            setStatus(GatheringStatus.RECRUITING);
            cancel(host).andExpect(status().isOk());

            assertThat(exactTemperature(host)).isEqualByComparingTo("34.50");
            assertThat(gatheringLogCount(host, MannerTemperatureReason.HOST_CANCELED_RECRUITING, "-2.00")).isEqualTo(1);
            assertThat(exactTemperature(alice)).isEqualByComparingTo("36.50");
            assertThat(logCount(alice, null)).isZero();
        }

        @Test
        void 마감_후_취소는_더_크게_내려간다() throws Exception {
            cancel(host).andExpect(status().isOk());

            assertThat(exactTemperature(host)).isEqualByComparingTo("31.50");
            assertThat(gatheringLogCount(host, MannerTemperatureReason.HOST_CANCELED_CLOSED, "-5.00")).isEqualTo(1);
        }

        @Test
        void 마감_후_취소는_데드라인이_지나도_같다() throws Exception {
            mutableClock.fix(AFTER_DEADLINE);
            cancel(host).andExpect(status().isOk());

            assertThat(exactTemperature(host)).isEqualByComparingTo("31.50");
            assertThat(logCount(host, MannerTemperatureReason.HOST_CANCELED_CLOSED)).isEqualTo(1);
        }

        @Test
        void 정원_미달_자동_취소는_누구에게도_영향이_없다() {
            setStatus(GatheringStatus.RECRUITING);
            scheduler.runAt(AFTER_DEADLINE);
            flushAndClear();

            assertThat(statusOf()).isEqualTo("CANCELED");
            assertThat(count("SELECT count(*) FROM manner_temperature_logs WHERE gathering_id = ?", gathering.getId()))
                    .isZero();
        }
    }

    @Test
    void 모임당_한_번뿐인_사유만_중복을_건너뛴다() {
        User locked = userRepository.findByIdForUpdate(alice.getId()).orElseThrow();

        assertThat(mannerTemperatureService.applyLeave(locked, gathering.getId(), true)).isEqualByComparingTo("-5.00");
        assertThat(mannerTemperatureService.applyLeave(locked, gathering.getId(), true)).isEqualByComparingTo("0");
        // 데드라인 전 나가기는 모임당 여러 번 생길 수 있어 매번 반영된다(31.50 에서 곡선: -2 × 31.5 / 36.5 = -1.73)
        assertThat(mannerTemperatureService.applyLeave(locked, gathering.getId(), false)).isEqualByComparingTo("-1.73");
        assertThat(mannerTemperatureService.applyLeave(locked, gathering.getId(), false)).isEqualByComparingTo("-1.63");
        flushAndClear();

        assertThat(logCount(alice, MannerTemperatureReason.LEFT_AFTER_DEADLINE)).isEqualTo(1);
        assertThat(logCount(alice, MannerTemperatureReason.LEFT_BEFORE_DEADLINE)).isEqualTo(2);
        assertThat(exactTemperature(alice)).isEqualByComparingTo("28.14");
    }

    private ResultActions leave(User user) throws Exception {
        return mockMvc.perform(post("/api/gatherings/{id}/leave", gathering.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions cancel(User user) throws Exception {
        return mockMvc.perform(post("/api/gatherings/{id}/cancel", gathering.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"갑자기 출장이 잡혔어요\"}"));
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.issueAccessToken(user.getId()).value();
    }

    private void confirmed(User user) {
        participationRepository.save(Participation.builder()
                .gathering(gathering).user(user).status(ParticipationStatus.CONFIRMED)
                .appliedAt(NOW.minus(Duration.ofDays(1))).decidedAt(NOW.minus(Duration.ofDays(1))).build());
        flushAndClear();
    }

    private User user(String key, String nickname) {
        return userRepository.save(User.signUpWithEmail(key + "@example.com", "hash", nickname, key, Gender.MALE));
    }

    private void setStatus(GatheringStatus status) {
        entityManager.createNativeQuery("UPDATE gatherings SET status = :status WHERE id = :id")
                .setParameter("status", status.name()).setParameter("id", gathering.getId()).executeUpdate();
        flushAndClear();
    }

    private String statusOf() {
        return (String) entityManager.createNativeQuery("SELECT status FROM gatherings WHERE id = :id")
                .setParameter("id", gathering.getId()).getSingleResult();
    }

    private BigDecimal exactTemperature(User user) {
        return (BigDecimal) entityManager.createNativeQuery("SELECT manner_temperature FROM users WHERE id = :id")
                .setParameter("id", user.getId()).getSingleResult();
    }

    private long logCount(User user, MannerTemperatureReason reason) {
        return reason == null
                ? count("SELECT count(*) FROM manner_temperature_logs WHERE user_id = ?", user.getId())
                : count("SELECT count(*) FROM manner_temperature_logs WHERE user_id = ? AND reason = '"
                        + reason.name() + "'", user.getId());
    }

    private long gatheringLogCount(User user, MannerTemperatureReason reason, String delta) {
        return count("SELECT count(*) FROM manner_temperature_logs WHERE user_id = ? AND reason = '" + reason.name()
                + "' AND delta = " + delta + " AND gathering_id = " + gathering.getId() + " AND review_id IS NULL",
                user.getId());
    }

    private long count(String sql, Object param) {
        return ((Number) entityManager.createNativeQuery(sql).setParameter(1, param).getSingleResult()).longValue();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
