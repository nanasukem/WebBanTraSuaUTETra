package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "ProductSizes")
@Getter
@Setter
@NoArgsConstructor
public class ProductSize {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(name = "size", nullable = false, length = 1)
	private String size;

	@Column(name = "extra_price", nullable = false, precision = 12, scale = 0)
	private BigDecimal extraPrice = BigDecimal.ZERO;
}