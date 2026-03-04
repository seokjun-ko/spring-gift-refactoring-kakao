package gift.option;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.jdbc.Sql;

import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OptionControllerTest {

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    // ===== GET /api/products/{productId}/options =====

    @Test
    @DisplayName("옵션 목록 조회 성공")
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/get_options_success.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 옵션_목록_조회_성공() {
        RestAssured
            .given()
                .contentType(JSON)
            .when()
                .get("/api/products/1/options")
            .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("[0].id", notNullValue())
                .body("[0].name", notNullValue())
                .body("[0].quantity", notNullValue())
                .body("[1].id", notNullValue())
                .body("[1].name", notNullValue())
                .body("[1].quantity", notNullValue());
    }

    @Test
    @DisplayName("옵션 목록 조회 실패 - 존재하지 않는 상품")
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 옵션_목록_조회_실패_존재하지_않는_상품() {
        RestAssured
            .given()
                .contentType(JSON)
            .when()
                .get("/api/products/999/options")
            .then()
                .statusCode(404);
    }

    // ===== POST /api/products/{productId}/options =====

    @Test
    @DisplayName("옵션 생성 성공")
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/create_option_success.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 옵션_생성_성공() {
        RestAssured
            .given()
                .contentType(JSON)
                .body("""
                    {
                        "name": "새 옵션",
                        "quantity": 10
                    }
                    """)
            .when()
                .post("/api/products/1/options")
            .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", equalTo("새 옵션"))
                .body("quantity", equalTo(10));
    }

    @Test
    @DisplayName("옵션 생성 실패 - 존재하지 않는 상품")
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 옵션_생성_실패_존재하지_않는_상품() {
        RestAssured
            .given()
                .contentType(JSON)
                .body("""
                    {
                        "name": "새 옵션",
                        "quantity": 10
                    }
                    """)
            .when()
                .post("/api/products/999/options")
            .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("옵션 생성 실패 - 중복 옵션명")
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/create_option_fail_duplicate_name.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 옵션_생성_실패_중복_옵션명() {
        RestAssured
            .given()
                .contentType(JSON)
                .body("""
                    {
                        "name": "기존 옵션",
                        "quantity": 5
                    }
                    """)
            .when()
                .post("/api/products/1/options")
            .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("옵션 생성 실패 - 이름 누락")
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 옵션_생성_실패_이름_누락() {
        RestAssured
            .given()
                .contentType(JSON)
                .body("""
                    {
                        "name": "",
                        "quantity": 5
                    }
                    """)
            .when()
                .post("/api/products/1/options")
            .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("옵션 생성 실패 - 허용되지 않는 특수문자")
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 옵션_생성_실패_허용되지_않는_특수문자() {
        RestAssured
            .given()
                .contentType(JSON)
                .body("""
                    {
                        "name": "옵션#!@테스트",
                        "quantity": 5
                    }
                    """)
            .when()
                .post("/api/products/1/options")
            .then()
                .statusCode(400);
    }

    // ===== DELETE /api/products/{productId}/options/{optionId} =====

    @Test
    @DisplayName("옵션 삭제 성공")
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/delete_option_success.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 옵션_삭제_성공() {
        RestAssured
            .given()
            .when()
                .delete("/api/products/1/options/1")
            .then()
                .statusCode(204);
    }

    @Test
    @DisplayName("옵션 삭제 실패 - 존재하지 않는 상품")
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 옵션_삭제_실패_존재하지_않는_상품() {
        RestAssured
            .given()
            .when()
                .delete("/api/products/999/options/1")
            .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("옵션 삭제 실패 - 마지막 옵션 삭제 불가")
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/delete_option_fail_last_option.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 옵션_삭제_실패_마지막_옵션_삭제_불가() {
        RestAssured
            .given()
            .when()
                .delete("/api/products/1/options/1")
            .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("옵션 삭제 실패 - 존재하지 않는 옵션")
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/delete_option_fail_option_not_found.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 옵션_삭제_실패_존재하지_않는_옵션() {
        RestAssured
            .given()
            .when()
                .delete("/api/products/1/options/999")
            .then()
                .statusCode(404);
    }
}
