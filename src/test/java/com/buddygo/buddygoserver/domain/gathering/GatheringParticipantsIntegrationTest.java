package com.buddygo.buddygoserver.domain.gathering;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
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
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import java.util.List;
import java.util.Map;

import static com.buddygo.buddygoserver.domain.gathering.GatheringIntegrationTest.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GatheringParticipantsIntegrationTest extends IntegrationTest {
    @Override
    protected Instant fixedNow() { return NOW; }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired GatheringRepository gatherings;
    @Autowired PlaceRepository places;
    @Autowired ParticipationRepository participations;
    @Autowired JwtTokenProvider tokens;
    @Autowired EntityManager em;
    User host, member, outsider;
    Gathering gathering;
    Participation membership;

    @BeforeEach
    void setup() {
        host = user("host"); member = user("member"); outsider = user("outsider");
        gathering = gathering(host);
        membership = participation(gathering, member, ParticipationStatus.CONFIRMED);
    }

    @ParameterizedTest
    @EnumSource(GatheringStatus.class)
    void 호스트는_모든상태에서_조회한다(GatheringStatus state) throws Exception {
        changeStatus(state);
        request(host, path()).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
    }

    @ParameterizedTest
    @EnumSource(value = GatheringStatus.class, names = {"CLOSED", "IN_PROGRESS", "FINISHED"})
    void 확정참가자는_마감이후에_조회한다(GatheringStatus state) throws Exception {
        changeStatus(state);
        request(member, path()).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = GatheringStatus.class, names = {"RECRUITING", "CANCELED"})
    void 참가자는_모집중이나_취소상태에서_조회할수없다(GatheringStatus state) throws Exception {
        changeStatus(state);
        request(member, path()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("GA006"));
    }

    @ParameterizedTest
    @EnumSource(value = ParticipationStatus.class, names = {"PENDING", "REJECTED", "LEFT"})
    void 미확정자는_마감되어도_명단에_접근할수없다(ParticipationStatus state) throws Exception {
        gathering.close();
        participation(gathering, outsider, state);
        request(outsider, path()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("GA006"));
        request(host, path()).andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void 외부인과_다른모임의_호스트는_조회할수없다() throws Exception {
        gathering.close();
        gathering(outsider);
        request(outsider, path()).andExpect(status().isForbidden());
    }

    @Test
    void 마감이_풀리거나_나가면_조회권한을_잃는다() throws Exception {
        gathering.close();
        request(member, path()).andExpect(status().isOk());
        gathering.reopen();
        request(member, path()).andExpect(status().isForbidden());
        gathering.close();
        membership.leave(NOW);
        request(member, path()).andExpect(status().isForbidden());
        request(host, path()).andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void 재신청자는_현재확정기록만_한번_표시한다() throws Exception {
        membership.leave(NOW);
        em.flush(); // 실제 나가기 요청이 먼저 저장된 뒤 재신청한다.
        participation(gathering, member, ParticipationStatus.CONFIRMED);
        gathering.close();
        request(member, path()).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void 호스트를_먼저_표시하고_상세명단과_같은_공개필드만_반환한다() throws Exception {
        gathering.close();
        // 호스트보다 빠른 확정 시각이어도 호스트가 먼저 온다.
        membership.confirm(NOW.minusSeconds(60));
        em.flush(); em.clear();
        String json = request(member, path()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(host.getId()))
                .andExpect(jsonPath("$.data[0].host").value(true))
                .andExpect(jsonPath("$.data[1].userId").value(member.getId()))
                .andExpect(jsonPath("$.data[1].host").value(false))
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> data = JsonPath.read(json, "$.data");
        assertThat(data).allSatisfy(row -> assertThat(row.keySet())
                .containsExactlyInAnyOrder("userId", "nickname", "mannerTemperature", "host"));
        String detail = request(member, "/api/gatherings/" + gathering.getId()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> detailMembers = JsonPath.read(detail, "$.data.members");
        assertThat(data).isEqualTo(detailMembers);
    }

    @Test
    void 없는모임과_삭제된모임은_404_인증누락은_401이다() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        request(host, "/api/gatherings/9223372036854775807/participants")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GA001"));
        gatherings.deleteWithChildren(gathering.getId());
        request(host, path()).andExpect(status().isNotFound());
    }

    private void changeStatus(GatheringStatus state) {
        switch (state) {
            case RECRUITING -> { }
            case CLOSED -> gathering.close();
            case IN_PROGRESS -> { gathering.close(); gathering.start(); }
            case FINISHED -> { gathering.close(); gathering.start(); gathering.finish(); }
            case CANCELED -> gathering.cancel();
        }
    }

    private User user(String name) {
        return users.save(User.signUpWithEmail("roster-" + name + "@example.com", "hash", name, "roster-" + name, Gender.MALE));
    }

    private Gathering gathering(User host) {
        Place place = places.save(Place.pinned("버디짐", null, "서울 강남구 역삼동 1", "1168010100",
                new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(127, 37.5))));
        Gathering result = gatherings.save(Gathering.open(host, place, "모임", null, null,
                NOW.plusSeconds(10800), Duration.ofHours(1), (short) 3, GenderRule.ANY));
        participations.save(Participation.hostOf(result, NOW));
        return result;
    }

    private Participation participation(Gathering target, User user, ParticipationStatus state) {
        return participations.save(Participation.builder().gathering(target).user(user).status(state)
                .appliedAt(NOW).decidedAt(NOW).build());
    }

    private String path() { return "/api/gatherings/" + gathering.getId() + "/participants"; }

    private ResultActions request(User viewer, String path) throws Exception {
        em.flush();
        return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION,
                "Bearer " + tokens.issueAccessToken(viewer.getId()).value()));
    }
}
