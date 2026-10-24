package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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
	private String method;

	@Column(name = "txn_ref", length = 50)
	private String txnRef;

	@Column(name = "transaction_code", length = 100)
	private String transactionCode;

	@Column(nullable = false)
	private BigDecimal amount;

	@Column(nullable = false, length = 20)
	private String status = "PENDING";

	@Column(name = "bank_code", length = 30)
	private String bankCode;

	@Column(name = "gateway_date", length = 14)
	private String gatewayDate;

	@Column(name = "response_code", length = 10)
	private String responseCode;

	@Column(length = 255)
	private String message;

	@Column(name = "paid_at")
	private LocalDateTime paidAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt = LocalDateTime.now();

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

	public BigDecimal refundValue() {
		return refundAmount != null ? refundAmount : amount;
	}

	public boolean isOnline() {
		return "VNPAY".equals(method) || "MOMO".equals(method);
	}
}
