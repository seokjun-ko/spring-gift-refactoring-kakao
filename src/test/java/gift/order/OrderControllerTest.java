package gift.order;

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
class OrderControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/create_order_success.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void create_order_success() {
        String token = jwtProvider.createToken("test@example.com");

        RestAssured
            .given()
                .contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body("""
                    {
                        "optionId": 1,
                        "quantity": 2,
                        "message": "테스트 주문"
                    }
                    """)
            .when()
                .post("/api/orders")
            .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("optionId", equalTo(1))
                .body("quantity", equalTo(2))
                .body("message", equalTo("테스트 주문"))
                .body("orderDateTime", notNullValue());
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/create_order_fail_option_not_found.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void create_order_fail_option_not_found() {
        String token = jwtProvider.createToken("test@example.com");

        RestAssured
            .given()
                .contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body("""
                    {
                        "optionId": 999,
                        "quantity": 1,
                        "message": "존재하지 않는 옵션"
                    }
                    """)
            .when()
                .post("/api/orders")
            .then()
                .statusCode(404);
    }

    @Test
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void create_order_fail_unauthorized() {
        RestAssured
            .given()
                .contentType(JSON)
                .header("Authorization", "Bearer invalid-token")
                .body("""
                    {
                        "optionId": 1,
                        "quantity": 1,
                        "message": "인증 실패"
                    }
                    """)
            .when()
                .post("/api/orders")
            .then()
                .statusCode(401);
    }

    @Test
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void create_order_fail_validation() {
        RestAssured
            .given()
                .contentType(JSON)
                .header("Authorization", "Bearer any-token")
                .body("""
                    {
                        "quantity": 0
                    }
                    """)
            .when()
                .post("/api/orders")
            .then()
                .statusCode(400);
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/create_order_fail_insufficient_stock.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void create_order_fail_insufficient_stock() {
        String token = jwtProvider.createToken("test@example.com");

        RestAssured
            .given()
                .contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body("""
                    {
                        "optionId": 1,
                        "quantity": 5,
                        "message": "재고 부족 테스트"
                    }
                    """)
            .when()
                .post("/api/orders")
            .then()
                .statusCode(500);
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/create_order_fail_insufficient_point.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void create_order_fail_insufficient_point() {
        String token = jwtProvider.createToken("test@example.com");

        RestAssured
            .given()
                .contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body("""
                    {
                        "optionId": 1,
                        "quantity": 1,
                        "message": "포인트 부족 테스트"
                    }
                    """)
            .when()
                .post("/api/orders")
            .then()
                .statusCode(500);
    }
}
