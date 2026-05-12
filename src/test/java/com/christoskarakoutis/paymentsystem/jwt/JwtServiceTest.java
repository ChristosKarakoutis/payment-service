package com.christoskarakoutis.paymentsystem.jwt;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private java.security.PrivateKey signingKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        signingKey = keyPair.getPrivate();

        Path tempDir = Files.createTempDirectory("jwt-test");
        Path publicKeyPath = tempDir.resolve("public.pem");
        Files.writeString(publicKeyPath, pemEncode(keyPair.getPublic().getEncoded(), "PUBLIC KEY"));

        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "publicKeyResource",
                new FileSystemResource(publicKeyPath.toFile()));
    }

    @Test
    @DisplayName("extractUsername returns the subject claim")
    void extractUsername_returnsSubject() {
        String token = signToken("user@test.com", "user-1", 900000);
        assertThat(jwtService.extractUsername(token)).isEqualTo("user@test.com");
    }

    @Test
    @DisplayName("extractUserId returns the user_id claim")
    void extractUserId_returnsUserId() {
        String token = signToken("user@test.com", "user-1", 900000);
        assertThat(jwtService.extractUserId(token)).isEqualTo("user-1");
    }

    @Test
    @DisplayName("isTokenExpired returns false for a valid token")
    void isTokenExpired_returnsFalseForValidToken() {
        String token = signToken("user@test.com", "user-1", 900000);
        assertThat(jwtService.isTokenExpired(token)).isFalse();
    }

    @Test
    @DisplayName("isTokenExpired returns true for an expired token")
    void isTokenExpired_returnsTrueForExpiredToken() {
        String token = signToken("user@test.com", "user-1", -60000);
        assertThat(jwtService.isTokenExpired(token)).isTrue();
    }

    private String signToken(String subject, String userId, long expirationMillis) {
        return Jwts.builder()
                .subject(subject)
                .claim("user_id", userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(signingKey)
                .compact();
    }

    private String pemEncode(byte[] der, String label) {
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + b64 + "\n-----END " + label + "-----\n";
    }
}
