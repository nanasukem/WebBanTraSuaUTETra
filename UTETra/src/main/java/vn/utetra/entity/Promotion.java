package vn.utetra.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "Promotions")
public class Promotion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(nullable = false, unique = true, length = 50)
	private String code;

	// PERCENT: giảm theo phần trăm; AMOUNT: giảm số tiền cố định
	// SHIPPING: giảm phí vận chuyển
	@Builder.Default
	@Column(name = "discount_type", nullable = false, length = 20)
	private String discountType = "PERCENT";

	@Builder.Default
	@Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
	private BigDecimal discountValue = BigDecimal.ZERO;

	@Column(length = 500)
	private String description;

	@Column(name = "start_at")
	private LocalDateTime startAt;

	@Column(name = "end_at")
	private LocalDateTime endAt;

	@Builder.Default
	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;
}