package vn.utetra.dto.response;

import java.math.BigDecimal;

/** Thông tin hạng để hiện ở trang hồ sơ / checkout */
public record MemberTierInfo(
		String tierName,               // hạng hiện tại
		BigDecimal discountPercent,    // % giảm mỗi đơn
		BigDecimal maxDiscount,        // giảm tối đa mỗi đơn (null = không giới hạn)
		BigDecimal spentLast12Months,  // chi tiêu đơn đã giao trong 12 tháng gần nhất
		String nextTierName,           // hạng kế tiếp (null = đã cao nhất)
		BigDecimal amountToNextTier) { // còn thiếu bao nhiêu để lên hạng kế tiếp
}