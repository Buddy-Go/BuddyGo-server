package com.buddygo.buddygoserver.domain.notice;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.entity.Place;
import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.PlaceRepository;
import com.buddygo.buddygoserver.domain.notice.dto.request.CommentCreateRequest;
import com.buddygo.buddygoserver.domain.notice.entity.Notice;
import com.buddygo.buddygoserver.domain.notice.repository.NoticeRepository;
import com.buddygo.buddygoserver.domain.notice.service.NoticeCommentCommandService;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.enums.Gender;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.security.jwt.JwtTokenProvider;
import com.buddygo.buddygoserver.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.buddygo.buddygoserver.domain.notice.NoticeCommentIntegrationTest.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 요청마다 실제 커밋한다. 테스트가 만든 모임·사용자만 정리한다. */
class NoticeCommentTransactionIntegrationTest extends IntegrationTest {
    @Override
    protected Instant fixedNow() {
        return NOW;
    }

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired NoticeCommentCommandService commands;
    @Autowired NoticeRepository notices;
    @Autowired UserRepository users;
    @Autowired PlaceRepository places;
    @Autowired GatheringRepository gatherings;
    @Autowired ParticipationRepository participations;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    Long hostId, memberId, otherId, gatheringId, placeId;

    @BeforeEach
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            User host = user("h" + suffix);
            User member = user("m" + suffix);
            User other = user("o" + suffix);
            hostId = host.getId(); memberId = member.getId(); otherId = other.getId();
            Place place = places.save(Place.pinned("트랜잭션 테스트", null, "서울 강남구 역삼동 1", "1168010100",
                    new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(127, 37.5))));
            placeId = place.getId();
            Gathering gathering = gatherings.save(Gathering.open(host, place, "테스트", null, null,
                    NOW.plusSeconds(10800), Duration.ofHours(1), (short) 3, GenderRule.ANY));
            gatheringId = gathering.getId();
            participations.save(Participation.hostOf(gathering, NOW));
            for (User participant : new User[]{member, other}) {
                participations.save(Participation.builder().gathering(gathering).user(participant)
                        .status(ParticipationStatus.CONFIRMED).appliedAt(NOW).build());
            }
        });
    }

    @AfterEach
    void cleanup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("delete from notifications where gathering_id = ?", gatheringId);
            jdbc.update("delete from notices where gathering_id = ?", gatheringId);
            jdbc.update("delete from participations where gathering_id = ?", gatheringId);
            jdbc.update("delete from gatherings where id = ?", gatheringId);
            jdbc.update("delete from places where id = ?", placeId);
            jdbc.update("delete from users where id in (?, ?, ?)", hostId, memberId, otherId);
        });
    }

    @Test
    void 동시에_댓글을_달아도_앞선_댓글작성자의_알림을_놓치지않는다() throws Exception {
        long noticeId = createNotice();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("시작 대기 초과");
                return commands.comment(memberId, gatheringId, noticeId, new CommentCreateRequest("첫 참가자"));
            });
            var second = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("시작 대기 초과");
                return commands.comment(otherId, gatheringId, noticeId, new CommentCreateRequest("둘째 참가자"));
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(jdbc.queryForObject("select count(*) from notice_comments where notice_id = ?", Long.class, noticeId)).isEqualTo(2);
        // 각 댓글의 호스트 알림 2개 + 먼저 쓴 참가자에게 보내는 알림 1개.
        assertThat(jdbc.queryForObject("select count(*) from notifications where notice_id = ? and type = 'NOTICE_COMMENT_CREATED'",
                Long.class, noticeId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from notifications where notice_id = ? and receiver_id = actor_id",
                Long.class, noticeId)).isZero();
    }

    @Test
    void 별도요청에서_댓글작성_조회_삭제가_커밋된다() throws Exception {
        long noticeId = createNotice();
        String url = "/api/gatherings/" + gatheringId + "/notices/" + noticeId + "/comments";
        String json = mvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, auth(memberId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"댓글\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long commentId = ((Number) JsonPath.read(json, "$.data.commentId")).longValue();
        mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, auth(hostId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].authorId").value(memberId));
        mvc.perform(delete(url + "/" + commentId).header(HttpHeaders.AUTHORIZATION, auth(memberId)))
                .andExpect(status().isOk());
        mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, auth(hostId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty());
        assertThat(jdbc.queryForObject("select count(*) from notifications where notice_id = ?", Long.class, noticeId)).isEqualTo(1);
    }

    @Test
    void 실패시_댓글과_알림이_함께_롤백된다() {
        long noticeId = createNotice();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            commands.comment(memberId, gatheringId, noticeId, new CommentCreateRequest("댓글"));
            throw new IllegalStateException("롤백 검증");
        })).isInstanceOf(IllegalStateException.class).hasMessage("롤백 검증");
        assertThat(jdbc.queryForObject("select count(*) from notice_comments where notice_id = ?", Long.class, noticeId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from notifications where notice_id = ?", Long.class, noticeId)).isZero();
    }

    private long createNotice() {
        return new TransactionTemplate(transactionManager).execute(status -> notices.save(
                Notice.builder()
                        .gathering(gatherings.findById(gatheringId).orElseThrow()).title("공지").content("본문").build()).getId());
    }

    private User user(String name) {
        return users.save(User.signUpWithEmail(name + "@example.com", "hash", name, name, Gender.MALE));
    }

    private String auth(Long userId) { return "Bearer " + tokens.issueAccessToken(userId).value(); }
}
