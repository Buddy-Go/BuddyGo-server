package com.buddygo.buddygoserver.domain.review;

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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 모임은 2시간 전에 끝났다(종료 = NOW - 2h, 평가 기한 = 종료 + 3일). */
@Transactional
class ReviewIntegrationTest extends IntegrationTest {

    static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");
    static final Instant START = NOW.minus(Duration.ofHours(3));
    static final Instant END = START.plus(Duration.ofHours(1));

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
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired EntityManager entityManager;

    User host;
    User member;
    User noShow;
    User outsider;
    Gathering gathering;

    @BeforeEach
    void setUp() {
        host = user("rv-host", "호스트");
        member = user("rv-member", "지각한사람");
        noShow = user("rv-noshow", "노쇼한사람");
        outsider = user("rv-outsider", "외부인");
        gathering = gathering(GatheringStatus.FINISHED, START);
        checkIn(participationRepository.findActive(gathering.getId(), host.getId()).orElseThrow(), Attendance.ON_TIME);
        checkIn(confirmed(gathering, member), Attendance.LATE);
        checkIn(confirmed(gathering, noShow), Attendance.NO_SHOW);
        flushAndClear();
    }

    @Nested
    class 제출 {

        @Test
        void 평가를_저장하고_곧바로_상대_매너온도에_반영한다() throws Exception {
            submit(member, gathering.getId(), host.getId(), "GOOD", "[\"PUNCTUAL\", \"GOOD_SPOTTER\"]", "  덕분에 즐거웠어요  ")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.reviewId").exists())
                    .andExpect(jsonPath("$.data.targetId").value(host.getId()))
                    .andExpect(jsonPath("$.data.rating").value("GOOD"))
                    .andExpect(jsonPath("$.data.tags").value(contains("PUNCTUAL", "GOOD_SPOTTER")))
                    .andExpect(jsonPath("$.data.comment").value("덕분에 즐거웠어요"));

            // 기준점(36.5)에서는 점수 그대로: 0.5 + 0.1 × 2
            assertThat(exactTemperature(host)).isEqualByComparingTo("37.20");
            assertThat(count("SELECT count(*) FROM review_tags t JOIN reviews r ON r.id = t.review_id "
                    + "WHERE r.target_id = ?", host.getId())).isEqualTo(2);
            assertThat(count("SELECT count(*) FROM manner_temperature_logs WHERE user_id = ? AND reason = 'REVIEW' "
                    + "AND delta = 0.70 AND review_id IS NOT NULL", host.getId())).isEqualTo(1);
        }

        @Test
        void 판정과_다른_성격의_태그도_고를_수_있고_방향은_판정이_정한다() throws Exception {
            // 좋음 0.5 + 긍정 1개 0.1 - 부정 2개 0.2 = 0.4
            submit(member, gathering.getId(), host.getId(), "GOOD", "[\"KIND\", \"NOT_PUNCTUAL\", \"ON_PHONE\"]", null)
                    .andExpect(status().isCreated());
            assertThat(exactTemperature(host)).isEqualByComparingTo("36.90");

            // 아쉬움에 긍정 태그를 최대로 골라도 올라가지 않는다: -0.5 + 0.1 × 5 = 0
            submit(host, gathering.getId(), member.getId(), "BAD",
                    "[\"PUNCTUAL\", \"GOOD_MANNERS\", \"KIND\", \"FUN_ATMOSPHERE\", \"WANT_AGAIN\"]",
                    null).andExpect(status().isCreated());
            assertThat(exactTemperature(member)).isEqualByComparingTo("36.50");
        }

        @Test
        void 보통은_태그대로_오르내린다() throws Exception {
            // 보통 0 - 부정 2개 0.2 = -0.2
            submit(member, gathering.getId(), host.getId(), "NORMAL", "[\"ON_PHONE\", \"UNCOMFORTABLE_TONE\"]", null)
                    .andExpect(status().isCreated());
            assertThat(exactTemperature(host)).isEqualByComparingTo("36.30");
        }

        @Test
        void 높은_온도에서는_덜_오르고_응답에는_소수_1자리로_보인다() throws Exception {
            setTemperature(host, "90.00");
            // (0.5 + 0.1 × 3) × (100 - 90) / 63.5 = 0.126 → 0.13
            submit(member, gathering.getId(), host.getId(), "GOOD", "[\"KIND\", \"PUNCTUAL\", \"WANT_AGAIN\"]", null)
                    .andExpect(status().isCreated());

            assertThat(exactTemperature(host)).isEqualByComparingTo("90.13");
            mockMvc.perform(get("/api/gatherings/{id}", gathering.getId()).header(HttpHeaders.AUTHORIZATION, bearer(member)))
                    .andExpect(jsonPath("$.data.host.mannerTemperature").value(90.1));
        }

        @Test
        void 끝났지만_종료_처리가_늦어_아직_진행중인_모임도_평가할_수_있다() throws Exception {
            setStatus(GatheringStatus.IN_PROGRESS);
            submit(member, gathering.getId(), host.getId(), "NORMAL", "[\"KIND\", \"ON_PHONE\"]", null)
                    .andExpect(status().isCreated());

            // 보통에 긍정·부정 태그가 하나씩이면 점수 0: 온도는 그대로지만 평가마다 이력은 하나씩 남는다
            assertThat(exactTemperature(host)).isEqualByComparingTo("36.50");
            assertThat(count("SELECT count(*) FROM manner_temperature_logs WHERE user_id = ? AND reason = 'REVIEW' "
                    + "AND delta = 0", host.getId())).isEqualTo(1);
        }

        @Test
        void 같은_사람을_두_번_평가하면_R002() throws Exception {
            submit(member, gathering.getId(), host.getId(), "GOOD", "[\"KIND\"]", null).andExpect(status().isCreated());
            submit(member, gathering.getId(), host.getId(), "BAD", "[\"ON_PHONE\"]", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("R002"));
        }

        @Test
        void 본인_노쇼한_사람_체크인하지_않은_사람은_평가할_수_없다_R004() throws Exception {
            for (User target : new User[]{member, noShow, outsider}) {
                submit(member, gathering.getId(), target.getId(), "GOOD", "[\"KIND\"]", null)
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("R004"));
            }
        }

        @Test
        void 노쇼했거나_참여하지_않은_사람은_평가할_수_없다_R001() throws Exception {
            for (User evaluator : new User[]{noShow, outsider}) {
                submit(evaluator, gathering.getId(), host.getId(), "GOOD", "[\"KIND\"]", null)
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("R001"));
            }
        }

        @ParameterizedTest
        @EnumSource(value = GatheringStatus.class, names = {"CLOSED", "CANCELED"})
        void 진행되지_않았거나_취소된_모임은_R001(GatheringStatus status) throws Exception {
            setStatus(status);
            submit(member, gathering.getId(), host.getId(), "GOOD", "[\"KIND\"]", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("R001"));
        }

        @Test
        void 아직_끝나지_않은_모임은_R001() throws Exception {
            mutableClock.fix(END.minusSeconds(1));
            setStatus(GatheringStatus.IN_PROGRESS);
            submit(member, gathering.getId(), host.getId(), "GOOD", "[\"KIND\"]", null)
                    .andExpect(jsonPath("$.code").value("R001"));
        }

        @Test
        void 종료_후_3일이_되는_순간부터는_R003() throws Exception {
            mutableClock.fix(END.plus(Duration.ofDays(3)).minusSeconds(1));
            submit(member, gathering.getId(), host.getId(), "GOOD", "[\"KIND\"]", null).andExpect(status().isCreated());

            mutableClock.fix(END.plus(Duration.ofDays(3)));
            submit(host, gathering.getId(), member.getId(), "GOOD", "[\"KIND\"]", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("R003"));
        }

        @Test
        void 없는_모임은_GA001() throws Exception {
            submit(member, Long.MAX_VALUE, host.getId(), "GOOD", "[\"KIND\"]", null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("GA001"));
        }

        @Test
        void 판정이_없거나_한마디가_200자를_넘으면_G002_없는_태그면_G003() throws Exception {
            submit(member, gathering.getId(), host.getId(), null, "[\"KIND\"]", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("G002"));
            submit(member, gathering.getId(), host.getId(), "GOOD", "[\"KIND\"]", "가".repeat(201))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("G002"));
            submit(member, gathering.getId(), host.getId(), "GOOD", "[\"UNKNOWN_TAG\"]", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("G003"));
        }

        @Test
        void 태그가_없거나_6개_이상이면_G002_5개까지는_된다() throws Exception {
            submit(member, gathering.getId(), host.getId(), "GOOD", null, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("G002"));
            submit(member, gathering.getId(), host.getId(), "GOOD", "[]", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("G002"));
            submit(member, gathering.getId(), host.getId(), "GOOD",
                    "[\"PUNCTUAL\", \"GOOD_MANNERS\", \"KIND\", \"FUN_ATMOSPHERE\", \"WANT_AGAIN\", \"ON_PHONE\"]", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("G002"));
            assertThat(count("SELECT count(*) FROM reviews WHERE gathering_id = ?", gathering.getId())).isZero();

            submit(member, gathering.getId(), host.getId(), "GOOD",
                    "[\"PUNCTUAL\", \"GOOD_MANNERS\", \"KIND\", \"FUN_ATMOSPHERE\", \"WANT_AGAIN\"]", null)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.tags", hasSize(5)));
        }

        @Test
        void 이력의_합은_현재_온도와_같다() throws Exception {
            setTemperature(host, "70.00");
            submit(member, gathering.getId(), host.getId(), "GOOD", "[\"KIND\"]", null);
            User third = user("rv-third", "세번째");
            checkIn(confirmed(gatheringRepository.findById(gathering.getId()).orElseThrow(), third), Attendance.ON_TIME);
            flushAndClear();
            submit(third, gathering.getId(), host.getId(), "BAD", "[\"ON_PHONE\"]", null);

            BigDecimal logged = entityManager.createQuery(
                            "select sum(l.delta) from MannerTemperatureLog l where l.user.id = :id", BigDecimal.class)
                    .setParameter("id", host.getId()).getSingleResult();
            assertThat(new BigDecimal("70.00").add(logged)).isEqualByComparingTo(exactTemperature(host));
        }
    }

    @Nested
    class 대상_조회 {

        @Test
        void 나를_뺀_체크인한_사람과_평가_여부_기한을_준다() throws Exception {
            submit(member, gathering.getId(), host.getId(), "GOOD", "[\"KIND\"]", null);

            targets(member, gathering.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.deadlineAt").value("2026-11-12T22:00:00Z"))
                    .andExpect(jsonPath("$.data.reviewable").value(true))
                    .andExpect(jsonPath("$.data.targets[*].userId").value(contains(host.getId().intValue())))
                    .andExpect(jsonPath("$.data.targets[0].host").value(true))
                    .andExpect(jsonPath("$.data.targets[0].reviewed").value(true));
            targets(host, gathering.getId())
                    .andExpect(jsonPath("$.data.targets[*].nickname").value(contains("지각한사람")))
                    .andExpect(jsonPath("$.data.targets[0].reviewed").value(false));
        }

        @Test
        void 기한이_지나도_볼_수_있고_reviewable_이_false_다() throws Exception {
            mutableClock.fix(END.plus(Duration.ofDays(4)));
            targets(member, gathering.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reviewable").value(false));
        }

        @Test
        void 노쇼한_사람은_R001() throws Exception {
            targets(noShow, gathering.getId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("R001"));
        }
    }

    @Nested
    class 대기_목록 {

        @Test
        void 평가할_사람이_남은_기한_전의_모임만_기한이_가까운_순으로_준다() throws Exception {
            // 하루 전에 끝난 모임: 기한이 더 가깝다
            Gathering older = gathering(GatheringStatus.FINISHED, START.minus(Duration.ofDays(1)));
            checkIn(participationRepository.findActive(older.getId(), host.getId()).orElseThrow(), Attendance.ON_TIME);
            checkIn(confirmed(older, member), Attendance.ON_TIME);
            // 기한이 지난 모임
            Gathering expired = gathering(GatheringStatus.FINISHED, START.minus(Duration.ofDays(4)));
            checkIn(participationRepository.findActive(expired.getId(), host.getId()).orElseThrow(), Attendance.ON_TIME);
            checkIn(confirmed(expired, member), Attendance.ON_TIME);
            flushAndClear();

            pending(member)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.gatherings[*].gatheringId")
                            .value(contains(older.getId().intValue(), gathering.getId().intValue())))
                    .andExpect(jsonPath("$.data.gatherings[1].remainingCount").value(1))
                    .andExpect(jsonPath("$.data.gatherings[1].deadlineAt").value("2026-11-12T22:00:00Z"));

            submit(member, gathering.getId(), host.getId(), "GOOD", "[\"KIND\"]", null);
            pending(member)
                    .andExpect(jsonPath("$.data.gatherings[*].gatheringId").value(contains(older.getId().intValue())));
        }

        @Test
        void 노쇼한_사람에게는_대기_목록이_없다() throws Exception {
            pending(noShow).andExpect(jsonPath("$.data.gatherings", empty()));
        }
    }

    @Test
    void 태그_목록은_긍정_10개_부정_8개다() throws Exception {
        mockMvc.perform(get("/api/review-tags").header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.positive.length()").value(10))
                .andExpect(jsonPath("$.data.negative.length()").value(8))
                .andExpect(jsonPath("$.data.positive[0].code").value("PUNCTUAL"))
                .andExpect(jsonPath("$.data.positive[0].label").value("시간 약속을 잘 지켜요"));
    }

    private ResultActions submit(User evaluator, long gatheringId, Long targetId, String rating, String tags,
                                 String comment) throws Exception {
        String body = "{\"targetId\": " + targetId
                + (rating == null ? "" : ", \"rating\": \"" + rating + "\"")
                + (tags == null ? "" : ", \"tags\": " + tags)
                + (comment == null ? "" : ", \"comment\": \"" + comment + "\"") + "}";
        ResultActions result = mockMvc.perform(post("/api/gatherings/{id}/reviews", gatheringId)
                .header(HttpHeaders.AUTHORIZATION, bearer(evaluator))
                .contentType(MediaType.APPLICATION_JSON).content(body));
        flushAndClear();
        return result;
    }

    private ResultActions targets(User user, long gatheringId) throws Exception {
        return mockMvc.perform(get("/api/gatherings/{id}/review-targets", gatheringId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions pending(User user) throws Exception {
        return mockMvc.perform(get("/api/reviews/pending").header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.issueAccessToken(user.getId()).value();
    }

    private User user(String key, String nickname) {
        return userRepository.save(User.signUpWithEmail(key + "@example.com", "hash", "이름", nickname, Gender.MALE));
    }

    private Gathering gathering(GatheringStatus status, Instant startAt) {
        Place place = placeRepository.save(Place.pinned("버디짐", null, "서울 강남구 역삼동 1", "1168010100",
                new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(127.0, 37.5))));
        Gathering saved = gatheringRepository.save(Gathering.builder()
                .host(host).place(place).title("같이 운동해요")
                .startAt(startAt).endAt(startAt.plus(Duration.ofHours(1)))
                .capacity((short) 3).genderRule(GenderRule.ANY).status(status)
                .build());
        participationRepository.save(Participation.hostOf(saved, startAt.minus(Duration.ofDays(1))));
        return saved;
    }

    private Participation confirmed(Gathering gathering, User user) {
        return participationRepository.save(Participation.builder()
                .gathering(gathering).user(user).status(ParticipationStatus.CONFIRMED)
                .appliedAt(NOW).decidedAt(NOW).build());
    }

    private void checkIn(Participation participation, Attendance attendance) {
        checkInRepository.save(CheckIn.builder().participation(participation).attendance(attendance).build());
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

    private long count(String sql, Object param) {
        return ((Number) entityManager.createNativeQuery(sql).setParameter(1, param).getSingleResult()).longValue();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
