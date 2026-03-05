package gift.category;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.jdbc.Sql;

import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CategoryControllerTest {

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    // === POST /api/categories ===

    @Test
    @DisplayName("카테고리 생성 성공")
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 카테고리_생성_성공() {
        RestAssured
            .given()
                .contentType(JSON)
                .body("""
                    {
                        "name": "전자기기",
                        "color": "#1E90FF",
                        "imageUrl": "https://example.com/images/electronics.jpg",
                        "description": "전자기기 카테고리"
                    }
                    """)
            .when()
                .post("/api/categories")
            .then()
                .statusCode(201)
                .header("Location", notNullValue())
                .body("id", notNullValue())
                .body("name", equalTo("전자기기"))
                .body("color", equalTo("#1E90FF"))
                .body("imageUrl", equalTo("https://example.com/images/electronics.jpg"))
                .body("description", equalTo("전자기기 카테고리"));
    }

    @Test
    @DisplayName("카테고리 생성 실패 - 이름 누락 시 400")
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 카테고리_생성_실패_이름_누락() {
        RestAssured
            .given()
                .contentType(JSON)
                .body("""
                    {
                        "name": "",
                        "color": "#1E90FF",
                        "imageUrl": "https://example.com/images/test.jpg",
                        "description": "이름 누락"
                    }
                    """)
            .when()
                .post("/api/categories")
            .then()
                .statusCode(400);
    }

    // === GET /api/categories ===

    @Test
    @DisplayName("카테고리 전체 조회 성공")
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/get_categories_success.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 카테고리_전체_조회_성공() {
        RestAssured
            .given()
                .contentType(JSON)
            .when()
                .get("/api/categories")
            .then()
                .statusCode(200)
                .body("$", hasSize(2))
                .body("[0].id", equalTo(1))
                .body("[0].name", equalTo("전자기기"))
                .body("[1].id", equalTo(2))
                .body("[1].name", equalTo("도서"));
    }

    // === PUT /api/categories/{id} ===

    @Test
    @DisplayName("카테고리 수정 성공")
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/update_category_success.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 카테고리_수정_성공() {
        RestAssured
            .given()
                .contentType(JSON)
                .body("""
                    {
                        "name": "가전제품",
                        "color": "#FF6347",
                        "imageUrl": "https://example.com/images/appliance.jpg",
                        "description": "수정된 카테고리"
                    }
                    """)
            .when()
                .put("/api/categories/1")
            .then()
                .statusCode(200)
                .body("id", equalTo(1))
                .body("name", equalTo("가전제품"))
                .body("color", equalTo("#FF6347"))
                .body("imageUrl", equalTo("https://example.com/images/appliance.jpg"))
                .body("description", equalTo("수정된 카테고리"));
    }

    @Test
    @DisplayName("카테고리 수정 실패 - 존재하지 않는 ID로 요청 시 404")
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 카테고리_수정_실패_존재하지_않는_ID() {
        RestAssured
            .given()
                .contentType(JSON)
                .body("""
                    {
                        "name": "존재하지않음",
                        "color": "#000000",
                        "imageUrl": "https://example.com/images/none.jpg",
                        "description": "없는 카테고리"
                    }
                    """)
            .when()
                .put("/api/categories/999")
            .then()
                .statusCode(404);
    }

    // === DELETE /api/categories/{id} ===

    @Test
    @DisplayName("카테고리 삭제 성공")
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/delete_category_success.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 카테고리_삭제_성공() {
        RestAssured
            .given()
            .when()
                .delete("/api/categories/1")
            .then()
                .statusCode(204);
    }

}
