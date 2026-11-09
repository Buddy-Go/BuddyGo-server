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
 * 호스트 출석 보너스(+1.0, application.yaml). 종료된 모임에서 호스트가 체크인(정상·지각)했으면 종료할 때 준다.
 * 오를 때 곡선: × min(1, (100 - 현재) / (100 - 36.5)). 정원은 호스트 포함 3명, 시작 10:00, 종료 11:00.
 */
@Transactional
class HostAttendanceBonusIntegrationTest extends IntegrationTest {

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
    @Autowired GatheringStatusScheduler scheduler;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired EntityManager entityManager;

    User host;
    User alice;
    User bob;
    Gathering gathering;

    @BeforeEach
    void setUp() {
        // 노쇼한 참가자보다 호스트 id 가 크도록 참가자를 먼저 만든다(한 번에 오름차순으로 잠그는지 확인)
        alice = user("hb-alice", "앨리스");
        bob = user("hb-bob", "밥");
        host = user("hb-host", "호스트");
        Place place = placeRepository.save(Place.pinned("버디짐", null, "서울 강남구 역삼동 1", "1168010100",
                new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(LNG, LAT))));
        gathering = gatheringRepository.save(Gathering.builder()
                .host(host).place(place).title("같이 운동해요")
                .startAt(START).endAt(END)
                .capacity((short) 3).genderRule(GenderRule.ANY).status(GatheringStatus.CLOSED)
                .build());
        participationRepository.save(Participation.hostOf(gathering, START.minus(Duration.ofDays(1))));
        confirmed(alice);
        confirmed(bob);
        flushAndClear();
    }

    @Test
    void 정상_출석한_호스트는_종료할_때_보너스를_받는다() throws Exception {
        checkIn(host).andExpect(status().isCreated());
        checkIn(alice).andExpect(status().isCreated());
        checkIn(bob).andExpect(status().isCreated());
        // 체크인만으로는 아직 주지 않는다
        flushAndClear();
        assertThat(exactTemperature(host)).isEqualByComparingTo("36.50");

        finish();

        assertThat(exactTemperature(host)).isEqualByComparingTo("37.50");
        assertThat(count("SELECT count(*) FROM manner_temperature_logs WHERE user_id = ? AND reason = 'HOST_ATTENDED' "
                + "AND delta = 1.00 AND gathering_id = " + gathering.getId(), host.getId())).isEqualTo(1);
        // 참가자는 보너스 대상이 아니다
        assertThat(exactTemperature(alice)).isEqualByComparingTo("36.50");
    }

    @Test
    void 지각한_호스트는_지각_페널티와_보너스를_둘_다_받는다() throws Exception {
        mutableClock.fix(START.plus(Duration.ofMinutes(6)));
        setStatus(GatheringStatus.IN_PROGRESS);
        checkIn(host).andExpect(status().isCreated()).andExpect(jsonPath("$.data.attendance").value("LATE"));
        flushAndClear();
        assertThat(exactTemperature(host)).isEqualByComparingTo("35.50");

        finish();

        // 35.50 은 기준점 아래라 오를 때 배율 1: +1.00
        assertThat(exactTemperature(host)).isEqualByComparingTo("36.50");
        assertThat(logCount(host, MannerTemperatureReason.LATE)).isEqualTo(1);
        assertThat(logCount(host, MannerTemperatureReason.HOST_ATTENDED)).isEqualTo(1);
    }

    @Test
    void 노쇼한_호스트는_보너스가_없다() throws Exception {
        checkIn(alice).andExpect(status().isCreated());

        finish();

        assertThat(exactTemperature(host)).isEqualByComparingTo("31.50");
        assertThat(logCount(host, MannerTemperatureReason.HOST_ATTENDED)).isZero();
        assertThat(logCount(host, MannerTemperatureReason.NO_SHOW)).isEqualTo(1);
    }

    @Test
    void 참가자가_모두_노쇼해도_출석한_호스트는_보너스를_받는다() throws Exception {
        checkIn(host).andExpect(status().isCreated());

        // 노쇼 판정을 건너뛰고 종료에서 노쇼 기록과 보너스를 함께 처리한다(호스트 id 가 노쇼한 사람들보다 크다)
        scheduler.runAt(START);
        scheduler.runAt(END);
        flushAndClear();

        assertThat(exactTemperature(host)).isEqualByComparingTo("37.50");
        assertThat(exactTemperature(alice)).isEqualByComparingTo("31.50");
        assertThat(exactTemperature(bob)).isEqualByComparingTo("31.50");
    }

    @Test
    void 높은_온도에서는_곡선에_따라_덜_오른다() throws Exception {
        setTemperature(host, "90.00");
        checkIn(host).andExpect(status().isCreated());

        finish();

        // +1 × (100 - 90) / 63.5 = 0.157 → 0.16
        assertThat(exactTemperature(host)).isEqualByComparingTo("90.16");
    }

    @Test
    void 종료가_다시_돌아도_한_번만_준다() throws Exception {
        checkIn(host).andExpect(status().isCreated());

        finish();
        scheduler.runAt(END.plus(Duration.ofMinutes(10)));
        flushAndClear();

        assertThat(exactTemperature(host)).isEqualByComparingTo("37.50");
        assertThat(logCount(host, MannerTemperatureReason.HOST_ATTENDED)).isEqualTo(1);
    }

    @Test
    void 체크인한_뒤_방폭으로_취소된_모임은_보너스가_없다() throws Exception {
        checkIn(host).andExpect(status().isCreated());
        // 데드라인 후, 시작 전 참가자가 나가 모임이 취소된다
        mockMvc.perform(post("/api/gatherings/{id}/leave", gathering.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(alice))).andExpect(status().isOk());

        finish();

        assertThat(logCount(host, MannerTemperatureReason.HOST_ATTENDED)).isZero();
        assertThat(exactTemperature(host)).isEqualByComparingTo("36.50");
    }

    private void finish() {
        flushAndClear();
        scheduler.runAt(START);
        scheduler.runAt(START.plus(Duration.ofMinutes(10)));
        scheduler.runAt(END);
        flushAndClear();
    }

    private ResultActions checkIn(User user) throws Exception {
        return mockMvc.perform(post("/api/gatherings/{id}/check-in", gathering.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"latitude\": %s, \"longitude\": %s}".formatted(LAT, LNG)));
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.issueAccessToken(user.getId()).value();
    }

    private void confirmed(User user) {
        participationRepository.save(Participation.builder()
                .gathering(gathering).user(user).status(ParticipationStatus.CONFIRMED)
                .appliedAt(START.minus(Duration.ofDays(1))).decidedAt(START.minus(Duration.ofDays(1))).build());
    }

    private User user(String key, String nickname) {
        return userRepository.save(User.signUpWithEmail(key + "@example.com", "hash", nickname, key, Gender.MALE));
    }

    private void setStatus(GatheringStatus status) {
        entityManager.createNativeQuery("UPDATE gatherings SET status = :status WHERE id = :id")
                .setParameter("status", status.name()).setParameter("id", gathering.getId()).executeUpdate();
        flushAndClear();
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
        return count("SELECT count(*) FROM manner_temperature_logs WHERE user_id = ? AND reason = '"
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
