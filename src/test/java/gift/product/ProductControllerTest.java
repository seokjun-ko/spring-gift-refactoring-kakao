package gift.product;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.jdbc.Sql;

import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductControllerTest {

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/create_product_success.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 상품_생성_성공() {
        RestAssured
            .given()
                .contentType(JSON)
                .body("""
                    {
                        "name": "테스트 상품",
                        "price": 10000,
                        "imageUrl": "https://example.com/images/test.jpg",
                        "categoryId": 1
                    }
                    """)
            .when()
                .post("/api/products")
            .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", equalTo("테스트 상품"))
                .body("price", equalTo(10000))
                .body("imageUrl", equalTo("https://example.com/images/test.jpg"))
                .body("categoryId", equalTo(1));
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/create_product_fail_name_too_long.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 상품_생성_실패_이름_15자_초과() {
        RestAssured
            .given()
                .contentType(JSON)
                .body("""
                    {
                        "name": "이름이십오자를초과하는아주긴상품이름입니다",
                        "price": 10000,
                        "imageUrl": "https://example.com/images/test.jpg",
                        "categoryId": 1
                    }
                    """)
            .when()
                .post("/api/products")
            .then()
                .statusCode(400);
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/create_product_fail_name_contains_kakao.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 상품_생성_실패_이름에_카카오_포함() {
        RestAssured
            .given()
                .contentType(JSON)
                .body("""
                    {
                        "name": "카카오 상품",
                        "price": 10000,
                        "imageUrl": "https://example.com/images/test.jpg",
                        "categoryId": 1
                    }
                    """)
            .when()
                .post("/api/products")
            .then()
                .statusCode(400);
    }

    @Test
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 상품_생성_실패_카테고리_미존재() {
        RestAssured
            .given()
                .contentType(JSON)
                .body("""
                    {
                        "name": "테스트 상품",
                        "price": 10000,
                        "imageUrl": "https://example.com/images/test.jpg",
                        "categoryId": 999
                    }
                    """)
            .when()
                .post("/api/products")
            .then()
                .statusCode(404);
    }

    @Test
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 상품_생성_실패_유효성_검증() {
        RestAssured
            .given()
                .contentType(JSON)
                .body("""
                    {
                        "name": "",
                        "price": 0,
                        "imageUrl": "",
                        "categoryId": null
                    }
                    """)
            .when()
                .post("/api/products")
            .then()
                .statusCode(400);
    }

    // ========== GET /api/products ==========

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/상품_목록_조회_성공.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 상품_목록_조회_성공() {
        RestAssured
            .given()
                .contentType(JSON)
            .when()
                .get("/api/products")
            .then()
                .statusCode(200)
                .body("totalElements", equalTo(2))
                .body("content.size()", greaterThanOrEqualTo(2));
    }

    // ========== GET /api/products/{id} ==========

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/상품_단건_조회_성공.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 상품_단건_조회_성공() {
        RestAssured
            .given()
                .contentType(JSON)
            .when()
                .get("/api/products/1")
            .then()
                .statusCode(200)
                .body("id", equalTo(1))
                .body("name", equalTo("테스트 상품"))
                .body("price", equalTo(10000))
                .body("categoryId", equalTo(1));
    }

    @Test
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 상품_단건_조회_실패_미존재() {
        RestAssured
            .given()
                .contentType(JSON)
            .when()
                .get("/api/products/999")
            .then()
                .statusCode(404);
    }

    // ========== PUT /api/products/{id} ==========

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/상품_수정_성공.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 상품_수정_성공() {
        RestAssured
            .given()
                .contentType(JSON)
                .body("""
                    {
                        "name": "수정된 상품",
                        "price": 25000,
                        "imageUrl": "https://example.com/images/updated.jpg",
                        "categoryId": 2
                    }
                    """)
            .when()
                .put("/api/products/1")
            .then()
                .statusCode(200)
                .body("id", equalTo(1))
                .body("name", equalTo("수정된 상품"))
                .body("price", equalTo(25000))
                .body("imageUrl", equalTo("https://example.com/images/updated.jpg"))
                .body("categoryId", equalTo(2));
    }

    @Test
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 상품_수정_실패_상품_미존재() {
        RestAssured
            .given()
                .contentType(JSON)
                .body("""
                    {
                        "name": "수정 상품",
                        "price": 10000,
                        "imageUrl": "https://example.com/images/test.jpg",
                        "categoryId": 1
                    }
                    """)
            .when()
                .put("/api/products/999")
            .then()
                .statusCode(404);
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/상품_수정_실패_카테고리_미존재.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 상품_수정_실패_카테고리_미존재() {
        RestAssured
            .given()
                .contentType(JSON)
                .body("""
                    {
                        "name": "수정 상품",
                        "price": 10000,
                        "imageUrl": "https://example.com/images/test.jpg",
                        "categoryId": 999
                    }
                    """)
            .when()
                .put("/api/products/1")
            .then()
                .statusCode(404);
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/상품_수정_실패_이름_15자_초과.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 상품_수정_실패_이름_15자_초과() {
        RestAssured
            .given()
                .contentType(JSON)
                .body("""
                    {
                        "name": "이름이십오자를초과하는아주긴상품이름입니다",
                        "price": 10000,
                        "imageUrl": "https://example.com/images/test.jpg",
                        "categoryId": 1
                    }
                    """)
            .when()
                .put("/api/products/1")
            .then()
                .statusCode(400);
    }

    // ========== DELETE /api/products/{id} ==========

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/상품_삭제_성공.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 상품_삭제_성공() {
        RestAssured
            .given()
            .when()
                .delete("/api/products/1")
            .then()
                .statusCode(204);
    }
}
