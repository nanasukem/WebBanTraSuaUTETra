package vn.utetra.dto.response;

import java.math.BigDecimal;

public record OrderListRes(
		Integer id,
		String branchName,
		boolean pickup,
		String status,
		String statusLabel,
		String badgeClass,
		int totalQty,
		String firstItem,
		int moreItems,
		BigDecimal totalAmount,
		String paymentMethodLabel,
		String createdAt,
		boolean canCancel,
		String cancelledBy,
		int toReview,
		PaymentInfoRes payment,
		OrderIssueRes issue) {
}