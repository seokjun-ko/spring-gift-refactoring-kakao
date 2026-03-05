package gift.auth;

import gift.member.MemberService;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.jdbc.Sql;

import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthenticationResolverTest {

    @LocalServerPort
    int port;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    MemberService memberService;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 잘못된_토큰으로_요청하면_401() {
        RestAssured
            .given()
                .header("Authorization", "Bearer invalid-token")
            .when()
                .get("/api/wishes")
            .then()
                .statusCode(401);
    }

    @Test
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 유효한_토큰이지만_회원이_없으면_401() {
        String token = jwtProvider.createToken("nonexistent@example.com");

        RestAssured
            .given()
                .header("Authorization", "Bearer " + token)
            .when()
                .get("/api/wishes")
            .then()
                .statusCode(401);
    }

    @Test
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 유효한_토큰과_회원이_존재하면_200() {
        memberService.create("valid@example.com");
        String token = jwtProvider.createToken("valid@example.com");

        RestAssured
            .given()
                .header("Authorization", "Bearer " + token)
            .when()
                .get("/api/wishes")
            .then()
                .statusCode(200)
                .body(notNullValue());
    }
}
