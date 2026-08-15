package com.appsdeveloperblog.api.users.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import javax.crypto.SecretKey;
import java.io.IOException;

public class AuthorizationFilter extends BasicAuthenticationFilter {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public AuthorizationFilter(AuthenticationManager authManager) {
        super(authManager);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws IOException, ServletException {

        String header = req.getHeader(SecurityConstants.HEADER_STRING);

        if (header == null || !header.startsWith(SecurityConstants.TOKEN_PREFIX)) {
            chain.doFilter(req, res);
            return;
        }

        UsernamePasswordAuthenticationToken authentication = getAuthentication(req);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(req, res);
    }

    private UsernamePasswordAuthenticationToken getAuthentication(HttpServletRequest request) {
        String token = request.getHeader(SecurityConstants.HEADER_STRING);

        if (token == null) return null;

        token = token.replace(SecurityConstants.TOKEN_PREFIX, "").trim();
        byte[] secretKeyBytes = SecurityConstants.TOKEN_SECRET.getBytes();
        SecretKey secretKey = Keys.hmacShaKeyFor(secretKeyBytes);

        try {

            JwtParser parser = Jwts.parser()
                    .verifyWith(secretKey)
                    .build();
            Claims claims = parser.parseSignedClaims(token).getPayload();
            String user = (String) claims.get("sub");

            if (user != null) {
                return new UsernamePasswordAuthenticationToken(user, null, null);
            }
        } catch(Exception ex) {
            logger.error("Invalid JWT signature: {}", ex.getMessage());
        }

        return null;
    }

}