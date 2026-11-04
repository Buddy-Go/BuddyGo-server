package com.buddygo.buddygoserver.domain.application;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 로컬 PostgreSQL(docker-compose.local.yml)이 떠 있어야 한다. DB 는 테스트마다 롤백되고 현재 시각은 NOW 로 고정된다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ApplicationIntegrationTest.FixedClockConfig.class) // @Nested 클래스에도 적용되도록 명시한다
@Transactional
class ApplicationIntegrationTest {

    static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");
    /** 데드라인(시작 - 90분) = NOW + 90분 */
    static final Instant START = NOW.plus(Duration.ofHours(3));

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired GatheringRepository gatheringRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired ParticipationRepository participationRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired EntityManager entityManager;

    User host;
    User alice;
    User bob;
    User carol;

    @BeforeEach
    void setUp() {
        host = user("host", Gender.MALE);
        alice = user("alice", Gender.MALE);
        bob = user("bob", Gender.MALE);
        carol = user("carol", Gender.FEMALE);
    }

    @Nested
    class 신청 {

        @Test
        void 모집중인_모임에_신청하면_대기_상태가_된다() throws Exception {
            Gathering gathering = gathering(START, 3);

            apply(alice, gathering, "{\"message\": \"  같이 해요  \"}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.applicationId").isNumber());

            Participation saved = participationRepository.findActive(gathering.getId(), alice.getId()).orElseThrow();
            assertThat(saved.getMessage()).isEqualTo("같이 해요");
            assertThat(saved.getAppliedAt()).isEqualTo(NOW);
        }

        @Test
        void 한마디_없이_본문을_비워도_신청된다() throws Exception {
            Gathering gathering = gathering(START, 3);
            mockMvc.perform(post("/api/gatherings/{id}/applications", gathering.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                    .andExpect(status().isCreated());
            assertThat(participationRepository.findActive(gathering.getId(), alice.getId()).orElseThrow().getMessage())
                    .isNull();
        }

        @Test
        void 이미_대기_중이면_AP002() throws Exception {
            Gathering gathering = gathering(START, 3);
            apply(alice, gathering, "{}").andExpect(status().isCreated());

            apply(alice, gathering, "{}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("AP002"));
        }

        @Test
        void 호스트는_자기_모임에_신청할_수_없다() throws Exception {
            apply(host, gathering(START, 3), "{}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("AP006"));
        }

        @Test
        void 성별_조건이_맞지_않으면_AP004() throws Exception {
            Gathering gathering = gathering(START, 3, GenderRule.MALE_ONLY);
            apply(carol, gathering, "{}")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AP004"));
        }

        @Test
        void 데드라인이_지났으면_GA003() throws Exception {
            Gathering atDeadline = gathering(NOW.plus(Duration.ofMinutes(90)), 3); // 데드라인 = NOW
            apply(alice, atDeadline, "{}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("GA003"));
        }

        @Test
        void 한마디가_200자를_넘으면_400() throws Exception {
            apply(alice, gathering(START, 3), "{\"message\": \"" + "가".repeat(201) + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.message").exists());
        }

        @Test
        void 없는_모임이면_GA001() throws Exception {
            mockMvc.perform(post("/api/gatherings/{id}/applications", 999_999L)
                            .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("GA001"));
        }
    }

    // ───────── helpers ─────────

    private User user(String name, Gender gender) {
        return userRepository.save(User.signUpWithEmail(name + "@example.com", "hash", name, name, gender));
    }

    private Gathering gathering(Instant startAt, int capacity) {
        return gathering(startAt, capacity, GenderRule.ANY);
    }

    /** API 로는 지난 데드라인 등으로 개설할 수 없어 직접 저장한다. 호스트 확정 레코드도 만든다. */
    private Gathering gathering(Instant startAt, int capacity, GenderRule genderRule) {
        Place place = placeRepository.save(Place.pinned("버디짐", null, "서울 강남구 역삼동 1", "1168010100",
                new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(127.0, 37.5))));
        Gathering gathering = gatheringRepository.save(Gathering.open(host, place, "같이 운동해요", null, null,
                startAt, Duration.ofMinutes(60), (short) capacity, genderRule));
        participationRepository.save(Participation.hostOf(gathering, NOW));
        return gathering;
    }

    private ResultActions apply(User user, Gathering gathering, String body) throws Exception {
        return mockMvc.perform(post("/api/gatherings/{id}/applications", gathering.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.issueAccessToken(user.getId()).value();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
