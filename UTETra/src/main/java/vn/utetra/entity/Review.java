package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "Reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Review {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@OneToOne
	@JoinColumn(name = "order_detail_id", nullable = false, unique = true)
	private OrderDetail orderDetail;

	@Column(nullable = false)
	private Integer rating;

	/** Bình luận - không bắt buộc (null = chỉ chấm sao) */
	@Column(length = 2000)
	private String content;

	/** Gợi ý khách bấm chọn, ngăn bằng "|": "Đúng vị, rất ngon|Sẽ mua lại" */
	@Column(length = 255)
	private String tags;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt = LocalDateTime.now();
}