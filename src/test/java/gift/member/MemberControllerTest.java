package gift.member;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.jdbc.Sql;

import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberControllerTest {

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    // ========== POST /api/members/register ==========

    @Test
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void register_success() {
        RestAssured
            .given()
                .contentType(JSON)
                .body("""
                    {
                        "email": "newuser@example.com",
                        "password": "password123"
                    }
                    """)
            .when()
                .post("/api/members/register")
            .then()
                .statusCode(201)
                .body("token", notNullValue());
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/register_duplicate_email.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void register_duplicate_email() {
        RestAssured
            .given()
                .contentType(JSON)
                .body("""
                    {
                        "email": "existing@example.com",
                        "password": "password123"
                    }
                    """)
            .when()
                .post("/api/members/register")
            .then()
                .statusCode(400)
                .body(equalTo("Email is already registered."));
    }

    @Test
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void register_invalid_email_format() {
        RestAssured
            .given()
                .contentType(JSON)
                .body("""
                    {
                        "email": "not-an-email",
                        "password": "password123"
                    }
                    """)
            .when()
                .post("/api/members/register")
            .then()
                .statusCode(400);
    }

    // ========== POST /api/members/login ==========

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/login_success.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void login_success() {
        RestAssured
            .given()
                .contentType(JSON)
                .body("""
                    {
                        "email": "test@example.com",
                        "password": "password123"
                    }
                    """)
            .when()
                .post("/api/members/login")
            .then()
                .statusCode(200)
                .body("token", notNullValue());
    }

    @Test
    @Sql(scripts = "/data/truncate.sql",
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void login_nonexistent_email() {
        RestAssured
            .given()
                .contentType(JSON)
                .body("""
                    {
                        "email": "nobody@example.com",
                        "password": "password123"
                    }
                    """)
            .when()
                .post("/api/members/login")
            .then()
                .statusCode(400)
                .body(equalTo("Invalid email or password."));
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/login_wrong_password.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void login_wrong_password() {
        RestAssured
            .given()
                .contentType(JSON)
                .body("""
                    {
                        "email": "test@example.com",
                        "password": "wrongpassword"
                    }
                    """)
            .when()
                .post("/api/members/login")
            .then()
                .statusCode(400)
                .body(equalTo("Invalid email or password."));
    }
}
