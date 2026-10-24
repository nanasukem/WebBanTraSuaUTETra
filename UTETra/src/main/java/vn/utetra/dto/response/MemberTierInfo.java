package vn.utetra.dto.response;

import java.math.BigDecimal;

public record MemberTierInfo(
		String tierName,
		BigDecimal discountPercent,
		BigDecimal maxDiscount,
		BigDecimal spentLast12Months,
		String nextTierName,
		BigDecimal amountToNextTier) {
}