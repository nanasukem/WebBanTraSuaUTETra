package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "OtpCodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OtpCode {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(nullable = false, length = 100)
	private String email;

	@Column(nullable = false, length = 6)
	private String code;

	@Column(nullable = false, length = 20)
	private String purpose; // REGISTER, RESET_PASSWORD

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(nullable = false)
	private Boolean used = false;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt = LocalDateTime.now();
}