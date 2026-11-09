package com.buddygo.buddygoserver.domain.profile;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.buddy.entity.Buddy;
import com.buddygo.buddygoserver.domain.buddy.entity.BuddyRequest;
import com.buddygo.buddygoserver.domain.buddy.repository.BuddyRepository;
import com.buddygo.buddygoserver.domain.buddy.repository.BuddyRequestRepository;
import com.buddygo.buddygoserver.domain.checkin.entity.CheckIn;
import com.buddygo.buddygoserver.domain.checkin.enums.Attendance;
import com.buddygo.buddygoserver.domain.checkin.repository.CheckInRepository;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.entity.Place;
import com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus;
import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.PlaceRepository;
import com.buddygo.buddygoserver.domain.review.entity.Review;
import com.buddygo.buddygoserver.domain.review.entity.ReviewTag;
import com.buddygo.buddygoserver.domain.review.enums.ReviewRating;
import com.buddygo.buddygoserver.domain.review.enums.ReviewTagType;
import com.buddygo.buddygoserver.domain.review.repository.ReviewRepository;
import com.buddygo.buddygoserver.domain.review.repository.ReviewTagRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 나(me)의 기록:
 * - 개설: g1(종료, 실제 진행), g3(취소, 세지 않음)
 * - 참여: g2(alice 개설, 지각 = 참여), g4(bob 개설, 노쇼 = 세지 않음). g1 출석은 내가 연 모임이라 참여에 안 센다.
 * - 받은 평가 4개: 좋음(alice, KIND·PUNCTUAL), 아쉬움(alice, PUNCTUAL·ON_PHONE), 보통(bob), 좋음(bob, KIND·GOOD_SPOTTER)
 */
@Transactional
class ProfileIntegrationTest extends IntegrationTest {

    static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");
    static final Instant PAST = NOW.minus(Duration.ofDays(1));

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
    @Autowired ReviewRepository reviewRepository;
    @Autowired ReviewTagRepository reviewTagRepository;
    @Autowired BuddyRepository buddyRepository;
    @Autowired BuddyRequestRepository buddyRequestRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired EntityManager entityManager;

    User me;
    User alice;
    User bob;
    User carol;

    @BeforeEach
    void setUp() {
        me = user("pf-me", "나");
        alice = user("pf-alice", "앨리스");
        bob = user("pf-bob", "밥");
        carol = user("pf-carol", "캐롤");

        Gathering g1 = gathering(me, GatheringStatus.FINISHED);
        checkIn(hostParticipation(g1), Attendance.ON_TIME);
        checkIn(confirmed(g1, alice), Attendance.ON_TIME);

        Gathering g2 = gathering(alice, GatheringStatus.FINISHED);
        checkIn(hostParticipation(g2), Attendance.ON_TIME);
        checkIn(confirmed(g2, me), Attendance.LATE);
        checkIn(confirmed(g2, bob), Attendance.ON_TIME);

        gathering(me, GatheringStatus.CANCELED);

        Gathering g4 = gathering(bob, GatheringStatus.FINISHED);
        checkIn(hostParticipation(g4), Attendance.ON_TIME);
        checkIn(confirmed(g4, me), Attendance.NO_SHOW);

        review(g1, alice, ReviewRating.GOOD, ReviewTagType.KIND, ReviewTagType.PUNCTUAL);
        review(g2, alice, ReviewRating.BAD, ReviewTagType.PUNCTUAL, ReviewTagType.ON_PHONE);
        review(g2, bob, ReviewRating.NORMAL);
        review(g1, bob, ReviewRating.GOOD, ReviewTagType.KIND, ReviewTagType.GOOD_SPOTTER);
        flushAndClear();
    }

    @Nested
    class 내_정보 {

        @Test
        void 기본_정보와_후기_수_개설_참여_횟수_많이_받은_태그를_준다() throws Exception {
            mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(me)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(me.getId()))
                    .andExpect(jsonPath("$.data.email").value("pf-me@example.com"))
                    .andExpect(jsonPath("$.data.pushEnabled").value(true))
                    .andExpect(jsonPath("$.data.stats.reviewCount").value(4))
                    .andExpect(jsonPath("$.data.stats.hostedCount").value(1))
                    .andExpect(jsonPath("$.data.stats.participatedCount").value(1))
                    // 아쉬움 평가의 PUNCTUAL 을 세면 PUNCTUAL 이 2개로 KIND 와 같아져 맨 앞에 온다
                    .andExpect(jsonPath("$.data.stats.topTags[*].code").value(contains("KIND", "PUNCTUAL", "GOOD_SPOTTER")))
                    .andExpect(jsonPath("$.data.stats.topTags[*].count").value(contains(2, 1, 1)))
                    .andExpect(jsonPath("$.data.stats.topTags[0].label").value("친절하고 말투가 편안해요"))
                    .andExpect(jsonPath("$.data.stats.topTags[0].polarity").value("POSITIVE"));
        }

        @Test
        void 활동이_없으면_모두_0이고_태그는_빈_배열() throws Exception {
            mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(carol)))
                    .andExpect(jsonPath("$.data.stats.reviewCount").value(0))
                    .andExpect(jsonPath("$.data.stats.hostedCount").value(0))
                    .andExpect(jsonPath("$.data.stats.participatedCount").value(0))
                    .andExpect(jsonPath("$.data.stats.topTags").value(empty()));
        }
    }

    @Nested
    class 다른_사용자_프로필 {

        @Test
        void 공개_정보와_활동_요약을_주고_이메일은_주지_않는다() throws Exception {
            profile(carol, me)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(me.getId()))
                    .andExpect(jsonPath("$.data.nickname").value("나"))
                    .andExpect(jsonPath("$.data.mannerTemperature").value(36.5))
                    .andExpect(jsonPath("$.data.me").value(false))
                    .andExpect(jsonPath("$.data.email").doesNotExist())
                    .andExpect(jsonPath("$.data.stats.reviewCount").value(4))
                    .andExpect(jsonPath("$.data.stats.hostedCount").value(1))
                    .andExpect(jsonPath("$.data.stats.participatedCount").value(1))
                    .andExpect(jsonPath("$.data.stats.topTags[*].code").value(contains("KIND", "PUNCTUAL", "GOOD_SPOTTER")));
        }

        @Test
        void 함께_운동한_적이_없으면_버디_신청_버튼이_없다() throws Exception {
            profile(carol, me)
                    .andExpect(jsonPath("$.data.buddy.relation").value("NONE"))
                    .andExpect(jsonPath("$.data.buddy.requestId").value(nullValue()))
                    .andExpect(jsonPath("$.data.buddy.canRequest").value(false));
        }

        @Test
        void 함께_운동했고_관계가_없으면_버디_신청할_수_있다() throws Exception {
            profile(alice, me)
                    .andExpect(jsonPath("$.data.buddy.relation").value("NONE"))
                    .andExpect(jsonPath("$.data.buddy.canRequest").value(true));
        }

        @Test
        void 대기_중인_신청이_있으면_방향과_신청_ID를_주고_신청할_수_없다() throws Exception {
            BuddyRequest request = buddyRequestRepository.save(BuddyRequest.send(me, alice));
            flushAndClear();

            profile(alice, me)
                    .andExpect(jsonPath("$.data.buddy.relation").value("REQUEST_RECEIVED"))
                    .andExpect(jsonPath("$.data.buddy.requestId").value(request.getId()))
                    .andExpect(jsonPath("$.data.buddy.canRequest").value(false));
            profile(me, alice)
                    .andExpect(jsonPath("$.data.buddy.relation").value("REQUEST_SENT"))
                    .andExpect(jsonPath("$.data.buddy.requestId").value(request.getId()));
        }

        @Test
        void 이미_버디면_BUDDY() throws Exception {
            buddyRepository.save(Buddy.of(alice, me));
            buddyRepository.save(Buddy.of(me, alice));
            flushAndClear();

            profile(alice, me)
                    .andExpect(jsonPath("$.data.buddy.relation").value("BUDDY"))
                    .andExpect(jsonPath("$.data.buddy.canRequest").value(false));
        }

        @Test
        void 내_ID로_열면_me_true_관계_없음() throws Exception {
            profile(me, me)
                    .andExpect(jsonPath("$.data.me").value(true))
                    .andExpect(jsonPath("$.data.buddy.relation").value("NONE"))
                    .andExpect(jsonPath("$.data.buddy.canRequest").value(false));
        }

        @Test
        void 없는_사용자는_404_U001() throws Exception {
            mockMvc.perform(get("/api/users/{id}", Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, bearer(me)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("U001"));
        }
    }

    private ResultActions profile(User viewer, User target) throws Exception {
        return mockMvc.perform(get("/api/users/{id}", target.getId()).header(HttpHeaders.AUTHORIZATION, bearer(viewer)));
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.issueAccessToken(user.getId()).value();
    }

    private User user(String key, String nickname) {
        return userRepository.save(User.signUpWithEmail(key + "@example.com", "hash", "이름", nickname, Gender.MALE));
    }

    private Gathering gathering(User host, GatheringStatus status) {
        Place place = placeRepository.save(Place.pinned("버디짐", null, "서울 강남구 역삼동 1", "1168010100",
                new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(127.0, 37.5))));
        Gathering saved = gatheringRepository.save(Gathering.builder()
                .host(host).place(place).title("같이 운동해요")
                .startAt(PAST).endAt(PAST.plus(Duration.ofHours(1)))
                .capacity((short) 3).genderRule(GenderRule.ANY).status(status)
                .build());
        participationRepository.save(Participation.hostOf(saved, PAST.minus(Duration.ofDays(1))));
        return saved;
    }

    private Participation hostParticipation(Gathering gathering) {
        return participationRepository.findActive(gathering.getId(), gathering.getHost().getId()).orElseThrow();
    }

    private Participation confirmed(Gathering gathering, User user) {
        return participationRepository.save(Participation.builder()
                .gathering(gathering).user(user).status(ParticipationStatus.CONFIRMED)
                .appliedAt(PAST).decidedAt(PAST).build());
    }

    private void checkIn(Participation participation, Attendance attendance) {
        checkInRepository.save(CheckIn.builder().participation(participation).attendance(attendance).build());
    }

    private void review(Gathering gathering, User evaluator, ReviewRating rating, ReviewTagType... tags) {
        Review review = reviewRepository.saveAndFlush(Review.of(gathering, evaluator, me, rating, null));
        for (ReviewTagType tag : tags) {
            reviewTagRepository.save(ReviewTag.of(review, tag));
        }
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
