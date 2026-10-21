package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "Notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne
	@JoinColumn(name = "order_id")
	private Order order;

	@Column(nullable = false, length = 150)
	private String title;

	@Column(length = 500)
	private String content;

	/** Loại để chọn biểu tượng: ORDER, REVIEW, COIN, VOUCHER, USER */
	@Column(nullable = false, length = 20)
	private String type = "ORDER";

	/** Bấm vào thông báo thì mở trang này (vd /orders/12) */
	@Column(length = 255)
	private String link;

	/** Khoá chống gửi trùng (vd VOUCHER_LOW:5 chỉ báo 1 lần) */
	@Column(name = "ref_key", length = 100)
	private String refKey;

	@Column(name = "is_read", nullable = false)
	private Boolean isRead = false;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt = LocalDateTime.now();
}