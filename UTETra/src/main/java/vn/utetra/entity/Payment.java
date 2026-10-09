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
	private String method; // COD, VNPAY, MOMO

	@Column(name = "transaction_code", length = 100)
	private String transactionCode;

	@Column(nullable = false)
	private BigDecimal amount;

	@Column(nullable = false, length = 20)
	private String status = "PENDING"; // PENDING, SUCCESS, FAILED, REFUNDED

	@Column(name = "paid_at")
	private LocalDateTime paidAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt = LocalDateTime.now();
}