package com.buddygo.buddygoserver.domain.gathering;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.entity.Place;
import com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus;
import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringLikeRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.PlaceRepository;
import com.buddygo.buddygoserver.domain.sport.gym.entity.GatheringGymPart;
import com.buddygo.buddygoserver.domain.sport.gym.enums.ArmLevel;
import com.buddygo.buddygoserver.domain.sport.gym.enums.BodyPart;
import com.buddygo.buddygoserver.domain.sport.gym.repository.GatheringGymPartRepository;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로컬 DB 를 쓰므로 이미 있는 데이터와 섞이지 않게, 모임 장소를 바다 위 좌표(북위 33.0~33.1, 동경 124.0~124.1)와
 * 쓰이지 않는 법정동 코드(99999…)에만 둔다. 검색 영역도 그 안으로만 건다.
 */
@Transactional
class GatheringSearchIntegrationTest extends IntegrationTest {

    /** KST 2026-11-10 09:00. 데드라인 90분이라 01:30Z 이후에 시작하는 모임만 신청을 받는다. */
    static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");
    static final String AREA = "&swLat=33.0&swLng=124.0&neLat=33.1&neLng=124.1";
    static final String ORIGIN = "&lat=33.0&lng=124.05";
    static final GeometryFactory GEOMETRY = new GeometryFactory(new PrecisionModel(), 4326);

    @Override
    protected Instant fixedNow() {
        return NOW;
    }

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired GatheringRepository gatheringRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired ParticipationRepository participationRepository;
    @Autowired GatheringGymPartRepository gatheringGymPartRepository;
    @Autowired GatheringLikeRepository gatheringLikeRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired EntityManager entityManager;

    User host;
    User viewer;

    @BeforeEach
    void setUp() {
        host = userRepository.save(User.signUpWithEmail("search-host@example.com", "hash", "김호스트", "검색호스트", Gender.FEMALE));
        viewer = userRepository.save(User.signUpWithEmail("search-viewer@example.com", "hash", "이보는", "검색보는사람", Gender.MALE));
    }

    @Nested
    class 리스트 {

        @Test
        void 영역_안의_모집중이고_데드라인_전인_모임만_거리순으로_카드에_담는다() throws Exception {
            Gathering far = gathering("먼 모임", 33.02, "9999910100", "바다동 2", hoursLater(3));
            Gathering near = gathering("가까운 모임", 33.01, "9999910100", "바다동 1", hoursLater(5));
            participate(near, viewer, ParticipationStatus.CONFIRMED);
            participate(near, userRepository.save(User.signUpWithEmail("p@example.com", "hash", "박", "대기자", Gender.MALE)),
                    ParticipationStatus.PENDING);
            gatheringLikeRepository.insertIgnoringDuplicate(viewer.getId(), near.getId(), NOW);

            Gathering closed = gathering("마감", 33.03, "9999910100", "바다동 3", hoursLater(3));
            setStatus(closed, GatheringStatus.CLOSED);
            Gathering canceled = gathering("취소", 33.03, "9999910100", "바다동 3", hoursLater(3));
            setStatus(canceled, GatheringStatus.CANCELED);
            gathering("데드라인 지남", 33.03, "9999910100", "바다동 3", NOW.plus(Duration.ofMinutes(80)));
            gathering("영역 밖", 33.2, "9999910100", "바다동 9", hoursLater(3));
            flushAndClear();

            search(viewer, AREA + ORIGIN)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[*].title").value(contains("가까운 모임", "먼 모임")))
                    .andExpect(jsonPath("$.data.content[0].gatheringId").value(near.getId()))
                    .andExpect(jsonPath("$.data.content[0].distanceMeters").value(1109))
                    .andExpect(jsonPath("$.data.content[0].startAt").value("2026-11-10T05:00:00Z"))
                    .andExpect(jsonPath("$.data.content[0].place.jibunAddress").value("바다동 1"))
                    .andExpect(jsonPath("$.data.content[0].place.latitude").value(33.01))
                    .andExpect(jsonPath("$.data.content[0].host.nickname").value("검색호스트"))
                    .andExpect(jsonPath("$.data.content[0].host.mannerTemperature").value(36.5))
                    .andExpect(jsonPath("$.data.content[0].confirmedCount").value(2))
                    .andExpect(jsonPath("$.data.content[0].capacity").value(3))
                    .andExpect(jsonPath("$.data.content[0].liked").value(true))
                    .andExpect(jsonPath("$.data.content[1].confirmedCount").value(1))
                    .andExpect(jsonPath("$.data.content[1].liked").value(false))
                    .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
        }

        @Test
        void 내_위치가_없으면_거리_없이_시작_시각순이다() throws Exception {
            gathering("늦은 가까운 모임", 33.01, "9999910100", "바다동 1", hoursLater(5));
            gathering("이른 먼 모임", 33.02, "9999910100", "바다동 2", hoursLater(3));
            flushAndClear();

            search(viewer, AREA)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[*].title").value(contains("이른 먼 모임", "늦은 가까운 모임")))
                    .andExpect(jsonPath("$.data.content[0].distanceMeters").doesNotExist());
        }

        @Test
        void 구_코드는_앞_5자리_동_코드는_앞_8자리로_거른다() throws Exception {
            gathering("바다동", 33.01, "9999910100", "바다동 1", hoursLater(3));
            gathering("바다동 리", 33.01, "9999910121", "바다동 1", hoursLater(4));
            gathering("섬동", 33.02, "9999920100", "섬동 1", hoursLater(5));
            gathering("다른 구", 33.03, "9999810100", "다른구 1", hoursLater(6));
            flushAndClear();

            search(viewer, "&regionCode=9999900000")
                    .andExpect(jsonPath("$.data.content[*].title").value(contains("바다동", "바다동 리", "섬동")));
            search(viewer, "&regionCode=9999910100")
                    .andExpect(jsonPath("$.data.content[*].title").value(contains("바다동", "바다동 리")));
        }

        @Test
        void 묶인_핀을_누르면_그_지번의_모임만_나온다() throws Exception {
            gathering("1번지 A", 33.01, "9999910100", "바다동 1", hoursLater(3));
            gathering("1번지 B", 33.0101, "9999910100", "바다동 1", hoursLater(4));
            gathering("2번지", 33.02, "9999910100", "바다동 2", hoursLater(5));
            flushAndClear();

            search(viewer, AREA + "&parcelKey=바다동 1")
                    .andExpect(jsonPath("$.data.content[*].title").value(contains("1번지 A", "1번지 B")));
        }

        @Test
        void 핀을_누른_뒤에는_지도를_옮겨도_parcelKey_만으로_그_지번의_모임을_받고_다른_조건은_그대로_건다() throws Exception {
            Gathering chest = gathering("1번지 가슴", 33.01, "9999910100", "바다동 1", hoursLater(3));
            parts(chest, BodyPart.CHEST);
            Gathering back = gathering("1번지 등", 33.01, "9999910100", "바다동 1", hoursLater(4));
            parts(back, BodyPart.BACK);
            gathering("2번지", 33.02, "9999910100", "바다동 2", hoursLater(5));
            flushAndClear();

            search(viewer, "&parcelKey=바다동 1")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[*].title").value(contains("1번지 가슴", "1번지 등")));
            search(viewer, "&parcelKey=바다동 1&parts=CHEST")
                    .andExpect(jsonPath("$.data.content[*].title").value(contains("1번지 가슴")));
        }

        @Test
        void 날짜와_시간대는_KST_시작_시각으로_거른다() throws Exception {
            gathering("KST 12시", 33.01, "9999910100", "바다동 1", Instant.parse("2026-11-10T03:00:00Z"));
            gathering("KST 19시", 33.01, "9999910100", "바다동 1", Instant.parse("2026-11-10T10:00:00Z"));
            gathering("KST 다음날 08시", 33.01, "9999910100", "바다동 1", Instant.parse("2026-11-10T23:00:00Z"));
            flushAndClear();

            search(viewer, AREA + "&date=2026-11-10")
                    .andExpect(jsonPath("$.data.content[*].title").value(contains("KST 12시", "KST 19시")));
            search(viewer, AREA + "&date=2026-11-10&timeSlots=EVENING&timeSlots=DAWN")
                    .andExpect(jsonPath("$.data.content[*].title").value(contains("KST 19시")));
            search(viewer, AREA + "&date=2026-11-10&fromTime=11:00&toTime=12:00")
                    .andExpect(jsonPath("$.data.content[*].title", empty()));
            search(viewer, AREA + "&date=2026-11-10&fromTime=12:00&toTime=19:10")
                    .andExpect(jsonPath("$.data.content[*].title").value(contains("KST 12시", "KST 19시")));
            search(viewer, AREA + "&date=2026-11-11&timeSlots=MORNING")
                    .andExpect(jsonPath("$.data.content[*].title").value(contains("KST 다음날 08시")));
        }

        @Test
        void 고른_부위를_모두_포함하는_모임만_나온다() throws Exception {
            Gathering chestBackArm = gathering("가슴·등·팔", 33.01, "9999910100", "바다동 1", hoursLater(3));
            parts(chestBackArm, BodyPart.CHEST, BodyPart.BACK, BodyPart.ARM);
            Gathering chest = gathering("가슴", 33.01, "9999910100", "바다동 1", hoursLater(4));
            parts(chest, BodyPart.CHEST);
            flushAndClear();

            search(viewer, AREA + "&parts=CHEST&parts=ARM")
                    .andExpect(jsonPath("$.data.content[*].title").value(contains("가슴·등·팔")));
            search(viewer, AREA + "&parts=CHEST")
                    .andExpect(jsonPath("$.data.content[*].title").value(contains("가슴·등·팔", "가슴")));
        }

        @Test
        void 기준_범위는_범위를_보낸_부위_중_하나라도_맞으면_포함하고_숨긴_기록은_맞지_않는다() throws Exception {
            Gathering chestIn = gathering("가슴 80", 33.01, "9999910100", "바다동 1", hoursLater(3));
            gatheringGymPartRepository.save(GatheringGymPart.of(chestIn, BodyPart.CHEST, BigDecimal.valueOf(80), null));
            gatheringGymPartRepository.save(GatheringGymPart.of(chestIn, BodyPart.ARM, null, ArmLevel.ADVANCED));
            Gathering armIn = gathering("가슴 120 · 팔 중급", 33.01, "9999910100", "바다동 1", hoursLater(4));
            gatheringGymPartRepository.save(GatheringGymPart.of(armIn, BodyPart.CHEST, BigDecimal.valueOf(120), null));
            gatheringGymPartRepository.save(GatheringGymPart.of(armIn, BodyPart.ARM, null, ArmLevel.INTERMEDIATE));
            Gathering hidden = gathering("기록 숨김", 33.01, "9999910100", "바다동 1", hoursLater(5));
            gatheringGymPartRepository.save(GatheringGymPart.of(hidden, BodyPart.CHEST, null, null));
            gatheringGymPartRepository.save(GatheringGymPart.of(hidden, BodyPart.ARM, null, null));
            flushAndClear();

            search(viewer, AREA + "&parts=CHEST&parts=ARM&chestMin=70&chestMax=90")
                    .andExpect(jsonPath("$.data.content[*].title").value(contains("가슴 80")));
            search(viewer, AREA + "&parts=CHEST&parts=ARM&chestMin=70&chestMax=90&armLevel=INTERMEDIATE")
                    .andExpect(jsonPath("$.data.content[*].title").value(contains("가슴 80", "가슴 120 · 팔 중급")));
            search(viewer, AREA + "&parts=CHEST&chestMin=80")
                    .andExpect(jsonPath("$.data.content[*].title").value(contains("가슴 80", "가슴 120 · 팔 중급")));
        }

        @Test
        void 성별_매너온도_찜_조건으로_거른다() throws Exception {
            Gathering women = gathering("여성만", 33.01, "9999910100", "바다동 1", hoursLater(3), GenderRule.FEMALE_ONLY);
            gathering("누구나", 33.01, "9999910100", "바다동 1", hoursLater(4), GenderRule.ANY);
            gatheringLikeRepository.insertIgnoringDuplicate(viewer.getId(), women.getId(), NOW);
            flushAndClear();

            search(viewer, AREA + "&gender=FEMALE_ONLY")
                    .andExpect(jsonPath("$.data.content[*].title").value(contains("여성만")));
            search(viewer, AREA + "&likedOnly=true")
                    .andExpect(jsonPath("$.data.content[*].title").value(contains("여성만")));
            search(viewer, AREA + "&minManner=36.5")
                    .andExpect(jsonPath("$.data.content.length()").value(2));
            search(viewer, AREA + "&minManner=50")
                    .andExpect(jsonPath("$.data.content[*].title", empty()));
        }

        @Test
        void 무한_스크롤은_nextCursor_로_이어_받고_마지막이면_null_이다() throws Exception {
            for (int i = 1; i <= 3; i++) {
                gathering("모임 " + i, 33.0 + i * 0.01, "9999910100", "바다동 " + i, hoursLater(3));
            }
            flushAndClear();

            String cursor = nextCursor(search(viewer, AREA + ORIGIN + "&size=2")
                    .andExpect(jsonPath("$.data.content[*].title").value(contains("모임 1", "모임 2"))));
            search(viewer, AREA + ORIGIN + "&size=2&cursor=" + cursor)
                    .andExpect(jsonPath("$.data.content[*].title").value(contains("모임 3")))
                    .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
        }

        @Test
        void 스크롤하는_사이에_앞쪽_모임이_빠지거나_생겨도_건너뛰거나_중복되지_않는다() throws Exception {
            Gathering first = gathering("모임 1", 33.01, "9999910100", "바다동 1", hoursLater(3));
            gathering("모임 2", 33.02, "9999910100", "바다동 2", hoursLater(3));
            gathering("모임 3", 33.03, "9999910100", "바다동 3", hoursLater(3));
            gathering("모임 4", 33.04, "9999910100", "바다동 4", hoursLater(3));
            flushAndClear();

            String cursor = nextCursor(search(viewer, AREA + ORIGIN + "&size=2")
                    .andExpect(jsonPath("$.data.content[*].title").value(contains("모임 1", "모임 2"))));

            setStatus(first, GatheringStatus.CLOSED);
            gathering("새 가까운 모임", 33.005, "9999910100", "바다동 0", hoursLater(3));
            flushAndClear();

            search(viewer, AREA + ORIGIN + "&size=2&cursor=" + cursor)
                    .andExpect(jsonPath("$.data.content[*].title").value(contains("모임 3", "모임 4")));
        }

        @Test
        void 내_위치가_없으면_시작_시각과_id_순으로_이어_받는다() throws Exception {
            gathering("늦은 모임", 33.01, "9999910100", "바다동 1", hoursLater(5));
            gathering("이른 모임 A", 33.02, "9999910100", "바다동 2", hoursLater(3));
            gathering("이른 모임 B", 33.03, "9999910100", "바다동 3", hoursLater(3));
            flushAndClear();

            String cursor = nextCursor(search(viewer, AREA + "&size=1")
                    .andExpect(jsonPath("$.data.content[*].title").value(contains("이른 모임 A"))));
            cursor = nextCursor(search(viewer, AREA + "&size=1&cursor=" + cursor)
                    .andExpect(jsonPath("$.data.content[*].title").value(contains("이른 모임 B"))));
            search(viewer, AREA + "&size=1&cursor=" + cursor)
                    .andExpect(jsonPath("$.data.content[*].title").value(contains("늦은 모임")))
                    .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
        }

        @Test
        void 커서가_깨졌거나_거리순_여부가_첫_요청과_다르면_400() throws Exception {
            gathering("모임 1", 33.01, "9999910100", "바다동 1", hoursLater(3));
            gathering("모임 2", 33.02, "9999910100", "바다동 2", hoursLater(3));
            flushAndClear();
            String byDistance = nextCursor(search(viewer, AREA + ORIGIN + "&size=1"));

            search(viewer, AREA + "&size=1&cursor=" + byDistance)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("GA018"));
            search(viewer, AREA + ORIGIN + "&cursor=not-a-cursor")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("GA018"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "",
                "&regionCode=9999910100" + AREA,
                "&swLat=33.0&swLng=124.0&neLat=33.1",
                "&swLat=33.1&swLng=124.0&neLat=33.0&neLng=124.1"
        })
        void 검색_영역이_지도_범위나_구동_하나가_아니면_400(String query) throws Exception {
            search(viewer, query)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("GA016"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "&timeSlots=MORNING",
                "&fromTime=10:00&toTime=12:00",
                "&date=2026-11-10&timeSlots=MORNING&fromTime=10:00&toTime=12:00",
                "&date=2026-11-10&fromTime=10:00",
                "&chestMin=70",
                "&parts=CHEST&chestMin=90&chestMax=70",
                "&armLevel=BEGINNER&parts=CHEST",
                "&lat=33.0"
        })
        void 조건_조합이_맞지_않으면_400(String query) throws Exception {
            search(viewer, AREA + query)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("GA017"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "&regionCode=11680",
                AREA + "&size=0",
                AREA + "&size=101",
                AREA + "&chestMin=-1",
                AREA + "&parts=NECK",
                "&swLat=91&swLng=124.0&neLat=33.1&neLng=124.1"
        })
        void 입력값이_올바르지_않으면_400(String query) throws Exception {
            search(viewer, query)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("G002"));
        }

        @Test
        void 토큰이_없으면_401() throws Exception {
            mockMvc.perform(get("/api/gatherings?" + AREA.substring(1)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class 지도_핀 {

        @Test
        void 같은_지번의_모임을_핀_하나로_묶어_모임_수와_중심_좌표를_준다() throws Exception {
            gathering("1번지 A", 33.01, "9999910100", "바다동 1", hoursLater(3));
            gathering("1번지 B", 33.03, "9999910100", "바다동 1", hoursLater(4));
            gathering("2번지", 33.05, "9999910100", "바다동 2", hoursLater(5));
            Gathering closed = gathering("마감", 33.07, "9999910100", "바다동 3", hoursLater(3));
            setStatus(closed, GatheringStatus.CLOSED);
            flushAndClear();

            pins(viewer, AREA)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.pins[*].parcelKey").value(contains("바다동 1", "바다동 2")))
                    .andExpect(jsonPath("$.data.pins[0].gatheringCount").value(2))
                    .andExpect(jsonPath("$.data.pins[0].latitude").value(closeTo(33.02, 1e-9)))
                    .andExpect(jsonPath("$.data.pins[0].longitude").value(closeTo(124.05, 1e-9)))
                    .andExpect(jsonPath("$.data.pins[1].gatheringCount").value(1));
        }

        @Test
        void 리스트와_같은_조건으로_거른다() throws Exception {
            Gathering chest = gathering("가슴", 33.01, "9999910100", "바다동 1", hoursLater(3));
            parts(chest, BodyPart.CHEST);
            Gathering back = gathering("등", 33.02, "9999910100", "바다동 2", hoursLater(3));
            parts(back, BodyPart.BACK);
            flushAndClear();

            pins(viewer, AREA + "&parts=BACK")
                    .andExpect(jsonPath("$.data.pins[*].parcelKey").value(contains("바다동 2")));
        }

        @Test
        void 검색_영역이_없으면_400() throws Exception {
            pins(viewer, "")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("GA016"));
        }
    }

    private ResultActions search(User user, String query) throws Exception {
        return mockMvc.perform(get("/api/gatherings?" + query.replaceFirst("^&", ""))
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private static String nextCursor(ResultActions result) throws Exception {
        String cursor = JsonPath.read(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.data.nextCursor");
        assertThat(cursor).isNotNull();
        return cursor;
    }

    private ResultActions pins(User user, String query) throws Exception {
        return mockMvc.perform(get("/api/gatherings/pins?" + query.replaceFirst("^&", ""))
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.issueAccessToken(user.getId()).value();
    }

    private static Instant hoursLater(int hours) {
        return NOW.plus(Duration.ofHours(hours));
    }

    private Gathering gathering(String title, double lat, String regionCode, String jibun, Instant startAt) {
        return gathering(title, lat, regionCode, jibun, startAt, GenderRule.ANY);
    }

    /** 경도는 124.05 로 두고 위도만 바꾼다(위도 0.01도 ≈ 1.1km). */
    private Gathering gathering(String title, double lat, String regionCode, String jibun, Instant startAt,
                                GenderRule genderRule) {
        Place place = placeRepository.save(Place.pinned("집결지", null, jibun, regionCode,
                GEOMETRY.createPoint(new Coordinate(124.05, lat))));
        Gathering gathering = gatheringRepository.save(Gathering.open(host, place, title, null, null,
                startAt, Duration.ofMinutes(60), (short) 3, genderRule));
        participationRepository.save(Participation.hostOf(gathering, NOW));
        return gathering;
    }

    private void parts(Gathering gathering, BodyPart... parts) {
        for (BodyPart part : parts) {
            gatheringGymPartRepository.save(GatheringGymPart.of(gathering, part, null, null));
        }
    }

    private void participate(Gathering gathering, User user, ParticipationStatus status) {
        participationRepository.save(Participation.builder()
                .gathering(gathering)
                .user(user)
                .status(status)
                .appliedAt(NOW)
                .decidedAt(status == ParticipationStatus.CONFIRMED ? NOW : null)
                .build());
    }

    private void setStatus(Gathering gathering, GatheringStatus status) {
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE gatherings SET status = :status WHERE id = :id")
                .setParameter("status", status.name()).setParameter("id", gathering.getId()).executeUpdate();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
