package com.christoskarakoutis.paymentsystem.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        filter = new JwtAuthenticationFilter(jwtService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("jwt_access_token", token));
        return request;
    }

    private Claims claims(String typ) {
        var builder = Jwts.claims().subject("user@test.com").add("user_id", "user-1");
        if (typ != null) {
            builder.add("typ", typ);
        }
        return builder.build();
    }

    @Test
    @DisplayName("access token grants access and sets the principal")
    void accessToken_isAccepted() throws Exception {
        when(jwtService.parseClaims("tok")).thenReturn(claims(JwtService.ACCESS_TOKEN_TYPE));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithToken("tok"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("user@test.com");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getDetails()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("token without typ claim is accepted as an access token")
    void tokenWithoutType_isAccepted() throws Exception {
        when(jwtService.parseClaims("tok")).thenReturn(claims(null));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithToken("tok"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("MFA challenge token is rejected even though its signature is valid")
    void mfaChallengeToken_isRejected() throws Exception {
        when(jwtService.parseClaims("tok")).thenReturn(claims("mfa_challenge"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithToken("tok"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
