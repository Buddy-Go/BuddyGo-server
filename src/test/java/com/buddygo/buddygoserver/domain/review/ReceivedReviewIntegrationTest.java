package com.buddygo.buddygoserver.domain.review;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
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
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
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

/** 내가(me) 받은 평가 4개: r1 좋음(alice) → r2 아쉬움(alice) → r3 보통·내용 없음(bob) = r4 좋음(bob, r3과 같은 시각) */
@Transactional
class ReceivedReviewIntegrationTest extends IntegrationTest {

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
    @Autowired ReviewRepository reviewRepository;
    @Autowired ReviewTagRepository reviewTagRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired EntityManager entityManager;

    User me;
    User alice;
    User bob;
    User carol;
    Gathering g1;
    Review r1;
    Review r2;
    Review r3;
    Review r4;

    @BeforeEach
    void setUp() {
        me = user("rr-me", "나");
        alice = user("rr-alice", "앨리스");
        bob = user("rr-bob", "밥");
        carol = user("rr-carol", "캐롤");
        g1 = gathering(me);
        Gathering g2 = gathering(alice);

        Instant t1 = NOW.minus(Duration.ofHours(3));
        r1 = review(t1, g1, alice, ReviewRating.GOOD, "좋았어요", ReviewTagType.KIND, ReviewTagType.PUNCTUAL);
        r2 = review(t1.plusSeconds(60), g2, alice, ReviewRating.BAD, "아쉬웠어요",
                ReviewTagType.PUNCTUAL, ReviewTagType.ON_PHONE);
        Instant t3 = t1.plusSeconds(120);
        r3 = review(t3, g2, bob, ReviewRating.NORMAL, null);
        r4 = review(t3, g1, bob, ReviewRating.GOOD, null, ReviewTagType.KIND, ReviewTagType.GOOD_SPOTTER);
        mutableClock.fix(NOW);
        flushAndClear();
    }


    @Test
    void 내_목록은_최신순이고_같은_시각이면_ID_역순이며_커서로_이어진다() throws Exception {
        String cursor = myReviews(null, 1)
                .andExpect(jsonPath("$.data.content[*].reviewId").value(contains(r4.getId().intValue())))
                .andReturn().getResponse().getContentAsString();
        cursor = myReviews(nextCursor(cursor), 1)
                .andExpect(jsonPath("$.data.content[*].reviewId").value(contains(r3.getId().intValue())))
                .andReturn().getResponse().getContentAsString();
        myReviews(nextCursor(cursor), 2)
                .andExpect(jsonPath("$.data.content[*].reviewId")
                        .value(contains(r2.getId().intValue(), r1.getId().intValue())))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
    }

    @Test
    void 내_목록의_아쉬움_평가는_보이지만_평가자와_모임을_비운다() throws Exception {
        myReviews(null, 10)
                .andExpect(jsonPath("$.data.content[2].rating").value("BAD"))
                .andExpect(jsonPath("$.data.content[2].comment").value("아쉬웠어요"))
                .andExpect(jsonPath("$.data.content[2].tags[*].code").value(contains("PUNCTUAL", "ON_PHONE")))
                .andExpect(jsonPath("$.data.content[2].evaluator").value(nullValue()))
                .andExpect(jsonPath("$.data.content[2].gathering").value(nullValue()))
                // 좋음 평가는 평가자·모임을 보여주고 태그는 태그 목록 순
                .andExpect(jsonPath("$.data.content[3].evaluator.userId").value(alice.getId()))
                .andExpect(jsonPath("$.data.content[3].evaluator.nickname").value("앨리스"))
                .andExpect(jsonPath("$.data.content[3].gathering.gatheringId").value(g1.getId()))
                .andExpect(jsonPath("$.data.content[3].gathering.title").value("같이 운동해요"))
                .andExpect(jsonPath("$.data.content[3].tags[*].code").value(contains("PUNCTUAL", "KIND")))
                .andExpect(jsonPath("$.data.content[3].tags[0].label").value("시간 약속을 잘 지켜요"))
                // 한마디·태그가 없는 보통 평가도 판정만 있는 줄로 나온다
                .andExpect(jsonPath("$.data.content[1].rating").value("NORMAL"))
                .andExpect(jsonPath("$.data.content[1].comment").value(nullValue()))
                .andExpect(jsonPath("$.data.content[1].tags").value(empty()));
    }

    @Test
    void 다른_사람의_목록에는_아쉬움_평가가_없다() throws Exception {
        mockMvc.perform(get("/api/users/{id}/reviews", me.getId()).header(HttpHeaders.AUTHORIZATION, bearer(carol)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].reviewId").value(contains(
                        r4.getId().intValue(), r3.getId().intValue(), r1.getId().intValue())))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
    }

    @Test
    void 후기가_없으면_빈_배열() throws Exception {
        mockMvc.perform(get("/api/users/me/reviews").header(HttpHeaders.AUTHORIZATION, bearer(carol)))
                .andExpect(jsonPath("$.data.content").value(empty()))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
    }

    @Test
    void 잘못된_커서는_400_R005() throws Exception {
        myReviews("not-a-cursor", 10)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("R005"));
    }

    @Test
    void 없는_사용자는_404_U001() throws Exception {
        mockMvc.perform(get("/api/users/{id}/reviews", Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, bearer(me)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    void size가_범위를_벗어나면_400() throws Exception {
        myReviews(null, 0).andExpect(status().isBadRequest());
    }

    private ResultActions myReviews(String cursor, int size) throws Exception {
        var request = get("/api/users/me/reviews").header(HttpHeaders.AUTHORIZATION, bearer(me))
                .param("size", String.valueOf(size));
        if (cursor != null) {
            request.param("cursor", cursor);
        }
        return mockMvc.perform(request);
    }

    private static String nextCursor(String body) {
        return JsonPath.read(body, "$.data.nextCursor");
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.issueAccessToken(user.getId()).value();
    }

    private User user(String key, String nickname) {
        return userRepository.save(User.signUpWithEmail(key + "@example.com", "hash", "이름", nickname, Gender.MALE));
    }

    private Gathering gathering(User host) {
        Place place = placeRepository.save(Place.pinned("버디짐", null, "서울 강남구 역삼동 1", "1168010100",
                new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(127.0, 37.5))));
        Gathering saved = gatheringRepository.save(Gathering.builder()
                .host(host).place(place).title("같이 운동해요")
                .startAt(PAST).endAt(PAST.plus(Duration.ofHours(1)))
                .capacity((short) 3).genderRule(GenderRule.ANY).status(GatheringStatus.FINISHED)
                .build());
        participationRepository.save(Participation.hostOf(saved, PAST.minus(Duration.ofDays(1))));
        return saved;
    }

    /** created_at 은 감사(auditing) 시계에서 오므로 시계를 옮겨 저장한다. */
    private Review review(Instant at, Gathering gathering, User evaluator, ReviewRating rating, String comment,
                          ReviewTagType... tags) {
        mutableClock.fix(at);
        Review review = reviewRepository.saveAndFlush(Review.of(gathering, evaluator, me, rating, comment));
        for (ReviewTagType tag : tags) {
            reviewTagRepository.save(ReviewTag.of(review, tag));
        }
        return review;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
