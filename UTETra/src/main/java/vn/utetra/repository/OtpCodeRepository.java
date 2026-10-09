package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.utetra.entity.OtpCode;

import java.util.Optional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Integer> {
	Optional<OtpCode> findTopByEmailAndPurposeAndUsedFalseOrderByCreatedAtDesc(String email, String purpose);
}