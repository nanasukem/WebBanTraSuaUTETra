package vn.utetra.entity;

import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.entity.enums.PaymentMethod;
import vn.utetra.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Bảng Orders. Mỗi đơn thuộc 1 shop (branch). totalAmount = itemsTotal -
 * productDiscount + shippingFee - shippingDiscount
 */
@Entity
@Table(name = "Orders") // "Order" là từ khoá SQL nên bảng tên Orders
@Getter
@Setter
@NoArgsConstructor
public class Order {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user; // khách đặt

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "branch_id", nullable = false)
	private Branch branch;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "carrier_id")
	private Carrier carrier;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "shipper_id")
	private User shipper;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_voucher_id")
	private Voucher productVoucher;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "shipping_voucher_id")
	private Voucher shippingVoucher;

	@Column(name = "receiver_name", nullable = false, length = 100)
	private String receiverName;

	@Column(name = "receiver_phone", nullable = false, length = 15)
	private String receiverPhone;

	@Column(name = "delivery_address", nullable = false, length = 255)
	private String deliveryAddress;

	@Column(name = "note", length = 500)
	private String note;

	@Column(name = "items_total", nullable = false, precision = 12, scale = 0)
	private BigDecimal itemsTotal = BigDecimal.ZERO;

	@Column(name = "product_discount", nullable = false, precision = 12, scale = 0)
	private BigDecimal productDiscount = BigDecimal.ZERO;

	@Column(name = "shipping_fee", nullable = false, precision = 12, scale = 0)
	private BigDecimal shippingFee = BigDecimal.ZERO;

	@Column(name = "shipping_discount", nullable = false, precision = 12, scale = 0)
	private BigDecimal shippingDiscount = BigDecimal.ZERO;

	@Column(name = "total_amount", nullable = false, precision = 12, scale = 0)
	private BigDecimal totalAmount = BigDecimal.ZERO;

	@Column(name = "commission_amount", nullable = false, precision = 12, scale = 0)
	private BigDecimal commissionAmount = BigDecimal.ZERO; // tiền app thu của shop

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private OrderStatus status = OrderStatus.PENDING;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_method", nullable = false, length = 20)
	private PaymentMethod paymentMethod;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_status", nullable = false, length = 20)
	private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

	@Column(name = "cancel_reason", length = 255)
	private String cancelReason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	@Column(name = "delivered_at")
	private LocalDateTime deliveredAt;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrderDetail> details = new ArrayList<>();

	/** Thêm 1 dòng chi tiết và gắn 2 chiều */
	public void addDetail(OrderDetail detail) {
		detail.setOrder(this);
		details.add(detail);
	}

	@PrePersist
	void prePersist() {
		if (createdAt == null)
			createdAt = LocalDateTime.now();
	}

	@PreUpdate
	void preUpdate() {
		updatedAt = LocalDateTime.now();
	}
}