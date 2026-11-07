package com.buddygo.buddygoserver.domain.application;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import com.buddygo.buddygoserver.domain.application.enums.RejectReason;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.entity.GatheringCancellation;
import com.buddygo.buddygoserver.domain.gathering.entity.Place;
import com.buddygo.buddygoserver.domain.gathering.enums.CancellationType;
import com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus;
import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringCancellationRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.PlaceRepository;
import com.buddygo.buddygoserver.domain.sport.gym.entity.GatheringGymPart;
import com.buddygo.buddygoserver.domain.sport.gym.entity.UserGymRecord;
import com.buddygo.buddygoserver.domain.sport.gym.enums.BodyPart;
import com.buddygo.buddygoserver.domain.sport.gym.repository.GatheringGymPartRepository;
import com.buddygo.buddygoserver.domain.sport.gym.repository.UserGymRecordRepository;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 로컬 PostgreSQL(docker-compose.local.yml)이 떠 있어야 한다. DB 는 테스트마다 롤백되고 현재 시각은 NOW 로 고정된다. */
@Transactional
class ApplicationIntegrationTest extends IntegrationTest {

    static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

    @Override
    protected Instant fixedNow() {
        return NOW;
    }
    /** 데드라인(시작 - 90분) = NOW + 90분 */
    static final Instant START = NOW.plus(Duration.ofHours(3));

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired GatheringRepository gatheringRepository;
    @Autowired GatheringCancellationRepository gatheringCancellationRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired ParticipationRepository participationRepository;
    @Autowired GatheringGymPartRepository gatheringGymPartRepository;
    @Autowired UserGymRecordRepository userGymRecordRepository;
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
        void 헬스_수준을_보내면_신청에_저장되고_보낸_부위는_프로필에도_반영된다() throws Exception {
            Gathering gathering = gathering(START, 3);
            gymParts(gathering, BodyPart.CHEST, BodyPart.BACK, BodyPart.ARM);
            profile(alice, BodyPart.CHEST, 60);
            profile(alice, BodyPart.LEGS, 100);

            apply(alice, gathering, """
                    {"gymRecords": [{"bodyPart": "CHEST", "recordValue": 80},
                                    {"bodyPart": "ARM", "armLevel": "INTERMEDIATE"}]}""")
                    .andExpect(status().isCreated());
            flushAndClear();

            applicants(host, gathering)
                    .andExpect(jsonPath("$.data.pending[0].gymRecords[*].bodyPart")
                            .value(contains("CHEST", "BACK", "ARM")))
                    .andExpect(jsonPath("$.data.pending[0].gymRecords[0].recordValue").value(80))
                    .andExpect(jsonPath("$.data.pending[0].gymRecords[1].recorded").value(false))
                    .andExpect(jsonPath("$.data.pending[0].gymRecords[2].armLevel").value("INTERMEDIATE"));
            assertThat(profileOf(alice)).containsEntry(BodyPart.CHEST, "80")
                    .containsEntry(BodyPart.ARM, "INTERMEDIATE")
                    .containsEntry(BodyPart.LEGS, "100"); // 보내지 않은 프로필 부위는 그대로
        }

        @Test
        void 헬스_수준을_생략하면_지금_프로필의_모임_부위를_쓰고_프로필은_그대로다() throws Exception {
            Gathering gathering = gathering(START, 3);
            gymParts(gathering, BodyPart.CHEST);
            profile(alice, BodyPart.CHEST, 60);
            profile(alice, BodyPart.LEGS, 100);

            apply(alice, gathering, "{}").andExpect(status().isCreated());
            flushAndClear();

            applicants(host, gathering)
                    .andExpect(jsonPath("$.data.pending[0].gymRecords.length()").value(1))
                    .andExpect(jsonPath("$.data.pending[0].gymRecords[0].recordValue").value(60));
            assertThat(profileOf(alice)).hasSize(2).containsEntry(BodyPart.CHEST, "60");
        }

        @Test
        void 모임_부위가_아닌_기록을_보내면_AP009() throws Exception {
            Gathering gathering = gathering(START, 3);
            gymParts(gathering, BodyPart.CHEST);

            apply(alice, gathering, """
                    {"gymRecords": [{"bodyPart": "LEGS", "recordValue": 100}]}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("AP009"));
        }

        @Test
        void 부위에_맞지_않는_기록이나_같은_부위_중복은_400() throws Exception {
            Gathering gathering = gathering(START, 3);
            gymParts(gathering, BodyPart.CHEST, BodyPart.ARM);

            apply(alice, gathering, """
                    {"gymRecords": [{"bodyPart": "ARM", "recordValue": 3}]}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors['gymRecords[0].recordValue']").exists());
            apply(alice, gathering, """
                    {"gymRecords": [{"bodyPart": "CHEST", "recordValue": 80}, {"bodyPart": "CHEST", "recordValue": 90}]}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.gymRecords").exists());
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

    @Nested
    class 신청_취소 {

        @Test
        void 대기_중인_신청을_취소하면_신청과_스냅샷이_지워지고_다시_신청할_수_있다() throws Exception {
            Gathering gathering = gathering(START, 3);
            gymParts(gathering, BodyPart.CHEST);
            apply(alice, gathering, """
                    {"gymRecords": [{"bodyPart": "CHEST", "recordValue": 80}]}""").andExpect(status().isCreated());
            flushAndClear();
            long applicationId = participationRepository.findActive(gathering.getId(), alice.getId()).orElseThrow().getId();
            assertThat(snapshotCount(applicationId)).isEqualTo(1);

            cancelMine(alice, gathering).andExpect(status().isOk());
            flushAndClear();
            assertThat(participationRepository.findById(applicationId)).isEmpty();
            assertThat(snapshotCount(applicationId)).isZero();

            apply(alice, gathering, "{}").andExpect(status().isCreated());
        }

        private long snapshotCount(long applicationId) {
            return ((Number) entityManager.createNativeQuery(
                            "SELECT count(*) FROM participation_gym_records WHERE participation_id = ?1")
                    .setParameter(1, applicationId).getSingleResult()).longValue();
        }

        @Test
        void 신청이_없으면_AP001() throws Exception {
            cancelMine(alice, gathering(START, 3))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("AP001"));
        }

        @Test
        void 확정된_뒤에는_AP005() throws Exception {
            Gathering gathering = gathering(START, 3);
            confirmed(gathering, alice);

            cancelMine(alice, gathering)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("AP005"));
        }
    }

    @Nested
    class 참가자_관리 {

        @Test
        void 호스트는_확정_명단과_대기_신청을_기록과_함께_본다() throws Exception {
            Gathering gathering = gathering(START, 3);
            gymParts(gathering, BodyPart.CHEST, BodyPart.ARM);
            confirmed(gathering, alice);
            userGymRecordRepository.save(UserGymRecord.of(bob, BodyPart.CHEST, new BigDecimal("80"), null));
            userGymRecordRepository.save(UserGymRecord.of(bob, BodyPart.LEGS, new BigDecimal("100"), null));
            apply(bob, gathering, "{\"message\": \"첫 번째\"}").andExpect(status().isCreated());
            apply(carol, gathering, "{}").andExpect(status().isCreated());

            applicants(host, gathering)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.confirmed.length()").value(2))
                    .andExpect(jsonPath("$.data.confirmed[0].host").value(true))
                    .andExpect(jsonPath("$.data.confirmed[1].userId").value(alice.getId()))
                    .andExpect(jsonPath("$.data.pending[0].nickname").value("bob"))
                    .andExpect(jsonPath("$.data.pending[0].message").value("첫 번째"))
                    .andExpect(jsonPath("$.data.pending[0].mannerTemperature").value(36.5))
                    // 모임 부위(가슴·팔)만, 기록 없는 팔은 미설정
                    .andExpect(jsonPath("$.data.pending[0].gymRecords.length()").value(2))
                    .andExpect(jsonPath("$.data.pending[0].gymRecords[0].bodyPart").value("CHEST"))
                    .andExpect(jsonPath("$.data.pending[0].gymRecords[0].recordValue").value(80))
                    .andExpect(jsonPath("$.data.pending[0].gymRecords[1].bodyPart").value("ARM"))
                    .andExpect(jsonPath("$.data.pending[0].gymRecords[1].recorded").value(false))
                    .andExpect(jsonPath("$.data.pending[1].nickname").value("carol"))
                    .andExpect(jsonPath("$.data.pending[1].gymRecords[0].recorded").value(false));
        }

        @Test
        void 신청_후_프로필을_바꿔도_신청_시점_값을_보여준다() throws Exception {
            Gathering gathering = gathering(START, 3);
            gymParts(gathering, BodyPart.CHEST);
            apply(alice, gathering, """
                    {"gymRecords": [{"bodyPart": "CHEST", "recordValue": 80}]}""").andExpect(status().isCreated());
            userGymRecordRepository.findAllByIdUserId(alice.getId()).getFirst().update(new BigDecimal("120"), null);
            flushAndClear();

            applicants(host, gathering).andExpect(jsonPath("$.data.pending[0].gymRecords[0].recordValue").value(80));
        }

        @Test
        void 호스트가_아니면_GA002() throws Exception {
            applicants(alice, gathering(START, 3))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("GA002"));
        }
    }

    @Nested
    class 수락_거절 {

        @Test
        void 수락하면_확정되고_정원이_남으면_모집중이다() throws Exception {
            Gathering gathering = gathering(START, 3);
            long applicationId = appliedId(alice, gathering);
            appliedId(bob, gathering);

            accept(host, gathering, applicationId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.data.gatheringStatus").value("RECRUITING"));
            flushAndClear();

            assertThat(participationRepository.findById(applicationId).orElseThrow().getDecidedAt()).isEqualTo(NOW);
            assertThat(participationRepository.findActive(gathering.getId(), bob.getId()).orElseThrow().isPending())
                    .isTrue();
        }

        @Test
        void 마지막_자리를_수락하면_마감되고_남은_대기_신청은_자동_거절된다() throws Exception {
            Gathering gathering = gathering(START, 3);
            confirmed(gathering, alice);
            long bobId = appliedId(bob, gathering);
            long carolId = appliedId(carol, gathering);

            accept(host, gathering, bobId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.data.gatheringStatus").value("CLOSED"));
            flushAndClear();

            assertThat(gatheringRepository.findById(gathering.getId()).orElseThrow().getStatus())
                    .isEqualTo(GatheringStatus.CLOSED);
            assertThat(participationRepository.findById(bobId).orElseThrow().getStatus())
                    .isEqualTo(ParticipationStatus.CONFIRMED);
            Participation carolApplication = participationRepository.findById(carolId).orElseThrow();
            assertThat(carolApplication.getStatus()).isEqualTo(ParticipationStatus.REJECTED);
            assertThat(carolApplication.getRejectReason()).isEqualTo(RejectReason.MEETING_FULL);

            apply(carol, gathering, "{}").andExpect(jsonPath("$.code").value("GA003"));
        }

        @Test
        void 정원이_찬_모임의_신청을_수락하면_AP003() throws Exception {
            Gathering gathering = gathering(START, 2);
            long applicationId = appliedId(alice, gathering);
            confirmed(gathering, bob); // 마감 처리 없이 정원이 찬 상태

            accept(host, gathering, applicationId)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("AP003"));
        }

        @Test
        void 데드라인이_지나면_수락할_수_없다() throws Exception {
            Gathering gathering = gathering(NOW.plus(Duration.ofMinutes(91)), 3);
            long applicationId = appliedId(alice, gathering);
            setStartAt(gathering, NOW.plus(Duration.ofMinutes(90))); // 신청 후 데드라인 = NOW

            accept(host, gathering, applicationId)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("GA003"));
        }

        @Test
        void 거절하면_다시_신청할_수_있다() throws Exception {
            Gathering gathering = gathering(START, 3);
            long applicationId = appliedId(alice, gathering);

            reject(host, gathering, applicationId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("REJECTED"));
            flushAndClear();
            assertThat(participationRepository.findById(applicationId).orElseThrow().getRejectReason())
                    .isEqualTo(RejectReason.BY_HOST);

            apply(alice, gathering, "{}").andExpect(status().isCreated());
        }

        @Test
        void 다른_모임의_신청이면_AP001() throws Exception {
            Gathering gathering = gathering(START, 3);
            Gathering other = gathering(START, 3);
            long applicationId = appliedId(alice, other);

            accept(host, gathering, applicationId)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("AP001"));
        }

        @Test
        void 대기_중이_아니면_AP005() throws Exception {
            Gathering gathering = gathering(START, 3);
            long applicationId = appliedId(alice, gathering);
            reject(host, gathering, applicationId).andExpect(status().isOk());

            accept(host, gathering, applicationId)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("AP005"));
        }

        @Test
        void 호스트가_아니면_GA002() throws Exception {
            Gathering gathering = gathering(START, 3);
            long applicationId = appliedId(alice, gathering);

            accept(bob, gathering, applicationId)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("GA002"));
            reject(alice, gathering, applicationId)
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class 나가기 {

        @Test
        void 모집중에_나가면_확정_인원만_줄고_다시_신청할_수_있다() throws Exception {
            Gathering gathering = gathering(START, 3);
            confirmed(gathering, alice);

            leave(alice, gathering)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("LEFT"))
                    .andExpect(jsonPath("$.data.gatheringStatus").value("RECRUITING"));
            flushAndClear();
            assertThat(participationRepository.countConfirmedExcludingHost(gathering.getId())).isZero();

            apply(alice, gathering, "{}").andExpect(status().isCreated());
        }

        @Test
        void 데드라인_전에_마감된_모임에서_나가면_다시_모집한다() throws Exception {
            Gathering gathering = gathering(START, 2);
            long applicationId = appliedId(alice, gathering);
            accept(host, gathering, applicationId).andExpect(jsonPath("$.data.gatheringStatus").value("CLOSED"));

            leave(alice, gathering)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.gatheringStatus").value("RECRUITING"));
            flushAndClear();
            assertThat(participationRepository.findById(applicationId).orElseThrow().getLeftAt()).isEqualTo(NOW);

            apply(bob, gathering, "{}").andExpect(status().isCreated());
        }

        @Test
        void 데드라인_후에_마감된_모임에서_나가면_모임이_취소된다() throws Exception {
            Gathering gathering = gathering(START, 2);
            long applicationId = appliedId(alice, gathering);
            accept(host, gathering, applicationId).andExpect(status().isOk());
            setStartAt(gathering, NOW.plus(Duration.ofMinutes(90))); // 데드라인 = NOW

            leave(alice, gathering)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.gatheringStatus").value("CANCELED"));
            flushAndClear();

            assertThat(gatheringRepository.findById(gathering.getId()).orElseThrow().getStatus())
                    .isEqualTo(GatheringStatus.CANCELED);
            GatheringCancellation cancellation = gatheringCancellationRepository.findById(gathering.getId()).orElseThrow();
            assertThat(cancellation.getType()).isEqualTo(CancellationType.MEMBER_LEFT);
            assertThat(cancellation.getReason()).isNull();
        }

        @Test
        void 호스트는_나갈_수_없다() throws Exception {
            leave(host, gathering(START, 3))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("AP008"));
        }

        @Test
        void 대기_신청자는_나가기가_아니라_신청_취소를_쓴다() throws Exception {
            Gathering gathering = gathering(START, 3);
            appliedId(alice, gathering);

            leave(alice, gathering)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AP007"));
        }

        @Test
        void 시작된_모임에서는_나갈_수_없다() throws Exception {
            Gathering gathering = gathering(NOW.minus(Duration.ofMinutes(1)), 3);
            confirmed(gathering, alice);

            leave(alice, gathering)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("GA008"));
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

    private ResultActions cancelMine(User user, Gathering gathering) throws Exception {
        return mockMvc.perform(delete("/api/gatherings/{id}/applications/me", gathering.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    /** 수락 API 를 거치지 않고 확정 참가자를 만든다. */
    private Participation confirmed(Gathering gathering, User user) {
        return participationRepository.save(Participation.builder()
                .gathering(gathering).user(user).status(ParticipationStatus.CONFIRMED)
                .appliedAt(NOW).decidedAt(NOW).build());
    }

    private ResultActions applicants(User user, Gathering gathering) throws Exception {
        return mockMvc.perform(get("/api/gatherings/{id}/applications", gathering.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private void gymParts(Gathering gathering, BodyPart... parts) {
        for (BodyPart part : parts) {
            gatheringGymPartRepository.save(GatheringGymPart.of(gathering, part, null, null));
        }
    }

    private long appliedId(User user, Gathering gathering) throws Exception {
        String json = apply(user, gathering, "{}").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.data.applicationId")).longValue();
    }

    private ResultActions accept(User user, Gathering gathering, long applicationId) throws Exception {
        return mockMvc.perform(post("/api/gatherings/{id}/applications/{applicationId}/accept",
                gathering.getId(), applicationId).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions reject(User user, Gathering gathering, long applicationId) throws Exception {
        return mockMvc.perform(post("/api/gatherings/{id}/applications/{applicationId}/reject",
                gathering.getId(), applicationId).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions leave(User user, Gathering gathering) throws Exception {
        return mockMvc.perform(post("/api/gatherings/{id}/leave", gathering.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private void setStartAt(Gathering gathering, Instant startAt) {
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE gatherings SET start_at = :startAt, end_at = :endAt WHERE id = :id")
                .setParameter("startAt", startAt).setParameter("endAt", startAt.plus(Duration.ofHours(1)))
                .setParameter("id", gathering.getId()).executeUpdate();
        entityManager.clear();
    }

    private void profile(User user, BodyPart part, int value) {
        userGymRecordRepository.save(UserGymRecord.of(user, part, BigDecimal.valueOf(value), null));
    }

    /** 부위 → 값(kg·횟수 또는 숙련도) */
    private Map<BodyPart, String> profileOf(User user) {
        return userGymRecordRepository.findAllByIdUserId(user.getId()).stream().collect(Collectors.toMap(
                UserGymRecord::getBodyPart,
                r -> r.getArmLevel() != null ? r.getArmLevel().name() : r.getRecordValue().stripTrailingZeros().toPlainString()));
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.issueAccessToken(user.getId()).value();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
