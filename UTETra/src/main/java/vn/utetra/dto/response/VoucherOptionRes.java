package vn.utetra.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record VoucherOptionRes(
		String code,
		String name,
		String applyTo,
		String discountType,
		BigDecimal discountValue,
		BigDecimal maxDiscount,
		BigDecimal minOrderValue,
		String endDate,
		List<String> tags,
		boolean usable,
		String reason,
		BigDecimal discount) {
}