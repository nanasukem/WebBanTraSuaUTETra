package vn.utetra.dto.response;

import java.math.BigDecimal;

public record PaymentInfoRes(
		String method,
		String methodLabel,
		String status,
		String badge,
		String badgeClass,
		String icon,
		String line,
		boolean prepaid,
		String paidAt,
		String transactionCode,
		String bankCode,
		boolean awaiting,
		long payDeadlineMillis,
		String payDeadline,
		Refund refund) {

	public record Refund(
			String status,
			String statusLabel,
			BigDecimal amount,
			String destination,
			String requestedAt,
			String refundedAt,
			String transactionCode,
			String note,
			String arrival,
			boolean arrived,
			String arrivalLabel) {
	}
}
