package com.buddygo.buddygoserver.domain.auth;

import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.security.jwt.JwtProperties;
import com.buddygo.buddygoserver.global.security.jwt.TokenType;
import com.buddygo.buddygoserver.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로컬 PostgreSQL·Redis(docker-compose.local.yml)가 떠 있어야 한다.
 * DB 는 테스트마다 롤백되고, Redis 는 테스트 전용 DB(15번)를 매번 비운다.
 */
@Transactional
class AuthIntegrationTest extends IntegrationTest {

    private static final String EMAIL = "buddy@example.com";
    private static final String PASSWORD = "password123";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository; // 스파이(IntegrationTest 에서 선언)
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired JwtProperties jwtProperties;

    @BeforeEach
    void flushRedis() {
        redisTemplate.execute(connection -> {
            connection.serverCommands().flushDb();
            return null;
        }, true);
    }

    @Nested
    class 회원가입 {

        @Test
        void 정상_가입하면_201과_토큰을_주고_비밀번호는_해시로_저장한다() throws Exception {
            signup(EMAIL, "헬스버디")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value(201))
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$..password").doesNotExist())
                    .andExpect(jsonPath("$..passwordHash").doesNotExist());

            User saved = userRepository.findByEmail(EMAIL).orElseThrow();
            assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2a$12$");
        }

        @Test
        void 이메일은_소문자로_정규화되어_대소문자만_다른_중복을_막는다() throws Exception {
            signup("Buddy@Example.COM", "헬스버디").andExpect(status().isCreated());
            assertThat(userRepository.findByEmail(EMAIL)).isPresent();

            signup(EMAIL, "다른닉네임")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("U002"));
        }

        @Test
        void 닉네임이_중복이면_409() throws Exception {
            signup(EMAIL, "헬스버디").andExpect(status().isCreated());
            signup("other@example.com", "헬스버디")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("U003"));
        }

        /*
         * 동시 가입: 두 요청이 사전 중복 확인을 함께 통과한 상황을 exists 조회를 false 로 고정해 재현한다.
         * 실패한 INSERT 뒤에는 트랜잭션이 망가지므로 응답만 확인하고 DB 는 다시 조회하지 않는다.
         */
        @Test
        void 동시_가입으로_이메일_유니크_제약에_걸리면_409_U002() throws Exception {
            signup(EMAIL, "헬스버디").andExpect(status().isCreated());
            doReturn(false).when(userRepository).existsByEmail(anyString());

            signup(EMAIL, "다른닉네임")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("U002"));
        }

        @Test
        void 동시_가입으로_닉네임_유니크_제약에_걸리면_409_U003() throws Exception {
            signup(EMAIL, "헬스버디").andExpect(status().isCreated());
            doReturn(false).when(userRepository).existsByNickname(anyString());

            signup("other@example.com", "헬스버디")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("U003"));
        }

        @Test
        void 잘못된_입력과_약한_비밀번호는_400과_필드별_사유를_준다() throws Exception {
            mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                            {"email":"not-an-email","password":"short","name":"","nickname":"a","gender":null}
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("G002"))
                    .andExpect(jsonPath("$.errors.email").exists())
                    .andExpect(jsonPath("$.errors.password").exists())
                    .andExpect(jsonPath("$.errors.name").exists())
                    .andExpect(jsonPath("$.errors.nickname").exists())
                    .andExpect(jsonPath("$.errors.gender").exists());
        }

        @Test
        void 숫자_없는_비밀번호는_거절한다() throws Exception {
            mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                            .content(signupBody(EMAIL, "onlyletters", "헬스버디")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.password").exists());
        }
    }

    @Nested
    class 중복_확인 {

        @Test
        void 사용_가능_여부를_알려준다() throws Exception {
            signup(EMAIL, "헬스버디").andExpect(status().isCreated());

            mockMvc.perform(get("/api/auth/check-email").param("email", "BUDDY@example.com"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.available").value(false));
            mockMvc.perform(get("/api/auth/check-email").param("email", "new@example.com"))
                    .andExpect(jsonPath("$.data.available").value(true));
            mockMvc.perform(get("/api/auth/check-nickname").param("nickname", "헬스버디"))
                    .andExpect(jsonPath("$.data.available").value(false));
        }

        @Test
        void 이메일_형식이_아니면_400() throws Exception {
            mockMvc.perform(get("/api/auth/check-email").param("email", "nope"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("G002"));
        }
    }

    @Nested
    class 로그인 {

        @Test
        void 정상_로그인하면_토큰_쌍을_발급한다() throws Exception {
            signup(EMAIL, "헬스버디");
            login(EMAIL, PASSWORD)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
        }

        @Test
        void 틀린_비밀번호와_없는_사용자는_같은_401_응답() throws Exception {
            signup(EMAIL, "헬스버디");
            String wrongPassword = login(EMAIL, "wrongpass1")
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();
            String unknownUser = login("nobody@example.com", PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();

            assertThat(wrongPassword).isEqualTo(unknownUser).contains("A001");
        }

        @Test
        void 연속_5회_실패하면_올바른_비밀번호여도_429() throws Exception {
            signup(EMAIL, "헬스버디");
            for (int i = 0; i < 5; i++) {
                login(EMAIL, "wrongpass1").andExpect(status().isUnauthorized());
            }
            login(EMAIL, PASSWORD)
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("A005"));
        }
    }

    @Nested
    class 액세스_토큰_검증 {

        @Test
        void 유효한_토큰이면_보호_API에_접근하고_사용자_ID가_주입된다() throws Exception {
            Tokens tokens = signupAndGetTokens();
            Long userId = userRepository.findByEmail(EMAIL).orElseThrow().getId();

            mockMvc.perform(get("/api/test/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.access()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(userId));
        }

        @Test
        void 토큰이_없으면_401_G004() throws Exception {
            mockMvc.perform(get("/api/test/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("G004"));
        }

        @Test
        void Bearer_접두사가_없으면_401() throws Exception {
            Tokens tokens = signupAndGetTokens();
            mockMvc.perform(get("/api/test/me").header(HttpHeaders.AUTHORIZATION, tokens.access()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void 만료된_토큰은_401_A003() throws Exception {
            String expired = encode(jwtEncoder, claims(1L, TokenType.ACCESS)
                    .issuedAt(Instant.now().minusSeconds(3600))
                    .expiresAt(Instant.now().minusSeconds(600))
                    .build());
            expectRejected(expired, "A003");
        }

        @Test
        void 페이로드를_변조한_토큰은_401_A002() throws Exception {
            Tokens tokens = signupAndGetTokens();
            String[] parts = tokens.access().split("\\.");
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    payload.replaceFirst("\"sub\":\"\\d+\"", "\"sub\":\"999999\"").getBytes(StandardCharsets.UTF_8));

            expectRejected(parts[0] + "." + forgedPayload + "." + parts[2], "A002");
        }

        @Test
        void 다른_비밀키로_서명한_토큰은_401_A002() throws Exception {
            JwtEncoder otherEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                    "another-secret-key-that-is-long-enough-0123".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
            expectRejected(encode(otherEncoder, claims(1L, TokenType.ACCESS).build()), "A002");
        }

        @Test
        void alg_none_토큰은_401_A002() throws Exception {
            Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
            long exp = Instant.now().plusSeconds(600).getEpochSecond();
            String header = b64.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String payload = b64.encodeToString(("{\"sub\":\"1\",\"iss\":\"buddygo\",\"aud\":[\"buddygo-app\"],"
                    + "\"token_type\":\"ACCESS\",\"exp\":" + exp + "}").getBytes(StandardCharsets.UTF_8));
            expectRejected(header + "." + payload + ".", "A002");
        }

        @Test
        void 리프레시_토큰을_액세스_토큰으로_쓰면_401_A002() throws Exception {
            Tokens tokens = signupAndGetTokens();
            expectRejected(tokens.refresh(), "A002");
        }

        @Test
        void issuer나_audience가_다르면_401_A002() throws Exception {
            expectRejected(encode(jwtEncoder, claims(1L, TokenType.ACCESS).issuer("evil").build()), "A002");
            expectRejected(encode(jwtEncoder, claims(1L, TokenType.ACCESS).audience(List.of("other")).build()), "A002");
        }

        private void expectRejected(String token, String code) throws Exception {
            mockMvc.perform(get("/api/test/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
                    .andExpect(jsonPath("$.code").value(code));
        }
    }

    @Nested
    class 재발급 {

        @Test
        void 정상_재발급하면_새_토큰_쌍을_주고_기존_리프레시_토큰은_폐기된다() throws Exception {
            Tokens first = signupAndGetTokens();

            Tokens second = tokensOf(refresh(first.refresh()).andExpect(status().isOk()).andReturn());
            assertThat(second.refresh()).isNotEqualTo(first.refresh());
            assertThat(second.access()).isNotEqualTo(first.access());

            // 새 리프레시 토큰은 다시 쓸 수 있다
            refresh(second.refresh()).andExpect(status().isOk());
        }

        @Test
        void 폐기된_리프레시_토큰을_재사용하면_401_A004이고_해당_세션_전체가_폐기된다() throws Exception {
            Tokens first = signupAndGetTokens();
            Tokens second = tokensOf(refresh(first.refresh()).andReturn());

            refresh(first.refresh())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("A004"));

            // 탈취자가 아닌 정상 사용자의 최신 토큰도 함께 무효화된다
            refresh(second.refresh())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("A002"));
        }

        @Test
        void 재사용_탐지는_다른_기기_세션에는_영향을_주지_않는다() throws Exception {
            Tokens phone = signupAndGetTokens();
            Tokens tablet = tokensOf(login(EMAIL, PASSWORD).andReturn());

            refresh(phone.refresh());
            refresh(phone.refresh()).andExpect(jsonPath("$.code").value("A004"));

            refresh(tablet.refresh()).andExpect(status().isOk());
        }

        @Test
        void 만료된_리프레시_토큰은_401_A003() throws Exception {
            String expired = encode(jwtEncoder, claims(1L, TokenType.REFRESH)
                    .claim("fid", UUID.randomUUID().toString())
                    .issuedAt(Instant.now().minusSeconds(3600))
                    .expiresAt(Instant.now().minusSeconds(600))
                    .build());
            refresh(expired)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("A003"));
        }

        @Test
        void 액세스_토큰으로는_재발급할_수_없다() throws Exception {
            Tokens tokens = signupAndGetTokens();
            refresh(tokens.access())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("A002"));
        }

        @Test
        void 리프레시_토큰_원문은_Redis에_저장되지_않는다() throws Exception {
            Tokens tokens = signupAndGetTokens();
            List<String> stored = redisTemplate.keys("auth:rt:family:*").stream()
                    .flatMap(key -> redisTemplate.opsForHash().values(key).stream())
                    .map(Object::toString)
                    .toList();
            assertThat(stored).isNotEmpty().doesNotContain(tokens.refresh());
        }
    }

    @Nested
    class 로그아웃 {

        @Test
        void 로그아웃한_리프레시_토큰으로는_재발급할_수_없다() throws Exception {
            Tokens tokens = signupAndGetTokens();

            logout(tokens.refresh()).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
            refresh(tokens.refresh())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("A002"));
        }

        @Test
        void 다른_기기_세션은_유지되고_로그아웃은_멱등이다() throws Exception {
            Tokens phone = signupAndGetTokens();
            Tokens tablet = tokensOf(login(EMAIL, PASSWORD).andReturn());

            logout(phone.refresh()).andExpect(status().isOk());
            logout(phone.refresh()).andExpect(status().isOk());

            refresh(tablet.refresh()).andExpect(status().isOk());
        }
    }

    // ───────── helpers ─────────

    private ResultActions signup(String email, String nickname) throws Exception {
        return mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(signupBody(email, PASSWORD, nickname)));
    }

    private String signupBody(String email, String password, String nickname) {
        return """
                {"email":"%s","password":"%s","name":"김버디","nickname":"%s","gender":"MALE"}
                """.formatted(email, password, nickname);
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
    }

    private Tokens signupAndGetTokens() throws Exception {
        return tokensOf(signup(EMAIL, "헬스버디").andExpect(status().isCreated()).andReturn());
    }

    private Tokens tokensOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return new Tokens(JsonPath.read(body, "$.data.accessToken"), JsonPath.read(body, "$.data.refreshToken"));
    }

    private JwtClaimsSet.Builder claims(Long userId, TokenType type) {
        Instant now = Instant.now();
        return JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of(jwtProperties.audience()))
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .id(UUID.randomUUID().toString())
                .claim(TokenType.CLAIM, type.name());
    }

    private static String encode(JwtEncoder encoder, JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private record Tokens(String access, String refresh) {
    }
}
