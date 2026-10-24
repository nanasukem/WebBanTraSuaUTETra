package vn.utetra.dto.response;

import java.math.BigDecimal;

public record ManagerVoucherRes(
		Integer id,
		String code,
		String name,
		boolean chainWide,
		String applyToLabel,
		String discountText,
		String conditionText,
		String period,
		String status,
		String statusLabel,
		String badgeClass,
		int quantity,
		int usedCount,
		long usedAtBranch,
		BigDecimal discountAtBranch
) {
}
