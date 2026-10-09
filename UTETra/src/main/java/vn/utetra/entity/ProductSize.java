package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "ProductSizes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductSize {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(name = "size_name", nullable = false, length = 20)
	private String sizeName; // S, M, L

	@Column(name = "extra_price", nullable = false)
	private BigDecimal extraPrice = BigDecimal.ZERO;
}