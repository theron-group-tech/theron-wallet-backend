package com.theron.wallet.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.dto.request.CreateUserRequest;
import com.theron.wallet.dto.request.LoginRequest;
import com.theron.wallet.dto.request.UpdateUserRequest;
import com.theron.wallet.dto.response.LoginResponse;
import com.theron.wallet.dto.response.UserResponse;
import com.theron.wallet.enums.UserStatus;
import com.theron.wallet.exception.UnauthorizedException;
import com.theron.wallet.repository.AuthSessionRepository;
import com.theron.wallet.security.JwtTokenProvider;
import com.theron.wallet.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthSessionIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "SenhaForte1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Test
    @DisplayName("1. product login returns access+refresh without password")
    void productLoginSuccess() throws Exception {
        UserResponse user = createUser("login-ok@theron.test");
        LoginResponse login = loginProduct(user.getEmail(), "device-1");

        assertThat(login.getAccessToken()).isNotBlank();
        assertThat(login.getToken()).isEqualTo(login.getAccessToken());
        assertThat(login.getRefreshToken()).isNotBlank();
        assertThat(login.getTokenType()).isEqualTo("Bearer");
        assertThat(login.getExpiresIn()).isEqualTo(jwtTokenProvider.getAccessExpirationMs());
        assertThat(login.getUserId()).isEqualTo(user.getId());
        assertThat(login.getName()).isEqualTo(user.getName());
        assertThat(login.getEmail()).isEqualTo(user.getEmail());
        assertThat(login.getAdminId()).isNull();
        assertThat(login.getRole()).isNull();
        assertThat(objectMapper.writeValueAsString(login).toLowerCase())
                .doesNotContain("password");
    }

    @Test
    @DisplayName("2. invalid product password returns 401")
    void invalidProductPassword() throws Exception {
        UserResponse user = createUser("login-bad@theron.test");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email(user.getEmail())
                                .password("WrongPass1!")
                                .build())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("3. access token authenticates /me")
    void accessTokenAuthenticatesMe() throws Exception {
        UserResponse user = createUser("me-ok@theron.test");
        LoginResponse login = loginProduct(user.getEmail(), "device-me");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + login.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("4. expired access token returns 401")
    void expiredAccessToken() throws Exception {
        UserResponse user = createUser("me-expired@theron.test");
        LoginResponse login = loginProduct(user.getEmail(), "device-exp");
        var claims = jwtTokenProvider.parseClaims(login.getAccessToken());
        UUID userId = UUID.fromString(claims.getSubject());
        UUID sessionId = jwtTokenProvider.getSessionId(claims);
        String expired = jwtTokenProvider.generateAccessToken(userId, sessionId, new Date(System.currentTimeMillis() - 1000));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("5. refresh returns a new token pair")
    void refreshRotatesTokens() throws Exception {
        UserResponse user = createUser("refresh-ok@theron.test");
        LoginResponse login = loginProduct(user.getEmail(), "device-ref");

        LoginResponse refreshed = refresh(login.getRefreshToken());
        assertThat(refreshed.getAccessToken()).isNotBlank().isNotEqualTo(login.getAccessToken());
        assertThat(refreshed.getRefreshToken()).isNotBlank().isNotEqualTo(login.getRefreshToken());
        assertThat(refreshed.getToken()).isEqualTo(refreshed.getAccessToken());
    }

    @Test
    @DisplayName("6. invalid refresh returns 401")
    void invalidRefresh() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"not-a-real-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("7. reused refresh returns 401 and revokes other sessions")
    void refreshReuseRevokesFamily() throws Exception {
        UserResponse user = createUser("reuse@theron.test");
        LoginResponse first = loginProduct(user.getEmail(), "device-reuse");
        LoginResponse rotated = refresh(first.getRefreshToken());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("refreshToken", first.getRefreshToken()))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("refreshToken", rotated.getRefreshToken()))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + rotated.getAccessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("8. logout revokes the current session")
    void logoutRevokesCurrentSession() throws Exception {
        UserResponse user = createUser("logout@theron.test");
        LoginResponse login = loginProduct(user.getEmail(), "device-logout");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + login.getAccessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + login.getAccessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("9. logout-all revokes every session")
    void logoutAllRevokesEverySession() throws Exception {
        UserResponse user = createUser("logout-all@theron.test");
        LoginResponse a = loginProduct(user.getEmail(), "device-a");
        LoginResponse b = loginProduct(user.getEmail(), "device-b");

        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .header("Authorization", "Bearer " + a.getAccessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("refreshToken", a.getRefreshToken()))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("refreshToken", b.getRefreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("10. revoked session cannot refresh")
    void revokedSessionCannotRefresh() throws Exception {
        UserResponse user = createUser("revoked-refresh@theron.test");
        LoginResponse login = loginProduct(user.getEmail(), "device-rr");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + login.getAccessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("refreshToken", login.getRefreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("11. two deviceIds create two devices and two sessions")
    void twoDevicesTwoSessions() throws Exception {
        UserResponse user = createUser("two-dev@theron.test");
        LoginResponse phone = loginProduct(user.getEmail(), "phone");
        LoginResponse tablet = loginProduct(user.getEmail(), "tablet");

        MvcResult result = mockMvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + phone.getAccessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode sessions = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(sessions).hasSize(2);
        assertThat(sessions.get(0).get("deviceId").asText())
                .isNotEqualTo(sessions.get(1).get("deviceId").asText());
        assertThat(tablet.getAccessToken()).isNotEqualTo(phone.getAccessToken());
    }

    @Test
    @DisplayName("12. deleting one session does not drop the other")
    void deleteOneSessionKeepsTheOther() throws Exception {
        UserResponse user = createUser("del-one@theron.test");
        LoginResponse phone = loginProduct(user.getEmail(), "phone");
        LoginResponse tablet = loginProduct(user.getEmail(), "tablet");

        MvcResult list = mockMvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + phone.getAccessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode sessions = objectMapper.readTree(list.getResponse().getContentAsString());
        UUID phoneSid = jwtTokenProvider.getSessionId(jwtTokenProvider.parseClaims(phone.getAccessToken()));

        mockMvc.perform(delete("/api/v1/auth/sessions/" + phoneSid)
                        .header("Authorization", "Bearer " + phone.getAccessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + tablet.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + phone.getAccessToken()))
                .andExpect(status().isUnauthorized());
        assertThat(sessions).hasSize(2);
    }

    @Test
    @DisplayName("13. password is absent from login and /me payloads")
    void passwordAbsentFromPayloads() throws Exception {
        UserResponse user = createUser("no-pwd@theron.test");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginBody(user.getEmail(), "dev"))))
                .andExpect(status().isOk())
                .andReturn();
        String loginJson = loginResult.getResponse().getContentAsString();
        assertThat(loginJson.toLowerCase()).doesNotContain("password");

        LoginResponse login = objectMapper.readValue(loginJson, LoginResponse.class);
        MvcResult meResult = mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + login.getAccessToken()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(meResult.getResponse().getContentAsString().toLowerCase()).doesNotContain("password");
    }

    @Test
    @DisplayName("14. logs never include password or tokens")
    void logsDoNotContainSecrets() throws Exception {
        UserResponse user = createUser("logs@theron.test");
        Logger logger = (Logger) LoggerFactory.getLogger(AuthServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            LoginResponse login = loginProduct(user.getEmail(), "device-log");
            String all = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (a, b) -> a + "\n" + b);
            assertThat(all).doesNotContain(PASSWORD);
            assertThat(all).doesNotContain(login.getAccessToken());
            assertThat(all).doesNotContain(login.getRefreshToken());
            assertThat(all).contains("sessionId=");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("15. parallel refresh: one success, other reuse/401; at most one active session")
    void parallelRefreshDetectsReuse() throws Exception {
        UserResponse user = createUser("parallel@theron.test");
        LoginResponse login = loginProduct(user.getEmail(), "device-parallel");
        MvcResult sessionList = mockMvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + login.getAccessToken()))
                .andExpect(status().isOk())
                .andReturn();
        UUID deviceId = UUID.fromString(objectMapper.readTree(sessionList.getResponse().getContentAsString())
                .get(0).get("deviceId").asText());

        AtomicInteger success = new AtomicInteger();
        AtomicInteger unauthorized = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        String refreshToken = login.getRefreshToken();
        Runnable task = () -> {
            try {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                authService.refresh(refreshToken, "127.0.0.1", "junit");
                success.incrementAndGet();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                Throwable t = ex;
                boolean unauth = false;
                while (t != null) {
                    if (t instanceof UnauthorizedException) {
                        unauth = true;
                        break;
                    }
                    t = t.getCause();
                }
                if (unauth) {
                    unauthorized.incrementAndGet();
                } else {
                    throw new RuntimeException(ex);
                }
            }
        };
        pool.submit(task);
        pool.submit(task);
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(success.get() + unauthorized.get()).isEqualTo(2);
        assertThat(success.get()).isLessThanOrEqualTo(1);
        assertThat(unauthorized.get()).isGreaterThanOrEqualTo(1);
        long active = authSessionRepository.countByUser_IdAndDevice_IdAndRevokedAtIsNullAndReplacedAtIsNull(
                user.getId(), deviceId);
        assertThat(active).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("admin login regression still returns token and adminId")
    void adminLoginRegression() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email(adminEmail)
                                .password(adminPassword)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.adminId").isString())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    @DisplayName("suspended product user cannot authenticate")
    void suspendedUserCannotLogin() throws Exception {
        UserResponse user = createUser("suspended@theron.test");
        userService.update(user.getId(), UpdateUserRequest.builder().status(UserStatus.SUSPENDED).build());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginBody(user.getEmail(), "dev"))))
                .andExpect(status().isUnauthorized());
    }

    private UserResponse createUser(String email) {
        return userService.create(CreateUserRequest.builder()
                .name("Auth User")
                .email(email)
                .password(PASSWORD)
                .build());
    }

    private LoginRequest loginBody(String email, String deviceId) {
        return LoginRequest.builder()
                .email(email)
                .password(PASSWORD)
                .deviceId(deviceId)
                .deviceName("JUnit " + deviceId)
                .platform("test")
                .build();
    }

    private LoginResponse loginProduct(String email, String deviceId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "JUnit")
                        .content(objectMapper.writeValueAsString(loginBody(email, deviceId))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), LoginResponse.class);
    }

    private LoginResponse refresh(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), LoginResponse.class);
    }
}
