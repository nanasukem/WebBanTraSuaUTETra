package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Menu chung của cả chuỗi (Admin quản lý). Tình trạng còn/hết theo chi nhánh
 * nằm ở BranchProduct
 */
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

	/** false = ngừng bán trên toàn chuỗi */
	@Column(name = "is_available", nullable = false)
	private Boolean isAvailable = true;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt = LocalDateTime.now();

	/**
	 * != null: món này là TOPPING BÁN RIÊNG (danh mục Topping), tạo tự động khi Admin thêm topping
	 * ở trang /admin/toppings. Món loại này không chọn size / đường / đá / topping.
	 */
	@ManyToOne
	@JoinColumn(name = "topping_id")
	private Topping topping;

	/** true = topping bán riêng (ẩn size, đường, đá khi đặt) */
	public boolean isToppingItem() {
		return topping != null;
	}
}