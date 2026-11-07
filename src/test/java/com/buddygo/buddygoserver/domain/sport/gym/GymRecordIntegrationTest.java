package com.buddygo.buddygoserver.domain.sport.gym;

import com.buddygo.buddygoserver.domain.sport.gym.entity.UserGymRecord;
import com.buddygo.buddygoserver.domain.sport.gym.enums.BodyPart;
import com.buddygo.buddygoserver.domain.sport.gym.repository.UserGymRecordRepository;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.enums.Gender;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.security.jwt.JwtTokenProvider;
import com.buddygo.buddygoserver.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 로컬 PostgreSQL(docker-compose.local.yml)이 떠 있어야 한다. DB 는 테스트마다 롤백된다. */
@Transactional
class GymRecordIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired UserGymRecordRepository userGymRecordRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired EntityManager entityManager;

    User user;
    String accessToken;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.signUpWithEmail("gym@example.com", "hash", "김버디", "헬스버디", Gender.MALE));
        accessToken = jwtTokenProvider.issueAccessToken(user.getId()).value();
    }

    @Nested
    class 조회 {

        @Test
        void 기록이_없으면_다섯_부위_모두_미설정으로_준다() throws Exception {
            getProfile()
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.records.length()").value(5))
                    .andExpect(jsonPath("$.data.records[*].bodyPart")
                            .value(org.hamcrest.Matchers.contains("CHEST", "BACK", "LEGS", "SHOULDER", "ARM")))
                    .andExpect(jsonPath("$.data.records[*].recorded")
                            .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(false))));
        }

        @Test
        void 토큰이_없으면_401() throws Exception {
            mockMvc.perform(get("/api/users/me/profile"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("G004"));
        }
    }

    @Nested
    class 저장 {

        @Test
        void 부위별_기록을_저장하고_저장된_프로필을_돌려준다() throws Exception {
            putRecords("""
                    {"records": [
                      {"bodyPart": "CHEST", "recordValue": 80},
                      {"bodyPart": "BACK", "recordValue": 0},
                      {"bodyPart": "ARM", "armLevel": "INTERMEDIATE"}
                    ]}""")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.records[0].bodyPart").value("CHEST"))
                    .andExpect(jsonPath("$.data.records[0].recorded").value(true))
                    .andExpect(jsonPath("$.data.records[0].recordValue").value(80))
                    .andExpect(jsonPath("$.data.records[1].recordValue").value(0))
                    .andExpect(jsonPath("$.data.records[2].recorded").value(false))
                    .andExpect(jsonPath("$.data.records[4].armLevel").value("INTERMEDIATE"))
                    .andExpect(jsonPath("$.data.records[4].recordValue").doesNotExist());

            flushAndClear();
            getProfile()
                    .andExpect(jsonPath("$.data.records[0].recordValue").value(80))
                    .andExpect(jsonPath("$.data.records[1].recorded").value(true))
                    .andExpect(jsonPath("$.data.records[4].armLevel").value("INTERMEDIATE"));
        }

        @Test
        void 다시_저장하면_보낸_부위가_전체가_되어_고치고_넣고_빠진_부위는_지운다() throws Exception {
            putRecords("""
                    {"records": [
                      {"bodyPart": "CHEST", "recordValue": 80},
                      {"bodyPart": "LEGS", "recordValue": 100}
                    ]}""").andExpect(status().isOk());
            flushAndClear();

            putRecords("""
                    {"records": [
                      {"bodyPart": "CHEST", "recordValue": 85},
                      {"bodyPart": "SHOULDER", "recordValue": 50}
                    ]}""").andExpect(status().isOk());
            flushAndClear();

            List<UserGymRecord> saved = userGymRecordRepository.findAllByIdUserId(user.getId()).stream()
                    .sorted(Comparator.comparing(UserGymRecord::getBodyPart)).toList();
            assertThat(saved).extracting(UserGymRecord::getBodyPart)
                    .containsExactly(BodyPart.CHEST, BodyPart.SHOULDER);
            assertThat(saved.get(0).getRecordValue()).isEqualByComparingTo("85");
            assertThat(saved.get(0).getUpdatedAt()).isNotNull();
        }

        @Test
        void 빈_배열이면_기록을_모두_지운다() throws Exception {
            putRecords("""
                    {"records": [{"bodyPart": "ARM", "armLevel": "BEGINNER"}]}""").andExpect(status().isOk());
            flushAndClear();

            putRecords("""
                    {"records": []}""")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.records.length()").value(5));
            flushAndClear();

            assertThat(userGymRecordRepository.findAllByIdUserId(user.getId())).isEmpty();
        }

        @Test
        void 저장된_기록은_다른_사용자에게_섞이지_않는다() throws Exception {
            User other = userRepository.save(
                    User.signUpWithEmail("other@example.com", "hash", "이버디", "다른버디", Gender.FEMALE));
            userGymRecordRepository.save(UserGymRecord.of(other, BodyPart.CHEST, new BigDecimal("40"), null));

            putRecords("""
                    {"records": [{"bodyPart": "CHEST", "recordValue": 80}]}""").andExpect(status().isOk());
            flushAndClear();

            assertThat(userGymRecordRepository.findAllByIdUserId(other.getId()))
                    .singleElement()
                    .satisfies(r -> assertThat(r.getRecordValue()).isEqualByComparingTo("40"));
        }

        @Test
        void 토큰이_없으면_401() throws Exception {
            mockMvc.perform(put("/api/users/me/gym-records")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"records\": []}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class 검증 {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource(delimiter = '|', value = {
                "{\"bodyPart\": \"CHEST\"}                                             | records[0].recordValue",
                "{\"bodyPart\": \"CHEST\", \"recordValue\": 0}                          | records[0].recordValue",
                "{\"bodyPart\": \"LEGS\", \"recordValue\": 301}                         | records[0].recordValue",
                "{\"bodyPart\": \"SHOULDER\", \"recordValue\": 50.5}                    | records[0].recordValue",
                "{\"bodyPart\": \"CHEST\", \"recordValue\": 0.5}                        | records[0].recordValue",
                "{\"bodyPart\": \"CHEST\", \"recordValue\": 80, \"armLevel\": \"BEGINNER\"} | records[0].armLevel",
                "{\"bodyPart\": \"BACK\", \"recordValue\": 10.5}                        | records[0].recordValue",
                "{\"bodyPart\": \"BACK\", \"recordValue\": -1}                          | records[0].recordValue",
                "{\"bodyPart\": \"BACK\", \"recordValue\": 101}                         | records[0].recordValue",
                "{\"bodyPart\": \"ARM\"}                                                | records[0].armLevel",
                "{\"bodyPart\": \"ARM\", \"recordValue\": 3, \"armLevel\": \"ADVANCED\"} | records[0].recordValue",
                "{\"recordValue\": 80}                                                  | records[0].bodyPart",
        })
        void 부위에_맞지_않거나_범위_밖이면_400(String record, String field) throws Exception {
            putRecords("{\"records\": [" + record + "]}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("G002"))
                    .andExpect(jsonPath("$.errors['" + field + "']").exists());
        }

        @Test
        void 허용_경계값은_저장된다() throws Exception {
            putRecords("""
                    {"records": [
                      {"bodyPart": "CHEST", "recordValue": 1},
                      {"bodyPart": "LEGS", "recordValue": 300},
                      {"bodyPart": "SHOULDER", "recordValue": 52.0},
                      {"bodyPart": "BACK", "recordValue": 100},
                      {"bodyPart": "ARM", "armLevel": "ADVANCED"}
                    ]}""")
                    .andExpect(status().isOk());
        }

        @Test
        void 같은_부위를_두_번_보내면_400() throws Exception {
            putRecords("""
                    {"records": [
                      {"bodyPart": "CHEST", "recordValue": 80},
                      {"bodyPart": "CHEST", "recordValue": 90}
                    ]}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.records").value("같은 부위를 두 번 보낼 수 없습니다."));
        }

        @Test
        void records_가_없으면_400() throws Exception {
            putRecords("{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.records").exists());
        }

        @Test
        void 없는_부위_이름이면_400() throws Exception {
            putRecords("""
                    {"records": [{"bodyPart": "NECK", "recordValue": 10}]}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("G003"));
        }
    }

    private ResultActions getProfile() throws Exception {
        return mockMvc.perform(get("/api/users/me/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private ResultActions putRecords(String body) throws Exception {
        return mockMvc.perform(put("/api/users/me/gym-records")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
