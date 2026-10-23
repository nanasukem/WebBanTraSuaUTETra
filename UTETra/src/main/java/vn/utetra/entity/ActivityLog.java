package vn.utetra.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Nhật ký hoạt động: ai (Admin / Manager / Shipper) làm gì, lúc nào */
@Entity
@Table(name = "ActivityLogs")
@Getter
@Setter
@NoArgsConstructor
public class ActivityLog {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "actor_id")
	private Integer actorId;

	@Column(name = "actor_name", nullable = false, length = 150)
	private String actorName;

	@Column(name = "actor_role", nullable = false, length = 20)
	private String actorRole;

	@Column(nullable = false, length = 500)
	private String action;

	@Column(length = 255)
	private String path;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt = LocalDateTime.now();
}
