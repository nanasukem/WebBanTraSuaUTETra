package vn.utetra.entity;

import vn.utetra.entity.enums.DeliveryType;
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
 * Bảng Orders. Mỗi đơn thuộc 1 chi nhánh (khách chọn khi Checkout).
 * totalAmount = itemsTotal - productDiscount - memberDiscount + shippingFee - shippingDiscount
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

	@Enumerated(EnumType.STRING)
	@Column(name = "delivery_type", nullable = false, length = 10)
	private DeliveryType deliveryType = DeliveryType.DELIVERY; // giao tận nơi / đến lấy

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "carrier_id")
	private Carrier carrier; // null khi đến lấy

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

	@Column(name = "delivery_address", length = 255)
	private String deliveryAddress; // null khi đến lấy
	
	@Column(name = "delivery_note", length = 200)
	private String deliveryNote; // ghi chú địa chỉ, chép từ sổ địa chỉ lúc đặt
	
	@Column(name = "note", length = 500)
	private String note;

	@Column(name = "items_total", nullable = false, precision = 12, scale = 0)
	private BigDecimal itemsTotal = BigDecimal.ZERO;

	@Column(name = "product_discount", nullable = false, precision = 12, scale = 0)
	private BigDecimal productDiscount = BigDecimal.ZERO;

	@Column(name = "member_discount", nullable = false, precision = 12, scale = 0)
	private BigDecimal memberDiscount = BigDecimal.ZERO; // giảm theo hạng thành viên

	@Column(name = "member_tier_name", length = 50)
	private String memberTierName; // hạng lúc đặt (chốt lại, sau này lên/xuống hạng không đổi)

	@Column(name = "shipping_fee", nullable = false, precision = 12, scale = 0)
	private BigDecimal shippingFee = BigDecimal.ZERO;

	@Column(name = "shipping_discount", nullable = false, precision = 12, scale = 0)
	private BigDecimal shippingDiscount = BigDecimal.ZERO;

	@Column(name = "total_amount", nullable = false, precision = 12, scale = 0)
	private BigDecimal totalAmount = BigDecimal.ZERO;


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

	/** Ai huỷ đơn (null = chưa huỷ) */
	@Enumerated(EnumType.STRING)
	@Column(name = "cancelled_by", length = 10)
	private vn.utetra.entity.enums.CancelledBy cancelledBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	@Column(name = "delivered_at")
	private LocalDateTime deliveredAt;

	/** Tiền quán đã hoàn cho khách sau khi giao (khách báo vấn đề với đơn) - trừ khỏi doanh thu */
	@Column(name = "refund_amount", nullable = false, precision = 12, scale = 0)
	private BigDecimal refundAmount = BigDecimal.ZERO;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrderDetail> details = new ArrayList<>();

	/**
	 * Doanh thu bán hàng của đơn = tiền món - mã giảm món - ưu đãi hạng - tiền đã hoàn (báo vấn đề).
	 * KHÔNG gồm phí ship (phí ship trả cho đối tác giao hàng, không phải tiền quán bán được).
	 * Dùng cho báo cáo doanh thu, chi tiêu của khách và xét hạng thành viên.
	 */
	public BigDecimal getSalesAmount() {
		BigDecimal items = itemsTotal == null ? BigDecimal.ZERO : itemsTotal;
		BigDecimal pd = productDiscount == null ? BigDecimal.ZERO : productDiscount;
		BigDecimal md = memberDiscount == null ? BigDecimal.ZERO : memberDiscount;
		BigDecimal rf = refundAmount == null ? BigDecimal.ZERO : refundAmount; // tiền đã hoàn khi khách báo vấn đề
		return items.subtract(pd).subtract(md).subtract(rf).max(BigDecimal.ZERO);
	}

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