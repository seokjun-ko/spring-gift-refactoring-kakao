package gift.auth;

import gift.member.Member;
import gift.member.MemberService;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated member from an Authorization header.
 *
 * @author brian.kim
 * @since 1.0
 */
@Component
public class AuthenticationResolver {
    private final JwtProvider jwtProvider;
    private final MemberService memberService;

    public AuthenticationResolver(JwtProvider jwtProvider, MemberService memberService) {
        this.jwtProvider = jwtProvider;
        this.memberService = memberService;
    }

    public Member extractMember(String authorization) {
        try {
            final String token = authorization.replace("Bearer ", "");
            final String email = jwtProvider.getEmail(token);
            return memberService.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("회원을 찾을 수 없습니다"));
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException("유효하지 않은 토큰입니다");
        }
    }
}
