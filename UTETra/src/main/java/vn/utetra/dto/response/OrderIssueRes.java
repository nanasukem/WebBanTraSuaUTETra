package vn.utetra.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** Báo cáo "vấn đề với đơn" để hiện cho khách / quán */
public record OrderIssueRes(
		Integer id,
		String reason,           // WRONG_ITEM, MISSING_ITEM...
		String reasonLabel,
		String description,
		List<String> images,
		String status,           // PENDING / REFUNDED / REMADE / REJECTED
		String statusLabel,
		String badgeClass,
		BigDecimal refundAmount,
		String storeNote,
		String createdAt,
		String resolvedAt) {
}
