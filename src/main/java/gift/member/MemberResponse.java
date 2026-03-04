package gift.member;

public record MemberResponse(
    Long id,
    String email,
    String password,
    int point
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
            member.getId(),
            member.getEmail(),
            member.getPassword(),
            member.getPoint()
        );
    }
}
