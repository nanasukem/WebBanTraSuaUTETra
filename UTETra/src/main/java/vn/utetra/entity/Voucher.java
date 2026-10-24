package vn.utetra.entity;

import vn.utetra.entity.enums.DiscountType;
import vn.utetra.entity.enums.PaymentMethod;
import vn.utetra.entity.enums.VoucherApplyTo;
import vn.utetra.entity.enums.VoucherAudience;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Vouchers")
@Getter
@Setter
@NoArgsConstructor
public class Voucher {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "code", nullable = false, unique = true, length = 30)
	private String code;

	@Column(name = "name", nullable = false, length = 150)
	private String name;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "branch_id")
	private Branch branch;

	@Enumerated(EnumType.STRING)
	@Column(name = "apply_to", nullable = false, length = 10)
	private VoucherApplyTo applyTo;

	@Enumerated(EnumType.STRING)
	@Column(name = "discount_type", nullable = false, length = 10)
	private DiscountType discountType;

	@Column(name = "discount_value", nullable = false, precision = 12, scale = 0)
	private BigDecimal discountValue;

	@Column(name = "max_discount", precision = 12, scale = 0)
	private BigDecimal maxDiscount;

	@Column(name = "min_order_value", nullable = false, precision = 12, scale = 0)
	private BigDecimal minOrderValue = BigDecimal.ZERO;

	@Column(name = "quantity", nullable = false)
	private Integer quantity = 0;

	@Column(name = "used_count", nullable = false)
	private Integer usedCount = 0;

	@Column(name = "start_date", nullable = false)
	private LocalDateTime startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDateTime endDate;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "per_user_limit")
	private Integer perUserLimit;

	@Enumerated(EnumType.STRING)
	@Column(name = "audience", nullable = false, length = 20)
	private VoucherAudience audience = VoucherAudience.ALL;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "min_tier_id")
	private MemberTier minTier;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_method", length = 20)
	private PaymentMethod paymentMethod;

	@PrePersist
	void prePersist() {
		if (createdAt == null)
			createdAt = LocalDateTime.now();
	}
}