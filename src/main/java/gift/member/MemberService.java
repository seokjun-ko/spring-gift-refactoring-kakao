package gift.member;

import gift.auth.JwtProvider;
import gift.auth.TokenResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class MemberService {
    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;

    public MemberService(MemberRepository memberRepository, JwtProvider jwtProvider) {
        this.memberRepository = memberRepository;
        this.jwtProvider = jwtProvider;
    }

    @Transactional
    public TokenResponse register(String email, String password) {
        if (memberRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered.");
        }
        Member member = memberRepository.save(new Member(email, password));
        String token = jwtProvider.createToken(member.getEmail());
        return new TokenResponse(token);
    }

    public TokenResponse login(String email, String password) {
        Member member = memberRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));

        if (member.getPassword() == null || !member.getPassword().equals(password)) {
            throw new IllegalArgumentException("Invalid email or password.");
        }

        String token = jwtProvider.createToken(member.getEmail());
        return new TokenResponse(token);
    }

    public List<MemberResponse> findAll() {
        return memberRepository.findAll().stream()
            .map(MemberResponse::from)
            .toList();
    }

    public MemberResponse findById(Long id) {
        return MemberResponse.from(findEntityById(id));
    }

    @Transactional
    public MemberResponse create(String email, String password) {
        if (memberRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered.");
        }
        Member member = memberRepository.save(new Member(email, password));
        return MemberResponse.from(member);
    }

    @Transactional
    public void update(Long id, String email, String password) {
        Member member = findEntityById(id);
        member.update(email, password);
    }

    @Transactional
    public void chargePoint(Long id, int amount) {
        Member member = findEntityById(id);
        member.chargePoint(amount);
    }

    private Member findEntityById(Long id) {
        return memberRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Member not found. id=" + id));
    }

    @Transactional
    public void delete(Long id) {
        memberRepository.deleteById(id);
    }
}
