package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Order {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne
	@JoinColumn(name = "customer_id", nullable = false)
	private User customer;

	@ManyToOne
	@JoinColumn(name = "branch_id", nullable = false)
	private Branch branch;

	@ManyToOne
	@JoinColumn(name = "shipper_id")
	private User shipper;

	@ManyToOne
	@JoinColumn(name = "voucher_id")
	private Voucher voucher;

	@Column(name = "total_amount", nullable = false)
	private BigDecimal totalAmount;

	@Column(name = "discount_amount")
	private BigDecimal discountAmount = BigDecimal.ZERO;

	@Column(name = "final_amount", nullable = false)
	private BigDecimal finalAmount;

	@Column(name = "payment_method", length = 30)
	private String paymentMethod; // COD, VNPAY...

	@Column(name = "payment_status", length = 30)
	private String paymentStatus; // PENDING, PAID, FAILED

	@Column(name = "order_status", length = 30)
	private String orderStatus; // PENDING, CONFIRMED, DELIVERING, COMPLETED, CANCELLED

	@Column(name = "shipping_address", columnDefinition = "NVARCHAR(MAX)")
	private String shippingAddress;

	@Column(columnDefinition = "NVARCHAR(MAX)")
	private String note;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt = LocalDateTime.now();
}