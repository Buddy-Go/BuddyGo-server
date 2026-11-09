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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 지각·노쇼 매너온도 페널티. 점수는 노쇼 -5, 지각 -1(application.yaml)이고 내릴 때 곡선(× min(1, 현재 / 36.5))을 거친다.
 * 확정 인원은 호스트·late·onTime 세 명이다.
 */
@Transactional
class AttendancePenaltyIntegrationTest extends IntegrationTest {

    static final Instant START = Instant.parse("2026-11-10T10:00:00Z");
    static final Instant END = START.plus(Duration.ofHours(1));
    static final double LAT = 37.5;
    static final double LNG = 127.0;

    @Override
    protected Instant fixedNow() {
        return START.minus(Duration.ofMinutes(5));
    }

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired GatheringRepository gatheringRepository;
    @Autowired ParticipationRepository participationRepository;
    @Autowired MannerTemperatureService mannerTemperatureService;
    @Autowired GatheringStatusScheduler scheduler;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired EntityManager entityManager;

    User host;
    User late;
    User onTime;
    Gathering gathering;

    @BeforeEach
    void setUp() {
        host = user("mp-host", "호스트");
        late = user("mp-late", "지각");
        onTime = user("mp-ontime", "정상");
        Place place = placeRepository.save(Place.pinned("버디짐", null, "서울 강남구 역삼동 1", "1168010100",
                new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(LNG, LAT))));
        gathering = gatheringRepository.save(Gathering.builder()
                .host(host).place(place).title("같이 운동해요")
                .startAt(START).endAt(END)
                .capacity((short) 3).genderRule(GenderRule.ANY).status(GatheringStatus.CLOSED)
                .build());
        participationRepository.save(Participation.hostOf(gathering, START.minus(Duration.ofDays(1))));
        confirmed(late);
        confirmed(onTime);
        flushAndClear();
    }

    @Test
    void 정상_체크인은_매너온도에_영향이_없다() throws Exception {
        checkIn(onTime).andExpect(status().isCreated()).andExpect(jsonPath("$.data.attendance").value("ON_TIME"));

        assertThat(exactTemperature(onTime)).isEqualByComparingTo("36.50");
        assertThat(logCount(onTime, null)).isZero();
    }

    @Test
    void 지각_체크인은_곧바로_매너온도를_내리고_모임과_함께_이력을_남긴다() throws Exception {
        mutableClock.fix(START.plus(Duration.ofMinutes(5)));
        checkIn(late).andExpect(status().isCreated()).andExpect(jsonPath("$.data.attendance").value("LATE"));

        assertThat(exactTemperature(late)).isEqualByComparingTo("35.50");
        assertThat(count("SELECT count(*) FROM manner_temperature_logs WHERE user_id = ? AND reason = 'LATE' "
                + "AND delta = -1.00 AND gathering_id = " + gathering.getId() + " AND review_id IS NULL", late.getId()))
                .isEqualTo(1);
    }

    @Test
    void 노쇼는_체크인하지_않은_사람만_내리고_호스트도_포함된다() throws Exception {
        checkIn(onTime).andExpect(status().isCreated());
        mutableClock.fix(START.plus(Duration.ofMinutes(6)));
        checkIn(late).andExpect(status().isCreated());
        flushAndClear();
        // onTime·late 는 체크인했으므로 호스트만 노쇼가 된다
        scheduler.runAt(START);
        scheduler.runAt(START.plus(Duration.ofMinutes(10)));
        flushAndClear();

        assertThat(exactTemperature(host)).isEqualByComparingTo("31.50");
        assertThat(logCount(host, MannerTemperatureReason.NO_SHOW)).isEqualTo(1);
        assertThat(exactTemperature(onTime)).isEqualByComparingTo("36.50");
        assertThat(exactTemperature(late)).isEqualByComparingTo("35.50");
        assertThat(logCount(late, MannerTemperatureReason.NO_SHOW)).isZero();
    }

    @Test
    void 노쇼_판정과_종료가_다시_돌아도_페널티는_한_번만_들어간다() {
        scheduler.runAt(START);
        scheduler.runAt(START.plus(Duration.ofMinutes(10)));
        scheduler.runAt(START.plus(Duration.ofMinutes(10)));
        scheduler.runAt(END);
        flushAndClear();

        for (User user : new User[]{host, late, onTime}) {
            assertThat(exactTemperature(user)).isEqualByComparingTo("31.50");
            assertThat(logCount(user, MannerTemperatureReason.NO_SHOW)).isEqualTo(1);
        }
        assertThat(count("SELECT count(*) FROM manner_temperature_logs WHERE delta = -5.00 AND gathering_id = ?",
                gathering.getId())).isEqualTo(3);
    }

    @Test
    void 노쇼_판정이_빠져도_종료할_때_남기며_페널티를_준다() {
        scheduler.runAt(START);
        scheduler.runAt(END);
        flushAndClear();

        assertThat(exactTemperature(host)).isEqualByComparingTo("31.50");
        assertThat(logCount(host, MannerTemperatureReason.NO_SHOW)).isEqualTo(1);
    }

    @Test
    void 기준점보다_낮으면_곡선에_따라_덜_내려간다() {
        setTemperature(host, "18.25");
        scheduler.runAt(START);
        scheduler.runAt(START.plus(Duration.ofMinutes(10)));
        flushAndClear();

        // -5 × 18.25 / 36.5 = -2.5
        assertThat(exactTemperature(host)).isEqualByComparingTo("15.75");
        assertThat(count("SELECT count(*) FROM manner_temperature_logs WHERE user_id = ? AND delta = -2.50",
                host.getId())).isEqualTo(1);
    }

    @Test
    void 같은_모임_같은_사유로_두_번_불려도_한_번만_반영하고_실패하지_않는다() {
        User user = userRepository.findByIdForUpdate(host.getId()).orElseThrow();

        assertThat(mannerTemperatureService.applyNoShow(user, gathering.getId())).isEqualByComparingTo("-5.00");
        assertThat(mannerTemperatureService.applyNoShow(user, gathering.getId())).isEqualByComparingTo("0");
        // 사유가 다르면 따로 반영된다. 31.50 에서는 곡선으로 -1 × 31.5 / 36.5 = -0.86
        assertThat(mannerTemperatureService.applyLate(user, gathering.getId())).isEqualByComparingTo("-0.86");
        flushAndClear();

        assertThat(exactTemperature(host)).isEqualByComparingTo("30.64");
        assertThat(logCount(host, MannerTemperatureReason.NO_SHOW)).isEqualTo(1);
        assertThat(logCount(host, MannerTemperatureReason.LATE)).isEqualTo(1);
    }

    private ResultActions checkIn(User user) throws Exception {
        return mockMvc.perform(post("/api/gatherings/{id}/check-in", gathering.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(user.getId()).value())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"latitude\": %s, \"longitude\": %s}".formatted(LAT, LNG)));
    }

    private void confirmed(User user) {
        participationRepository.save(Participation.builder()
                .gathering(gathering).user(user).status(ParticipationStatus.CONFIRMED)
                .appliedAt(START.minus(Duration.ofDays(1))).decidedAt(START.minus(Duration.ofDays(1))).build());
    }

    private User user(String key, String nickname) {
        return userRepository.save(User.signUpWithEmail(key + "@example.com", "hash", nickname, key, Gender.MALE));
    }

    private void setTemperature(User user, String value) {
        entityManager.createNativeQuery("UPDATE users SET manner_temperature = :t WHERE id = :id")
                .setParameter("t", new BigDecimal(value)).setParameter("id", user.getId()).executeUpdate();
        flushAndClear();
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

    private long count(String sql, Object param) {
        return ((Number) entityManager.createNativeQuery(sql).setParameter(1, param).getSingleResult()).longValue();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
