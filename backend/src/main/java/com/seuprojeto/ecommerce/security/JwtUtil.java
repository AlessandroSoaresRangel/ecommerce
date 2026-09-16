package com.seuprojeto.ecommerce.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtil {

    public static final String CLAIM_TOKEN_TYPE = "type";
    public static final String TYPE_ACCESS = "ACCESS";
    public static final String TYPE_REFRESH = "REFRESH";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(UserDetails userDetails) {
        return buildToken(userDetails, expirationMs, TYPE_ACCESS);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(userDetails, refreshExpirationMs, TYPE_REFRESH);
    }

    private String buildToken(UserDetails userDetails, long ttlMs, String tokenType) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(signingKey())
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    public boolean isTokenType(String token, String expectedType) {
        String type = extractTokenType(token);
        return expectedType.equals(type);
    }

    // A expiração já é validada pelo próprio parser do jjwt dentro de
    // extractUsername (lança ExpiredJwtException para um token vencido),
    // então não é preciso checá-la de novo aqui manualmente.
    public boolean isTokenValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }
}
