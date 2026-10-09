package vn.utetra.entity;

import vn.utetra.entity.enums.BranchStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bảng Branches = Shop / Chi nhánh. Vendor đăng ký -> PENDING -> Manager/Admin
 * duyệt -> APPROVED.
 */
@Entity
@Table(name = "Branches")
@Getter
@Setter
@NoArgsConstructor
public class Branch {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id", nullable = false)
	private User owner; // Vendor sở hữu shop

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "address", nullable = false, length = 255)
	private String address;

	@Column(name = "phone", length = 15)
	private String phone;

	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "image_cloudinary_id")
	private String imageCloudinaryId;

	@Column(name = "commission_rate", nullable = false, precision = 5, scale = 2)
	private BigDecimal commissionRate = new BigDecimal("5.00"); // % chiết khấu app

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private BranchStatus status = BranchStatus.PENDING;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	void prePersist() {
		if (createdAt == null)
			createdAt = LocalDateTime.now();
	}
}