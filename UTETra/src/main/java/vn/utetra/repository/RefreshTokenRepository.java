package vn.utetra.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.utetra.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Integer> {

	Optional<RefreshToken> findByToken(String token);

	@Modifying
	@Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.user.id = :userId AND t.revoked = false")
	int revokeAllByUserId(@Param("userId") Integer userId);

	@Modifying
	@Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :now")
	int deleteExpired(@Param("now") LocalDateTime now);
}
