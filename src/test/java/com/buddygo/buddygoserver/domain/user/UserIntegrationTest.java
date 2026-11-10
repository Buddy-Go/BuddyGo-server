package com.buddygo.buddygoserver.domain.user;

import com.buddygo.buddygoserver.domain.user.entity.DeviceToken;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.enums.Gender;
import com.buddygo.buddygoserver.domain.user.repository.DeviceTokenRepository;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.security.jwt.JwtTokenProvider;
import com.buddygo.buddygoserver.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 로컬 PostgreSQL(docker-compose.local.yml)이 떠 있어야 한다. DB 는 테스트마다 롤백된다. */
@Transactional
class UserIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired DeviceTokenRepository deviceTokenRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired EntityManager entityManager;

    User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.signUpWithEmail("me@example.com", "hash", "김버디", "헬스버디", Gender.MALE));
    }

    @Nested
    class 내_정보 {

        @Test
        void 기본_정보와_매너온도를_주고_비밀번호_해시는_주지_않는다() throws Exception {
            mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(user.getId()))
                    .andExpect(jsonPath("$.data.email").value("me@example.com"))
                    .andExpect(jsonPath("$.data.name").value("김버디"))
                    .andExpect(jsonPath("$.data.nickname").value("헬스버디"))
                    .andExpect(jsonPath("$.data.gender").value("MALE"))
                    .andExpect(jsonPath("$.data.provider").value("LOCAL"))
                    .andExpect(jsonPath("$.data.mannerTemperature").value(36.5))
                    .andExpect(jsonPath("$.data.pushEnabled").value(true))
                    .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        }

        @Test
        void 토큰이_없으면_401() throws Exception {
            mockMvc.perform(get("/api/users/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("G004"));
        }

        @Test
        void 토큰은_유효한데_사용자가_없으면_404() throws Exception {
            String token = jwtTokenProvider.issueAccessToken(Long.MAX_VALUE).value();
            mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("U001"));
        }
    }

    @Nested
    class 알림_설정 {

        @Test
        void 끄고_켤_수_있고_내_정보에_반영된다() throws Exception {
            changeNotification("{\"pushEnabled\": false}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.pushEnabled").value(false));
            flushAndClear();
            assertThat(userRepository.findById(user.getId()).orElseThrow().isPushEnabled()).isFalse();

            changeNotification("{\"pushEnabled\": true}")
                    .andExpect(jsonPath("$.data.pushEnabled").value(true));
            flushAndClear();
            mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(jsonPath("$.data.pushEnabled").value(true));
        }

        @Test
        void 값이_없으면_false_로_바꾸지_않고_400() throws Exception {
            changeNotification("{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.pushEnabled").exists());
            flushAndClear();
            assertThat(userRepository.findById(user.getId()).orElseThrow().isPushEnabled()).isTrue();
        }

        private ResultActions changeNotification(String body) throws Exception {
            return mockMvc.perform(patch("/api/users/me/notification-settings")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON).content(body));
        }
    }

    @Nested
    class 푸시_토큰 {

        @Test
        void 등록하면_저장되고_같은_토큰을_다시_보내도_하나만_남는다() throws Exception {
            registerToken(user, "token-a").andExpect(status().isOk());
            Instant later = Instant.parse("2026-11-10T00:00:00Z");
            mutableClock.fix(later);
            registerToken(user, "token-a").andExpect(status().isOk());
            flushAndClear();

            assertThat(deviceTokenRepository.findAllByUserId(user.getId()))
                    .singleElement()
                    .satisfies(t -> {
                        assertThat(t.getFcmToken()).isEqualTo("token-a");
                        // 다시 등록한 시각(서버 시계)으로 갱신된다
                        assertThat(t.getUpdatedAt()).isEqualTo(later);
                    });
        }

        @Test
        void 기기가_여러_대면_토큰이_기기마다_쌓인다() throws Exception {
            registerToken(user, "phone").andExpect(status().isOk());
            registerToken(user, "tablet").andExpect(status().isOk());
            flushAndClear();

            assertThat(deviceTokenRepository.findAllByUserId(user.getId()))
                    .extracting(DeviceToken::getFcmToken)
                    .containsExactlyInAnyOrder("phone", "tablet");
        }

        @Test
        void 같은_기기에서_다른_계정으로_로그인하면_토큰이_새_계정으로_옮겨간다() throws Exception {
            User other = userRepository.save(
                    User.signUpWithEmail("other@example.com", "hash", "이버디", "다른버디", Gender.FEMALE));

            registerToken(user, "shared-device").andExpect(status().isOk());
            registerToken(other, "shared-device").andExpect(status().isOk());
            flushAndClear();

            assertThat(deviceTokenRepository.findAllByUserId(user.getId())).isEmpty();
            assertThat(deviceTokenRepository.findAllByUserId(other.getId()))
                    .extracting(DeviceToken::getFcmToken).containsExactly("shared-device");
        }

        @Test
        void 토큰이_비어_있으면_400() throws Exception {
            mockMvc.perform(put("/api/users/me/fcm-token")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"fcmToken\": \"  \"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.fcmToken").exists());
        }

        @Test
        void 로그아웃할_때_지우면_그_기기_토큰만_사라진다() throws Exception {
            registerToken(user, "phone").andExpect(status().isOk());
            registerToken(user, "tablet").andExpect(status().isOk());

            deleteToken(user, "phone").andExpect(status().isOk());
            flushAndClear();

            assertThat(deviceTokenRepository.findAllByUserId(user.getId()))
                    .extracting(DeviceToken::getFcmToken).containsExactly("tablet");
        }

        @Test
        void 남의_토큰은_지우지_않고_없는_토큰도_200() throws Exception {
            User other = userRepository.save(
                    User.signUpWithEmail("other@example.com", "hash", "이버디", "다른버디", Gender.FEMALE));
            registerToken(other, "other-device").andExpect(status().isOk());

            deleteToken(user, "other-device").andExpect(status().isOk());
            deleteToken(user, "missing").andExpect(status().isOk());
            flushAndClear();

            assertThat(deviceTokenRepository.findAllByUserId(other.getId()))
                    .extracting(DeviceToken::getFcmToken).containsExactly("other-device");
        }

        private ResultActions deleteToken(User owner, String token) throws Exception {
            return mockMvc.perform(delete("/api/users/me/fcm-token")
                    .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"fcmToken\": \"" + token + "\"}"));
        }

        private ResultActions registerToken(User owner, String token) throws Exception {
            return mockMvc.perform(put("/api/users/me/fcm-token")
                    .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"fcmToken\": \"" + token + "\"}"));
        }
    }

    private String bearer(User owner) {
        return "Bearer " + jwtTokenProvider.issueAccessToken(owner.getId()).value();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
