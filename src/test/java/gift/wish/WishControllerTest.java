package gift.wish;

import gift.auth.JwtProvider;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.jdbc.Sql;

import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WishControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    // ========== POST /api/wishes ==========

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/위시_추가_성공.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 위시_추가_성공() {
        String token = jwtProvider.createToken("test@example.com");

        RestAssured
            .given()
                .contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body("""
                    {
                        "productId": 1
                    }
                    """)
            .when()
                .post("/api/wishes")
            .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("productId", equalTo(1))
                .body("name", equalTo("테스트 상품"))
                .body("price", equalTo(10000));
    }

    @Test
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 위시_추가_실패_인증_실패() {
        RestAssured
            .given()
                .contentType(JSON)
                .header("Authorization", "Bearer invalid-token")
                .body("""
                    {
                        "productId": 1
                    }
                    """)
            .when()
                .post("/api/wishes")
            .then()
                .statusCode(401);
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/위시_추가_실패_상품_미존재.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 위시_추가_실패_상품_미존재() {
        String token = jwtProvider.createToken("test@example.com");

        RestAssured
            .given()
                .contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body("""
                    {
                        "productId": 999
                    }
                    """)
            .when()
                .post("/api/wishes")
            .then()
                .statusCode(404);
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/위시_추가_중복_시_기존_반환.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 위시_추가_중복_시_기존_반환() {
        String token = jwtProvider.createToken("test@example.com");

        RestAssured
            .given()
                .contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body("""
                    {
                        "productId": 1
                    }
                    """)
            .when()
                .post("/api/wishes")
            .then()
                .statusCode(200)
                .body("id", equalTo(1))
                .body("productId", equalTo(1));
    }

    // ========== GET /api/wishes ==========

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/위시_목록_조회_성공.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 위시_목록_조회_성공() {
        String token = jwtProvider.createToken("test@example.com");

        RestAssured
            .given()
                .contentType(JSON)
                .header("Authorization", "Bearer " + token)
            .when()
                .get("/api/wishes")
            .then()
                .statusCode(200)
                .body("totalElements", equalTo(2));
    }

    // ========== DELETE /api/wishes/{id} ==========

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/위시_삭제_성공.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 위시_삭제_성공() {
        String token = jwtProvider.createToken("test@example.com");

        RestAssured
            .given()
                .header("Authorization", "Bearer " + token)
            .when()
                .delete("/api/wishes/1")
            .then()
                .statusCode(204);
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/위시_삭제_실패_미존재.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 위시_삭제_실패_미존재() {
        String token = jwtProvider.createToken("test@example.com");

        RestAssured
            .given()
                .header("Authorization", "Bearer " + token)
            .when()
                .delete("/api/wishes/999")
            .then()
                .statusCode(404);
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/위시_삭제_실패_소유권_없음.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 위시_삭제_실패_소유권_없음() {
        String token = jwtProvider.createToken("other@example.com");

        RestAssured
            .given()
                .header("Authorization", "Bearer " + token)
            .when()
                .delete("/api/wishes/1")
            .then()
                .statusCode(403);
    }
}
