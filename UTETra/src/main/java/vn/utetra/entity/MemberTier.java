package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "MemberTiers")
@Getter
@Setter
@NoArgsConstructor
public class MemberTier {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "name", nullable = false, unique = true, length = 50)
	private String name;

	@Column(name = "min_spent", nullable = false, precision = 12, scale = 0)
	private BigDecimal minSpent = BigDecimal.ZERO;

	@Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
	private BigDecimal discountPercent = BigDecimal.ZERO;

	@Column(name = "max_discount", precision = 12, scale = 0)
	private BigDecimal maxDiscount;

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder = 0;
}