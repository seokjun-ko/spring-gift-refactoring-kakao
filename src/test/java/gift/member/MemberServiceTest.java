package gift.member;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class MemberServiceTest {

    @Autowired
    MemberService memberService;

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/update_member_duplicate_email.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 다른_회원의_이메일로_변경하면_실패하고_상태가_변경되지_않는다() {
        assertThatThrownBy(() -> memberService.update(1L, "user2@example.com", "newpassword"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Email is already registered.");

        Member member = memberService.findById(1L);
        assertThat(member.getEmail()).isEqualTo("user1@example.com");
        assertThat(member.getPassword()).isEqualTo("password123");
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/update_member_duplicate_email.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 자기_이메일_유지하며_비밀번호를_변경하면_성공한다() {
        memberService.update(1L, "user1@example.com", "newpassword");

        Member member = memberService.findById(1L);
        assertThat(member.getEmail()).isEqualTo("user1@example.com");
        assertThat(member.getPassword()).isEqualTo("newpassword");
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/update_member_duplicate_email.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 미사용_이메일로_변경하면_성공한다() {
        memberService.update(1L, "newemail@example.com", "newpassword");

        Member member = memberService.findById(1L);
        assertThat(member.getEmail()).isEqualTo("newemail@example.com");
        assertThat(member.getPassword()).isEqualTo("newpassword");
    }
}
