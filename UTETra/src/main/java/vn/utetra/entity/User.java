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

	@Column(length = 100, unique = true)
	private String email;

	@Column(length = 15)
	private String phone;

	@Column(name = "avatar_cloudinary_id")
	private String avatarCloudinaryId;

	@ManyToOne
	@JoinColumn(name = "role_id", nullable = false)
	private Role role;

	@Column(name = "branch_id")
	private Integer branchId;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt = LocalDateTime.now();
}