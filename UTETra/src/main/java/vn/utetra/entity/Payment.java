package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 1 lần thanh toán online (VNPAY / MoMo) của đơn. Mỗi lần bấm "Thanh toán" / "Thanh toán lại" là 1 dòng mới
 * với mã giao dịch riêng (txnRef) gửi sang cổng thanh toán.
 *
 * status: PENDING (đang chờ khách trả) -> SUCCESS / FAILED / CANCELLED (khách bỏ) / EXPIRED (quá hạn)
 *         SUCCESS -> REFUND_PENDING (đang hoàn) -> REFUNDED (đã hoàn)
 */
@Entity
@Table(name = "Payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	@Column(nullable = false, length = 20)
	private String method; // COD, VNPAY, MOMO

	/** Mã giao dịch phía mình gửi sang cổng (vnp_TxnRef / orderId của MoMo), duy nhất */
	@Column(name = "txn_ref", length = 50)
	private String txnRef;

	/** Mã giao dịch phía cổng trả về (vnp_TransactionNo / transId của MoMo) */
	@Column(name = "transaction_code", length = 100)
	private String transactionCode;

	@Column(nullable = false)
	private BigDecimal amount;

	@Column(nullable = false, length = 20)
	private String status = "PENDING";

	/** Ngân hàng / hình thức khách dùng: NCB, VNPAYQR, qr, napas, credit... */
	@Column(name = "bank_code", length = 30)
	private String bankCode;

	/** vnp_CreateDate đã gửi (yyyyMMddHHmmss, GMT+7) - VNPAY cần lại khi truy vấn / hoàn tiền */
	@Column(name = "gateway_date", length = 14)
	private String gatewayDate;

	/** Mã kết quả cổng trả về: vnp_ResponseCode / resultCode của MoMo */
	@Column(name = "response_code", length = 10)
	private String responseCode;

	@Column(length = 255)
	private String message;

	@Column(name = "paid_at")
	private LocalDateTime paidAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt = LocalDateTime.now();

	// ===== Hoàn tiền =====

	/** Số tiền cần hoàn (null = hoàn toàn bộ amount; nhỏ hơn = hoàn một phần khi khách báo vấn đề) */
	@Column(name = "refund_amount", precision = 12, scale = 0)
	private BigDecimal refundAmount;

	@Column(name = "refund_ref", length = 50)
	private String refundRef;

	@Column(name = "refund_transaction_code", length = 100)
	private String refundTransactionCode;

	@Column(name = "refund_requested_at")
	private LocalDateTime refundRequestedAt;

	@Column(name = "refunded_at")
	private LocalDateTime refundedAt;

	@Column(name = "refund_attempts", nullable = false)
	private Integer refundAttempts = 0;

	@Column(name = "refund_message", length = 255)
	private String refundMessage;

	/** Số tiền thực sự hoàn: refundAmount nếu có, không thì toàn bộ */
	public BigDecimal refundValue() {
		return refundAmount != null ? refundAmount : amount;
	}

	public boolean isOnline() {
		return "VNPAY".equals(method) || "MOMO".equals(method);
	}
}
