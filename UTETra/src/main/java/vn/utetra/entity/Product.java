package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Product {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne
	@JoinColumn(name = "branch_id", nullable = false)
	private Branch branch;

	@ManyToOne
	@JoinColumn(name = "category_id", nullable = false)
	private Category category;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(name = "base_price", nullable = false)
	private BigDecimal basePrice;

	@Column(columnDefinition = "NVARCHAR(MAX)")
	private String description;

	@Column(name = "image_cloudinary_id")
	private String imageCloudinaryId;

	@Column(name = "sold_count", nullable = false)
	private Integer soldCount = 0;

	@Column(name = "is_available", nullable = false)
	private Boolean isAvailable = true;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt = LocalDateTime.now();
}