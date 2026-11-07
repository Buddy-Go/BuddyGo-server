package com.buddygo.buddygoserver.domain.notice;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.entity.Place;
import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.PlaceRepository;
import com.buddygo.buddygoserver.domain.notice.dto.request.NoticeCreateRequest;
import com.buddygo.buddygoserver.domain.notice.service.NoticeCommandService;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.enums.Gender;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.security.jwt.JwtTokenProvider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.UUID;

import static com.buddygo.buddygoserver.domain.notice.NoticeIntegrationTest.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 요청마다 실제 커밋한다. 테스트가 만든 모임·사용자만 정리한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(NoticeIntegrationTest.FixedClockConfig.class)
class NoticeTransactionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired NoticeCommandService commands;
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
    void 별도요청에서_작성_조회_수정_댓글_삭제까지_커밋된다() throws Exception {
        String base = "/api/gatherings/" + gatheringId + "/notices";
        String json = mvc.perform(post(base).header(HttpHeaders.AUTHORIZATION, auth(hostId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"공지\",\"content\":\"본문\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long noticeId = ((Number) JsonPath.read(json, "$.data.noticeId")).longValue();
        String path = base + "/" + noticeId;
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, auth(memberId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.authorId").value(hostId));
        mvc.perform(patch(path).header(HttpHeaders.AUTHORIZATION, auth(hostId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"변경\"}"))
                .andExpect(status().isOk());
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, auth(memberId)))
                .andExpect(jsonPath("$.data.content").value("변경"))
                .andExpect(jsonPath("$.data.edited").value(true));
        mvc.perform(delete(path).header(HttpHeaders.AUTHORIZATION, auth(hostId))).andExpect(status().isOk());
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, auth(memberId))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from notice_comments where notice_id = ?", Long.class, noticeId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from notifications where gathering_id = ? and notice_id is null",
                Long.class, gatheringId)).isEqualTo(4);
    }

    @Test
    void 트랜잭션이_실패하면_공지와_알림이_모두_롤백된다() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            commands.create(hostId, gatheringId, new NoticeCreateRequest("공지", "본문"));
            throw new IllegalStateException("강제 롤백 검증");
        })).isInstanceOf(IllegalStateException.class).hasMessage("강제 롤백 검증");
        assertThat(jdbc.queryForObject("select count(*) from notices where gathering_id = ?", Long.class, gatheringId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from notifications where gathering_id = ?", Long.class, gatheringId)).isZero();
    }

    private User user(String name) {
        return users.save(User.signUpWithEmail(name + "@example.com", "hash", name, name, Gender.MALE));
    }

    private String auth(Long userId) { return "Bearer " + tokens.issueAccessToken(userId).value(); }
}
