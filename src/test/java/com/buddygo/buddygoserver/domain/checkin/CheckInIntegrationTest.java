package com.buddygo.buddygoserver.domain.checkin;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
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
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.enums.Gender;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.security.jwt.JwtTokenProvider;
import com.buddygo.buddygoserver.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
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
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 모임 장소는 (위도 37.5, 경도 127.0). 위도 0.0001도 ≈ 11.1m 라 0.0003 ≈ 33m(범위 안), 0.0012 ≈ 133m(범위 밖)다.
 * 체크인 창: 시작 10분 전 ~ 시작 후 10분 전, 시작 후 5분부터 지각.
 */
@Transactional
class CheckInIntegrationTest extends IntegrationTest {

    static final Instant START = Instant.parse("2026-11-10T10:00:00Z");
    static final double LAT = 37.5;
    static final double LNG = 127.0;
    static final double NEAR_LAT = 37.5003;
    static final double FAR_LAT = 37.5012;

    @Override
    protected Instant fixedNow() {
        return START.minus(Duration.ofMinutes(5));
    }

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired GatheringRepository gatheringRepository;
    @Autowired ParticipationRepository participationRepository;
    @Autowired CheckInRepository checkInRepository;
    @Autowired GatheringStatusScheduler scheduler;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired EntityManager entityManager;

    User host;
    User member;
    User applicant;
    User outsider;
    Gathering gathering;
    Participation hostParticipation;
    Participation memberParticipation;

    @BeforeEach
    void setUp() {
        host = user("ci-host", "호스트");
        member = user("ci-member", "메이트");
        applicant = user("ci-applicant", "대기자");
        outsider = user("ci-outsider", "외부인");
        Place place = placeRepository.save(Place.pinned("버디짐", null, "서울 강남구 역삼동 1", "1168010100",
                new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(LNG, LAT))));
        gathering = gatheringRepository.save(Gathering.builder()
                .host(host).place(place).title("같이 운동해요")
                .startAt(START).endAt(START.plus(Duration.ofHours(1)))
                .capacity((short) 2).genderRule(GenderRule.ANY).status(GatheringStatus.CLOSED)
                .build());
        hostParticipation = participationRepository.save(Participation.hostOf(gathering, START.minus(Duration.ofDays(1))));
        memberParticipation = participationRepository.save(Participation.builder()
                .gathering(gathering).user(member).status(ParticipationStatus.CONFIRMED)
                .appliedAt(START.minus(Duration.ofDays(1))).decidedAt(START.minus(Duration.ofDays(1))).build());
        participationRepository.save(Participation.builder()
                .gathering(gathering).user(applicant).status(ParticipationStatus.PENDING)
                .appliedAt(START.minus(Duration.ofDays(1))).build());
        flushAndClear();
    }

    @Nested
    class 체크인 {

        @Test
        void 범위_안에서_시작_전에_하면_정상이고_거리와_함께_저장한다() throws Exception {
            checkIn(member, NEAR_LAT, LNG)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.attendance").value("ON_TIME"))
                    .andExpect(jsonPath("$.data.checkedInAt").value(fixedNow().toString()))
                    .andExpect(jsonPath("$.data.distanceMeters").value(allOf(greaterThan(30), lessThan(37))));

            CheckIn saved = checkInRepository.findById(memberParticipation.getId()).orElseThrow();
            assertThat(saved.getAttendance()).isEqualTo(Attendance.ON_TIME);
            assertThat(saved.getCheckedInAt()).isEqualTo(fixedNow());
            assertThat(saved.getDistanceM()).isBetween(30, 37);
        }

        @Test
        void 호스트도_체크인한다() throws Exception {
            checkIn(host, LAT, LNG)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.distanceMeters").value(0));
            assertThat(checkInRepository.existsById(hostParticipation.getId())).isTrue();
        }

        @ParameterizedTest(name = "시작 기준 {0}초 → {1}")
        @CsvSource({"-600, ON_TIME", "0, ON_TIME", "299, ON_TIME", "300, LATE", "599, LATE"})
        void 시작_후_5분부터는_지각이다(long secondsFromStart, Attendance expected) throws Exception {
            mutableClock.fix(START.plusSeconds(secondsFromStart));
            if (secondsFromStart >= 0) {
                setStatus(GatheringStatus.IN_PROGRESS);
            }
            checkIn(member, NEAR_LAT, LNG)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.attendance").value(expected.name()));
        }

        @Test
        void 시작이_지났는데_아직_마감_상태여도_시각으로_판정한다() throws Exception {
            // 스케줄러가 진행중으로 바꾸기 전
            mutableClock.fix(START.plus(Duration.ofMinutes(6)));
            checkIn(member, NEAR_LAT, LNG)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.attendance").value("LATE"));
        }

        @ParameterizedTest(name = "시작 기준 {0}초")
        @CsvSource({"-601", "600", "601"})
        void 체크인_창_밖이면_C004(long secondsFromStart) throws Exception {
            mutableClock.fix(START.plusSeconds(secondsFromStart));
            checkIn(member, NEAR_LAT, LNG)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("C004"));
            assertThat(checkInRepository.existsById(memberParticipation.getId())).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = GatheringStatus.class, names = {"RECRUITING", "CANCELED", "FINISHED"})
        void 마감_진행중이_아닌_모임은_C004(GatheringStatus status) throws Exception {
            setStatus(status);
            checkIn(member, NEAR_LAT, LNG)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("C004"));
        }

        @Test
        void 범위_밖이면_422와_현재_거리를_주고_저장하지_않는다() throws Exception {
            checkIn(member, FAR_LAT, LNG)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("C003"))
                    .andExpect(jsonPath("$.message").value("체크인 가능 범위를 벗어났습니다."))
                    .andExpect(jsonPath("$.distanceMeters").value(allOf(greaterThan(125), lessThan(140))))
                    .andExpect(jsonPath("$.radiusMeters").value(50));
            assertThat(checkInRepository.existsById(memberParticipation.getId())).isFalse();
        }

        @Test
        void 범위_밖에서_실패한_뒤_다시_시도할_수_있다() throws Exception {
            checkIn(member, FAR_LAT, LNG).andExpect(status().isUnprocessableContent());
            checkIn(member, NEAR_LAT, LNG).andExpect(status().isCreated());
        }

        @Test
        void 한_번만_할_수_있다() throws Exception {
            checkIn(member, NEAR_LAT, LNG).andExpect(status().isCreated());
            flushAndClear();
            checkIn(member, NEAR_LAT, LNG)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("C002"))
                    .andExpect(jsonPath("$.message").value("이미 체크인했습니다."));
        }

        @Test
        void 확정_참가자가_아니면_C001() throws Exception {
            checkIn(applicant, NEAR_LAT, LNG)
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("C001"));
            checkIn(outsider, NEAR_LAT, LNG)
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("C001"));
        }

        @Test
        void 없는_모임은_404() throws Exception {
            mockMvc.perform(post("/api/gatherings/{id}/check-in", Long.MAX_VALUE)
                            .header(HttpHeaders.AUTHORIZATION, bearer(member))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"latitude\": 37.5, \"longitude\": 127.0}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("GA001"));
        }

        @Test
        void 좌표가_없거나_범위를_벗어나면_400() throws Exception {
            for (String body : new String[]{"{\"longitude\": 127.0}", "{\"latitude\": 91, \"longitude\": 127.0}"}) {
                mockMvc.perform(post("/api/gatherings/{id}/check-in", gathering.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearer(member))
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("G002"));
            }
        }

        @Test
        void 노쇼_판정은_체크인한_사람을_건드리지_않고_체크인하지_않은_사람만_남긴다() throws Exception {
            checkIn(member, NEAR_LAT, LNG).andExpect(status().isCreated());
            flushAndClear();

            scheduler.runAt(START);
            scheduler.runAt(START.plus(Duration.ofMinutes(10)));
            flushAndClear();

            assertThat(checkInRepository.findById(memberParticipation.getId()).orElseThrow().getAttendance())
                    .isEqualTo(Attendance.ON_TIME);
            assertThat(checkInRepository.findById(hostParticipation.getId()).orElseThrow().getAttendance())
                    .isEqualTo(Attendance.NO_SHOW);

            // 노쇼로 기록된 뒤에는 다시 체크인할 수 없고, 체크인한 적은 없으므로 사유를 따로 준다
            checkIn(host, LAT, LNG)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("C002"))
                    .andExpect(jsonPath("$.message").value("노쇼로 기록되어 체크인할 수 없습니다."));
        }
    }

    @Nested
    class 현황 {

        @Test
        void 체크인_창과_명단별_결과와_내_결과를_준다() throws Exception {
            checkIn(member, NEAR_LAT, LNG).andExpect(status().isCreated());
            flushAndClear();

            checkInStatus(member)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.openAt").value("2026-11-10T09:50:00Z"))
                    .andExpect(jsonPath("$.data.lateFrom").value("2026-11-10T10:05:00Z"))
                    .andExpect(jsonPath("$.data.closeAt").value("2026-11-10T10:10:00Z"))
                    .andExpect(jsonPath("$.data.radiusMeters").value(50))
                    .andExpect(jsonPath("$.data.me.attendance").value("ON_TIME"))
                    .andExpect(jsonPath("$.data.me.distanceMeters").value(allOf(greaterThan(30), lessThan(37))))
                    .andExpect(jsonPath("$.data.members.length()").value(2))
                    .andExpect(jsonPath("$.data.members[0].userId").value(host.getId()))
                    .andExpect(jsonPath("$.data.members[0].host").value(true))
                    .andExpect(jsonPath("$.data.members[0].attendance").isEmpty())
                    .andExpect(jsonPath("$.data.members[1].userId").value(member.getId()))
                    .andExpect(jsonPath("$.data.members[1].attendance").value("ON_TIME"))
                    .andExpect(jsonPath("$.data.members[1].checkedInAt").value(fixedNow().toString()))
                    .andExpect(jsonPath("$.data.members[1].distanceMeters").doesNotExist());

            checkInStatus(host)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.me.attendance").isEmpty())
                    .andExpect(jsonPath("$.data.me.distanceMeters").isEmpty())
                    .andExpect(jsonPath("$.data.members.length()").value(2));
        }

        @Test
        void 노쇼도_결과로_보인다() throws Exception {
            scheduler.runAt(START);
            scheduler.runAt(START.plus(Duration.ofMinutes(10)));
            flushAndClear();

            checkInStatus(member)
                    .andExpect(jsonPath("$.data.me.attendance").value("NO_SHOW"))
                    .andExpect(jsonPath("$.data.me.checkedInAt").isEmpty())
                    .andExpect(jsonPath("$.data.members[0].attendance").value("NO_SHOW"));
        }

        @Test
        void 마감_전_모임에서_확정_참가자는_명단을_받지_않고_호스트는_받는다() throws Exception {
            setStatus(GatheringStatus.RECRUITING);
            checkInStatus(member)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.members").isEmpty())
                    .andExpect(jsonPath("$.data.me").exists());
            checkInStatus(host)
                    .andExpect(jsonPath("$.data.members.length()").value(2));
        }

        @Test
        void 확정_참가자가_아니면_C001() throws Exception {
            checkInStatus(applicant).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("C001"));
            checkInStatus(outsider).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("C001"));
        }
    }

    private ResultActions checkIn(User user, double latitude, double longitude) throws Exception {
        return mockMvc.perform(post("/api/gatherings/{id}/check-in", gathering.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"latitude\": %s, \"longitude\": %s}".formatted(latitude, longitude)));
    }

    private ResultActions checkInStatus(User user) throws Exception {
        return mockMvc.perform(get("/api/gatherings/{id}/check-ins", gathering.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.issueAccessToken(user.getId()).value();
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
