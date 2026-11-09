package com.buddygo.buddygoserver.domain.notification;

import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.entity.Place;
import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.PlaceRepository;
import com.buddygo.buddygoserver.domain.notice.entity.Notice;
import com.buddygo.buddygoserver.domain.notice.repository.NoticeRepository;
import com.buddygo.buddygoserver.domain.notification.entity.Notification;
import com.buddygo.buddygoserver.domain.notification.repository.NotificationRepository;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.enums.Gender;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.security.jwt.JwtTokenProvider;
import com.buddygo.buddygoserver.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class NotificationIntegrationTest extends IntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");
    private static final String BASE = "/api/notifications";
    @Autowired MockMvc mvc;
    @Autowired NotificationRepository notifications;
    @Autowired UserRepository users;
    @Autowired GatheringRepository gatherings;
    @Autowired PlaceRepository places;
    @Autowired NoticeRepository notices;
    @Autowired JwtTokenProvider tokens;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    User me, other;

    @Override
    protected Instant fixedNow() { return NOW; }

    @BeforeEach
    void setup() {
        me = users.save(User.signUpWithEmail("notification-me@example.com", "hash", "나", "noti-me", Gender.MALE));
        other = users.save(User.signUpWithEmail("notification-other@example.com", "hash", "상대", "noti-other", Gender.FEMALE));
    }

    @Test
    void 본인알림만_읽음여부와_전체미읽음수를_반환한다() throws Exception {
        Notification unread = save(me, null);
        save(me, NOW.minusSeconds(60));
        save(other, null);
        request(me, get(BASE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].read").value(true))
                .andExpect(jsonPath("$.data.content[1].read").value(false))
                .andExpect(jsonPath("$.data.unreadCount").value(1));
        assertThat(notifications.findById(unread.getId()).orElseThrow().getReadAt()).isNull();
    }

    @Test
    void 생성시각과_ID_내림차순으로_페이지를_나눈다() throws Exception {
        Notification first = save(me, null);
        Notification second = save(me, null);
        Notification older = save(me, null);
        em.flush();
        jdbc.update("update notifications set created_at = ? where id = ?", Timestamp.from(NOW.minusSeconds(60)), older.getId());
        em.clear();
        request(me, get(BASE).param("size", "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].notificationId").value(second.getId()))
                .andExpect(jsonPath("$.data.hasNext").value(true)).andExpect(jsonPath("$.data.unreadCount").value(3));
        request(me, get(BASE).param("page", "1").param("size", "1"))
                .andExpect(jsonPath("$.data.content[0].notificationId").value(first.getId()));
        request(me, get(BASE).param("page", "2").param("size", "1"))
                .andExpect(jsonPath("$.data.content[0].notificationId").value(older.getId()))
                .andExpect(jsonPath("$.data.hasNext").value(false));
        request(me, get(BASE).param("page", "3").param("size", "1"))
                .andExpect(jsonPath("$.data.content").isEmpty()).andExpect(jsonPath("$.data.unreadCount").value(3));
    }

    @Test
    void 빈목록과_빈전체읽음은_정상처리한다() throws Exception {
        request(me, get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.unreadCount").value(0)).andExpect(jsonPath("$.data.hasNext").value(false));
        request(me, patch(BASE + "/read-all")).andExpect(status().isOk());
    }

    @Test
    void 개별읽음은_반복해도_최초시각을_유지한다() throws Exception {
        Notification n = save(me, null);
        request(me, patch(readPath(n))).andExpect(status().isOk());
        assertThat(notifications.findById(n.getId()).orElseThrow().getReadAt()).isEqualTo(NOW);
        mutableClock.fix(NOW.plusSeconds(60));
        request(me, patch(readPath(n))).andExpect(status().isOk());
        assertThat(notifications.findById(n.getId()).orElseThrow().getReadAt()).isEqualTo(NOW);
        request(me, get(BASE)).andExpect(jsonPath("$.data.unreadCount").value(0))
                .andExpect(jsonPath("$.data.content[0].read").value(true))
                .andExpect(jsonPath("$.data.content[0].readAt").value(NOW.toString()));
    }

    @Test
    void 남의알림과_없는알림은_동일하게_404이다() throws Exception {
        Notification foreign = save(other, null);
        request(me, patch(readPath(foreign))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NT001"));
        request(me, patch(BASE + "/9223372036854775807/read"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NT001"));
        assertThat(notifications.findById(foreign.getId()).orElseThrow().getReadAt()).isNull();
    }

    @Test
    void 전체읽음은_본인의_미읽음만_바꾼다() throws Exception {
        Notification first = save(me, null);
        Notification second = save(me, null);
        Notification read = save(me, NOW.minusSeconds(60));
        Notification foreign = save(other, null);
        request(me, patch(BASE + "/read-all")).andExpect(status().isOk());
        assertThat(notifications.findById(first.getId()).orElseThrow().getReadAt()).isEqualTo(NOW);
        assertThat(notifications.findById(second.getId()).orElseThrow().getReadAt()).isEqualTo(NOW);
        assertThat(notifications.findById(read.getId()).orElseThrow().getReadAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(notifications.findById(foreign.getId()).orElseThrow().getReadAt()).isNull();
        mutableClock.fix(NOW.plusSeconds(60));
        request(me, patch(BASE + "/read-all")).andExpect(status().isOk());
        assertThat(notifications.findById(first.getId()).orElseThrow().getReadAt()).isEqualTo(NOW);
        Notification later = save(me, null);
        request(me, get(BASE)).andExpect(jsonPath("$.data.unreadCount").value(1));
        assertThat(notifications.findById(later.getId()).orElseThrow().getReadAt()).isNull();
    }

    @Test
    void 관련ID를_반환하고_대상삭제후에도_알림을_보존한다() throws Exception {
        Place place = places.save(Place.pinned("짐", null, "서울 강남구 역삼동 1", "1168010100",
                new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(127, 37.5))));
        Gathering gathering = gatherings.save(Gathering.open(other, place, "모임", null, null,
                NOW.plusSeconds(10800), Duration.ofHours(1), (short) 3, GenderRule.ANY));
        Notice notice = notices.save(Notice.builder().gathering(gathering).title("공지").content("본문").build());
        Notification n = notifications.save(Notification.builder().receiver(me).actor(other).gathering(gathering)
                .notice(notice).type("NOTICE_CREATED").message("새 공지").build());
        em.flush(); em.clear();
        request(me, get(BASE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].actorId").value(other.getId()))
                .andExpect(jsonPath("$.data.content[0].gatheringId").value(gathering.getId()))
                .andExpect(jsonPath("$.data.content[0].noticeId").value(notice.getId()));
        gatherings.deleteWithChildren(gathering.getId());
        request(me, get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].gatheringId").isEmpty())
                .andExpect(jsonPath("$.data.content[0].noticeId").isEmpty());
        request(me, patch(readPath(n))).andExpect(status().isOk());
    }

    @Test
    void 시스템알림과_새알림타입도_조회할수있다() throws Exception {
        save(me, null);
        request(me, get(BASE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].type").value("FUTURE_SYSTEM_EVENT"))
                .andExpect(jsonPath("$.data.content[0].actorId").isEmpty())
                .andExpect(jsonPath("$.data.content[0].buddyRequestId").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,101"})
    void 잘못된_페이지는_400(int page, int size) throws Exception {
        request(me, get(BASE).param("page", String.valueOf(page)).param("size", String.valueOf(size)))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/read-all", "/1/read"})
    void 세API_모두_인증이_필요하다(String suffix) throws Exception {
        mvc.perform(suffix.isEmpty() ? get(BASE) : patch(BASE + suffix)).andExpect(status().isUnauthorized());
    }

    private Notification save(User receiver, Instant readAt) {
        return notifications.save(Notification.builder().receiver(receiver).type("FUTURE_SYSTEM_EVENT")
                .message("테스트 알림").readAt(readAt).build());
    }

    private String readPath(Notification notification) { return BASE + "/" + notification.getId() + "/read"; }

    private ResultActions request(User viewer, MockHttpServletRequestBuilder request) throws Exception {
        em.flush();
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issueAccessToken(viewer.getId()).value()));
    }
}
