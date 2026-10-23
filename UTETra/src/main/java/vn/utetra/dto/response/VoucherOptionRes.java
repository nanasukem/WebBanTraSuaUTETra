package vn.utetra.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** 1 mã trong khung "Mã của bạn" ở trang Checkout */
public record VoucherOptionRes(
		String code,
		String name,
		String applyTo,          // luôn PRODUCT (mã giảm tiền món)
		String discountType,     // PERCENT / AMOUNT
		BigDecimal discountValue,
		BigDecimal maxDiscount,
		BigDecimal minOrderValue,
		String endDate,          // dd/MM/yyyy
		List<String> tags,       // "Bạn mới", "Hạng Vàng+", "Chỉ thanh toán MoMo"...
		boolean usable,          // dùng được cho đơn hiện tại không
		String reason,           // lý do không dùng được (null nếu dùng được)
		BigDecimal discount) {   // số tiền sẽ giảm nếu dùng
}