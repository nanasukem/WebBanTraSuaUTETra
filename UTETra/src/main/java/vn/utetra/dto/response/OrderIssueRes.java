package vn.utetra.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record OrderIssueRes(
		Integer id,
		String reason,
		String reasonLabel,
		String description,
		List<String> images,
		String status,
		String statusLabel,
		String badgeClass,
		BigDecimal refundAmount,
		String storeNote,
		String createdAt,
		String resolvedAt,
		int removedReviews) {
}
