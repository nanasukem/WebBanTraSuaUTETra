package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Bảng VoucherUsages - ai đã dùng mã nào cho đơn nào (đếm lượt mỗi người, hoàn lượt khi huỷ) */
@Entity
@Table(name = "VoucherUsages")
@Getter
@Setter
@NoArgsConstructor
public class VoucherUsage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "voucher_id", nullable = false)
	private Voucher voucher;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	@Column(name = "used_at", nullable = false)
	private LocalDateTime usedAt = LocalDateTime.now();
}