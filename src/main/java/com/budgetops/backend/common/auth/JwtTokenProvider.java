package com.budgetops.backend.common.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Component
public class JwtTokenProvider {

    private final Algorithm algorithm;
    private final String issuer;
    private final long accessValiditySeconds;
    private final long refreshValiditySeconds;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.issuer}") String issuer,
            @Value("${jwt.access-token-validity-seconds}") long accessValiditySeconds,
            @Value("${jwt.refresh-token-validity-seconds}") long refreshValiditySeconds
    ) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.issuer = issuer;
        this.accessValiditySeconds = accessValiditySeconds;
        this.refreshValiditySeconds = refreshValiditySeconds;
    }

    public String createAccessToken(String subject, Map<String, Object> claims) {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer(issuer)
                .withSubject(subject)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(accessValiditySeconds)))
                .withPayload(claims)
                .sign(algorithm);
    }

    public String createRefreshToken(String subject) {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer(issuer)
                .withSubject(subject)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(refreshValiditySeconds)))
                .withClaim("typ", "refresh")
                .sign(algorithm);
    }

    public com.auth0.jwt.interfaces.DecodedJWT verify(String token) {
        return JWT.require(algorithm).withIssuer(issuer).build().verify(token);
    }
}
