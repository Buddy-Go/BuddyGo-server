package com.buddygo.buddygoserver.domain.notice;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.entity.Place;
import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.PlaceRepository;
import com.buddygo.buddygoserver.domain.notice.entity.Notice;
import com.buddygo.buddygoserver.domain.notice.entity.NoticeComment;
import com.buddygo.buddygoserver.domain.notice.repository.NoticeRepository;
import com.buddygo.buddygoserver.domain.notice.repository.NoticeCommentRepository;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 로컬 PostgreSQL·Redis에서 실행. 테스트 데이터는 매번 롤백한다. */
@Transactional
class NoticeCommentIntegrationTest extends IntegrationTest {
    static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

    @Override
    protected Instant fixedNow() {
        return NOW;
    }
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired GatheringRepository gatherings;
    @Autowired PlaceRepository places;
    @Autowired ParticipationRepository participations;
    @Autowired NoticeRepository notices;
    @Autowired NoticeCommentRepository comments;
    @Autowired NotificationRepository notifications;
    @Autowired JwtTokenProvider tokens;
    @Autowired EntityManager em;
    User host, member, other, outsider;
    private final List<Long> userIds = new ArrayList<>();
    Gathering gathering;
    Participation memberParticipation;

    @BeforeEach
    void setup() {
        host = user("host"); member = user("member"); other = user("other"); outsider = user("outsider");
        gathering = gathering();
        memberParticipation = participation(member, ParticipationStatus.CONFIRMED);
        participation(other, ParticipationStatus.CONFIRMED);
    }

    @ParameterizedTest
    @EnumSource(value = ParticipationStatus.class, names = {"PENDING", "LEFT", "REJECTED"})
    void 확정자가_아니면_조회와_댓글_접근불가(ParticipationStatus state) throws Exception {
        participation(outsider, state);
        Notice n = notice(gathering);
        request(outsider, get(path(n) + "/comments"), null).andExpect(status().isForbidden());
        request(outsider, post(path(n) + "/comments"), "{\"content\":\"댓글\"}").andExpect(status().isForbidden());
    }

    @Test
    void 외부인과_나간_작성자는_접근불가() throws Exception {
        Notice n = notice(gathering);
        NoticeComment c = comment(n, member);
        memberParticipation.leave(NOW);
        request(member, delete(path(n) + "/comments/" + c.getId()), null).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELED", "FINISHED", "EXPIRED", "UNFILLED"})
    void 읽기전용이면_조회만_가능하다(String state) throws Exception {
        Notice n = notice(gathering);
        NoticeComment c = comment(n, member);
        if (state.equals("CANCELED")) gathering.cancel();
        if (state.equals("FINISHED")) gathering.finish();
        if (state.equals("EXPIRED")) {
            gathering.close();
            gathering.changeCore(gathering.getPlace(), NOW.minusSeconds(3600), Duration.ofHours(1), (short) 3, GenderRule.ANY);
        }
        if (state.equals("UNFILLED")) {
            gathering.changeCore(gathering.getPlace(), NOW.plusSeconds(5400), Duration.ofHours(1), (short) 3, GenderRule.ANY);
        }
        request(member, get(path(n) + "/comments"), null).andExpect(status().isOk());
        request(member, post(path(n) + "/comments"), "{\"content\":\"댓글\"}").andExpect(status().isConflict());
        request(host, delete(path(n) + "/comments/" + c.getId()), null).andExpect(status().isConflict());
        assertThat(myNotifications().size()).isZero();
    }

    @Test
    void 진행중에는_댓글을_쓸수있다() throws Exception {
        gathering.close();
        gathering.changeCore(gathering.getPlace(), NOW.minusSeconds(60), Duration.ofHours(1), (short) 3, GenderRule.ANY);
        gathering.start();
        Notice n = notice(gathering);
        request(member, post(path(n) + "/comments"), "{\"content\":\"곧 도착해요\"}")
                .andExpect(status().isCreated());
    }

    @Test
    void 댓글알림은_호스트와_이미댓글단_확정자에게_중복없이_간다() throws Exception {
        Notice n = notice(gathering);
        comment(n, other); comment(n, other); comment(n, outsider);
        request(member, post(path(n) + "/comments"), "{\"content\":\"좋아요\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.authorId").value(member.getId()));
        assertThat(myNotifications()).hasSize(2);
        assertThat(myNotifications().stream().map(x -> x.getReceiver().getId()).toList())
                .containsExactlyInAnyOrder(host.getId(), other.getId());
    }

    @Test
    void 호스트댓글은_본인에게_알리지않고_나간사람도_제외한다() throws Exception {
        Notice n = notice(gathering);
        comment(n, member); comment(n, other);
        memberParticipation.leave(NOW);
        request(host, post(path(n) + "/comments"), "{\"content\":\"확인\"}").andExpect(status().isCreated());
        assertThat(myNotifications()).hasSize(1);
        assertThat(myNotifications().getFirst().getReceiver().getId()).isEqualTo(other.getId());
    }

    @Test
    void 댓글삭제는_작성자와_호스트만_가능하다() throws Exception {
        Notice n = notice(gathering);
        NoticeComment mine = comment(n, member);
        NoticeComment theirs = comment(n, other);
        request(member, delete(path(n) + "/comments/" + theirs.getId()), null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("N003"));
        request(member, delete(path(n) + "/comments/" + mine.getId()), null).andExpect(status().isOk());
        request(host, delete(path(n) + "/comments/" + theirs.getId()), null).andExpect(status().isOk());
        em.flush();
        assertThat(comments.count()).isZero();
    }

    @Test
    void 다른모임_공지와_다른공지_댓글을_조작할수없다() throws Exception {
        Notice n = notice(gathering);
        Notice foreign = notice(gathering());
        NoticeComment c = comment(foreign, member);
        String wrong = base() + "/" + foreign.getId();
        request(host, post(wrong + "/comments"), "{\"content\":\"댓글\"}").andExpect(status().isNotFound());
        request(host, delete(path(n) + "/comments/" + c.getId()), null).andExpect(status().isNotFound());
        assertThat(myNotifications().size()).isZero();
    }

    @Test
    void 댓글목록은_공지별로_작성순_페이지를_나눈다() throws Exception {
        Notice n = notice(gathering);
        NoticeComment first = comment(n, member);
        NoticeComment second = comment(n, host);
        comment(notice(gathering), other);
        request(member, get(path(n) + "/comments").param("size", "1"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].commentId").value(first.getId()))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        request(member, get(path(n) + "/comments").param("size", "1").param("page", "1"), null)
                .andExpect(jsonPath("$.data.content[0].commentId").value(second.getId()))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 댓글입력과_페이지_인증_삭제모임을_검사한다() throws Exception {
        Notice n = notice(gathering);
        String url = path(n) + "/comments";
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        request(outsider, get(url), null).andExpect(status().isForbidden());
        request(member, post(url), "{\"content\":\" \"}").andExpect(status().isBadRequest());
        request(member, post(url), "{\"content\":\"" + "가".repeat(201) + "\"}").andExpect(status().isBadRequest());
        request(member, post(url), "{\"content\":\"" + "가".repeat(200) + "\"}").andExpect(status().isCreated());
        request(member, get(url).param("page", "-1"), null).andExpect(status().isBadRequest());
        request(member, get(url).param("size", "101"), null).andExpect(status().isBadRequest());
        gatherings.deleteWithChildren(gathering.getId());
        request(member, get(url), null).andExpect(status().isNotFound());
        request(member, post(url), "{\"content\":\"댓글\"}").andExpect(status().isNotFound());
    }

    private User user(String name) {
        User user = users.save(User.signUpWithEmail("notice-" + name + "@example.com", "hash", name, "notice-" + name, Gender.MALE));
        userIds.add(user.getId());
        return user;
    }

    /** 이 테스트가 만든 사람들의 알림만. 공유 로컬 DB 에 남아 있는 다른 알림은 세지 않는다. */
    private List<Notification> myNotifications() {
        return notifications.findAll().stream().filter(n -> userIds.contains(n.getReceiver().getId())).toList();
    }
    private Gathering gathering() {
        var place = places.save(Place.pinned("버디짐", null, "서울 강남구 역삼동 1", "1168010100",
                new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(127, 37.5))));
        var g = gatherings.save(Gathering.open(host, place, "모임", null, null,
                NOW.plusSeconds(10800), Duration.ofHours(1), (short) 3, GenderRule.ANY));
        participations.save(Participation.hostOf(g, NOW));
        return g;
    }
    private Participation participation(User user, ParticipationStatus state) {
        return participations.save(Participation.builder().gathering(gathering).user(user).status(state).appliedAt(NOW).build());
    }
    private Notice notice(Gathering g) { return notices.save(Notice.builder().gathering(g).title("공지").content("본문").build()); }
    private NoticeComment comment(Notice n, User author) {
        return comments.save(NoticeComment.builder().notice(n).author(author).content("댓글").build());
    }
    private String base() { return "/api/gatherings/" + gathering.getId() + "/notices"; }
    private String path(Notice n) { return base() + "/" + n.getId(); }
    private ResultActions request(User user, MockHttpServletRequestBuilder request, String body) throws Exception {
        em.flush();
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issueAccessToken(user.getId()).value());
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }
}
