package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Vouchers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Voucher {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(nullable = false, length = 50, unique = true)
	private String code;

	@Column(name = "discount_percent")
	private Integer discountPercent;

	@Column(name = "max_discount_amount")
	private BigDecimal maxDiscountAmount;

	@Column(name = "min_order_value")
	private BigDecimal minOrderValue;

	@Column(name = "start_date", nullable = false)
	private LocalDateTime startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDateTime endDate;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;
}