package com.buddygo.buddygoserver.domain.gathering;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import com.buddygo.buddygoserver.domain.application.enums.RejectReason;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.entity.Place;
import com.buddygo.buddygoserver.domain.gathering.enums.CancellationType;
import com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus;
import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringCancellationRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.PlaceRepository;
import com.buddygo.buddygoserver.domain.review.entity.Review;
import com.buddygo.buddygoserver.domain.review.enums.ReviewRating;
import com.buddygo.buddygoserver.domain.review.repository.ReviewRepository;
import com.buddygo.buddygoserver.domain.sport.gym.entity.UserGymRecord;
import com.buddygo.buddygoserver.domain.sport.gym.enums.ArmLevel;
import com.buddygo.buddygoserver.domain.sport.gym.enums.BodyPart;
import com.buddygo.buddygoserver.domain.sport.gym.repository.UserGymRecordRepository;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.enums.Gender;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.security.jwt.JwtTokenProvider;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 로컬 PostgreSQL(docker-compose.local.yml)이 떠 있어야 한다. DB 는 테스트마다 롤백되고 현재 시각은 NOW 로 고정된다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(GatheringIntegrationTest.FixedClockConfig.class) // @Nested 클래스에도 적용되도록 명시한다
@Transactional
class GatheringIntegrationTest {

    static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");
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
    @Autowired UserGymRecordRepository userGymRecordRepository;
    @Autowired GatheringRepository gatheringRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired ParticipationRepository participationRepository;
    @Autowired GatheringCancellationRepository gatheringCancellationRepository;
    @Autowired ReviewRepository reviewRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired EntityManager entityManager;

    User host;
    User member;
    User other;

    @BeforeEach
    void setUp() {
        host = userRepository.save(User.signUpWithEmail("host@example.com", "hash", "김호스트", "호스트", Gender.MALE));
        member = userRepository.save(User.signUpWithEmail("member@example.com", "hash", "이멤버", "멤버", Gender.MALE));
        other = userRepository.save(User.signUpWithEmail("other@example.com", "hash", "박다른", "다른사람", Gender.FEMALE));
    }

    @Nested
    class 개설 {

        @Test
        void 개설하면_호스트가_확정_참가자가_되고_기록은_범위로_보인다() throws Exception {
            record(host, BodyPart.CHEST, 80);
            record(host, BodyPart.BACK, 1);
            userGymRecordRepository.save(UserGymRecord.of(host, BodyPart.ARM, null, ArmLevel.INTERMEDIATE));

            String json = create(host, body("27484196", "[\"LEGS\", \"CHEST\", \"BACK\", \"ARM\"]", true))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value(201))
                    .andExpect(jsonPath("$.data.status").value("RECRUITING"))
                    .andExpect(jsonPath("$.data.startAt").value("2026-11-10T03:00:00Z"))
                    .andExpect(jsonPath("$.data.endAt").value("2026-11-10T04:30:00Z"))
                    .andExpect(jsonPath("$.data.deadlineAt").value("2026-11-10T01:30:00Z"))
                    .andExpect(jsonPath("$.data.durationMinutes").value(90))
                    .andExpect(jsonPath("$.data.confirmedCount").value(1))
                    .andExpect(jsonPath("$.data.host.userId").value(host.getId()))
                    .andExpect(jsonPath("$.data.place.latitude").value(37.5009))
                    .andExpect(jsonPath("$.data.place.longitude").value(127.0364))
                    .andExpect(jsonPath("$.data.viewer.role").value("HOST"))
                    .andExpect(jsonPath("$.data.members[0].userId").value(host.getId()))
                    .andExpect(jsonPath("$.data.members[0].host").value(true))
                    .andExpect(jsonPath("$.data.gym.parts[*].bodyPart").value(contains("CHEST", "BACK", "LEGS", "ARM")))
                    .andExpect(jsonPath("$.data.gym.parts[0].minValue").value(70))
                    .andExpect(jsonPath("$.data.gym.parts[0].maxValue").value(90))
                    .andExpect(jsonPath("$.data.gym.parts[1].minValue").value(0))
                    .andExpect(jsonPath("$.data.gym.parts[1].maxValue").value(3))
                    .andExpect(jsonPath("$.data.gym.parts[2].recordShown").value(false))
                    .andExpect(jsonPath("$.data.gym.parts[3].armLevel").value("INTERMEDIATE"))
                    .andExpect(jsonPath("$.data.gym.parts[0].comparison").doesNotExist())
                    .andExpect(jsonPath("$.data.gym.parts[0].myRecordValue").doesNotExist())
                    .andExpect(jsonPath("$.data.host.reviewCount").value(0))
                    .andExpect(jsonPath("$.data.gym.match").doesNotExist())
                    .andReturn().getResponse().getContentAsString();

            long id = ((Number) JsonPath.read(json, "$.data.gatheringId")).longValue();
            assertThat(participationRepository.countConfirmedExcludingHost(id)).isZero();
            assertThat(participationRepository.countByGatheringIdAndStatus(id, ParticipationStatus.CONFIRMED))
                    .isEqualTo(1);
        }

        @Test
        void 내_기록_표시를_끄면_기록은_복사되지_않는다() throws Exception {
            record(host, BodyPart.CHEST, 80);
            create(host, body(null, "[\"CHEST\"]", false))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.gym.parts[0].recordShown").value(false))
                    .andExpect(jsonPath("$.data.gym.parts[0].minValue").doesNotExist());
        }

        @Test
        void 개설_후_프로필을_바꿔도_모임의_기록은_그대로다() throws Exception {
            record(host, BodyPart.CHEST, 80);
            long id = createdId(host, body(null, "[\"CHEST\"]", true));
            userGymRecordRepository.findAllByIdUserId(host.getId()).getFirst().update(new BigDecimal("120"), null);
            flushAndClear();

            detail(other, id).andExpect(jsonPath("$.data.gym.parts[0].minValue").value(70));
        }

        @Test
        void 같은_카카오_장소는_재사용하고_집결지는_매번_새로_만든다() throws Exception {
            long placesBefore = placeRepository.count(); // 로컬 DB 에 남은 장소가 있어도 되도록 차이로 본다
            long first = createdId(host, body("27484196", "[\"CHEST\"]", false));
            long second = createdId(host, body("27484196", "[\"CHEST\"]", false));
            createdId(host, body(null, "[\"CHEST\"]", false));
            createdId(host, body(null, "[\"CHEST\"]", false));
            flushAndClear();

            assertThat(gatheringRepository.findById(first).orElseThrow().getPlace().getId())
                    .isEqualTo(gatheringRepository.findById(second).orElseThrow().getPlace().getId());
            assertThat(placeRepository.count() - placesBefore).isEqualTo(3);
        }

        @Test
        void 시작이_데드라인_이내면_GA010() throws Exception {
            create(host, body(null, "[\"CHEST\"]", false).replace("2026-11-10T03:00:00Z", "2026-11-10T01:30:00Z"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("GA010"));
            create(host, body(null, "[\"CHEST\"]", false).replace("2026-11-10T03:00:00Z", "2026-11-10T01:40:00Z"))
                    .andExpect(status().isCreated());
        }

        @ParameterizedTest
        @ValueSource(strings = {"2026-11-10T03:05:00Z", "2026-11-10T03:00:30Z", "2026-11-10T03:00:00.500Z"})
        void 시작_시각이_10분_단위_정각이_아니면_GA014(String startAt) throws Exception {
            create(host, body(null, "[\"CHEST\"]", false).replace("2026-11-10T03:00:00Z", startAt))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("GA014"));
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 4})
        void 정원이_범위_밖이면_GA011(int capacity) throws Exception {
            create(host, body(null, "[\"CHEST\"]", false).replace("\"capacity\": 3", "\"capacity\": " + capacity))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("GA011"));
        }

        @ParameterizedTest
        @ValueSource(ints = {20, 35, 250})
        void 진행_시간이_범위_밖이거나_10분_단위가_아니면_GA012(int minutes) throws Exception {
            create(host, body(null, "[\"CHEST\"]", false).replace("\"durationMinutes\": 90", "\"durationMinutes\": " + minutes))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("GA012"));
        }

        @Test
        void 호스트_성별과_다른_성별_조건은_GA013() throws Exception {
            create(host, body(null, "[\"CHEST\"]", false).replace("\"ANY\"", "\"FEMALE_ONLY\""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("GA013"));
        }

        @Test
        void 부위가_없거나_법정동_코드가_틀리면_400() throws Exception {
            create(host, body(null, "[]", false))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors['gym.parts']").exists());
            create(host, body(null, "[\"CHEST\", \"CHEST\"]", false))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors['gym.parts']").exists());
            create(host, body(null, "[\"CHEST\"]", false).replace("1168010100", "11680"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors['place.regionCode']").exists());
        }

        @Test
        void 토큰이_없으면_401() throws Exception {
            mockMvc.perform(post("/api/gatherings").contentType(MediaType.APPLICATION_JSON)
                            .content(body(null, "[\"CHEST\"]", false)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class 상세 {

        @Test
        void 그_외_사용자는_명단_없이_기준_일치_판정을_받는다() throws Exception {
            record(host, BodyPart.CHEST, 80);
            record(host, BodyPart.LEGS, 100);
            long id = createdId(host, body(null, "[\"CHEST\", \"LEGS\", \"ARM\"]", true));
            record(other, BodyPart.CHEST, 90);  // 70–90 안
            record(other, BodyPart.LEGS, 111);  // 90–110 밖

            detail(other, id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.gym.parts[0].myRecordValue").value(90))
                    .andExpect(jsonPath("$.data.gym.parts[0].comparison").value("WITHIN"))
                    .andExpect(jsonPath("$.data.gym.parts[1].myRecordValue").value(111))
                    .andExpect(jsonPath("$.data.gym.parts[1].comparison").value("ABOVE"))
                    .andExpect(jsonPath("$.data.gym.parts[2].myRecordValue").doesNotExist())
                    .andExpect(jsonPath("$.data.gym.parts[2].comparison").value("NO_MY_RECORD"))
                    .andExpect(jsonPath("$.data.viewer.role").value("NONE"))
                    .andExpect(jsonPath("$.data.viewer.applicationId").doesNotExist())
                    .andExpect(jsonPath("$.data.viewer.liked").value(false))
                    .andExpect(jsonPath("$.data.members").doesNotExist())
                    .andExpect(jsonPath("$.data.confirmedCount").value(1))
                    .andExpect(jsonPath("$.data.gym.match.result").value("PARTIAL"))
                    .andExpect(jsonPath("$.data.gym.match.mismatchedParts").value(contains("LEGS")))
                    .andExpect(jsonPath("$.data.gym.match.partsWithoutMyRecord").value(contains("ARM")));
        }

        @Test
        void 범위보다_낮거나_팔_숙련도가_다르면_맞지_않고_높낮이를_알려준다() throws Exception {
            record(host, BodyPart.BACK, 10);
            userGymRecordRepository.save(UserGymRecord.of(host, BodyPart.ARM, null, ArmLevel.INTERMEDIATE));
            long id = createdId(host, body(null, "[\"BACK\", \"ARM\"]", true));
            record(other, BodyPart.BACK, 7);  // 8–12 밖
            userGymRecordRepository.save(UserGymRecord.of(other, BodyPart.ARM, null, ArmLevel.ADVANCED));

            detail(other, id)
                    .andExpect(jsonPath("$.data.gym.parts[0].comparison").value("BELOW"))
                    .andExpect(jsonPath("$.data.gym.parts[1].myArmLevel").value("ADVANCED"))
                    .andExpect(jsonPath("$.data.gym.parts[1].comparison").value("ABOVE"))
                    .andExpect(jsonPath("$.data.gym.match.result").value("MISMATCH"));
        }

        @Test
        void 비교할_부위가_없으면_판정_결과는_null() throws Exception {
            long id = createdId(host, body(null, "[\"CHEST\"]", false));
            record(other, BodyPart.CHEST, 80);

            detail(other, id)
                    .andExpect(jsonPath("$.data.gym.parts[0].comparison").value("HOST_HIDDEN"))
                    .andExpect(jsonPath("$.data.gym.parts[0].myRecordValue").value(80))
                    .andExpect(jsonPath("$.data.gym.match.result").doesNotExist());
        }

        @Test
        void 호스트_카드에_받은_후기_수를_준다() throws Exception {
            long id = createdId(host, body(null, "[\"CHEST\"]", false));
            Gathering past = savedGathering(NOW.minus(Duration.ofDays(1)));
            reviewRepository.save(Review.builder().gathering(past).evaluator(member).target(host)
                    .rating(ReviewRating.GOOD).build());
            reviewRepository.save(Review.builder().gathering(past).evaluator(other).target(member)
                    .rating(ReviewRating.GOOD).build());

            detail(other, id).andExpect(jsonPath("$.data.host.reviewCount").value(1));
        }

        @Test
        void 대기_신청_수는_호스트에게만_준다() throws Exception {
            long id = createdId(host, body(null, "[\"CHEST\"]", false));
            participate(id, member, ParticipationStatus.CONFIRMED);
            participate(id, other, ParticipationStatus.PENDING);

            detail(host, id).andExpect(jsonPath("$.data.pendingCount").value(1));
            detail(other, id).andExpect(jsonPath("$.data.pendingCount").doesNotExist());
        }

        @Test
        void 대기_신청자는_APPLICANT_와_신청_ID_를_받는다() throws Exception {
            long id = createdId(host, body(null, "[\"CHEST\"]", false));
            Participation pending = participate(id, other, ParticipationStatus.PENDING);

            detail(other, id)
                    .andExpect(jsonPath("$.data.viewer.role").value("APPLICANT"))
                    .andExpect(jsonPath("$.data.viewer.applicationId").value(pending.getId()));
        }

        @Test
        void 확정_참가자는_마감된_뒤에만_명단을_본다() throws Exception {
            long id = createdId(host, body(null, "[\"CHEST\"]", false));
            participate(id, member, ParticipationStatus.CONFIRMED);

            detail(member, id)
                    .andExpect(jsonPath("$.data.viewer.role").value("PARTICIPANT"))
                    .andExpect(jsonPath("$.data.members").doesNotExist());

            setStatus(id, GatheringStatus.CLOSED);
            detail(member, id)
                    .andExpect(jsonPath("$.data.confirmedCount").value(2))
                    .andExpect(jsonPath("$.data.members.length()").value(2))
                    .andExpect(jsonPath("$.data.members[0].host").value(true));
        }

        @Test
        void 없는_모임은_GA001() throws Exception {
            detail(other, 999_999L)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("GA001"));
        }
    }

    @Nested
    class 수정 {

        @Test
        void 확정_참가자가_없으면_모든_항목을_바꿀_수_있다() throws Exception {
            record(host, BodyPart.CHEST, 80);
            long id = createdId(host, body(null, "[\"CHEST\"]", false));
            participate(id, other, ParticipationStatus.PENDING); // 대기 신청은 확정 참가자가 아니다

            update(host, id, """
                    {"title": "바뀐 제목", "description": "", "startAt": "2026-11-11T10:00:00Z",
                     "durationMinutes": 60, "capacity": 2, "genderRule": "MALE_ONLY",
                     "gym": {"parts": ["CHEST", "BACK"], "showMyRecord": true}}""")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value("바뀐 제목"))
                    .andExpect(jsonPath("$.data.description").doesNotExist())
                    .andExpect(jsonPath("$.data.endAt").value("2026-11-11T11:00:00Z"))
                    .andExpect(jsonPath("$.data.capacity").value(2))
                    .andExpect(jsonPath("$.data.genderRule").value("MALE_ONLY"))
                    .andExpect(jsonPath("$.data.gym.parts[*].bodyPart").value(contains("CHEST", "BACK")))
                    .andExpect(jsonPath("$.data.gym.parts[0].minValue").value(70))
                    .andExpect(jsonPath("$.data.gym.parts[1].recordShown").value(false));
        }

        @Test
        void 진행_시간만_바꾸면_시작은_그대로_두고_종료를_다시_계산한다() throws Exception {
            long id = createdId(host, body(null, "[\"CHEST\"]", false));
            update(host, id, "{\"durationMinutes\": 240}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.startAt").value("2026-11-10T03:00:00Z"))
                    .andExpect(jsonPath("$.data.endAt").value("2026-11-10T07:00:00Z"));
        }

        @Test
        void 장소를_바꿀_수_있다() throws Exception {
            long id = createdId(host, body(null, "[\"CHEST\"]", false));
            update(host, id, """
                    {"place": {"kakaoPlaceId": "999", "name": "새 헬스장", "jibunAddress": "서울 마포구 서교동 1",
                               "regionCode": "1144012000", "latitude": 37.55, "longitude": 126.92}}""")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.place.name").value("새 헬스장"))
                    .andExpect(jsonPath("$.data.place.regionCode").value("1144012000"));
        }

        @Test
        void 확정_참가자가_있으면_제목_설명_상세주소만_바꿀_수_있다() throws Exception {
            long id = createdId(host, body(null, "[\"CHEST\"]", false));
            participate(id, member, ParticipationStatus.CONFIRMED);

            update(host, id, "{\"title\": \"새 제목\", \"detailAddress\": \"4층\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value("새 제목"))
                    .andExpect(jsonPath("$.data.place.detailAddress").value("4층"));
            update(host, id, "{\"title\": \"새 제목\", \"capacity\": 2}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("GA005"));
        }

        @Test
        void 호스트가_아니면_GA002() throws Exception {
            long id = createdId(host, body(null, "[\"CHEST\"]", false));
            update(other, id, "{\"title\": \"남의 모임\"}")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("GA002"));
        }

        @Test
        void 시작_시각이_지난_모임은_GA008() throws Exception {
            long id = savedGathering(NOW.minus(Duration.ofMinutes(1))).getId();
            update(host, id, "{\"title\": \"늦은 수정\"}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("GA008"));
        }

        @Test
        void 제목을_공백으로_바꾸면_400() throws Exception {
            long id = createdId(host, body(null, "[\"CHEST\"]", false));
            update(host, id, "{\"title\": \"  \"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.title").exists());
        }
    }

    @Nested
    class 삭제 {

        @Test
        void 확정_참가자가_없으면_삭제되고_대기_신청은_자동_거절된다() throws Exception {
            long id = createdId(host, body(null, "[\"CHEST\"]", false));
            Participation pending = participate(id, other, ParticipationStatus.PENDING);

            deleteGathering(host, id).andExpect(status().isOk());
            flushAndClear();

            assertThat(gatheringRepository.findById(id).orElseThrow().getDeletedAt()).isEqualTo(NOW);
            Participation rejected = participationRepository.findById(pending.getId()).orElseThrow();
            assertThat(rejected.getStatus()).isEqualTo(ParticipationStatus.REJECTED);
            assertThat(rejected.getRejectReason()).isEqualTo(RejectReason.MEETING_DELETED);
            detail(host, id).andExpect(jsonPath("$.code").value("GA001"));
            deleteGathering(host, id).andExpect(jsonPath("$.code").value("GA001"));
        }

        @Test
        void 확정_참가자가_있으면_GA004() throws Exception {
            long id = createdId(host, body(null, "[\"CHEST\"]", false));
            participate(id, member, ParticipationStatus.CONFIRMED);

            deleteGathering(host, id)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("GA004"));
        }
    }

    @Nested
    class 취소 {

        @Test
        void 확정_참가자가_있으면_취소되고_사유는_참가자에게만_보인다() throws Exception {
            long id = createdId(host, body(null, "[\"CHEST\"]", false));
            participate(id, member, ParticipationStatus.CONFIRMED);
            Participation pending = participate(id, other, ParticipationStatus.PENDING);

            cancel(host, id, "{\"reason\": \"출장이 잡혔어요\"}").andExpect(status().isOk());
            flushAndClear();

            assertThat(gatheringRepository.findById(id).orElseThrow().getStatus()).isEqualTo(GatheringStatus.CANCELED);
            assertThat(gatheringCancellationRepository.findById(id).orElseThrow().getType())
                    .isEqualTo(CancellationType.HOST);
            assertThat(participationRepository.findById(pending.getId()).orElseThrow().getRejectReason())
                    .isEqualTo(RejectReason.MEETING_CANCELED);

            detail(member, id)
                    .andExpect(jsonPath("$.data.status").value("CANCELED"))
                    .andExpect(jsonPath("$.data.cancellation.reason").value("출장이 잡혔어요"))
                    .andExpect(jsonPath("$.data.cancellation.canceledAt").value("2026-11-10T00:00:00Z"));
            detail(other, id)
                    .andExpect(jsonPath("$.data.cancellation.type").value("HOST"))
                    .andExpect(jsonPath("$.data.cancellation.reason").doesNotExist());
        }

        @Test
        void 확정_참가자가_없으면_GA007() throws Exception {
            long id = createdId(host, body(null, "[\"CHEST\"]", false));
            cancel(host, id, "{\"reason\": \"사정이 생겼어요\"}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("GA007"));
        }

        @Test
        void 이미_취소된_모임은_GA009() throws Exception {
            long id = createdId(host, body(null, "[\"CHEST\"]", false));
            participate(id, member, ParticipationStatus.CONFIRMED);
            cancel(host, id, "{\"reason\": \"사정이 생겼어요\"}").andExpect(status().isOk());

            cancel(host, id, "{\"reason\": \"또 취소\"}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("GA009"));
            update(host, id, "{\"title\": \"취소 후 수정\"}").andExpect(jsonPath("$.code").value("GA009"));
        }

        @Test
        void 사유가_없거나_100자를_넘으면_400() throws Exception {
            long id = createdId(host, body(null, "[\"CHEST\"]", false));
            cancel(host, id, "{\"reason\": \" \"}").andExpect(status().isBadRequest());
            cancel(host, id, "{\"reason\": \"" + "가".repeat(101) + "\"}").andExpect(status().isBadRequest());
        }
    }

    // ───────── helpers ─────────

    /** 시작 = NOW + 3h, 90분, 정원 3, 누구나 */
    private static String body(String kakaoPlaceId, String parts, boolean showMyRecord) {
        String kakao = kakaoPlaceId == null ? "null" : "\"" + kakaoPlaceId + "\"";
        return """
                {"title": "저녁 가슴 같이 하실 분", "description": "벤치 위주", "startAt": "2026-11-10T03:00:00Z",
                 "durationMinutes": 90, "capacity": 3, "genderRule": "ANY", "detailAddress": "3층",
                 "place": {"kakaoPlaceId": %s, "name": "버디짐", "roadAddress": "서울 강남구 테헤란로 123",
                           "jibunAddress": "서울 강남구 역삼동 123-45", "regionCode": "1168010100",
                           "latitude": 37.5009, "longitude": 127.0364},
                 "gym": {"parts": %s, "showMyRecord": %s}}""".formatted(kakao, parts, showMyRecord);
    }

    private ResultActions create(User user, String body) throws Exception {
        return mockMvc.perform(post("/api/gatherings").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long createdId(User user, String body) throws Exception {
        String json = create(user, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.data.gatheringId")).longValue();
    }

    private ResultActions detail(User user, long id) throws Exception {
        return mockMvc.perform(get("/api/gatherings/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions update(User user, long id, String body) throws Exception {
        return mockMvc.perform(patch("/api/gatherings/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions deleteGathering(User user, long id) throws Exception {
        return mockMvc.perform(delete("/api/gatherings/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions cancel(User user, long id, String body) throws Exception {
        return mockMvc.perform(post("/api/gatherings/{id}/cancel", id).header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.issueAccessToken(user.getId()).value();
    }

    private void record(User user, BodyPart part, int value) {
        userGymRecordRepository.save(UserGymRecord.of(user, part, BigDecimal.valueOf(value), null));
    }

    private Participation participate(long gatheringId, User user, ParticipationStatus status) {
        return participationRepository.save(Participation.builder()
                .gathering(gatheringRepository.getReferenceById(gatheringId))
                .user(user)
                .status(status)
                .appliedAt(NOW)
                .decidedAt(status == ParticipationStatus.CONFIRMED ? NOW : null)
                .build());
    }

    private void setStatus(long gatheringId, GatheringStatus status) {
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE gatherings SET status = :status WHERE id = :id")
                .setParameter("status", status.name()).setParameter("id", gatheringId).executeUpdate();
        entityManager.clear();
    }

    /** API 로는 지난 시각에 개설할 수 없어 직접 저장한다. */
    private Gathering savedGathering(Instant startAt) {
        Place place = placeRepository.save(Place.pinned("집결지", null, "서울 강남구 역삼동 1", "1168010100",
                new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(127.0, 37.5))));
        Gathering gathering = gatheringRepository.save(Gathering.open(host, place, "지난 모임", null, null,
                startAt, Duration.ofMinutes(60), (short) 3, GenderRule.ANY));
        participationRepository.save(Participation.hostOf(gathering, NOW));
        return gathering;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
