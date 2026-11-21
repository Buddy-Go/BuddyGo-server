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
        request(member, post(path(n) + "/comments/" + c.getId() + "/replies"), "{\"content\":\"답글\"}")
                .andExpect(status().isConflict());
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
        reply(comment(n, member), host);
        gatherings.deleteWithChildren(gathering.getId());
        request(member, get(url), null).andExpect(status().isNotFound());
        request(member, post(url), "{\"content\":\"댓글\"}").andExpect(status().isNotFound());
    }

    @Test
    void 답글은_원댓글아래_작성순으로_담기고_페이지는_원댓글로_나눈다() throws Exception {
        Notice n = notice(gathering);
        NoticeComment first = comment(n, member);
        NoticeComment second = comment(n, other);
        request(other, post(path(n) + "/comments/" + first.getId() + "/replies"), "{\"content\":\" 답글1 \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.parentCommentId").value(first.getId()))
                .andExpect(jsonPath("$.data.content").value("답글1"))
                .andExpect(jsonPath("$.data.replies").isEmpty());
        request(host, post(path(n) + "/comments/" + first.getId() + "/replies"), "{\"content\":\"답글2\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.authorHost").value(true));
        request(member, get(path(n) + "/comments").param("size", "1"), null)
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].commentId").value(first.getId()))
                .andExpect(jsonPath("$.data.content[0].parentCommentId").isEmpty())
                .andExpect(jsonPath("$.data.content[0].replies[0].content").value("답글1"))
                .andExpect(jsonPath("$.data.content[0].replies[1].content").value("답글2"))
                .andExpect(jsonPath("$.data.content[0].replies[1].parentCommentId").value(first.getId()))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        request(member, get(path(n) + "/comments").param("size", "1").param("page", "1"), null)
                .andExpect(jsonPath("$.data.content[0].commentId").value(second.getId()))
                .andExpect(jsonPath("$.data.content[0].replies").isEmpty())
                .andExpect(jsonPath("$.data.hasNext").value(false));
        request(member, get(path(n)), null).andExpect(jsonPath("$.data.commentCount").value(4));
        request(member, get(base()), null).andExpect(jsonPath("$.data.content[0].commentCount").value(4));
    }

    @Test
    void 답글에는_답글을_달수없고_없는댓글_다른공지댓글에도_달수없다() throws Exception {
        Notice n = notice(gathering);
        NoticeComment root = comment(n, member);
        NoticeComment child = reply(root, other);
        NoticeComment foreign = comment(notice(gathering), other);
        request(member, post(path(n) + "/comments/" + child.getId() + "/replies"), "{\"content\":\"답글\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("N006"));
        request(member, post(path(n) + "/comments/" + foreign.getId() + "/replies"), "{\"content\":\"답글\"}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("N002"));
        request(member, post(path(n) + "/comments/" + root.getId() + "/replies"), "{\"content\":\" \"}")
                .andExpect(status().isBadRequest());
        request(outsider, post(path(n) + "/comments/" + root.getId() + "/replies"), "{\"content\":\"답글\"}")
                .andExpect(status().isForbidden());
        assertThat(myNotifications()).isEmpty();
    }

    @Test
    void 답글알림은_원댓글작성자에게_답글알림_나머지는_댓글알림으로_한번씩() throws Exception {
        Notice n = notice(gathering);
        NoticeComment root = comment(n, member);
        comment(n, other);
        request(other, post(path(n) + "/comments/" + root.getId() + "/replies"), "{\"content\":\"답글\"}")
                .andExpect(status().isCreated());
        assertThat(myNotifications()).extracting(x -> x.getReceiver().getId(), Notification::getType)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(member.getId(), "NOTICE_COMMENT_REPLIED"),
                        org.assertj.core.groups.Tuple.tuple(host.getId(), "NOTICE_COMMENT_CREATED"));
    }

    @Test
    void 원댓글작성자가_호스트면_답글알림만_받고_자기댓글에_단_답글은_알리지않는다() throws Exception {
        Notice n = notice(gathering);
        NoticeComment hostRoot = comment(n, host);
        request(member, post(path(n) + "/comments/" + hostRoot.getId() + "/replies"), "{\"content\":\"답글\"}")
                .andExpect(status().isCreated());
        assertThat(myNotifications()).extracting(x -> x.getReceiver().getId(), Notification::getType)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(host.getId(), "NOTICE_COMMENT_REPLIED"));
        notifications.deleteAll(myNotifications());

        request(host, post(path(n) + "/comments/" + hostRoot.getId() + "/replies"), "{\"content\":\"추가\"}")
                .andExpect(status().isCreated());
        assertThat(myNotifications()).extracting(x -> x.getReceiver().getId(), Notification::getType)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(member.getId(), "NOTICE_COMMENT_CREATED"));
    }

    @Test
    void 나간_원댓글작성자는_답글알림을_받지않는다() throws Exception {
        Notice n = notice(gathering);
        NoticeComment root = comment(n, member);
        memberParticipation.leave(NOW);
        request(other, post(path(n) + "/comments/" + root.getId() + "/replies"), "{\"content\":\"답글\"}")
                .andExpect(status().isCreated());
        assertThat(myNotifications()).extracting(x -> x.getReceiver().getId()).containsExactly(host.getId());
    }

    @Test
    void 답글이_남은_원댓글은_삭제표시되고_마지막답글이_지워지면_함께_지워진다() throws Exception {
        Notice n = notice(gathering);
        NoticeComment root = comment(n, member);
        NoticeComment r1 = reply(root, other);
        NoticeComment r2 = reply(root, host);
        request(member, delete(path(n) + "/comments/" + root.getId()), null).andExpect(status().isOk());
        request(other, get(path(n) + "/comments"), null)
                .andExpect(jsonPath("$.data.content[0].commentId").value(root.getId()))
                .andExpect(jsonPath("$.data.content[0].deleted").value(true))
                .andExpect(jsonPath("$.data.content[0].content").isEmpty())
                .andExpect(jsonPath("$.data.content[0].authorId").isEmpty())
                .andExpect(jsonPath("$.data.content[0].replies.length()").value(2));
        request(other, get(path(n)), null).andExpect(jsonPath("$.data.commentCount").value(2));
        request(host, delete(path(n) + "/comments/" + root.getId()), null)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("N002"));
        request(other, post(path(n) + "/comments/" + root.getId() + "/replies"), "{\"content\":\"답글\"}")
                .andExpect(status().isNotFound());

        request(other, delete(path(n) + "/comments/" + r1.getId()), null).andExpect(status().isOk());
        em.flush(); em.clear();
        assertThat(comments.findById(root.getId())).isPresent();
        request(host, delete(path(n) + "/comments/" + r2.getId()), null).andExpect(status().isOk());
        em.flush(); em.clear();
        assertThat(comments.findById(root.getId())).isEmpty();
        request(other, get(path(n) + "/comments"), null).andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    void 삭제표시되지_않은_원댓글의_답글을_지우면_원댓글은_남는다() throws Exception {
        Notice n = notice(gathering);
        NoticeComment root = comment(n, member);
        NoticeComment r = reply(root, other);
        request(member, delete(path(n) + "/comments/" + r.getId()), null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("N003"));
        request(host, delete(path(n) + "/comments/" + r.getId()), null).andExpect(status().isOk());
        em.flush(); em.clear();
        assertThat(comments.findById(root.getId())).get().extracting(NoticeComment::isDeleted).isEqualTo(false);
        assertThat(comments.findById(r.getId())).isEmpty();
    }

    @Test
    void 답글이_있는_공지를_지우면_댓글과_답글이_함께_지워진다() throws Exception {
        Notice n = notice(gathering);
        NoticeComment root = comment(n, member);
        reply(root, other);
        request(member, delete(path(n) + "/comments/" + root.getId()), null).andExpect(status().isOk());
        reply(comment(n, other), host);
        request(host, delete(path(n)), null).andExpect(status().isOk());
        em.flush(); em.clear();
        assertThat(em.createQuery("select count(c) from NoticeComment c where c.notice.id = :id", Long.class)
                .setParameter("id", n.getId()).getSingleResult()).isZero();
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
    private NoticeComment reply(NoticeComment parent, User author) {
        return comments.save(NoticeComment.replyTo(parent, author, "답글"));
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
