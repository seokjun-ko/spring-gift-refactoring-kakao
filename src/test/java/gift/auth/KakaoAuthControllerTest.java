package gift.auth;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KakaoAuthControllerTest {

    @LocalServerPort
    int port;

    @MockitoBean
    KakaoLoginClient kakaoLoginClient;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void login_redirect_to_kakao() {
        RestAssured
            .given()
                .redirects().follow(false)
            .when()
                .get("/api/auth/kakao/login")
            .then()
                .statusCode(302)
                .header("Location", containsString("kauth.kakao.com/oauth/authorize"));
    }

    @Test
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void callback_success_auto_register() {
        when(kakaoLoginClient.requestAccessToken("test-auth-code"))
            .thenReturn(new KakaoLoginClient.KakaoTokenResponse("mock-access-token"));
        when(kakaoLoginClient.requestUserInfo("mock-access-token"))
            .thenReturn(new KakaoLoginClient.KakaoUserResponse(
                new KakaoLoginClient.KakaoUserResponse.KakaoAccount("kakao@example.com")));

        RestAssured
            .given()
                .queryParam("code", "test-auth-code")
            .when()
                .get("/api/auth/kakao/callback")
            .then()
                .statusCode(200)
                .body("token", notNullValue());
    }

    @Test
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void callback_fail_missing_code() {
        RestAssured
            .given()
            .when()
                .get("/api/auth/kakao/callback")
            .then()
                .statusCode(400);
    }
}
