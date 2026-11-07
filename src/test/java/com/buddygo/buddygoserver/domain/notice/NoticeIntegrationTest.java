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
import com.buddygo.buddygoserver.domain.notification.repository.NotificationRepository;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.enums.Gender;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.security.jwt.JwtTokenProvider;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 로컬 PostgreSQL·Redis에서 실행. 테스트 데이터는 매번 롤백한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(NoticeIntegrationTest.FixedClockConfig.class)
@Transactional
class NoticeIntegrationTest {
    static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");
    @TestConfiguration
    static class FixedClockConfig {
        @Bean @Primary
        Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
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
    Gathering gathering;
    Participation memberParticipation;

    @BeforeEach
    void setup() {
        host = user("host"); member = user("member"); other = user("other"); outsider = user("outsider");
        gathering = gathering();
        memberParticipation = participation(member, ParticipationStatus.CONFIRMED);
        participation(other, ParticipationStatus.CONFIRMED);
    }

    @Test
    void 호스트가_작성하면_확정자에게만_알림이_생긴다() throws Exception {
        participation(outsider, ParticipationStatus.PENDING);
        request(host, post(base()), "{\"title\":\" 공지 \",\"content\":\" 내용 \"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.title").value("공지"))
                .andExpect(jsonPath("$.data.edited").value(false))
                .andExpect(jsonPath("$.data.authorId").value(host.getId()));
        em.flush();
        assertThat(notifications.findAll()).hasSize(2).allSatisfy(n -> {
            assertThat(n.getType()).isEqualTo("NOTICE_CREATED");
            assertThat(n.getReceiver().getId()).isIn(member.getId(), other.getId());
        });
    }

    @Test
    void 부분수정은_나머지를_유지하고_수정표시와_알림을_남긴다() throws Exception {
        Notice n = notice(gathering);
        request(host, patch(path(n)), "{\"title\":\"바뀐 제목\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("바뀐 제목"))
                .andExpect(jsonPath("$.data.content").value("본문"))
                .andExpect(jsonPath("$.data.edited").value(true))
                .andExpect(jsonPath("$.data.editedAt").value(NOW.toString()));
        request(member, get(path(n)), null).andExpect(status().isOk());
        assertThat(notifications.findAll()).hasSize(2).allMatch(noti -> noti.getType().equals("NOTICE_UPDATED"));
    }

    @Test
    void 확정참가자라도_공지를_작성_수정_삭제할수없다() throws Exception {
        Notice n = notice(gathering);
        request(member, post(base()), "{\"title\":\"공지\",\"content\":\"내용\"}").andExpect(status().isForbidden());
        request(member, patch(path(n)), "{\"title\":\"변경\"}").andExpect(status().isForbidden());
        request(member, delete(path(n)), null).andExpect(status().isForbidden());
        assertThat(notifications.count()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = ParticipationStatus.class, names = {"PENDING", "LEFT", "CANCELED", "REJECTED"})
    void 확정자가_아니면_조회와_댓글_접근불가(ParticipationStatus state) throws Exception {
        participation(outsider, state);
        Notice n = notice(gathering);
        request(outsider, get(base()), null).andExpect(status().isForbidden());
        request(outsider, get(path(n)), null).andExpect(status().isForbidden());
        request(outsider, get(path(n) + "/comments"), null).andExpect(status().isForbidden());
        request(outsider, post(path(n) + "/comments"), "{\"content\":\"댓글\"}").andExpect(status().isForbidden());
    }

    @Test
    void 외부인과_나간_작성자는_접근불가() throws Exception {
        Notice n = notice(gathering);
        NoticeComment c = comment(n, member);
        memberParticipation.leave(NOW);
        request(outsider, get(path(n)), null).andExpect(status().isForbidden());
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
        request(member, get(base()), null).andExpect(status().isOk());
        request(member, get(path(n)), null).andExpect(status().isOk());
        request(member, get(path(n) + "/comments"), null).andExpect(status().isOk());
        request(host, post(base()), "{\"title\":\"공지\",\"content\":\"본문\"}").andExpect(status().isConflict());
        request(host, patch(path(n)), "{\"title\":\"변경\"}").andExpect(status().isConflict());
        request(host, delete(path(n)), null).andExpect(status().isConflict());
        request(member, post(path(n) + "/comments"), "{\"content\":\"댓글\"}").andExpect(status().isConflict());
        request(host, delete(path(n) + "/comments/" + c.getId()), null).andExpect(status().isConflict());
        assertThat(notifications.count()).isZero();
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
        assertThat(notifications.findAll()).hasSize(2);
        assertThat(notifications.findAll().stream().map(x -> x.getReceiver().getId()).toList())
                .containsExactlyInAnyOrder(host.getId(), other.getId());
    }

    @Test
    void 호스트댓글은_본인에게_알리지않고_나간사람도_제외한다() throws Exception {
        Notice n = notice(gathering);
        comment(n, member); comment(n, other);
        memberParticipation.leave(NOW);
        request(host, post(path(n) + "/comments"), "{\"content\":\"확인\"}").andExpect(status().isCreated());
        assertThat(notifications.findAll()).hasSize(1);
        assertThat(notifications.findAll().getFirst().getReceiver().getId()).isEqualTo(other.getId());
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
    void 공지삭제는_댓글을_지우고_알림연결만_해제한다() throws Exception {
        String json = request(host, post(base()), "{\"title\":\"공지\",\"content\":\"내용\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(json, "$.data.noticeId")).longValue();
        Notice n = notices.findById(id).orElseThrow();
        comment(n, member);
        // 실제로는 작성과 삭제가 별도 요청이므로 영속성 컨텍스트도 분리된다.
        em.flush(); em.clear();
        request(host, delete(path(n)), null).andExpect(status().isOk());
        em.flush(); em.clear();
        assertThat(notices.findById(id)).isEmpty();
        assertThat(comments.count()).isZero();
        assertThat(notifications.findAll()).hasSize(2).allMatch(x -> x.getNotice() == null);
    }

    @Test
    void 다른모임_공지와_다른공지_댓글을_조작할수없다() throws Exception {
        Notice n = notice(gathering);
        Notice foreign = notice(gathering());
        NoticeComment c = comment(foreign, member);
        String wrong = base() + "/" + foreign.getId();
        request(host, get(wrong), null).andExpect(status().isNotFound());
        request(host, patch(wrong), "{\"title\":\"변경\"}").andExpect(status().isNotFound());
        request(host, delete(wrong), null).andExpect(status().isNotFound());
        request(host, post(wrong + "/comments"), "{\"content\":\"댓글\"}").andExpect(status().isNotFound());
        request(host, delete(path(n) + "/comments/" + c.getId()), null).andExpect(status().isNotFound());
        assertThat(notifications.count()).isZero();
    }

    @Test
    void 목록은_모임별로_정렬하고_페이지를_나눈다() throws Exception {
        Notice first = notice(gathering);
        Notice second = notice(gathering);
        notice(gathering());
        request(member, get(base()).param("size", "1"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].noticeId").value(second.getId()))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        request(member, get(base()).param("size", "1").param("page", "1"), null)
                .andExpect(jsonPath("$.data.content[0].noticeId").value(first.getId()))
                .andExpect(jsonPath("$.data.hasNext").value(false));
        NoticeComment c = comment(first, member); comment(first, host);
        request(member, get(path(first) + "/comments").param("size", "1"), null)
                .andExpect(jsonPath("$.data.content[0].commentId").value(c.getId()))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        request(member, get(base()).param("page", "99"), null).andExpect(jsonPath("$.data.content").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"title\":\" \"}", "{\"title\":\"\u2003\"}", "{\"content\":\"\"}", "{\"title\":null,\"content\":null}"})
    void 잘못된_수정요청은_400(String body) throws Exception {
        Notice n = notice(gathering);
        request(host, patch(path(n)), body).andExpect(status().isBadRequest());
        assertThat(notifications.count()).isZero();
    }

    @Test
    void 입력길이와_공백을_검사한다() throws Exception {
        Notice n = notice(gathering);
        request(host, post(base()), "{\"title\":\" \" ,\"content\":\"본문\"}").andExpect(status().isBadRequest());
        request(host, post(base()), "{\"title\":\"" + "가".repeat(256) + "\",\"content\":\"본문\"}")
                .andExpect(status().isBadRequest());
        request(host, patch(path(n)), "{\"content\":\"" + "가".repeat(201) + "\"}").andExpect(status().isBadRequest());
        request(member, post(path(n) + "/comments"), "{\"content\":\" \"}").andExpect(status().isBadRequest());
        request(member, post(path(n) + "/comments"), "{\"content\":\"" + "가".repeat(201) + "\"}")
                .andExpect(status().isBadRequest());
        request(host, post(base()), "{\"title\":\"" + "가".repeat(255) + "\",\"content\":\"" + "가".repeat(200) + "\"}")
                .andExpect(status().isCreated());
    }

    @Test
    void 삭제모임과_인증누락_잘못된페이지를_검사한다() throws Exception {
        Notice n = notice(gathering);
        mvc.perform(get(base())).andExpect(status().isUnauthorized());
        mvc.perform(post(base()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        request(host, get(base()).param("page", "-1"), null).andExpect(status().isBadRequest());
        request(host, get(path(n) + "/comments").param("size", "101"), null).andExpect(status().isBadRequest());
        gathering.delete(NOW);
        request(host, get(base()), null).andExpect(status().isNotFound());
        request(host, get(path(n)), null).andExpect(status().isNotFound());
        request(host, post(path(n) + "/comments"), "{\"content\":\"댓글\"}").andExpect(status().isNotFound());
    }

    private User user(String name) {
        return users.save(User.signUpWithEmail("notice-" + name + "@example.com", "hash", name, "notice-" + name, Gender.MALE));
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
