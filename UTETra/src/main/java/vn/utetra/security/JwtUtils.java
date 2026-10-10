package vn.utetra.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtils {

	// Chuỗi Secret Key tối thiểu 256-bit (32+ ký tự)
	private final String JWT_SECRET = "UTETraBubbleTeaSecretKeyForJWTAuthentication2026";
	private final long JWT_EXPIRATION_MS = 86400000L; // 24 giờ

	private SecretKey getSigningKey() {
		return Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
	}

	public String generateToken(String username, String role) {
		return Jwts.builder().subject(username).claim("role", role).issuedAt(new Date())
				.expiration(new Date(System.currentTimeMillis() + JWT_EXPIRATION_MS)).signWith(getSigningKey())
				.compact();
	}

	public String getUsernameFromToken(String token) {
		Claims claims = Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();

		return claims.getSubject();
	}

	public boolean validateToken(String token) {
		try {
			Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token);
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}