package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "Users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(nullable = false, length = 50, unique = true)
	private String username;

	@Column(nullable = false)
	private String password;

	@Column(name = "full_name", length = 100)
	private String fullName;

	@Column(nullable = false, length = 100, unique = true)
	private String email;

	@Column(length = 15)
	private String phone;

	@Column(name = "avatar_cloudinary_id")
	private String avatarCloudinaryId;

	@ManyToOne
	@JoinColumn(name = "role_id", nullable = false)
	private Role role;

	@ManyToOne
	@JoinColumn(name = "carrier_id")
	private Carrier carrier;

	@Column(name = "is_verified", nullable = false)
	private Boolean isVerified = false;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;

	/** true = đang dùng mật khẩu tạm do Admin đặt -> lần đăng nhập tới bắt buộc đổi mật khẩu */
	@Column(name = "must_change_password", nullable = false)
	private Boolean mustChangePassword = false;

	/** Mật khẩu tạm hết hạn lúc (72 giờ sau khi Admin cấp); null = không có mật khẩu tạm */
	@Column(name = "temp_password_expires_at")
	private LocalDateTime tempPasswordExpiresAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt = LocalDateTime.now();
}