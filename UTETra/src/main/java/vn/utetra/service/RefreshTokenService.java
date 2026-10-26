package vn.utetra.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.utetra.entity.RefreshToken;
import vn.utetra.entity.User;
import vn.utetra.repository.RefreshTokenRepository;
import vn.utetra.repository.UserRepository;

@Service
public class RefreshTokenService {

	private static final long GRACE_MILLIS = 30_000;

	private final RefreshTokenRepository refreshTokenRepository;
	private final UserRepository userRepository;
	private final SecureRandom random = new SecureRandom();
	private final Map<String, Grace> recentlyRotated = new ConcurrentHashMap<>();

	@Value("${security.jwt.refresh-expiration-time:604800000}")
	private long refreshExpiration;

	public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.userRepository = userRepository;
	}

	public record Rotation(User user, String newToken) {
	}

	private record Grace(String successor, long until) {
	}

	public long getExpirationTime() {
		return refreshExpiration;
	}

	@Transactional
	public String issue(User user) {
		byte[] bytes = new byte[48];
		random.nextBytes(bytes);
		String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		RefreshToken token = new RefreshToken();
		token.setUser(user);
		token.setToken(hash(raw));
		token.setRevoked(false);
		token.setCreatedAt(LocalDateTime.now());
		token.setExpiresAt(LocalDateTime.now().plus(Duration.ofMillis(refreshExpiration)));
		refreshTokenRepository.save(token);
		return raw;
	}

	@Transactional
	public Optional<String> issueForUsername(String username) {
		return userRepository.findByUsername(username).map(this::issue);
	}

	@Transactional
	public Optional<String> issueForUserId(Integer userId) {
		return userId == null ? Optional.empty() : userRepository.findById(userId).map(this::issue);
	}

	@Transactional
	public Optional<Rotation> rotate(String raw, boolean allowGrace) {
		if (raw == null || raw.isBlank()) {
			return Optional.empty();
		}
		String hashed = hash(raw);
		RefreshToken token = refreshTokenRepository.findByToken(hashed).orElse(null);
		if (token == null) {
			return Optional.empty();
		}
		User user = token.getUser();
		if (user == null || !Boolean.TRUE.equals(user.getIsActive())) {
			token.setRevoked(true);
			return Optional.empty();
		}
		long now = System.currentTimeMillis();
		if (Boolean.TRUE.equals(token.getRevoked())) {
			Grace grace = recentlyRotated.get(hashed);
			if (grace != null && grace.until() > now) {
				boolean successorActive = refreshTokenRepository.findByToken(grace.successor())
						.map(next -> !Boolean.TRUE.equals(next.getRevoked())).orElse(false);
				return allowGrace && successorActive ? Optional.of(new Rotation(user, null)) : Optional.empty();
			}
			refreshTokenRepository.revokeAllByUserId(user.getId());
			return Optional.empty();
		}
		if (token.getExpiresAt() == null || token.getExpiresAt().isBefore(LocalDateTime.now())) {
			token.setRevoked(true);
			return Optional.empty();
		}
		token.setRevoked(true);
		String next = issue(user);
		recentlyRotated.values().removeIf(grace -> grace.until() < now);
		recentlyRotated.put(hashed, new Grace(hash(next), now + GRACE_MILLIS));
		return Optional.of(new Rotation(user, next));
	}

	@Transactional
	public void revoke(String raw) {
		if (raw == null || raw.isBlank()) {
			return;
		}
		String hashed = hash(raw);
		for (int i = 0; hashed != null && i < 10; i++) {
			refreshTokenRepository.findByToken(hashed).ifPresent(token -> token.setRevoked(true));
			Grace grace = recentlyRotated.remove(hashed);
			hashed = grace == null ? null : grace.successor();
		}
	}

	@Transactional
	public void revokeAllForUser(Integer userId) {
		if (userId != null) {
			refreshTokenRepository.revokeAllByUserId(userId);
		}
	}

	@Scheduled(cron = "0 30 3 * * *")
	@Transactional
	public void cleanupExpired() {
		refreshTokenRepository.deleteExpired(LocalDateTime.now());
	}

	private static String hash(String raw) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
