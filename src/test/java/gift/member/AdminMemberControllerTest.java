package gift.member;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.jdbc.Sql;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminMemberControllerTest {

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    // ========== GET /admin/members ==========

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/admin_list_members.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 회원_목록을_조회하면_정상_응답한다() {
        RestAssured
            .given()
            .when()
                .get("/admin/members")
            .then()
                .statusCode(200)
                .body(containsString("user1@example.com"))
                .body(containsString("user2@example.com"));
    }

    @Test
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 회원이_없어도_목록_조회가_정상_응답한다() {
        RestAssured
            .given()
            .when()
                .get("/admin/members")
            .then()
                .statusCode(200);
    }

    // ========== GET /admin/members/new ==========

    @Test
    void 회원_생성_폼을_조회하면_정상_응답한다() {
        RestAssured
            .given()
            .when()
                .get("/admin/members/new")
            .then()
                .statusCode(200);
    }

    // ========== POST /admin/members ==========

    @Test
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 새_회원을_생성하면_목록_페이지로_리다이렉트된다() {
        RestAssured
            .given()
                .redirects().follow(false)
                .contentType("application/x-www-form-urlencoded")
                .formParam("email", "newuser@example.com")
                .formParam("password", "password123")
            .when()
                .post("/admin/members")
            .then()
                .statusCode(302)
                .header("Location", endsWith("/admin/members"));
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/admin_create_member_duplicate_email.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 이미_등록된_이메일로_생성하면_에러_메시지가_표시된다() {
        RestAssured
            .given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("email", "existing@example.com")
                .formParam("password", "password123")
            .when()
                .post("/admin/members")
            .then()
                .statusCode(200)
                .body(containsString("Email is already registered."));
    }

    // ========== POST /admin/members/{id}/charge-point ==========

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/admin_charge_point_success.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 유효한_금액으로_포인트를_충전하면_목록_페이지로_리다이렉트된다() {
        RestAssured
            .given()
                .redirects().follow(false)
                .contentType("application/x-www-form-urlencoded")
                .formParam("amount", 5000)
            .when()
                .post("/admin/members/1/charge-point")
            .then()
                .statusCode(302)
                .header("Location", endsWith("/admin/members"));
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/admin_charge_point_zero_amount.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 금액이_0이면_포인트_충전에_실패한다() {
        RestAssured
            .given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("amount", 0)
            .when()
                .post("/admin/members/1/charge-point")
            .then()
                .statusCode(500);
    }

    @Test
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 존재하지_않는_회원에게_포인트를_충전하면_실패한다() {
        RestAssured
            .given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("amount", 5000)
            .when()
                .post("/admin/members/999/charge-point")
            .then()
                .statusCode(500);
    }

    // ========== GET /admin/members/{id}/edit ==========

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/admin_edit_form_success.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 회원_수정_폼을_조회하면_정상_응답한다() {
        RestAssured
            .given()
            .when()
                .get("/admin/members/1/edit")
            .then()
                .statusCode(200);
    }

    @Test
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 존재하지_않는_회원의_수정_폼을_조회하면_실패한다() {
        RestAssured
            .given()
            .when()
                .get("/admin/members/999/edit")
            .then()
                .statusCode(500);
    }

    // ========== POST /admin/members/{id}/edit ==========

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/admin_update_member_success.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 회원_정보를_수정하면_목록_페이지로_리다이렉트된다() {
        RestAssured
            .given()
                .redirects().follow(false)
                .contentType("application/x-www-form-urlencoded")
                .formParam("email", "updated@example.com")
                .formParam("password", "newpassword456")
            .when()
                .post("/admin/members/1/edit")
            .then()
                .statusCode(302)
                .header("Location", endsWith("/admin/members"));
    }

    @Test
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 존재하지_않는_회원을_수정하면_실패한다() {
        RestAssured
            .given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("email", "updated@example.com")
                .formParam("password", "newpassword456")
            .when()
                .post("/admin/members/999/edit")
            .then()
                .statusCode(500);
    }

    // ========== POST /admin/members/{id}/delete ==========

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/admin_delete_member_success.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 회원을_삭제하면_목록_페이지로_리다이렉트된다() {
        RestAssured
            .given()
                .redirects().follow(false)
            .when()
                .post("/admin/members/1/delete")
            .then()
                .statusCode(302)
                .header("Location", endsWith("/admin/members"));
    }
}
