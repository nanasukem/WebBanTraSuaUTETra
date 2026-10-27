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

@Entity
@Table(name = "Orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "branch_id", nullable = false)
	private Branch branch;

	@Enumerated(EnumType.STRING)
	@Column(name = "delivery_type", nullable = false, length = 10)
	private DeliveryType deliveryType = DeliveryType.DELIVERY;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "carrier_id")
	private Carrier carrier;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "shipper_id")
	private User shipper;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "manager_id")
	private User manager;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_voucher_id")
	private Voucher productVoucher;

	@Column(name = "receiver_name", nullable = false, length = 100)
	private String receiverName;

	@Column(name = "receiver_phone", nullable = false, length = 15)
	private String receiverPhone;

	@Column(name = "delivery_address", length = 255)
	private String deliveryAddress;
	
	@Column(name = "delivery_note", length = 200)
	private String deliveryNote;
	
	@Column(name = "note", length = 500)
	private String note;

	@Column(name = "items_total", nullable = false, precision = 12, scale = 0)
	private BigDecimal itemsTotal = BigDecimal.ZERO;

	@Column(name = "product_discount", nullable = false, precision = 12, scale = 0)
	private BigDecimal productDiscount = BigDecimal.ZERO;

	@Column(name = "member_discount", nullable = false, precision = 12, scale = 0)
	private BigDecimal memberDiscount = BigDecimal.ZERO;

	@Column(name = "member_tier_name", length = 50)
	private String memberTierName;

	@Column(name = "shipping_fee", nullable = false, precision = 12, scale = 0)
	private BigDecimal shippingFee = BigDecimal.ZERO;

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

	@Enumerated(EnumType.STRING)
	@Column(name = "cancelled_by", length = 10)
	private vn.utetra.entity.enums.CancelledBy cancelledBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	@Column(name = "delivered_at")
	private LocalDateTime deliveredAt;

	@Column(name = "refund_amount", nullable = false, precision = 12, scale = 0)
	private BigDecimal refundAmount = BigDecimal.ZERO;

	@Column(name = "eta_minutes")
	private Integer etaMinutes;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrderDetail> details = new ArrayList<>();

	public BigDecimal getGoodsRevenue() {
		BigDecimal ship = shippingFee == null ? BigDecimal.ZERO : shippingFee;
		return getSalesAmount().subtract(ship);
	}

	public boolean countsAsRevenue() {
		return status == vn.utetra.entity.enums.OrderStatus.DELIVERED
				|| (status == vn.utetra.entity.enums.OrderStatus.RETURNED
						&& paymentMethod != vn.utetra.entity.enums.PaymentMethod.COD
						&& paymentStatus == vn.utetra.entity.enums.PaymentStatus.PAID);
	}

	public BigDecimal getSalesAmount() {
		BigDecimal total = totalAmount == null ? BigDecimal.ZERO : totalAmount;
		BigDecimal rf = refundAmount == null ? BigDecimal.ZERO : refundAmount;
		return total.subtract(rf).max(BigDecimal.ZERO);
	}

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
