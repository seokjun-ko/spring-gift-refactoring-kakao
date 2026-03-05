package gift.member;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class MemberServiceTest {

    @Autowired
    MemberService memberService;

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/find_member_by_email.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 존재하는_이메일로_조회하면_회원을_반환한다() {
        Optional<Member> result = memberService.findByEmail("existing@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("existing@example.com");
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/find_member_by_email.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 존재하지_않는_이메일로_조회하면_빈값을_반환한다() {
        Optional<Member> result = memberService.findByEmail("nonexistent@example.com");

        assertThat(result).isEmpty();
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 이메일만으로_회원을_생성하면_비밀번호_없이_저장된다() {
        Member created = memberService.create("kakao@example.com");

        Member found = memberService.findById(created.getId());
        assertThat(found.getEmail()).isEqualTo("kakao@example.com");
        assertThat(found.getPassword()).isNull();
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/find_member_by_email.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 이미_존재하는_이메일로_생성하면_예외가_발생한다() {
        assertThatThrownBy(() -> memberService.create("existing@example.com"))
            .isInstanceOf(Exception.class);

        assertThat(memberService.findAll()).hasSize(1);
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/find_member_by_email.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 이미_존재하는_이메일로_이메일만_생성하면_예외가_발생하고_회원수가_유지된다() {
        assertThatThrownBy(() -> memberService.create("existing@example.com"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Email is already registered.");

        assertThat(memberService.findAll()).hasSize(1);
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/find_member_by_email.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 카카오_액세스_토큰을_업데이트하면_재조회_시_반영된다() {
        memberService.updateKakaoAccessToken(1L, "new-kakao-token");

        Member found = memberService.findById(1L);
        assertThat(found.getKakaoAccessToken()).isEqualTo("new-kakao-token");
    }

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

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/delete_member_success.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 존재하는_회원을_삭제하면_재조회_시_찾을_수_없다() {
        memberService.delete(1L);

        assertThatThrownBy(() -> memberService.findById(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Member not found");
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 존재하지_않는_회원을_삭제하면_예외가_발생한다() {
        assertThatThrownBy(() -> memberService.delete(999L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("999");
    }
}
