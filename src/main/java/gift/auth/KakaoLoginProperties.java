package gift.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kakao OAuth2 login configuration properties.
 */
@ConfigurationProperties(prefix = "kakao.login")
public record KakaoLoginProperties(String clientId, String clientSecret, String redirectUri) {
}
