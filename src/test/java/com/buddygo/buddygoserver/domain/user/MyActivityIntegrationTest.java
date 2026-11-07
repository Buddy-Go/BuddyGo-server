package com.buddygo.buddygoserver.domain.user;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.checkin.entity.CheckIn;
import com.buddygo.buddygoserver.domain.checkin.enums.Attendance;
import com.buddygo.buddygoserver.domain.checkin.repository.CheckInRepository;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.entity.Place;
import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.PlaceRepository;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.enums.Gender;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.security.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 로컬 PostgreSQL 필요. 테스트 데이터는 롤백하며 시간 경계는 고정한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MyActivityIntegrationTest.FixedClockConfig.class)
@Transactional
class MyActivityIntegrationTest {

    static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");
    static final String URL = "/api/users/me/gatherings";

    @TestConfiguration
    static class FixedClockConfig {
        @Bean @Primary
        Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired GatheringRepository gatheringRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired ParticipationRepository participationRepository;
    @Autowired CheckInRepository checkInRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired EntityManager entityManager;

    User me;
    User other;
    Place place;

    @BeforeEach
    void setUp() {
        me = userRepository.save(User.signUpWithEmail("activity-me@example.com", "hash", "주원", "활동주원", Gender.MALE));
        other = userRepository.save(User.signUpWithEmail("activity-other@example.com", "hash", "다른", "활동다른", Gender.MALE));
        place = placeRepository.save(Place.pinned("버디짐", null, "서울 강남구 역삼동 1", "1168010100",
                new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(127, 37.5))));
    }

    @Test
    void 개설과_참여를_분리하고_다른_사용자_내역은_제외한다() throws Exception {
        Gathering hosted = gathering(me, NOW.plusSeconds(7200));
        Gathering joined = gathering(other, NOW.plusSeconds(10800));
        participate(joined, me, ParticipationStatus.PENDING);
        gathering(other, NOW.plusSeconds(14400));
        activities("host", "upcoming").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].gatheringId").value(hosted.getId()))
                .andExpect(jsonPath("$.data.content[0].confirmedCount").value(1));
        activities("participant", "upcoming").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].gatheringId").value(joined.getId()))
                .andExpect(jsonPath("$.data.content[0].participationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.content[0].placeName").value("버디짐"));
    }

    @ParameterizedTest
    @EnumSource(value = ParticipationStatus.class, names = {"REJECTED", "CANCELED", "LEFT"})
    void 거절_취소_나가기는_미래_모임이어도_지난_내역이다(ParticipationStatus state) throws Exception {
        participate(gathering(other, NOW.plusSeconds(7200)), me, state);
        activities("participant", "past").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].participationStatus").value(state.name()));
        activities("participant", "upcoming").andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    void 재신청한_모임은_최신_상태로_한번만_보인다() throws Exception {
        Gathering g = gathering(other, NOW.plusSeconds(7200));
        participate(g, me, ParticipationStatus.REJECTED);
        Participation latest = participate(g, me, ParticipationStatus.PENDING);
        activities("participant", "upcoming").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].participationId").value(latest.getId()));
        activities("participant", "past").andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    void 진행중은_예정이고_종료시각부터_지난_내역이다() throws Exception {
        Gathering ongoing = gathering(me, NOW.minusSeconds(1800));
        Gathering ended = gathering(me, NOW.minusSeconds(3600));
        activities("host", "upcoming").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].gatheringId").value(ongoing.getId()))
                .andExpect(jsonPath("$.data.content[0].inProgress").value(true));
        activities("host", "past").andExpect(jsonPath("$.data.content[0].gatheringId").value(ended.getId()))
                .andExpect(jsonPath("$.data.content[0].result").doesNotExist());
    }

    @Test
    void 모임취소는_지난내역이고_삭제모임은_제외한다() throws Exception {
        Gathering canceled = gathering(me, NOW.plusSeconds(7200));
        canceled.cancel();
        gathering(me, NOW.plusSeconds(10800)).delete(NOW);
        activities("host", "past").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].gatheringStatus").value("CANCELED"));
        activities("host", "upcoming").andExpect(jsonPath("$.data.content").isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Attendance.class)
    void 출결에_따라_완료와_노쇼를_구분한다(Attendance attendance) throws Exception {
        Gathering g = gathering(other, NOW.minusSeconds(7200));
        g.finish();
        Participation p = participate(g, me, ParticipationStatus.CONFIRMED);
        checkInRepository.save(CheckIn.builder().participation(p).attendance(attendance).build());
        activities("participant", "past").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].attendance").value(attendance.name()))
                .andExpect(jsonPath("$.data.content[0].result").value(
                        attendance == Attendance.NO_SHOW ? "NO_SHOW" : "COMPLETED"));
    }

    @Test
    void 페이지별_정렬과_전체건수가_일치한다() throws Exception {
        Gathering later = gathering(me, NOW.plusSeconds(10800));
        Gathering first = gathering(me, NOW.plusSeconds(7200));
        Gathering second = gathering(me, NOW.plusSeconds(7200));
        page("upcoming", 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].gatheringId").value(first.getId()))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(3));
        page("upcoming", 1).andExpect(jsonPath("$.data.content[0].gatheringId").value(second.getId()));
        page("upcoming", 2).andExpect(jsonPath("$.data.content[0].gatheringId").value(later.getId()));
        page("upcoming", 3).andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }

    @Test
    void 지난내역은_최근_모임부터_보인다() throws Exception {
        gathering(me, NOW.minusSeconds(10800));
        Gathering recent = gathering(me, NOW.minusSeconds(7200));
        page("past", 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].gatheringId").value(recent.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"role=unknown", "period=unknown", "sport=RUNNING", "page=-1", "size=0", "size=101", "page=abc"})
    void 잘못된_필터는_400(String invalid) throws Exception {
        String[] pair = invalid.split("=");
        var request = get(URL).header(HttpHeaders.AUTHORIZATION, bearer(me.getId()));
        if (!pair[0].equals("role")) { request.param("role", "host"); }
        mockMvc.perform(request.param(pair[0], pair[1])).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("G002"));
    }

    @Test
    void 필수역할과_인증을_확인한다() throws Exception {
        mockMvc.perform(get(URL).param("role", "host")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(me.getId())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("role", "host").header(HttpHeaders.AUTHORIZATION, bearer(Long.MAX_VALUE)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    void 헬스필터와_기본페이지를_지원하며_없으면_빈목록이다() throws Exception {
        mockMvc.perform(get(URL).param("role", "host").param("sport", "gym")
                        .header(HttpHeaders.AUTHORIZATION, bearer(me.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.page").value(0)).andExpect(jsonPath("$.data.size").value(20));
    }

    private ResultActions activities(String role, String period) throws Exception {
        entityManager.flush();
        entityManager.clear();
        return mockMvc.perform(get(URL).param("role", role).param("period", period)
                .header(HttpHeaders.AUTHORIZATION, bearer(me.getId())));
    }

    private ResultActions page(String period, int page) throws Exception {
        entityManager.flush();
        return mockMvc.perform(get(URL).param("role", "host").param("period", period)
                .param("page", String.valueOf(page)).param("size", "1")
                .header(HttpHeaders.AUTHORIZATION, bearer(me.getId())));
    }

    private String bearer(Long userId) { return "Bearer " + jwtTokenProvider.issueAccessToken(userId).value(); }

    private Gathering gathering(User host, Instant start) {
        Gathering g = gatheringRepository.save(Gathering.open(host, place, "헬스 같이 해요", null, "3층",
                start, Duration.ofHours(1), (short) 3, GenderRule.ANY));
        participationRepository.save(Participation.hostOf(g, NOW));
        return g;
    }

    private Participation participate(Gathering g, User user, ParticipationStatus state) {
        return participationRepository.save(Participation.builder().gathering(g).user(user).status(state)
                .appliedAt(NOW).build());
    }
}
