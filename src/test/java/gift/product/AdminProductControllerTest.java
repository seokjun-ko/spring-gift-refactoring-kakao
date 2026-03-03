package gift.product;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.jdbc.Sql;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminProductControllerTest {

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    // ========== GET /admin/products ==========

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/admin_list_products.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 상품_목록을_조회하면_정상_응답한다() {
        RestAssured
            .given()
            .when()
                .get("/admin/products")
            .then()
                .statusCode(200)
                .body(containsString("상품A"))
                .body(containsString("상품B"));
    }

    @Test
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 상품이_없어도_목록_조회가_정상_응답한다() {
        RestAssured
            .given()
            .when()
                .get("/admin/products")
            .then()
                .statusCode(200);
    }

    // ========== GET /admin/products/new ==========

    @Test
    void 상품_생성_폼을_조회하면_정상_응답한다() {
        RestAssured
            .given()
            .when()
                .get("/admin/products/new")
            .then()
                .statusCode(200);
    }

    // ========== POST /admin/products ==========

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/admin_create_product_success.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 새_상품을_생성하면_목록_페이지로_리다이렉트된다() {
        RestAssured
            .given()
                .redirects().follow(false)
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .formParam("name", "테스트 상품")
                .formParam("price", 10000)
                .formParam("imageUrl", "https://example.com/images/test.jpg")
                .formParam("categoryId", 1)
            .when()
                .post("/admin/products")
            .then()
                .statusCode(302)
                .header("Location", endsWith("/admin/products"));
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/admin_create_product_fail_name_blank.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 이름이_공백이면_상품_생성에_실패한다() {
        RestAssured
            .given()
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .formParam("name", "")
                .formParam("price", 10000)
                .formParam("imageUrl", "https://example.com/images/test.jpg")
                .formParam("categoryId", 1)
            .when()
                .post("/admin/products")
            .then()
                .statusCode(200)
                .body(containsString("상품 이름은 필수입니다."));
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/admin_create_product_fail_name_too_long.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 이름이_15자를_초과하면_상품_생성에_실패한다() {
        RestAssured
            .given()
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .formParam("name", "이름이십오자를초과하는아주긴상품이름")
                .formParam("price", 10000)
                .formParam("imageUrl", "https://example.com/images/test.jpg")
                .formParam("categoryId", 1)
            .when()
                .post("/admin/products")
            .then()
                .statusCode(200)
                .body(containsString("상품 이름은 공백을 포함하여 최대 15자까지 입력할 수 있습니다."));
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/admin_create_product_fail_special_chars.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 이름에_허용되지_않는_특수문자가_있으면_상품_생성에_실패한다() {
        RestAssured
            .given()
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .formParam("name", "상품@테스트")
                .formParam("price", 10000)
                .formParam("imageUrl", "https://example.com/images/test.jpg")
                .formParam("categoryId", 1)
            .when()
                .post("/admin/products")
            .then()
                .statusCode(200)
                .body(containsString("상품 이름에 허용되지 않는 특수 문자가 포함되어 있습니다."));
    }

    @Test
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 존재하지_않는_카테고리로_상품을_생성하면_실패한다() {
        RestAssured
            .given()
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .formParam("name", "테스트 상품")
                .formParam("price", 10000)
                .formParam("imageUrl", "https://example.com/images/test.jpg")
                .formParam("categoryId", 999)
            .when()
                .post("/admin/products")
            .then()
                .statusCode(500);
    }

    // ========== GET /admin/products/{id}/edit ==========

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/admin_edit_product_form_success.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 상품_수정_폼을_조회하면_정상_응답한다() {
        RestAssured
            .given()
            .when()
                .get("/admin/products/1/edit")
            .then()
                .statusCode(200);
    }

    @Test
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 존재하지_않는_상품의_수정_폼을_조회하면_실패한다() {
        RestAssured
            .given()
            .when()
                .get("/admin/products/999/edit")
            .then()
                .statusCode(500);
    }

    // ========== POST /admin/products/{id}/edit ==========

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/admin_update_product_success.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 상품_정보를_수정하면_목록_페이지로_리다이렉트된다() {
        RestAssured
            .given()
                .redirects().follow(false)
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .formParam("name", "수정된 상품")
                .formParam("price", 25000)
                .formParam("imageUrl", "https://example.com/images/updated.jpg")
                .formParam("categoryId", 2)
            .when()
                .post("/admin/products/1/edit")
            .then()
                .statusCode(302)
                .header("Location", endsWith("/admin/products"));
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/admin_update_product_fail_name_blank.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 이름이_공백이면_상품_수정에_실패한다() {
        RestAssured
            .given()
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .formParam("name", "")
                .formParam("price", 10000)
                .formParam("imageUrl", "https://example.com/images/test.jpg")
                .formParam("categoryId", 1)
            .when()
                .post("/admin/products/1/edit")
            .then()
                .statusCode(200)
                .body(containsString("상품 이름은 필수입니다."));
    }

    @Test
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 존재하지_않는_상품을_수정하면_실패한다() {
        RestAssured
            .given()
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .formParam("name", "수정 상품")
                .formParam("price", 10000)
                .formParam("imageUrl", "https://example.com/images/test.jpg")
                .formParam("categoryId", 1)
            .when()
                .post("/admin/products/999/edit")
            .then()
                .statusCode(500);
    }

    // ========== POST /admin/products/{id}/delete ==========

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/admin_delete_product_success.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 상품을_삭제하면_목록_페이지로_리다이렉트된다() {
        RestAssured
            .given()
                .redirects().follow(false)
            .when()
                .post("/admin/products/1/delete")
            .then()
                .statusCode(302)
                .header("Location", endsWith("/admin/products"));
    }
}
