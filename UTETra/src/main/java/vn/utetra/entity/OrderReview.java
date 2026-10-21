package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Đánh giá quán của cả đơn (phục vụ, đóng gói, tốc độ) + góp ý. 1 đơn 1 dòng */
@Entity
@Table(name = "OrderReviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderReview {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@OneToOne
	@JoinColumn(name = "order_id", nullable = false, unique = true)
	private Order order;

	@ManyToOne
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "store_rating", nullable = false)
	private Integer storeRating;

	/** Góp ý cho quán (không bắt buộc) */
	@Column(length = 1000)
	private String comment;

	/** Gợi ý khách bấm chọn, ngăn bằng "|": "Nhân viên thân thiện|Làm món nhanh" */
	@Column(length = 255)
	private String tags;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt = LocalDateTime.now();
}
