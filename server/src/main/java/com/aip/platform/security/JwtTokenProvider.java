package com.aip.platform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JWT 令牌签发与解析
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${platform.jwt.secret}")
    private String secret;

    @Value("${platform.jwt.expire-minutes:1440}")
    private long expireMinutes;

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_DEPT_ID = "deptId";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_PERMS = "perms";
    public static final String CLAIM_SUPER = "super";
    public static final String CLAIM_NICKNAME = "nickname";

    private SecretKey key() {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(bytes);
    }

    /** 签发令牌 */
    public String generateToken(LoginUser user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, user.getUserId());
        claims.put(CLAIM_DEPT_ID, user.getDeptId());
        claims.put(CLAIM_NICKNAME, user.getNickname());
        claims.put(CLAIM_SUPER, user.isSuperAdmin());
        claims.put(CLAIM_ROLES, user.getRoleKeys() == null ? Collections.emptyList() : user.getRoleKeys());
        claims.put(CLAIM_PERMS, user.getPermissions() == null ? Collections.emptyList() : user.getPermissions());

        LocalDateTime now = LocalDateTime.now();
        return Jwts.builder()
                .claims(claims)
                .subject(user.getUsername())
                .issuedAt(Date.from(now.atZone(ZoneId.systemDefault()).toInstant()))
                .expiration(Date.from(now.plusMinutes(expireMinutes).atZone(ZoneId.systemDefault()).toInstant()))
                .signWith(key())
                .compact();
    }

    /** 解析令牌，过期/非法返回 null */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("JWT 已过期");
            return null;
        } catch (Exception e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /** 从载荷还原 LoginUser */
    public LoginUser buildLoginUser(Claims claims) {
        LoginUser user = new LoginUser();
        user.setUserId(asLong(claims.get(CLAIM_USER_ID)));
        user.setDeptId(asLong(claims.get(CLAIM_DEPT_ID)));
        user.setUsername(claims.getSubject());
        user.setNickname((String) claims.get(CLAIM_NICKNAME));
        user.setSuperAdmin(Boolean.TRUE.equals(claims.get(CLAIM_SUPER)));
        user.setLoginTime(LocalDateTime.now());
        user.setRoleKeys(castList(claims.get(CLAIM_ROLES)));
        Set<String> perms = new HashSet<>(castList(claims.get(CLAIM_PERMS)));
        user.setPermissions(perms);
        return user;
    }

    public Date getExpirationFrom(String token) {
        Claims claims = parseToken(token);
        return claims == null ? null : claims.getExpiration();
    }

    public long getExpireSeconds() {
        return expireMinutes * 60L;
    }

    private Long asLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> castList(Object obj) {
        if (!(obj instanceof Collection<?> col)) {
            return Collections.emptyList();
        }
        return col.stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.toList());
    }
}
