package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.utetra.entity.enums.IssueReason;
import vn.utetra.entity.enums.IssueStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bảng OrderIssues - khách "Báo vấn đề với đơn" (thay cho Trả hàng: đồ uống không trả lại được).
 * Khách báo trong 2 giờ sau khi nhận món, kèm ảnh; Manager của chi nhánh xử lý:
 * hoàn tiền (một phần / toàn bộ), làm lại món, hoặc từ chối (ghi lý do). Mỗi đơn báo 1 lần.
 */
@Entity
@Table(name = "OrderIssues")
@Getter
@Setter
@NoArgsConstructor
public class OrderIssue {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id", nullable = false, unique = true)
	private Order order;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private IssueReason reason;

	@Column(length = 500)
	private String description;

	/** Link ảnh Cloudinary, cách nhau bởi | (tối đa 3 ảnh) */
	@Column(length = 1000)
	private String images;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private IssueStatus status = IssueStatus.PENDING;

	/** Số tiền quán hoàn cho khách (khi status = REFUNDED) */
	@Column(name = "refund_amount", nullable = false, precision = 12, scale = 0)
	private BigDecimal refundAmount = BigDecimal.ZERO;

	/** Quán ghi chú / lý do từ chối */
	@Column(name = "store_note", length = 255)
	private String storeNote;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt = LocalDateTime.now();

	@Column(name = "resolved_at")
	private LocalDateTime resolvedAt;
}
